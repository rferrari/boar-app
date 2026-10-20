# Bounty compliance

How BOAR meets each requirement of the
[poidh bounty 31](https://poidh.xyz/mainnet/bounty/31), with the evidence behind
every status. Statuses are only what has been checked:

- **PASS**: met, with evidence linked.
- **PARTIAL**: met with a stated limitation.
- **OPEN**: not done or not verified yet.

Last reviewed 2026-10-20 on v2.0.0-rc1 (native). Code references point to the native
app (`android-native/`). The lab devices are the Lab device 12 GB (X6) and the Lab
device 8 GB. The raw benchmark files in [evidence/](evidence/) and the demo media in
[demo/](demo/README.md) are from the 1.x Expo app (September 2026) and are marked as
history where they are used below.

| # | Requirement | Status |
|---|---|---|
| 1 | Runs on Android and compatible GrapheneOS hardware | PARTIAL |
| 2 | Operates within 12 GB of RAM | PASS |
| 3 | Uses at most 50 GB for app, models, indexes and assets | PASS |
| 4 | Works completely offline once installed | PASS |
| 5 | No API calls, remote inference, web searches or network requests during use | PASS |
| 6 | Doesn't require Google Play Services for core functionality | PASS |
| 7 | Handles explanation, comparison, synthesis and reasoning | PARTIAL |
| 8 | Responds at speeds usable for real lookups | PARTIAL |
| 9 | Published in a public GitHub repository | PASS |
| 10 | Includes all code, assets, dependencies and instructions to reproduce | PASS |
| 11 | Documents the models, datasets, indexes and other resources | PASS |
| 12 | Works on real Android hardware at submission time | PARTIAL |
| 13 | Someone can get it running within a few minutes | PARTIAL |
| 14 | Required assets included or with clear download instructions | PASS |
| 15 | Public demo on X or Farcaster | PASS |
| 16 | Demo shows offline use, hard queries, repo link and approach | PARTIAL |
| 17 | Screenshot and links submitted to poidh | OPEN |
| 18 | Repository contains the functional submitted version at claim time | OPEN |
| 19 | Not fraudulent, malicious, plagiarized or otherwise in violation | PASS |
| 20 | ">50% as good as internet + frontier models" | OPEN |

## Details

### 1. Android and GrapheneOS: PARTIAL
- The native app builds for 64-bit ARM (`arm64-v8a`), Android 8 or newer
  (`minSdk = 26` in `android-native/app/build.gradle.kts`).
- It runs on the Lab device 12 GB (X6) and the Lab device 8 GB, where it was developed
  and evaluated in October 2026. Those native evaluation runs are not committed to this
  repo yet.
- Not tested on GrapheneOS with our build. The APK has no Google Play Services
  dependencies (see 6), but that is not a substitute for a real GrapheneOS run.

### 2. 12 GB RAM: PASS
- One generation model is loaded at a time (`BoarServices.host`); the search model
  loads when retrieval first needs it.
- Setup recommends the 4B model only when Android reports 10 GiB of RAM or more (12 GB
  phones), and Qwen3 1.7B below that
  (`android-native/engine/src/main/java/team/sopa/boar/engine/models/SetupModel.kt`).
- The Model Catalog shows a memory hint before each download (file size × 1.15,
  `Compatibility.kt`). A model whose load closes the app is not loaded again on its
  own; the chat offers a retry with less memory (`LoadGuard.kt`).
- Big Boar (Boar Labs, off by default) runs a 12.3 GB model on a 12 GB phone by reading
  its experts from storage instead of holding them in RAM.
- History: in the 1.x app on the Lab device 12 GB (X6), 2026-09-24, the highest process
  peak in the benchmark was 5.07 GB (Qwen2.5-7B Q4_K_M)
  ([evidence/](evidence/2026-09-24-baseline-5-configs/)).

### 3. 50 GB storage: PASS
- Default install on a 12 GB phone: Qwen3 4B (2.50 GB), bge-small (0.04 GB) and the
  Encyclopedia package with the Vital Articles pack (0.17 GB), about 2.7 GB. Below
  12 GB, Qwen3 1.7B (1.11 GB) instead of the 4B.
- Every visible catalog model and pack together is about 19.8 GB; the Boar Labs models
  add 12.3 GB (Big Boar) and 0.68 GB (Designer), about 32.8 GB in all
  (`catalog.json`, `BmoeLab.kt`, `ImageLab.kt`).
- Enforced before every download
  (`android-native/engine/src/main/java/team/sopa/boar/engine/models/StorageBudget.kt`):
  what BOAR already stores, plus downloads in progress, a 1 GB reserve for the app and
  the new file must stay within the catalog's `storageBudgetBytes` (50 GB), and the
  phone must have the space free. Otherwise the download is refused before any data is
  fetched. In builds with Boar Labs, the user can set a different limit in Advanced
  tweaks; the default stays 50 GB.

### 4. Works offline once installed: PASS
- After the one-time setup download, answering uses only the on-device model
  (llama.cpp through JNI, `libboar_engine.so`) and the local SQLite knowledge base and
  packs.
- The chat header's pill shows OFFLINE when the phone has no working internet, and
  answers, sources and documents keep working ([USING.md](USING.md)).
- History: recorded with the 1.x app on the Lab device 12 GB (X6) in airplane mode,
  2026-09-24 ([demo/](demo/README.md)). A native airplane-mode recording is not in the
  repo yet.

### 5. No network requests during use: PASS
The network code in the app:
- `android-native/engine/src/main/java/team/sopa/boar/engine/models/AssetStore.kt`:
  model and knowledge-pack downloads, started by setup or an explicit Download tap
  (Boar Labs models included).
- `android-native/app/src/main/java/team/sopa/boar/models/HfBrowser.kt`: Hugging Face
  model search, only when the user searches.
- `android-native/app/src/main/java/team/sopa/boar/share/ResultsSharing.kt`:
  Evaluation › Share results, built in only with `EVAL_SHARING=true` (off by default:
  no Share button, and the server's address is not in the APK).

Inference, retrieval, chat history and evaluation make no network calls. The rc1 APK's
permissions are `INTERNET`, `ACCESS_NETWORK_STATE`, `RECORD_AUDIO`, `VIBRATE`,
`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` and `POST_NOTIFICATIONS`
(`android-native/app/src/main/AndroidManifest.xml`). No location permission.

Voice input (`android-native/app/src/main/java/team/sopa/boar/voice/VoiceInput.kt`) uses
only Android's on-device speech recognizer (Android 12 or later). Phones without one get
no microphone button. Typing always works.

### 6. No Google Play Services: PASS
- The rc1 APK was checked for Google Play Services: none.
  `RELEASE_VARIANT=downloader scripts/offline-proof.sh <apk>` repeats the check
  (`scripts/audit-apk.py`: permissions, Play Services and tracker classes).
- The app code never calls Google services.

### 7. Research beyond recall: PARTIAL
- A fixed 17-question evaluation set covers greeting, factual, explanation,
  comparison, synthesis, reasoning, retrieval-grounded and no-knowledge-base
  questions ([EVAL_QUERIES.md](EVAL_QUERIES.md)). The native runner runs it on the
  phone over adb ([DEVICE_EVALUATION.md](DEVICE_EVALUATION.md)).
- Deep Research breaks a question into up to 3 sub-questions, searches each, then
  writes one answer (`DeepResearch.kt`).
- History, 1.x app, Lab device 12 GB (X6), 2026-09-24
  ([evidence/](evidence/)): all 68 answers of the four configurations that loaded
  completed; with the Vital Articles pack, Qwen2.5-1.5B answered 6 of 6 factual and
  retrieval-grounded questions correctly from retrieved articles; LFM2.5-8B-A1B was the
  first model to get the RAM-budget question right.
- Limitations seen then: arithmetic errors, invented citations, and answers cut by the
  120-second step timeout. Native evaluation results for the 2.0 models (Qwen3 4B,
  Qwen3 1.7B, LFM2.5 8B) are not committed yet.

### 8. Usable speed: PARTIAL
- Every answer's receipt shows the model, the speed and the time, measured on the phone.
- History, 1.x app, Lab device 12 GB (X6), 2026-09-24, retrieval on, 512-token budget:

  | Configuration | Median total | Median TTFT | Median tok/s |
  |---|---|---|---|
  | Qwen2.5-1.5B | 21 s | 13.6 s | 11.4 |
  | Adaptive routing | 25 s | 14.1 s | 11.4 |
  | Phi-3.5-mini | 73 s | 44.0 s | 4.0 |
  | Qwen2.5-7B | 107 s | 70.5 s | 2.7 |

- Native speeds for the 2.0 models are not committed to the repo yet.

### 9. Public repository: PASS
[github.com/rferrari/boar-app](https://github.com/rferrari/boar-app). Confirm it's
public at claim time (18).

### 10. Code, assets, dependencies, instructions: PASS
- Build and install: [README.md](../README.md) and [AGENTS.md](../AGENTS.md) (Gradle
  build in `android-native/`, llama.cpp and zstd as git submodules).
- Knowledge base: `assets/corpus/*.json`, committed. The Wikipedia Vital Articles pack
  is a [release asset](https://github.com/rferrari/boar-app/releases/tag/knowledge-pack-v1),
  built reproducibly with `npm --prefix scripts run pack:build`
  ([KNOWLEDGE_PACKS.md](KNOWLEDGE_PACKS.md)).
- Models: downloaded by setup from the URLs in
  `android-native/app/src/main/assets/boar-data/catalog.json`, which also records each
  file's size and SHA-256. Downloads are verified by SHA-256 on the phone, and the
  Hugging Face URLs are pinned to a commit.
- Boar Labs engines (Big Boar, Wisp, Designer) are built from upstream repos at the
  commits pinned in `scripts/engine-pins.env` (AGENTS.md, "Boar Labs engines").

### 11. Models and datasets documented: PASS
[MODELS.md](MODELS.md) and `catalog.json`: every model and knowledge pack with source,
size, checksum and license. The knowledge base and packs are Wikipedia-derived, CC BY-SA
4.0. Third-party code: [android-native/NOTICE](../android-native/NOTICE).

### 12. Real hardware at submission: PARTIAL
- 2.0 builds run on the Lab device 12 GB (X6) and the Lab device 8 GB (October 2026).
- Still to record here: a smoke test of the published v2.0.0-rc1 APK (its SHA-256
  matching the release) on a lab device: clean install, setup, one answer.
- History: the v1.0.0 APK was smoke-tested on the Lab device 12 GB (X6) on 2026-09-25.

### 13. Running within a few minutes: PARTIAL
- No build needed: download the APK from the
  [releases page](https://github.com/rferrari/boar-app/releases), check it against its
  `.sha256` file and install it ([README.md](../README.md), "Install").
- First launch downloads about 2.7 GB on a 12 GB phone, or about 1.3 GB below that, so
  the total time depends on the connection. A tour runs while it downloads. The
  install-to-first-answer time hasn't been measured with a stopwatch.

### 14. Assets or download instructions: PASS
Setup downloads every required model and the knowledge package in the app, from the
catalog ([MODELS.md](MODELS.md)). An offline APK with the models inside is in testing
and not released.

### 15–17. Public demo and poidh submission: PASS / PARTIAL / OPEN
- Posted on X: [x.com/arferrari/status/2103677576380387484](https://x.com/arferrari/status/2103677576380387484),
  with the clips in [demo/](demo/README.md): airplane mode, a synthesis prompt, a
  follow-up and knowledge-pack facts. These show the 1.x app.
- Still to do: a demo of the 2.0 app ([DEMO_SCRIPT.md](DEMO_SCRIPT.md)), and the
  screenshot and links submitted to poidh.

### 18. Submitted version matches the repository: OPEN
- To check at claim time: the tag `v2.0.0-rc1` (or the final candidate) exists, its
  GitHub pre-release holds the APK and its `.sha256`, and the APK is signed with the
  release key (`apksigner verify --print-certs`, signer `7cf514f9…`;
  [RELEASING.md](RELEASING.md)).
- History: [v1.0.0](https://github.com/rferrari/boar-app/releases/tag/v1.0.0), the 1.x
  app, was built from the tagged commit `8ae9d44`.

### 19. Not fraudulent, malicious or plagiarized: PASS
- Original code, MIT licensed ([LICENSE](../LICENSE)). Code derived from other projects
  is credited with its license in [android-native/NOTICE](../android-native/NOTICE)
  (for example the retrieval design derived from AndroidLM, Apache-2.0).
- Models and data are third-party, used under their licenses and credited in
  [MODELS.md](MODELS.md).

### 20. ">50% as good as internet + frontier models": OPEN
Not measured yet. The on-device evaluation and its adb runner
([DEVICE_EVALUATION.md](DEVICE_EVALUATION.md), [EVAL_QUERIES.md](EVAL_QUERIES.md))
produce the BOAR side. Still needed: reference answers from a frontier model with web
search, rubric scoring against them, and blind human spot checks, with every raw file
committed.

## Evidence

All three runs are from the 1.x Expo app on the Lab device 12 GB (X6).

| Run | What | Files |
|---|---|---|
| `eval-2026-09-24T03-12-53-754Z` | Baseline: 17 queries × 5 configurations | [evidence/2026-09-24-baseline-5-configs](evidence/2026-09-24-baseline-5-configs/) |
| `eval-2026-09-24T05-42-38-163Z` | LFM2.5-8B-A1B, 17 queries | [evidence/2026-09-24-lfm2.5-8b-a1b](evidence/2026-09-24-lfm2.5-8b-a1b/) |
| `eval-2026-09-24T20-41-45-071Z` | Qwen2.5-1.5B with the Vital Articles pack, 6 queries | [evidence/2026-09-24-vital-articles-pack](evidence/2026-09-24-vital-articles-pack/) |

The `.jsonl` files are the raw per-answer records written on the phone; the reports and
answer sheets are generated from them (`node scripts/eval-summary.mjs`).
