package com.termux.app.ngterminal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.junit.Test;

public class TabBarStateTest {
    @Test public void firstListIsAStructureChange() {
        assertTrue(new TabBarState().structureChanged(Arrays.asList("a", "b")));
    }
    @Test public void sameSessionsInSameOrderIsNotAStructureChange() {
        TabBarState s = new TabBarState();
        s.structureChanged(Arrays.asList("a", "b"));
        assertFalse(s.structureChanged(Arrays.asList("a", "b")));   // title-only refresh: update in place
    }
    @Test public void addedRemovedOrReorderedSessionsAreStructureChanges() {
        TabBarState s = new TabBarState();
        s.structureChanged(Arrays.asList("a", "b"));
        assertTrue(s.structureChanged(Arrays.asList("a", "b", "c")));
        assertTrue(s.structureChanged(Arrays.asList("a", "c")));
        assertTrue(s.structureChanged(Arrays.asList("c", "a")));
    }
    @Test public void scrollOnlyWhenTheSelectionChanges() {
        TabBarState s = new TabBarState();
        assertTrue(s.selectionChanged("a"));
        assertFalse(s.selectionChanged("a"));                        // title tick on the same tab: don't scroll
        assertTrue(s.selectionChanged("b"));
        assertTrue(s.selectionChanged(null));
    }
}
