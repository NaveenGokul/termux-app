package com.termux.app.ngterminal;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** NGTerminal: the "# ngterminal-launcher: N" marker on line 2 of claude-next. 0 = no marker (v14 and older). */
public final class LauncherVersion {
    private static final Pattern MARKER = Pattern.compile("# ngterminal-launcher: (\\d{1,6})");

    private LauncherVersion() {}

    public static int parse(String script) {
        if (script == null) return 0;
        String[] lines = script.split("\n", 3);
        if (lines.length < 2) return 0;
        Matcher m = MARKER.matcher(lines[1].trim());
        return m.matches() ? Integer.parseInt(m.group(1)) : 0;
    }

    /** Install when the file is missing or has an older (or no) marker; a newer hand edit is left alone. */
    public static boolean needsInstall(String existing, int bundled) {
        return existing == null || parse(existing) < bundled;
    }
}
