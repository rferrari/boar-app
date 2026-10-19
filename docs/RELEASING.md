# Releasing the Android APK

Release APKs are built and signed by GitHub Actions (`.github/workflows/release-apk.yml`), so the
signing key doesn't have to be on anyone's laptop. The workflow builds the APK, checks it with
`scripts/check-release-apk.sh` (package, versionCode, signed with v1.0.0's key) and, given a tag,
creates a draft release with it.

## Once: put the key in the `release` environment

The key and its passwords are secrets of a GitHub environment named `release` that only `main` can
use. Anyone who can push a branch could otherwise edit the workflow there to print them.

1. Create the environment and limit it to `main` (Settings → Environments → New environment →
   `release` → Deployment branches and tags → Selected → add `main`), or:

   ```bash
   gh api -X PUT repos/rferrari/boar-app/environments/release \
     -F 'deployment_branch_policy[protected_branches]=false' -F 'deployment_branch_policy[custom_branch_policies]=true'
   gh api -X POST repos/rferrari/boar-app/environments/release/deployment-branch-policies -f name=main -f type=branch
   ```

   Optional: add yourself under "Required reviewers", so every release build waits for your OK.

2. Add the four secrets from the machine that has the key. Each value goes straight from the file to
   GitHub, never to the screen:

   ```bash
   base64 -w0 ~/.android/boar-release.jks | gh secret set BOAR_UPLOAD_KEYSTORE_B64 --env release
   for k in KEY_ALIAS STORE_PASSWORD KEY_PASSWORD; do
     grep "^BOAR_UPLOAD_$k=" ~/.gradle/gradle.properties | cut -d= -f2- | tr -d '\n' | gh secret set "BOAR_UPLOAD_$k" --env release
   done
   ```

3. The repository variables `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY` (Settings → Secrets and
   variables → Actions → Variables) turn on "Share results" in the build. They're public
   by design; the same values as `.env`. The native Android app (`android-native/`) also needs
   `EVAL_SHARING=true`: without it, the default since Oct 19, 2026, the two values aren't built
   in at all, there's no Share button, and evaluation runs stay on the phone as files.

Keep an offline backup of `boar-release.jks` anyway. BOAR isn't on Google Play, so a lost or
leaked key can't be replaced: a new key means every user uninstalls and loses their models.

## Each release

1. Bump `expo.version` and `expo.android.versionCode` in `app.json` and merge to `main`.
2. Actions → Release APK → Run workflow, on `main`, with the tag (`v1.1.0`). About 40 minutes.
   Without a tag it only builds; the APK is the run's artifact.
3. On the draft release: write the notes, install the APK over the previous release on a phone
   (no uninstall), then publish.
