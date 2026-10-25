# NGTerminal M3 UI — icon keys, tab bar, settings

Status: design approved section by section on 2026-10-25; written spec awaiting the user's review.
Branch: `github-releases/m3-extra-keys` (fork `NaveenGokul/termux-app`), on top of ng.2 (`e998815`).

## 1. Goal

Make NGTerminal (the user's Termux build) look and work like Pixel's Terminal app, in Material 3 (M3)
Expressive, **without breaking anything Termux does**. The user runs all their Claude Code sessions in it, so a
broken build costs them their working environment.

**Done means:**
- the extra keys show Google Material Symbols icons;
- a top tab bar replaces the slide-out session drawer;
- a Pixel-style settings page has Keep awake, an installed-packages list and every stock Termux setting;
- every stage has passed the test checklist on the Pixel 8 before it is installed on the user's phone.

### Non-goals (decided against)
- Recovery actions (reset to initial version, remove backup data): the user declined.
- A RAM cap: Android gives apps no reliable per-app memory limit, and the user dropped it.
- Display resolution settings: dropped.
- Dynamic (wallpaper) colour: the terminal palette is fixed to seed `#6750A4`, and the app chrome must match it.
- Rebasing onto upstream `master`: it carries unreleased runtime changes (the am-socket path move, the command
  runner, properties loading, a new bootstrap). Rejected under the "don't break" rule.

## 2. Invariants: "don't break any Termux things"

1. **Runtime code stays v0.118.3.** Changes are additive UI. Untouched: sessions, `TermuxService`, intents,
   RUN_COMMAND, plugins, `termux.properties` keys, the extra-keys format, storage, bootstrap, `TermuxConstants` paths.
2. **Package `com.termux`, upstream's public `app/dev_keystore.jks` signer (cert SHA-256 `b6da0148…e5e1`), and
   versionCode `1002`.** Each build installs as an update with no data loss, and the official
   `termux-app_v0.118.3+github-debug_arm64-v8a.apk` (kept in Download/) can always be installed over it to roll back.
3. **targetSdk stays 28.** Android 10+ blocks executing files from app data for higher targets, and every Termux
   package depends on that.
4. **The drawer is locked, not removed.** `DrawerLayout` and its code stay. It is locked closed
   (`LOCK_MODE_LOCKED_CLOSED`), so swipes don't open it, but programmatic opens (the `DRAWER` key) still work.
5. **Unknown key names keep today's behaviour.** The icon lookup falls back to the key's normal label.

## 3. Foundation: toolchain upgrade (stage ng.3)

- Copy upstream `master`'s working build setup onto our branch, **build files only**:
  - Gradle 9.2.1 wrapper and Android Gradle Plugin 8.13.2;
  - Java 17;
  - `compileSdkVersion=36` and NDK 29.0.14206865;
  - the AGP-8 `namespace` and `buildFeatures` entries per module.
- Libraries:
  - `com.google.android.material:material:1.14.0`, the latest stable;
  - AppCompat and AndroidX Preference at the versions Material 1.14 requires.
  - If 1.14.0 fails, step back one release at a time and write the reason beside the version.
- Fix only the compile errors the new SDK and AGP raise; runtime behaviour must not change.
- **Acceptance:** looks and behaves identical to ng.2 and passes the full checklist (§8).

## 4. Key icons + keep-awake key (stage ng.4)

- `ExtraKeysView` chooses an icon by key name; keys without an icon keep their text. Icons are
  **Material Symbols Rounded** (Google, Apache 2.0) vector drawables, tinted with the existing text colours
  (`#E6E0E9`, or `#36275D` when latched).

  | Key | Shown as |
  |---|---|
  | `ESC`, `/`, `-` | text in Roboto |
  | `HOME` / `END` | `first_page` / `last_page` |
  | `UP` `DOWN` `LEFT` `RIGHT` | `keyboard_arrow_up/down/left/right` (chevrons, option A) |
  | `TAB` | `keyboard_tab` |
  | `CTRL` | text "Ctrl" |
  | macro `CTRL j` (new line) | `keyboard_return` |
  | `KEYBOARD` | `keyboard` |
  | `WAKELOCK` (new) | `coffee` |

- **New key `WAKELOCK`:**
  - `TermuxTerminalExtraKeys` gets one new case beside `KEYBOARD`/`DRAWER`. It sends `TermuxService`'s existing
    `ACTION_WAKE_LOCK` / `ACTION_WAKE_UNLOCK` intent, the same one the notification button uses.
  - The key is drawn latched (primary `#CFBDFE`) while the wakelock is held. It re-reads that state on resume and
    on service notification updates, so the key, the notification and `termux-wake-lock` agree.
- The user's `~/.termux/termux.properties` replaces `DRAWER` with `WAKELOCK`.

## 5. Tab bar (stage ng.5)

- **Layout:** a new top bar in `activity_termux.xml`, above the terminal, coloured surface-container. Existing views
  are not modified. It contains:
  - a horizontally scrolling row of pill tabs (selected tab = secondary-container);
  - on the right, `add` and `settings` (Material Symbols) buttons.
- **Tab label:** the session name if it was renamed, else the terminal title if one is set, else "Tab N" (N = the tab's 1-based position). Each tab
  has a `close` icon.
- **Actions** (all reuse existing Termux code paths):

  | Gesture | Effect |
  |---|---|
  | tap tab | switch to that session (same call as the drawer list) |
  | long-press tab | rename dialog (existing `renameSession`) |
  | tap `add` | new session (existing "New session") |
  | long-press `add` | failsafe session (existing long-press behaviour) |
  | `close` on an idle tab | finish the session immediately |
  | `close` while busy | M3 dialog "`<command>` is still running. Close tab?", then finish |
  | last tab closed | unchanged Termux behaviour |
  | `settings` | opens `SettingsActivity` |

- **Busy detection:** the tab is busy if its shell pid has any child process. Found by scanning `/proc/*/stat` for
  `ppid == shell pid` (same uid, readable); the dialog shows that child's `/proc/<pid>/cmdline`. This doesn't
  depend on `/proc/<pid>/task/*/children`, which some kernels lack.
- **Sync:** a new `TermuxSessionsTabBarController` (one job: render tabs plus the click handling) is rebuilt from
  `termuxSessionListNotifyUpdated()` and on current-session change, the same events that refresh the drawer.
  Sessions created by plugins or RUN_COMMAND therefore appear as tabs automatically.
- **`TermuxActivity` changes:** create the controller, lock the drawer, forward the two events. A few lines only.

## 6. Settings (stage ng.6)

- `SettingsActivity` gets the M3 theme:
  - a large collapsing "Settings" title;
  - list rows with leading Material Symbols icons;
  - `MaterialSwitch` via one shared `widgetLayout` (`@android:id/switch_widget`).
- **New rows** at the top of `root_preferences.xml`. Existing entries are unchanged and keep their order below.

  | Row | Icon | Behaviour |
  |---|---|---|
  | Keep awake | `coffee` | Switch. Sends the same wakelock intents as §4 and reflects the live state on resume. |
  | Installed packages | `package_2` | Summary "N packages · X GB". Opens `PackagesFragment`: read-only list of name, version and size, largest first, total at top. |
  | Termux (stock pages) | `terminal` | Existing Debugging / Terminal I/O / Terminal view pages, restyled only |
  | Plugin pages | as today | Shown only when the plugin is installed (unchanged logic) |
  | About | `info` | "NGTerminal <versionName> · based on Termux 0.118.3" |

- **Package data:** parse `$PREFIX/var/lib/dpkg/status` directly. A record counts if its `Status` ends in
  `installed`; read `Package`, `Version` and `Installed-Size` (KiB). No shell command is run. The parse happens off
  the main thread.

## 7. Versioning, naming, delivery

- Stages ship as `versionName` `0.118.3-ng.3` … `0.118.3-ng.6`; versionCode stays `1002` (§2).
- CI (free, public fork) produces `NGTerminal_<IST date>_v<versionName>.apk` + `SHA256SUMS.txt`.
- Each APK is verified before use: sha256, signer `b6da0148…`, package/versionCode/versionName/label (`aapt dump
  badging`). Only then is it copied to `Download/`.
- Every stage gets a `RELEASE_NOTES.md` entry in `Termux_M3/`, and the vault session log is updated.

## 8. Test gate: Pixel 8 first, then the user's phone

Test device: **Pixel 8 (`shiba`), Android 17 / SDK 37, arm64**, reached over wireless adb (`10.151.80.205`). It had
no Termux, so there is no data at risk. Builds are `application-debuggable`, so `adb shell run-as com.termux …`
runs commands inside the app.

**Every stage, on the Pixel (screenshots saved to `Download/` so the user can see them):**
1. Install the previous stage first, then the new APK **as an update**: the same path the user's phone takes.
2. Bootstrap and shell work; `pkg install` works (e.g. `jq`).
3. Multiple sessions; the extra keys produce the right input (e.g. arrows move the cursor).
4. `termux-open-url https://example.com` opens the browser (the `/login` bridge depends on it).
5. Termux:API (GitHub build, same key) answers `termux-battery-status`.
6. `termux-reload-settings` applies `termux.properties` changes.
7. Keep-awake toggles both ways.
8. The Claude runtime: copy `~/.cc-min`, then `claude --version` through proot (no login needed).
9. The stage's own checks (§4–§6 Pixel tests).

**Only after all pass:** the user installs it on their phone (close Claude sessions first; the update restarts
Termux). **Rollback:** install the official APK from `Download/` (same signer and versionCode, so no wipe).

## 9. Risks

| Risk | Mitigation |
|---|---|
| Toolchain jump breaks compile in many files | ng.3 isolates it; fix compile errors only; upstream master is the working reference |
| Material 1.14 restyles existing Termux dialogs unexpectedly | Pixel screenshots of the stock dialogs in ng.3; theme changes limited to `TermuxActivity` + `SettingsActivity` |
| Android 17 (Pixel) vs 16 (user's phone) behave differently | Results on the Pixel are necessary, not sufficient; the user's first run after install re-checks items 2–4 |
| Busy detection misses a process | A false "idle" only skips the dialog, same as stock Termux today |
| A stage misbehaves on the user's phone | Official APK rollback; data kept |
