# AGENTS.md: building and installing BOAR from source

For an AI coding agent, or anyone scripting a build. It covers exact commands and the
constraints that make the naive path fail. What the app is: `README.md`.

## The app is native Kotlin in `android-native/`

BOAR 2.x is a native Android app:

- `android-native/app`: the app (Kotlin, Jetpack Compose). Package `team.sopa.boar`,
  application id `team.sopa.aoair` (kept from 1.x so a release installs over it).
- `android-native/engine`: llama.cpp, SQLite and zstd through JNI (`libboar_engine.so`),
  plus the retrieval, routing, model and evaluation code. Most unit tests live here.
- `android-native/app/src/labs/plugins/<id>/`: Boar Labs plugins, one folder each
  (`docs/PLUGINS.md`).

The rest of the repo root:

- `assets/`: the Portuguese lexicon and the bundled corpus, copied into the app at build
  time (and read by the engine tests).
- `.env`: build flags (below). Not committed; see `.env.example`.
- `scripts/`: Node and shell build tools (knowledge packs, evaluation reports, release
  checks, the labs' engines). They have their own `scripts/package.json`; run them from the
  repo root (`node scripts/<name>.mjs`) after `npm --prefix scripts install`. The app build
  doesn't need them.
- `supabase/`: the results server. `docs/`: the guides.

The old Expo / React Native app (1.x) is not in this branch. Its last code is on the `expo`
branch and the tag `expo-final`.

## Prerequisites

- JDK 17.
- Android SDK with platform 36, NDK `27.1.12297006` and CMake `3.22.1` (versions from
  `android-native/app/build.gradle.kts` and `engine/build.gradle.kts`). `ANDROID_HOME`
  set, or `sdk.dir` in `android-native/local.properties`.
- The git submodules: llama.cpp and zstd under `android-native/engine/src/main/cpp`.
  Without them the CMake step fails.
- For installing: a 64-bit ARM phone (only `arm64-v8a` is built) with USB debugging on
  and `adb devices` showing it as `device`.

## Build a release APK

```bash
git clone --recursive https://github.com/rferrari/boar-app.git
cd boar-app
git submodule update --init          # only if you cloned without --recursive
cd android-native
./gradlew assembleRelease -Pboar.versionName=2.0.0
# -> app/build/outputs/apk/release/app-release.apk
adb install -r app/build/outputs/apk/release/app-release.apk
./gradlew --stop                     # don't leave the Gradle daemon running
```

Without `-Pboar.versionName` the version is `2.0.0-dev`. Use it for release candidates
too, e.g. `-Pboar.versionName=2.0.0-rc1`. About shows the version, the git commit and the
build date.

Other build types:

- `./gradlew assembleDebug`: id `team.sopa.aoair.native`. It installs beside a release
  BOAR, never over it.
- `devtest`: id `team.sopa.aoair.devtest`, a throwaway install for instrumented tests that
  wipe data (`./gradlew :app:assembleDevtestAndroidTest`).

## Signing

The release build is signed with the key named in `~/.gradle/gradle.properties`, never in
the repo:

```properties
BOAR_UPLOAD_STORE_FILE=/path/to/boar-release.jks
BOAR_UPLOAD_KEY_ALIAS=...
BOAR_UPLOAD_STORE_PASSWORD=...
BOAR_UPLOAD_KEY_PASSWORD=...
```

Without them the release is debug-signed. That is fine for your own phone, but Android
won't install it over a BOAR signed with another key. You have to uninstall first, and
uninstalling deletes the downloaded models. Published releases are built and signed by
GitHub Actions (`docs/RELEASING.md`).

## Build flags

Each flag can be set in `.env` at the repo root (not committed; see `.env.example`), in the
environment, or as a Gradle property with `-P`.

| `.env` / environment | Gradle property | Default | Effect |
|---|---|---|---|
| `EXPERIMENTAL_FEATURES` | `boar.experimentalFeatures` | false | Boar Labs and Boar Avatars exist (off until turned on in Settings › Advanced tweaks) |
| `EVAL_SHARING` | `boar.evalSharing` | false | Builds in the results server (`EXPO_PUBLIC_SUPABASE_URL`, `EXPO_PUBLIC_SUPABASE_PUBLISHABLE_KEY` from `.env`). Off: no Share button, runs stay on the phone as files |
| `SHOW_EVAL_IN_MENU` | `boar.showEvalInMenu` | false | Evaluation in the side menu (always reachable from Advanced tweaks) |
| `ALLOW_DEBUG_LOG` | `boar.allowDebugLog` | false | Settings › Advanced tweaks › Debug log exists |
| `ALLOW_DRAWER_STATS` | `boar.allowDrawerStats` | true | The menu's memory and storage bars can be turned on |
| `ALLOW_UNCENSORED_MODELS` | `boar.allowUncensoredModels` | false | Uncensored models show in Discover GGUF models |
| `BOAR_TRUFFLE_LAUNCHER_ICONS` | `boar.truffleLauncherIcons` | false | Truffle Vault boars as launcher icons |
| | `boar.appIdSuffix` | none | `.labs` gives `team.sopa.aoair.labs`, labelled "BOAR Labs", installed beside BOAR with its own data |
| | `boar.labs.executables` | true | In experimental builds, false leaves the labs' engines out of the APK |
| | `boar.labs.hide` | empty | Lab ids to leave out of the grid, comma-separated |

A Labs build that installs beside the normal BOAR:

```bash
./gradlew assembleRelease -Pboar.experimentalFeatures=true -Pboar.appIdSuffix=.labs -Pboar.versionName=2.0.0
```

## Boar Labs engines

Big Boar (`libbmoe_cli.so`), Boar Wisp (`libcolibri_olmoe.so`) and Boar Designer
(`libsdcli.so`, `libsdcli_i8mm.so`) run their own executables. They are prebuilt into
`android-native/app/src/labs/plugins/<id>/jniLibs/arm64-v8a/`, which is gitignored, so a
fresh clone doesn't have them. They are packed into the APK only in experimental builds.

To build them, clone the upstream repos yourself at the commits pinned in
`scripts/engine-pins.env`, then:

```bash
scripts/build-lab-engines.sh ../colibri ../BigMoeOnEdge   # bmoe and colibri; needs ANDROID_HOME with NDK and CMake
scripts/build-sd-cli.sh ../stable-diffusion.cpp           # Designer's sd-cli
```

The scripts stop when a checkout isn't at its pinned commit (`ALLOW_UNPINNED=1` to build
anyway). Big Boar is tested with BOAR's patch series on BigMoeOnEdge; the script warns
when it's missing.

Without these files the experimental build still succeeds. The labs that need them still
show in the grid, and their page says the engine is missing.

## Tests

```bash
cd android-native
./gradlew :engine:testDebugUnitTest   # JVM unit tests, about 380; no device needed
./gradlew :app:compileDebugKotlin     # the app compiles
./gradlew --stop
```

CI runs the same two on pull requests (`.github/workflows/ci.yml`). They don't prove the
APK installs or runs. Don't claim a build or install works without a real device.

## After install: first-launch setup

First launch shows a one-time setup that downloads:

- the answer model: Qwen3 4B (about 2.5 GB) on phones with 10 GB of RAM or more, Qwen2.5
  1.5B (about 1 GB) below that (`SetupModel.recommended` in the engine). The user can pick
  the other one.
- BGE Small, the search (embedding) model.
- knowledge packs, optional.

The app is gated behind this download. After it, BOAR works without a network connection.
Models are stored in the app's `files/` folder.

## Before pushing: CodeRabbit review

Run a local CodeRabbit review before every `git push`, in addition to the tests:

```bash
cr doctor                          # once: CLI installed and signed in
cr review --agent --base main      # this branch against main, structured findings
cr review --agent --uncommitted    # or only what isn't committed yet
```

`cr review --base main` gives the same review with readable output. A review can take several
minutes, so run it in the background. Check each finding against the current code before
acting on it: fix the ones that hold up, and note in one line why you skipped the others.
Re-run once after fixing, not in a loop.

If `cr doctor` says you are signed out, ask the person you're working with to run
`cr auth login` (a browser sign-in) before you push. If they choose to push without the
review, say so in the PR.

## Benchmarking models on a connected phone

The evaluation runner is `BenchActivity`. It is exported with
`android.permission.DUMP`, which adb's shell holds and other apps don't, so only adb (USB
or wireless debugging) can start it. It works on release builds.

```bash
adb devices                                        # exactly one device, state "device"
req=$(printf '%s' '{"requestId":"req-1","models":["qwen2.5"],"queries":["greeting-1"]}' | base64 -w0)
adb shell am start -n team.sopa.aoair/team.sopa.boar.bench.BenchActivity \
  --es run eval --es request64 "$req"
adb logcat | grep -i eval                          # progress
adb pull /sdcard/Android/data/team.sopa.aoair/files/eval-out eval-results/
node scripts/eval-summary.mjs --report eval-results/eval-out/<runId>.jsonl
```

- `models` matches part of an installed model's id or label. Leave out `queries` for
  the full question set (`docs/EVAL_QUERIES.md`). `--ei waitmin 10` is how long the runner
  waits for the request.
- For a debug build use `team.sopa.aoair.native`, for a Labs build `team.sopa.aoair.labs`,
  in both the component and the pull path.
- Results: `<runId>.jsonl` (one row per answer, the source of truth), `<runId>.run.json`
  and `requests/<requestId>.status.json`, copied to `files/eval-out` when the run ends.
- `scripts/eval-summary.mjs` needs only Node, no `npm install`.

Before and during a run:

- **Check the phone isn't busy.** Starting the runner brings BOAR to the front. Don't run
  it, reinstall, force-stop or uninstall while a model download is in progress. A stopped
  download has to be restarted from the app, and an uninstall deletes everything already
  downloaded. The phone's owner may be using it: ask first.
- **Keep the screen on.** Locking the phone backgrounds the app. If the phone won't accept
  `settings put global stay_on_while_plugged_in`, send
  `adb shell input keyevent KEYCODE_WAKEUP` every 30 seconds for the length of the run,
  then stop.
- **Use `"queries":["greeting-1"]` for a quick probe** of a new model (does it load, what
  prompt format does it get) before a full run.
- **Report only what the phone measured.** The JSONL is the source of truth. A model that
  fails to load is recorded as failures, not skipped.

To check a Hugging Face GGUF before anyone downloads it, read its header (the first few MB)
for `general.architecture` and `tokenizer.chat_template`. The architecture must be
supported by the llama.cpp submodule at its pinned commit.

## Building a knowledge pack

Full guide: [docs/KNOWLEDGE_PACKS.md](docs/KNOWLEDGE_PACKS.md). The pack builder is Node
tooling in `scripts/`, separate from the app build. Run it from the repo root.

```bash
npm --prefix scripts install                                      # once
npm --prefix scripts run pack:build -- --limit 200 --id test-pack # quick check that the pipeline works
npm --prefix scripts run pack:build                               # the full Vital Articles level 5 pack
npm --prefix scripts run pack:push -- build/knowledge-pack/<id>.sqlite  # copy onto a USB-connected debuggable build
npm --prefix scripts test                                         # the tools' unit tests
```

- A full level 5 build takes hours (downloading about 50k introductions, then embedding).
  Run it in the background and let it resume if interrupted: every step is cached under
  `build/knowledge-pack/<id>/`.
- Packs must be embedded with the app's search model (`assets/models/embedding.gguf`,
  checked by SHA-256). The app ignores packs built with another one.
- Don't commit `.sqlite` packs to git. They go to a release asset (see the guide).

## Common pitfalls

- **CMake can't find `llama.cpp` or `zstd`**: the submodules are missing. Run
  `git submodule update --init`.
- **NDK or CMake not found**: install exactly NDK `27.1.12297006` and CMake `3.22.1` with
  the SDK manager.
- **`INSTALL_FAILED_UPDATE_INCOMPATIBLE`**: the APK is signed with a different key than
  the installed BOAR. Uninstalling deletes the models; ask first, or build with the right
  key, or install as a separate app (`assembleDebug`, or `-Pboar.appIdSuffix=.labs`).
- **A Labs page says the engine is missing**: the `jniLibs` weren't built, or the build
  wasn't experimental. See "Boar Labs engines".
- **Don't run destructive git commands to fix a build** (`git clean -fdx`, force-pushes,
  deleting submodules) without checking `git status` first and understanding why the
  build failed.
