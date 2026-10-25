package com.termux.app.ngterminal;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * NGTerminal: writes the bundled claude-next over $PREFIX/bin/claude-next when LauncherVersion says so, keeping the
 * old file in the backup dir. Writes a temp file and renames it over the target, so a bash still reading the old
 * launcher (another tab at its picker) keeps its open inode. java.io only: minSdk 24 has no java.nio.file.
 */
public final class LauncherInstaller {
    public enum Result { INSTALLED, UPGRADED, UP_TO_DATE }

    private LauncherInstaller() {}

    /** @param stamp yyyyMMdd-HHmmss, names the backup copy */
    public static Result install(File target, File backupDir, String bundled, String stamp) throws IOException {
        String existing = target.isFile() ? read(target) : null;
        if (!LauncherVersion.needsInstall(existing, LauncherVersion.parse(bundled))) return Result.UP_TO_DATE;
        if (existing != null) {
            if (!backupDir.isDirectory() && !backupDir.mkdirs()) throw new IOException("Cannot create " + backupDir);
            write(new File(backupDir, "claude-next-" + stamp), existing);
        }
        File tmp = new File(target.getPath() + ".ngnew");
        write(tmp, bundled);
        boolean modeSet = tmp.setReadable(false, false) && tmp.setReadable(true, true)
            && tmp.setWritable(false, false) && tmp.setWritable(true, true)
            && tmp.setExecutable(false, false) && tmp.setExecutable(true, true);
        if (!modeSet) throw new IOException("Cannot set mode 0700 on " + tmp);
        if (!tmp.renameTo(target)) throw new IOException("Cannot replace " + target);
        return existing == null ? Result.INSTALLED : Result.UPGRADED;
    }

    static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) > 0) out.write(b, 0, n);
        return out.toByteArray();
    }

    private static String read(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            return new String(readAll(in), StandardCharsets.UTF_8);
        }
    }

    private static void write(File f, String s) throws IOException {
        try (OutputStream out = new FileOutputStream(f)) {
            out.write(s.getBytes(StandardCharsets.UTF_8));
        }
    }
}
