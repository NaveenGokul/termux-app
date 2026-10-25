# NGTerminal M3 UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give NGTerminal (the user's Termux build) Material Symbols key icons, a keep-awake key, a Pixel-style tab bar
and Pixel-style settings, without changing any Termux runtime behaviour.

**Architecture:** Termux 0.118.3 runtime code stays as it is. The build toolchain is lifted to upstream master's
(Gradle 9.2.1 / AGP 8.13.2 / SDK 36) so Material 1.14.0 can be used. New UI is additive:
- pure-Java helpers (`com.termux.app.ngterminal.*`) are covered by JUnit;
- views get M3 styling through their own theme wrapper, so the framework-themed `TermuxActivity` is untouched;
- every stage is CI-built on the public fork and must pass a scripted Pixel 8 check before it reaches the user's
  phone.

**Tech Stack:**
- Java 8 source level, Android SDK 36, minSdk 24, targetSdk 28;
- Gradle 9.2.1, AGP 8.13.2, NDK 29.0.14206865;
- Material Components 1.14.0, AndroidX Preference 1.2.1;
- JUnit 4.13.2;
- GitHub Actions; adb (Termux `android-tools`) for the Pixel harness.

**Spec:** `docs/superpowers/specs/2026-10-25-ngterminal-m3-ui-design.md`

## Global Constraints

- Package `com.termux`; signer `app/dev_keystore.jks` (cert SHA-256 `b6da01480eefd5fbf2cd3771b8d1021ec791304bdd6c4bf41d3faabad48ee5e1`). **Never** use upstream master's `testkey_untrusted.jks`.
- `versionCode 1002`, always. `versionName "0.118.3-ng.N"`, with nothing after the closing quote (the CI regex is `\s+versionName "([^"]+)"$`).
- `targetSdkVersion=28` and `minSdkVersion=24`, unchanged.
- Bootstrap stays `2025.03.28-r1+apt-android-7` with its current four checksums. **Never** copy master's 2026.02.12 bootstrap.
- `androidx.window:window:1.0.0-alpha09` stays (its comment says newer versions break `ViewUtils` in this code).
- Do not add `com.termux:termux-am-library` or master's `termux-shared/src/main/cpp` (master-only runtime).
- Runtime code untouched: sessions, `TermuxService` behaviour, intents, RUN_COMMAND, plugins, `termux.properties` keys, the extra-keys format, storage, bootstrap, `TermuxConstants` paths. Only additive methods/hooks named in this plan.
- `TermuxActivity`'s theme (`Theme.Termux`, framework Material) is not changed. M3 views get `ContextThemeWrapper(..., R.style.Theme_NGTerminal_M3)`.
- Colours are M3 dark roles from seed `#6750A4` (material-color-utilities 0.3.0):
  - surface `#141218`, surface-container `#211F26`, surface-container-high `#2B292F`;
  - secondary-container `#4A4458`, on-secondary-container `#E8DEF8`;
  - on-surface `#E6E0E9`, on-surface-variant `#CAC4CF`;
  - primary `#CFBDFE`, on-primary `#36275D`.
- Icons are Material Symbols **Rounded**, from `github.com/google/material-design-icons` (`symbols/web/<name>/materialsymbolsrounded/<name>_24px.svg`), Apache 2.0.
- APK artifact: `NGTerminal_<IST yyyymmdd>_v<versionName>.apk` + `SHA256SUMS.txt` (existing CI step).
- CI is free (public fork). JVM unit tests run in CI (`./gradlew test`), because the phone has no x86 NDK.
- No stage is installed on the user's phone until `pixel_check_v1.sh` passes for it on the Pixel 8 (`10.151.80.205`).

## Review Focus

1. **A process vanishes between listing `/proc` and reading its `stat`** (normal on a busy phone). Expected: the scan
   skips it and never throws → Task 5 test `skipsVanishedAndGarbledEntries`.
2. **dpkg records that aren't installed** (`deinstall ok config-files`) **or have no `Installed-Size`.** Expected: the
   first are excluded, the second count as 0 KiB → Task 7 tests `excludesNotInstalled`, `missingSizeIsZero`.
3. **Very long tab names** (e.g. a renamed tab or a long terminal title). Expected: the label is ellipsized, and
   `add`/`settings` stay visible → Task 6 Pixel check `long_title`.
4. **Closing the only tab.** Expected: stock Termux behaviour (the activity finishes), and relaunching gives a fresh
   tab → Task 6 Pixel check `close_last`.
5. **Tapping the keep-awake key before the service is bound** (cold start). Expected: no crash; the tap still toggles,
   because it goes through `startService` → Task 4 null-guard plus Pixel check `wake_cold`.

---

## File Structure

| File | Responsibility | Task |
|---|---|---|
| `Termux_M3/tools/pixel_check_v1.sh` (outside repo) | Pixel 8 install-as-update + checks + screenshots | 1 |
| `build.gradle`, `gradle.properties`, `gradle/wrapper/*`, `gradlew*`, `*/build.gradle`, `*/AndroidManifest.xml`, `.github/workflows/*.yml` | toolchain | 2 |
| `termux-shared/src/main/res/drawable/ic_ng_key_*.xml` | Material Symbols vectors for keys | 3 |
| `termux-shared/.../extrakeys/ExtraKeyIcons.java` + test | key name → icon drawable id | 3 |
| `termux-shared/.../extrakeys/ExtraKeysView.java` | draw icon keys; `setKeyLatched()` | 3, 4 |
| `app/.../TermuxService.java` | `isWakeLockHeld()`, notify client on notification update | 4 |
| `app/.../terminal/io/TermuxTerminalExtraKeys.java` | `WAKELOCK` key | 4 |
| `app/.../terminal/TermuxTerminalSessionClient.java` | forward wake-lock + session-list events | 4, 6 |
| `app/.../ngterminal/TabLabels.java`, `ProcessTree.java` + tests | tab label, busy detection | 5 |
| `app/src/main/res/layout/ng_tab_bar.xml`, `ng_tab_item.xml`, `res/drawable/ng_*`, `res/values/ng_themes.xml` | tab bar views | 6 |
| `app/.../ngterminal/TermuxSessionsTabBarController.java` | render tabs, handle gestures | 6 |
| `app/.../TermuxActivity.java`, `res/layout/activity_termux.xml` | host tab bar, lock drawer | 6 |
| `app/.../ngterminal/DpkgStatusParser.java`, `SizeFormat.java` + tests | package data | 7 |
| `app/.../activities/SettingsActivity.java`, `res/layout/activity_settings.xml`, `res/xml/root_preferences.xml`, `res/layout/ng_preference_switch.xml`, `app/.../ngterminal/PackagesFragment.java` | settings | 8 |

`app/...` means `app/src/main/java/com/termux/app`; `termux-shared/...` means
`termux-shared/src/main/java/com/termux/shared/terminal/io`.

---

### Task 1: Pixel test harness, proven on the current ng.2

**Files:**
- Create: `/storage/emulated/0/Download/a_Claude_Things/Termux_M3/tools/pixel_check_v1.sh`

**Interfaces:**
- Produces: `pixel_check_v1.sh <stage-label> <new.apk> [<previous.apk>]`. It prints `PASS <check>` / `FAIL <check>`
  lines and a final `RESULT: PASS|FAIL`, and saves screenshots to `/storage/emulated/0/Download/NGTerminal_pixel/<stage-label>/`.
  Later tasks add stage checks by setting `EXTRA_CHECKS=<file>`, which is sourced at the end.

- [ ] **Step 1: Write the harness**

```bash
#!/data/data/com.termux/files/usr/bin/bash
# NGTerminal Pixel 8 check: install-as-update + core Termux checks + screenshots.
# Usage: pixel_check_v1.sh <stage> <new.apk> [<previous.apk>]   (EXTRA_CHECKS=file sources stage checks)
set -u
S=${PIXEL:-10.151.80.205:33279}; STAGE=$1; NEW=$2; PREV=${3:-}
OUT=/storage/emulated/0/Download/NGTerminal_pixel/$STAGE; mkdir -p "$OUT"
F=/data/data/com.termux/files; FAILS=0
a()   { timeout 60 adb -s "$S" shell "$@"; }
ok()  { echo "PASS $1"; }
bad() { echo "FAIL $1: $2"; FAILS=$((FAILS+1)); }
shot(){ a screencap -p /sdcard/ng_shot.png && adb -s "$S" pull -q /sdcard/ng_shot.png "$OUT/$1.png"; }
# run a bash script inside NGTerminal (debuggable build -> run-as works)
rx()  { printf '%s\n' "$1" | timeout ${2:-300} adb -s "$S" exec-in run-as com.termux sh -c \
        "cat > $F/home/.ng_cmd.sh; $F/usr/bin/env -i HOME=$F/home PREFIX=$F/usr PATH=$F/usr/bin TMPDIR=$F/usr/tmp \
         LANG=en_US.UTF-8 LD_PRELOAD=$F/usr/lib/libtermux-exec.so $F/usr/bin/bash $F/home/.ng_cmd.sh"; }
launch(){ a am start -n com.termux/com.termux.app.TermuxActivity >/dev/null; }

timeout 20 adb connect "$S" >/dev/null
[ "$(a getprop ro.product.model)" = "Pixel 8" ] || { echo "RESULT: FAIL (Pixel 8 not connected at $S)"; exit 1; }

# 1. install path the user's phone takes: previous build first, then the new one as an UPDATE
if [ -n "$PREV" ] && ! a pm list packages | grep -qx package:com.termux; then
  adb -s "$S" install "$PREV" >/dev/null && ok install_prev || bad install_prev "adb install failed"
  launch
fi
adb -s "$S" install -r "$NEW" | grep -q Success && ok install_update || bad install_update "update refused"
vn=$(a dumpsys package com.termux | grep -m1 versionName | cut -d= -f2)
vc=$(a dumpsys package com.termux | grep -m1 versionCode | sed 's/.*versionCode=\([0-9]*\).*/\1/')
[ "$vc" = 1002 ] && ok "versionCode=1002 versionName=$vn" || bad versionCode "$vc"

# 2. bootstrap + shell
launch; for i in $(seq 1 60); do a run-as com.termux ls $F/usr/bin/bash >/dev/null 2>&1 && break; sleep 2; done
[ "$(rx 'echo shell-ok')" = shell-ok ] && ok shell || bad shell "no shell"
rx 'pkg install -y jq >/dev/null 2>&1; jq -n "1+1"' | grep -qx 2 && ok pkg_install || bad pkg_install "jq failed"

# 3. termux-open-url (the /login browser bridge depends on it)
rx 'termux-open-url https://example.com' >/dev/null; sleep 4
a dumpsys activity activities | grep -m1 -E 'ResumedActivity' | grep -qiE 'chrome|browser' && ok open_url || bad open_url "browser not foreground"
shot open_url; launch; sleep 2

# 4. Termux:API (GitHub build, same key)
API=/storage/emulated/0/Download/termux-api-app_v0.53.0+github.debug.apk
a pm list packages | grep -qx package:com.termux.api || adb -s "$S" install "$API" >/dev/null
rx 'pkg install -y termux-api >/dev/null 2>&1; timeout 30 termux-battery-status' | grep -q percentage && ok termux_api || bad termux_api "no battery json"

# 5. keep-awake both ways (service wakelock visible in dumpsys power)
rx 'termux-wake-lock'; sleep 2; a dumpsys power | grep -q 'termux:service-wakelock' && ok wake_on || bad wake_on "not held"
rx 'termux-wake-unlock'; sleep 2; a dumpsys power | grep -q 'termux:service-wakelock' && bad wake_off "still held" || ok wake_off

# 6. termux-reload-settings applies properties
rx 'mkdir -p ~/.termux; echo "extra-keys-text-all-caps = false" > ~/.termux/termux.properties; termux-reload-settings' >/dev/null && ok reload_settings || bad reload_settings "failed"

# 7. Claude runtime (proot + glibc rootfs), no login needed
if ! rx 'test -x ~/.cc-min/claude && echo y' | grep -qx y; then
  tar -C "$HOME" -cf - .cc-min/claude .cc-min/lib .cc-min/etc .cc-min/bin .cc-min/usr | \
    timeout 600 adb -s "$S" exec-in run-as com.termux sh -c "cd $F/home && tar -xf -"
fi
rx 'pkg install -y proot >/dev/null 2>&1; env -u LD_PRELOAD proot -r ~/.cc-min -b /proc -b /dev -b /sys -b /system -b /apex -b /data/data/com.termux/files ~/.cc-min/claude --version' 600 \
  | grep -q 'Claude Code' && ok claude_runtime || bad claude_runtime "proot run failed"

launch; sleep 2; shot main
[ -n "${EXTRA_CHECKS:-}" ] && . "$EXTRA_CHECKS"
[ $FAILS -eq 0 ] && echo "RESULT: PASS ($STAGE)" || echo "RESULT: FAIL ($FAILS) ($STAGE)"
```

- [ ] **Step 2: Prove the harness on a known build (lesson: reproduce a known-green result before trusting a harness)**

Run:
```bash
chmod +x /storage/emulated/0/Download/a_Claude_Things/Termux_M3/tools/pixel_check_v1.sh
/storage/emulated/0/Download/a_Claude_Things/Termux_M3/tools/pixel_check_v1.sh baseline-ng2 \
  /storage/emulated/0/Download/NGTerminal_20261025_v0.118.3-ng.2.apk \
  /storage/emulated/0/Download/termux-app_v0.118.3+github-debug_arm64-v8a.apk
```
Expected: the official build installs first, then ng.2 as an update, with every check `PASS` and `RESULT: PASS (baseline-ng2)`.
If a check fails on ng.2 (which the user runs fine), the **harness** is wrong. Fix the harness, not the app, and
re-run until it passes.

- [ ] **Step 3: Mutation-check one check**

Run the harness with `PIXEL` pointing at a wrong port (`PIXEL=10.151.80.205:1`). Expected: `RESULT: FAIL (Pixel 8 not
connected …)`. That shows a failure is reported rather than swallowed.

- [ ] **Step 4: Record**

Add a `Termux_M3/RELEASE_NOTES.md` entry "Pixel harness v1: baseline ng.2 PASS". Copy the baseline screenshots path
into the vault session log.

---

### Task 2: Toolchain upgrade (ng.3), runtime unchanged

**Files:**
- Modify: `gradle/wrapper/gradle-wrapper.properties`, `gradle/wrapper/gradle-wrapper.jar`, `gradlew`, `gradlew.bat` (copied from upstream master)
- Modify: `build.gradle`, `gradle.properties`, `app/build.gradle`, `termux-shared/build.gradle`, `terminal-emulator/build.gradle`, `terminal-view/build.gradle`
- Modify: `app/src/main/AndroidManifest.xml`, `termux-shared/src/main/AndroidManifest.xml`, `terminal-emulator/src/main/AndroidManifest.xml`, `terminal-view/src/main/AndroidManifest.xml` (remove `package=`)
- Modify: `.github/workflows/debug_build.yml`, `.github/workflows/run_tests.yml`

**Interfaces:**
- Produces: a build where `com.google.android.material:material:1.14.0` and `androidx.preference:preference:1.2.1` are on the `app` compile classpath, used by Tasks 6 and 8.

- [ ] **Step 1: Wrapper**

```bash
cd /storage/emulated/0/Download/a_Claude_Things/Termux_M3/repo
for f in gradle/wrapper/gradle-wrapper.properties gradle/wrapper/gradle-wrapper.jar gradlew gradlew.bat; do
  gh api "repos/termux/termux-app/contents/$f?ref=master" --jq .download_url | xargs curl -fsSL -o "$f"; done
chmod +x gradlew; grep distributionUrl gradle/wrapper/gradle-wrapper.properties   # expect gradle-9.2.1-bin.zip
```

- [ ] **Step 2: Root `build.gradle` and `gradle.properties`**

In `build.gradle`, change the classpath line to `classpath "com.android.tools.build:gradle:8.13.2"`, and delete the
`task clean(type: Delete) { delete rootProject.buildDir }` block (Gradle 9's base plugin already provides `clean`).

In `gradle.properties`, change only these lines:
```
org.gradle.jvmargs=-Xmx2048M
ndkVersion=29.0.14206865
compileSdkVersion=36
```
(`minSdkVersion=24` and `targetSdkVersion=28` stay.)

- [ ] **Step 3: Namespaces, and manifests without `package=`**

In each module's `android {}` block add the namespace **equal to its current manifest package** (so `R` and
`BuildConfig` class names don't change):
- `app/build.gradle`: `namespace "com.termux"`
- `termux-shared/build.gradle`: `namespace "com.termux.shared"`
- `terminal-emulator/build.gradle`: `namespace "com.termux.terminal"`
- `terminal-view/build.gradle`: `namespace "com.termux.view"`

Then remove the `package="…"` attribute from the four `AndroidManifest.xml` files. For example,
`terminal-emulator/src/main/AndroidManifest.xml` becomes `<manifest>` … and `app`'s `package="com.termux"` line is
deleted. The `applicationId "com.termux"` in `app/build.gradle` stays.

- [ ] **Step 4: AGP-8 syntax in module files**

Apply exactly what upstream master changed, with nothing more:
- `app/build.gradle`:
  - `lintOptions {` → `lint {`;
  - add `buildFeatures { buildConfig true }` inside `android {}`.
- `termux-shared`, `terminal-emulator`, `terminal-view`:
  - `classifier "sources"` → `archiveClassifier = "sources"`;
  - `from components.release` → `from components.default`;
  - add inside `android {}`:
    ```groovy
    publishing {
        multipleVariants {
            withSourcesJar()
            withJavadocJar()
            allVariants()
        }
    }
    ```
- `termux-shared/build.gradle`: add `ndkVersion = System.getenv("JITPACK_NDK_VERSION") ?: project.properties.ndkVersion` inside `android {}`, as master does. **Do not** add master's `externalNativeBuild` or `termux-am-library`.

- [ ] **Step 5: Dependencies, at the latest stable versions with held-back reasons in comments**

`termux-shared/build.gradle`:
```groovy
implementation "androidx.appcompat:appcompat:1.7.1"
implementation "androidx.annotation:annotation:1.9.1"
implementation "androidx.core:core:1.17.0"
implementation "com.google.android.material:material:1.14.0"
// Do not increment version higher than 1.0.0-alpha09 since it will break ViewUtils and needs to be looked into
// noinspection GradleDependency
implementation "androidx.window:window:1.0.0-alpha09"
```
`app/build.gradle`:
```groovy
implementation "androidx.annotation:annotation:1.9.1"
implementation "androidx.core:core:1.17.0"
implementation "androidx.drawerlayout:drawerlayout:1.2.0"
implementation "androidx.preference:preference:1.2.1"
implementation "com.google.android.material:material:1.14.0" // NGTerminal: tab bar + settings (M3)
```
Before writing, confirm each version is the newest on `https://dl.google.com/android/maven2/<group path>/<artifact>/maven-metadata.xml`
(`<release>`). If one is newer, use it.

- [ ] **Step 6: versionName and CI workflows**

In `app/build.gradle`, change the version line to `versionName "0.118.3-ng.3"`, with nothing after the closing quote.

`.github/workflows/debug_build.yml`: after `actions/checkout`, add:
```yaml
      - name: Setup java 17
        uses: actions/setup-java@v5
        with:
          distribution: 'temurin'
          java-version: '17'
```
`.github/workflows/run_tests.yml`: add `- 'github-releases/**'` under `on.push.branches`, plus the same `Setup java 17`
step.

- [ ] **Step 7: Push and watch both runs (by run ID)**

```bash
git add -A && git commit -m "build: toolchain to Gradle 9.2.1 / AGP 8.13.2 / SDK 36 / Material 1.14.0; runtime unchanged (ng.3)"
git push origin github-releases/m3-extra-keys
gh run list -R NaveenGokul/termux-app --branch github-releases/m3-extra-keys --limit 2 --json databaseId,name,headSha
gh run watch <BUILD_RUN_ID> -R NaveenGokul/termux-app --exit-status
gh run watch <TEST_RUN_ID>  -R NaveenGokul/termux-app --exit-status
```
Expected: both succeed. On a failure, read `gh run view <ID> --log-failed`:
- fix **compile/config errors only**;
- a dependency that fails → step back one release and add a `// held at X: <reason>` comment;
- never change runtime code to get a green build.

- [ ] **Step 8: Verify the APK**

```bash
SCRATCH=/tmp/claude-$(id -u)/ngapk && mkdir -p $SCRATCH && cd $SCRATCH && gh run download <BUILD_RUN_ID> -R NaveenGokul/termux-app -n NGTerminal_<date>_v0.118.3-ng.3.apk
sha256sum -c SHA256SUMS.txt
apksigner verify --print-certs NGTerminal_*.apk | grep -i 'SHA-256'   # b6da0148…e5e1
aapt dump badging NGTerminal_*.apk | grep -E "^package:|application-label"  # com.termux, 1002, 0.118.3-ng.3, NGTerminal
unzip -l NGTerminal_*.apk | grep -c 'assets/bootstrap\|lib/arm64-v8a/libtermux-bootstrap.so'  # bootstrap still embedded (>=1)
```

- [ ] **Step 9: Pixel gate**

```bash
tools/pixel_check_v1.sh ng3 $SCRATCH/NGTerminal_*ng.3.apk /storage/emulated/0/Download/NGTerminal_20261025_v0.118.3-ng.2.apk
```
Expected: `RESULT: PASS (ng3)`. Also compare `Download/NGTerminal_pixel/ng3/main.png` with `baseline-ng2/main.png`;
the UI should be identical.

- [ ] **Step 10: Release**

Copy the APK to `Download/`. Add a `RELEASE_NOTES.md` entry (what/why/files/breaking=no). Tell the user to install it
(close Claude sessions first; it installs as an update).

---

### Task 3: Material Symbols on the extra keys

**Files:**
- Create: `termux-shared/src/main/res/drawable/ic_ng_key_{first_page,last_page,keyboard_arrow_up,keyboard_arrow_down,keyboard_arrow_left,keyboard_arrow_right,keyboard_tab,keyboard_return,keyboard,coffee}.xml`
- Create: `termux-shared/src/main/java/com/termux/shared/terminal/io/extrakeys/ExtraKeyIcons.java`
- Create: `termux-shared/src/test/java/com/termux/shared/terminal/io/extrakeys/ExtraKeyIconsTest.java`
- Modify: `termux-shared/build.gradle` (add `testImplementation "junit:junit:4.13.2"` if absent)
- Modify: `termux-shared/src/main/java/com/termux/shared/terminal/io/extrakeys/ExtraKeysView.java`

**Interfaces:**
- Produces: `public static int ExtraKeyIcons.iconFor(String key)`, which returns a drawable res id or `0` (no icon → text).
- Produces: `ExtraKeysView.styleKey(View, boolean pressed, boolean latched)` now draws the key's icon too (icon id in view tag `R.id.ng_key_icon`).

- [ ] **Step 1: Generate the vector drawables from Google's SVGs**

```bash
cd /storage/emulated/0/Download/a_Claude_Things/Termux_M3/repo
python3 - <<'EOF'
import re, urllib.request
names = ["first_page","last_page","keyboard_arrow_up","keyboard_arrow_down","keyboard_arrow_left",
         "keyboard_arrow_right","keyboard_tab","keyboard_return","keyboard","coffee"]
base = "https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/{0}/materialsymbolsrounded/{0}_24px.svg"
for n in names:
    svg = urllib.request.urlopen(base.format(n), timeout=30).read().decode()
    d = re.search(r'd="([^"]+)"', svg).group(1)
    assert 'viewBox="0 -960 960 960"' in svg, n
    xml = f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Material Symbols Rounded "{n}" (Google, Apache 2.0). Tinted at runtime by ExtraKeysView. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="960" android:viewportHeight="960">
    <group android:translateY="960">
        <path android:fillColor="#FFFFFFFF" android:pathData="{d}"/>
    </group>
</vector>
'''
    open(f"termux-shared/src/main/res/drawable/ic_ng_key_{n}.xml", "w").write(xml)
print("ok")
EOF
ls termux-shared/src/main/res/drawable/ic_ng_key_*.xml | wc -l    # expect 10
```

- [ ] **Step 2: Write the failing test**

```java
package com.termux.shared.terminal.io.extrakeys;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.termux.shared.R;
import org.junit.Test;

public class ExtraKeyIconsTest {
    @Test public void mapsSpecSpecifiedKeys() {
        assertEquals(R.drawable.ic_ng_key_first_page, ExtraKeyIcons.iconFor("HOME"));
        assertEquals(R.drawable.ic_ng_key_last_page, ExtraKeyIcons.iconFor("END"));
        assertEquals(R.drawable.ic_ng_key_keyboard_arrow_up, ExtraKeyIcons.iconFor("UP"));
        assertEquals(R.drawable.ic_ng_key_keyboard_arrow_down, ExtraKeyIcons.iconFor("DOWN"));
        assertEquals(R.drawable.ic_ng_key_keyboard_arrow_left, ExtraKeyIcons.iconFor("LEFT"));
        assertEquals(R.drawable.ic_ng_key_keyboard_arrow_right, ExtraKeyIcons.iconFor("RIGHT"));
        assertEquals(R.drawable.ic_ng_key_keyboard_tab, ExtraKeyIcons.iconFor("TAB"));
        assertEquals(R.drawable.ic_ng_key_keyboard_return, ExtraKeyIcons.iconFor("CTRL j"));
        assertEquals(R.drawable.ic_ng_key_keyboard, ExtraKeyIcons.iconFor("KEYBOARD"));
        assertEquals(R.drawable.ic_ng_key_coffee, ExtraKeyIcons.iconFor("WAKELOCK"));
    }
    @Test public void textKeysAndUnknownKeysHaveNoIcon() {
        for (String k : new String[]{"ESC", "/", "-", "CTRL", "ALT", "DRAWER", "PGUP", "x", "", null})
            assertEquals("key " + k, 0, ExtraKeyIcons.iconFor(k));
    }
    @Test public void caseSensitiveLikeTermuxKeyNames() {
        assertNotEquals(0, ExtraKeyIcons.iconFor("UP"));
        assertEquals(0, ExtraKeyIcons.iconFor("up"));
    }
}
```

- [ ] **Step 3: Run it to watch it fail (CI)**

Commit the test only, push, and watch the `Unit tests` run by ID. Expected: FAIL, `cannot find symbol: ExtraKeyIcons`.

- [ ] **Step 4: Implement**

```java
package com.termux.shared.terminal.io.extrakeys;

import androidx.annotation.DrawableRes;
import com.termux.shared.R;
import java.util.HashMap;
import java.util.Map;

/** NGTerminal: Material Symbols Rounded icon per extra-key name. 0 = no icon (draw the text label as before). */
public final class ExtraKeyIcons {
    private static final Map<String, Integer> ICONS = new HashMap<>();
    static {
        ICONS.put("HOME", R.drawable.ic_ng_key_first_page);
        ICONS.put("END", R.drawable.ic_ng_key_last_page);
        ICONS.put("UP", R.drawable.ic_ng_key_keyboard_arrow_up);
        ICONS.put("DOWN", R.drawable.ic_ng_key_keyboard_arrow_down);
        ICONS.put("LEFT", R.drawable.ic_ng_key_keyboard_arrow_left);
        ICONS.put("RIGHT", R.drawable.ic_ng_key_keyboard_arrow_right);
        ICONS.put("TAB", R.drawable.ic_ng_key_keyboard_tab);
        ICONS.put("CTRL j", R.drawable.ic_ng_key_keyboard_return); // the user's new-line macro
        ICONS.put("KEYBOARD", R.drawable.ic_ng_key_keyboard);
        ICONS.put("WAKELOCK", R.drawable.ic_ng_key_coffee);
    }
    private ExtraKeyIcons() {}

    @DrawableRes
    public static int iconFor(String key) {
        if (key == null) return 0;
        Integer id = ICONS.get(key);
        return id == null ? 0 : id;
    }
}
```

- [ ] **Step 5: Draw icons in `ExtraKeysView`**

Add an id resource `termux-shared/src/main/res/values/ng_ids.xml`:
```xml
<resources><item name="ng_key_icon" type="id"/></resources>
```
Add imports `android.graphics.drawable.Drawable`, `android.graphics.drawable.LayerDrawable`, `android.view.Gravity`,
`androidx.core.content.ContextCompat`. In `reload()`, right after `button.setPadding(0, 0, 0, 0);`, add:
```java
                int icon = ExtraKeyIcons.iconFor(buttonInfo.getKey());
                if (icon != 0) {
                    button.setTag(R.id.ng_key_icon, icon);
                    button.setText("");                                   // the icon replaces the text label
                    button.setContentDescription(buttonInfo.getKey());    // accessibility + Pixel harness lookup
                } else {
                    button.setContentDescription(buttonInfo.getKey());
                }
```
Replace the body of `styleKey` with:
```java
    public void styleKey(View view, boolean pressed, boolean latched) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(latched ? M3_KEY_LATCHED_COLOR : pressed ? mButtonActiveBackgroundColor : mButtonBackgroundColor);
        shape.setCornerRadius(dp(pressed ? M3_KEY_PRESSED_RADIUS_DP : M3_KEY_RADIUS_DP));
        Object tag = view.getTag(R.id.ng_key_icon);
        if (!(tag instanceof Integer)) { view.setBackground(shape); return; }
        Drawable icon = ContextCompat.getDrawable(getContext(), (Integer) tag);
        if (icon == null) { view.setBackground(shape); return; }
        icon = icon.mutate();
        icon.setTint(latched ? mButtonActiveTextColor : mButtonTextColor);
        LayerDrawable layers = new LayerDrawable(new Drawable[]{shape, icon});
        int size = (int) dp(22);
        layers.setLayerSize(1, size, size);
        layers.setLayerGravity(1, Gravity.CENTER);
        view.setBackground(layers);
    }
```
In `showPopup(...)`, before `styleKey(button, true, false);`, add the same icon tagging for `extraButton.getKey()`:
```java
        int popupIcon = ExtraKeyIcons.iconFor(extraButton.getKey());
        if (popupIcon != 0) { button.setTag(R.id.ng_key_icon, popupIcon); button.setText(""); }
```

- [ ] **Step 6: Run the tests (CI) and expect PASS; then the build run expects success**

- [ ] **Step 7: Commit**

`git commit -m "feat(keys): Material Symbols Rounded icons on extra keys (ng.4 part 1)"` (no release yet; Task 4 completes ng.4).

---

### Task 4: `WAKELOCK` key wired to Termux's wakelock (completes ng.4)

**Files:**
- Modify: `app/src/main/java/com/termux/app/TermuxService.java`
- Modify: `app/src/main/java/com/termux/app/terminal/TermuxTerminalSessionClient.java`
- Modify: `app/src/main/java/com/termux/app/terminal/io/TermuxTerminalExtraKeys.java`
- Modify: `termux-shared/src/main/java/com/termux/shared/terminal/io/extrakeys/ExtraKeysView.java`
- Modify: `app/src/main/java/com/termux/app/TermuxActivity.java` (`onResume` refresh)

**Interfaces:**
- Consumes: `ExtraKeysView.styleKey(View, boolean, boolean)` (Task 3).
- Produces: `TermuxService.isWakeLockHeld(): boolean`; `ExtraKeysView.setKeyLatched(String key, boolean latched)`;
  `TermuxTerminalSessionClient.onWakeLockStateChanged()`; `TermuxActivity.refreshWakeLockKey()`. Task 8 uses
  `isWakeLockHeld()` and `toggleWakeLock`.

- [ ] **Step 1: `TermuxService`, additive**

```java
    /** NGTerminal: whether the service wakelock is currently held (read-only). */
    public synchronized boolean isWakeLockHeld() {
        return mWakeLock != null;
    }
```
At the end of `updateNotification()` (after the `if/else`), add:
```java
        // NGTerminal: let the activity refresh the keep-awake key/switch (same thread as the notification update)
        if (mTermuxTerminalSessionClient != null) mTermuxTerminalSessionClient.onWakeLockStateChanged();
```

- [ ] **Step 2: `ExtraKeysView.setKeyLatched`**

Add a field `private final Map<String, Boolean> mExternalLatched = new HashMap<>();` and a field
`private final Map<String, List<Button>> mButtonsByKey = new HashMap<>();`. In `reload()`, call
`mButtonsByKey.clear();` before the loops, and after `addView(button);` add:
```java
                List<Button> sameKey = mButtonsByKey.get(buttonInfo.getKey());
                if (sameKey == null) { sameKey = new ArrayList<>(); mButtonsByKey.put(buttonInfo.getKey(), sameKey); }
                sameKey.add(button);
```
Extend `isLatched` so external state counts:
```java
    private boolean isLatched(ExtraKeyButton buttonInfo) {
        Boolean external = mExternalLatched.get(buttonInfo.getKey());
        if (external != null) return external;
        if (!isSpecialButton(buttonInfo)) return false;
        SpecialButtonState state = mSpecialButtons.get(SpecialButton.valueOf(buttonInfo.getKey()));
        return state != null && state.isActive;
    }

    /** NGTerminal: show a non-special key (e.g. WAKELOCK) as latched/unlatched. No-op if the key isn't in the bar. */
    public void setKeyLatched(String key, boolean latched) {
        mExternalLatched.put(key, latched);
        List<Button> buttons = mButtonsByKey.get(key);
        if (buttons == null) return;
        for (Button b : buttons) {
            if (b.getTag(R.id.ng_key_icon) == null) b.setTextColor(latched ? mButtonActiveTextColor : mButtonTextColor);
            styleKey(b, false, latched);
        }
    }
```

- [ ] **Step 3: `TermuxTerminalExtraKeys`: the `WAKELOCK` case, before the final `else`**

```java
        } else if ("WAKELOCK".equals(key)) {
            // NGTerminal: same intents as the notification's "Acquire/Release wakelock" button
            TermuxActivity activity = mTermuxTerminalViewClient.getActivity();
            TermuxService service = activity.getTermuxService();
            boolean held = service != null && service.isWakeLockHeld();   // null before bind: treat as not held
            Intent intent = new Intent(activity, TermuxService.class).setAction(held
                ? TermuxConstants.TERMUX_APP.TERMUX_SERVICE.ACTION_WAKE_UNLOCK
                : TermuxConstants.TERMUX_APP.TERMUX_SERVICE.ACTION_WAKE_LOCK);
            activity.startService(intent);
```
Imports: `android.content.Intent`, `com.termux.app.TermuxActivity`, `com.termux.app.TermuxService`, `com.termux.shared.termux.TermuxConstants`.

- [ ] **Step 4: Refresh hooks**

`TermuxTerminalSessionClient`:
```java
    /** NGTerminal: the service notification changed; refresh keep-awake UI. */
    public void onWakeLockStateChanged() {
        mActivity.refreshWakeLockKey();
    }
```
`TermuxActivity`:
```java
    /** NGTerminal: show the WAKELOCK key latched while the service wakelock is held. */
    public void refreshWakeLockKey() {
        if (mExtraKeysView == null) return;
        mExtraKeysView.setKeyLatched("WAKELOCK", mTermuxService != null && mTermuxService.isWakeLockHeld());
    }
```
Call `refreshWakeLockKey();` at the end of `onResume()` and at the end of `onServiceConnected(...)`.

- [ ] **Step 5: versionName `0.118.3-ng.4`, push, watch build + tests by run ID, verify the APK (Task 2 Step 8 commands)**

- [ ] **Step 6: Pixel gate with stage checks**

Create `Termux_M3/tools/checks_ng4.sh` (sourced by the harness):
```bash
# ng.4: icon keys + WAKELOCK
rx 'mkdir -p ~/.termux; printf "%s\n" "extra-keys = [[\"ESC\",\"/\",\"-\",\"HOME\",\"UP\",\"END\",\"WAKELOCK\"],[\"TAB\",\"CTRL\",{macro: \"CTRL j\", display: \"↵\"},\"LEFT\",\"DOWN\",\"RIGHT\",\"KEYBOARD\"]]" > ~/.termux/termux.properties; termux-reload-settings'
a am force-stop com.termux; launch; sleep 3; shot keys      # cold start: service not yet bound when we tap
a uiautomator dump /sdcard/ng_ui.xml >/dev/null; adb -s "$S" pull -q /sdcard/ng_ui.xml "$OUT/ui_keys.xml"
wb=$(python3 -c "import re,sys;x=open('$OUT/ui_keys.xml').read();m=re.search(r'content-desc=\"WAKELOCK\"[^>]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]',x);print((int(m[1])+int(m[3]))//2,(int(m[2])+int(m[4]))//2) if m else print('')")
[ -n "$wb" ] && ok key_found || bad key_found "WAKELOCK key not in UI"
a input tap $wb; sleep 2; a dumpsys power | grep -q 'termux:service-wakelock' && ok wake_cold || bad wake_cold "tap did not acquire"
shot keys_wake_on
rx 'termux-wake-unlock'; sleep 2; shot keys_wake_off      # key must un-latch from the notification-side change
a input tap $wb; sleep 2; a input tap $wb; sleep 2; a dumpsys power | grep -q 'termux:service-wakelock' && bad wake_toggle "still held after on+off" || ok wake_toggle
key(){ a uiautomator dump /sdcard/ng_ui.xml >/dev/null; adb -s "$S" pull -q /sdcard/ng_ui.xml "$OUT/ui_k.xml"
  c=$(python3 -c "import re;x=open('$OUT/ui_k.xml').read();m=re.search(r'content-desc=\"$1\"[^>]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]',x);print((int(m[1])+int(m[3]))//2,(int(m[2])+int(m[4]))//2) if m else print('')"); [ -n "$c" ] && a input tap $c; }
# HOME key really moves the cursor: "cho OK>~/k.txt" only becomes a valid command if HOME puts the cursor at line start
rx 'rm -f ~/k.txt ~/k3.txt'; a input text "'cho%sOK>~/k.txt'"   # inner quotes survive adb shell, so the device shell doesn't redirect; key HOME; a input text 'e'; a input keyevent 66; sleep 2
[ "$(rx 'cat ~/k.txt 2>/dev/null')" = OK ] && ok key_home || bad key_home "HOME did not move cursor"
# CTRL latches and sends ^C: the follow-up echo only runs within 5 s if sleep 50 was interrupted
a input text 'sleep%s50'; a input keyevent 66; sleep 1; key CTRL; a input text 'c'; sleep 1
a input text "'echo%sdone>~/k3.txt'"; a input keyevent 66; sleep 3
[ "$(rx 'cat ~/k3.txt 2>/dev/null')" = done ] && ok key_ctrl_c || bad key_ctrl_c "CTRL+c did not interrupt"
```
Run: `EXTRA_CHECKS=tools/checks_ng4.sh tools/pixel_check_v1.sh ng4 <ng.4.apk> <ng.3.apk>`. Expected: `RESULT: PASS (ng4)`.
Look at `keys.png`, `keys_wake_on.png` and `keys_wake_off.png`: icons are drawn, and the coffee key is latched only in
`wake_on`.

- [ ] **Step 7: Release ng.4**

Copy the APK to `Download/`. Add a `RELEASE_NOTES.md` entry. On the user's phone, update `~/.termux/termux.properties`
(`'DRAWER'` → `'WAKELOCK'`), archiving the old file in `~/Archive/` first. The user installs and runs `termux-reload-settings`.

---

### Task 5: Tab label + busy detection (pure Java, TDD)

**Files:**
- Create: `app/src/main/java/com/termux/app/ngterminal/TabLabels.java`
- Create: `app/src/main/java/com/termux/app/ngterminal/ProcessTree.java`
- Create: `app/src/test/java/com/termux/app/ngterminal/TabLabelsTest.java`
- Create: `app/src/test/java/com/termux/app/ngterminal/ProcessTreeTest.java`

**Interfaces:**
- Produces: `TabLabels.labelFor(String sessionName, String terminalTitle, int position1Based): String`
- Produces: `ProcessTree.childPids(File procRoot, int ppid): List<Integer>` and `ProcessTree.commandLine(File procRoot, int pid): String` (empty if unreadable)

- [ ] **Step 1: Failing tests**

```java
package com.termux.app.ngterminal;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class TabLabelsTest {
    @Test public void nameWins() { assertEquals("build", TabLabels.labelFor("build", "vim x", 2)); }
    @Test public void titleWhenNoName() { assertEquals("vim x", TabLabels.labelFor(null, "vim x", 2)); }
    @Test public void blankNameFallsThrough() { assertEquals("vim x", TabLabels.labelFor("  ", "vim x", 2)); }
    @Test public void numberedWhenNothing() { assertEquals("Tab 3", TabLabels.labelFor("", null, 3)); }
}
```
```java
package com.termux.app.ngterminal;

import static org.junit.Assert.assertEquals;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ProcessTreeTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private void proc(File root, int pid, String stat, String cmdline) throws Exception {
        File d = new File(root, String.valueOf(pid)); d.mkdirs();
        if (stat != null) Files.write(new File(d, "stat").toPath(), stat.getBytes(StandardCharsets.UTF_8));
        if (cmdline != null) Files.write(new File(d, "cmdline").toPath(), cmdline.getBytes(StandardCharsets.UTF_8));
    }

    @Test public void findsDirectChildren() throws Exception {
        File root = tmp.newFolder("proc");
        proc(root, 100, "100 (bash) S 1 100 100 0", "bash\0");
        proc(root, 200, "200 (proot) S 100 200 100 0", "proot\0-r\0x\0");
        proc(root, 300, "300 (sleep) S 200 300 100 0", "sleep\0999\0");
        assertEquals(Collections.singletonList(200), ProcessTree.childPids(root, 100));
        assertEquals("proot -r x", ProcessTree.commandLine(root, 200));
    }

    @Test public void commNameWithSpacesAndParens() throws Exception {
        File root = tmp.newFolder("proc");
        proc(root, 400, "400 (my (odd) name) S 100 400 100 0", "odd\0");
        assertEquals(Collections.singletonList(400), ProcessTree.childPids(root, 100));
    }

    @Test public void skipsVanishedAndGarbledEntries() throws Exception {
        File root = tmp.newFolder("proc");
        proc(root, 500, null, null);                       // vanished: dir exists, no stat
        proc(root, 501, "garbage", null);                  // unparsable
        new File(root, "self").mkdirs();                   // non-numeric entries
        proc(root, 502, "502 (sh) S 100 502 100 0", "sh\0");
        assertEquals(Arrays.asList(502), ProcessTree.childPids(root, 100));
        assertEquals("", ProcessTree.commandLine(root, 999)); // unreadable -> empty, no throw
    }
}
```

- [ ] **Step 2: Push; the CI `Unit tests` run (by ID) must FAIL with "cannot find symbol" for both classes**

- [ ] **Step 3: Implement**

```java
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
```
```java
package com.termux.app.ngterminal;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** NGTerminal: read-only /proc helpers for "is this tab busy?". Never throws on racing/garbled entries. */
public final class ProcessTree {
    private ProcessTree() {}

    public static List<Integer> childPids(File procRoot, int ppid) {
        List<Integer> out = new ArrayList<>();
        File[] entries = procRoot.listFiles();
        if (entries == null) return out;
        for (File e : entries) {
            int pid;
            try { pid = Integer.parseInt(e.getName()); } catch (NumberFormatException ex) { continue; }
            int parent = parentOf(new File(e, "stat"));
            if (parent == ppid) out.add(pid);
        }
        Collections.sort(out);
        return out;
    }

    /** Fields after the last ')' are: state ppid ... (comm may contain spaces and parentheses). */
    private static int parentOf(File stat) {
        try {
            String s = new String(Files.readAllBytes(stat.toPath()), StandardCharsets.UTF_8);
            int close = s.lastIndexOf(')');
            if (close < 0) return -1;
            String[] f = s.substring(close + 1).trim().split("\\s+");
            return f.length > 1 ? Integer.parseInt(f[1]) : -1;
        } catch (IOException | NumberFormatException ex) {
            return -1;
        }
    }

    public static String commandLine(File procRoot, int pid) {
        try {
            byte[] b = Files.readAllBytes(new File(new File(procRoot, String.valueOf(pid)), "cmdline").toPath());
            return new String(b, StandardCharsets.UTF_8).replace('\0', ' ').trim();
        } catch (IOException ex) {
            return "";
        }
    }
}
```

- [ ] **Step 4: Push; the `Unit tests` run must PASS (all 7 tests). Commit message: `feat(tabs): tab label + /proc busy detection helpers`**

---

### Task 6: Tab bar UI (completes ng.5)

**Files:**
- Create: `app/src/main/res/values/ng_themes.xml`, `app/src/main/res/values/ng_colors_ui.xml`
- Create: `app/src/main/res/drawable/ng_tab_bg_selected.xml`, `ng_tab_bg.xml`, `ic_ng_add.xml`, `ic_ng_close.xml`, `ic_ng_settings.xml` (generated as in Task 3 Step 1 into `app/src/main/res/drawable`, names `add`, `close`, `settings`)
- Create: `app/src/main/res/layout/ng_tab_bar.xml`, `ng_tab_item.xml`
- Create: `app/src/main/java/com/termux/app/ngterminal/TermuxSessionsTabBarController.java`
- Modify: `app/src/main/res/layout/activity_termux.xml` (add a `FrameLayout` host above the `RelativeLayout`'s drawer)
- Modify: `app/src/main/java/com/termux/app/TermuxActivity.java`

**Interfaces:**
- Consumes: `TabLabels.labelFor`, `ProcessTree.childPids/commandLine` (Task 5); existing
  `TermuxTerminalSessionClient.setCurrentSession`, `renameSession`, `addNewSession`, `removeFinishedSession`.
- Produces: `TermuxSessionsTabBarController(TermuxActivity)` with `attach(ViewGroup host)` and `refresh()`.
  `TermuxActivity.termuxSessionListNotifyUpdated()` calls `refresh()`.

- [ ] **Step 1: Theme, colours, drawables**

`ng_colors_ui.xml`:
```xml
<resources>
    <color name="ng_surface_container">#211F26</color>
    <color name="ng_surface_container_high">#2B292F</color>
    <color name="ng_secondary_container">#4A4458</color>
    <color name="ng_on_secondary_container">#E8DEF8</color>
    <color name="ng_on_surface">#E6E0E9</color>
    <color name="ng_on_surface_variant">#CAC4CF</color>
    <color name="ng_primary">#CFBDFE</color>
    <color name="ng_on_primary">#36275D</color>
</resources>
```
`ng_themes.xml` (used only by the tab bar wrapper and dialogs):
```xml
<resources>
    <style name="Theme.NGTerminal.M3" parent="Theme.Material3.Dark.NoActionBar">
        <item name="colorPrimary">@color/ng_primary</item>
        <item name="colorOnPrimary">@color/ng_on_primary</item>
        <item name="colorSecondaryContainer">@color/ng_secondary_container</item>
        <item name="colorOnSecondaryContainer">@color/ng_on_secondary_container</item>
        <item name="colorSurface">#141218</item>
        <item name="colorOnSurface">@color/ng_on_surface</item>
    </style>
</resources>
```
`ng_tab_bg.xml` / `ng_tab_bg_selected.xml`:
```xml
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <corners android:radius="18dp"/><solid android:color="@color/ng_surface_container_high"/>
</shape>
```
(The selected one uses `@color/ng_secondary_container`.)

- [ ] **Step 2: Layouts**

`ng_tab_bar.xml`:
```xml
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/ng_tab_bar" android:layout_width="match_parent" android:layout_height="48dp"
    android:background="@color/ng_surface_container" android:gravity="center_vertical"
    android:orientation="horizontal" android:paddingHorizontal="6dp">
    <HorizontalScrollView android:id="@+id/ng_tab_scroll" android:layout_width="0dp"
        android:layout_height="match_parent" android:layout_weight="1" android:scrollbars="none">
        <LinearLayout android:id="@+id/ng_tabs" android:layout_width="wrap_content"
            android:layout_height="match_parent" android:gravity="center_vertical" android:orientation="horizontal"/>
    </HorizontalScrollView>
    <ImageButton android:id="@+id/ng_add_tab" android:layout_width="44dp" android:layout_height="44dp"
        android:background="?attr/selectableItemBackgroundBorderless" android:src="@drawable/ic_ng_add"
        android:tint="@color/ng_on_surface" android:contentDescription="New tab"/>
    <ImageButton android:id="@+id/ng_settings" android:layout_width="44dp" android:layout_height="44dp"
        android:background="?attr/selectableItemBackgroundBorderless" android:src="@drawable/ic_ng_settings"
        android:tint="@color/ng_on_surface" android:contentDescription="Settings"/>
</LinearLayout>
```
`ng_tab_item.xml`:
```xml
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="wrap_content" android:layout_height="36dp" android:layout_marginEnd="6dp"
    android:background="@drawable/ng_tab_bg" android:gravity="center_vertical" android:orientation="horizontal"
    android:paddingStart="14dp" android:paddingEnd="4dp">
    <TextView android:id="@+id/ng_tab_title" android:layout_width="wrap_content" android:layout_height="wrap_content"
        android:maxWidth="160dp" android:ellipsize="end" android:singleLine="true"
        android:textColor="@color/ng_on_surface_variant" android:textSize="14sp"/>
    <ImageButton android:id="@+id/ng_tab_close" android:layout_width="32dp" android:layout_height="32dp"
        android:background="?attr/selectableItemBackgroundBorderless" android:src="@drawable/ic_ng_close"
        android:tint="@color/ng_on_surface_variant"/>
</LinearLayout>
```

- [ ] **Step 3: Controller**

```java
package com.termux.app.ngterminal;

import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.TermuxService;
import com.termux.app.activities.SettingsActivity;
import com.termux.shared.shell.TermuxSession;
import com.termux.terminal.TerminalSession;

import java.io.File;
import java.util.List;

/** NGTerminal: Pixel-style tab bar over Termux sessions. Reuses Termux's own session actions only. */
public final class TermuxSessionsTabBarController {
    private final TermuxActivity mActivity;
    private final ContextThemeWrapper mThemed;
    private LinearLayout mTabs;

    public TermuxSessionsTabBarController(TermuxActivity activity) {
        mActivity = activity;
        mThemed = new ContextThemeWrapper(activity, R.style.Theme_NGTerminal_M3);
    }

    public void attach(ViewGroup host) {
        View bar = LayoutInflater.from(mThemed).inflate(R.layout.ng_tab_bar, host, true);
        mTabs = bar.findViewById(R.id.ng_tabs);
        View add = bar.findViewById(R.id.ng_add_tab);
        add.setOnClickListener(v -> mActivity.getTermuxTerminalSessionClient().addNewSession(false, null));
        add.setOnLongClickListener(v -> { mActivity.getTermuxTerminalSessionClient().addNewSession(true, null); return true; });
        bar.findViewById(R.id.ng_settings).setOnClickListener(v ->
            mActivity.startActivity(new android.content.Intent(mActivity, SettingsActivity.class)));
    }

    public void refresh() {
        TermuxService service = mActivity.getTermuxService();
        if (mTabs == null || service == null) return;
        mTabs.removeAllViews();
        List<TermuxSession> sessions = service.getTermuxSessions();
        TerminalSession current = mActivity.getCurrentSession();
        LayoutInflater inflater = LayoutInflater.from(mThemed);
        for (int i = 0; i < sessions.size(); i++) {
            final TerminalSession s = sessions.get(i).getTerminalSession();
            if (s == null) continue;
            View tab = inflater.inflate(R.layout.ng_tab_item, mTabs, false);
            boolean selected = s == current;
            String label = TabLabels.labelFor(s.mSessionName, s.getTitle(), i + 1);
            TextView title = tab.findViewById(R.id.ng_tab_title);
            title.setText(label);
            title.setTextColor(ContextCompat.getColor(mActivity, selected ? R.color.ng_on_secondary_container : R.color.ng_on_surface_variant));
            tab.setBackgroundResource(selected ? R.drawable.ng_tab_bg_selected : R.drawable.ng_tab_bg);
            tab.setContentDescription("Tab: " + label);
            tab.setOnClickListener(v -> mActivity.getTermuxTerminalSessionClient().setCurrentSession(s));
            tab.setOnLongClickListener(v -> { mActivity.getTermuxTerminalSessionClient().renameSession(s); return true; });
            ImageButton close = tab.findViewById(R.id.ng_tab_close);
            close.setContentDescription("Close " + label);
            close.setOnClickListener(v -> close(s));
            mTabs.addView(tab);
            if (selected) tab.post(() -> tab.requestRectangleOnScreen(new android.graphics.Rect(0, 0, tab.getWidth(), tab.getHeight())));
        }
    }

    private void close(TerminalSession s) {
        int pid = s.getPid();
        List<Integer> children = pid > 0 ? ProcessTree.childPids(new File("/proc"), pid) : java.util.Collections.emptyList();
        if (children.isEmpty()) { mActivity.getTermuxTerminalSessionClient().removeFinishedSession(s); return; }
        String cmd = ProcessTree.commandLine(new File("/proc"), children.get(0));
        new MaterialAlertDialogBuilder(mThemed)
            .setTitle("Close tab?")
            .setMessage((cmd.isEmpty() ? "A program" : cmd) + " is still running. Close tab?")
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton("Close", (d, w) -> mActivity.getTermuxTerminalSessionClient().removeFinishedSession(s))
            .show();
    }
}
```
(`TermuxSession` lives in `termux-shared/src/main/java/com/termux/shared/shell/TermuxSession.java`, the same import
`TermuxSessionsListViewController` uses.)

- [ ] **Step 4: Host the bar and lock the drawer**

In `activity_termux.xml`, insert as the **first child** of `TermuxActivityRootView` (before the `RelativeLayout`):
```xml
    <FrameLayout
        android:id="@+id/ng_tab_bar_host"
        android:layout_width="match_parent"
        android:layout_height="wrap_content" />
```
In `TermuxActivity`:
- add a field `TermuxSessionsTabBarController mTabBarController;`
- in `onCreate`, after `setToggleKeyboardView();`:
```java
        // NGTerminal: Pixel-style tabs replace swiping the drawer; drawer code kept, opened only programmatically.
        mTabBarController = new TermuxSessionsTabBarController(this);
        mTabBarController.attach(findViewById(R.id.ng_tab_bar_host));
        getDrawer().setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
```
- in `termuxSessionListNotifyUpdated()`, after the existing line: `if (mTabBarController != null) mTabBarController.refresh();`
- in `onServiceConnected`, after `setTermuxSessionsListView();`: `if (mTabBarController != null) mTabBarController.refresh();`

In `TermuxTerminalSessionClient.setCurrentSession`, after `updateBackgroundColor();`, add
`termuxSessionListNotifyUpdated();` so the selected tab follows switches.

In `TermuxTerminalExtraKeys`, the `DRAWER` case opens a locked drawer, because programmatic opens are allowed in
`LOCK_MODE_LOCKED_CLOSED`. Leave it unchanged.

- [ ] **Step 5: versionName `0.118.3-ng.5`; push; watch build + tests by run ID; verify the APK**

- [ ] **Step 6: Pixel gate with stage checks**

`Termux_M3/tools/checks_ng5.sh`:
```bash
# ng.5: tab bar
tap_desc(){ a uiautomator dump /sdcard/ng_ui.xml >/dev/null; adb -s "$S" pull -q /sdcard/ng_ui.xml "$OUT/ui.xml"
  c=$(python3 -c "import re;x=open('$OUT/ui.xml').read();m=re.search(r'content-desc=\"$1\"[^>]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]',x);print((int(m[1])+int(m[3]))//2,(int(m[2])+int(m[4]))//2) if m else print('')")
  [ -n "$c" ] && a input tap $c; [ -n "$c" ]; }
count_tabs(){ a uiautomator dump /sdcard/ng_ui.xml >/dev/null; a cat /sdcard/ng_ui.xml | grep -o 'content-desc="Tab: ' | wc -l; }
a am force-stop com.termux; launch; sleep 3; shot tabs_1
[ "$(count_tabs)" = 1 ] && ok tabs_one || bad tabs_one "expected 1 tab"
tap_desc "New tab"; sleep 2; tap_desc "New tab"; sleep 2; shot tabs_3
[ "$(count_tabs)" = 3 ] && ok tabs_three || bad tabs_three "expected 3 tabs"
tap_desc "Tab: Tab 1"; sleep 1; shot tabs_switch
tap_desc "Close Tab 3" && sleep 2; [ "$(count_tabs)" = 2 ] && ok close_idle || bad close_idle "idle tab not closed"
a input text 'sleep%s999' ; a input keyevent 66; sleep 2; tap_desc "Close Tab 1"; sleep 2; shot close_busy_dialog
a uiautomator dump /sdcard/ng_ui.xml >/dev/null; a cat /sdcard/ng_ui.xml | grep -q 'is still running' && ok close_busy || bad close_busy "no dialog"
a input keyevent 4; sleep 1
# long title stays ellipsized and add/settings stay visible
rx 'printf "\033]0;%s\007" "a-very-very-long-terminal-title-that-should-be-ellipsized-in-the-tab"' >/dev/null
tap_desc "Settings" && ok long_title_settings_reachable || bad long_title "settings button not reachable"; a input keyevent 4; shot long_title
# drawer swipe locked
a input swipe 5 800 600 800 300; sleep 1; shot drawer_swipe
a uiautomator dump /sdcard/ng_ui.xml >/dev/null; a cat /sdcard/ng_ui.xml | grep -q 'terminal_sessions_list.*visible-to-user="true"' && bad drawer_locked "drawer opened" || ok drawer_locked
# close the last tab -> activity finishes; relaunch gives a fresh tab
for t in $(seq 1 3); do tap_desc "Close Tab 1" || tap_desc "Close Tab 2"; sleep 1; tap_desc "Close" ; sleep 1; done
launch; sleep 3; [ "$(count_tabs)" -ge 1 ] && ok close_last || bad close_last "no tab after relaunch"
# RUN_COMMAND session appears as a tab
rx 'echo "allow-external-apps = true" >> ~/.termux/termux.properties; termux-reload-settings'
# from inside Termux (app uid holds com.termux.permission.RUN_COMMAND; adb shell's uid does not)
rx 'am startservice --user 0 -n com.termux/com.termux.app.RunCommandService -a com.termux.RUN_COMMAND --es com.termux.RUN_COMMAND_PATH /data/data/com.termux/files/usr/bin/top --ez com.termux.RUN_COMMAND_BACKGROUND false' >/dev/null
sleep 3; shot run_command_tab; [ "$(count_tabs)" -ge 2 ] && ok run_command_tab || bad run_command_tab "plugin session missing"
```
Run with `EXTRA_CHECKS=tools/checks_ng5.sh … ng5 <ng.5.apk> <ng.4.apk>`. Expected: `RESULT: PASS (ng5)`. Review all
`tabs_*.png`.

- [ ] **Step 7: Release ng.5 (Download/, RELEASE_NOTES entry, the user installs)**

---

### Task 7: Installed-packages data (pure Java, TDD)

**Files:**
- Create: `app/src/main/java/com/termux/app/ngterminal/DpkgStatusParser.java`, `SizeFormat.java`
- Create: `app/src/test/java/com/termux/app/ngterminal/DpkgStatusParserTest.java`, `SizeFormatTest.java`

**Interfaces:**
- Produces: `DpkgStatusParser.parse(Reader): List<DpkgStatusParser.Pkg>` (sorted largest first); `Pkg` has `public final String name, version; public final long sizeKiB`.
- Produces: `SizeFormat.human(long bytes): String` ("812 KB", "48.3 MB", "1.9 GB").

- [ ] **Step 1: Failing tests**

```java
package com.termux.app.ngterminal;

import static org.junit.Assert.assertEquals;
import java.io.StringReader;
import java.util.List;
import org.junit.Test;

public class DpkgStatusParserTest {
    private static final String STATUS =
        "Package: bash\nStatus: install ok installed\nVersion: 5.3.3\nInstalled-Size: 1200\nDescription: x\n continued\n\n" +
        "Package: rust\nStatus: install ok installed\nVersion: 1.99.1\nInstalled-Size: 578560\n\n" +
        "Package: old\nStatus: deinstall ok config-files\nVersion: 1\nInstalled-Size: 99999\n\n" +
        "Package: tiny\nStatus: install ok installed\nVersion: 2\n";      // no Installed-Size, no trailing blank line

    @Test public void parsesInstalledLargestFirst() {
        List<DpkgStatusParser.Pkg> p = DpkgStatusParser.parse(new StringReader(STATUS));
        assertEquals(3, p.size());
        assertEquals("rust", p.get(0).name);
        assertEquals("1.99.1", p.get(0).version);
        assertEquals(578560, p.get(0).sizeKiB);
        assertEquals("bash", p.get(1).name);
    }
    @Test public void excludesNotInstalled() {
        for (DpkgStatusParser.Pkg x : DpkgStatusParser.parse(new StringReader(STATUS))) assertEquals(false, x.name.equals("old"));
    }
    @Test public void missingSizeIsZero() {
        List<DpkgStatusParser.Pkg> p = DpkgStatusParser.parse(new StringReader(STATUS));
        assertEquals("tiny", p.get(2).name);
        assertEquals(0, p.get(2).sizeKiB);
    }
}
```
```java
package com.termux.app.ngterminal;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class SizeFormatTest {
    @Test public void units() {
        assertEquals("0 KB", SizeFormat.human(0));
        assertEquals("812 KB", SizeFormat.human(812L * 1024));
        assertEquals("48.3 MB", SizeFormat.human((long) (48.3 * 1024 * 1024)));
        assertEquals("1.9 GB", SizeFormat.human((long) (1.9 * 1024 * 1024 * 1024)));
    }
}
```

- [ ] **Step 2: Push; the `Unit tests` run must FAIL (missing classes)**

- [ ] **Step 3: Implement**

```java
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
```
```java
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
```

- [ ] **Step 4: Push; `Unit tests` PASS. Commit message `feat(settings): dpkg status parser + size format`**

---

### Task 8: Pixel-style settings (completes ng.6)

**Files:**
- Modify: `app/src/main/AndroidManifest.xml` (`SettingsActivity` theme → `@style/Theme.NGTerminal.Settings`)
- Modify: `app/src/main/res/values/ng_themes.xml` (add `Theme.NGTerminal.Settings`)
- Modify: `app/src/main/res/layout/activity_settings.xml`
- Modify: `app/src/main/java/com/termux/app/activities/SettingsActivity.java`
- Modify: `app/src/main/res/xml/root_preferences.xml`
- Create: `app/src/main/res/layout/ng_preference_switch.xml` (id `switchWidget`: the id AndroidX `SwitchPreferenceCompat` binds to; `MaterialSwitch` extends `SwitchCompat`, so the checked state syncs)
- Create: `app/src/main/res/drawable/ic_ng_coffee.xml`, `ic_ng_package_2.xml`, `ic_ng_terminal.xml`, `ic_ng_info.xml` (generated as in Task 3 Step 1, with `android:tint="?attr/colorOnSurfaceVariant"` on the `<vector>`)
- Create: `app/src/main/java/com/termux/app/ngterminal/PackagesFragment.java`

**Interfaces:**
- Consumes: `TermuxService.isWakeLockHeld()` (Task 4), `DpkgStatusParser`, `SizeFormat` (Task 7).

- [ ] **Step 1: Theme**

Append to `ng_themes.xml`:
```xml
    <style name="Theme.NGTerminal.Settings" parent="Theme.NGTerminal.M3">
        <item name="android:windowBackground">#141218</item>
        <item name="android:statusBarColor">#141218</item>
    </style>
```
Manifest: `SettingsActivity`'s `android:theme="@style/Theme.AppCompat.Light.DarkActionBar"` → `android:theme="@style/Theme.NGTerminal.Settings"`.

- [ ] **Step 2: Layout with a large collapsing title**

`activity_settings.xml`:
```xml
<androidx.coordinatorlayout.widget.CoordinatorLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent" android:layout_height="match_parent" android:fitsSystemWindows="true">
    <com.google.android.material.appbar.AppBarLayout android:layout_width="match_parent"
        android:layout_height="wrap_content" android:fitsSystemWindows="true">
        <com.google.android.material.appbar.CollapsingToolbarLayout
            style="?attr/collapsingToolbarLayoutLargeStyle"
            android:layout_width="match_parent" android:layout_height="?attr/collapsingToolbarLayoutLargeSize"
            app:layout_scrollFlags="scroll|exitUntilCollapsed|snap">
            <com.google.android.material.appbar.MaterialToolbar android:id="@+id/ng_settings_toolbar"
                android:layout_width="match_parent" android:layout_height="?attr/actionBarSize"
                android:elevation="0dp" app:layout_collapseMode="pin"/>
        </com.google.android.material.appbar.CollapsingToolbarLayout>
    </com.google.android.material.appbar.AppBarLayout>
    <FrameLayout android:id="@+id/settings" android:layout_width="match_parent" android:layout_height="match_parent"
        app:layout_behavior="@string/appbar_scrolling_view_behavior"/>
</androidx.coordinatorlayout.widget.CoordinatorLayout>
```
In `SettingsActivity.onCreate`, after `setContentView(...)` and before the fragment transaction:
```java
        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.ng_settings_toolbar);
        setSupportActionBar(toolbar);
        setTitle(R.string.title_activity_termux_settings);
```
(The existing `getSupportActionBar()` up-button code stays.)

- [ ] **Step 3: New rows, then the existing rows unchanged**

`ng_preference_switch.xml`:
```xml
<com.google.android.material.materialswitch.MaterialSwitch xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/switchWidget" android:layout_width="wrap_content" android:layout_height="wrap_content"
    android:clickable="false" android:focusable="false"/>
```
At the top of `root_preferences.xml`, inside `<PreferenceScreen>` and before the existing `termux` Preference:
```xml
    <SwitchPreferenceCompat
        app:key="ng_keep_awake"
        app:title="Keep awake"
        app:summary="Hold Termux's wakelock so sessions keep running with the screen off"
        app:icon="@drawable/ic_ng_coffee"
        app:persistent="false"
        app:widgetLayout="@layout/ng_preference_switch"/>

    <Preference
        app:key="ng_packages"
        app:title="Installed packages"
        app:icon="@drawable/ic_ng_package_2"
        app:persistent="false"
        app:fragment="com.termux.app.ngterminal.PackagesFragment"/>
```
On the existing `termux` Preference, add `app:icon="@drawable/ic_ng_terminal"`; on the existing `about` Preference,
add `app:icon="@drawable/ic_ng_info"`. Nothing else in the file changes.

- [ ] **Step 4: Wire the rows in `RootPreferencesFragment`**

At the end of `onCreatePreferences`:
```java
            configureNGTerminalPreferences(context);
```
New methods in `RootPreferencesFragment`:
```java
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
            Preference about = findPreference("about");
            if (about != null) about.setSummary("NGTerminal " + com.termux.BuildConfig.VERSION_NAME + " · based on Termux 0.118.3");
        }

        @Override
        public void onResume() {
            super.onResume();
            androidx.preference.SwitchPreferenceCompat keepAwake = findPreference("ng_keep_awake");
            if (keepAwake != null) keepAwake.setChecked(com.termux.app.ngterminal.PackagesFragment.isWakeLockHeld(getContext()));
        }
```

- [ ] **Step 5: `PackagesFragment`**

```java
package com.termux.app.ngterminal;

import android.content.Context;
import android.os.Bundle;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;

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

    /** Wakelock state as Android reports it (works without binding to TermuxService). */
    public static boolean isWakeLockHeld(Context context) {
        return com.termux.app.TermuxService.isWakeLockHeldStatic();
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context ctx = getPreferenceManager().getContext();
        androidx.preference.PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(ctx);
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
```
`isWakeLockHeldStatic()` needs one more additive line in `TermuxService`: a `private static volatile TermuxService sInstance;`
set in `onCreate()` (`sInstance = this;`) and cleared in `onDestroy()` (`sInstance = null;`), plus:
```java
    /** NGTerminal: wakelock state for UI outside the activity binding (settings). */
    public static boolean isWakeLockHeldStatic() {
        TermuxService s = sInstance;
        return s != null && s.isWakeLockHeld();
    }
```

- [ ] **Step 6: versionName `0.118.3-ng.6`; push; watch build + tests by run ID; verify the APK**

- [ ] **Step 7: Pixel gate with stage checks**

`Termux_M3/tools/checks_ng6.sh`:
```bash
# ng.6: settings
. "$(dirname "$0")/checks_ng5.sh" >/dev/null 2>&1 || true      # reuse tap_desc/count_tabs helpers
a am force-stop com.termux; launch; sleep 3
tap_desc "Settings"; sleep 2; shot settings_root
a uiautomator dump /sdcard/ng_ui.xml >/dev/null; X=$(a cat /sdcard/ng_ui.xml)
for t in "Keep awake" "Installed packages" "Termux" "About"; do echo "$X" | grep -q "text=\"$t\"" && ok "row_$t" || bad "row_$t" missing; done
n_ui=$(echo "$X" | grep -o '[0-9]* packages ·' | head -1 | cut -d' ' -f1)
n_dpkg=$(rx 'dpkg -l | grep -c "^ii"')
[ "$n_ui" = "$n_dpkg" ] && ok "packages_count=$n_ui" || bad packages_count "ui=$n_ui dpkg=$n_dpkg"
a input tap $(python3 -c "import re;x='''$X''';m=re.search(r'text=\"Keep awake\"[^>]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]',x);print((int(m[1])+int(m[3]))//2,(int(m[2])+int(m[4]))//2)"); sleep 2
a dumpsys power | grep -q 'termux:service-wakelock' && ok settings_wake_on || bad settings_wake_on "switch did not acquire"
shot settings_wake_on; rx 'termux-wake-unlock'; sleep 1
tap_desc "Installed packages" || a input tap 500 700; sleep 2; shot packages_list
a input keyevent 4; sleep 1
for page in "Termux"; do tap_desc "$page" || true; sleep 2; shot "stock_$page"; a input keyevent 4; sleep 1; done
```
Run: `EXTRA_CHECKS=tools/checks_ng6.sh … ng6 <ng.6.apk> <ng.5.apk>`. Expected: `RESULT: PASS (ng6)`. Then review the
screenshots: settings root, packages list, stock page.

- [ ] **Step 8: Release ng.6 (Download/, RELEASE_NOTES entry, the user installs)**

---

### Task 9: Wrap-up

- [ ] Vault: append the stage results (run IDs, Pixel PASS lines, screenshot paths) to `App/Claude/<date>.md` and a
  short "NGTerminal" section in `Other_Topics/Tools/Termux_Backup_Restore.md`.
- [ ] Memory: update `project_termux_claude_version_ceiling.md` (NGTerminal now on toolchain AGP 8.13.2 / SDK 36; the
  Pixel harness lives at `Termux_M3/tools/`), then mirror it to the vault and add a CHANGELOG line.
- [ ] Push the spec/plan commits with the last code change (no separate docs-only push).
