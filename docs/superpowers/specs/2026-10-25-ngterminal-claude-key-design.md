# NGTerminal — Claude key (sub-project A) — design

Status: draft for review · 2026-10-25 · branch `github-releases/m3-extra-keys` · target `0.118.3-ng.10`

## 1. What the user asked for

> "Esc you move to -, and in place of esc keep claude button, that run our claude script in resume mode!
> … it should check proot if its not there, install that then install claude then resume > it may auto
> take claude login process right? then if no session automatically claude."

Answers given on 2026-10-25:

| Question | Answer |
|---|---|
| Key tapped while the current tab is busy | Ask "Open a new Claude session?" |
| Where Claude opens | Not the tab's folder: the launcher **asks where**, listing folders under NGTerminal's own folder |
| Folder has sessions | Run `claude --resume`; Claude Code shows its own session picker |
| Folder has no sessions | Start a new session |
| Picker root | NGTerminal's own folder `Download/NGTerminal/`. Until sub-project B creates it, `Download/a_Claude_Things` (today's root) |
| Picker rows | Session count per folder, plus "n. New folder…" |
| The `-` key | Removed (it is on the keyboard's symbol page) |

The decomposition is **A** (this spec) → **B** (NGTerminal folder in Download for Claude's files) → **C** (app login and AWS
backup/sync). B and C get their own specs.

## 2. Success criteria

1. On a phone with nothing installed beyond NGTerminal's bootstrap, one tap on the Claude key ends at Claude
   running in a chosen folder: the login screen on first use, the session picker or a new session after that.
2. On the user's phone, which is already set up, the tap goes straight to the picker. Nothing is reinstalled.
3. Typing `claude` / `claude-next` behaves exactly as launcher v14 does today.
4. No Termux behaviour changes: bootstrap, package name, plugins, sessions, and keys other than the three that
   move.

## 3. Key bar

```
row 1:  CLAUDE   /   Esc   HOME   UP   END   WAKELOCK
row 2:  TAB    Ctrl   ↵    LEFT  DOWN  RIGHT KEYBOARD
```

- New special key name `CLAUDE`. Icon: **the Claude logo** (user, 2026-10-25: "Claude logo should be there"), path from
  Simple Icons 16.35.0 (CC0) in Claude's own colour #D97757, not tinted (`ic_ng_key_claude.xml`, `ExtraKeyIcons.keepsOwnColor`).
  Was: Material Symbols `auto_awesome`. contentDescription = key name `CLAUDE` (ExtraKeysView convention since ng.4; the Pixel harness looks keys up by it).
- `ESC` moves to slot 3, and the `-` key is removed.
- NGTerminal's built-in default (`TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS`) becomes this layout, so a
  fresh install gets it:
  `[['CLAUDE','/',{key: ESC, display: 'Esc'},'HOME','UP','END','WAKELOCK'], ['TAB',{key: CTRL, display: 'Ctrl'},{macro: 'CTRL j', display: '↵'},'LEFT','DOWN','RIGHT','KEYBOARD']]`
- A user's own `~/.termux/termux.properties` still wins, as in Termux. The user's phone file is archived, then
  edited to this layout. The edit also replaces `DRAWER`→`WAKELOCK` and drops `extra-keys-style = all`, which
  was already pending since ng.4.

## 4. Tapping the Claude key (app, Java)

`TermuxTerminalExtraKeys` gets a `CLAUDE` case, next to `WAKELOCK`, which calls `TermuxActivity.onClaudeKey()`:

1. Install or refresh the launcher if needed (§6).
2. **Busy check.** The current session's shell has a child process (`ProcessTree.childPids`, the ng.5 helper).
   - **Idle:** write `claude-next --ng-pick\r` to the current session, which is the same as typing it.
   - **Busy:** an M3 dialog asks "Open a new Claude session?" with [Cancel] [Open].
     - Open: `addNewSession(false, null)` (a new tab in the current tab's folder), then write the command to it.
       Text written before bash reads it waits in the tty buffer.
   - **No session at all** (e.g. every tab closed): treated as busy, so Open creates the tab.

## 5. Launcher v15 (`claude-next`)

v15 is v14 plus the additions below. Everything v14 does is unchanged: picker outside Download, update prompt,
browser FIFO bridge, `--kill-on-exit`, messaging socket, and `opens_session` rules. The first line after the
shebang carries a marker: `# ngterminal-launcher: 15`.

### 5.1 `--ng-pick` mode (only used by the key; removed from Claude's argv)

```
Where should Claude open?   (Download/a_Claude_Things)
  1. a_Claude_Things       25 sessions
  2. Termux_M3              3 sessions
  3. New_Mobile_UI          -
  n. New folder...
Choose [1-3, n, Enter = 1, q = quit]
```

- Rows: the root, then its sub-folders, one level deep as today. The root row is included because sessions run
  there too (this design was written in one).
- **Session count** = number of `*.jsonl` in `$CLAUDE_CONFIG_DIR/projects/<slug>/`. `<slug>` is the absolute
  path with every character outside `[A-Za-z0-9]` replaced by `-`. This is Claude Code's own naming, checked on
  this device: `/storage/emulated/0/Download/a_Claude_Things` → `-storage-emulated-0-Download-a-Claude-Things`.
- `n`: reads a name (non-empty, no `/`, not `.` or `..`, must not exist), runs `mkdir`, and the new folder has 0
  sessions.
- After the choice: `cd` there. More than 0 sessions → `claude --resume`; 0 → `claude`. Both pass through the
  existing update prompt and proot exec path.
- If the naming ever changes, the count shows `-` and a new session starts. Nothing is lost: `claude --resume`
  typed by hand still works.

### 5.2 Setup when something is missing

This runs at the start of any interactive launch. It prints each step and stops with a clear message on
failure; nothing is half-swapped.

| Missing | Action | Proof it worked |
|---|---|---|
| `proot`, `jq`, `curl`, `zstd`, `openssl`, `dpkg-deb` | `pkg install -y` (Termux's own repos) | `command -v` for each |
| Write access to `Download` | run `termux-setup-storage`, then wait up to 30 s for `/storage/emulated/0/Download` to become writable | test write |
| glibc libs in `~/.cc-min/lib` | download `libc6_2.43-2ubuntu2.4_arm64.deb` from Launchpad (keeps every build permanently), check **SHA-256 `1652597c8c3419b298d8a87dd3d8eea1fdc5a1abd9bccb5a66b928f92ec463c9`**, `dpkg-deb -x` to a temp dir, copy the 6 libs with `cp -L` | the 6 libs are byte-identical to the rootfs proven on this device (checked 2026-10-25) |
| rootfs extras | `usr/bin/env`, `bin/sh`, `bin/bash` symlinks; `etc/hosts`, `etc/passwd`, `etc/group`, `etc/resolv.conf`; `bin/open-url` (vault recipe 2e) | files exist |
| `~/.cc-min/claude` | existing `update "$(latest)"`: npm tarball, **sha512 integrity** check, proven with `--version` before the swap | `VERSION` written |

- **Login:** on first run Claude shows `/login`. The URL opens through the existing FIFO bridge
  (`termux-open-url`), and nothing new is needed.
- **Offline during setup:** the step that needs the network fails with "No connection — tap the Claude key
  again when online". Steps already done are kept.

## 6. How the app ships the launcher

- The source lives in the repo: `app/src/main/assets/ngterminal/claude-next`. The vault copy stays as
  documentation.
- On a Claude key tap, the app reads `$PREFIX/bin/claude-next`:
  - **missing** → write it, mode 0700;
  - **marker < 15, or no marker** (the user's hand-made v14) → copy the old file to
    `~/.ngterminal/backup/claude-next-<yyyyMMdd-HHmmss>`, then write the new one;
  - **marker ≥ 15** → leave it alone. A newer hand edit is never overwritten.
- `$PREFIX/bin/claude` → symlink to `claude-next`, only if `claude` does not exist.
- Version parsing is a pure Java function (`LauncherVersion.parse(String)` and
  `needsInstall(existing, bundled)`), unit-tested.

## 7. What can go wrong

| Case | Behaviour |
|---|---|
| Launchpad, npm or Termux repo unreachable | the step prints which host failed and what to do; re-tap to retry |
| Checksum mismatch (libc6 or npm) | stop, delete the download, never install it |
| `pkg install` fails (mirror) | show the `pkg` error; Termux's own mirror selection applies |
| Storage permission denied | picker root unreadable → message "Allow storage (termux-setup-storage)"; nothing else runs |
| Key tapped twice quickly | the second tap sees a busy tab → asks; no double typing |
| v14 replaced by mistake | backup in `~/.ngterminal/backup/`; `cp` it back |

## 8. Testing

1. **JVM unit (CI):** `LauncherVersionTest` covers the marker parse and the needsInstall cases (missing, no
   marker, older, same, newer). It is written first and watched fail.
2. **Shell (locally in Termux, run before every push):** `tests/launcher/test_claude_next.sh`. It uses a fake
   `CLAUDE_CONFIG_DIR` and a fake folder tree, with `NG_DRY_RUN=1`, which makes the launcher print the final
   command instead of exec'ing it. Cases:
   - slug;
   - session counts;
   - the picker with and without sessions;
   - "n" with valid and invalid names;
   - `--ng-pick` stripped from argv;
   - plain `claude-next --version` unchanged.
3. **Pixel 8 (`tools/checks_ng10.sh`), before the user's phone:**
   - **Fresh setup:** move the Pixel's `~/.cc-min` and launcher aside, tap the key, wait for setup, check
     `claude-next --version` = `VERSION`, and check the picker or login screen is on screen. The Pixel has no
     Claude account, so the run stops at the login screen.
   - **Already set up:** tap the key and get the picker with no install output.
   - **Busy tab:** `sleep 999`, tap the key, expect the dialog. Cancel changes nothing; Open adds a tab.
   - **Key bar:** content-desc "Claude" in slot 1 and "Esc" in slot 3; screenshot.
   - Then the full regression (`checks_all_v1.sh`).
4. **The user's phone, after the Pixel passes:** archive and edit `termux.properties`, then the first tap
   replaces v14 (backup kept).

## 9. Out of scope (kept for B and C)

- **Moving Claude's files into `Download/NGTerminal`** (sub-project B).
  - Download is mounted **noexec**, so the claude binary and the rootfs cannot live there.
  - A Claude login token there is readable by any app with storage access.
- **App login and AWS backup/sync** (sub-project C). A login gate must work offline, or the user is locked out
  of their own terminal.
