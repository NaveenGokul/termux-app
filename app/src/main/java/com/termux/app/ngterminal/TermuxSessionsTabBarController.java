package com.termux.app.ngterminal;

import android.content.Intent;
import android.graphics.Rect;
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
import com.termux.shared.shell.TermuxSession;
import com.termux.terminal.TerminalSession;

import java.io.File;
import java.util.Collections;
import java.util.List;

/** NGTerminal: Pixel-style tab bar over Termux sessions. Reuses Termux's own session actions only. */
public final class TermuxSessionsTabBarController {
    private final TermuxActivity mActivity;
    private final ContextThemeWrapper mThemed;
    private LinearLayout mTabs;

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
        mTabs.removeAllViews();
        List<TermuxSession> sessions = service.getTermuxSessions();
        TerminalSession current = mActivity.getCurrentSession();
        LayoutInflater inflater = LayoutInflater.from(mThemed);
        for (int i = 0; i < sessions.size(); i++) {
            final TerminalSession s = sessions.get(i).getTerminalSession();
            if (s == null) continue;
            View tab = inflater.inflate(R.layout.ng_tab_item, mTabs, false);
            boolean selected = s == current;
            String label = TabLabels.labelFor(s.mSessionName, s.getTitle(), i + 1);
            TextView title = tab.findViewById(R.id.ng_tab_title);
            title.setText(label);
            title.setTextColor(ContextCompat.getColor(mActivity, selected ? R.color.ng_on_secondary_container : R.color.ng_on_surface_variant));
            tab.setBackgroundResource(selected ? R.drawable.ng_tab_bg_selected : R.drawable.ng_tab_bg);
            tab.setContentDescription("Tab: " + label);
            tab.setOnClickListener(v -> mActivity.getTermuxTerminalSessionClient().setCurrentSession(s));
            tab.setOnLongClickListener(v -> { mActivity.getTermuxTerminalSessionClient().renameSession(s); return true; });
            ImageButton close = tab.findViewById(R.id.ng_tab_close);
            close.setContentDescription("Close " + label);
            close.setOnClickListener(v -> close(s));
            mTabs.addView(tab);
            if (selected) tab.post(() -> tab.requestRectangleOnScreen(new Rect(0, 0, tab.getWidth(), tab.getHeight())));
        }
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
     * Termux's own two steps, combined: "Kill process" (SIGKILL the shell, as the context menu does), then remove the
     * session once it has exited. TermuxSession.finish() ignores sessions that are still running, so removal must
     * wait for the exit; Termux may already have auto-removed it (exit 0/130), hence the index check.
     */
    private void closeNow(TerminalSession s) {
        s.finishIfRunning();
        removeWhenExited(s, 0);
    }

    private void removeWhenExited(TerminalSession s, int attempt) {
        if (s.isRunning() && attempt < 30) {                       // up to ~3 s for the process to die
            mTabs.postDelayed(() -> removeWhenExited(s, attempt + 1), 100);
            return;
        }
        TermuxService service = mActivity.getTermuxService();
        if (service != null && service.getIndexOfSession(s) >= 0)
            mActivity.getTermuxTerminalSessionClient().removeFinishedSession(s);
    }
}
