package com.termux.app.ngterminal;

import android.content.Intent;
import android.graphics.Rect;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.TermuxService;
import com.termux.app.activities.SettingsActivity;
import com.termux.shared.logger.Logger;
import com.termux.shared.shell.TermuxSession;
import com.termux.terminal.TerminalSession;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** NGTerminal: Pixel-style tab bar over Termux sessions. Reuses Termux's own session actions only. */
public final class TermuxSessionsTabBarController {
    private final TermuxActivity mActivity;
    private final ContextThemeWrapper mThemed;
    private LinearLayout mTabs;
    private final TabBarState mState = new TabBarState();

    public TermuxSessionsTabBarController(TermuxActivity activity) {
        mActivity = activity;
        mThemed = new ContextThemeWrapper(activity, R.style.Theme_NGTerminal_M3);
    }

    public void attach(ViewGroup host) {
        View bar = LayoutInflater.from(mThemed).inflate(R.layout.ng_tab_bar, host, true);
        mTabs = bar.findViewById(R.id.ng_tabs);
        View add = bar.findViewById(R.id.ng_add_tab);
        add.setOnClickListener(v -> mActivity.getTermuxTerminalSessionClient().addNewSession(false, null));
        add.setOnLongClickListener(v -> { mActivity.getTermuxTerminalSessionClient().addNewSession(true, null); return true; });
        bar.findViewById(R.id.ng_settings).setOnClickListener(v ->
            mActivity.startActivity(new Intent(mActivity, SettingsActivity.class)));
    }

    public void refresh() {
        TermuxService service = mActivity.getTermuxService();
        if (mTabs == null || service == null) return;
        TerminalSession current = mActivity.getCurrentSession();
        List<TerminalSession> list = new ArrayList<>();
        List<String> handles = new ArrayList<>();
        for (TermuxSession ts : service.getTermuxSessions()) {
            TerminalSession s = ts.getTerminalSession();
            if (s != null) { list.add(s); handles.add(s.mHandle); }
        }
        // Rebuild views only when sessions were added/removed/reordered: a title tick (Claude Code retitles ~1/s while
        // working) then updates labels in place, so taps in progress and the strip's scroll position survive.
        if (mState.structureChanged(handles)) {
            mTabs.removeAllViews();
            LayoutInflater inflater = LayoutInflater.from(mThemed);
            for (final TerminalSession s : list) {
                View tab = inflater.inflate(R.layout.ng_tab_item, mTabs, false);
                tab.setOnClickListener(v -> mActivity.getTermuxTerminalSessionClient().setCurrentSession(s));
                tab.setOnLongClickListener(v -> { mActivity.getTermuxTerminalSessionClient().renameSession(s); return true; });
                tab.findViewById(R.id.ng_tab_close).setOnClickListener(v -> close(s));
                mTabs.addView(tab);
            }
        }
        View selectedTab = null;
        for (int i = 0; i < list.size() && i < mTabs.getChildCount(); i++) {
            TerminalSession s = list.get(i);
            View tab = mTabs.getChildAt(i);
            boolean selected = s == current;
            bind(tab, TabLabels.labelFor(s.mSessionName, s.getTitle(), i + 1), selected);
            if (selected) selectedTab = tab;
        }
        // Scroll the selected tab into view only when the selection moved, not on every title change.
        if (mState.selectionChanged(current == null ? null : current.mHandle) && selectedTab != null) {
            final View t = selectedTab;
            t.post(() -> t.requestRectangleOnScreen(new Rect(0, 0, t.getWidth(), t.getHeight())));
        }
    }

    private void bind(View tab, String label, boolean selected) {
        TextView title = tab.findViewById(R.id.ng_tab_title);
        if (!label.contentEquals(title.getText())) title.setText(label);
        title.setTextColor(ContextCompat.getColor(mActivity, selected ? R.color.ng_on_secondary_container : R.color.ng_on_surface_variant));
        tab.setBackgroundResource(selected ? R.drawable.ng_tab_bg_selected : R.drawable.ng_tab_bg);
        tab.setSelected(selected);
        tab.setContentDescription("Tab: " + label);
        ImageButton close = tab.findViewById(R.id.ng_tab_close);
        close.setContentDescription("Close " + label);
    }

    private void close(TerminalSession s) {
        int pid = s.getPid();
        List<Integer> children = pid > 0 ? ProcessTree.childPids(new File("/proc"), pid) : Collections.<Integer>emptyList();
        if (children.isEmpty()) { closeNow(s); return; }
        String cmd = ProcessTree.commandLine(new File("/proc"), children.get(0));
        new MaterialAlertDialogBuilder(mThemed)
            .setTitle("Close tab?")
            .setMessage((cmd.isEmpty() ? "A program" : cmd) + " is still running. Close tab?")
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton("Close", (d, w) -> closeNow(s))
            .show();
    }

    /**
     * Close like a desktop terminal: SIGHUP first, so bash saves its history and hangs up its jobs; if the shell is
     * still alive after 1 s, Termux's own "Kill process" (SIGKILL). Then remove the session once it has exited
     * (TermuxSession.finish() ignores running sessions). A background tab is removed through the service, so the
     * current tab stays current; only closing the current tab uses Termux's removeFinishedSession(), which selects a
     * neighbour. Termux may already have auto-removed it (exit 0/130), hence the index check.
     */
    private void closeNow(TerminalSession s) {
        int pid = s.getPid();
        if (s.isRunning() && pid > 0) {
            try {
                Os.kill(pid, OsConstants.SIGHUP);
            } catch (ErrnoException e) {
                Logger.logStackTraceWithMessage("NGTerminalTabs", "SIGHUP failed, falling back to SIGKILL", e);
                s.finishIfRunning();
            }
        }
        removeWhenExited(s, 0);
    }

    private void removeWhenExited(TerminalSession s, int attempt) {
        if (s.isRunning()) {
            if (attempt == 10) s.finishIfRunning();                // ignored SIGHUP for 1 s -> SIGKILL
            if (attempt < 30) {                                    // up to ~3 s in total
                mTabs.postDelayed(() -> removeWhenExited(s, attempt + 1), 100);
                return;
            }
        }
        TermuxService service = mActivity.getTermuxService();
        if (service == null || service.getIndexOfSession(s) < 0) return;
        if (s == mActivity.getCurrentSession())
            mActivity.getTermuxTerminalSessionClient().removeFinishedSession(s);
        else
            service.removeTermuxSession(s);                        // background tab: current tab stays current
    }
}
