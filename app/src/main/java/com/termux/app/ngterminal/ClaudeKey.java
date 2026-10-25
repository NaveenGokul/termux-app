package com.termux.app.ngterminal;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** NGTerminal: rules for the Claude extra key. Pure Java, unit-tested. */
public final class ClaudeKey {
    /** Typed into the tab: the launcher asks which folder to open and resumes when that folder has sessions. */
    public static final String COMMAND = "claude-next --ng-pick\r";

    private static final Set<String> SHELLS = new HashSet<>(Arrays.asList("bash", "sh", "zsh", "fish", "dash", "ash", "mksh", "login"));

    private ClaudeKey() {}

    /** Idle = the tab's process is a shell with no child process, so typed text reaches its prompt. */
    public static boolean isIdleShell(File procRoot, int pid) {
        if (pid <= 0) return false;
        String cmd = ProcessTree.commandLine(procRoot, pid);
        if (cmd.isEmpty()) return false;
        String exe = cmd.split(" ", 2)[0];
        String name = exe.substring(exe.lastIndexOf('/') + 1);
        if (name.startsWith("-")) name = name.substring(1);   // login shells show as "-bash"
        return SHELLS.contains(name) && ProcessTree.childPids(procRoot, pid).isEmpty();
    }
}
