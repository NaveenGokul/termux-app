package com.termux.app.ngterminal;

import android.content.Context;

import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** NGTerminal: installs the launcher bundled in assets/ngterminal/claude-next into $PREFIX/bin (see LauncherInstaller). */
public final class ClaudeLauncher {
    private ClaudeLauncher() {}

    public static LauncherInstaller.Result ensureInstalled(Context context) throws IOException {
        String bundled;
        try (InputStream in = context.getAssets().open("ngterminal/claude-next")) {
            bundled = new String(LauncherInstaller.readAll(in), StandardCharsets.UTF_8);
        }
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        return LauncherInstaller.install(new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "claude-next"),
            new File(TermuxConstants.TERMUX_HOME_DIR_PATH, ".ngterminal/backup"), bundled, stamp);
    }
}
