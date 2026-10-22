#!/usr/bin/env bash
# Is there anything new from the engines' creators? Compares the commits BOAR builds (submodules and
# scripts/engine-pins.env) with each creator's repo on GitHub. Reads version info only: nothing is downloaded,
# checked out or changed. Updating an engine is a decision: its own branch, re-benchmarked on a phone.
#
# Usage: scripts/engine-updates.sh     (gh signed in: commit counts and releases; without gh: changed yes/no)
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
. "$ROOT/scripts/engine-pins.env"
CPP=android-native/engine/src/main/cpp

sub() { git -C "$ROOT" rev-parse "HEAD:$CPP/$1"; }
ENGINES=(
  "llama.cpp (BOAR's engine)|ggml-org/llama.cpp|$(sub llama.cpp)"
  "zstd (packs)|facebook/zstd|$(sub zstd)"
  "Big Boar (bmoe)|Helldez/BigMoeOnEdge|$BMOE_PIN"
  "colibri|JustVugg/colibri|$COLIBRI_PIN"
  "Image Gen (sd-cli)|leejet/stable-diffusion.cpp|$SD_PIN"
)
HAS_GH=0; command -v gh >/dev/null && gh auth status >/dev/null 2>&1 && HAS_GH=1

printf '%-26s %-30s %-9s %s\n' ENGINE CREATOR PINNED UPSTREAM
for e in "${ENGINES[@]}"; do
  IFS='|' read -r name repo pin <<<"$e"
  if [ "$HAS_GH" = 1 ]; then
    branch=$(gh api "repos/$repo" --jq .default_branch)
    news=$(gh api "repos/$repo/compare/$pin...$branch" --jq '"\(.ahead_by) new commits, last \(.commits[-1].commit.committer.date[:10] // "-")"' 2>/dev/null || echo "compare failed")
    rel=$(gh api "repos/$repo/releases/latest" --jq '.tag_name + " " + .published_at[:10]' 2>/dev/null || echo "no releases")
    printf '%-26s %-30s %-9s %s | release %s\n' "$name" "$repo" "${pin:0:8}" "$news" "$rel"
    [[ "$news" == 0\ * ]] || echo "  https://github.com/$repo/compare/$pin...$branch"
  else
    head=$(git ls-remote "https://github.com/$repo" HEAD | cut -f1)
    printf '%-26s %-30s %-9s %s\n' "$name" "$repo" "${pin:0:8}" "$([ "$head" = "$pin" ] && echo up to date || echo "changed (now ${head:0:8})")"
  fi
done
