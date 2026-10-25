package com.termux.app.ngterminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/** Review I3: minSdk is 24, java.nio.file is API 26 - NGTerminal app code using it crashes on Android 7 (NoSuchMethodError). */
public class MinSdkGuardTest {
    @Test public void ngterminalAppCodeAvoidsJavaNioFile() throws Exception {
        File dir = new File("src/main/java/com/termux/app/ngterminal");                       // Gradle runs app tests in app/
        if (!dir.isDirectory()) dir = new File("app/src/main/java/com/termux/app/ngterminal");  // local runner: repo root
        assertTrue("sources not found from " + new File(".").getAbsolutePath(), dir.isDirectory());
        List<String> bad = new ArrayList<>();
        File[] files = dir.listFiles();
        for (File f : files == null ? new File[0] : files) {
            if (!f.getName().endsWith(".java")) continue;
            String s = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);   // JVM test: nio is fine here
            if (s.contains("import java.nio.file") || s.contains(".toPath()")) bad.add(f.getName());
        }
        Collections.sort(bad);
        assertEquals("java.nio.file (API 26) in NGTerminal app code", Collections.<String>emptyList(), bad);
    }
}
