package com.termux.app.ngterminal;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** NGTerminal: read-only /proc helpers for "is this tab busy?". Never throws on racing/garbled entries. */
public final class ProcessTree {
    private ProcessTree() {}

    public static List<Integer> childPids(File procRoot, int ppid) {
        List<Integer> out = new ArrayList<>();
        File[] entries = procRoot.listFiles();
        if (entries == null) return out;
        for (File e : entries) {
            int pid;
            try { pid = Integer.parseInt(e.getName()); } catch (NumberFormatException ex) { continue; }
            int parent = parentOf(new File(e, "stat"));
            if (parent == ppid) out.add(pid);
        }
        Collections.sort(out);
        return out;
    }

    /** Fields after the last ')' are: state ppid ... (comm may contain spaces and parentheses). */
    private static int parentOf(File stat) {
        try {
            String s = new String(Files.readAllBytes(stat.toPath()), StandardCharsets.UTF_8);
            int close = s.lastIndexOf(')');
            if (close < 0) return -1;
            String[] f = s.substring(close + 1).trim().split("\\s+");
            return f.length > 1 ? Integer.parseInt(f[1]) : -1;
        } catch (IOException | NumberFormatException ex) {
            return -1;
        }
    }

    public static String commandLine(File procRoot, int pid) {
        try {
            byte[] b = Files.readAllBytes(new File(new File(procRoot, String.valueOf(pid)), "cmdline").toPath());
            return new String(b, StandardCharsets.UTF_8).replace('\0', ' ').trim();
        } catch (IOException ex) {
            return "";
        }
    }
}
