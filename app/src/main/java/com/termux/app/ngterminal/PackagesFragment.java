package com.termux.app.ngterminal;

import android.content.Context;
import android.os.Bundle;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import com.termux.shared.termux.TermuxConstants;

import java.io.FileReader;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/** NGTerminal: read-only list of installed packages, largest first, total on top. */
public class PackagesFragment extends PreferenceFragmentCompat {

    public static List<DpkgStatusParser.Pkg> load() {
        try (FileReader r = new FileReader(TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/var/lib/dpkg/status")) {
            return DpkgStatusParser.parse(r);
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context ctx = getPreferenceManager().getContext();
        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(ctx);
        setPreferenceScreen(screen);
        List<DpkgStatusParser.Pkg> list = load();
        long kib = 0; for (DpkgStatusParser.Pkg p : list) kib += p.sizeKiB;
        PreferenceCategory header = new PreferenceCategory(ctx);
        header.setTitle(list.size() + " packages · " + SizeFormat.human(kib * 1024));
        screen.addPreference(header);
        for (DpkgStatusParser.Pkg p : list) {
            Preference row = new Preference(ctx);
            row.setTitle(p.name);
            row.setSummary(p.version + " · " + SizeFormat.human(p.sizeKiB * 1024));
            row.setPersistent(false);
            row.setSelectable(false);
            header.addPreference(row);
        }
    }
}
