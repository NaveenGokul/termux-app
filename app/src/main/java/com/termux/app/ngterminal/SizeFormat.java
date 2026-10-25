package com.termux.app.ngterminal;

import java.util.Locale;

/** NGTerminal: human-readable sizes, 1024-based. */
public final class SizeFormat {
    private SizeFormat() {}
    public static String human(long bytes) {
        double kb = bytes / 1024.0, mb = kb / 1024.0, gb = mb / 1024.0;
        if (gb >= 1) return String.format(Locale.US, "%.1f GB", gb);
        if (mb >= 1) return String.format(Locale.US, "%.1f MB", mb);
        return String.format(Locale.US, "%d KB", Math.round(kb));
    }
}
