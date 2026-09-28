# Laya in BOAR: proposal

[Laya](https://github.com/NandhaKishorM/laya) (Apache-2.0) is a "System 1" decision model: a
bidirectional encoder with a small decision head. It answers typed questions about a text (yes/no,
score, or choice) zero-shot, in one forward pass per question. Mood Lab (`~/projects/mood-lab`)
runs it with PyTorch on a laptop. This page is the plan for running it on the phone, next to
llama.rn.

## What Laya is

| Checkpoint | Encoder | Params | Weights published | Languages |
|---|---|---|---|---|
| `convaiinnovations/laya-multilingual` | mmBERT-base (MIT), 1,024 tokens | 322M | 644 MB (16-bit safetensors) | 100+ |
| `convaiinnovations/laya` (English) | ModernBERT-large, 512 tokens | 421M | 843 MB | English only: it collapses on other languages, with high confidence |

- **Input:** each question is its own sequence:
  `[CLS] <type> question [SEP] [MASK] option1 [MASK] option2 … [SEP] text [SEP]`
  (`laya/common.py`, `build_sequence`). So **N questions over M texts = N × M encoder passes**;
  the text is encoded again for every question.
- **Runtimes:** the package runs on PyTorch, and `laya/onnx_agent.py` can run an ONNX file, but
  **no ONNX export is published** and the package has no exporter. We export it ourselves.
- **Measured in Mood Lab (desktop i7-9750H CPU, 7 questions on one sentence):** about 350 ms for
  the multilingual model and about 1.3 s for English.

## Corrections to the brief

| Brief | What the numbers say |
|---|---|
| "~200–300 MB RAM" | mmBERT-base carries a 256k-token vocabulary: about 197M of its ~307M parameters are the embedding table. Quantized to int8 it's **about 320–350 MB** on disk and more in RAM. English is about 430 MB at int8. 200 MB would need vocabulary pruning. |
| "80–150 ms per pass, <100 ms pre-pass" | Plausible for **one question on a short prompt** with int8 on the fast cores, but this is unmeasured on a phone. A full 7-question analysis would take several passes, so expect 0.5–1 s. Filtering 4 chunks with 1 question each is about 4 passes. |
| "Filter subjective or emotional noise from chunks" | BOAR's chunks are Wikipedia or the user's documents, which are rarely emotional. The useful filter is **relevance**: "Does this passage help answer: *{question}*?" (yes/no), which Laya is built for. |
| "Laya stays resident, never unload llama.rn" | Fine next to the default model (about 1.8 GB peak, plus about 350 MB for Laya). Not next to the 35B streaming experiments, which use all the RAM. |
| English checkpoint | BOAR users write in Portuguese and English: use **multilingual**, which is also the smaller one. |

## Where it plugs in

- **Retrieval, `src/rag/retrieve.ts`:** after `fuseRetrievalResults`, take about 2× the needed
  chunks and run `filterChunksWithLaya(query, chunks)`. That's one yes/no relevance question per
  chunk; keep the top K whose P(yes) passes a threshold. It's optional and off by default until
  measured; the telemetry records its latency.
- **Before the query, next to `classifyTask`, `src/routing/classify.ts`:**
  - an urgency or distress check ("Is the writer in danger or asking for urgent help?"), which
    can surface safety guidance first;
  - "Is this a quick factual lookup or a multi-step question?", which can pick the fast model or
    the heavy one.

  The rule-based classifier stays the default; Laya overrides it only above a confidence
  threshold.

## Plan, with a go/no-go before app code

1. **Export (desktop):**
   - Export `laya-multilingual`'s `DecisionModel` to ONNX, with dynamic batch and length.
   - Quantize it to int8 (onnxruntime dynamic quantization).
   - Check that its answers match PyTorch on a fixed question set (target: max difference below
     0.02), and record file size and desktop latency per pass.
2. **Measure on the phone, without the app.** Build a tiny C runner with the NDK against ONNX
   Runtime's Android library and run it over `adb shell` on the fast cores, measuring:
   - **ms per pass** at 64, 256 and 512 tokens;
   - **peak RSS**;
   - **load time**.

   **Go** if one pass on a short prompt is ≤ ~100 ms and RSS ≤ ~400 MB.
3. **Bridge:** `onnxruntime-react-native` (the official package, Android arm64), or a small
   native module around the C runner. Add:
   - the Gemma/mmBERT tokenizer (`tokenizer.json`, native or JS);
   - a JS port of `build_sequence` and of the head's output decoding;
   - the API `analyzeText({ text, questions })`, returning `{ type, value, probabilities }`
     per question (the Mood Lab shape).
4. **Integrate:** the retrieval filter and the pre-query checks above, each behind a setting,
   with latency and RAM in execution telemetry.
5. **Evaluate:** run `npm run eval:device` with and without the filter: answer quality, first-token
   time and memory. Keep the filter only if it helps.

## Open questions

- Does mmBERT's attention (global plus sliding window, RoPE) export cleanly to ONNX, or does it
  need `attn_implementation="eager"` for the export?
- Would pruning the vocabulary to the languages BOAR serves bring the model near 200 MB without
  hurting accuracy?
