#!/usr/bin/env bash
# Builds the offline APK: BOAR with its default model, the search model and the Vital Articles pack inside, so a phone
# never needs the network, not even on first launch (BundledAssets unpacks and verifies them).
#
# Usage: scripts/build-offline-apk.sh [release|devtest]   (default release; devtest installs as a separate app, for testing
#        a first launch without touching an installed BOAR)
# Inputs, already on this machine (nothing is downloaded here), checked against the catalog's SHA-256:
#   build/offline-bundle/models/qwen3-4b-instruct-2507-q4km.gguf   (Hugging Face, unsloth/Qwen3-4B-Instruct-2507-GGUF)
#   assets/models/embedding.gguf                                   (bge-small-en-v1.5 Q8)
#   build/knowledge-pack/wiki-vital5.sqlite                        (npm run pack:build)
# Output: build/boar-offline[-devtest].apk
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
VARIANT=${1:-release}
# Where the inputs live (BOAR_OFFLINE_INPUTS: another checkout's root, e.g. from a worktree); this checkout by default.
IN=${BOAR_OFFLINE_INPUTS:-$ROOT}
CATALOG=$ROOT/android-native/app/src/main/assets/boar-data/catalog.json
STAGE=$ROOT/build/offline-apk-assets
declare -A SRC=(
  [qwen3-4b-instruct-2507-q4km]=$IN/build/offline-bundle/models/qwen3-4b-instruct-2507-q4km.gguf
  [bge-small-en-v1.5-q8]=$IN/assets/models/embedding.gguf
  [wiki-vital5]=$IN/build/knowledge-pack/wiki-vital5.sqlite
)
rm -rf "$STAGE"
for id in "${!SRC[@]}"; do
  read -r path sha size < <(python3 - "$CATALOG" "$id" <<'PY'
import json, sys
def walk(x):
    if isinstance(x, dict):
        if x.get("id") == sys.argv[2]: print(x["filename"], x["sha256"], x["sizeBytes"]); sys.exit()
        for v in x.values(): walk(v)
    elif isinstance(x, list):
        for v in x: walk(v)
walk(json.load(open(sys.argv[1])))
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
nice ./gradlew -q ":app:$TASK" -PreactNativeArchitectures=arm64-v8a -Pboar.bundleDir="$STAGE"
OUT=$ROOT/build/boar-offline$([ "$VARIANT" = release ] || echo "-$VARIANT").apk
cp "app/build/outputs/apk/$VARIANT/app-$VARIANT.apk" "$OUT"
./gradlew --stop >/dev/null
ls -la "$OUT"
