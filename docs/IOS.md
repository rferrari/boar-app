# BOAR on iOS

TL;DR: the same Expo app builds for iOS. The four local native modules have Swift implementations, llama.rn ships a prebuilt Metal-enabled xcframework, and a simulator build needs Xcode only, no Apple account. Installing on a real iPhone needs an Apple ID (free: 7-day signing, paid: TestFlight/App Store).

Status (2026-09-26): `expo prebuild -p ios` and `pod install` pass and all five local modules autolink. The Xcode build has not completed on this machine (see Toolchain requirement), so the Swift modules are not compile-verified yet and nothing has run in the simulator. `UNKNOWN` until a build with a newer Xcode or EAS.

```bash
# Simulator, Release (JS bundled, no Metro needed)
npm ci
npx expo prebuild -p ios --no-install
(cd ios && pod install)
xcodebuild -workspace ios/BOAR.xcworkspace -scheme BOAR -configuration Release \
  -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath ios/build ARCHS=arm64
xcrun simctl boot "iPhone 17 Pro" 2>/dev/null; open -a Simulator
xcrun simctl install booted ios/build/Build/Products/Release-iphonesimulator/BOAR.app
xcrun simctl launch booted team.sopa.boar

# Build on a remote Mac with the newer Xcode (default host r4toMacMini), fetch the
# .app to /Users/r4to/Script/boar/builds/ios/<sdk>/, then boot a simulator ON THAT MAC,
# seed the required models from its ~/boar/shared-models (APFS clone) and launch.
# Inference runs on the remote, not on the machine running the script.
scripts/ios-remote-build.sh sim-run
# Or only build the simulator .app
scripts/ios-remote-build.sh sim
# On the build Mac itself (what the remote wrapper runs). With IOS_OUT_DIR only the
# .app is kept (builds/ios/<sdk>/) and ios/build is deleted.
IOS_OUT_DIR=/Users/r4to/Script/boar/builds/ios scripts/ios-build-on-host.sh sim-run
# On the mini, npm ci / pod install / xcodebuild wait for the shared heavy-job queue
# (~/boar/bin/heavy, log in ~/boar/heavy.log). Inference in the simulator is interactive,
# so hold the queue yourself for the session, e.g.:
#   ssh r4toMacMini "bash -lc '~/boar/bin/heavy Harbor sleep 1200'" &

# Real iPhone: signed Release built on the mini (see docs/IOS_DEVICE_SMOKE.md)
IOS_TEAM=<team id> IOS_DEVICE=<devicectl id> scripts/ios-remote-build.sh device-local   # install from this Mac
IOS_TEAM=<team id> IOS_DEVICE=<devicectl id> scripts/ios-remote-build.sh device-run     # install from the mini

# Simulator, Debug with Metro
npx expo run:ios

# EAS (cloud) — simulator build needs no Apple credentials
eas build -p ios --profile preview-simulator
eas build -p ios --profile preview          # device, ad hoc: needs Apple Developer account
```

Minimum iOS: 16.4 (Expo SDK default).

### Toolchain requirement

With Xcode 26.1 (Swift 6.2.1) `expo prebuild` and `pod install` succeed, but the Release build fails inside Expo's own `expo-modules-jsi` 57.1.0:

```
JavaScriptActor.swift:98:20: error: 'weak' must be a mutable variable, because it may change at runtime
RuntimeScheduler.h:53:26: error: 'RuntimeScheduler' cannot be annotated with either SWIFT_RETURNS_RETAINED or SWIFT_RETURNS_UNRETAINED ...
```

`weak let` needs a newer Swift than 6.2.1; the package changelog targets Xcode 27. Exact minimum Xcode: `UNKNOWN`. Use that Xcode locally, or EAS Build (its image ships the Xcode the SDK expects). None of this repo's code is involved.

## Native modules

| Module | Android | iOS |
|---|---|---|
| `ram-monitor` | VmRSS, PSS, `ActivityManager` total/avail | `task_vm_info.resident_size` (rss), `phys_footprint` (reported as `totalPssBytes`: it is what jetsam counts), `ProcessInfo.physicalMemory`, `os_proc_available_memory()` (as `getAvailableRamBytes`, JS side in feat/engine-routing) |
| `download-wake-lock` | `PARTIAL_WAKE_LOCK` | `isIdleTimerDisabled` + `beginBackgroundTask`. The transfer itself runs on expo-file-system's background `URLSession` (legacy `createDownloadResumable` defaults to `sessionType: BACKGROUND`) |
| `voice-input` | `SpeechRecognizer` + `EXTRA_PREFER_OFFLINE` | `SFSpeechRecognizer` with `requiresOnDeviceRecognition = true`. `isAvailable()` is false when the locale has no on-device model, so audio is never sent to Apple |
| `bundled-assets` | copy from APK `assets/` | copy from the app bundle's `<subdir>/` folder (no build plugin adds it yet), plus `excludeFromBackup(path)` |
| `file-hash` (Ledger) | streaming SHA-256 | CryptoKit `SHA256` over `FileHandle` 1 MiB reads, security-scoped URL access |

New JS API: `excludeFromBackup()` in `bundled-assets`.

## Dependencies checked

| Dependency | iOS status |
|---|---|
| `llama.rn` 0.13 | Prebuilt `rnllama.xcframework`, Metal + Accelerate. Expo plugin added with `enableEntitlements: false` (entitlements set in app.json instead) and `enableOpenCLAndHexagon: false` (Android-only, left to its owner). `n_gpu_layers` is 0 in `LlamaEngine.ts`; Metal offload is the obvious next lever on iOS |
| `expo-pdf-text-extract` | Has an iOS module (PDFKit). No local module needed |
| `expo-sqlite` | Vendored SQLite compiled with `SQLITE_ENABLE_FTS5=1` by default (unless `expo.sqlite.enableFTS=false` in Podfile.properties) |
| `expo-file-system` | Large files OK. Models live in `Documents/`; exclude them from iCloud backup with `excludeFromBackup()` |

## app.json (ios)

- `bundleIdentifier`: `team.sopa.boar`. The Android package stays `team.sopa.aoair`, the id v1.0.0 shipped with: changing it would stop v1.0.0 from updating.
- `icon`: `assets/icon-ios.png`, the Android icon flattened on the adaptive background color (iOS icons must be opaque). Splash comes from the shared `expo-splash-screen` config.
- Info.plist: mic + speech usage strings (voice input), `UIFileSharingEnabled` + `LSSupportsOpeningDocumentsInPlace` (copy GGUF files into the app via Finder over USB or the Files app, the iOS analog of `adb push`), `ITSAppUsesNonExemptEncryption: false`.
- Entitlements: `com.apple.developer.kernel.increased-memory-limit` and `com.apple.developer.kernel.extended-virtual-addressing` (needed to mmap multi-GB GGUFs).
  - How they are applied: set unconditionally in `app.json` `ios.entitlements`, which Expo's prebuild writes into `ios/BOAR/BOAR.entitlements` for every build. The llama.rn plugin runs with `enableEntitlements: false` on purpose: its own switch adds the same two keys only when `EAS_BUILD_PROFILE` is `production` or `NODE_ENV=production`, so local and preview builds would silently lack them.
  - A signing team that cannot get a capability drops it at build time with `IOS_STRIP_ENTITLEMENTS` (see Apple account requirements); nothing in the repo changes.
- Offline variant: not built at the moment. The variant code is still in `src/config/variant.ts`, but the `app.config.js` that selected it isn't on `main`, so every build today is the downloader.

## Memory budget: 4GB iPhone (iPhone 13)

Only the default pair fits: Qwen2.5-1.5B Q4_K_M (0.92 GiB) + bge-small (35 MiB). The 7B/8B catalog models (4.4–4.8 GiB files) do not fit this phone.

| Item | Estimate | Counts in phys_footprint? |
|---|---|---|
| Qwen2.5-1.5B weights, mmap'd | 940 MB | Clean file pages: no. Any weights llama.cpp repacks into anonymous memory for the CPU kernels: yes (`UNKNOWN` how much for Q4_K_M on A15) |
| KV cache, f16, 28 layers × 2 KV heads × 128 dims | 28 KB/token: 59 MB at n_ctx 2048, 117 MB at 4096 | yes |
| Compute buffers | ~100–300 MB (`UNKNOWN`, grows with n_ctx) | yes |
| App (RN, Hermes, JS heap, SQLite, embeddings model) | ~250–350 MB (`UNKNOWN`) | yes |
| **Total, worst case (weights fully repacked, n_ctx 4096)** | **~1.8 GB** | |

- The per-app limit on a 4GB iPhone without the entitlement is believed to be around 2 GB; with `increased-memory-limit` it is higher. Both `UNKNOWN`: the smoke test measures footprint + available at launch.
- Engine settings (feat/engine-routing, PR #2): `n_ctx` 2048 when device RAM ≤ 4.5 GB (4096 otherwise), source budget capped at `n_ctx − maxTokens − 512`, `n_gpu_layers` 0 on every platform until Metal is measured (Metal buffers count in the footprint).
- Pre-flight (same PR, `src/inference/memoryFit.ts`): reads the GGUF header and refuses a load only when the anonymous, non-pageable memory (KV cache + compute buffers) exceeds `getAvailableRamBytes()`, which is `os_proc_available_memory()` on iOS. mmap'd weights only raise a warning (streaming / thrashing). For Qwen2.5-1.5B at n_ctx 2048 that is ~56 MiB of KV + ~0.3 GB of compute, well under the expected headroom; the real headroom at launch on the iPhone 13 is still `UNKNOWN` (the smoke test records it).

## Apple account requirements

| Goal | Needs |
|---|---|
| Simulator build and run | Xcode only, no account |
| Own iPhone | Free Apple ID signed into Xcode on the build Mac (profile expires every 7 days); step by step in [IOS_FREE_INSTALL.md](IOS_FREE_INSTALL.md). A personal team was refused both memory entitlements (Increased Memory Limit and Extended Virtual Addressing) on an iPhone 13: build with the three-key `IOS_STRIP_ENTITLEMENTS` list in [IOS_FREE_INSTALL.md](IOS_FREE_INSTALL.md) (both memory keys plus App Attest) |
| TestFlight / ad hoc / App Store | Apple Developer Program ($99/yr), App ID with "Increased Memory Limit" and "Extended Virtual Addressing" capabilities enabled, then `eas build -p ios --profile preview` or `production` |

`UNKNOWN`: which Apple team (if any) the project owner has. Nothing in this repo is signed for a device yet.

## Parity: Android features without an iOS equivalent

| Android feature | iOS status |
|---|---|
| APK sideload in minutes (bounty requirement is Android) | No sideload without an Apple ID; reproducing on a device needs Xcode or TestFlight |
| Models baked into the APK (`plugins/withBundledModels.js`) | Native side ready (`bundled-assets` reads the app bundle), no config plugin copies `assets/models/` into the Xcode project |
| Download continues with the screen off (wake lock) | Transfer continues on the background URLSession while suspended, but JS progress pauses until foreground, and the session identifier is random per launch, so a download interrupted by the app being killed restarts from the paused resume data or from zero (`UNKNOWN` which, not tested) |
| Build without `INTERNET` permission, verifiable in the manifest | No per-app network permission on iOS (and the offline variant isn't built at the moment) |
| OpenCL/Hexagon GPU/NPU plugins | Metal instead (in the xcframework), not enabled (`n_gpu_layers: 0`) |
| Release signing plugin (`withReleaseSigning`) | EAS credentials or Xcode signing |
| RAM fit check (`LlamaEngine.estimateFit`) uses device RAM − RSS | Wrong on iOS: the jetsam limit is far below device RAM. Should use `getAvailableRamBytes()` |
| Voice via system recognizer (may go online on Android) | On-device only; locales without an on-device model report unavailable |
| GrapheneOS | Not applicable |
| Deep MoE tier (expert streaming, ADR 0001) | Not on iOS for now: Caldera measured a peak footprint of 5.95 GiB (19-token prompt) to 10.7 GiB (1,310 tokens) on macOS, because prefill holds the union of experts; far above an iPhone's per-app limit. Revisit after the prefill fix |
