// Pure helpers for scripts/setup.mjs (tested in setup-lib.test.mjs).

/** Major Java version from `java -version` output (which goes to stderr), or null. */
export function javaMajor(output) {
  const m = /version "(\d+)(?:\.(\d+))?/.exec(output ?? "");
  if (!m) return null;
  const major = Number(m[1]);
  return major === 1 && m[2] ? Number(m[2]) : major; // "1.8.0" -> 8
}

export function nodeMajor(version) {
  const m = /^v?(\d+)/.exec(version ?? "");
  return m ? Number(m[1]) : null;
}

/**
 * The downloader APK and its optional .sha256 companion from a GitHub release JSON. GitHub lists
 * assets by name, not upload order, so "boar-offline-…" comes before "boar-v…": the offline build
 * is only taken when it's the release's only APK.
 */
export function pickReleaseApk(release) {
  const assets = release?.assets ?? [];
  const apks = assets.filter((a) => a.name.endsWith(".apk"));
  const apk = apks.find((a) => !/offline/i.test(a.name)) ?? apks[0];
  if (!apk) return null;
  const sha = assets.find((a) => a.name === `${apk.name}.sha256`);
  return { name: apk.name, url: apk.browser_download_url, size: apk.size, shaUrl: sha?.browser_download_url ?? null, tag: release.tag_name };
}

/** First 64-hex token of a sha256sum-style file. */
export function parseSha256File(text) {
  const m = /\b([0-9a-f]{64})\b/i.exec(text ?? "");
  return m ? m[1].toLowerCase() : null;
}

/** Android SDK location from the usual environment variables, or null. */
export function androidSdkFromEnv(env) {
  return env.ANDROID_HOME || env.ANDROID_SDK_ROOT || null;
}

export const MENU = [
  { key: "1", id: "install", label: "Install BOAR on my phone (easiest: download the app, no building)" },
  { key: "2", id: "build", label: "Build the app from source (cloud build, or local with the Android SDK)" },
  { key: "3", id: "dev", label: "Developer mode: run a live-reloading build over USB (advanced)" },
  { key: "4", id: "pack", label: "Build a bigger offline knowledge pack (optional, takes hours)" },
  { key: "q", id: "quit", label: "Quit" },
];

export function menuChoice(answer) {
  const a = (answer ?? "").trim().toLowerCase();
  return MENU.find((m) => m.key === a || m.id === a)?.id ?? null;
}
