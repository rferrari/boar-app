#!/usr/bin/env bash
# Syncs what the desktop runner needs to the Mac mini (models run there, not on the main Mac).
# Usage: bash eval/scripts/sync-mini.sh [host]   (default host: r4toMacMini)
set -euo pipefail
HOST="${1:-r4toMacMini}"
DEST="boar/eval-runner"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
ssh "$HOST" "mkdir -p ~/$DEST/assets/corpus"
rsync -az --delete "$ROOT/src/" "$HOST:$DEST/src/"
rsync -az "$ROOT/assets/corpus/corpus.json" "$HOST:$DEST/assets/corpus/corpus.json"
rsync -az --delete --exclude node_modules --exclude .cache --exclude results "$ROOT/eval/" "$HOST:$DEST/eval/"
ssh "$HOST" "bash -lc 'cd ~/$DEST/eval && npm install --no-audit --no-fund --silent && npm ls node-llama-cpp tsx --depth=0'"
