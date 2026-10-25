package com.termux.app.ngterminal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/** NGTerminal: read-only parser for $PREFIX/var/lib/dpkg/status (installed packages only). */
public final class DpkgStatusParser {
    public static final class Pkg {
        public final String name, version; public final long sizeKiB;
        Pkg(String n, String v, long s) { name = n; version = v; sizeKiB = s; }
    }
    private DpkgStatusParser() {}

    public static List<Pkg> parse(Reader in) {
        List<Pkg> out = new ArrayList<>();
        String name = null, version = "", status = ""; long size = 0;
        try (BufferedReader r = new BufferedReader(in)) {
            for (String line = r.readLine(); ; line = r.readLine()) {
                if (line == null || line.isEmpty()) {
                    if (name != null && status.endsWith(" installed")) out.add(new Pkg(name, version, size));
                    name = null; version = ""; status = ""; size = 0;
                    if (line == null) break;
                    continue;
                }
                if (line.startsWith("Package: ")) name = line.substring(9).trim();
                else if (line.startsWith("Version: ")) version = line.substring(9).trim();
                else if (line.startsWith("Status: ")) status = line.substring(8).trim();
                else if (line.startsWith("Installed-Size: ")) {
                    try { size = Long.parseLong(line.substring(16).trim()); } catch (NumberFormatException e) { size = 0; }
                }
            }
        } catch (IOException e) {
            // unreadable status file -> whatever was parsed so far
        }
        out.sort((a, b) -> Long.compare(b.sizeKiB, a.sizeKiB));
        return out;
    }
}
