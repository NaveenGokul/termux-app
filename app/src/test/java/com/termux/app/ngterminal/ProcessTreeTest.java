package com.termux.app.ngterminal;

import static org.junit.Assert.assertEquals;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ProcessTreeTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private void proc(File root, int pid, String stat, String cmdline) throws Exception {
        File d = new File(root, String.valueOf(pid)); d.mkdirs();
        if (stat != null) Files.write(new File(d, "stat").toPath(), stat.getBytes(StandardCharsets.UTF_8));
        if (cmdline != null) Files.write(new File(d, "cmdline").toPath(), cmdline.getBytes(StandardCharsets.UTF_8));
    }

    @Test public void findsDirectChildren() throws Exception {
        File root = tmp.newFolder("proc");
        proc(root, 100, "100 (bash) S 1 100 100 0", "bash\0");
        proc(root, 200, "200 (proot) S 100 200 100 0", "proot\0-r\0x\0");
        proc(root, 300, "300 (sleep) S 200 300 100 0", "sleep\0999\0");
        assertEquals(Collections.singletonList(200), ProcessTree.childPids(root, 100));
        assertEquals("proot -r x", ProcessTree.commandLine(root, 200));
    }

    @Test public void commNameWithSpacesAndParens() throws Exception {
        File root = tmp.newFolder("proc");
        proc(root, 400, "400 (my (odd) name) S 100 400 100 0", "odd\0");
        assertEquals(Collections.singletonList(400), ProcessTree.childPids(root, 100));
    }

    @Test public void skipsVanishedAndGarbledEntries() throws Exception {
        File root = tmp.newFolder("proc");
        proc(root, 500, null, null);                       // vanished: dir exists, no stat
        proc(root, 501, "garbage", null);                  // unparsable
        new File(root, "self").mkdirs();                   // non-numeric entries
        proc(root, 502, "502 (sh) S 100 502 100 0", "sh\0");
        assertEquals(Arrays.asList(502), ProcessTree.childPids(root, 100));
        assertEquals("", ProcessTree.commandLine(root, 999)); // unreadable -> empty, no throw
    }
}
