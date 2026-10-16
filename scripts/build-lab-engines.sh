#!/usr/bin/env bash
# Builds the Boar Labs engine plugins' executables for Android arm64 into their plugin folders
# (android-native/app/src/labs/plugins/<id>/jniLibs/arm64-v8a, gitignored). Each engine runs as its own process:
#
#   colibri       (https://github.com/JustVugg/colibri, Apache-2.0)      -> plugins/colibri: libcolibri_olmoe.so
#                 pure-C MoE engine that streams experts from storage; OLMoE only.
#   BigMoeOnEdge  (https://github.com/Helldez/BigMoeOnEdge, Apache-2.0)  -> plugins/bmoe: libbmoe_cli.so
#                 streams the experts of a GGUF MoE bigger than RAM, on top of llama.cpp.
#
# bmoe is linked statically (its own llama.cpp and libc++ inside one executable): BOAR already ships
# libggml.so and libllama.so for its own engine, and a second copy with the same names can't sit next to them.
#
# Usage: scripts/build-lab-engines.sh [colibri checkout] [BigMoeOnEdge checkout]
# Defaults: ../colibri and ../BigMoeOnEdge next to this repo (git clone them first; nothing is fetched here).
# ANDROID_HOME must point at an SDK with an NDK and CMake. JOBS (default 4) and nice keep the desktop usable.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
COLIBRI=$(cd "${1:-$ROOT/../colibri}" && pwd)
BMOE=$(cd "${2:-$ROOT/../BigMoeOnEdge}" && pwd)
PLUGINS=$ROOT/android-native/app/src/labs/plugins
# Upstream's portable choice: runs on any ARMv8.2 phone (i8mm would crash older cores).
ARM_ARCH=${ARM_ARCH:-armv8.2-a+dotprod+fp16}
API=29
JOBS=${JOBS:-4}

NDK=$(ls -d "$ANDROID_HOME"/ndk/* | sort -V | tail -1)
CMAKE_BIN=$(ls -d "$ANDROID_HOME"/cmake/* | sort -V | tail -1)/bin
TC=$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin
echo "NDK $NDK | arch $ARM_ARCH | jobs $JOBS"

# Build only the tested commits (scripts/engine-pins.env); ALLOW_UNPINNED=1 to try another one.
. "$ROOT/scripts/engine-pins.env"
check_pin() {  # <name> <checkout> <commit> [<patch marker>]
  local at; at=$(git -C "$2" rev-parse HEAD)
  if [ "$at" != "$3" ] && [ "${ALLOW_UNPINNED:-0}" != 1 ]; then
    echo "$1 is at ${at:0:8}, pinned ${3:0:8} (scripts/engine-pins.env): git -C $2 checkout $3, or ALLOW_UNPINNED=1" >&2; exit 1
  fi
  if [ -n "${4:-}" ] && [ "$(cat "$2/.boar-patches" 2>/dev/null)" != "$4" ]; then
    echo "warning: $1 doesn't carry BOAR's patch series $4 (scripts/build-native-engines.sh on experimental-engines)" >&2
  fi
}
check_pin colibri "$COLIBRI" "$COLIBRI_PIN"
check_pin BigMoeOnEdge "$BMOE" "$BMOE_PIN" "$BMOE_PATCHES"

echo "== colibri ($(git -C "$COLIBRI" rev-parse --short HEAD))"
OUT=$PLUGINS/colibri/jniLibs/arm64-v8a
mkdir -p "$OUT"
nice -n 15 "$TC/aarch64-linux-android$API-clang" -O3 -march="$ARM_ARCH" -fopenmp -static-openmp -pthread -w \
  "$COLIBRI/c/olmoe.c" -o "$OUT/libcolibri_olmoe.so" -lm -fopenmp -static-openmp
"$TC/llvm-strip" "$OUT/libcolibri_olmoe.so"

echo "== BigMoeOnEdge ($(git -C "$BMOE" rev-parse --short HEAD)), static"
OUT=$PLUGINS/bmoe/jniLibs/arm64-v8a
BUILD=$BMOE/build-boar-static
mkdir -p "$OUT"
"$CMAKE_BIN/cmake" -S "$BMOE" -B "$BUILD" -G Ninja \
  -DCMAKE_MAKE_PROGRAM="$CMAKE_BIN/ninja" \
  -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-$API -DANDROID_STL=c++_static -DCMAKE_BUILD_TYPE=Release \
  -DBUILD_SHARED_LIBS=OFF -DBMOE_BUILD_TESTS=OFF -DGGML_NATIVE=OFF -DGGML_OPENCL=OFF -DGGML_OPENMP=OFF \
  -DGGML_CPU_ARM_ARCH="$ARM_ARCH" -DLLAMA_CURL=OFF >/dev/null
nice -n 15 "$CMAKE_BIN/cmake" --build "$BUILD" -j "$JOBS" --target bmoe-cli
cp "$BUILD/cli/bmoe-cli" "$OUT/libbmoe_cli.so"
"$TC/llvm-strip" "$OUT/libbmoe_cli.so"
# Static or not: the executable must not ask for a library BOAR doesn't ship.
if "$TC/llvm-readelf" -d "$OUT/libbmoe_cli.so" | grep -E "NEEDED.*(ggml|llama|c\+\+_shared)"; then
  echo "bmoe still links shared llama.cpp libraries; check BUILD_SHARED_LIBS in its CMake" >&2; exit 1
fi
ls -la "$PLUGINS"/colibri/jniLibs/arm64-v8a "$PLUGINS"/bmoe/jniLibs/arm64-v8a
