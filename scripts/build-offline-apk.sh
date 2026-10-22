#!/usr/bin/env bash
# Builds the offline APK: BOAR with Qwen3 1.7B, the search model, the Vital Articles pack and the image lab's model
# inside, so a phone never needs the network, not even on first launch (BundledAssets unpacks and verifies them). The
# APK stays under GitHub's 2 GiB per-file limit, so it can go on a release page.
#
# Usage: scripts/build-offline-apk.sh [release|devtest]   (default release; devtest installs as a separate app, for testing
#        a first launch without touching an installed BOAR)
# Inputs, already on this machine (nothing is downloaded here), checked against the catalog's SHA-256:
#   build/offline-bundle/models/qwen3-1.7b-q4km.gguf               (Hugging Face, unsloth/Qwen3-1.7B-GGUF)
#   assets/models/embedding.gguf                                   (bge-small-en-v1.5 Q8)
#   build/knowledge-pack/wiki-vital5.sqlite                        (npm run pack:build)
#   build/offline-bundle/labs/sdxs-512-tinySDdistilled_Q8_0.gguf   (Hugging Face, concedo/sdxs-512-tinySDdistilled-GGUF)
# Output: build/boar-offline[-devtest].apk
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
VARIANT=${1:-release}
# Where the inputs live (BOAR_OFFLINE_INPUTS: another checkout's root, e.g. from a worktree); this checkout by default.
IN=${BOAR_OFFLINE_INPUTS:-$ROOT}
CATALOG=$ROOT/android-native/app/src/main/assets/boar-data/catalog.json
STAGE=$ROOT/build/offline-apk-assets
declare -A SRC=(
  [qwen3-1.7b-q4km]=$IN/build/offline-bundle/models/qwen3-1.7b-q4km.gguf
  [bge-small-en-v1.5-q8]=$IN/assets/models/embedding.gguf
  [wiki-vital5]=$IN/build/knowledge-pack/wiki-vital5.sqlite
  [lab-sdxs-512-q8]=$IN/build/offline-bundle/labs/sdxs-512-tinySDdistilled_Q8_0.gguf
)
LABS=$ROOT/android-native/app/src/labs/plugins
# GitHub's limit for one release file.
MAX_APK_BYTES=2147483648
rm -rf "$STAGE"
for id in "${!SRC[@]}"; do
  read -r path sha size < <(python3 - "$CATALOG" "$id" "$LABS" <<'PY'
import json, pathlib, re, sys
def walk(x):
    if isinstance(x, dict):
        if x.get("id") == sys.argv[2]: print(x["filename"], x["sha256"], x["sizeBytes"]); sys.exit()
        for v in x.values(): walk(v)
    elif isinstance(x, list):
        for v in x: walk(v)
walk(json.load(open(sys.argv[1])))
# A lab's model is a CatalogAsset in its plugin's code (LabPlugin.bundledAssets), not in catalog.json.
for kt in pathlib.Path(sys.argv[3]).rglob("*.kt"):
    m = re.search(r'id = "' + re.escape(sys.argv[2]) + r'".*?filename = "([^"]+)".*?sizeBytes = ([\d_]+).*?sha256 = "([0-9a-f]{64})"', kt.read_text(), re.S)
    if m: print(m[1], m[3], m[2].replace("_", "")); sys.exit()
sys.exit(f"{sys.argv[2]} not in the catalog")
PY
)
  f=${SRC[$id]}
  [ "$(stat -c %s "$f")" = "$size" ] || { echo "$f: size differs from the catalog ($size)" >&2; exit 1; }
  echo "checking $id"
  [ "$(sha256sum "$f" | cut -c1-64)" = "$sha" ] || { echo "$f: SHA-256 differs from the catalog" >&2; exit 1; }
  mkdir -p "$STAGE/bundled/$(dirname "$path")"
  # A hard link where possible: no 2.5 GB copy on a slow disk.
  ln "$f" "$STAGE/bundled/$path" 2>/dev/null || cp "$f" "$STAGE/bundled/$path"
done
cd "$ROOT/android-native"
TASK=assemble${VARIANT^}
# The release name the app shows: BOAR_VERSION_NAME, else the latest tag without its "v" (v2.0.0-rc1 -> 2.0.0-rc1).
VERSION_NAME=${BOAR_VERSION_NAME:-$(git -C "$ROOT" describe --tags --abbrev=0 | sed 's/^v//')}
echo "version $VERSION_NAME"
nice ./gradlew -q ":app:$TASK" -PreactNativeArchitectures=arm64-v8a -Pboar.bundleDir="$STAGE" -Pboar.experimentalFeatures=true -Pboar.versionName="$VERSION_NAME"
OUT=$ROOT/build/boar-offline$([ "$VARIANT" = release ] || echo "-$VARIANT").apk
cp "app/build/outputs/apk/$VARIANT/app-$VARIANT.apk" "$OUT"
./gradlew --stop >/dev/null
[ "$(stat -c %s "$OUT")" -lt $MAX_APK_BYTES ] || echo "warning: $OUT is over GitHub's 2 GiB per-file limit" >&2
ls -la "$OUT"
