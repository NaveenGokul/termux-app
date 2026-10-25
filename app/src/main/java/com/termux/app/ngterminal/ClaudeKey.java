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

    /** Idle = the tab's process is an interactive shell (options only, no script) with no child process, so typed text reaches its prompt. */
    public static boolean isIdleShell(File procRoot, int pid) {
        if (pid <= 0) return false;
        String cmd = ProcessTree.commandLine(procRoot, pid);
        if (cmd.isEmpty()) return false;
        String[] argv = cmd.split(" ");
        String name = argv[0].substring(argv[0].lastIndexOf('/') + 1);
        if (name.startsWith("-")) name = name.substring(1);   // login shells show as "-bash"
        if (!SHELLS.contains(name)) return false;
        for (int i = 1; i < argv.length; i++)                  // options only ("bash -l"): a script or -c command is no prompt
            if (!argv[i].startsWith("-") || argv[i].equals("-c")) return false;
        return ProcessTree.childPids(procRoot, pid).isEmpty();
    }
}
