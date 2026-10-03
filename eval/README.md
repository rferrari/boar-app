# BOAR eval: on-device answers vs frontier + web search

TL;DR: how good BOAR's offline answers are relative to a frontier model with web search, and how many seconds they take. Everything runs locally or on the user's Claude Code subscription. **No paid LLM API and no API key.**

```bash
# from the repo root
npm --prefix eval ci                     # node-llama-cpp + tsx only
npm --prefix eval run report             # rebuild eval/reports/ from results (no model calls)
npm --prefix eval test                   # unit tests for the judge/stats code
```

Full pipeline (milestones only; iterate on the `s32` subset):

```bash
bash eval/scripts/sync-mini.sh && bash eval/scripts/run-mini.sh    # BOAR answers on the Mac mini (sha256-checked GGUFs)
node eval/scripts/gen-references.mjs                               # reference answers, once per dataset version
node eval/scripts/judge.mjs --system qwen2.5-1.5b-instruct-q4km__bundled --subset s32   # or --subset all
node eval/scripts/report.mjs --subset s32
```

## Results

`results/` and `reports/` are not in the repo (about 150 MB, 3,090 files). The v1 set is a release asset
on the fork, next to the commit that produced it:

```bash
# from the repo root: fills eval/results/ and eval/reports/
gh release download eval-frontier-full -R r4topunk/boar-app -p boar-eval-results-v1.tar.zst
zstd -dc boar-eval-results-v1.tar.zst | tar -x --strip-components=1
```

## Layout

| Path | What |
|---|---|
| `dataset/questions.v1.jsonl` | 96 questions, 8 categories × 12, license and source per item (see `dataset/README.md`) |
| `dataset/subset.s32.v1.json` | Stratified iteration subset (4 per category, seed 2026) |
| `dataset/questions.suggestions-v1.jsonl` | Candidate empty-state chat suggestions (not part of the test set) |
| `prompts/reference.v1.md` | Fixed, versioned prompt for reference answers |
| `runner/desktop.ts` | Replays the app pipeline with node-llama-cpp (same classifier, retrieval, fusion, prompt, personality) |
| `references/v1/claude-code__opus.jsonl` | Reference answers + sources + search queries + duration + model/CLI version |
| `results/runs/<dataset>/<model>__<corpus>.jsonl` | BOAR answers, `EvalResultRow` shape (same as the device harness) |
| `results/judgments/…`, `results/calibration/…` | Judge verdicts (both orders) and hand labels |
| `results/spend.jsonl` | Consumption per call (list-price equivalent, not a charge) |
| `reports/` | Generated report + quality × latency chart |

## How it works

- **Reference**: `claude -p --model opus` with only `WebSearch`/`WebFetch`, isolated (temp cwd, no settings sources, no MCP, no session persistence, env stripped of API keys), sequential with a 5 s gap, resumable, stops on a rate limit (the subscription is shared).
- **Judge**: `claude -p` with no tools and a JSON schema; blind (links, citations, source lists, bold stripped); every pair judged in both A/B orders, first order from a recorded seed; disagreeing orders count as a tie. Rubric: correctness, completeness, usefulness (1–5).
- **Metrics**: win/tie/loss, win score (wins + ½ ties), quality ratio (BOAR rubric mean / reference rubric mean), bootstrap 95% CIs, position consistency, median/p90 latency, TTFT, tok/s.
- **Calibration**: `scripts/calibration.mjs sheet` writes a blind sheet; hand labels are applied with `apply` before the judge is read; the report shows agreement and Cohen's kappa.

## Known limitations

- Judge and reference are both Claude. Self-preference would favor the reference.
- Desktop latency (Apple M4, Metal) is a proxy; phone numbers come from `scripts/eval-device.mjs`.
- The runner does not index the 5 hand-written app topic docs; sampling is llama.cpp defaults (top_k 40, top_p 0.95, min_p 0.05), assumed to match llama.rn.
- `expectedKbHit` is 0 with the bundled corpus by design: the dataset avoids the bundled articles. It becomes meaningful with the full knowledge pack.
- Retrieval recall against the full Wikipedia pack lives in `eval/retrieval/` on `feat/knowledge` (it reads `dataset/questions.v1.jsonl`; filter out items without gold). The 1.55 GB sample pack holds ~1/15 of Wikipedia, so recall on this dataset needs the full pack.
- `results/_quarantine/` (gitignored) holds earlier gateway-generated references that are not used.
