# Models, indexes, and datasets

This file documents every offline asset BOAR 2.0 (the native Android app in
`android-native/`) ships with or downloads, per the bounty's "clearly document the
models, datasets, indexes, and other resources used" requirement.

Every model and pack is an entry in
`android-native/app/src/main/assets/boar-data/catalog.json`, with its source URL (Hugging
Face URLs pinned to a commit), size, SHA-256 and license.

## Answer models

| Model | Params | Quant | Size | License | In the app |
|---|---|---|---|---|---|
| [Qwen3-4B-Instruct-2507](https://huggingface.co/unsloth/Qwen3-4B-Instruct-2507-GGUF) | 4B dense | Q4_K_M | 2.50 GB | Apache-2.0 | Setup default on 12 GB phones: "Recommended · best answers" |
| [LFM2.5-8B-A1B](https://huggingface.co/LiquidAI/LFM2.5-8B-A1B-GGUF) | 8B mixture of experts, about 1B active | Q4_K_M | 5.16 GB | LFM Open License v1.0 | On 12 GB phones: "Recommended · fast" |
| [Qwen3-1.7B](https://huggingface.co/unsloth/Qwen3-1.7B-GGUF) | 1.7B dense, thinking off | Q4_K_M | 1.11 GB | Apache-2.0 | Setup default on phones under 12 GB: "Recommended · light" |
| [Qwen2.5-1.5B-Instruct](https://huggingface.co/bartowski/Qwen2.5-1.5B-Instruct-GGUF) | 1.5B dense | Q4_K_M | 0.99 GB | Apache-2.0 | Listed; the earlier compact model |
| [Gemma 4 E4B](https://huggingface.co/google/gemma-4-E4B-it-qat-q4_0-gguf) | about 4B effective | QAT Q4_0 | 5.15 GB | Apache-2.0 | Listed, no badge |
| [Qwen2.5-7B-Instruct](https://huggingface.co/bartowski/Qwen2.5-7B-Instruct-GGUF) | 7B dense | Q4_K_M | 4.68 GB | Apache-2.0 | Listed, no badge |
| [Phi-3.5-mini-instruct](https://huggingface.co/bartowski/Phi-3.5-mini-instruct-GGUF) | 3.8B dense | Q4_K_M | 2.39 GB | MIT | Hidden from the catalog |

The 12 GB line is a RAM reading: Android reports about 10.7 GB on a 12 GB phone and
about 7.3 GB on an 8 GB phone, so setup recommends the 4B when it reads 10 GiB or more
(`SetupModel.recommended` in
`android-native/engine/src/main/java/team/sopa/boar/engine/models/SetupModel.kt`). The
user can pick another model in setup or later in the Model Catalog.

All answer models run through llama.cpp (BOAR's own JNI build, `libboar_engine.so`) on
the phone's CPU. Only one answer model is loaded at a time. Switching models releases the
previous one before the next loads.

The catalog descriptions carry no speeds or dates. Measured numbers live in the
evaluation files, with their phone and date (see the history section at the end).

### Boar Labs models

Builds with `EXPERIMENTAL_FEATURES=true` add the models of some labs. They download only
when the user asks:

| Lab | Model | Size | Notes |
|---|---|---|---|
| Big Boar | Qwen3.6-35B-A3B, UD-Q2_K_XL | 12.3 GB | Runs on BigMoeOnEdge, which keeps the always-used weights in memory and reads each word's experts from storage. Download it in the app or copy it over USB. |
| Boar Designer | stable-diffusion.cpp image model | 0.68 GB | Draws a picture from an answer. |
| Boar Wisp | OLMoE-1B-7B on colibri | not in the catalog | In review. |

## Search (embedding) model

**[bge-small-en-v1.5](https://huggingface.co/BAAI/bge-small-en-v1.5)** (BAAI, MIT
license), GGUF from
[CompendiumLabs/bge-small-en-v1.5-gguf](https://huggingface.co/CompendiumLabs/bge-small-en-v1.5-gguf),
file `bge-small-en-v1.5-q8_0.gguf` (37 MB). 33M parameters. Setup always downloads it.
Knowledge packs must be embedded with this exact file (checked by SHA-256), or the app
ignores them.

## Local knowledge base and retrieval index

- **Lexical**: SQLite FTS5 (`chunks_fts`). See
  `android-native/engine/src/main/java/team/sopa/boar/engine/rag/KnowledgeBase.kt`.
- **Semantic**: brute-force cosine search over stored float32 embeddings
  (`chunk_embeddings`). This is fast enough at the scale a phone holds. An ANN index
  (for example quantized HNSW) is future work, only if corpus size makes brute force
  too slow.
- **Built-in corpus**: `assets/corpus/corpus.json`, 300 short articles: about 58 curated
  summaries on AI and systems topics (MoE, quantization, RAG, BM25, mmap, transformers)
  plus Wikipedia (CC BY-SA 4.0) articles across science, history, geography, biology
  and economics. Built by `scripts/build-corpus.mjs` and
  `scripts/build-corpus-tier.mjs` on a dev machine. Its embeddings are computed on the
  phone at first launch (`SeedCorpus.kt`), so they always match the shipped search model.
- **Attribution**: each stored chunk keeps a `source` link to its origin article
  (`https://en.wikipedia.org/wiki/<Title>`), shown as a citation under the answer.

## Knowledge packages in setup

Setup offers two packages (`SetupViewModel.kt`). Both download the search model and the
chosen answer model. They differ in the knowledge they add:

| Package | Catalog tier | Knowledge | Extra download |
|---|---|---|---|
| Essential | `full` | the 300 built-in topics + `corpus-standard` (1,000) + `corpus-full` (4,000) | about 3 MB |
| Encyclopedia | `encyclopedia` | Essential + the Wikipedia Vital Articles pack (`wiki-vital5`, about 50,000 article introductions) | about 167 MB |

Setup recommends the biggest package that fits in the free space, so most phones get
the Vital Articles pack at setup. The pack is a release asset,
[`knowledge-pack-v1`](https://github.com/rferrari/boar-app/releases/tag/knowledge-pack-v1).
How it is built: [KNOWLEDGE_PACKS.md](KNOWLEDGE_PACKS.md).

The corpus packs (`corpus-standard`, `corpus-full`) are committed to this repo and
downloaded from a `raw.githubusercontent.com` URL pinned to a commit.

**Scope note**: this is not the whole English Wikipedia. That needs dump processing,
compression and a real ANN index, which BOAR does not attempt. More packs can be added
within the 50 GB storage budget without changing how retrieval works.

## Language scope

The app's interface is in English and Portuguese. The search model (bge-small-en-v1.5)
and the knowledge packs are English. Questions in other languages reach an English
library, and their retrieval quality has not been evaluated.

## Deep Research: scope note

Deep Research (Settings → How BOAR answers, or the style button in the chat;
`android-native/engine/src/main/java/team/sopa/boar/engine/answer/DeepResearch.kt`)
breaks a question into at most 3 sub-questions, searches and reads sources for each,
then writes one answer. These are several sequential calls on the same loaded model,
not several models running at once. It is slower than a normal reply.

## Memory hints in the catalog

The app reads the phone's total RAM, and the Model Catalog
(`android-native/engine/src/main/java/team/sopa/boar/engine/models/Compatibility.kt`)
shows a rough compatibility hint before a download, estimated as the file size × 1.15
for context overhead. It is an approximation, not a guarantee. A model whose load closes
the app is not loaded again on its own the next time (`LoadGuard.kt`); the chat offers to
try again with less memory. Models beyond the catalog can be found on Hugging Face from
the app ([USING.md](USING.md), "Finding more models").

## Chat history and conversation memory

Chats are stored locally in SQLite
(`android-native/engine/src/main/java/team/sopa/boar/engine/chat/ChatStore.kt`) and listed
in the menu. With a PIN set, they are sealed with it. For long conversations,
`Summarize.kt` condenses older turns into a running summary and `HistoryBudget.kt` keeps
the latest exchanges within the prompt budget. Title generation and summaries are calls
on the same loaded model, after an answer finishes.

## Voice input

`android-native/app/src/main/java/team/sopa/boar/voice/VoiceInput.kt` uses only Android's
on-device speech recognizer (`createOnDeviceSpeechRecognizer`, Android 12 or later), so
the audio is recognized on the phone. Phones without an on-device recognizer (for
example GrapheneOS without a recognition service) get no microphone button. Typing
always works.

## Delivery: one-time first-run download

The APK carries no multi-GB assets. On first launch a mandatory setup downloads the
answer model, the search model and the chosen knowledge package. After setup, answering,
retrieval and your documents work without a network connection. A download that stops
resumes from where it stopped (HTTP Range). Other models download only when the user
taps Download in the Model Catalog, or adds one from a Hugging Face search.

The offline APKs carry the models and the pack inside them instead: Qwen3 1.7B (GitHub and
Hugging Face) or Qwen3 4B (Hugging Face only). See [USING.md](USING.md#the-offline-apk).

## Verification

Every catalog entry has a `sha256` and `sizeBytes`. `AssetStore`
(`android-native/engine/src/main/java/team/sopa/boar/engine/models/AssetStore.kt`) checks
the size, verifies the SHA-256 after a download and records verified files in
`files/integrity.json`. Files live in the app's `files/` folder under the catalog's
`filename`, the same layout as the 1.x app, so an upgrade keeps them.

## History: measurements from the 1.x app

These numbers are from the 1.x Expo app, on the Lab device 12 GB (X6), 2026-09-24
([evidence/](evidence/)). They are kept as history and are not numbers for the native
app.

| Model | Median tok/s | Notes |
|---|---|---|
| Qwen2.5-1.5B | 11.4 | 17 questions; the phone was thermally throttled |
| Phi-3.5-mini | 4.0 | most complete comparisons and syntheses |
| Qwen2.5-7B | 2.7 | accurate, often too slow for the 120 s step limit |
| LFM2.5-8B-A1B | 14.8 | thinks first; 4 of 17 answers lost to reasoning that used the whole budget |
