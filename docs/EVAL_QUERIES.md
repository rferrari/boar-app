# Evaluation

The bounty's bar is answer quality on real research questions, including ones
a 1B dense model typically fails (multi-hop reasoning, synthesis, comparison).
This document defines a fixed evaluation set and a reproducible way to run it
on an Android device against different models and routing configurations, so
their answers and costs can be compared side by side.

There is no automated judge. The harness produces structured results
(JSONL/CSV) that you grade by hand, using the grading notes below.

## The evaluation set (v1)

Source of truth: `android-native/app/src/main/assets/boar-data/eval-set.json` (`evalSet`; extra sets in `extraSets`). Bump the
`setVersion` of the queries whenever one changes; every result row records the
version it was produced with.

"Expected KB article" means the bundled corpus has an article on the topic,
so a correct retrieval should surface it (recorded as `expectedKbHit`). The
`no-kb-content` queries have no relevant article on purpose.

| id | category | query | expected KB article(s) |
|---|---|---|---|
| greeting-1 | greeting | hey, what's up? | — |
| factual-1 | factual | What is the capital of Australia? | — |
| factual-2 | factual | Who proposed the theory of evolution by natural selection? | Evolution |
| explanation-1 | explanation | How do vaccines work? | Vaccine |
| explanation-2 | explanation | Explain photosynthesis in simple terms. | Photosynthesis |
| comparison-1 | comparison | Compare the French Revolution and the Industrial Revolution. | French Revolution, Industrial Revolution |
| comparison-2 | comparison | Contrast how supply and demand explains price changes with how behavioral economics complicates that picture — where does the simple model break down? | Supply and demand, Behavioral economics |
| synthesis-1 | synthesis | What do the Agricultural Revolution and the Industrial Revolution have in common as turning points in human history, and how did they differ in how quickly they changed daily life? | Agricultural revolution, Industrial Revolution |
| synthesis-2 | synthesis | How does the immune system's response to a pathogen relate to how a vaccine works — walk through the mechanism. | Immune system, Vaccine |
| synthesis-3 | synthesis | Why is the Amazon rainforest considered important for global climate, and what does biodiversity loss there actually threaten beyond the obvious loss of species? | Amazon rainforest |
| reasoning-1 | reasoning | A train leaves at 3:40 pm and the trip takes 2 hours and 35 minutes. What time does it arrive? | — |
| reasoning-2 | reasoning | A device has a 12GB RAM budget. The OS and app overhead take 2GB, the embedding model needs 200MB, and the LLM's KV cache needs 1.5GB. How much is left for the LLM's weights, and would a 9GB model fit if loaded fully into RAM? | — |
| reasoning-3 | reasoning | If a Mixture-of-Experts model has 100B total parameters but only activates 8B per token, and each parameter needs 1 byte at Q8, what's the minimum disk footprint, and why doesn't RAM usage scale with the 100B figure? | — |
| grounded-1 | retrieval-grounded | Why did the Western Roman Empire fall? | Fall of the Western Roman Empire |
| grounded-2 | retrieval-grounded | What is a black hole and how does one form? | Black hole |
| no-kb-1 | no-kb-content | Who was Napoleon Bonaparte? | — (the corpus only has "Randy Napoleon", which must not be cited) |
| no-kb-2 | no-kb-content | How do antibiotics work, and why does antibiotic resistance develop? | — |

Each query's `gradingNotes` in `evalSet.ts` says what a good answer contains.

## Configurations

The harness runs the whole set once per selected configuration:

- **One per installed LLM** (`model:<id>`), including models added through the
  Hugging Face search. Every query goes to that model. Retrieval follows the same
  rule as the fixed-model chat path: skipped for greetings, calculation,
  translation and code, otherwise run. Generation goes through the routing
  executor in the model's own instruction format: the chat template embedded
  in its GGUF (`tokenizer.chat_template`, applied by llama.cpp), so Qwen gets
  ChatML, Phi gets `<|system|>…<|end|>`, and any other model gets whatever
  its file defines. Only a file with no embedded template falls back to the
  app's plain `Question:/Answer:` prompt. Every row records which one was used
  (`promptFormat`). Generation settings are otherwise identical in both cases
  (512 tokens, temperature 0.7); a template ends on the model's own
  end-of-turn token, the plain prompt on the app's stop strings. All three
  curated GGUFs (Phi-3.5-mini, Qwen2.5-1.5B, Qwen2.5-7B) ship a template.
- **Adaptive routing** (`adaptive`; the native runner reports it as not supported
  yet, so this describes the 1.x app). Every query goes through `runAdaptiveChat`,
  exactly as in chat with Adaptive Routing on, using the routing preset
  currently stored in settings (default `balanced`; there's no picker in the UI
  yet). This runs whether or not the Adaptive Routing toggle is on.
  `routingPreset` on each row records which preset was used. It keeps
  live-chat prompt formatting, which today means Qwen2.5-1.5B uses its
  template and every other model (Phi included) the plain prompt, so it
  measures the product as users get it; `promptFormat` shows the difference.

Held constant for every run and recorded on every row: the system prompt (the
default `succinct` personality), `maxTokens` = 512, no conversation history
(every query is answered on its own). Sampling temperature is the app's normal
0.7, so answers vary a little between runs; compare several runs before drawing
conclusions from small differences.

Configs run one after another, each over the full set, so each model loads once
per config. The first query of each config therefore carries the model load
cost (`modelResidency: "cold"` or `"switched"`, `modelLoadMs` > 0) and the rest
are `"resident"`. When the run ends, the model that was loaded before it is
reloaded, so chat carries on as before.

## Running it from the computer

Over adb, with no tapping: see [DEVICE_EVALUATION.md](DEVICE_EVALUATION.md). The
results land in `eval-results/`; print a report with
`node scripts/eval-summary.mjs --report <runId>.jsonl`.

## Running it on the device by hand

1. Build and install the app as described in [AGENTS.md](../AGENTS.md), finish
   first-run setup, and download any extra models you want to compare
   (Settings → Models).
2. Keep the phone plugged in with the screen on (for example, Developer options
   → Stay awake). Backgrounding or locking the phone can interrupt a run.
3. Open the drawer → **Execution Telemetry** → **🧪 Evaluate**.
4. Tick the configurations to compare (by default, every installed model plus
   adaptive routing) and tap **▶ Run**. Progress shows the current config and
   query. **■ Stop** ends the run after the current answer and keeps partial
   results.
5. When it finishes, the results are saved on the device as
   `files/eval/<runId>.jsonl`. **⬇ JSONL** and **⬇ CSV** open the share
   sheet to send them to your computer.

Expect roughly 17 answers × (seconds to a minute each) per config, depending
on the model and phone.

### Getting results onto your computer

Any of these works:

- **Share sheet**: the export buttons (file manager, email, Nearby Share, …).
- **adb** (debuggable builds; a debug build's package is `team.sopa.aoair.native`):

  ```bash
  adb exec-out run-as team.sopa.aoair.native ls files/eval
  adb exec-out run-as team.sopa.aoair.native cat files/eval/<runId>.jsonl > <runId>.jsonl
  ```

Every row is also written to the regular execution telemetry, so eval runs show
up in the Execution Telemetry screen and its exports like any other message.

## Comparing results

```bash
node scripts/eval-summary.mjs results/*.jsonl            # one line per run + config
node scripts/eval-summary.mjs --report results/*.jsonl   # detailed per-config report
node scripts/eval-summary.mjs --answers results/*.jsonl  # answers side by side, per query (markdown)
```

The summary lists, per config, successes, failures, how many expected KB
articles were retrieved, median TTFT, median tokens/sec, median total time,
max model load time and peak RSS. `--answers` groups every config's answer
under each query, for grading against the notes above.

### Result fields

Each JSONL row (and CSV line) contains:

- **Run:** `runId`, `evalSetVersion`, `configId`, `configLabel`,
  `routingPreset` (adaptive only), `personalityId`, `maxTokens`, `createdAt`.
- **Query:** `queryId`, `category`, `query`, `expectedKbTitles`.
- **Answer:** `answer`, `promptFormat` (`chat-template`/`plain`), `outcome` (`success`/`failure`/`cancelled`),
  `errorMessage`, `timedOut`.
- **Routing:** `modelId` (the model that generated the answer), `taskType`,
  `adaptiveRoutingUsed`, `reasonCodes`.
- **Retrieval:** `retrievalUsed`, `retrievedTitles`, `expectedKbHit`.
- **Model:** `modelSwitches` (within the plan), `crossMessageModelSwitch`,
  `modelResidency`, `modelLoadMs`.
- **Timing and resources:** `ttftMs` (from the generate call to the first
  token, load excluded), `generationLatencyMs` (first token to finish),
  `totalLatencyMs` (whole query), `tokensGenerated`, `tokPerSec`,
  `peakRssBytes`.

The metric fields mean exactly what they mean in the execution telemetry.

## The Vitalik battery (`vitalik-1`)

A second set of 6 questions in `extraSets` (same file), run only on request: a request with
`"queries":["vitalik"]` (or single ids such as `vitalik-about-x`). Its rows are
recorded as set `vitalik-1`, so they're never compared with the standard set above.

The questions are our own, on topics Vitalik Buterin (whose post inspired the bounty) writes and
talks about publicly: X (Twitter), zk-SNARKs, quadratic funding, far-UVC, Zuzalu, and vegan food
in Buenos Aires. Expected titles are the right Wikipedia articles even when no installed pack has
them, so the "expected article found" count measures library coverage as well as search.
