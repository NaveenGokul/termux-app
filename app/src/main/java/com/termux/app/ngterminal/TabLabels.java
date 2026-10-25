package com.termux.app.ngterminal;

/** NGTerminal: tab label = renamed session name, else terminal title, else "Tab N". */
public final class TabLabels {
    private TabLabels() {}
    public static String labelFor(String sessionName, String terminalTitle, int position1Based) {
        if (sessionName != null && !sessionName.trim().isEmpty()) return sessionName.trim();
        if (terminalTitle != null && !terminalTitle.trim().isEmpty()) return terminalTitle.trim();
        return "Tab " + position1Based;
    }
}
