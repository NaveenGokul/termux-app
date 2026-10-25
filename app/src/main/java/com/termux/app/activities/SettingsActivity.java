package com.termux.app.activities;

import android.content.Context;
import android.os.Bundle;
import android.os.Environment;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.termux.R;
import com.termux.shared.activities.ReportActivity;
import com.termux.shared.file.FileUtils;
import com.termux.shared.models.ReportInfo;
import com.termux.app.models.UserAction;
import com.termux.shared.interact.ShareUtils;
import com.termux.shared.packages.PackageUtils;
import com.termux.shared.settings.preferences.TermuxAPIAppSharedPreferences;
import com.termux.shared.settings.preferences.TermuxFloatAppSharedPreferences;
import com.termux.shared.settings.preferences.TermuxTaskerAppSharedPreferences;
import com.termux.shared.settings.preferences.TermuxWidgetAppSharedPreferences;
import com.termux.shared.termux.AndroidUtils;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxUtils;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        // NGTerminal: M3 collapsing large title
        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.ng_settings_toolbar);
        setSupportActionBar(toolbar);
        setTitle("Settings");
        // NGTerminal: Pixel-style grouped cards on every settings page (root, stock Termux pages, packages)
        getSupportFragmentManager().registerFragmentLifecycleCallbacks(new androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks() {
            @Override
            public void onFragmentViewCreated(@NonNull androidx.fragment.app.FragmentManager fm, @NonNull androidx.fragment.app.Fragment f,
                                              @NonNull android.view.View v, Bundle savedInstanceState) {
                if (!(f instanceof PreferenceFragmentCompat)) return;
                PreferenceFragmentCompat pf = (PreferenceFragmentCompat) f;
                pf.setDivider(null);
                // no item animator: when a switch's summary grows, the rows below used to slide over it; the switch animates itself
                pf.getListView().setItemAnimator(null);
                pf.getListView().addItemDecoration(new com.termux.app.ngterminal.PreferenceCardDecoration(SettingsActivity.this));
            }
        }, true);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings, new RootPreferencesFragment())
                .commit();
        }
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowHomeEnabled(true);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    public static class RootPreferencesFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            Context context = getContext();
            if (context == null) return;

            setPreferencesFromResource(R.xml.root_preferences, rootKey);

            configureTermuxAPIPreference(context);
            configureTermuxFloatPreference(context);
            configureTermuxTaskerPreference(context);
            configureTermuxWidgetPreference(context);
            configureAboutPreference(context);
            configureDonatePreference(context);
            configureNGTerminalPreferences(context); // NGTerminal: after donate, so it can hide it
        }

        /** NGTerminal: Keep awake switch, installed-packages summary, and the NGTerminal version on About. */
        private void configureNGTerminalPreferences(@NonNull Context context) {
            androidx.preference.SwitchPreferenceCompat keepAwake = findPreference("ng_keep_awake");
            if (keepAwake != null) {
                keepAwake.setOnPreferenceChangeListener((p, newValue) -> {
                    android.content.Intent i = new android.content.Intent(context, com.termux.app.TermuxService.class)
                        .setAction(Boolean.TRUE.equals(newValue)
                            ? com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_SERVICE.ACTION_WAKE_LOCK
                            : com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_SERVICE.ACTION_WAKE_UNLOCK);
                    context.startService(i);
                    return true;
                });
            }
            Preference packages = findPreference("ng_packages");
            if (packages != null) {
                new Thread(() -> {
                    java.util.List<com.termux.app.ngterminal.DpkgStatusParser.Pkg> list =
                        com.termux.app.ngterminal.PackagesFragment.load();
                    long kib = 0; for (com.termux.app.ngterminal.DpkgStatusParser.Pkg p : list) kib += p.sizeKiB;
                    String summary = list.size() + " packages · " + com.termux.app.ngterminal.SizeFormat.human(kib * 1024);
                    if (getActivity() != null) getActivity().runOnUiThread(() -> packages.setSummary(summary));
                }).start();
            }
            Preference donate = findPreference("donate");
            if (donate != null) donate.setVisible(false); // NGTerminal: no donate row (user request)
            Preference about = findPreference("about");
            if (about != null) about.setSummary("NGTerminal " + com.termux.BuildConfig.VERSION_NAME + " · based on Termux 0.118.3");
        }

        @Override
        public void onResume() {
            super.onResume();
            androidx.preference.SwitchPreferenceCompat keepAwake = findPreference("ng_keep_awake");
            if (keepAwake != null) keepAwake.setChecked(com.termux.app.TermuxService.isWakeLockHeldStatic());
        }

        private void configureTermuxAPIPreference(@NonNull Context context) {
            Preference termuxAPIPreference = findPreference("termux_api");
            if (termuxAPIPreference != null) {
                TermuxAPIAppSharedPreferences preferences = TermuxAPIAppSharedPreferences.build(context, false);
                // If failed to get app preferences, then likely app is not installed, so do not show its preference
                termuxAPIPreference.setVisible(preferences != null);
            }
        }

        private void configureTermuxFloatPreference(@NonNull Context context) {
            Preference termuxFloatPreference = findPreference("termux_float");
            if (termuxFloatPreference != null) {
                TermuxFloatAppSharedPreferences preferences = TermuxFloatAppSharedPreferences.build(context, false);
                // If failed to get app preferences, then likely app is not installed, so do not show its preference
                termuxFloatPreference.setVisible(preferences != null);
            }
        }

        private void configureTermuxTaskerPreference(@NonNull Context context) {
            Preference termuxTaskerPreference = findPreference("termux_tasker");
            if (termuxTaskerPreference != null) {
                TermuxTaskerAppSharedPreferences preferences = TermuxTaskerAppSharedPreferences.build(context, false);
                // If failed to get app preferences, then likely app is not installed, so do not show its preference
                termuxTaskerPreference.setVisible(preferences != null);
            }
        }

        private void configureTermuxWidgetPreference(@NonNull Context context) {
            Preference termuxWidgetPreference = findPreference("termux_widget");
            if (termuxWidgetPreference != null) {
                TermuxWidgetAppSharedPreferences preferences = TermuxWidgetAppSharedPreferences.build(context, false);
                // If failed to get app preferences, then likely app is not installed, so do not show its preference
                termuxWidgetPreference.setVisible(preferences != null);
            }
        }

        private void configureAboutPreference(@NonNull Context context) {
            Preference aboutPreference = findPreference("about");
            if (aboutPreference != null) {
                aboutPreference.setOnPreferenceClickListener(preference -> {
                    new Thread() {
                        @Override
                        public void run() {
                            String title = "About";

                            StringBuilder aboutString = new StringBuilder();
                            aboutString.append(TermuxUtils.getAppInfoMarkdownString(context, false));

                            String termuxPluginAppsInfo =  TermuxUtils.getTermuxPluginAppsInfoMarkdownString(context);
                            if (termuxPluginAppsInfo != null)
                                aboutString.append("\n\n").append(termuxPluginAppsInfo);

                            aboutString.append("\n\n").append(AndroidUtils.getDeviceInfoMarkdownString(context));
                            aboutString.append("\n\n").append(TermuxUtils.getImportantLinksMarkdownString(context));

                            String userActionName = UserAction.ABOUT.getName();
                            ReportActivity.startReportActivity(context, new ReportInfo(userActionName,
                                TermuxConstants.TERMUX_APP.TERMUX_SETTINGS_ACTIVITY_NAME, title, null,
                                aboutString.toString(), null, false,
                                userActionName,
                                Environment.getExternalStorageDirectory() + "/" +
                                    FileUtils.sanitizeFileName(TermuxConstants.TERMUX_APP_NAME + "-" + userActionName + ".log", true, true)));
                        }
                    }.start();

                    return true;
                });
            }
        }

        private void configureDonatePreference(@NonNull Context context) {
            Preference donatePreference = findPreference("donate");
            if (donatePreference != null) {
                String signingCertificateSHA256Digest = PackageUtils.getSigningCertificateSHA256DigestForPackage(context);
                if (signingCertificateSHA256Digest != null) {
                    // If APK is a Google Playstore release, then do not show the donation link
                    // since Termux isn't exempted from the playstore policy donation links restriction
                    // Check Fund solicitations: https://pay.google.com/intl/en_in/about/policy/
                    String apkRelease = TermuxUtils.getAPKRelease(signingCertificateSHA256Digest);
                    if (apkRelease == null || apkRelease.equals(TermuxConstants.APK_RELEASE_GOOGLE_PLAYSTORE_SIGNING_CERTIFICATE_SHA256_DIGEST)) {
                        donatePreference.setVisible(false);
                        return;
                    } else {
                        donatePreference.setVisible(true);
                    }
                }

                donatePreference.setOnPreferenceClickListener(preference -> {
                    ShareUtils.openURL(context, TermuxConstants.TERMUX_DONATE_URL);
                    return true;
                });
            }
        }
    }

}
