package com.termux.app.ngterminal;

import java.util.ArrayList;
import java.util.List;

/**
 * NGTerminal: remembers what the tab bar last showed, so a title-only refresh (Claude Code retitles ~1/s while
 * working) updates labels in place instead of rebuilding views, and the strip scrolls only when the selection moves.
 */
public final class TabBarState {
    private List<String> mHandles;
    private String mSelected;
    private boolean mHasSelection;

    /** True if the session list (identity and order) differs from the last call; remembers the new list. */
    public boolean structureChanged(List<String> handles) {
        boolean changed = mHandles == null || !mHandles.equals(handles);
        mHandles = new ArrayList<>(handles);
        return changed;
    }

    /** True if the selected session differs from the last call; remembers it. */
    public boolean selectionChanged(String selectedHandle) {
        boolean changed = !mHasSelection || (mSelected == null ? selectedHandle != null : !mSelected.equals(selectedHandle));
        mSelected = selectedHandle;
        mHasSelection = true;
        return changed;
    }
}
