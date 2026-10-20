# Demo script

For the public demo post the bounty asks for: the app running offline, several
questions including ones a 1B model would fail on, the repo link, and a short
explanation of the approach. Use questions from the
[device evaluation](DEVICE_EVALUATION.md), so the result shown on camera can be
checked against a measured run.

## Before filming

1. Install the exact release build you'll submit, 2.0.0-rc1 or later
   ([RELEASING.md](RELEASING.md)).
2. Have the recommended models for the phone downloaded (Settings → Reasoning Models →
   Open the model catalog). On a 12 GB phone: Qwen3 4B and LFM2.5 8B. Under 12 GB:
   Qwen3 1.7B.
3. Settings → How BOAR answers:
   - **Max output tokens: Auto** (or 1024 or more). LFM2.5 reasons before answering
     and needs room for it.
   - **Adaptive routing: off**, so the model you picked is the one answering.
   - **Deep Research: off** for the quick questions. Turn it on for the synthesis
     question if you want to show it; it is slower.
4. Charge the phone and let it cool down; speed drops as it heats up.
5. Do a full rehearsal of every question below on the same build and settings.
   Only film questions that answered correctly in the rehearsal.

## Recording

Screen-record the phone (or film it) in one take, status bar visible.

### 1. Show it's offline (10 s)

Swipe down the quick settings: airplane mode on, Wi-Fi off, mobile data off.
Keep the status bar in view for the whole video. The pill in the chat header says
OFFLINE.

### 2. A reasoning question (60 s)

> A device has a 12GB RAM budget. The OS and app overhead take 2GB, the embedding
> model needs 200MB, and the LLM's KV cache needs 1.5GB. How much is left for the
> LLM's weights, and would a 9GB model fit if loaded fully into RAM?

The answer is about 8.3 GB, so a 9 GB model would not fit. In the 1.x app's benchmark
on the Lab device 12 GB (X6), 2026-09-24, Qwen2.5-1.5B got it wrong and LFM2.5-8B-A1B
got it right ([evidence/](evidence/)). Rehearse it with the models you'll film.

To compare two models on camera, switch the model in the catalog, long-press your
question to put it back in the input, and send it again.

Say it: LFM2.5 8B is a mixture of experts, 8B parameters in total, about 1B active
per word.

### 3. A comparison (60 s)

> Compare the French Revolution and the Industrial Revolution.

Point out the sources under the answer: the articles come from the offline library on
the phone. Tap one to show the passage.

### 4. A synthesis (60 s)

> What do the Agricultural Revolution and the Industrial Revolution have in common
> as turning points in human history, and how did they differ in how quickly they
> changed daily life?

Optionally with Deep Research on: the chat glows and the answer takes longer.

### 5. A quick answer from Boar Labs (optional, 30 s)

> I have 23 L of water and 5 L jerrycans. How many jerrycans do I need?

Boar Calculator answers at once, without the model. Tap "Ask BOAR instead" to show the
model's answer.

### 6. Show the measurements (20 s)

The receipt under an answer: the model, the speed and the time. Then Settings →
Advanced tweaks → Evaluation (for builders), and About → "Benchmark it yourself".

## Post draft

> BOAR: an AI research app for Android that works offline after a one-time download.
>
> Airplane mode on, and it still answers explanations, comparisons and multi-step
> questions from a library on the phone, and shows the sources it used.
>
> It runs the models on the phone with llama.cpp, and it measures itself: models are
> benchmarked on the device, results in the repo.
>
> [video]
>
> Repo: https://github.com/rferrari/boar-app
> Bounty: https://poidh.xyz/mainnet/bounty/31

Add speeds only from a measured run, with the phone and the date.
