package com.termux.app.ngterminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LauncherVersionTest {
    private static final String V15 = "#!/data/data/com.termux/files/usr/bin/bash\n# ngterminal-launcher: 15\n# rest\n";
    private static final String V14 = "#!/data/data/com.termux/files/usr/bin/bash\n# Claude Code 2.1.296 (Opus 5.5) in a 2.8 MB minimal glibc rootfs.\n";

    @Test public void readsMarkerOnLine2() { assertEquals(15, LauncherVersion.parse(V15)); }
    @Test public void handMadeV14HasNoMarker() { assertEquals(0, LauncherVersion.parse(V14)); }
    @Test public void crlfMarker() { assertEquals(15, LauncherVersion.parse("#!/bin/bash\r\n# ngterminal-launcher: 15\r\n")); }
    @Test public void markerOnlyCountsOnLine2() { assertEquals(0, LauncherVersion.parse("# ngterminal-launcher: 15\n#!/bin/bash\n")); }
    @Test public void garbageAndEmpty() {
        assertEquals(0, LauncherVersion.parse(null));
        assertEquals(0, LauncherVersion.parse(""));
        assertEquals(0, LauncherVersion.parse("#!/bin/bash"));
        assertEquals(0, LauncherVersion.parse("#!/bin/bash\n# ngterminal-launcher: abc\n"));
    }
    @Test public void needsInstall() {
        assertTrue(LauncherVersion.needsInstall(null, 15));     // missing
        assertTrue(LauncherVersion.needsInstall(V14, 15));      // v14, no marker
        assertTrue(LauncherVersion.needsInstall(V15.replace("15", "14"), 15));
        assertFalse(LauncherVersion.needsInstall(V15, 15));     // same
        assertFalse(LauncherVersion.needsInstall(V15.replace("15", "16"), 15));   // newer hand edit stays
    }
}
