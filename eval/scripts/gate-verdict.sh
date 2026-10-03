#!/usr/bin/env bash
# Every check after a gate run, in one step: fixed regression cases, s32 (Jev judge + no-regression check), control
# comparison, uncited-answer net, CIT-1 citations and context, citation audit, PT vs EN. Prints one line per criterion
# and exits 0 = PASS, 1 = a blocking criterion fails. The accepted PQ case (1.5B names ECC in 1 of 5 seeds,
# Boar 2026-09-27) is reported, not blocking, while the control has it too.
# Usage (from the eval worktree root): bash eval/scripts/gate-verdict.sh <control-label> <candidate-label>
set -uo pipefail
C="$1"; K="$2"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
JUDGES=jev bash "$ROOT/eval/scripts/gate-s32-judge.sh" "$K" >/dev/null 2>&1 || echo "s32 judge: failed"
cd "$ROOT/eval"
FAIL=0
line() { printf '%-28s %s\n' "$1" "$2"; }

node scripts/regress.mjs --name "gate-$K" --runs "results/gates/$K/runs" >/dev/null 2>&1
BLOCK=$(grep "| \*\*FAIL\*\*" "reports/regression-gate-$K.md" | grep -v "crypto-named-001" | wc -l | tr -d ' ')
PQ=$(grep "| \*\*FAIL\*\*" "reports/regression-gate-$K.md" | grep -c "crypto-named-001")
PQC=$(grep "| \*\*FAIL\*\*" "reports/regression-gate-$C.md" 2>/dev/null | grep -c "crypto-named-001")
[ "$BLOCK" -gt 0 ] && FAIL=1
[ "$PQ" -gt "$PQC" ] && FAIL=1
line "fixed cases" "$([ "$BLOCK" = 0 ] && echo PASS || echo "FAIL ($BLOCK)") · PQ failures $PQ (control $PQC)$([ "$PQ" -gt "$PQC" ] && echo ' FAIL: more than the control')"

S32=$(node scripts/s32-gate-check.mjs "$C" "$K" --judges jev 2>/dev/null | grep '^TL;DR' | sed -E 's/TL;DR: //; s/\. (4B:|Regenerate).*//'); rc=$?
echo "$S32" | grep -q FAIL && FAIL=1; echo "$S32" | grep -q INCOMPLETE && FAIL=1
line "s32 (Jev)" "$S32"

node scripts/gate-compare.mjs "$C" "$K" --no-model safety-006,safety-007 >/dev/null 2>&1
line "compare vs control" "reports/gate-compare-$C-vs-$K.md"
line "uncited net (s32)" "$(node scripts/preface-regression.mjs "$C" "$K" 2>/dev/null | grep '^TL;DR' | sed -E 's/TL;DR: //; s/\. (4B:|Regenerate).*//')"
node scripts/cit1-report.mjs "$K" >/dev/null 2>&1; line "CIT-1" "reports/cit1-$K.md"
node scripts/citation-audit.mjs --name "$K" --runs "results/gates/$K/runs" >/dev/null 2>&1; line "citation audit" "reports/citations-$K.md"
PT=$(node scripts/pt-gap-check.mjs "$C" "$K" 2>/dev/null | grep '^TL;DR' | sed -E 's/TL;DR: //; s/ Jev, 4B.*//'); [ "${PIPESTATUS[0]}" = 0 ] || true
echo "$PT" | grep -q '\*\*FAIL' && FAIL=1
line "PT vs EN" "$PT"
DEN=$(node scripts/proposal-denial-check.mjs "$K" 2>/dev/null | grep '^TL;DR' | sed -E 's/TL;DR: //; s/ Regenerate.*//')
echo "$DEN" | grep -q '\*\*FAIL' && FAIL=1
line "EIP/ERC/BIP denial" "$DEN"
SCR=$(node scripts/screen-check.mjs "$K" 2>/dev/null | grep '^TL;DR' | sed -E 's/TL;DR: //; s/ Regenerate.*//')
echo "$SCR" | grep -q '\*\*FAIL' && FAIL=1
line "chat screen [n] (CIT-2)" "$SCR"
# SNIPPET_TOPIC=report: the off-topic passage case is reported without blocking (its first candidate, 3ccf7c0).
SNT=$(node scripts/snippet-topic-check.mjs "$K" 2>/dev/null | grep '^TL;DR' | sed -E 's/TL;DR: //; s/ Regenerate.*//')
[ "${SNIPPET_TOPIC:-block}" = block ] && echo "$SNT" | grep -q '\*\*FAIL' && FAIL=1
line "instant passage on topic" "$SNT$([ "${SNIPPET_TOPIC:-block}" = block ] || echo ' (reported, not blocking this run)')"
# CIT-2 fixed case (Prism): the candidate's own citation post-processing on the raw "… Zone. [1]" texts.
SHA=$(node -e 'console.log(JSON.parse(require("fs").readFileSync(process.argv[1],"utf8")).sha)' "results/gates/$K/meta.json" 2>/dev/null)
TREE=$(node -e 'console.log(JSON.parse(require("fs").readFileSync(process.argv[1],"utf8")).tree)' "results/gates/$K/meta.json" 2>/dev/null)
if [ -n "$SHA" ] && T=$(mktemp -d) && git -C "$TREE" archive "$SHA" src | tar -x -C "$T"; then
  CIT2=$(npx tsx scripts/cit2-fixed.mts "$T" 2>/dev/null | tail -1)
  # PT answers written in English (Tusk), by the candidate's own passageLanguage; reported.
  PTL=$(npx tsx scripts/pt-language.mts "$T" "$C" "$K" 2>/dev/null | grep '^TL;DR' | sed -E 's/TL;DR: //; s/ Blocks when.*//')
  rm -rf "$T"
  echo "$CIT2" | grep -q "CIT-2 PASS" || FAIL=1
  line "CIT-2 fixed case" "${CIT2:-not run}"
else line "CIT-2 fixed case" "not run (no tree/sha in meta.json)"; FAIL=1; fi
PTC=$(node scripts/pt-compact-check.mjs "$C" "$K" 2>/dev/null | grep '^TL;DR' | sed -E 's/TL;DR: //; s/ Jev, 1.5B.*//')
echo "$PTC" | grep -q '\*\*FAIL' && FAIL=1
line "1.5B PT confident errors" "$PTC"
HS=$(node scripts/health-scan.mjs "$C" "$K" 2>/dev/null | grep '^TL;DR' | sed -E 's/TL;DR: //; s/ Regenerate.*//')
echo "$HS" | grep -q '\*\*FAIL' && FAIL=1
line "health scan (all answers)" "$HS"
echo "$PTL" | grep -q '\*\*FAIL' && FAIL=1
[ -z "$PTL" ] && FAIL=1
line "PT answers in English" "${PTL:-not run (blocking)}"
DUP=$(node scripts/dup-disclaimer.mjs "$C" "$K" 2>/dev/null | grep '^TL;DR' | sed -E 's/TL;DR: //; s/ Regenerate.*//')
# Blocking since Boar 2026-09-27: candidate <= control.
echo "$DUP" | grep -q '\*\*FAIL' && FAIL=1
[ -z "$DUP" ] && FAIL=1
line "duplicate disclaimer" "${DUP:-not run (blocking)}"
FW=$(node scripts/foreign-word-check.mjs "$K" 2>/dev/null | grep '^TL;DR' | sed -E 's/TL;DR: //; s/ Regenerate.*//')
line "trv-007 foreign word" "${FW:-not run}"
HR=$(node scripts/health-route.mjs "$C" "$K" 2>/dev/null | grep '^TL;DR' | sed -E 's/TL;DR: //; s/ Regenerate.*//')
line "health routing changes" "${HR:-not run} (reported; see reports/health-route-$C-vs-$K.md)"
AL=$(node scripts/allergy-translation-check.mjs "$K" 2>/dev/null | grep '^TL;DR' | sed -E 's/TL;DR: //; s/ Regenerate.*//')
echo "$AL" | grep -q '\*\*PASS' || FAIL=1
line "lng-009 allergy in French" "${AL:-not run}"
line "EIP PT (cited page)" "$(node scripts/eip-pt-check.mjs "$C" "$K" 2>/dev/null | grep '^TL;DR' | sed -E 's/TL;DR: //; s/ \(EIP-4844.*//') (target, reported)"
line "VERDICT" "$([ $FAIL = 0 ] && echo PASS || echo FAIL)"
exit $FAIL
