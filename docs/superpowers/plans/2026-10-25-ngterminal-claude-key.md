# NGTerminal Claude key (sub-project A) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** One tap on a new Claude extra key sets up proot + the Claude rootfs + binary if missing, asks which project folder to open (with session counts and "New folder"), and resumes there or starts a new session.

**Architecture:**
- The bash launcher `claude-next` (v14 on the user's phone) becomes v15. It ships in the APK as an asset, and the app writes it to `$PREFIX/bin` when the file there is missing or older.
- The Java side is small: a version-marker check, an atomic installer, and an "is this tab an idle shell" rule (all pure Java and unit-tested). Around them sit thin Android glue and a `CLAUDE` key case.
- All install and picker logic stays in bash. The user sees it in the terminal, and it is unit-tested by sourcing the launcher.

**Tech Stack:**
- Java 17, compiled locally with OpenJDK 21. Android Gradle (AGP 8.13.2 / Gradle 9.2.1) in CI.
- JUnit 4.13.2 and bash 5.
- Termux `pkg`, proot, `dpkg-deb`.
- Ubuntu `libc6 2.43-2ubuntu2.4` (arm64) from Launchpad, and npm `@anthropic-ai/claude-code-linux-arm64`.
- Material Symbols Rounded (Apache 2.0).

**Spec:** `docs/superpowers/specs/2026-10-25-ngterminal-claude-key-design.md`

## Global Constraints

- Package `com.termux`, `versionCode 1002`, `versionName "0.118.3-ng.10"` with nothing after the closing quote (CI regex `\s+versionName "([^"]+)"$`), targetSdk 28, minSdk 24, bootstrap `2025.03.28-r1` unchanged, signed with `app/dev_keystore.jks`.
- minSdk 24: new app code must not use `java.nio.file` (API 26). Use `java.io` streams.
- No Termux behaviour changes beyond the key bar (spec §2.4). Typing `claude` / `claude-next` must behave as v14.
- Launcher marker: line 2 is exactly `# ngterminal-launcher: 15`.
- glibc source: `https://launchpad.net/ubuntu/+archive/primary/+files/libc6_2.43-2ubuntu2.4_arm64.deb`, SHA-256 `1652597c8c3419b298d8a87dd3d8eea1fdc5a1abd9bccb5a66b928f92ec463c9`. The libraries are in `usr/lib/aarch64-linux-gnu/` inside the package.
- Picker root: `PROJECTS="$BASE"` (`/storage/emulated/0/Download/a_Claude_Things`) until sub-project B.
- Key content description = key name (`CLAUDE`, `ESC`). That is ExtraKeysView's convention since ng.4, which the Pixel harness relies on. Ruling: this replaces the spec's "Claude" wording.
- Pure-Java tests run on the phone before any push: `bash /storage/emulated/0/Download/a_Claude_Things/Termux_M3/tools/junit_local_v1.sh <files>`. Android-dependent tests (`ExtraKeyIconsTest`) run in CI.
- One push for the whole feature (CI-minutes rule). The Pixel 8 gate comes before the user's phone.

## Review Focus

1. **Launcher replaced while another tab's bash is still reading it** (e.g. waiting at the v14 picker): that bash must keep running the old copy, so the install writes a temp file and renames it over the target. Pinned by `LauncherInstallerTest.readerOfOldFileKeepsOldContent` (Task 2).
2. **Session limit (8 tabs) reached while the current tab is busy, then Open:** nothing may be typed into the busy tab, or bash would run `claude-next` when that program ends. Pinned by the Pixel check `ng10_max_sessions_no_typing` (Task 5).
3. **The current tab is not a shell** (a RUN_COMMAND tab running python, or a finished session with pid 0): never type into it; ask instead. Pinned by `ClaudeKeyTest.nonShellIsNotIdle` and `finishedSessionIsNotIdle` (Task 3).
4. **Bad picker input** (out-of-range number, `n` with `a/b`, `..`, an existing name, Ctrl+D/EOF): re-prompt or quit cleanly, and never create a folder outside `PROJECTS`. Pinned by the `pick_*` shell tests (Task 1).
5. **Fresh phone offline, or with a corrupted download:** setup stops with a message and leaves no partial libraries that would break the next launch. Pinned by the `rootfs_offline` and `rootfs_bad_checksum` shell tests (Task 1).

---

### Task 1: Launcher v15 (asset) + shell tests

**Files:**
- Create: `app/src/main/assets/ngterminal/claude-next`
- Create: `app/src/test/launcher/test_claude_next.sh`
- Modify: `.github/workflows/run_tests.yml` (one step after "Execute tests")

**Interfaces:**
- Produces (bash, used by Tasks 4 and 5):
  - the `claude-next --ng-pick` command;
  - marker line 2 `# ngterminal-launcher: 15`;
  - functions `slug`, `session_count`, `valid_name`, `pick_project`, `resume_args`, `parse_args` (sets `NG_PICK` and `ARGS`), `pkg_of`, `ensure_tools`, `ensure_storage`, `ensure_rootfs`, `ensure_claude`, `setup`;
  - the sourcing guard `[[ ${BASH_SOURCE[0]} != "$0" ]] && return 0`.

- [ ] **Step 1: Write the failing shell test**

`app/src/test/launcher/test_claude_next.sh`:

```bash
#!/usr/bin/env bash
# Launcher v15 tests: source claude-next (functions only, thanks to its sourcing guard) and check the
# Claude key's picker, resume decision, argv handling and offline/corrupt setup. Runs in Termux and on CI:
#   bash app/src/test/launcher/test_claude_next.sh
HERE=$(cd "$(dirname "$0")" && pwd)
L="$HERE/../../main/assets/ngterminal/claude-next"
PASS=0; FAIL=0
check() { if [ "$2" = "$3" ]; then PASS=$((PASS + 1)); else FAIL=$((FAIL + 1)); echo "FAIL $1: expected [$2] got [$3]"; fi; }
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT

[ -f "$L" ] || { echo "FAIL launcher missing: $L"; echo "launcher tests: 0 passed, 1 failed"; exit 1; }
SAVED_PATH=$PATH; PREFIX=${PREFIX:-/nonexistent}
# shellcheck source=/dev/null
source "$L"
PATH=$SAVED_PATH            # the launcher exports Termux's PATH; CI needs its own back
CLAUDE_DIR="$T/claude"; PROJECTS="$T/projects"; DL="$T"; ROOT="$T/root"
mkdir -p "$PROJECTS/alpha" "$PROJECTS/beta" "$CLAUDE_DIR/projects/$(slug "$PROJECTS/alpha")"
touch "$CLAUDE_DIR/projects/$(slug "$PROJECTS/alpha")/a.jsonl" "$CLAUDE_DIR/projects/$(slug "$PROJECTS/alpha")/b.jsonl"

# pick <input>: runs the picker in a subshell, prints "OK:<dir>" or "RC:<code>"
pick() { (cd "$T" && if pick_project; then echo "OK:$PWD"; else echo "RC:$?"; fi) <<<"$1" 2>/dev/null | tail -1; }

check marker_line_2 "# ngterminal-launcher: 15" "$(sed -n 2p "$L")"
check slug_real_path "-storage-emulated-0-Download-a-Claude-Things" "$(slug /storage/emulated/0/Download/a_Claude_Things)"
check count_two "2" "$(session_count "$PROJECTS/alpha")"
check count_none "0" "$(session_count "$PROJECTS/beta")"

menu=$( (pick_project <<<"q") 2>/dev/null)
check menu_root_row "1" "$(grep -cE '^  1\. projects +- *$' <<<"$menu")"
check menu_alpha_count "1" "$(grep -cE '^  2\. alpha +2 sessions$' <<<"$menu")"
check menu_beta_none "1" "$(grep -cE '^  3\. beta +-$' <<<"$menu")"
check menu_new_folder "1" "$(grep -cx '  n. New folder...' <<<"$menu")"

check pick_enter_is_root "OK:$PROJECTS" "$(pick "")"
check pick_number "OK:$PROJECTS/alpha" "$(pick "2")"
check pick_quit "RC:1" "$(pick "q")"
check pick_eof "RC:1" "$( (cd "$T" && if pick_project; then echo OK; else echo "RC:$?"; fi) </dev/null 2>/dev/null | tail -1)"
check pick_out_of_range_then_quit "RC:1" "$(pick $'9\nq')"
check pick_new_folder "OK:$PROJECTS/gamma" "$(pick $'n\ngamma')"
check pick_new_folder_made "yes" "$([ -d "$PROJECTS/gamma" ] && echo yes)"
check pick_new_slash_rejected "RC:1" "$(pick $'n\na/b\nq')"
check pick_new_slash_not_made "no" "$([ -e "$PROJECTS/a" ] && echo yes || echo no)"
check pick_new_dotdot_rejected "RC:1" "$(pick $'n\n..\nq')"
check pick_new_existing_rejected "RC:1" "$(pick $'n\nalpha\nq')"
check pick_new_empty_rejected "RC:1" "$(pick $'n\n\nq')"
PROJECTS="$T/fresh_root"
check pick_creates_missing_root "OK:$T/fresh_root" "$(pick "")"
PROJECTS="$T/projects"

check resume_with_sessions "--resume" "$(cd "$PROJECTS/alpha" && resume_args)"
check resume_without_sessions "" "$(cd "$PROJECTS/beta" && resume_args)"

parse_args --ng-pick --model x
check parse_strips_flag "1|--model x" "$NG_PICK|${ARGS[*]}"
parse_args --version
check parse_without_flag "0|--version" "$NG_PICK|${ARGS[*]}"
check opens_session_version "1" "$(opens_session --version; echo $?)"
check opens_session_plain "0" "$(opens_session; echo $?)"

check pkg_openssl "openssl-tool" "$(pkg_of openssl)"
check pkg_dpkg_deb "dpkg" "$(pkg_of dpkg-deb)"
check pkg_proot "proot" "$(pkg_of proot)"

# setup failures must leave no libs behind (stubs shadow the real commands inside this shell)
curl() { return 6; }
check rootfs_offline "1" "$(ensure_rootfs >/dev/null 2>&1; echo $?)"
check rootfs_offline_no_libs "0" "$(ls "$ROOT/lib" 2>/dev/null | wc -l | tr -d ' ')"
curl() { local o; while [ $# -gt 0 ]; do [ "$1" = -o ] && o=$2; shift; done; echo corrupt > "$o"; }
check rootfs_bad_checksum "1" "$(ensure_rootfs >/dev/null 2>&1; echo $?)"
check rootfs_bad_checksum_no_libs "0" "$(ls "$ROOT/lib" 2>/dev/null | wc -l | tr -d ' ')"
unset -f curl

echo "launcher tests: $PASS passed, $FAIL failed"
[ "$FAIL" -eq 0 ]
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd /storage/emulated/0/Download/a_Claude_Things/Termux_M3/repo && bash app/src/test/launcher/test_claude_next.sh`
Expected: `FAIL launcher missing: …/claude-next` and `launcher tests: 0 passed, 1 failed`. Exit code 1.

- [ ] **Step 3: Write the launcher v15**

`app/src/main/assets/ngterminal/claude-next` is v14 (`$PREFIX/bin/claude-next` on the user's phone, 145 lines) plus the v15 parts. Full file:

```bash
#!/data/data/com.termux/files/usr/bin/bash
# ngterminal-launcher: 15
# Claude Code (Opus 5.5) in a 2.8 MB minimal glibc rootfs.
#
# WHY THIS SHAPE:
#  - Termux can't exec the official binary: it is ET_EXEC (non-PIE) and Android's
#    /system/bin/linker64 rejects those ("unexpected e_type: 2"). Termux's own
#    glibc ld.so refuses it too, so glibc-runner/grun cannot help.
#  - proot's change-root (-r) loads it via the rootfs's own ld.so, bypassing
#    Android's linker. Only glibc is needed for that, not a whole distro: the
#    binary links just libc/libm/libdl/libpthread/librt + ld.so (6 files, 2.8 MB).
#  - -r must be used, not -b: a bind alone still goes through Android's linker.
#  - `env -u LD_PRELOAD` is REQUIRED: termux-exec's libtermux-exec.so rewrites
#    exec paths and breaks every glibc binary with a misleading PHDR error.
#
# BINDS: the whole Termux files tree (home AND usr) plus the Android system dirs,
# so Claude's Bash tool can run the real Termux toolchain (bash, git, node,
# python3, sshpass, adb). Binding only ~/ leaves the rootfs with no shell at all.
# /system /apex /vendor /linkerconfig give bionic binaries their linker + libs.
# PATH and SHELL must be exported explicitly, or bash falls through to
# /system/bin and every lookup fails with "Operation not permitted".
#
# ~/.claude and /storage/emulated/0 stay at their IDENTICAL paths, because
# Claude keys history/memory/tasks by working-directory path.
#
# CAVEAT: LD_PRELOAD is unset, so termux-exec is inactive. #!/bin/sh, #!/bin/bash and
# #!/usr/bin/env work only because ~/.cc-min has symlinks to Termux's copies.
#
# UPDATES: on an interactive launch this checks the npm registry and asks before updating.
# The installed version is tracked in ~/.cc-min/VERSION. The old binary is deleted only after
# the new one passes --version; every old version stays on npm if a rollback is ever needed.
#
# NGTERMINAL (v15): shipped inside the NGTerminal APK (assets/ngterminal/claude-next). The app
# writes it to $PREFIX/bin when that file is missing or has an older marker (line 2), keeping the
# old file in ~/.ngterminal/backup/. New in v15:
#  - SETUP: an interactive launch first installs whatever a fresh phone is missing: proot & tools
#    (pkg), the 6 glibc libs (Ubuntu libc6 2.43-2ubuntu2.4 from Launchpad, SHA-256 pinned - the same
#    files as the proven rootfs), and the Claude binary (npm, sha512 checked, proven with --version).
#  - --ng-pick (the Claude key): always ask where to open (root + sub-folders with session counts,
#    or a new folder); a folder with sessions opens `claude --resume`, otherwise a new session.

ROOT="$HOME/.cc-min"
CLAUDE_DIR="/data/data/com.termux/files/home/.claude"

export CLAUDE_CONFIG_DIR="$CLAUDE_DIR"
export ANTHROPIC_MODEL="${ANTHROPIC_MODEL:-claude-opus-5-5[1m]}"
export DISABLE_AUTOUPDATER=1   # updater would fetch the stock Linux build and break this
export PATH="/data/data/com.termux/files/usr/bin:/data/data/com.termux/files/usr/bin/applets"
export SHELL="/data/data/com.termux/files/usr/bin/bash"
# glibc has no zoneinfo in the 2.8 MB rootfs and Termux ships no tzdata, so without TZ the
# binary falls back to UTC (session expiry etc. shown 5h30m off). POSIX form needs no zone
# files; India has no DST, so this is exact. An existing TZ in the environment wins.
export TZ="${TZ:-IST-5:30}"
# proot gives no uid map, so Claude can't verify a default socket dir and turns off cross-session
# messaging (SendMessage/ListAgents). An explicit per-launch path in our own 0700 dir turns it back on.
MSG_DIR="/data/data/com.termux/files/usr/tmp/cc-msg"

# --kill-on-exit: when Claude exits, kill anything still traced by THIS proot (a runaway
# `find /`, a stray dev server) - otherwise proot waits for it and the terminal hangs after /exit.
# Each claude-next session is its own proot, so parallel sessions are never touched.
PROOT=(env -u LD_PRELOAD proot --kill-on-exit
  -r "$ROOT"
  -b /data/data/com.termux/files:/data/data/com.termux/files
  -b /data/data/com.termux/cache:/data/data/com.termux/cache
  -b /system -b /apex -b /vendor -b /linkerconfig
  -b /storage/emulated/0:/storage/emulated/0
  -b /proc -b /dev -b /sys
  -b "$PREFIX/etc/resolv.conf:/etc/resolv.conf"
  -b "$PREFIX/tmp:/tmp")

REG="https://registry.npmjs.org/@anthropic-ai/claude-code-linux-arm64"
ask() { local a; read -r -p "$1 [y/N] " a; [[ $a == [yY]* ]]; }

DL="/storage/emulated/0/Download"
BASE="$DL/a_Claude_Things"
PROJECTS="$BASE"   # the Claude key's picker root; sub-project B moves it to $DL/NGTerminal
pick_dir() {   # 1 = stay here, then a_Claude_Things and its subfolders; cd into the choice
  local n d dirs
  shopt -s nullglob; dirs=("$PWD" "$BASE" "$BASE"/*/); shopt -u nullglob
  echo "Outside Download - where should Claude open?"
  printf '%3d. Continue in current directory (%s)\n' 1 "$PWD"
  for ((n = 1; n < ${#dirs[@]}; n++)); do d=${dirs[n]%/}; printf '%3d. %s\n' $((n + 1)) "${d##*/}"; done
  while read -r -p "Choose [1-${#dirs[@]}, Enter = 1, q = quit] " n; do
    [[ -z $n ]] && n=1
    [[ $n == [qQ] ]] && exit 0
    [[ $n =~ ^[0-9]+$ ]] && (( n >= 1 && n <= ${#dirs[@]} )) && { cd "${dirs[n - 1]%/}" || exit 1; return; }
  done
  exit 0
}

# --- v15: the Claude key's picker -------------------------------------------------------------
slug() { printf '%s' "${1//[^A-Za-z0-9]/-}"; }   # Claude Code's projects/ folder name for a path
session_count() {   # dir -> number of saved Claude sessions for it
  local f
  shopt -s nullglob; f=("$CLAUDE_DIR/projects/$(slug "$1")"/*.jsonl); shopt -u nullglob
  echo "${#f[@]}"
}
valid_name() { [[ -n $1 && $1 != */* && $1 != . && $1 != .. ]]; }
pick_project() {   # root + sub-folders with session counts, or a new folder; cd into it. 1 = quit/EOF
  local n d c dirs name
  mkdir -p "$PROJECTS" || return 1
  shopt -s nullglob; dirs=("$PROJECTS" "$PROJECTS"/*/); shopt -u nullglob
  echo "Where should Claude open?   (${PROJECTS#/storage/emulated/0/})"
  for ((n = 0; n < ${#dirs[@]}; n++)); do
    d=${dirs[n]%/}; c=$(session_count "$d")
    if ((c == 0)); then c=-; elif ((c == 1)); then c="1 session"; else c="$c sessions"; fi
    printf '%3d. %-22s %s\n' $((n + 1)) "${d##*/}" "$c"
  done
  echo "  n. New folder..."
  while read -r -p "Choose [1-${#dirs[@]}, n, Enter = 1, q = quit] " n; do
    [[ -z $n ]] && n=1
    [[ $n == [qQ] ]] && return 1
    if [[ $n == [nN] ]]; then
      read -r -p "Folder name: " name || return 1
      if valid_name "$name" && [ ! -e "$PROJECTS/$name" ]; then
        mkdir "$PROJECTS/$name" && cd "$PROJECTS/$name" && return 0
      fi
      echo "Can't use \"$name\": empty, contains /, is . or .., or already exists."
      continue
    fi
    [[ $n =~ ^[0-9]+$ ]] && (( n >= 1 && n <= ${#dirs[@]} )) && { cd "${dirs[n - 1]%/}" || return 1; return 0; }
  done
  return 1
}
resume_args() { (( $(session_count "$PWD") > 0 )) && echo --resume; return 0; }
parse_args() {   # --ng-pick is the launcher's own flag: strip it into NG_PICK, the rest goes to Claude
  local a
  NG_PICK=0; ARGS=()
  for a in "$@"; do if [[ $a == --ng-pick ]]; then NG_PICK=1; else ARGS+=("$a"); fi; done
}

# --- v15: first-run setup (every step is skipped when already done) ----------------------------
LIBC_DEB_URL="https://launchpad.net/ubuntu/+archive/primary/+files/libc6_2.43-2ubuntu2.4_arm64.deb"
LIBC_DEB_SHA256="1652597c8c3419b298d8a87dd3d8eea1fdc5a1abd9bccb5a66b928f92ec463c9"
LIBS=(ld-linux-aarch64.so.1 libc.so.6 libm.so.6 libdl.so.2 libpthread.so.0 librt.so.1)
offline() { echo "No connection - tap the Claude key again when online." >&2; return 1; }
pkg_of() { case $1 in dpkg-deb) echo dpkg ;; openssl) echo openssl-tool ;; *) echo "$1" ;; esac; }
ensure_tools() {
  local c pkgs=()
  for c in proot jq curl zstd openssl dpkg-deb; do command -v "$c" >/dev/null || pkgs+=("$(pkg_of "$c")"); done
  ((${#pkgs[@]} == 0)) && return 0
  echo "Installing ${pkgs[*]}..."
  pkg install -y "${pkgs[@]}" || { echo "pkg install failed - check the connection, then tap the Claude key again." >&2; return 1; }
}
ensure_storage() {
  [ -w "$DL" ] && return 0
  echo "Allow storage access in the dialog (your project folders live in Download)."
  termux-setup-storage
  local i
  for ((i = 0; i < 30; i++)); do [ -w "$DL" ] && return 0; sleep 1; done
  echo "Storage access not granted - allow it, then tap the Claude key again." >&2; return 1
}
ensure_rootfs() {
  local l d ok=1 missing=0
  for l in "${LIBS[@]}"; do [ -f "$ROOT/lib/$l" ] || missing=1; done
  if ((missing)); then
    echo "Setting up the Claude rootfs (glibc 2.43, 1.6 MB download)..."
    d=$(mktemp -d) || return 1
    if ! curl -fsSL --max-time 120 -o "$d/libc6.deb" "$LIBC_DEB_URL"; then rm -rf "$d"; offline; return 1; fi
    if [ "$(sha256sum "$d/libc6.deb" | cut -d' ' -f1)" != "$LIBC_DEB_SHA256" ]; then
      rm -rf "$d"; echo "glibc package checksum mismatch - not installed." >&2; return 1
    fi
    dpkg-deb -x "$d/libc6.deb" "$d/x" || ok=0
    mkdir -p "$ROOT/lib"
    for l in "${LIBS[@]}"; do ((ok)) && { cp -L "$d/x/usr/lib/aarch64-linux-gnu/$l" "$ROOT/lib/$l" || ok=0; }; done
    rm -rf "$d"
    ((ok)) || { echo "Could not unpack the glibc package." >&2; return 1; }
  fi
  # Mount points, interpreter symlinks and etc files (vault: Claude_Code_Latest_on_Termux, 2e).
  mkdir -p "$ROOT"/{bin,etc,usr/bin,tmp,system,apex,vendor,linkerconfig,storage,data,proc,dev,sys}
  ln -sfn "$PREFIX/bin/env" "$ROOT/usr/bin/env"
  ln -sfn "$PREFIX/bin/sh" "$ROOT/bin/sh"
  ln -sfn "$PREFIX/bin/bash" "$ROOT/bin/bash"
  [ -f "$ROOT/etc/hosts" ] || printf '127.0.0.1 localhost\n::1 localhost ip6-localhost ip6-loopback\n' > "$ROOT/etc/hosts"
  [ -f "$ROOT/etc/passwd" ] || printf 'root:x:0:0:root:/root:/bin/sh\n%s:x:%s:%s::%s:%s\n' \
    "$(id -un)" "$(id -u)" "$(id -g)" "$HOME" "$PREFIX/bin/bash" > "$ROOT/etc/passwd"
  [ -f "$ROOT/etc/group" ] || printf 'root:x:0:\n%s:x:%s:\n' "$(id -gn)" "$(id -g)" > "$ROOT/etc/group"
  [ -f "$ROOT/etc/resolv.conf" ] || printf 'nameserver 8.8.8.8\nnameserver 8.8.4.4\n' > "$ROOT/etc/resolv.conf"
  if [ ! -x "$ROOT/bin/open-url" ]; then
    cat > "$ROOT/bin/open-url" <<'EOF'
#!/data/data/com.termux/files/usr/bin/bash
# $BROWSER for claude-next: inside proot Android's `am` can't run, so hand the URL to the
# launcher's native helper through its FIFO. It opens the URL with termux-open-url.
[ -p "$CC_BROWSER_FIFO" ] || exit 1
timeout 3 bash -c 'printf "%s\n" "$1" > "$CC_BROWSER_FIFO"' _ "$1"
EOF
    chmod 700 "$ROOT/bin/open-url"
  fi
}
ensure_claude() {
  [ -x "$ROOT/claude" ] && return 0
  local v
  v=$(curl -fs --max-time 10 "$REG/latest" | jq -r .version 2>/dev/null)
  [[ -n $v && $v != null ]] || { offline; return 1; }
  echo "Installing Claude Code $v..."
  update "$v" && return 0
  rm -rf "$ROOT/.update" "$ROOT/claude.new"; echo "Claude Code install failed." >&2; return 1
}
setup() {
  ensure_tools && ensure_storage && ensure_rootfs && ensure_claude || return 1
  [ -e "$PREFIX/bin/claude" ] || [ -L "$PREFIX/bin/claude" ] || ln -s claude-next "$PREFIX/bin/claude"
}

update() {   # version
  local v=$1 d="$ROOT/.update" meta sri
  rm -rf "$d"; mkdir -p "$d"
  meta=$(curl -fs --max-time 10 "$REG/$v") || return 1
  sri=$(jq -r .dist.integrity <<<"$meta")
  curl -f#L -o "$d/pkg.tgz" "$(jq -r .dist.tarball <<<"$meta")" || return 1
  [ "sha512-$(openssl dgst -sha512 -binary "$d/pkg.tgz" | base64 -w0)" = "$sri" ] \
    || { echo "Checksum mismatch." >&2; return 1; }
  # Termux has no gzip (tar -z fails); zstd reads gzip.
  zstd -dcq --format=gzip "$d/pkg.tgz" | tar -x -C "$d" package/claude || return 1
  chmod +x "$d/package/claude" && mv "$d/package/claude" "$ROOT/claude.new"
  # Prove it runs here before swapping. mv keeps running sessions on the old inode.
  [ "$("${PROOT[@]}" "$ROOT/claude.new" --version 2>/dev/null | cut -d' ' -f1)" = "$v" ] \
    || { rm -f "$ROOT/claude.new"; echo "New binary failed --version." >&2; return 1; }
  # Old binary stays in place until the new one is proven; unlinking it is safe for running sessions.
  mv "$ROOT/claude.new" "$ROOT/claude"
  echo "$v" > "$ROOT/VERSION"; rm -rf "$d"
  echo "Updated to $v."
}

# Prompts only on an interactive terminal, so -p / piped / scripted runs never block.
# Picker + update prompt only when this launch opens an interactive session: never for
# --version/--help/-p, or a subcommand (list from `claude --help`, 2.1.310). Bug before v14:
# `claude --version` showed the folder list first.
opens_session() {
  local a
  for a in "$@"; do case "$a" in -v|--version|-h|--help|-p|--print) return 1 ;; esac; done
  case "${1:-}" in
    agents|attach|auth|auto-mode|doctor|gateway|import|install|logs|mcp|plugin|plugins|purge|\
    respawn|rm|setup-token|stop|kill|ultrareview|update|upgrade) return 1 ;;
  esac
}

[[ ${BASH_SOURCE[0]} != "$0" ]] && return 0   # sourced by the launcher tests: functions only

mkdir -p -m 700 "$MSG_DIR"
parse_args "$@"; set -- "${ARGS[@]}"
if [ -t 0 ] && [ -t 1 ] && opens_session "$@"; then
  setup || exit 1
  if ((NG_PICK)); then
    # The Claude key: always ask where; a folder with sessions resumes (Claude shows its own picker).
    pick_project || exit 0
    set -- $(resume_args) "$@"
  else
    # Projects live under Download; anywhere else shows the picker (1 = stay here).
    case "$PWD" in
      "$DL"|"$DL"/*) ;;
      *) pick_dir ;;
    esac
  fi
  current=$(cat "$ROOT/VERSION" 2>/dev/null)
  latest=$(curl -fs --max-time 4 "$REG/latest" | jq -r .version 2>/dev/null)   # offline -> skipped
  if [ -n "$latest" ] && [ "$latest" != "$current" ] \
     && [ "$(printf '%s\n' "$current" "$latest" | sort -V | tail -1)" = "$latest" ] \
     && ask "Claude Code update: ${current:-unknown} -> $latest. Update now?"; then
    update "$latest" || { rm -rf "$ROOT/.update" "$ROOT/claude.new"
      echo "Update failed - continuing with ${current:-current}." >&2; }
  fi
fi

# Browser bridge: `am` (used by termux-open-url) can't run inside proot, so /login etc. couldn't
# open a browser. Claude honours $BROWSER: it points at ~/.cc-min/bin/open-url, which writes the URL
# to a FIFO read by this NATIVE helper (forked before proot starts, so it isn't traced).
export CC_BROWSER_FIFO="$MSG_DIR/$$.browser" BROWSER="$ROOT/bin/open-url"
rm -f "$CC_BROWSER_FIFO"; mkfifo -m 600 "$CC_BROWSER_FIFO"
# Double fork: the helper must NOT be proot's child. proot only exits when it has no children
# left (traced or not), and the helper waits for proot -> v10 deadlocked on exit (terminal hung).
( (
  exec 3<>"$CC_BROWSER_FIFO" </dev/null >/dev/null 2>&1   # read-write: no EOF between writers
  while kill -0 $$ 2>/dev/null; do      # $$ becomes proot after exec; stop when it exits
    read -r -t 5 url <&3 && [[ $url == http://* || $url == https://* ]] && termux-open-url "$url"
  done
  rm -f "$CC_BROWSER_FIFO"
) & )

exec "${PROOT[@]}" -w "$PWD" "$ROOT/claude" --messaging-socket-path "$MSG_DIR/$$.sock" "$@"
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `bash app/src/test/launcher/test_claude_next.sh`
Expected: `launcher tests: 34 passed, 0 failed`, exit 0.

Then prove v14 behaviour is kept. Only additions may differ: no v14 line may be lost except the old header line and the `mkdir -p -m 700 "$MSG_DIR"`, which moved below the sourcing guard. Running the launcher itself is not an option here, because this session already runs inside proot. The Pixel exercises it in Task 5.

```bash
bash -n app/src/main/assets/ngterminal/claude-next && diff /data/data/com.termux/files/usr/bin/claude-next app/src/main/assets/ngterminal/claude-next | grep '^<'
```

Expected: exactly two `<` lines: `< # Claude Code 2.1.296 (Opus 5.5) in a 2.8 MB minimal glibc rootfs.` and `< mkdir -p -m 700 "$MSG_DIR"`. (The mkdir line also reappears as a `>` line further down.)

- [ ] **Step 5: Mutation-check the tests**

Each of these, applied alone to a scratch copy, must make at least one test fail. Restore the file after each.
- In `valid_name`, delete `&& $1 != ..`. Expected failure: `pick_new_dotdot_rejected`.
- In `resume_args`, change `> 0` to `> 5`. Expected failure: `resume_with_sessions`.
- In `ensure_rootfs`, remove the `!=` checksum test block. Expected failure: `rootfs_bad_checksum`.

- [ ] **Step 6: Add the CI step**

In `.github/workflows/run_tests.yml`, insert after the "Execute tests" step:

```yaml
    - name: Launcher tests (claude-next)
      run: bash app/src/test/launcher/test_claude_next.sh
```

- [ ] **Step 7: Commit**

```bash
git add app/src/main/assets/ngterminal/claude-next app/src/test/launcher/test_claude_next.sh .github/workflows/run_tests.yml
git commit -m "feat(claude): launcher v15 - Claude key picker with session counts, first-run setup (A)"
```

---

### Task 2: LauncherVersion + LauncherInstaller (pure Java)

**Files:**
- Create: `/storage/emulated/0/Download/a_Claude_Things/Termux_M3/tools/junit_local_v1.sh`
- Create: `app/src/main/java/com/termux/app/ngterminal/LauncherVersion.java`
- Create: `app/src/main/java/com/termux/app/ngterminal/LauncherInstaller.java`
- Test: `app/src/test/java/com/termux/app/ngterminal/LauncherVersionTest.java`
- Test: `app/src/test/java/com/termux/app/ngterminal/LauncherInstallerTest.java`

**Interfaces:**
- Consumes: the asset's marker line from Task 1.
- Produces:
  - `int LauncherVersion.parse(String script)`: 0 when there is no marker.
  - `boolean LauncherVersion.needsInstall(String existing, int bundled)`: `existing` is null when the file is missing.
  - `enum LauncherInstaller.Result { INSTALLED, UPGRADED, UP_TO_DATE }`.
  - `LauncherInstaller.Result LauncherInstaller.install(File target, File backupDir, String bundled, String stamp) throws IOException`.
  - `static byte[] LauncherInstaller.readAll(InputStream in) throws IOException`.

- [ ] **Step 1: Create the local JUnit runner**

`/storage/emulated/0/Download/a_Claude_Things/Termux_M3/tools/junit_local_v1.sh`:

```bash
#!/data/data/com.termux/files/usr/bin/bash
# Runs pure-Java JUnit 4 tests on the phone (no Android SDK): bash junit_local_v1.sh <src and test .java files>
# JUnit 4.13.2 + Hamcrest 1.3 from Maven Central, SHA-1 pinned, cached in ~/.cache/ngterminal-junit.
set -e
J=$HOME/.cache/ngterminal-junit; mkdir -p "$J"
get() {   # maven path, sha1
  local f="$J/${1##*/}"
  [ -f "$f" ] || curl -sSfL -o "$f" "https://repo1.maven.org/maven2/$1"
  [ "$(sha1sum "$f" | cut -c1-40)" = "$2" ] || { echo "SHA-1 mismatch: $f" >&2; rm -f "$f"; exit 1; }
}
get junit/junit/4.13.2/junit-4.13.2.jar 8ac9e16d933b6fb43bc7f576336b8f4d7eb5ba12
get org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar 42a25dc3219429f0e5d060061f71acb49bf010a0
CP="$J/junit-4.13.2.jar:$J/hamcrest-core-1.3.jar"
O=$(mktemp -d); trap 'rm -rf "$O"' EXIT
javac -d "$O" -cp "$CP" "$@"
classes=()
for f in "$@"; do
  case $f in *Test.java) classes+=("$(sed -n 's/^package \(.*\);/\1/p' "$f" | head -1).$(basename "$f" .java)") ;; esac
done
java -cp "$O:$CP" org.junit.runner.JUnitCore "${classes[@]}"
```

Verify the runner on a known-green test first:
`bash /storage/emulated/0/Download/a_Claude_Things/Termux_M3/tools/junit_local_v1.sh app/src/main/java/com/termux/app/ngterminal/ProcessTree.java app/src/test/java/com/termux/app/ngterminal/ProcessTreeTest.java`
Expected: `OK (3 tests)`.

- [ ] **Step 2: Write the failing tests**

`app/src/test/java/com/termux/app/ngterminal/LauncherVersionTest.java`:

```java
package com.termux.app.ngterminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LauncherVersionTest {
    private static final String V15 = "#!/data/data/com.termux/files/usr/bin/bash\n# ngterminal-launcher: 15\n# rest\n";
    private static final String V14 = "#!/data/data/com.termux/files/usr/bin/bash\n# Claude Code 2.1.296 (Opus 5.5) in a 2.8 MB minimal glibc rootfs.\n";

    @Test public void readsMarkerOnLine2() { assertEquals(15, LauncherVersion.parse(V15)); }
    @Test public void handMadeV14HasNoMarker() { assertEquals(0, LauncherVersion.parse(V14)); }
    @Test public void crlfMarker() { assertEquals(15, LauncherVersion.parse("#!/bin/bash\r\n# ngterminal-launcher: 15\r\n")); }
    @Test public void markerOnlyCountsOnLine2() { assertEquals(0, LauncherVersion.parse("# ngterminal-launcher: 15\n#!/bin/bash\n")); }
    @Test public void garbageAndEmpty() {
        assertEquals(0, LauncherVersion.parse(null));
        assertEquals(0, LauncherVersion.parse(""));
        assertEquals(0, LauncherVersion.parse("#!/bin/bash"));
        assertEquals(0, LauncherVersion.parse("#!/bin/bash\n# ngterminal-launcher: abc\n"));
    }
    @Test public void needsInstall() {
        assertTrue(LauncherVersion.needsInstall(null, 15));     // missing
        assertTrue(LauncherVersion.needsInstall(V14, 15));      // v14, no marker
        assertTrue(LauncherVersion.needsInstall(V15.replace("15", "14"), 15));
        assertFalse(LauncherVersion.needsInstall(V15, 15));     // same
        assertFalse(LauncherVersion.needsInstall(V15.replace("15", "16"), 15));   // newer hand edit stays
    }
}
```

`app/src/test/java/com/termux/app/ngterminal/LauncherInstallerTest.java`:

```java
package com.termux.app.ngterminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class LauncherInstallerTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    private static final String V14 = "#!/bin/bash\n# Claude Code 2.1.296\necho v14\n";
    private static final String V15 = "#!/bin/bash\n# ngterminal-launcher: 15\necho v15\n";

    private String read(File f) throws Exception { return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8); }
    private void write(File f, String s) throws Exception { Files.write(f.toPath(), s.getBytes(StandardCharsets.UTF_8)); }

    @Test public void installsWhenMissing() throws Exception {
        File bin = tmp.newFolder("bin"), target = new File(bin, "claude-next"), backup = new File(tmp.getRoot(), "backup");
        assertEquals(LauncherInstaller.Result.INSTALLED, LauncherInstaller.install(target, backup, V15, "20261025-120000"));
        assertEquals(V15, read(target));
        assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(target.toPath())));
        assertFalse(backup.exists());
    }

    @Test public void upgradesV14KeepingABackup() throws Exception {
        File bin = tmp.newFolder("bin"), target = new File(bin, "claude-next"), backup = new File(tmp.getRoot(), "backup");
        write(target, V14);
        assertEquals(LauncherInstaller.Result.UPGRADED, LauncherInstaller.install(target, backup, V15, "20261025-120000"));
        assertEquals(V15, read(target));
        assertEquals(V14, read(new File(backup, "claude-next-20261025-120000")));
        assertFalse(new File(bin, "claude-next.ngnew").exists());
    }

    @Test public void leavesSameOrNewerAlone() throws Exception {
        File bin = tmp.newFolder("bin"), target = new File(bin, "claude-next"), backup = new File(tmp.getRoot(), "backup");
        String v16 = V15.replace("15", "16");
        write(target, v16);
        assertEquals(LauncherInstaller.Result.UP_TO_DATE, LauncherInstaller.install(target, backup, V15, "20261025-120000"));
        assertEquals(v16, read(target));
        write(target, V15);
        assertEquals(LauncherInstaller.Result.UP_TO_DATE, LauncherInstaller.install(target, backup, V15, "20261025-120000"));
        assertFalse(backup.exists());
    }

    /** Review Focus 1: a bash still reading the old launcher must keep reading the old file (rename, not overwrite). */
    @Test public void readerOfOldFileKeepsOldContent() throws Exception {
        File bin = tmp.newFolder("bin"), target = new File(bin, "claude-next");
        write(target, V14);
        try (FileInputStream reader = new FileInputStream(target)) {
            LauncherInstaller.install(target, new File(tmp.getRoot(), "backup"), V15, "20261025-120000");
            assertEquals(V14, new String(LauncherInstaller.readAll(reader), StandardCharsets.UTF_8));
        }
        assertEquals(V15, read(target));
    }

    @Test public void readAllReadsEverything() throws Exception {
        File f = tmp.newFile("big");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5000; i++) sb.append("line ").append(i).append('\n');
        write(f, sb.toString());
        try (FileInputStream in = new FileInputStream(f)) {
            assertTrue(new String(LauncherInstaller.readAll(in), StandardCharsets.UTF_8).endsWith("line 4999\n"));
        }
    }
}
```

- [ ] **Step 3: Run them to verify they fail**

Run: `bash /storage/emulated/0/Download/a_Claude_Things/Termux_M3/tools/junit_local_v1.sh app/src/test/java/com/termux/app/ngterminal/LauncherVersionTest.java app/src/test/java/com/termux/app/ngterminal/LauncherInstallerTest.java`
Expected: compile errors `cannot find symbol … LauncherVersion` / `LauncherInstaller`.

- [ ] **Step 4: Implement**

`app/src/main/java/com/termux/app/ngterminal/LauncherVersion.java`:

```java
package com.termux.app.ngterminal;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** NGTerminal: the "# ngterminal-launcher: N" marker on line 2 of claude-next. 0 = no marker (v14 and older). */
public final class LauncherVersion {
    private static final Pattern MARKER = Pattern.compile("# ngterminal-launcher: (\\d{1,6})");

    private LauncherVersion() {}

    public static int parse(String script) {
        if (script == null) return 0;
        String[] lines = script.split("\n", 3);
        if (lines.length < 2) return 0;
        Matcher m = MARKER.matcher(lines[1].trim());
        return m.matches() ? Integer.parseInt(m.group(1)) : 0;
    }

    /** Install when the file is missing or has an older (or no) marker; a newer hand edit is left alone. */
    public static boolean needsInstall(String existing, int bundled) {
        return existing == null || parse(existing) < bundled;
    }
}
```

`app/src/main/java/com/termux/app/ngterminal/LauncherInstaller.java`:

```java
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
```

- [ ] **Step 5: Run them to verify they pass**

Run: `bash /storage/emulated/0/Download/a_Claude_Things/Termux_M3/tools/junit_local_v1.sh app/src/main/java/com/termux/app/ngterminal/LauncherVersion.java app/src/main/java/com/termux/app/ngterminal/LauncherInstaller.java app/src/test/java/com/termux/app/ngterminal/LauncherVersionTest.java app/src/test/java/com/termux/app/ngterminal/LauncherInstallerTest.java`
Expected: `OK (11 tests)`.

Mutation check: replace the temp-file-and-rename with a direct `write(target, bundled)`. Expected: `readerOfOldFileKeepsOldContent` FAILS. Then restore.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/termux/app/ngterminal/LauncherVersion.java app/src/main/java/com/termux/app/ngterminal/LauncherInstaller.java app/src/test/java/com/termux/app/ngterminal/LauncherVersionTest.java app/src/test/java/com/termux/app/ngterminal/LauncherInstallerTest.java
git commit -m "feat(claude): launcher version marker + atomic installer with backup (A)"
```

---

### Task 3: ClaudeKey rule + CLAUDE icon

**Files:**
- Create: `app/src/main/java/com/termux/app/ngterminal/ClaudeKey.java`
- Test: `app/src/test/java/com/termux/app/ngterminal/ClaudeKeyTest.java`
- Create: `termux-shared/src/main/res/drawable/ic_ng_key_claude.xml`
- Modify: `termux-shared/src/main/java/com/termux/shared/terminal/io/extrakeys/ExtraKeyIcons.java` (after line 21)
- Modify: `termux-shared/src/test/java/com/termux/shared/terminal/io/extrakeys/ExtraKeyIconsTest.java` (in `mapsSpecSpecifiedKeys`)

**Interfaces:**
- Consumes: `ProcessTree.childPids(File, int)` and `ProcessTree.commandLine(File, int)` (ng.5).
- Produces:
  - `ClaudeKey.COMMAND` = `"claude-next --ng-pick\r"`;
  - `boolean ClaudeKey.isIdleShell(File procRoot, int pid)`;
  - drawable `R.drawable.ic_ng_key_claude`;
  - `ExtraKeyIcons.iconFor("CLAUDE")`.

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/termux/app/ngterminal/ClaudeKeyTest.java`:

```java
package com.termux.app.ngterminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ClaudeKeyTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private void proc(File root, int pid, int ppid, String cmdline) throws Exception {
        File d = new File(root, String.valueOf(pid)); d.mkdirs();
        Files.write(new File(d, "stat").toPath(), (pid + " (x) S " + ppid + " 1 1 0").getBytes(StandardCharsets.UTF_8));
        Files.write(new File(d, "cmdline").toPath(), cmdline.getBytes(StandardCharsets.UTF_8));
    }

    @Test public void termuxTabShellAtPromptIsIdle() throws Exception {   // as seen on the phone: "bash -l", parent = app
        File root = tmp.newFolder("proc");
        proc(root, 100, 50, "/data/data/com.termux/files/usr/bin/bash\0-l\0");
        assertTrue(ClaudeKey.isIdleShell(root, 100));
    }

    @Test public void loginDashShellIsIdle() throws Exception {
        File root = tmp.newFolder("proc");
        proc(root, 100, 50, "-bash\0");
        assertTrue(ClaudeKey.isIdleShell(root, 100));
    }

    @Test public void shellRunningSomethingIsBusy() throws Exception {
        File root = tmp.newFolder("proc");
        proc(root, 100, 50, "/data/data/com.termux/files/usr/bin/bash\0-l\0");
        proc(root, 200, 100, "proot\0-r\0x\0");
        assertFalse(ClaudeKey.isIdleShell(root, 100));
    }

    /** Review Focus 3: a RUN_COMMAND tab running python is not a prompt; typing there would feed python. */
    @Test public void nonShellIsNotIdle() throws Exception {
        File root = tmp.newFolder("proc");
        proc(root, 100, 50, "/data/data/com.termux/files/usr/bin/python3\0script.py\0");
        assertFalse(ClaudeKey.isIdleShell(root, 100));
    }

    @Test public void finishedSessionIsNotIdle() throws Exception {
        File root = tmp.newFolder("proc");
        assertFalse(ClaudeKey.isIdleShell(root, 0));
        assertFalse(ClaudeKey.isIdleShell(root, -1));
        assertFalse(ClaudeKey.isIdleShell(root, 4242));   // vanished: no /proc entry
    }

    @Test public void commandOpensThePickerAndPressesEnter() {
        assertEquals("claude-next --ng-pick\r", ClaudeKey.COMMAND);
    }
}
```

In `ExtraKeyIconsTest.mapsSpecSpecifiedKeys()`, add after the `WAKELOCK` line:

```java
        assertEquals(R.drawable.ic_ng_key_claude, ExtraKeyIcons.iconFor("CLAUDE"));
```

- [ ] **Step 2: Run ClaudeKeyTest to verify it fails**

Run: `bash /storage/emulated/0/Download/a_Claude_Things/Termux_M3/tools/junit_local_v1.sh app/src/main/java/com/termux/app/ngterminal/ProcessTree.java app/src/test/java/com/termux/app/ngterminal/ClaudeKeyTest.java`
Expected: compile error `cannot find symbol … ClaudeKey`. (`ExtraKeyIconsTest` needs Android `R`; it goes RED and GREEN in CI with the Task 6 push, and Task 6 checks its count.)

- [ ] **Step 3: Implement**

`app/src/main/java/com/termux/app/ngterminal/ClaudeKey.java`:

```java
package com.termux.app.ngterminal;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** NGTerminal: rules for the Claude extra key. Pure Java, unit-tested. */
public final class ClaudeKey {
    /** Typed into the tab: the launcher asks which folder to open and resumes when that folder has sessions. */
    public static final String COMMAND = "claude-next --ng-pick\r";

    private static final Set<String> SHELLS = new HashSet<>(Arrays.asList("bash", "sh", "zsh", "fish", "dash", "ash", "mksh", "login"));

    private ClaudeKey() {}

    /** Idle = the tab's process is a shell with no child process, so typed text reaches its prompt. */
    public static boolean isIdleShell(File procRoot, int pid) {
        if (pid <= 0) return false;
        String cmd = ProcessTree.commandLine(procRoot, pid);
        if (cmd.isEmpty()) return false;
        String exe = cmd.split(" ", 2)[0];
        String name = exe.substring(exe.lastIndexOf('/') + 1);
        if (name.startsWith("-")) name = name.substring(1);   // login shells show as "-bash"
        return SHELLS.contains(name) && ProcessTree.childPids(procRoot, pid).isEmpty();
    }
}
```

`termux-shared/src/main/res/drawable/ic_ng_key_claude.xml`. The path data is copied verbatim from `https://fonts.gstatic.com/s/i/short-term/release/materialsymbolsrounded/auto_awesome/default/24px.svg`, the same source as `ic_ng_key_coffee.xml`, whose path matches that source byte for byte:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Material Symbols Rounded "auto_awesome" (Google, Apache 2.0): the Claude key. Tinted at runtime by ExtraKeysView. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="960" android:viewportHeight="960">
    <group android:translateY="960">
        <path android:fillColor="#FFFFFFFF" android:pathData="m706-706-70-32q-6-3-8.5-8t-2.5-10q0-5 2.5-10t8.5-8l70-32 32-70q3-6 8-9t10-3q5 0 10 3t8 9l32 70 70 32q6 3 9 8t3 10q0 5-3 10t-9 8l-70 32-32 70q-3 6-8 8.5t-10 2.5q-5 0-10-2.5t-8-8.5l-32-70ZM260-380l-160-73q-9-4-13-11.5T83-480q0-8 4-15.5t13-11.5l160-73 73-160q4-9 11.5-13t15.5-4q8 0 15.5 4t11.5 13l73 160 160 73q9 4 13 11.5t4 15.5q0 8-4 15.5T620-453l-160 73-73 160q-4 9-11.5 13t-15.5 4q-8 0-15.5-4T333-220l-73-160Zm100 26 40-86 86-40-86-40-40-86-40 86-86 40 86 40 40 86Zm350 204-70-32q-6-3-9-8t-3-10q0-5 3-10t9-8l70-32 32-70q3-6 8-9t10-3q5 0 10 3t8 9l32 70 70 32q6 3 9 8t3 10q0 5-3 10t-9 8l-70 32-32 70q-3 6-8 9t-10 3q-5 0-10-3t-8-9l-32-70ZM360-480Z"/>
    </group>
</vector>
```

In `ExtraKeyIcons.java`, add after `ICONS.put("WAKELOCK", R.drawable.ic_ng_key_coffee);`:

```java
        ICONS.put("CLAUDE", R.drawable.ic_ng_key_claude);
```

- [ ] **Step 4: Run ClaudeKeyTest to verify it passes**

Run: the same command as Step 2, plus `app/src/main/java/com/termux/app/ngterminal/ClaudeKey.java` in the file list.
Expected: `OK (6 tests)`.

Mutation check: remove the `SHELLS.contains(name) &&` condition. Expected: `nonShellIsNotIdle` FAILS. Then restore.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/termux/app/ngterminal/ClaudeKey.java app/src/test/java/com/termux/app/ngterminal/ClaudeKeyTest.java termux-shared/src/main/res/drawable/ic_ng_key_claude.xml termux-shared/src/main/java/com/termux/shared/terminal/io/extrakeys/ExtraKeyIcons.java termux-shared/src/test/java/com/termux/shared/terminal/io/extrakeys/ExtraKeyIconsTest.java
git commit -m "feat(claude): Claude key idle-shell rule + auto_awesome icon (A)"
```

---

### Task 4: Wire the key, default layout, ng.10

**Files:**
- Create: `app/src/main/java/com/termux/app/ngterminal/ClaudeLauncher.java`
- Modify: `app/src/main/java/com/termux/app/terminal/io/TermuxTerminalExtraKeys.java:45` (new case before `WAKELOCK`)
- Modify: `app/src/main/java/com/termux/app/TermuxActivity.java` (new method `onClaudeKey()` next to `refreshWakeLockKey()`, ~line 800; imports)
- Modify: `termux-shared/src/main/java/com/termux/shared/settings/properties/TermuxPropertyConstants.java:285` (`DEFAULT_IVALUE_EXTRA_KEYS`)
- Modify: `app/build.gradle:46` (versionName)

**Interfaces:**
- Consumes: `ClaudeKey.COMMAND`, `ClaudeKey.isIdleShell` (Task 3); `LauncherInstaller.install`, `LauncherInstaller.readAll`, `LauncherInstaller.Result` (Task 2); asset `ngterminal/claude-next` (Task 1).
- Produces: `TermuxActivity.onClaudeKey()`, plus the key name `CLAUDE` handled by `TermuxTerminalExtraKeys`.

This task has no JVM-testable logic: all of it is Android glue around the tested pieces. It is verified on the Pixel in Task 5, and its RED is the Task 5 checks run against ng.9.

- [ ] **Step 1: ClaudeLauncher (asset → $PREFIX/bin)**

`app/src/main/java/com/termux/app/ngterminal/ClaudeLauncher.java`:

```java
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
```

- [ ] **Step 2: The CLAUDE case**

In `TermuxTerminalExtraKeys.onTerminalExtraKeyButtonClick`, insert before `} else if ("WAKELOCK".equals(key)) {`:

```java
        } else if ("CLAUDE".equals(key)) {
            // NGTerminal: set up / open Claude (launcher picker; asks first when the tab is busy)
            mTermuxTerminalViewClient.getActivity().onClaudeKey();
```

- [ ] **Step 3: TermuxActivity.onClaudeKey()**

Add, directly above `public void refreshWakeLockKey() {`:

```java
    /** NGTerminal: the Claude extra key. Installs/updates the launcher, then types it into an idle tab or offers a new tab. */
    public void onClaudeKey() {
        try {
            if (ClaudeLauncher.ensureInstalled(this) == LauncherInstaller.Result.UPGRADED)
                showToast("Claude launcher updated - old copy in ~/.ngterminal/backup", true);
        } catch (IOException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Claude launcher install failed", e);
            showToast("Could not install the Claude launcher: " + e.getMessage(), true);
            return;
        }
        TerminalSession current = getCurrentSession();
        if (current != null && ClaudeKey.isIdleShell(new File("/proc"), current.getPid())) {
            current.write(ClaudeKey.COMMAND);
            return;
        }
        new MaterialAlertDialogBuilder(new ContextThemeWrapper(this, R.style.Theme_NGTerminal_M3))
            .setTitle("Claude")
            .setMessage("Open a new Claude session?")
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton("Open", (d, w) -> {
                TerminalSession before = getCurrentSession();
                mTermuxTerminalSessionClient.addNewSession(false, null);
                TerminalSession created = getCurrentSession();
                // Session limit (8) or no service: no new tab -> type nothing (never into the busy tab)
                if (created != null && created != before) created.write(ClaudeKey.COMMAND);
            })
            .show();
    }
```

Imports to add to `TermuxActivity.java` if missing:
- `android.view.ContextThemeWrapper`
- `com.google.android.material.dialog.MaterialAlertDialogBuilder`
- `com.termux.app.ngterminal.ClaudeKey`
- `com.termux.app.ngterminal.ClaudeLauncher`
- `com.termux.app.ngterminal.LauncherInstaller`
- `com.termux.shared.logger.Logger`
- `java.io.File`
- `java.io.IOException`

Confirm `LOG_TAG` exists with `grep -n 'LOG_TAG =' app/src/main/java/com/termux/app/TermuxActivity.java`. If it doesn't, use the string `"TermuxActivity"`.

- [ ] **Step 4: Default key layout**

In `TermuxPropertyConstants.java`, replace the line

```java
    public static final String DEFAULT_IVALUE_EXTRA_KEYS = "[['ESC','/',{key: '-', popup: '|'},'HOME','UP','END','PGUP'], ['TAB','CTRL','ALT','LEFT','DOWN','RIGHT','PGDN']]"; // Double row
```

with

```java
    // NGTerminal: Claude key first, Esc third, keep-awake + keyboard keys (Termux's default: ESC / - HOME UP END PGUP | TAB CTRL ALT LEFT DOWN RIGHT PGDN)
    public static final String DEFAULT_IVALUE_EXTRA_KEYS = "[['CLAUDE','/',{key: ESC, display: 'Esc'},'HOME','UP','END','WAKELOCK'], ['TAB',{key: CTRL, display: 'Ctrl'},{macro: 'CTRL j', display: '\u21b5'},'LEFT','DOWN','RIGHT','KEYBOARD']]"; // Double row
```

- [ ] **Step 5: versionName**

`app/build.gradle:46`: `        versionName "0.118.3-ng.10"`. Nothing may follow the closing quote.

- [ ] **Step 6: Local checks that need no Android SDK**

```bash
grep -n '^        versionName "0.118.3-ng.10"$' app/build.gradle
grep -c '"CLAUDE".equals(key)' app/src/main/java/com/termux/app/terminal/io/TermuxTerminalExtraKeys.java
bash app/src/test/launcher/test_claude_next.sh
bash /storage/emulated/0/Download/a_Claude_Things/Termux_M3/tools/junit_local_v1.sh app/src/main/java/com/termux/app/ngterminal/ProcessTree.java app/src/main/java/com/termux/app/ngterminal/ClaudeKey.java app/src/main/java/com/termux/app/ngterminal/LauncherVersion.java app/src/main/java/com/termux/app/ngterminal/LauncherInstaller.java app/src/test/java/com/termux/app/ngterminal/ProcessTreeTest.java app/src/test/java/com/termux/app/ngterminal/ClaudeKeyTest.java app/src/test/java/com/termux/app/ngterminal/LauncherVersionTest.java app/src/test/java/com/termux/app/ngterminal/LauncherInstallerTest.java
```

Expected:
- line 46 printed;
- `1`;
- `launcher tests: 34 passed, 0 failed`;
- `OK (20 tests)`.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/termux/app/ngterminal/ClaudeLauncher.java app/src/main/java/com/termux/app/terminal/io/TermuxTerminalExtraKeys.java app/src/main/java/com/termux/app/TermuxActivity.java termux-shared/src/main/java/com/termux/shared/settings/properties/TermuxPropertyConstants.java app/build.gradle
git commit -m "feat(claude): Claude extra key - launcher install, busy-tab dialog, default layout; ng.10 (A)"
```

---

### Task 5: Pixel checks (RED on ng.9)

**Files:**
- Create: `/storage/emulated/0/Download/a_Claude_Things/Termux_M3/tools/checks_ng10.sh`
- Modify: `/storage/emulated/0/Download/a_Claude_Things/Termux_M3/tools/checks_all_v1.sh` (source `checks_ng10.sh` after `checks_ng8.sh`)

**Interfaces:**
- Consumes these helpers from `pixel_check_v1.sh` lines 8–58: `a`, `ok`, `bad`, `shot`, `rx`, `launch`, `tap_desc`, `tap_text`, `count_tabs`, `ttype`, plus `$S`, `$F`, `$OUT`.
- Also consumes `claude-next --ng-pick`, the `CLAUDE` key name, and the dialog text "Open a new Claude session?".

- [ ] **Step 1: Write the checks**

`/storage/emulated/0/Download/a_Claude_Things/Termux_M3/tools/checks_ng10.sh`:

```bash
# ng.10: the Claude key (sub-project A), sourced by pixel_check_v1.sh after checks_all; uses its helpers.
# Runs a FRESH setup on the Pixel: moves ~/.cc-min aside and removes proot + the launcher first.
ROW1='CLAUDE / ESC HOME UP END WAKELOCK'
LAYOUT="extra-keys = [['CLAUDE','/',{key: ESC, display: 'Esc'},'HOME','UP','END','WAKELOCK'], ['TAB',{key: CTRL, display: 'Ctrl'},{macro: 'CTRL j', display: '↵'},'LEFT','DOWN','RIGHT','KEYBOARD']]"
row1(){ a uiautomator dump /sdcard/ng_ui.xml >/dev/null; a cat /sdcard/ng_ui.xml | python3 -c "
import re,sys
x=sys.stdin.read()
keys={'CLAUDE','/','ESC','HOME','UP','END','WAKELOCK','PGUP','-','DRAWER'}
k=[(int(t),int(l),d) for d,l,t in re.findall(r'content-desc=\"([^\"]+)\"[^>]*bounds=\"\[(\d+),(\d+)\]',x) if d in keys]
top=min(t for t,l,d in k) if k else 0
print(' '.join(d for t,l,d in sorted(k) if t==top))"; }
procs(){ a ps -A -o ARGS 2>/dev/null; }
has_proc(){ procs | grep -q -- "$1"; }
wait_proc(){ local i; for i in $(seq 1 "$2"); do has_proc "$1" && return 0; sleep 2; done; return 1; }
kill_claude(){ rx 'pkill -f "/.cc-min/claude" ; pkill -f "claude-next --ng-pick"; true' >/dev/null; sleep 2; }

# 1 user layout from termux.properties (how the user's phone is configured)
rx "cp ~/.termux/termux.properties ~/.termux/termux.properties.ng10bak 2>/dev/null; touch ~/.termux/termux.properties
awk 'skip{skip=/\\\\\$/;next} /^extra-keys[ ]*=/{skip=/\\\\\$/;next} {print}' ~/.termux/termux.properties.ng10bak > ~/.termux/termux.properties 2>/dev/null
echo \"$LAYOUT\" >> ~/.termux/termux.properties; termux-reload-settings" >/dev/null
a am force-stop com.termux; launch; sleep 3
r=$(row1); [ "$r" = "$ROW1" ] && ok ng10_key_bar || bad ng10_key_bar "row 1 is [$r]"; shot ng10_key_bar

# 2 built-in default (no extra-keys in the file) parses to the same bar
rx 'mv ~/.termux/termux.properties ~/.termux/termux.properties.ng10user; termux-reload-settings' >/dev/null
a am force-stop com.termux; launch; sleep 3
r=$(row1); [ "$r" = "$ROW1" ] && ok ng10_default_layout || bad ng10_default_layout "row 1 is [$r]"
rx 'mv ~/.termux/termux.properties.ng10user ~/.termux/termux.properties; termux-reload-settings' >/dev/null

# 3 fresh setup: no proot, no rootfs, no launcher -> one tap ends at the picker
# storage granted up front (test device), else termux-setup-storage waits on its permission dialog
a pm grant com.termux android.permission.READ_EXTERNAL_STORAGE; a pm grant com.termux android.permission.WRITE_EXTERNAL_STORAGE
rx 'apt-get remove -y proot >/dev/null 2>&1; mv ~/.cc-min ~/.cc-min.ng10bak 2>/dev/null; rm -f $PREFIX/bin/claude-next $PREFIX/bin/claude; command -v proot || echo no-proot' | grep -q no-proot \
  && ok ng10_fresh_precondition || bad ng10_fresh_precondition "proot still installed"
a am force-stop com.termux; launch; sleep 3; tap_desc "CLAUDE"; sleep 2
wait_proc "claude-next --ng-pick" 2 || bad ng10_launcher_started "no claude-next --ng-pick process"
for i in $(seq 1 120); do rx 'test -x ~/.cc-min/claude && echo y' | grep -qx y && break; sleep 5; done   # ~10 min: the binary is a large npm download
shot ng10_fresh_picker
v=$(rx 'cat ~/.cc-min/VERSION'); b=$(rx 'env -u LD_PRELOAD proot -r ~/.cc-min -b /proc -b /dev -b /sys -b /system -b /apex -b /data/data/com.termux/files ~/.cc-min/claude --version' | cut -d' ' -f1)
[ -n "$v" ] && [ "$v" = "$b" ] && ok "ng10_fresh_setup (Claude $v)" || bad ng10_fresh_setup "VERSION [$v] vs --version [$b]"
rx 'head -2 $PREFIX/bin/claude-next | tail -1' | grep -qx '# ngterminal-launcher: 15' && ok ng10_launcher_installed || bad ng10_launcher_installed "marker missing"
[ "$(rx 'readlink $PREFIX/bin/claude')" = claude-next ] && ok ng10_claude_symlink || bad ng10_claude_symlink "no claude -> claude-next"
# new folder -> new session (no --resume)
T="ng10_$(date +%H%M%S)"; a input text n; a input keyevent 66; sleep 1; a input text "$T"; a input keyevent 66; sleep 8
procs | grep '/.cc-min/claude' | grep -v -- '--resume' | grep -q messaging && ok ng10_new_folder_new_session || bad ng10_new_folder_new_session "no plain claude process"
shot ng10_fresh_claude_first_run
kill_claude

# 4 resume: the root gets a (fake) session -> Enter -> claude --resume
rx 's=$(printf %s /storage/emulated/0/Download/a_Claude_Things | sed "s/[^A-Za-z0-9]/-/g"); mkdir -p ~/.claude/projects/$s; touch ~/.claude/projects/$s/ng10-fake.jsonl' >/dev/null
a am force-stop com.termux; launch; sleep 3; tap_desc "CLAUDE"; sleep 4; shot ng10_picker_counts
a input keyevent 66; sleep 8
procs | grep '/.cc-min/claude' | grep -q -- '--resume' && ok ng10_sessions_resume || bad ng10_sessions_resume "no claude --resume process"
kill_claude
rx 's=$(printf %s /storage/emulated/0/Download/a_Claude_Things | sed "s/[^A-Za-z0-9]/-/g"); rm -f ~/.claude/projects/$s/ng10-fake.jsonl' >/dev/null

# 5 busy tab: ask; Cancel changes nothing; Open adds a tab running the picker
a am force-stop com.termux; launch; sleep 3; ttype 'sleep 999'; sleep 2
n0=$(count_tabs); tap_desc "CLAUDE"; sleep 2
a uiautomator dump /sdcard/ng_ui.xml >/dev/null; a cat /sdcard/ng_ui.xml | grep -q 'text="Open a new Claude session?"' && ok ng10_busy_asks || bad ng10_busy_asks "no dialog"
shot ng10_busy_dialog
tap_text "Cancel" >/dev/null; sleep 2
[ "$(count_tabs)" = "$n0" ] && ! has_proc "claude-next --ng-pick" && ok ng10_busy_cancel || bad ng10_busy_cancel "tab added or command typed"
tap_desc "CLAUDE"; sleep 2; tap_text "Open" >/dev/null; sleep 4
[ "$(count_tabs)" = "$((n0 + 1))" ] && wait_proc "claude-next --ng-pick" 3 && ok ng10_busy_open || bad ng10_busy_open "tabs $n0 -> $(count_tabs)"
a input text q; a input keyevent 66; sleep 1
rx 'pkill -x sleep; true' >/dev/null

# 6 Review Focus 2: 8 tabs, busy, Open -> Termux refuses the 9th; nothing typed into the busy tab
a am force-stop com.termux; launch; sleep 3
for i in 1 2 3 4 5 6 7; do tap_desc "New tab"; sleep 1; done
ttype 'sleep 20'; sleep 1; tap_desc "CLAUDE"; sleep 2; tap_text "Open" >/dev/null; sleep 2
shot ng10_max_sessions; tap_text "OK" >/dev/null; sleep 25
has_proc "claude-next --ng-pick" && bad ng10_max_sessions_no_typing "command ran in the busy tab after sleep ended" || ok ng10_max_sessions_no_typing

# 7 upgrade from the user's hand-made v14: replaced, backup kept byte-identical
V14=/data/data/com.termux/files/usr/bin/claude-next   # this phone's launcher (v14 until the user installs ng.10)
if [ "$(sed -n 2p "$V14")" != "# ngterminal-launcher: 15" ]; then
  h14=$(sha256sum < "$V14" | cut -c1-64)
  timeout 60 adb -s "$S" exec-in run-as com.termux sh -c "cat > $F/usr/bin/claude-next; chmod 700 $F/usr/bin/claude-next" < "$V14"
  rx 'rm -rf ~/.ngterminal/backup' >/dev/null
  a am force-stop com.termux; launch; sleep 3; tap_desc "CLAUDE"; sleep 4
  hb=$(rx 'sha256sum ~/.ngterminal/backup/claude-next-* | cut -c1-64')
  rx 'head -2 $PREFIX/bin/claude-next | tail -1' | grep -qx '# ngterminal-launcher: 15' && [ "$hb" = "$h14" ] \
    && ok ng10_upgrade_v14_with_backup || bad ng10_upgrade_v14_with_backup "marker or backup wrong (backup $hb)"
  a input text q; a input keyevent 66; sleep 1
else
  echo "INFO ng10_upgrade_v14_with_backup skipped: this phone already runs v15"
fi

# restore the Pixel: keep the fresh rootfs if setup made one, else put the old one back (RED run on ng.9)
rx 'if [ -x ~/.cc-min/claude ]; then rm -rf ~/.cc-min.ng10bak; elif [ -d ~/.cc-min.ng10bak ]; then rm -rf ~/.cc-min; mv ~/.cc-min.ng10bak ~/.cc-min; fi
mv ~/.termux/termux.properties.ng10bak ~/.termux/termux.properties 2>/dev/null; termux-reload-settings' >/dev/null
```

Add to `checks_all_v1.sh`, after the `checks_ng8.sh` line:

```bash
. /storage/emulated/0/Download/a_Claude_Things/Termux_M3/tools/checks_ng10.sh
```

- [ ] **Step 2: Syntax check + RED on ng.9 (the Pixel still runs ng.9)**

Run:
```bash
bash -n /storage/emulated/0/Download/a_Claude_Things/Termux_M3/tools/checks_ng10.sh
bash /tmp/claude-10473/-storage-emulated-0-Download-a-Claude-Things/356f7847-4897-439b-8efe-71ea6179853b/scratchpad/run_checks_only.sh ng10_red_on_ng9 /storage/emulated/0/Download/a_Claude_Things/Termux_M3/tools/checks_ng10.sh
```

Expected on ng.9:
- `ng10_key_bar` PASS. The bar is properties-driven and ng.9 shows `CLAUDE` as a text key.
- `ng10_default_layout` FAIL: the old default.
- `ng10_launcher_started` and `ng10_fresh_setup` FAIL: the key does nothing.
- `ng10_busy_asks` FAIL.

On RED the script's last line puts the old `~/.cc-min` back. proot comes back with the harness's own `pkg install -y proot` (`claude_runtime` check).

- [ ] **Step 3: Commit** (tools live outside the repo; the ledger records this)

Append to the ledger: `Task 5: checks_ng10.sh written; RED on ng.9: <list of FAILs>`.

---

### Task 6: Push, CI, Pixel GREEN + full regression

**Files:** none new.

- [ ] **Step 1: Local gate before the push**

Run the Task 4 Step 6 commands again. Expected: all green, the same counts.

- [ ] **Step 2: Push once and watch the run IDs**

```bash
git push origin HEAD
gh run list -R NaveenGokul/termux-app --branch github-releases/m3-extra-keys --limit 2 --json databaseId,name,headSha
```

Watch each run ID with `gh run watch <id> --exit-status`. Expected:
- Build success.
- Unit tests success, with `TESTS EXECUTED: 350`: 330, plus `ClaudeKeyTest` 7, `LauncherVersionTest` 6, `LauncherInstallerTest` 5 and `MinSdkGuardTest` 1, plus 1 more in `ExtraKeyIconsTest` (now 4). Also `launcher tests: 37 passed, 0 failed`. (Updated after the final review's fix pass.)

If the count differs, find out why before going on.

- [ ] **Step 3: Download and verify the APK**

Expect `NGTerminal_<date>_v0.118.3-ng.10.apk`, `SHA256SUMS` OK, signer `b6da01480eefd5fb…`, `versionCode='1002' versionName='0.118.3-ng.10'`.

- [ ] **Step 4: Pixel GREEN (unlocked and charging)**

```bash
cd /storage/emulated/0/Download/a_Claude_Things/Termux_M3/tools
PIXEL=<current ip:port> EXTRA_CHECKS=$PWD/checks_all_v1.sh bash pixel_check_v1.sh ng10 <apk>
```

Expected: every line PASS, `RESULT: PASS (ng10)`. Look at the screenshots in `Download/NGTerminal_pixel/ng10/`:
- `ng10_key_bar.png`: sparkles in slot 1, Esc in slot 3;
- `ng10_fresh_picker.png`: the picker with counts;
- `ng10_fresh_claude_first_run.png`: Claude's login screen;
- `ng10_busy_dialog.png`.

---

### Task 7: The user's phone + records

- [ ] **Step 1: Hand over, never self-install.** Installing an update of `com.termux` kills this Claude session. Copy the APK to `/storage/emulated/0/Download/` and ask the user to install it themselves.
- [ ] **Step 2: Phone `termux.properties`** (with the user's go-ahead):
  - Archive it to `~/.termux/Archive/termux-v1_<YYYYMMDD_HHMMSS>.properties`.
  - Replace the `extra-keys = …` block (2 lines) with the `LAYOUT` line from `checks_ng10.sh`.
  - Delete the `extra-keys-style = all` line.
  - Run `termux-reload-settings`.
- [ ] **Step 3: First tap on the phone.** Expect the toast "Claude launcher updated - old copy in ~/.ngterminal/backup", then the picker. Check that `~/.ngterminal/backup/claude-next-*` has the same sha256 as the old v14.
- [ ] **Step 4: Records:**
  - `Termux_M3/RELEASE_NOTES.md` entry for ng.10.
  - Ledger line `Task 7: complete`.
  - Vault `App/Claude/2026-10-25.md` (or the day it ships) and `Other_Topics/Tools/Claude_Code_Latest_on_Termux.md`: the launcher v15 ships in the NGTerminal APK, plus the fresh-setup recipe.
  - Memory `project_termux_claude_version_ceiling.md`, mirrored to `App/Claude/Config/memory/…`.
