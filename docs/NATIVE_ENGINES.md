# Experimental engines: colibri and BigMoeOnEdge

BOAR runs its models with llama.cpp through llama.rn. This branch adds two more engines for
mixture-of-experts models bigger than the phone's free RAM. Each one runs as its own process and
**streams the experts from storage** instead of holding the whole model in memory:

| Engine | What it runs | Model on the phone |
|---|---|---|
| [colibri](https://github.com/JustVugg/colibri) (Apache-2.0) | OLMoE-1B-7B, pure C | colibri's own int8 format, a folder of about 7 GB |
| [BigMoeOnEdge](https://github.com/Helldez/BigMoeOnEdge) (Apache-2.0) | any GGUF MoE it supports, on top of llama.cpp | a GGUF file, for example Qwen3.6-35B-A3B at 12.3 GB |

Both show up in Settings → Reasoning models with "experimental" in the name. Pick one with
**Select & Use**. While one is selected, adaptive routing is skipped, because the router only
chooses among llama.rn models. Retrieval, sources, Stop and the reasoning view work the same.

## How it works

- `modules/native-engine` starts an engine executable shipped as `lib*.so`. That's the only
  kind of file Android extracts into the app's native library folder and lets it execute. So
  native libraries use legacy packaging (`plugins/withNativeEngines.js`).
- It streams the engine's stdout to JavaScript as events and writes requests to its stdin.
- `src/inference/externalEngines.ts` speaks each engine's protocol:
  - **colibri:** serve mode. It sends `SUBMIT` requests with the prompt already rendered in
    OLMoE's chat template, and reads back `DATA` frames and a `DONE` line with stats.
  - **BigMoeOnEdge:** `bmoe-cli --moe-stream --session --chatml`. It sends one JSON request
    per line, and reads back `BMOE_PROGRESS` deltas and `BMOE_DONE` with stats.
- `LlamaEngine` hands generate, stop and unload to the engine whenever the selected catalog
  model sets `engine`, and frees llama.rn's model first. There's only ever one model in memory.

## Speed settings for BigMoeOnEdge

These are from [AndroidLM](https://github.com/Phineas1500/AndroidLM)'s measurements on a Pixel 8
Pro (`notes/2026-09-24-speed-levers.md`), where together they took generation from about 3.5 to
5.7 tok/s:

| Setting | Where |
|---|---|
| AndroidLM's engine patches: pinned thread pool, priority, fast-core placement, repacked dense weights, ik_llama.cpp's 2/3-bit ARM kernels (about 2x faster prompt reading) | `patches/bigmoeonedge/`, applied by the build script |
| Built with `i8mm`; the app refuses to start it on a CPU without i8mm instead of crashing | `BMOE_ARM_ARCH` in the build script, `BmoeEngine.load` |
| 5,000 MiB expert cache on phones with 11 GiB+ of RAM (`auto` otherwise), 2 read lanes overlapping compute, dense weights in memory the kernel won't reclaim | `BmoeEngine.args` |
| Priority -16, compute threads pinned to the fastest cores, repacked dense weights | `BmoeEngine.env` (`BMOE_NICE`, `BMOE_CPUMASK`, `BMOE_REPACK`) |
| 2 retrieved articles instead of 4, so there's less prompt to read | `ChatScreen` |
| The answer reaches the screen at most 4 times a second | `ChatScreen` |

## Build the engines

```bash
git clone https://github.com/JustVugg/colibri ../colibri
git clone --recursive https://github.com/Helldez/BigMoeOnEdge ../BigMoeOnEdge
scripts/build-native-engines.sh        # builds both into modules/native-engine/.../jniLibs
npx expo prebuild -p android --clean
npx expo run:android --device --app-id team.sopa.aoair.dev
```

The binaries are build output, not in git. The builds are portable
(`armv8.2-a+dotprod+fp16`, upstream's choice), and use React Native's `libc++_shared.so`.

## Put the models on the phone (USB)

The app doesn't download these; copy them with adb. The files must be readable by apps, so
keep the folders at 755 and the files at 644:

```bash
# BigMoeOnEdge: any supported GGUF MoE (the catalog entry expects this path and name)
adb shell mkdir -p /data/local/tmp/bmoe
adb push Qwen3.6-35B-A3B-UD-Q2_K_XL.gguf /data/local/tmp/bmoe/
adb shell chmod 755 /data/local/tmp/bmoe && adb shell chmod 644 /data/local/tmp/bmoe/*.gguf

# colibri: convert an OLMoE GGUF with colibri's converter, then copy the folder
python ../colibri/c/tools/convert_gguf_to_olmoe.py --input OLMoE-1B-7B-0125-Instruct-Q8_0.gguf --output olmoe-colibri
adb shell mkdir -p /data/local/tmp/colibri/olmoe-colibri
adb push olmoe-colibri/. /data/local/tmp/colibri/olmoe-colibri/
adb shell chmod -R a+rX /data/local/tmp/colibri
```

To use another model, add a catalog entry with `engine` and `externalPath` in
`src/models/manifest.ts`.

## What to expect on the test phone (Dimensity 8300, 11.6 GB RAM)

- **colibri, OLMoE:**
  - about 2–3 GB of RAM with 16 of each layer's 64 experts cached;
  - the first answers are slow while the expert cache warms up (0.4 tok/s at a 46% hit rate
    on the first question);
  - about 1.8 tok/s over a longer answer in our earlier test.
- **BigMoeOnEdge, Qwen3.6-35B-A3B:** a real 35B model on the phone. It's slow, and reading a
  long prompt, like BOAR's retrieved articles, takes a minute or more before the first word.
