#!/bin/bash
# Heavy job (inside the lock, ~5 min, most of it the setup import): per-build answer smoke, NO video (r4to 28/09).
# EN: the model answer must end with body ([1]), SOURCES, Helpful/Copy answer and Go deeper. PT (same data, language
# switched in Settings): the quick passage + either the decline note and "Arriscar resposta" or a model answer.
# Maestro asserts use visible bounds; a render bug can keep a node with bounds but no pixels (f7e8eb2e), so each answer
# screen also gets a pixel check: dark pixels in the chat area (y 300-2000) >= 99% = FAIL (bug 99%, good 96-97%).
# usage: [FLOOR_GB=12] [SMOKE_PT=0] run-smoke.sh <apk> <sha>   rc=0 PASS, else FAIL. Output: ~/boar/android/e2e-out/smoke-<sha>-<ts>/
set -uo pipefail
APK=${1:?apk}; SHA=${2:?sha}; SDK=$HOME/Library/Android/sdk; A=$SDK/platform-tools/adb; P=team.sopa.aoair.offline
M=$HOME/boar/shared-models; D=$HOME/boar/android/e2e-data; F=$HOME/boar/android/e2e/flows-piston
OUT=$HOME/boar/android/e2e-out/smoke-$SHA-$(date +%Y%m%d-%H%M%S); mkdir -p "$OUT/maestro"; FLOOR=${FLOOR_GB:-12}
export JAVA_HOME=$(/usr/libexec/java_home -v 17) ANDROID_HOME=$SDK ANDROID_SDK_ROOT=$SDK
free() { df -g / | tail -1 | awk '{print $4}'; }
log() { echo "[smoke] $(date +%T) $*" | tee -a "$OUT/smoke.log"; }
stop_emu() { $A emu kill >/dev/null 2>&1; sleep 5; pkill -x qemu-system-aarch64 2>/dev/null; sleep 2; pkill -9 -x qemu-system-aarch64 2>/dev/null; rm -f $HOME/.android/avd/boar_api35.avd/userdata-qemu.img* $HOME/.android/avd/boar_api35.avd/cache.img*; echo "[smoke] $(date +%T) emulator off, AVD data removed"; }
trap stop_emu EXIT
t0=$(date +%s); log "start $SHA free $(free) GB floor $FLOOR"
( while sleep 10; do f=$(free); [ "$f" -lt "$FLOOR" ] && { echo "[smoke] ABORT: free $f GB < $FLOOR"; stop_emu; kill $$; break; }; done ) & WD=$!
nohup $SDK/emulator/emulator -avd boar_api35 -no-window -no-audio -no-boot-anim -no-snapshot -wipe-data -gpu ${EMU_GPU:-host} > "$OUT/emulator.log" 2>&1 &
t=0; until [ "$($A shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; do sleep 5; t=$((t+5)); [ $t -gt 400 ] && { log "no boot"; exit 2; }; done
log "booted in $(( $(date +%s)-t0 ))s"; sleep 20
$A shell settings put system font_scale 1.0; $A shell cmd uimode night yes >/dev/null
for ime in $($A shell pm list packages | tr -d '\r' | sed -n 's/^package://p' | grep -i -E 'inputmethod|latin'); do $A shell pm grant "$ime" android.permission.READ_CONTACTS 2>/dev/null; done
$A install "$APK" | tail -1; $A shell cmd connectivity airplane-mode enable
$A push "$M/bge-small-en-v1.5-q8_0.gguf" "$M/Qwen2.5-1.5B-Instruct-Q4_K_M.gguf" "$D/corpus-standard.json" "$D/corpus-full.json" /sdcard/Download/ | tail -1
for f in bge-small-en-v1.5-q8_0.gguf Qwen2.5-1.5B-Instruct-Q4_K_M.gguf corpus-standard.json corpus-full.json; do $A shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d "file:///sdcard/Download/$f" >/dev/null; done
echo "apk $(basename "$APK") $(shasum -a 256 "$APK" | cut -c1-64)" | tee "$OUT/device.txt"
EN=(-e T_START="Get started" -e T_ESSENTIAL="Essential" -e T_CONTINUE="Continue" -e T_PICK="Choose files" -e T_DONE="Everything runs offline from now on."
  -e T_OPEN="Open BOAR" -e T_ASK="Ask something" -e T_MENU="Open menu" -e T_NEWCHAT="New chat" -e T_SEND="Send")
PT=(-e T_ASK="Pergunte algo" -e T_MENU="Abrir menu" -e T_NEWCHAT="Nova conversa" -e T_SEND="Enviar")
FAIL=0; RES=()
mf() { # <label> <flow> <en|pt>
  local -a E; [ "$3" = pt ] && E=("${PT[@]}") || E=("${EN[@]}"); local ts=$(date +%s); mkdir -p "$OUT/maestro/$1"
  (cd "$OUT/maestro/$1" && $HOME/.maestro/bin/maestro test "$F/$2" -e APP_ID=$P -e L=$3 "${E[@]}" --test-output-dir "$OUT/maestro/$1" > "$OUT/maestro/$1/maestro.out" 2>&1); local rc=$?
  log "$1 ($2) rc=$rc $(( $(date +%s)-ts ))s"; [ $rc -ne 0 ] && grep -E "FAILED|not found|Assertion" "$OUT/maestro/$1/maestro.out" | tail -3 | tee -a "$OUT/smoke.log"; return $rc; }
shot() { $A exec-out screencap -p > "$OUT/$1.png"; $A shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; $A exec-out cat /sdcard/ui.xml > "$OUT/$1.xml"; }
pixels() { # $1 = screenshot stem; FAIL when the chat area is (almost) all dark
  command -v ffmpeg >/dev/null || { log "pixels $1: no ffmpeg on this host, skipped"; return 0; }
  local pb; pb=$(ffmpeg -v info -i "$OUT/$1.png" -vf "crop=1080:1700:0:300,format=gray,blackframe=amount=0:threshold=110" -f null - 2>&1 | grep -o 'pblack:[0-9]*' | head -1 | cut -d: -f2)
  log "pixels $1: dark ${pb:-?}% (FAIL >= 99)"; [ -n "$pb" ] && [ "$pb" -ge 99 ] && return 1; return 0; }
check() { # <label> <flow> <lang>
  mf "$1" "$2" "$3"; local rc=$?; shot "$1"; pixels "$1" || rc=9
  [ $rc -eq 0 ] && RES+=("$1 PASS") || { RES+=("$1 FAIL (rc=$rc)"); FAIL=1; }; }
mf setup prints/setup-to-chat.yaml en || { log "setup failed: smoke not run"; exit 3; }
check answer-en smoke/answer-en.yaml en
if [ "${SMOKE_PT:-1}" = 1 ]; then mf to-pt smoke/to-pt.yaml en && check answer-pt smoke/answer-pt.yaml pt || { RES+=("answer-pt FAIL (language switch)"); FAIL=1; }; fi
kill $WD 2>/dev/null
log "RESULT $([ $FAIL = 0 ] && echo PASS || echo FAIL): ${RES[*]}; total $(( $(date +%s)-t0 ))s; free $(free) GB; out $OUT"
exit $FAIL
