package com.termux.app.ngterminal;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class TabLabelsTest {
    @Test public void nameWins() { assertEquals("build", TabLabels.labelFor("build", "vim x", 2)); }
    @Test public void titleWhenNoName() { assertEquals("vim x", TabLabels.labelFor(null, "vim x", 2)); }
    @Test public void blankNameFallsThrough() { assertEquals("vim x", TabLabels.labelFor("  ", "vim x", 2)); }
    @Test public void numberedWhenNothing() { assertEquals("Tab 3", TabLabels.labelFor("", null, 3)); }
}
