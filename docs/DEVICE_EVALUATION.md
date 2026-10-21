# Benchmark models on a real phone

BOAR can benchmark any installed model on a real Android phone, started from your
computer over adb. The phone runs the questions and shows live progress on its
Evaluation screen; the computer pulls the results.

## Two ways to run it

**From the computer.** The evaluation runner is `BenchActivity`. It is exported with
`android.permission.DUMP`, which adb's shell holds and other apps don't, so only adb
(USB or wireless debugging) can start it. It works on release builds.

**On the phone.** Settings › Advanced tweaks › Evaluation (or the side menu in builds with
`SHOW_EVAL_IN_MENU`). Tick the models, tap Run, and export JSONL or
CSV through the share sheet when it's done.

Both run the same evaluation code (`android-native/app/src/main/java/team/sopa/boar/eval/`
and `android-native/engine/src/main/java/team/sopa/boar/engine/eval/`), so results are
identical in shape and comparable.

## Setup, once

1. **On the computer:** `adb` (Android SDK platform-tools) on your `PATH`, and Node for
   the report (`scripts/eval-summary.mjs` needs no `npm install`).
2. **On the phone:** Developer options › USB debugging on. Connect the cable and accept
   "Allow USB debugging". `adb devices` must show it as `device`.
3. **BOAR installed** ([AGENTS.md](../AGENTS.md)), first-run setup finished, and every
   model you want to compare downloaded. The runner never downloads models.

## Commands

```bash
adb devices                                        # exactly one device, state "device"
req=$(printf '%s' '{"requestId":"req-1","models":["qwen2.5"],"queries":["greeting-1"]}' | base64 -w0)
adb shell am start -n team.sopa.aoair/team.sopa.boar.bench.BenchActivity \
  --es run eval --es request64 "$req"
adb logcat | grep -i eval                          # progress
adb pull /sdcard/Android/data/team.sopa.aoair/files/eval-out eval-results/
node scripts/eval-summary.mjs --report eval-results/eval-out/<runId>.jsonl
node scripts/eval-summary.mjs --answers eval-results/eval-out/<runId>.jsonl   # answers side by side, for grading
```

- `models` matches part of an installed model's id or label (adaptive routing isn't
  supported by the native runner yet). Leave out `queries` for the full question set
  ([EVAL_QUERIES.md](EVAL_QUERIES.md)); `queries` takes ids or categories.
- `"labs": [ids]` names the Boar Labs plugins that answer and check in this run, as in
  the chat with only those labs on, e.g. `"labs":["calc","facts"]`. Without it, the labs
  switched on in the app answer. Run the same questions with and without a lab to compare.
- `--ei waitmin 10` is how long the runner waits for the request.
- For a debug build use `team.sopa.aoair.native`, for a Labs build `team.sopa.aoair.labs`,
  in both the component and the pull path.

## Test switches

`BenchActivity` takes one `--es run <switch>`. Every switch is adb-only for the same reason (`DUMP`); none of them is
reachable from the app's screens. Logs go to logcat under the tag shown.

| Switch | What it does | Options | Log tag |
|---|---|---|---|
| `eval` | The evaluation runner above | `--es request64 <base64 JSON>`, `--ei waitmin <minutes>` (default 10) | `BOAR_EVAL` |
| `retrieval` | Runs the app's own search on a list of questions and writes `files/retrieval/results.json` (passages, scores, similarity) | `--es q64 <base64 JSON array of questions>`, `--ez copyPacks true` | `BOAR_PARITY` |
| `bench` | llama-bench style speed test of one model file | `--es model <file.gguf>`, `--ei threads`, `--es cpus big`, `--ei nctx`, `--ei batch`, `--es kv F16\|Q8_0`, `--es fa AUTO\|ON\|OFF`, `--ei pp/tg/reps` | `BOAR_BENCH` |
| `ask` | One question to one model with the same settings | as `bench`, plus `--es prompt "…"`, `--ei npredict` | `BOAR_BENCH` |
| `themes` | Unlocks every Boarverse theme on this phone, for testing them | none | `BOAR_BENCH` |
| `labtest` | A Boar Labs self-test without its screen: `image` (Designer: every size, a cancel check), `printer` (Press: made-up baskets to PDF and Markdown) | `--es lab image\|printer`, `--es args "all=1;seed=7"` | `BOAR_LABTEST` |

Example: `adb shell am start -n team.sopa.aoair/team.sopa.boar.bench.BenchActivity --es run themes`.

## Results

Copied to `files/eval-out` (under `/sdcard/Android/data/<package>/`) when the run ends:

- `<runId>.jsonl`: one row per answer, the source of truth
- `<runId>.run.json`: the run's settings
- `requests/<requestId>.status.json`: the final status reported by the phone

`eval-results/` is gitignored; commit a run on purpose when you want it on record. The
questions and every result field are described in [EVAL_QUERIES.md](EVAL_QUERIES.md).

## Tips

- **Check the phone isn't busy.** Starting the runner brings BOAR to the front. Don't start
  a run during a model download.
- **Keep the screen on and the app open.** Locking the phone moves BOAR to the background.
  Some phones, such as Xiaomi/HyperOS, block changing "Stay awake" over adb. If the screen
  would time out during a long run, send a keypress now and then:
  `while true; do adb shell input keyevent KEYCODE_WAKEUP; sleep 30; done`.
- **Heat matters.** Tokens/sec drops as the phone warms up during a long run. Compare
  models from runs in similar conditions, and note the battery temperature
  (`adb shell dumpsys battery`).
- **A model that fails to load isn't skipped silently.** Every question is recorded as a
  failure with the reason, and the run moves on to the next model.
- **Answers vary slightly between runs** (temperature 0.7). Timings compare well; for
  quality, look at more than one run.
