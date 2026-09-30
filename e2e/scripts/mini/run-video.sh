#!/bin/bash
# Heavy job (inside the lock), polish round 28/09 (POLISH-2026-09-28.md #1): the 8 UI flows as video on the headless AVD.
# Dark theme, EN and PT, font 1.0 on every flow + AX_SCALE on flows 01, 02 and 05. One file per flow/lang/font.
# usage: [FLOWS="02"] [LANGS="en pt"] [RM_LANGS="en"] [AX_SCALE=1.3] [REC=emu|dev] [EMU_GPU=host] [FLOOR_GB=12] run-video.sh <apk> <sha>
#  REC=emu (default): the emulator encodes on the host (`adb emu screenrecord`, webm VP8, fixed --fps 60, guest CPU untouched).
#    A 4 s probe runs right after boot; if it fails or comes out empty, the run falls back to REC=dev.
#  REC=dev: `adb shell screenrecord` in the guest (H.264 mp4, --bit-rate 100 Mbps, variable frame rate up to the display's 60 Hz).
#  EMU_GPU=host (default) renders on the host GPU; if the AVD does not boot with it in 240 s it restarts on swiftshader_indirect.
#  RM_LANGS: languages that also get one flow-02 pass (font 1.0) with reduce motion = Android "Remove animations"
#    (window/transition/animator scales 0; RN's isReduceMotionEnabled reads transition_animation_scale == 0). File suffix _rm.
#  Real fps per video = app frames rendered (dumpsys gfxinfo, reset at record start) / seconds; written to videos.tsv.
# Flows in ~/boar/android/e2e/flows-piston/video. Output: ~/boar/android/e2e-out/video-<sha>-<ts>/<nn>-<flow>_<en|pt>[_ax].<webm|mp4>
set -uo pipefail
APK=${1:?apk}; SHA=${2:?sha}; SDK=$HOME/Library/Android/sdk; A=$SDK/platform-tools/adb; P=team.sopa.aoair.offline
M=$HOME/boar/shared-models; D=$HOME/boar/android/e2e-data; PL=$HOME/boar/shared-data/packs/pinned
F=$HOME/boar/android/e2e/flows-piston; OUT=$HOME/boar/android/e2e-out/video-$SHA-$(date +%Y%m%d-%H%M%S); mkdir -p "$OUT/maestro"
FLOWS=${FLOWS:-"01 02 03 04 05 06 07 08"}; LANGS=${LANGS:-"en pt"}; AX=${AX_SCALE:-1.3}; REC=${REC:-emu}; GPU=${EMU_GPU:-host}; FLOOR=${FLOOR_GB:-12}
export JAVA_HOME=$(/usr/libexec/java_home -v 17) ANDROID_HOME=$SDK ANDROID_SDK_ROOT=$SDK
free() { df -g / | tail -1 | awk '{print $4}'; }
log() { echo "[video] $(date +%T) $*" | tee -a "$OUT/video.log"; }
now() { python3 -c 'import time;print(round(time.time(),2))'; }
stop_emu() { $A emu kill >/dev/null 2>&1; sleep 5; pkill -x qemu-system-aarch64 2>/dev/null; sleep 2; pkill -9 -x qemu-system-aarch64 2>/dev/null; rm -f $HOME/.android/avd/boar_api35.avd/userdata-qemu.img* $HOME/.android/avd/boar_api35.avd/cache.img*; echo "[video] $(date +%T) emulator off, AVD data removed"; }
trap stop_emu EXIT
boot() { # $1 = gpu mode; returns 1 if not booted in $2 s
  nohup $SDK/emulator/emulator -avd boar_api35 -no-window -no-audio -no-boot-anim -no-snapshot -wipe-data -gpu $1 > "$OUT/emulator-$1.log" 2>&1 &
  local t=0; until [ "$($A shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; do sleep 5; t=$((t+5)); [ $t -gt $2 ] && return 1; done
  return 0  # without it the function returns the loop body's last status (1 from the timeout test) even after a boot
}
t0=$(date +%s); log "start $SHA flows='$FLOWS' langs='$LANGS' ax=$AX rec=$REC gpu=$GPU floor $FLOOR GB free $(free) GB"
( while sleep 10; do f=$(free); [ "$f" -lt "$FLOOR" ] && { echo "[video] ABORT: free $f GB < $FLOOR"; stop_emu; kill $$; break; }; done ) & WD=$!
if ! boot $GPU 240; then log "no boot with -gpu $GPU in 240 s: restarting on swiftshader_indirect"; stop_emu; GPU=swiftshader_indirect; boot $GPU 400 || { log "no boot"; exit 2; }; fi
log "booted (-gpu $GPU) in $(( $(date +%s)-t0 ))s"; sleep 30
$A shell settings put system font_scale 1.0; $A shell cmd uimode night yes >/dev/null
# touches visible in the video (marks when Maestro taps); animation scales pinned to 1x
$A shell settings put system show_touches 1; for k in window_animation_scale transition_animation_scale animator_duration_scale; do $A shell settings put global $k 1; done
for ime in $($A shell pm list packages | tr -d '\r' | sed -n 's/^package://p' | grep -i -E 'inputmethod|latin'); do $A shell pm grant "$ime" android.permission.READ_CONTACTS 2>/dev/null; done
$A install "$APK" | tail -1; $A shell cmd connectivity airplane-mode enable
$A push "$M/bge-small-en-v1.5-q8_0.gguf" "$M/Qwen2.5-1.5B-Instruct-Q4_K_M.gguf" "$D/corpus-standard.json" "$D/corpus-full.json" /sdcard/Download/ | tail -1
$A shell mkdir -p /sdcard/Download/places
PF=${PLACES_FILES:-"$PL/41bfdd14*/t-N41E012.sqlite $PL/215ce466*/world-places.sqlite"}; PF=$(echo $PF)
$A push $PF /sdcard/Download/places/ | tail -1
for f in bge-small-en-v1.5-q8_0.gguf Qwen2.5-1.5B-Instruct-Q4_K_M.gguf corpus-standard.json corpus-full.json $(for f in $PF; do echo places/$(basename $f); done); do $A shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d "file:///sdcard/Download/$f" >/dev/null; done

# probe the host-side recorder once; anything odd -> guest screenrecord for the whole run
if [ "$REC" = emu ]; then
  r=$($A emu screenrecord start --fps 60 --bit-rate 25000000 --time-limit 10 "$OUT/probe.webm" 2>&1 | tr -d '\r' | tr '\n' ' '); sleep 4
  $A emu screenrecord stop >/dev/null 2>&1; sleep 3; sz=$(stat -f %z "$OUT/probe.webm" 2>/dev/null || echo 0)
  log "probe emu screenrecord: '$r' size $sz"; [ "$sz" -lt 20000 ] && { REC=dev; log "falling back to REC=dev"; }
fi
{ echo "apk $(basename "$APK") $(shasum -a 256 "$APK" | cut -c1-64)"; echo "avd boar_api35 Android $($A shell getprop ro.build.version.release) API $($A shell getprop ro.build.version.sdk) $($A shell getprop ro.product.cpu.abi) -gpu $GPU"
  $A shell wm size; $A shell wm density; $A shell dumpsys SurfaceFlinger | grep -m1 -E '^GLES:'; $A shell dumpsys display | grep -m1 -o -E 'renderFrameRate [0-9.]+|fps=[0-9.]+'
  grep -E '^hw.ramSize|^hw.cpu.ncore' $HOME/.android/avd/boar_api35.avd/config.ini; echo "host $(sysctl -n machdep.cpu.brand_string) $(sysctl -n hw.ncpu) cpu"
  echo "recorder $REC: $([ $REC = emu ] && echo 'adb emu screenrecord --fps 60 --bit-rate 25000000 (webm VP8, host)' || echo 'adb shell screenrecord --bit-rate 100000000 (mp4 H.264, guest, VFR)')"
  echo "theme dark (cmd uimode night yes), show_touches 1, animation scales 1x, airplane mode on, AX font_scale $AX"; } | tr -d '\r' | tee "$OUT/device.txt"

EN=(T_START="Get started" T_ESSENTIAL="Essential" T_CONTINUE="Continue" T_PICK="Choose files" T_DONE="Everything runs offline from now on." T_OPEN="Open BOAR"
  T_ASK="Ask something" T_MENU="Open menu" T_NEWCHAT="New chat" T_SEND="Send" T_STOP="Stop answer" T_SETTINGS="Settings" T_KNOWLEDGE="Knowledge"
  T_ASSISTANT="Assistant" T_MODELS="Models" T_PERFORMANCE="Performance" T_ASK_MODEL="Answer with AI" T_SOURCES="Sources? \\(?[0-9]" T_SOURCE1="Sources? 1:.*"
  T_SHOW_MORE="Show [0-9]+ more" T_RECEIPT="Performance details:.*" T_IMPORT_PLACES="Import places" T_VERIFIED="Verified:" T_DELETE="Delete" T_CANCEL="Cancel"
  T_APPEARANCE="Appearance" T_LIGHT="Light" T_DARK="Dark" T_SYSTEM="System" T_LANGUAGE="Language" T_OTHER_LANG="Português" T_BACK_LANG="English"
  T_CONV_MONSOON="What causes the monsoon.*" T_CONV_PLACES="best vegan restaurants.*" T_OPEN_SOURCE="Open source 1:.*" T_SOURCE_SHEET="Source 1"
  T_PLACE_ROW=".*, source .+" T_DELETE_CONV="Delete conversation:.*"
  Q_MONSOON="What causes the monsoon?" Q_PLACES="best vegan restaurants in Rome")
PT=(T_START="Começar" T_ESSENTIAL="Essencial" T_CONTINUE="Continuar" T_PICK="Escolher arquivos" T_DONE="Tudo roda offline a partir de agora." T_OPEN="Abrir o BOAR"
  T_ASK="Pergunte algo" T_MENU="Abrir menu" T_NEWCHAT="Nova conversa" T_SEND="Enviar" T_STOP="Parar resposta" T_SETTINGS="Ajustes" T_KNOWLEDGE="Conhecimento"
  T_ASSISTANT="Assistente" T_MODELS="Modelos" T_PERFORMANCE="Desempenho" T_ASK_MODEL="Responder com IA" T_SOURCES="Fontes? \\(?[0-9]" T_SOURCE1="Fontes? 1:.*"
  T_SHOW_MORE="Mostrar mais [0-9]+" T_RECEIPT="Detalhes de desempenho:.*" T_IMPORT_PLACES="Importar lugares" T_VERIFIED="Verificado:" T_DELETE="Apagar" T_CANCEL="Cancelar"
  T_APPEARANCE="Aparência" T_LIGHT="Claro" T_DARK="Escuro" T_SYSTEM="Sistema" T_LANGUAGE="Idioma" T_OTHER_LANG="English" T_BACK_LANG="Português"
  T_CONV_MONSOON="O que causa a monção.*" T_CONV_PLACES="melhores restaurantes veganos.*" T_OPEN_SOURCE="Abrir fonte 1:.*" T_SOURCE_SHEET="Fonte 1"
  T_PLACE_ROW=".*, fonte .+" T_DELETE_CONV="Apagar conversa:.*"
  Q_MONSOON="O que causa a monção?" Q_PLACES="melhores restaurantes veganos em Roma")
# mf <label> <flow> <en|pt> [KEY=VALUE...]: one Maestro run (not recorded unless wrapped by rec)
mf() {
  local label=$1 flow=$2 L=$3; shift 3; local -a S; [ "$L" = pt ] && S=("${PT[@]}") || S=("${EN[@]}")
  local args=(-e APP_ID=$P -e L=$L); for kv in "${S[@]}" "$@"; do args+=(-e "$kv"); done
  local ts=$(date +%s); mkdir -p "$OUT/maestro/$label"
  (cd "$OUT/maestro/$label" && $HOME/.maestro/bin/maestro test "$F/$flow" "${args[@]}" --test-output-dir "$OUT/maestro/$label" > "$OUT/maestro/$label/maestro.out" 2>&1); local rc=$?
  log "$label ($flow $L $*) rc=$rc $(( $(date +%s)-ts ))s"; [ $rc -ne 0 ] && { grep -E "FAILED|not found|Assertion" "$OUT/maestro/$label/maestro.out" | tail -3 | tee -a "$OUT/video.log"; $A exec-out screencap -p > "$OUT/maestro/$label/fail.png"; }
  return $rc
}
TSV=$OUT/videos.tsv; echo -e "file\tlang\tfont\trecorder\tgpu\tseconds\tapp_frames\tapp_fps\tjanky_frames\tjanky_per_s\tjanky_pct\tp90_ms\tflow_rc" > $TSV
RP=; RT=; RF=
rec_start() { # $1 = file stem
  RF=$1; $A shell dumpsys gfxinfo $P reset >/dev/null 2>&1
  if [ $REC = emu ]; then $A emu screenrecord start --fps 60 --bit-rate 25000000 --time-limit 180 "$OUT/$RF.webm" >/dev/null
  else ( $A shell screenrecord --bit-rate 100000000 --time-limit 180 /sdcard/rec.mp4 ) & RP=$!; fi
  RT=$(now); sleep 1
}
rec_stop() { # $1 = flow rc; 1.5 s tail so the last transition settles on video
  sleep 1.5; local s=$(python3 -c "print(round($(now)-$RT,1))") ext
  if [ $REC = emu ]; then $A emu screenrecord stop >/dev/null; ext=webm; sleep 2
  else $A shell pkill -INT screenrecord; wait $RP 2>/dev/null; sleep 1; $A pull /sdcard/rec.mp4 "$OUT/$RF.mp4" >/dev/null; $A shell rm -f /sdcard/rec.mp4; ext=mp4; fi
  local g=$OUT/maestro/$RF.gfxinfo.txt; $A shell dumpsys gfxinfo $P | tr -d '\r' > $g
  local fr=$(grep -m1 'Total frames rendered' $g | awk -F': ' '{print $2}'); fr=${fr:-0}
  local jn=$(grep -m1 '^Janky frames:' $g | sed -E 's/^Janky frames: ([0-9]+).*/\1/'); jn=${jn:-0}
  local jk=$(grep -m1 '^Janky frames:' $g | sed -E 's/.*\(([0-9.]+)%\).*/\1/'); local p90=$(grep -m1 '^90th percentile' $g | sed -E 's/.*: ([0-9]+)ms/\1/')
  local L=$(echo $RF | sed -E 's/.*_(en|pt)(_ax|_rm)?$/\1/'); local fs=1.0; [[ $RF == *_ax ]] && fs=$AX
  echo -e "$RF.$ext\t$L\t$fs\t$REC\t$GPU\t$s\t$fr\t$(python3 -c "print(round($fr/max($s,0.1),1))")\t$jn\t$(python3 -c "print(round($jn/max($s,0.1),1))")\t$jk\t$p90\t$1" | tee -a $TSV
}
# vf <nn-name> <flow> <lang> <suffix> [KEY=VALUE...]: recorded Maestro run
vf() { local stem=$1_$3$4 flow=$2 L=$3; shift 4; rec_start $stem; mf $stem video/$flow $L "$@"; local rc=$?; rec_stop $rc; return $rc; }
fontscale() { $A shell settings put system font_scale "$1"; }
anim() { for k in window_animation_scale transition_animation_scale animator_duration_scale; do $A shell settings put global $k $1; done; log "animation scales $1 (transition_animation_scale=$($A shell settings get global transition_animation_scale | tr -d '\r'))"; }
fresh() { $A shell am force-stop $P; $A shell pm clear $P >/dev/null; }
has() { [[ " $FLOWS " == *" $1 "* ]]; }
ready() { # $1 = lang: fresh app, setup + import not recorded, then in the chat
  fresh; mf setup-$1 prints/setup-to-chat.yaml $1
}

for L in $LANGS; do
  fontscale 1.0
  if has 01; then
    # 01 setup 1 -> 4: a = steps 1-3 + language switch + pack (stops once the import starts), b = import progress -> done -> chat
    fresh; vf 01a-setup 01a-setup.yaml $L ""; vf 01b-setup-import 01b-setup-import.yaml $L ""
  else ready $L || { log "setup $L failed: skipping $L"; continue; }; fi
  # 02: new chat + question pasted (keyboard up) off camera, then Send -> stages -> passage -> stream -> sources -> receipt on camera
  has 02 && { mf 02-prep-$L video/02-prep.yaml $L && vf 02-chat-send 02-chat-send.yaml $L ""; }
  # 03-08 need a sourced conversation (02 leaves one; otherwise ask off camera) and 04/06 a places answer (off camera)
  if ! has 02 && { has 03 || has 04 || has 06 || has 08; }; then mf ask-$L video/ask-offcam.yaml $L; fi
  has 03 && vf 03-drawer 03-drawer.yaml $L ""
  { has 04 || has 06; } && mf places-$L video/places-prep.yaml $L
  has 04 && vf 04-sheets 04-sheets.yaml $L ""
  has 05 && vf 05-settings-nav 05-settings-nav.yaml $L ""
  has 06 && vf 06-expand 06-expand.yaml $L ""
  has 07 && vf 07-theme-lang 07-theme-lang.yaml $L ""
  has 08 && vf 08-keyboard 08-keyboard.yaml $L ""
  # AX pass (flows 02 and 05 on the same data; 01 needs a fresh app, so it goes last)
  if has 02 || has 05; then
    fontscale $AX; $A shell am force-stop $P
    has 02 && { mf 02-prep-$L-ax video/02-prep.yaml $L && vf 02-chat-send 02-chat-send.yaml $L _ax; }
    has 05 && vf 05-settings-nav 05-settings-nav.yaml $L _ax
  fi
  # reduce motion pass (flow 02, font 1.0): app restarted so it reads the setting at launch
  if has 02 && [[ " ${RM_LANGS:-} " == *" $L "* ]]; then
    fontscale 1.0; anim 0; $A shell am force-stop $P
    mf 02-prep-$L-rm video/02-prep.yaml $L && vf 02-chat-send 02-chat-send.yaml $L _rm
    anim 1; $A shell am force-stop $P
  fi
  if has 01; then fontscale $AX; fresh; vf 01a-setup 01a-setup.yaml $L _ax; vf 01b-setup-import 01b-setup-import.yaml $L _ax; fi
  fontscale 1.0
done
# container facts (cheap: header + packet count, no decode) when ffprobe exists here; otherwise done on the MacBook
if command -v ffprobe >/dev/null; then
  for v in "$OUT"/*.webm "$OUT"/*.mp4; do [ -f "$v" ] || continue
    echo -e "$(basename $v)\t$(ffprobe -v error -select_streams v:0 -count_packets -show_entries stream=codec_name,width,height,r_frame_rate,avg_frame_rate,nb_read_packets -show_entries format=duration -of csv=p=0 "$v" | tr '\n' ' ')"; done | tee "$OUT/ffprobe.txt"
fi
kill $WD 2>/dev/null; log "total $(( $(date +%s)-t0 ))s; free $(free) GB; out $OUT"; column -t -s $'\t' $TSV | tee -a "$OUT/video.log"; ls -la "$OUT" | grep -E 'webm|mp4'
