<p align="center">
  <img src="./docs/readme/banner.webp" width="100%" alt="BOAR, a boar in a ranger hat reading his phone by a mountain lake at night" />
</p>

<p align="center">
  <img src="./docs/readme/app-icon.webp" width="96" alt="BOAR's app icon" />
  &nbsp;&nbsp;
  <img src="./assets/boar.png" width="96" alt="The original BOAR mascot" />
  <br />
  <sub>The app icon, and the original mascot (kept for reference while the new avatar replaces it).</sub>
</p>

<h1 align="center">BOAR</h1>

<p align="center">
  <b>AI that works when the internet doesn't.</b><br />
  An open-source research assistant that runs on your phone and shows where every answer came from.
</p>

<p align="center">
  <a href="https://github.com/rferrari/boar-app/releases"><img alt="Latest release" src="https://img.shields.io/github/v/release/rferrari/boar-app?include_prereleases&color=FF7A3D" /></a>
  <img alt="Android" src="https://img.shields.io/badge/platform-Android-FFB547" />
  <a href="https://github.com/sopa-agency/boar-ios-app"><img alt="iOS and macOS: boar-ios-app" src="https://img.shields.io/badge/iOS%20%26%20macOS-boar--ios--app-FFB547" /></a>
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
   (any 64-bit ARM phone, Android 8 or newer). The current release is 2.0.0.
2. Check it against `SHA256SUMS`, next to it on the release (`sha256sum -c SHA256SUMS`), then
   open it on the phone or run `adb install boar-2.0.0.apk`.
3. On first launch, setup downloads three things: the answer model, the search model
   (BGE Small) and the Wikipedia Vital Articles pack (about 164 MB, from the
   `knowledge-pack-v1` release). The answer model is Qwen3 4B (about 2.5 GB) on 12 GB phones
   (Android reports 10 GB or more) and Qwen3 1.7B (about 1.1 GB) below that. If the phone is
   short on space, setup offers a smaller package without the Vital Articles pack. While it
   downloads, setup walks you through BOAR's main choices.

No internet at all, not even once? 2.0.0 has no offline APK. The last ones are 2.0.0-rc2's, on
[Hugging Face](https://huggingface.co/BOAR-App/boar-offline): an **offline APK** with the
model, the search model, the Vital Articles pack and Designer's drawing model inside, which
unpacks them on first launch and never needs the network (Qwen3 1.7B, about 2 GB, or Qwen3 4B,
about 3.4 GB, for 12 GB phones). You can also build your own from the current code, with
another model if you like ([USING.md](docs/USING.md#build-your-own)).

After setup, chat, search and sources work without a connection. The network permission
stays for things you start yourself: more models, packs, or searching Hugging Face for
other GGUF models.

If you have BOAR 1.x (the Expo app) installed, an official release installs over it and
keeps your models.

The APK has no Google Play Services dependencies. 2.0.0-rc1 was tested on a Pixel 8a with
GrapheneOS.

## Which branch?

| You want to… | Branch | What's there |
|---|---|---|
| Build or change the Android app | **`main`** | The native app (Kotlin, llama.cpp). 2.0.0 is built from it. |
| Send a pull request | a branch off `main` | `feat/…`, `fix/…`, `docs/…`, then a pull request into `main` |
| Write a Boar Lab or plugin | a branch off `main` | One plugin folder, then a pull request into `main`: [docs/PLUGINS.md](docs/PLUGINS.md) |
| Use BOAR on iPhone or Mac | [sopa-agency/boar-ios-app](https://github.com/sopa-agency/boar-ios-app) | Its own repository: SwiftUI and llama.cpp, kept in step with the Android work |
| Ship a phone that never goes online | `feat/offline-bundle` | The offline APK's packaging (`scripts/build-offline-apk.sh`), at 2.0.0-rc2, not updated since |
| Find BOAR 1.x | `expo`, `ios` | The Expo / React Native app (tag `expo-final`), including the routing and facts work of PR #85. Parked. |
| Try other inference engines | `experimental-engines`, `experimental-laya` | Parked experiments, with their measurements |

`main` holds the current code and the releases. Branch from `main`, and open your pull request
against it.

## Build from source

The app lives in [`android-native/`](android-native). You need the Android SDK with
NDK 27.1.12297006 and CMake 3.22.1, and JDK 17.

```bash
git clone --recursive https://github.com/rferrari/boar-app.git
cd boar-app/android-native
./gradlew assembleRelease -Pboar.experimentalFeatures=true
# -> app/build/outputs/apk/release/boar-2.0.0.apk
```

Without the release key the APK is debug-signed. That is fine for your own phone.

Exact commands, build flags, signing and tests: [AGENTS.md](AGENTS.md).

## Boar Labs

Boar Labs, the plugins, exists only in builds made with `EXPERIMENTAL_FEATURES=true`, as 2.0.0
is. Each plugin is one folder under `android-native/app/src/labs/plugins/`; a release build
carries the ones listed in `android-native/app/src/labs/released.txt`, each with its tests.

- Start on: Vitals, Designer, Press, Calculator, Distance, Facts, Cohazard and Cards.
- Start off: Nomad, Lunar and Guard.
- Polyglot is deprecated: its code stays, but it is off and not in the list.
- Big Boar and Boar Wisp, which run their own engines, are left out of a release build. Designer
  draws on the phone with stable-diffusion.cpp.

The Plugins list, with a switch for each plugin, is in the menu in every profile. Turning off
Cohazard, the carbon monoxide safety card, asks first. Boar Labs' own switch is in Settings →
Experimental (Pro). A build without the flag has none of this. How a plugin is made:
[docs/PLUGINS.md](docs/PLUGINS.md).

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

## License

MIT, see [LICENSE](LICENSE). Third-party code and models keep their own licenses, listed in
[android-native/NOTICE](android-native/NOTICE).
