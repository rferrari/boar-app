#!/bin/bash
# Heavy job (inside the lock, ~6 min): TalkBack linear reading order in a chat with 2 questions + 2 answers (Boar, SEND-MOTION v2 P2,
# inverted FlatList). The AOSP AVD has no TalkBack (no GMS by design), so the evidence is the accessibility node tree
# (uiautomator dump): TalkBack's linear navigation walks those nodes in tree order. Pass: going down the tree, the older
# question comes before the newer one, and within each screen the tree order runs top -> bottom (y ascending).
# TALKBACK_APK=<apk pulled from a real phone> also installs/enables TalkBack and logs a best-effort swipe-right walk (logcat).
# usage: [FLOOR_GB=12] run-a11y-order.sh <apk> <sha>     Output: ~/boar/android/e2e-out/a11y-<sha>-<ts>/
set -uo pipefail
APK=${1:?apk}; SHA=${2:?sha}; SDK=$HOME/Library/Android/sdk; A=$SDK/platform-tools/adb; P=team.sopa.aoair.offline
M=$HOME/boar/shared-models; D=$HOME/boar/android/e2e-data; F=$HOME/boar/android/e2e/flows-piston
OUT=$HOME/boar/android/e2e-out/a11y-$SHA-$(date +%Y%m%d-%H%M%S); mkdir -p "$OUT/maestro"; FLOOR=${FLOOR_GB:-12}
export JAVA_HOME=$(/usr/libexec/java_home -v 17) ANDROID_HOME=$SDK ANDROID_SDK_ROOT=$SDK
free() { df -g / | tail -1 | awk '{print $4}'; }
log() { echo "[a11y] $(date +%T) $*" | tee -a "$OUT/a11y.log"; }
stop_emu() { $A emu kill >/dev/null 2>&1; sleep 5; pkill -x qemu-system-aarch64 2>/dev/null; sleep 2; pkill -9 -x qemu-system-aarch64 2>/dev/null; rm -f $HOME/.android/avd/boar_api35.avd/userdata-qemu.img* $HOME/.android/avd/boar_api35.avd/cache.img*; echo "[a11y] $(date +%T) emulator off, AVD data removed"; }
trap stop_emu EXIT
t0=$(date +%s); log "start $SHA free $(free) GB floor $FLOOR"
( while sleep 10; do f=$(free); [ "$f" -lt "$FLOOR" ] && { echo "[a11y] ABORT: free $f GB < $FLOOR"; stop_emu; kill $$; break; }; done ) & WD=$!
nohup $SDK/emulator/emulator -avd boar_api35 -no-window -no-audio -no-boot-anim -no-snapshot -wipe-data -gpu ${EMU_GPU:-host} > "$OUT/emulator.log" 2>&1 &
t=0; until [ "$($A shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; do sleep 5; t=$((t+5)); [ $t -gt 400 ] && { log "no boot"; exit 2; }; done
log "booted in $(( $(date +%s)-t0 ))s"; sleep 30
$A shell settings put system font_scale 1.0; $A shell cmd uimode night yes >/dev/null
for ime in $($A shell pm list packages | tr -d '\r' | sed -n 's/^package://p' | grep -i -E 'inputmethod|latin'); do $A shell pm grant "$ime" android.permission.READ_CONTACTS 2>/dev/null; done
$A install "$APK" | tail -1; $A shell cmd connectivity airplane-mode enable
$A push "$M/bge-small-en-v1.5-q8_0.gguf" "$M/Qwen2.5-1.5B-Instruct-Q4_K_M.gguf" "$D/corpus-standard.json" "$D/corpus-full.json" /sdcard/Download/ | tail -1
for f in bge-small-en-v1.5-q8_0.gguf Qwen2.5-1.5B-Instruct-Q4_K_M.gguf corpus-standard.json corpus-full.json; do $A shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d "file:///sdcard/Download/$f" >/dev/null; done
echo "apk $(basename "$APK") $(shasum -a 256 "$APK" | cut -c1-64)" | tee "$OUT/device.txt"
QA="What causes the monsoon?"; QB="What happened today in history?"
EN=(-e APP_ID=$P -e L=en -e T_START="Get started" -e T_ESSENTIAL="Essential" -e T_CONTINUE="Continue" -e T_PICK="Choose files" -e T_DONE="Everything runs offline from now on."
  -e T_OPEN="Open BOAR" -e T_ASK="Ask something" -e T_MENU="Open menu" -e T_NEWCHAT="New chat" -e T_SEND="Send" -e T_STOP="Stop answer"
  -e Q_A="$QA" -e Q_B="$QB" -e Q_A_RE="What causes the monsoon.*")
mf() { local ts=$(date +%s); mkdir -p "$OUT/maestro/$1"; (cd "$OUT/maestro/$1" && $HOME/.maestro/bin/maestro test "$F/$2" "${EN[@]}" --test-output-dir "$OUT/maestro/$1" > "$OUT/maestro/$1/maestro.out" 2>&1); local rc=$?
  log "$1 ($2) rc=$rc $(( $(date +%s)-ts ))s"; [ $rc -ne 0 ] && grep -E "FAILED|not found|Assertion" "$OUT/maestro/$1/maestro.out" | tail -3 | tee -a "$OUT/a11y.log"; return $rc; }
# the app must be in front before every dump (a stray Back once left the launcher and the shade in the dumps)
front() { $A shell dumpsys activity activities | grep -m1 -E 'ResumedActivity' | grep -q "$P"; }
ime_down() { $A shell dumpsys input_method | grep -q 'mInputShown=true' && $A shell input keyevent 4; sleep 1; }  # Back only while the keyboard is up
dump() { front || { log "INVALID: $P not in front before dump $1"; $A exec-out screencap -p > "$OUT/$1-notfront.png"; exit 5; }
  $A shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; $A exec-out cat /sdcard/ui.xml > "$OUT/$1.xml"; $A exec-out screencap -p > "$OUT/$1.png"; }
mf setup prints/setup-to-chat.yaml || exit 3
mf two-questions a11y/two-questions.yaml || exit 4
ime_down; dump 0-bottom
mf to-top a11y/to-top.yaml
dump 1-top
# walk down one screen at a time, as TalkBack's linear read scrolls forward
for i in 2 3 4 5 6; do $A shell input swipe 540 1700 540 900 600; sleep 1.5; dump $i-down; done
# order report: nodes with text/content-desc in tree order, y centre, and where each question sits
python3 - "$OUT" "$QA" "$QB" <<'PY' | tee "$OUT/order.txt" | tee -a "$OUT/a11y.log"
import glob, os, re, sys, xml.etree.ElementTree as ET
out, qa, qb = sys.argv[1:4]
seq = []
for f in sorted(glob.glob(os.path.join(out, "*.xml"))):
    try: root = ET.parse(f).getroot()
    except ET.ParseError: print(f"{os.path.basename(f)}: unreadable"); continue
    nodes = []
    for n in root.iter("node"):
        lab = (n.get("content-desc") or n.get("text") or "").strip()
        if not lab: continue
        y1, y2 = map(int, re.findall(r"\d+", n.get("bounds"))[1::2])
        nodes.append(((y1 + y2) // 2, lab))
    qs = [(i, y, "A(older)" if qa in lab else "B(newer)") for i, (y, lab) in enumerate(nodes) if qa in lab or qb in lab]
    inv = sum(1 for a, b in zip(nodes, nodes[1:]) if b[0] < a[0] - 40)
    print(f"\n== {os.path.basename(f)}: {len(nodes)} labelled nodes, tree-order steps that go UP the screen: {inv}")
    for i, y, q in qs: print(f"   question {q} at tree index {i}, y={y}")
    for i, (y, lab) in enumerate(nodes): print(f"   {i:3d} y={y:5d} {lab[:90]!r}")
    seq += [q for _, _, q in qs]
if not seq: print("\nINVALID: no question node in any dump (app not in front, or the question label differs from the text)")
print("\nquestion order as met in tree order, dump by dump (0-bottom first, then top -> down):", " ".join(seq))
PY
if [ -n "${TALKBACK_APK:-}" ]; then
  $A install "$TALKBACK_APK" | tail -1; TB=com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService
  mf to-top-tb a11y/to-top.yaml
  $A shell settings put secure enabled_accessibility_services $TB; $A shell settings put secure accessibility_enabled 1; sleep 5; $A logcat -c
  for i in $(seq 1 14); do $A shell input swipe 200 1200 900 1200 120; sleep 2; done   # swipe right = next item (best effort: injected gestures)
  $A logcat -d -v time | grep -i -E 'talkback|accessibility' > "$OUT/talkback-logcat.txt"; log "talkback logcat lines: $(wc -l < "$OUT/talkback-logcat.txt")"
  $A shell settings put secure enabled_accessibility_services '""'; $A shell settings put secure accessibility_enabled 0
fi
kill $WD 2>/dev/null; log "total $(( $(date +%s)-t0 ))s; free $(free) GB; out $OUT"
