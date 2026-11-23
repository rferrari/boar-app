#!/usr/bin/env bash
# Runs a bench preset on the one USB phone and prints its scorecard (t9 0521 item 6; adam, Nov 14: "we should have a
# light benchmark and a long benchmark").
#
#   scripts/bench-preset.sh fast-plugins
#   scripts/bench-preset.sh medium-research --package team.sopa.aoair.labs --models qwen3-4b --out eval-results
#
# A preset is a level (fast, medium, complete), then a topic: fast, fast-plugins, complete-research. The level alone
# runs the fixed 17. Topics: the files in android-native/app/src/main/assets/boar-data/topics.
#
# What it does, in order: checks one device is connected; keeps the screen on (stay_on_while_plugged_in, put back to
# what it was when the script ends, however it ends); closes an open BenchActivity with Back (a second "am start" is
# delivered to the open one and ignored); starts BenchActivity with the request; follows the status the app copies to
# Android/data/<package>/files/eval-out after every answer; pulls the run's rows, meta and scorecard; prints the scorecard.
#
# Before running: the phone isn't downloading a model, and its owner isn't using it (AGENTS.md "Benchmarking models").
set -euo pipefail

usage() { sed -n '2,9p' "$0" | sed 's/^# \{0,1\}//'; exit "${1:-0}"; }

PRESET=""
PKG="team.sopa.aoair"
MODELS=""
OUT="eval-results"
WAIT_MIN=10
while [ $# -gt 0 ]; do
  case "$1" in
    --package) PKG="$2"; shift 2 ;;
    --models) MODELS="$2"; shift 2 ;;
    --out) OUT="$2"; shift 2 ;;
    --wait) WAIT_MIN="$2"; shift 2 ;;
    -h|--help) usage 0 ;;
    -*) echo "unknown option $1" >&2; usage 1 ;;
    *) PRESET="$1"; shift ;;
  esac
done
[ -n "$PRESET" ] || usage 1
case "$PRESET" in fast|fast-*|medium|medium-*|complete|complete-*) ;; *) echo "preset must start with fast, medium or complete: $PRESET" >&2; exit 1 ;; esac

devices=$(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')
if [ "$(printf '%s\n' "$devices" | grep -c .)" != "1" ]; then
  echo "need exactly one device in state \"device\":" >&2; adb devices >&2; exit 1
fi

REMOTE="/sdcard/Android/data/$PKG/files/eval-out"
REQ_ID=$(printf 'preset-%s-%s' "$PRESET" "$(date +%s)" | tr 'A-Z' 'a-z' | sed 's/[_+]/-/g' | tr -cd 'a-z0-9-' | cut -c1-64)

# The stay-awake setting, put back on exit.
STAY_BEFORE=$(adb shell settings get global stay_on_while_plugged_in 2>/dev/null | tr -d '\r' || true)
WAKE_LOOP=0
restore() {
  if [ -n "${STAY_BEFORE:-}" ] && [ "$STAY_BEFORE" != "null" ]; then
    adb shell settings put global stay_on_while_plugged_in "$STAY_BEFORE" >/dev/null 2>&1 || true
    echo "stay_on_while_plugged_in back to $STAY_BEFORE"
  fi
}
trap restore EXIT
if ! adb shell settings put global stay_on_while_plugged_in 7 >/dev/null 2>&1; then
  echo "the phone won't take stay_on_while_plugged_in: waking it every 30 s instead"
  WAKE_LOOP=1
fi
adb shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true

# A BenchActivity already open ignores a new request: Back closes it first.
if adb shell dumpsys activity activities 2>/dev/null | grep -E "mResumedActivity|topResumedActivity|ResumedActivity" | grep -q "BenchActivity"; then
  echo "BenchActivity is open: pressing Back to close it"
  adb shell input keyevent KEYCODE_BACK >/dev/null
  sleep 2
fi

models_json=""
if [ -n "$MODELS" ]; then
  models_json=$(printf '%s' "$MODELS" | awk -F, '{ for (i = 1; i <= NF; i++) printf "%s\"%s\"", (i > 1 ? "," : ""), $i }')
  models_json=",\"models\":[$models_json]"
fi
# The style is never left to the app's default (t9 1040): a preset's level names its passes, so say them.
case "${PRESET%%-*}" in
  fast|medium) echo "styles: succinct (level ${PRESET%%-*})" ;;
  complete) echo "styles: succinct and detailed, plus Deep Research for the research topic (level complete)" ;;
  *) echo "unknown level in preset \"$PRESET\": fast, medium or complete" >&2; exit 2 ;;
esac
REQUEST="{\"requestId\":\"$REQ_ID\",\"preset\":\"$PRESET\"$models_json}"
echo "request: $REQUEST"
REQ64=$(printf '%s' "$REQUEST" | base64 -w0)
adb shell am start -n "$PKG/team.sopa.boar.bench.BenchActivity" --es run eval --es request64 "$REQ64" --ei waitmin "$WAIT_MIN" >/dev/null

status_field() { # status_field <json> <field>
  node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>{try{const v=JSON.parse(s)[process.argv[1]];process.stdout.write(v==null?"":String(v))}catch{}})' "$2" <<<"$1"
}

STATUS_FILE="$REMOTE/requests/$REQ_ID.status.json"
started=$(date +%s)
last_wake=$started
state=""
while :; do
  sleep 10
  now=$(date +%s)
  if [ "$WAKE_LOOP" = "1" ] && [ $((now - last_wake)) -ge 30 ]; then adb shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true; last_wake=$now; fi
  status=$(adb shell cat "$STATUS_FILE" 2>/dev/null | tr -d '\r' || true)
  if [ -z "$status" ] || ! printf '%s' "$status" | grep -q '"state"'; then
    if [ $((now - started)) -gt $((WAIT_MIN * 60 + 60)) ]; then echo "no status after ${WAIT_MIN} min: the app didn't take the request" >&2; exit 1; fi
    continue
  fi
  state=$(status_field "$status" state)
  printf '\r%s  %s/%s  %s   ' "$state" "$(status_field "$status" completed)" "$(status_field "$status" total)" "$(status_field "$status" current)"
  case "$state" in done|failed) echo; break ;; esac
done

if [ "$state" = "failed" ]; then
  echo "run failed: $(status_field "$status" error)" >&2
  exit 1
fi

RUN_ID=$(status_field "$status" runId)
dest="$OUT/$RUN_ID"
mkdir -p "$dest"
# The app copies everything at the end too; the files are there once the status says done.
for f in "$RUN_ID.jsonl" "$RUN_ID.meta.json" "$RUN_ID.scorecard.txt" "requests/$REQ_ID.status.json"; do
  adb pull "$REMOTE/$f" "$dest/" >/dev/null 2>&1 || echo "could not pull $f"
done
echo "pulled to $dest"
echo
echo "== scorecard: $PRESET ($RUN_ID) =="
if [ -f "$dest/$RUN_ID.scorecard.txt" ]; then cat "$dest/$RUN_ID.scorecard.txt"; else echo "(no scorecard file: an app from before presets?)"; fi
