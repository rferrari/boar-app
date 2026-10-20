# Building and developing BOAR

BOAR is a native Android app in `android-native/`. The build, its flags, signing, tests and
the on-phone evaluation are in [AGENTS.md](../AGENTS.md). In short:

```bash
git clone --recursive https://github.com/rferrari/boar-app.git
cd boar-app/android-native
./gradlew assembleDebug                 # id team.sopa.aoair.native, installs beside a release BOAR
adb install -r app/build/outputs/apk/debug/app-debug.apk
./gradlew :engine:testDebugUnitTest     # engine unit tests, no device needed
./gradlew --stop
```

Needs JDK 17 and an Android SDK with NDK `27.1.12297006` and CMake `3.22.1`.

The build tools in `scripts/` (knowledge packs, evaluation reports) are Node scripts with
their own `scripts/package.json`: `npm --prefix scripts install`, then run them from the repo
root.

## iOS

The iPhone app (the 1.x Expo app) lives on the [`ios`](https://github.com/rferrari/boar-app/tree/ios)
branch. This branch is Android only.

## Testing result sharing

Evaluation › Share results is built in only with `EVAL_SHARING=true` in `.env` (or the
environment, or `-Pboar.evalSharing=true`), next to the two `EXPO_PUBLIC_SUPABASE_*` values.
It's off by default since Oct 19, 2026: the server's address isn't built in, and runs stay on
the phone as files.

Sharing only works from a release build signed with BOAR's release key: the server checks the
phone's hardware-key attestation, and debug-signed builds are refused. Build a release APK with
the key in `~/.gradle/gradle.properties` (AGENTS.md › Signing):

```bash
cd android-native
./gradlew assembleRelease -Pboar.evalSharing=true -Pboar.versionName=2.0.0
adb install -r app/build/outputs/apk/release/app-release.apk   # application id team.sopa.aoair
```

A debug-signed release build has the same application id but a different key, so Android won't
install one over the other: uninstall first, which deletes the downloaded models (copy them out
and import them from files to avoid downloading them again).

The share flow, limits and moderation: [RESULTS_SCORE.md](RESULTS_SCORE.md#how-a-share-works)
and [supabase/README.md](../supabase/README.md).
