package com.termux.app.ngterminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class LauncherInstallerTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    private static final String V14 = "#!/bin/bash\n# Claude Code 2.1.296\necho v14\n";
    private static final String V15 = "#!/bin/bash\n# ngterminal-launcher: 15\necho v15\n";

    private String read(File f) throws Exception { return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8); }
    private void write(File f, String s) throws Exception { Files.write(f.toPath(), s.getBytes(StandardCharsets.UTF_8)); }

    @Test public void installsWhenMissing() throws Exception {
        File bin = tmp.newFolder("bin"), target = new File(bin, "claude-next"), backup = new File(tmp.getRoot(), "backup");
        assertEquals(LauncherInstaller.Result.INSTALLED, LauncherInstaller.install(target, backup, V15, "20261025-120000"));
        assertEquals(V15, read(target));
        assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(target.toPath())));
        assertFalse(backup.exists());
    }

    @Test public void upgradesV14KeepingABackup() throws Exception {
        File bin = tmp.newFolder("bin"), target = new File(bin, "claude-next"), backup = new File(tmp.getRoot(), "backup");
        write(target, V14);
        assertEquals(LauncherInstaller.Result.UPGRADED, LauncherInstaller.install(target, backup, V15, "20261025-120000"));
        assertEquals(V15, read(target));
        assertEquals(V14, read(new File(backup, "claude-next-20261025-120000")));
        assertFalse(new File(bin, "claude-next.ngnew").exists());
    }

    @Test public void leavesSameOrNewerAlone() throws Exception {
        File bin = tmp.newFolder("bin"), target = new File(bin, "claude-next"), backup = new File(tmp.getRoot(), "backup");
        String v16 = V15.replace("15", "16");
        write(target, v16);
        assertEquals(LauncherInstaller.Result.UP_TO_DATE, LauncherInstaller.install(target, backup, V15, "20261025-120000"));
        assertEquals(v16, read(target));
        write(target, V15);
        assertEquals(LauncherInstaller.Result.UP_TO_DATE, LauncherInstaller.install(target, backup, V15, "20261025-120000"));
        assertFalse(backup.exists());
    }

    /** Review Focus 1: a bash still reading the old launcher must keep reading the old file (rename, not overwrite). */
    @Test public void readerOfOldFileKeepsOldContent() throws Exception {
        File bin = tmp.newFolder("bin"), target = new File(bin, "claude-next");
        write(target, V14);
        try (FileInputStream reader = new FileInputStream(target)) {
            LauncherInstaller.install(target, new File(tmp.getRoot(), "backup"), V15, "20261025-120000");
            assertEquals(V14, new String(LauncherInstaller.readAll(reader), StandardCharsets.UTF_8));
        }
        assertEquals(V15, read(target));
    }

    @Test public void readAllReadsEverything() throws Exception {
        File f = tmp.newFile("big");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5000; i++) sb.append("line ").append(i).append('\n');
        write(f, sb.toString());
        try (FileInputStream in = new FileInputStream(f)) {
            assertTrue(new String(LauncherInstaller.readAll(in), StandardCharsets.UTF_8).endsWith("line 4999\n"));
        }
    }
}
