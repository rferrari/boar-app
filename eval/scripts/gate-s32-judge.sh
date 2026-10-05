#!/usr/bin/env bash
# Judges the s32 answers a gate produced (GATE_S32=1) against the reference with Jev and Claude (claude -p, one at a
# time, resumable), then writes reports/s32-gate-<labels>.md for the no-regression check.
# Usage (from the eval worktree root): [JUDGES=jev] bash eval/scripts/gate-s32-judge.sh <gate-label> [<gate-label> ...]
set -euo pipefail
cd "$(dirname "$0")/.."
systems=()
for label in "$@"; do
  for f in results/gates/"$label"/s32/*.jsonl; do
    name="$(basename "$f" .jsonl)__$label"
    cp "$f" "results/runs/v1/$name.jsonl"
    systems+=("$name")
  done
done
# JUDGES=jev: Jev only (Boar, 2026-09-27: the Claude judge is kept for the final candidate of the official report).
for s in "${systems[@]}"; do
  node scripts/jev-judge.mjs --subset s32 --system "$s"
  [ "${JUDGES:-both}" = jev ] || node scripts/judge.mjs --subset s32 --system "$s"
done
IFS=,; node scripts/report.mjs --subset s32 --name "s32-gate-$(IFS=-; echo "$*")" --systems "${systems[*]}"
