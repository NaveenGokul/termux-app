package com.termux.app.ngterminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ClaudeKeyTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private void proc(File root, int pid, int ppid, String cmdline) throws Exception {
        File d = new File(root, String.valueOf(pid)); d.mkdirs();
        Files.write(new File(d, "stat").toPath(), (pid + " (x) S " + ppid + " 1 1 0").getBytes(StandardCharsets.UTF_8));
        Files.write(new File(d, "cmdline").toPath(), cmdline.getBytes(StandardCharsets.UTF_8));
    }

    @Test public void termuxTabShellAtPromptIsIdle() throws Exception {   // as seen on the phone: "bash -l", parent = app
        File root = tmp.newFolder("proc");
        proc(root, 100, 50, "/data/data/com.termux/files/usr/bin/bash\0-l\0");
        assertTrue(ClaudeKey.isIdleShell(root, 100));
    }

    @Test public void loginDashShellIsIdle() throws Exception {
        File root = tmp.newFolder("proc");
        proc(root, 100, 50, "-bash\0");
        assertTrue(ClaudeKey.isIdleShell(root, 100));
    }

    @Test public void shellRunningSomethingIsBusy() throws Exception {
        File root = tmp.newFolder("proc");
        proc(root, 100, 50, "/data/data/com.termux/files/usr/bin/bash\0-l\0");
        proc(root, 200, 100, "proot\0-r\0x\0");
        assertFalse(ClaudeKey.isIdleShell(root, 100));
    }

    /** Review Focus 3: a RUN_COMMAND tab running python is not a prompt; typing there would feed python. */
    @Test public void nonShellIsNotIdle() throws Exception {
        File root = tmp.newFolder("proc");
        proc(root, 100, 50, "/data/data/com.termux/files/usr/bin/python3\0script.py\0");
        assertFalse(ClaudeKey.isIdleShell(root, 100));
    }

    @Test public void finishedSessionIsNotIdle() throws Exception {
        File root = tmp.newFolder("proc");
        assertFalse(ClaudeKey.isIdleShell(root, 0));
        assertFalse(ClaudeKey.isIdleShell(root, -1));
        assertFalse(ClaudeKey.isIdleShell(root, 4242));   // vanished: no /proc entry
    }

    @Test public void commandOpensThePickerAndPressesEnter() {
        assertEquals("claude-next --ng-pick\r", ClaudeKey.COMMAND);
    }
}
