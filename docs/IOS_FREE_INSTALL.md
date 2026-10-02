# Install BOAR on your own iPhone (free Apple ID)

TL;DR: with a Mac, Xcode 27 and a free Apple ID you can build BOAR and install it on your own iPhone. The signature lasts 7 days, then you rebuild. No paid developer account and no App Store.

```bash
git clone https://github.com/rferrari/boar-app.git && cd boar-app
# 1. give the app a bundle id of your own (see step 2 below), e.g. com.yourname.boar
# 2. find your team id and device id (steps 3 and 4), then:
IOS_TEAM=<your team id> IOS_DEVICE=<your device id> \
IOS_STRIP_ENTITLEMENTS=com.apple.developer.kernel.increased-memory-limit,com.apple.developer.kernel.extended-virtual-addressing,com.apple.developer.devicecheck.appattest-environment \
scripts/ios-build-on-host.sh device-run
```

## What you need

| | |
|---|---|
| Mac | Apple silicon recommended, with plenty of free disk for Xcode, pods and the build |
| Xcode | 27 (Expo SDK 57 doesn't build with Xcode 26.1, see [IOS.md](IOS.md#toolchain-requirement)) |
| Tools | Node 24 and npm (what CI uses), CocoaPods (`brew install cocoapods`) |
| iPhone | a recent iOS (tested on an iPhone 13 with iOS 26), connected by USB, with Developer Mode on |
| Apple ID | any Apple ID, free |

## Steps

1. **Sign in to Xcode.** Xcode › Settings › Accounts › `+` › Apple ID. Your free "Personal Team" appears there.

2. **Use your own bundle id.** `team.sopa.boar` belongs to BOAR's team, and a free team can't sign for it. In `app.json`, change `expo.ios.bundleIdentifier` to something only you use, e.g. `com.yourname.boar`. Don't commit it.

3. **Find your team id.** Signing creates an "Apple Development" certificate on the Mac the first time. If you have none yet, open any Xcode project once, pick your Personal Team under Signing & Capabilities and build. Then:

   ```bash
   security find-certificate -c "Apple Development" -p | openssl x509 -noout -subject
   # ... OU=ABCDE12345 ...   <- your team id
   ```

4. **Prepare the iPhone.**
   - Settings › Privacy & Security › Developer Mode › on (the phone restarts).
   - Connect it by USB and trust the Mac.
   - Find its id: `xcrun devicectl list devices`.

5. **Build and install** with the command in the TL;DR. The first run does `npm ci`, `expo prebuild` and `pod install`, and takes several minutes.
   - `IOS_STRIP_ENTITLEMENTS` drops the capabilities a free team can't have. On an iPhone 13 a personal team refused both memory entitlements. App Attest is stripped too.
   - Sharing evaluation results isn't available in this build: a source build has no Supabase keys, and the results server only accepts App Attest keys from BOAR's own App ID, so there's no review queue for it on iOS either. Everything else works.

6. **Trust the developer on the iPhone.** The first launch is blocked. Go to Settings › General › VPN & Device Management, tap your Apple ID and tap Trust. Then open BOAR.

7. **In setup, pick Qwen2.5-1.5B.** Without the increased-memory-limit entitlement an app gets about 2 GB on a 4 GB iPhone, so the default Qwen3-4B (about 2.5 GB) doesn't fit. Qwen2.5-1.5B (about 1 GB) does.

## Every 7 days

The free signature expires and the app stops opening. Your models and conversations stay on the phone. Rebuild and reinstall without redoing the dependencies:

```bash
IOS_SKIP_DEPS=1 IOS_TEAM=<team id> IOS_DEVICE=<device id> \
IOS_STRIP_ENTITLEMENTS=com.apple.developer.kernel.increased-memory-limit,com.apple.developer.kernel.extended-virtual-addressing,com.apple.developer.devicecheck.appattest-environment \
scripts/ios-build-on-host.sh device-run
```

## If something fails

| Symptom | Fix |
|---|---|
| `No profiles for '…' were found` or a capability error | Check the bundle id is yours (step 2) and that the entitlement named in the error is in `IOS_STRIP_ENTITLEMENTS` |
| `Developer Mode disabled` | Step 4 |
| "Untrusted Developer" on launch | Step 6 |
| The app closes while loading a model | You picked a model too big for the memory limit: use Qwen2.5-1.5B (step 7) |
| `weak let` / `JavaScriptActor.swift` compile error | Xcode is too old: use Xcode 27 |
| Free team limit reached | A free Apple ID can have at most 3 sideloaded apps per device: remove one |

## UNKNOWN

- Whether a free team can get Increased Memory Limit on other iPhones: it was refused on an iPhone 13, and Apple's capability table doesn't list it for free accounts.
- Whether a free team can use App Attest; it's stripped here, and sharing wouldn't work from a source build anyway.
- How much free disk the first build needs (not measured).
- The oldest iOS that works (only iOS 26 was tested).
