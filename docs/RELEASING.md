# Releasing the Android APK

Release APKs of the native app (`android-native/`) are built locally, on the machine that
holds the release key, and checked before upload. The 2.0.0 candidates are published as
GitHub pre-releases: `v2.0.0-rc1`, then `rc2` or `rc3` at most if needed.

The flow: changes land on `fullnative-dev` through pull requests. A release is a pull request
from `fullnative-dev` into `main`, then the tag on `main` and the GitHub release. `v2.0.0` becomes
the Latest release when it's tagged.

## Once: the key

The key and its passwords live in `~/.gradle/gradle.properties`, never in the repo
([AGENTS.md](../AGENTS.md#signing)):

```properties
BOAR_UPLOAD_STORE_FILE=/path/to/boar-release.jks
BOAR_UPLOAD_KEY_ALIAS=...
BOAR_UPLOAD_STORE_PASSWORD=...
BOAR_UPLOAD_KEY_PASSWORD=...
```

Without them the release build is debug-signed. That is fine for your own phone, but it
can't install over an official BOAR and must never be uploaded.

Keep an offline backup of `boar-release.jks`. BOAR isn't on Google Play, so a lost or
leaked key can't be replaced: a new key means every user uninstalls and loses their
models.

## Each release

1. **Version.** `versionCode` in `android-native/app/build.gradle.kts` must be higher than
   the last published release, or the APK won't install over it (2.0.0-rc1 is
   `versionCode` 4, 2.0.0-rc2 is 5). The version name comes from the build command.

2. **Build.** No `.env` with `EVAL_SHARING=true`: the release has no Share button and never
   contacts the results server.

   ```bash
   cd android-native
   ./gradlew assembleRelease -Pboar.experimentalFeatures=true -Pboar.versionName=2.0.0-rc1
   ./gradlew --stop
   ```

3. **Check the signer.** The certificate SHA-256 must be the release key's,
   `7cf514f9ab8253cbd78eb7d112d66fd05c2ad27e6fc987bf8f3276a7eedf1f01`:

   ```bash
   apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
   ```

   `scripts/check-release-apk.sh <apk>` checks the same signer, the application id
   (`team.sopa.aoair`) and the `versionCode`, and exits 1 on any mismatch.

4. **Name it and hash it.**

   ```bash
   cp app/build/outputs/apk/release/app-release.apk boar-v2.0.0-rc1-arm64.apk
   sha256sum boar-v2.0.0-rc1-arm64.apk > boar-v2.0.0-rc1-arm64.apk.sha256
   ```

5. **Install it on a phone over the previous release** (no uninstall), and check that
   setup, a chat answer and About (version, commit, build date) work.

   **Offline APKs.** Rebase `feat/offline-bundle` on the release commit, then, from the repository root of that branch (not `android-native/`), build each one
   with the main APK's version name (without it the script takes the last tag's), then name
   and hash them as in step 4:

   ```bash
   BOAR_VERSION_NAME=2.0.0-rc1 scripts/build-offline-apk.sh release
   BOAR_VERSION_NAME=2.0.0-rc1 BOAR_OFFLINE_MODEL=qwen3-4b-instruct-2507-q4km scripts/build-offline-apk.sh release
   # -> build/boar-offline.apk, build/boar-offline-qwen3-4b-instruct-2507-q4km.apk
   cp build/boar-offline.apk boar-v2.0.0-rc1-offline-arm64.apk
   cp build/boar-offline-qwen3-4b-instruct-2507-q4km.apk boar-v2.0.0-rc1-offline-qwen3-4b-arm64.apk
   sha256sum boar-v2.0.0-rc1-offline-arm64.apk > boar-v2.0.0-rc1-offline-arm64.apk.sha256
   sha256sum boar-v2.0.0-rc1-offline-qwen3-4b-arm64.apk > boar-v2.0.0-rc1-offline-qwen3-4b-arm64.apk.sha256
   ```

   Check each with `scripts/check-release-apk.sh` (its default mode: the offline APK is
   `team.sopa.aoair` too), and test a first launch in airplane mode. The 1.7B one goes on
   GitHub and Hugging Face (`BOAR-App/boar-offline`), the 4B one on Hugging Face only (over
   GitHub's 2 GiB per file).

   **Reviews, last.** After the team's reviews and a free CodeRabbit run with no findings
   (`cr review --agent --base main`), run a CodeRabbit Deep Scan on the release pull request,
   and fix what holds up before tagging.

6. **Tag and publish as a pre-release.**

   ```bash
   git tag v2.0.0-rc1 <commit>
   git push origin v2.0.0-rc1
   gh release create v2.0.0-rc1 --prerelease --title "BOAR 2.0.0-rc1" \
     boar-v2.0.0-rc1-arm64.apk boar-v2.0.0-rc1-arm64.apk.sha256 \
     boar-v2.0.0-rc1-offline-arm64.apk boar-v2.0.0-rc1-offline-arm64.apk.sha256
   hf upload BOAR-App/boar-offline boar-v2.0.0-rc1-offline-arm64.apk
   hf upload BOAR-App/boar-offline boar-v2.0.0-rc1-offline-arm64.apk.sha256
   hf upload BOAR-App/boar-offline boar-v2.0.0-rc1-offline-qwen3-4b-arm64.apk
   hf upload BOAR-App/boar-offline boar-v2.0.0-rc1-offline-qwen3-4b-arm64.apk.sha256
   ```

   The Hugging Face upload needs a login with write access to `BOAR-App` (`hf auth login`);
   never put the token in a file or a message.

   Write the notes on the release. For `v2.0.0`, leave out `--prerelease` so it becomes
   Latest.

## The GitHub Actions workflow

`.github/workflows/release-apk.yml` can also build, check and draft a release from
`main`, with the key stored as secrets of a GitHub environment named `release` that only
`main` can use. The 2.0.0 candidates are not built this way (its tag check accepts only
`vX.Y.Z`, not `-rc` tags). To set it up, put the four
values above in that environment as `BOAR_UPLOAD_KEYSTORE_B64` (the `.jks`, base64),
`BOAR_UPLOAD_KEY_ALIAS`, `BOAR_UPLOAD_STORE_PASSWORD` and `BOAR_UPLOAD_KEY_PASSWORD`,
then run Actions → Release APK with a tag. The workflow has no `.env`, so its build has
`EVAL_SHARING` off.
