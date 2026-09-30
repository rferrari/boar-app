# Device lab (macOS, Apple Silicon)

TL;DR: build the Android app locally with no Android Studio, run it on an arm64
emulator **without Google Play Services** (AOSP image, the same situation as
GrapheneOS), and drive it with Maestro end-to-end flows.

Footprint on disk (measured on an M1, 2026-09-26):

| Piece | Size |
|---|---|
| Android SDK (platform-tools, build-tools 36, platform 36, NDK 27.1, emulator, 1 system image) | ~5.6 GB |
| OpenJDK 17 (Homebrew formula) | ~0.3 GB |
| Maestro CLI | ~0.35 GB |
| Gradle cache (`~/.gradle`) after one build | ~4 GB (UNKNOWN: re-measure) |
| AVD data partition (capped) | ≤ 4 GB |

## 1. Toolchain

Versions come from the project, not from taste:
`node_modules/react-native/gradle/libs.versions.toml` pins compileSdk 36,
build-tools 36.0.0 and NDK 27.1.12297006.

```bash
# JDK 17: the Homebrew *formula*, no sudo (the zulu/temurin casks need sudo)
brew install openjdk@17
# cmdline-tools (sdkmanager, avdmanager)
brew install --cask android-commandlinetools

export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=$HOME/Library/Android/sdk
mkdir -p $ANDROID_HOME/cmdline-tools

# avdmanager derives the SDK root from its own path, so it must live *inside*
# the SDK (a symlink is not enough: it resolves to the Homebrew path).
cp -R /opt/homebrew/share/android-commandlinetools/cmdline-tools/latest \
      $ANDROID_HOME/cmdline-tools/latest

yes | sdkmanager --sdk_root=$ANDROID_HOME --licenses
sdkmanager --sdk_root=$ANDROID_HOME \
  "platform-tools" "emulator" "platforms;android-36" "build-tools;36.0.0" \
  "ndk;27.1.12297006" "system-images;android-35;default;arm64-v8a"
```

fish (the default shell on this machine), in `~/.config/fish/config.fish`:

```fish
set -gx JAVA_HOME /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
set -gx ANDROID_HOME $HOME/Library/Android/sdk
fish_add_path $ANDROID_HOME/platform-tools $ANDROID_HOME/emulator $ANDROID_HOME/cmdline-tools/latest/bin $HOME/.maestro/bin
```

## 2. Emulator without Google Play Services

`default` system images are plain AOSP: no GMS, no Play Store. That is the
bounty's "no Google Play Services" condition, and close to GrapheneOS.

```bash
echo no | avdmanager create avd -n boar_api35 \
  -k "system-images;android-35;default;arm64-v8a" -d pixel_7
# cap disk and give it a phone-like amount of RAM
C=~/.android/avd/boar_api35.avd/config.ini
sed -i '' '/^disk.dataPartition.size=/d;/^hw.ramSize=/d;/^hw.keyboard=/d' $C
printf "disk.dataPartition.size=4G\nhw.ramSize=4096\nhw.keyboard=yes\n" >> $C

emulator -avd boar_api35 -no-snapshot-save -no-boot-anim -gpu host &
adb wait-for-device && adb shell getprop sys.boot_completed   # 1 when ready
```

Check that there is no GMS: `adb shell pm list packages | grep -c google.android.gms` prints `0`.

## 3. Build (release, JS bundle embedded, no Metro)

```bash
make -C e2e build VARIANT=downloader     # or offline; → dist/boar-integration-<sha>-<variant>-arm64.apk
make -C e2e build-js SHA=<commit>        # same native base and variant: JS-only rebuild
```

`e2e/scripts/build.sh` = `expo prebuild -p android --clean` → llama.rn C++ with
`ninja -j3` under `nice` → app CMake configure → `ninja -j3` → `assembleRelease`
(arm64-v8a only) → `scripts/audit-offline-apk.sh`. Measured on the 16 GB M1 with other
jobs running (2026-09-26/27):

| Build | Time |
|---|---|
| full, llama.rn C++ cache warm | 8–13 min (first after deleting `android/app/build`: 20–25 min) |
| JS-only (`build-js.sh`, `android/app/build` kept, same variant) | 40–75 s |
| full, cold C++ (after `npm ci`) | ~40 min loaded, ~13 min with -j3 |

Rules that keep the Mac usable:

- **Close the emulator while compiling** (`adb emu kill`, then `pgrep -x qemu-system-aarch64`).
- Gradle's `org.gradle.workers.max=2` (`~/.gradle/gradle.properties`) does not limit
  C++: AGP calls ninja directly and ninja starts one clang per core, and
  `CMAKE_BUILD_PARALLEL_LEVEL` is ignored. Hence the explicit `ninja -j3` before Gradle.
- Don't use `taskpolicy -b` on the compilers: on a busy machine it starves them.
- `./gradlew --stop` at the end.
- Changes to `app.json` or `plugins/` (manifest, icon, splash) need the full build
  (prebuild); changes only under `src/` can use `build-js`. Check with
  `git diff --stat <built> <new> -- package-lock.json app.json plugins modules`.

Keep the C++ cache, drop the rest:

- `node_modules/llama.rn/android/.cxx` (~2 GB) saves ~40 min. `expo prebuild --clean`
  leaves it alone; **`npm ci` deletes it** (fresh timestamps force a full rebuild), so
  run `npm ci` only when `package-lock.json` changed.
- After a session: `rm -rf android/app/build node_modules/llama.rn/android/build`
  (~5.5 GB). Deleting `android/app/build` makes the next `build-js` a full Gradle pass.

## 4. Install and run

```bash
adb install -r dist/boar-integration-<sha>-downloader-arm64.apk   # -r keeps imported models and packs
adb shell am start -n team.sopa.aoair/.MainActivity               # offline build: team.sopa.aoair.offline/.MainActivity
```

The AVD has 4 GB of RAM (3.8 GB visible), the profile of a weak phone: the setup picks
the compact model there, and loading the 4B got the app killed by lmkd (CR-1). For a 4B
measurement raise `hw.ramSize` to 6144 in `~/.android/avd/boar_api35.avd/config.ini`,
cold boot, and put it back afterwards.

## 5. End-to-end tests (Maestro)

See `e2e/README.md`.

## 6. Per-build checks on the Mac mini (r4to rule, 28/09: no video per build)

| When | Script | Pass = |
|---|---|---|
| Every build | `e2e/scripts/mini/run-smoke.sh <apk> <sha>` (~5 min, mostly the setup import) | rc=0: the EN answer shows body `[1]`, SOURCES, Helpful/Copy answer and Go deeper; PT shows the passage + (note + "Arriscar resposta" or a model answer); dark pixels in the chat area < 99% |
| When the chat list or its structure changes | `e2e/scripts/mini/run-a11y-order.sh <apk> <sha>` (~6 min) | `order.txt`: older question before newer in tree order, no tree steps going up the screen |
| Only for a specific animation question, 1 flow, with Boar's OK | `e2e/scripts/mini/run-video.sh` (`FLOWS=02` etc.) | a video for Prism, not a gate |

```bash
# inside the heavy queue on the mini (from the MacBook: ssh -n -f r4toMacMini "bash -lc '...'")
FLOOR_GB=7 ~/boar/bin/heavy Piston bash ~/boar/android/run-smoke.sh $(ls ~/boar/android/apk/boar-integration-<sha>*-offline-arm64.apk | head -1) <sha>
```

## UNKNOWN

- The Gradle cache size after a clean build (re-measure).
- The emulator runs llama.cpp on the host's CPU through arm64 virtualization, so
  its speeds are **not** phone numbers. The emulator checks flows, not performance.
