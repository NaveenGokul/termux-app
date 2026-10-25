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
check pick_new_slash_rejected "RC:1" "$(pick $'n\nalpha/sub\nq')"          # parent exists: only the / check stops it
check pick_new_slash_not_made "no" "$([ -e "$PROJECTS/alpha/sub" ] && echo yes || echo no)"
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
# a well-formed .deb with the wrong libs: dpkg-deb would unpack it, only the pinned SHA-256 stops it
mkdir -p "$T/fakedeb/DEBIAN" "$T/fakedeb/usr/lib/aarch64-linux-gnu"; chmod 755 "$T/fakedeb/DEBIAN"   # dpkg-deb refuses 0700 (umask 077)
printf 'Package: libc6\nVersion: 0\nArchitecture: arm64\nMaintainer: test\nDescription: fake\n' > "$T/fakedeb/DEBIAN/control"
for l in "${LIBS[@]}"; do echo fake > "$T/fakedeb/usr/lib/aarch64-linux-gnu/$l"; done
dpkg-deb --build "$T/fakedeb" "$T/fake.deb" >/dev/null 2>&1
check fake_deb_built "yes" "$([ -s "$T/fake.deb" ] && echo yes)"
curl() { local o; while [ $# -gt 0 ]; do [ "$1" = -o ] && o=$2; shift; done; cp "$T/fake.deb" "$o"; }
check rootfs_wrong_package "1" "$(ensure_rootfs >/dev/null 2>&1; echo $?)"
check rootfs_wrong_package_no_libs "0" "$(ls "$ROOT/lib" 2>/dev/null | wc -l | tr -d ' ')"
unset -f curl

# review I2: rootfs links are created only when missing, never written through a symlinked dir into Termux's own bin
R2="$T/root2"; mkdir -p "$R2/lib" "$T/realbin"; for l in "${LIBS[@]}"; do echo lib > "$R2/lib/$l"; done
echo REAL > "$T/realbin/sh"; ln -s "$T/realbin" "$R2/bin"
check rootfs_keeps_existing_links "0|REAL" "$(ROOT=$R2; ensure_rootfs >/dev/null 2>&1; echo "$?|$([ -L "$T/realbin/sh" ] && echo LINK || cat "$T/realbin/sh")")"
# review minor 4 (re-graded): an empty (truncated) lib counts as missing, so setup repairs it instead of trusting it
R3="$T/root3"; mkdir -p "$R3/lib"; for l in "${LIBS[@]}"; do echo lib > "$R3/lib/$l"; done; : > "$R3/lib/libc.so.6"
curl() { return 6; }
check rootfs_empty_lib_is_repaired "1" "$(ROOT=$R3; ensure_rootfs >/dev/null 2>&1; echo $?)"
unset -f curl

echo "launcher tests: $PASS passed, $FAIL failed"
[ "$FAIL" -eq 0 ]
