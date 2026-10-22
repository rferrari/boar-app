<p align="center">
  <img src="./assets/boar.png" width="112" alt="BOAR mascot" />
</p>

<h1 align="center">BOAR</h1>

<p align="center">
  <b>AI that works when the internet doesn't.</b><br />
  An open-source research assistant that runs on your phone and shows where every answer came from.
</p>

<p align="center">
  <a href="https://github.com/rferrari/boar-app/releases"><img alt="Latest release" src="https://img.shields.io/github/v/release/rferrari/boar-app?include_prereleases&color=FF7A3D" /></a>
  <img alt="Android" src="https://img.shields.io/badge/platform-Android-FFB547" />
  <img alt="Works offline" src="https://img.shields.io/badge/works-offline-17110D" />
  <a href="./LICENSE"><img alt="License" src="https://img.shields.io/github/license/rferrari/boar-app" /></a>
</p>

## What it is

BOAR downloads a small language model and the knowledge you pick once, then answers
your questions in airplane mode. There is no account, and the app needs no server and no
Google Play Services. Answers cite local sources: built-in Wikipedia articles, knowledge packs and
your own documents. Tap a citation to read the exact passage.

It is a native Android app written in Kotlin with Jetpack Compose. Inference is
[llama.cpp](https://github.com/ggml-org/llama.cpp), called through JNI. What we
believe: [MANIFESTO.md](MANIFESTO.md).

## Install

1. Download the APK from the [releases page](https://github.com/rferrari/boar-app/releases)
   (any 64-bit ARM phone, Android 8 or newer). Version 2.0.0-rc1 is a pre-release, a release
   candidate for 2.0.0.
2. Check it against the `.sha256` file next to it, then open it on the phone or run
   `adb install <file>.apk`.
3. On first launch, setup downloads three things: the answer model, the search model
   (BGE Small) and the Wikipedia Vital Articles pack (about 164 MB, from the
   `knowledge-pack-v1` release). The answer model is Qwen3 4B (about 2.5 GB) on 12 GB phones
   (Android reports 10 GB or more) and Qwen3 1.7B (about 1.1 GB) below that. If the phone is
   short on space, setup offers a smaller package without the Vital Articles pack. While it
   downloads, you can take a short BOAR tour.

After setup, chat, search and sources work without a connection. The network permission
stays for things you start yourself: more models, packs, or searching Hugging Face for
other GGUF models.

If you have BOAR 1.x (the Expo app) installed, an official release installs over it and
keeps your models.

BOAR has not been tested on GrapheneOS with this build. The APK has no Google Play
Services dependencies.

## Build from source

The app lives in [`android-native/`](android-native). You need the Android SDK with
NDK 27.1.12297006 and CMake 3.22.1, and JDK 17.

```bash
git clone --recursive https://github.com/rferrari/boar-app.git
cd boar-app/android-native
./gradlew assembleRelease -Pboar.experimentalFeatures=true -Pboar.versionName=2.0.0-rc1
# -> app/build/outputs/apk/release/app-release.apk
```

Without the release key the APK is debug-signed. That is fine for your own phone.

Exact commands, build flags, signing and tests: [AGENTS.md](AGENTS.md).

## Boar Labs

Boar Labs is a set of experiments that exist only in builds made with
`EXPERIMENTAL_FEATURES=true`, as the 2.0.0 releases are. Each lab is one plugin folder under
`android-native/app/src/labs/plugins/`. Boar Labs starts on (Settings → Experimental). The
quick-answer labs (Calculator, Facts, Cohazard, Distance, Polyglot, all beta) and Boar Vitals
start on. The alpha labs start off: Guard, Press, Designer, Big Boar, Nomad and Lunar. Boar
Wisp is in review. Some run their own engine: Big Boar (Qwen3.6 35B, 12.3 GB, read from
storage), Boar Wisp (colibri) and Boar Designer (image generation with stable-diffusion.cpp).
A build without the flag has none of this. How a lab is made: [docs/PLUGINS.md](docs/PLUGINS.md).

## Docs

| | |
|---|---|
| [docs/USING.md](docs/USING.md) | Using BOAR: answer styles, Deep Research, sources, Labs, PIN |
| [AGENTS.md](AGENTS.md) | Build, install, test and evaluate from the command line |
| [docs/PLUGINS.md](docs/PLUGINS.md) | Boar Labs plugins |
| [docs/MODELS.md](docs/MODELS.md) | Models, datasets and indexes |
| [docs/KNOWLEDGE_PACKS.md](docs/KNOWLEDGE_PACKS.md) | How knowledge packs are built and searched |
| [docs/EVAL_QUERIES.md](docs/EVAL_QUERIES.md) | The evaluation questions |
| [docs/COMPLIANCE.md](docs/COMPLIANCE.md) | Bounty requirements and benchmark files |
| [PRIVACY.md](PRIVACY.md) · [TERMS.md](TERMS.md) | Privacy policy and terms |

Built for the [poidh bounty #31](https://poidh.xyz/mainnet/bounty/31), "Best Offline AI
Research App".

The earlier Expo / React Native app (BOAR 1.x) is kept as history on the `expo` branch
and the git tag `expo-final`.

## License

MIT, see [LICENSE](LICENSE). Third-party code and models keep their own licenses, listed in
[android-native/NOTICE](android-native/NOTICE).
