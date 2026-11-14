# Qwen3-0.6B small model test: phone requests

t9 0443 and 0452. The 0.6B is a bench-only model (`android-native/app/src/main/assets/boar-data/bench-models.json`):
setup, the tiers, onboarding, the Model Catalog and the chat never list it, and the app never downloads it.

## The file

unsloth/Qwen3-0.6B-GGUF at revision `50968a4468ef4233ed78cd7c3de230dd1d61a56b`, `Qwen3-0.6B-Q4_K_M.gguf`,
396,705,472 bytes, SHA-256 `ac2d97712095a558e31573f62f466a3f9d93990898b0ec79d7c974c1780d524a`.
Chat template in the file (`tokenizer.chat_template`, 4,905 bytes): SHA-256
`5da44855ab7e0641774d5ed0ff0b4f89671d01df7d316e54d06557a962211cb4`.

## Putting it on the phone

The bench looks for `models/qwen3-0.6b-q4km.gguf` under the app's files, then under its shared folder, and takes it
only at exactly 396,705,472 bytes. On a release build (not debuggable) use the shared folder:

```bash
PKG=team.sopa.aoair   # team.sopa.aoair.native for a debug build, team.sopa.aoair.labs for a Labs build
adb shell mkdir -p /sdcard/Android/data/$PKG/files/models
adb push Qwen3-0.6B-Q4_K_M.gguf /sdcard/Android/data/$PKG/files/models/qwen3-0.6b-q4km.gguf
adb shell sha256sum /sdcard/Android/data/$PKG/files/models/qwen3-0.6b-q4km.gguf   # ac2d9771...
```

On a debuggable build `run-as $PKG` into `files/models/` works too. To remove it afterwards, delete the same file.
The probe request's status lists `installedModels`; `qwen3-0.6b-q4km` must be there.

## Requests

| File | Preset | Order |
|---|---|---|
| `0-probe-greeting.json` | greeting-1 only | 0.6B: does it load, what prompt format |
| `1a-complete-0.6b-first.json` | `complete-core+portuguese+health` | 0.6B, then 1.7B |
| `1b-complete-1.7b-first.json` | `complete-core+portuguese+health` | 1.7B, then 0.6B |
| `2a-medium-0.6b-first.json` | `medium-core+portuguese+health` | 0.6B, then 1.7B |
| `2b-medium-1.7b-first.json` | `medium-core+portuguese+health` | 1.7B, then 0.6B |

The questions: the fixed 17 (topic `core`), the language fix's 30 (`portuguese`) and five health and safety questions
(`health`, new: carbon monoxide, a burn, a sting, a fever, a dose): 52. `complete` answers each in Succinct and in
Detailed (the chat's default style; the health questions the chat keeps short answer in Succinct in both);
`medium` only in Succinct. The scorecard checks each topic's rules (Portuguese answers in Portuguese, no [n] without a
real source). Labs: as switched on in the app (same for both models). Language arm: as the build ships (1).

Start one with the preset script (it builds the same request):

```bash
scripts/bench-preset.sh complete-core+portuguese+health --models qwen3-0.6b,qwen3-1.7b
scripts/bench-preset.sh complete-core+portuguese+health --models qwen3-1.7b,qwen3-0.6b
```

or by hand:

```bash
req=$(base64 -w0 < scripts/bench-requests/qwen3-0.6b/1a-complete-0.6b-first.json)
adb shell am start -n team.sopa.aoair/team.sopa.boar.bench.BenchActivity --es run eval --es request64 "$req"
```

## The blind file

```bash
node scripts/blind-pair.mjs --models qwen3-0.6b,qwen3-1.7b --out blind-small06 <0.6b-first>.jsonl <1.7b-first>.jsonl
```

`blind.md` (A and B per question, drawn at random, no names or timings), `marks.csv` (better / same / worse and wrong
facts) and `key.json` (who is A and B, and the seed; open after marking). `--pass succinct` or `--run <runId>` for
one pass or one run.
