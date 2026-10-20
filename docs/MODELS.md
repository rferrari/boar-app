# Models, indexes, and datasets

This file documents every offline asset the app ships with or depends on, per the
bounty's "clearly document the models, datasets, indexes, and other resources used"
requirement.

## Phi-3.5-mini — optional (the default until 2026-09-24)

**[Phi-3.5-mini-instruct](https://huggingface.co/microsoft/Phi-3.5-mini-instruct)**
(Microsoft, **MIT license**), quantized GGUF from
**[bartowski/Phi-3.5-mini-instruct-GGUF](https://huggingface.co/bartowski/Phi-3.5-mini-instruct-GGUF)**,
file `Phi-3.5-mini-instruct-Q4_K_M.gguf` (~2.23GB, sha256 below).

- 3.8B params, dense, Q4_K_M quantization
- Chosen over Qwen2.5-3B-Instruct because Qwen's 3B GGUF ships under the restrictive
  `qwen-research` license (non-commercial/research-only), which is a poor fit for a
  public open-source bounty submission; Phi-3.5-mini's MIT license has no such
  restriction.
- Strong reasoning-per-parameter for its size (outperforms most 1–3B dense models on
  MMLU/GSM8K-style benchmarks per its model card), directly targeting the bounty's
  ">1B dense model" reasoning bar.
- Runs via `llama.cpp` (BOAR's own JNI build) on Android CPU, mmap-streamable so weights aren't
  fully pinned in RAM.
- **Upgrade path (not yet implemented)**: a proper MoE model (e.g. an Apache/MIT
  licensed variant in the Qwen1.5-MoE / OLMoE family, ~2–3B active params) for more
  world knowledge at similar active-param RAM/speed cost, once benchmarked on-device
  against this baseline.

## Embedding model — chosen

**[bge-small-en-v1.5](https://huggingface.co/BAAI/bge-small-en-v1.5)** (BAAI, **MIT
license**), quantized GGUF from
**[CompendiumLabs/bge-small-en-v1.5-gguf](https://huggingface.co/CompendiumLabs/bge-small-en-v1.5-gguf)**,
file `bge-small-en-v1.5-q8_0.gguf` (~35MB, sha256 below).

- 33M params, well under the 300MB sub-budget, fast enough to stay resident alongside
  the primary LLM without meaningfully affecting the 12GB RAM budget.

## Local knowledge base / retrieval index

- **Lexical**: SQLite FTS5 virtual table (`chunks_fts`), built from the same source
  corpus as the vector index. See `android-native/engine/src/main/java/team/sopa/boar/engine/rag/KnowledgeBase.kt`.
- **Semantic**: brute-force cosine search over stored float32 embeddings
  (`chunk_embeddings` table). Fine at the corpus scale a phone can hold within the
  storage budget; revisit with a proper ANN index (e.g. quantized HNSW) only if
  corpus size makes brute-force too slow on-device.
- **Corpus source**: `assets/corpus/corpus.json` — 300 docs: ~58 curated article
  summaries covering AI/systems topics relevant to the bounty's own eval questions
  (MoE, quantization, RAG, BM25, mmap, transformers) plus general research topics
  across science, history, geography, biology, and economics, topped up with
  random **Wikipedia** (CC BY-SA 4.0) articles to reach 300. Built by
  `scripts/build-corpus.mjs` + `scripts/build-corpus-tier.mjs` (dev-machine-only,
  online, run once to curate/update the corpus — not run by the shipped app).
  ~188KB of text; embeddings are computed on-device at first launch via
  `android-native/engine/src/main/java/team/sopa/boar/engine/rag/SeedCorpus.kt`, not precomputed, so they always match whatever
  embedding model ships.
- **Attribution**: per Wikipedia's CC BY-SA 4.0 license, each stored chunk keeps a
  `source` field linking back to its origin article
  (`https://en.wikipedia.org/wiki/<Title>`), surfaced to the user as a citation.
- **Known limitation**: still just Wikipedia lead-paragraph summaries, not full
  article text or non-Wikipedia sources — sufficient to demonstrate the RAG
  pipeline and answer the eval questions in `docs/EVAL_QUERIES.md`, but growing
  it further (fuller article text, other sources) would meaningfully improve
  real-world usefulness.

## Language scope: English-only for this version

bge-small-en-v1.5 is an English-only embedding model, and the bootstrap corpus is
English-only. This was a deliberate choice, not an oversight: swapping in a
per-locale embedding model would also require a per-locale knowledge base and
separate retrieval-quality testing per language, and the primary LLM
(Phi-3.5-mini-instruct) is itself primarily English-tuned — a multilingual
embedding model wouldn't meaningfully help without a multilingual LLM and corpus
to match. Worth revisiting (e.g. a multilingual embedding model matched to the
device's locale, paired with a multilingual LLM candidate and corpus) as future
work, not in this version.

## Default generation model: Qwen2.5-1.5B (required, downloaded at first-run setup)

**[Qwen2.5-1.5B-Instruct](https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct)**
(Alibaba, **Apache-2.0 license**), quantized GGUF from
**[bartowski/Qwen2.5-1.5B-Instruct-GGUF](https://huggingface.co/bartowski/Qwen2.5-1.5B-Instruct-GGUF)**,
file `Qwen2.5-1.5B-Instruct-Q4_K_M.gguf` (~0.92GB, sha256 in `android-native/app/src/main/assets/boar-data/catalog.json`).

Downloaded alongside Phi-3.5-mini and the embedding model during mandatory
first-run setup — `required: true`, same as the other two — rather than left
as an optional Settings-screen download. This is deliberate: the adaptive
routing work (`android-native/engine/src/main/java/team/sopa/boar/engine/routing/`, see `docs/ADAPTIVE_ROUTING.md`) needs at least
two real, actually-different-sized models to route between (a `fast` role and
a `general`/`reasoning` role) from the moment the app is usable, not only
after a user manually fetches a second model later. Curated as `fast` in
the catalog (`android-native/app/src/main/assets/boar-data/catalog.json`) — smallest/quickest of the
three catalog LLMs.

## Optional LLM catalog (choose your model)

Beyond the required default, Settings offers these models, each tested on a real
phone (Xiaomi 2311DRK48G, Dimensity 8300, 11.6 GB RAM; see
[DEVICE_EVALUATION.md](DEVICE_EVALUATION.md)):

| Candidate | Params | Quant | Size | License | Measured on the phone |
|---|---|---|---|---|---|
| Phi-3.5-mini-instruct | 3.8B dense | Q4_K_M | 2.39GB | MIT | ~4 tok/s; most complete comparisons and syntheses |
| Qwen2.5-7B-Instruct | 7B dense | Q4_K_M | 4.68GB | Apache-2.0 | ~2.7 tok/s; accurate, often too slow for the 120s step limit |
| LFM2.5-8B-A1B | 8B MoE, ~1.5B active | Q4_K_M | 5.16GB | LFM Open License v1.0 | ~15 tok/s; best reasoning, but it thinks first and needs a larger answer budget |
| Gemma 4 E4B | ~4B effective | QAT Q4_0 | 5.15GB | Apache-2.0 | loads and answers; not benchmarked yet |

Sizes and SHA-256 checksums are in `android-native/app/src/main/assets/boar-data/catalog.json`. Switching models re-loads the
inference engine, which releases the previous context before loading a new one.

## Setup tiers and corpus packs

First run offers the tiers in `android-native/app/src/main/assets/boar-data/catalog.json` (`tiers`, picked in the setup
wizard) — all download the same required models, only
the knowledge base differs, and higher tiers are strict supersets:

| Tier | Knowledge base | Extra download |
|---|---|---|
| Minimum | 300 bundled topics (in the APK, no download) | none |
| Standard | + 1,000 more Wikipedia-derived topics | ~600KB |
| Full | + 4,000 more on top of Standard (5,300 total) | ~3MB total |

Corpus packs (`corpus-standard`, `corpus-full`) are built by
`scripts/build-corpus-tier.mjs`, which uses MediaWiki's batched
`generator=random` API (20 articles/request) rather than one-by-one summary
calls — far fewer HTTP round-trips for a given count. They're committed to
this repo and downloaded via a `raw.githubusercontent.com` URL (no separate
hosting needed) through the same download path as everything else
(`AssetStore` in the engine), verified by sha256.

**Honest scope note**: "Full" is not literally the entire English Wikipedia —
that's a different scale of engineering (dump processing, compression, a real
ANN index) not attempted here. The architecture is designed so more packs can
be added later within the 50GB storage budget without changing how any of
this works — the catalog's `models` and `tiers` are meant to grow.

## "Deep Research Mode" — honest scope note

Settings has a "🔬 Deep Research Mode" toggle (`android-native/engine/src/main/java/team/sopa/boar/engine/answer/DeepResearch.kt`)
that decomposes a question into 2-3 sub-questions, retrieves + answers each
separately, then synthesizes one final answer. This is **not** multiple AI
models/agents running concurrently — running 3 models at once, or a
dedicated planner/critic model, genuinely doesn't fit in the 12GB RAM budget
alongside everything else. It's several sequential generation
calls on the *same* single loaded model, playing different roles in turn.
Noticeably slower than a normal reply (multiple LLM passes instead of one) —
the UI badge says "Deep Research", not "multi-agent", and the in-app
description spells this out, to avoid overclaiming what's actually
happening.

The app reads the device's physical RAM (not just its own usage) so the model catalog
(`android-native/engine/src/main/java/team/sopa/boar/engine/models/Compatibility.kt`) can show a
rough 🟢/🟡/🔴 compatibility hint before downloading a large model —
estimated from file size (×1.15 for KV-cache/context overhead), which is an
approximation, not a guarantee. Models beyond the catalog can be found on Hugging
Face from the app (docs/USING.md, "Finding more models").

## Multi-session chat history & conversation memory

Chat sessions/messages persist locally in the same SQLite database as the
knowledge base — `android-native/engine/src/main/java/team/sopa/boar/engine/chat/ChatStore.kt` is the CRUD layer, surfaced as a "Recent
Chats" list in the drawer with auto-generated titles and per-session
delete.

For long conversations, `android-native/engine/src/main/java/team/sopa/boar/engine/answer/Summarize.kt` condenses older turns
into a running summary, and `HistoryBudget.kt` keeps the latest exchanges within the
prompt budget, instead of sending the whole transcript every time.

**Concurrency constraint worth being explicit about**: title generation and
summarization are themselves LLM calls through the *same* `llamaEngine`
context as the main chat — `llama.cpp` contexts only run one completion at
a time. They run in the background after a response finishes; a new message
stops one still in flight before its own generation starts, so it never queues
behind it.

## Voice input (offline speech-to-text)

`android-native/app/src/main/java/team/sopa/boar/voice/VoiceInput.kt` wraps Android's built-in `SpeechRecognizer` with
`EXTRA_PREFER_OFFLINE`, behind the mic button in the chat. No cloud STT API is called.

**Honest scope note**: this depends on a system-provided recognition service
(Google's, or an OEM's) being installed on the device. Most stock
Android/OEM builds ship one; **GrapheneOS and other de-Googled builds
typically do not**, so voice input won't work there out of the box —
the app detects this and the UI shows a clear "unavailable" message
rather than pretending to listen. A true cross-device guarantee would mean
bundling a `whisper.cpp` model with a custom binding, which is a project on
the scale of the llama.cpp integration itself — documented as future work,
not attempted in this version. Typing always works everywhere regardless.

## Delivery: one-time first-run download

All models are in the in-app catalog (`android-native/app/src/main/assets/boar-data/catalog.json`). The app ships small (no
multi-GB assets in the APK); on first launch a mandatory setup screen downloads the
answer model (Qwen3 4B on phones with 10 GB of RAM or more, Qwen2.5-1.5B below that;
`android-native/engine/src/main/java/team/sopa/boar/engine/models/SetupModel.kt`) and the embedding model. Phi-3.5-mini, Qwen2.5-7B,
LFM2.5-8B-A1B and Gemma 4 E4B are optional entries in the same catalog. Once setup is
done, answering, retrieval and your documents work without a network connection.

Downloads resume after an interruption.
Optional models are fetched only when the user taps Download on an entry, or adds one
from a Hugging Face search.

## Verification

Every catalog entry has a `sha256` and `sizeBytes`. `AssetStore`
(`android-native/engine/src/main/java/team/sopa/boar/engine/models/AssetStore.kt`) checks the size, verifies the sha256 after a download, and
records verified files in `files/integrity.json` so they aren't hashed again. Files live
in the app's `files/` folder under the catalog's `filename`.

The 1.x Expo app's other delivery paths (models bundled into the APK, pre-seeding with
`scripts/setup-models.sh`) are on the `expo` branch only.
