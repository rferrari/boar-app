#!/usr/bin/env bash
# Runs the baseline systems on the Mac mini (sha256-checked) through the mini's heavy-job queue
# (~/boar/bin/heavy: one heavy job at a time, log in ~/boar/heavy.log) and pulls the result rows back.
# Usage: bash eval/scripts/run-mini.sh [host] [models...]   (run sync-mini.sh first)
set -euo pipefail
HOST="${1:-r4toMacMini}"; shift || true
MODELS=("${@:-qwen2.5-1.5b-instruct-q4km qwen3-4b-instruct-2507-q4km lfm2.5-8b-a1b-q4_0}")
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
for m in ${MODELS[@]}; do
  ssh "$HOST" "~/boar/bin/heavy Sextant bash -lc 'cd ~/boar/eval-runner && BOAR_SHARED_MODELS=\$HOME/boar/shared-models npx --prefix eval tsx eval/runner/desktop.ts --model $m --gpu'"
done
mkdir -p "$ROOT/eval/results/runs"
rsync -az "$HOST:boar/eval-runner/eval/results/runs/" "$ROOT/eval/results/runs/"
