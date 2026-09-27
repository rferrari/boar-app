#!/usr/bin/env bash
# Builds the experimental external inference engines for Android arm64 and stages them into the
# native-engine module (modules/native-engine), which runs them as child processes.
#
#   colibri       (https://github.com/JustVugg/colibri, Apache-2.0)  -> libcolibri_olmoe.so
#                 pure-C MoE engine that streams experts from storage; OLMoE engine only.
#   BigMoeOnEdge  (https://github.com/Helldez/BigMoeOnEdge, Apache-2.0) -> libbmoe_cli.so + libs
#                 streams the experts of a GGUF MoE bigger than RAM, on top of llama.cpp.
#
# Usage: scripts/build-native-engines.sh [colibri checkout] [BigMoeOnEdge checkout]
# Defaults: ../colibri and ../BigMoeOnEdge next to this repo. ANDROID_HOME must point at an SDK
# with an NDK and CMake installed. The binaries are build output: they're gitignored.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
COLIBRI=$(cd "${1:-$ROOT/../colibri}" && pwd)
BMOE=$(cd "${2:-$ROOT/../BigMoeOnEdge}" && pwd)
JNI=$ROOT/modules/native-engine/android/src/main/jniLibs/arm64-v8a
# Upstream's portable choice: runs on any ARMv8.2 phone (i8mm would crash older cores).
ARM_ARCH=${ARM_ARCH:-armv8.2-a+dotprod+fp16}
API=29

NDK=$(ls -d "$ANDROID_HOME"/ndk/* | sort -V | tail -1)
CMAKE_BIN=$(ls -d "$ANDROID_HOME"/cmake/* | sort -V | tail -1)/bin
TC=$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin
STRIP=$TC/llvm-strip
echo "NDK $NDK | arch $ARM_ARCH"
mkdir -p "$JNI"
rm -f "$JNI"/*.so

echo "== colibri ($(git -C "$COLIBRI" rev-parse --short HEAD))"
"$TC/aarch64-linux-android$API-clang" -O3 -march="$ARM_ARCH" -fopenmp -static-openmp -pthread -w \
  "$COLIBRI/c/olmoe.c" -o "$JNI/libcolibri_olmoe.so" -lm -fopenmp -static-openmp

echo "== BigMoeOnEdge ($(git -C "$BMOE" rev-parse --short HEAD))"
"$CMAKE_BIN/cmake" -S "$BMOE" -B "$BMOE/build-boar-android" -G Ninja \
  -DCMAKE_MAKE_PROGRAM="$CMAKE_BIN/ninja" \
  -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-$API -DCMAKE_BUILD_TYPE=Release \
  -DBMOE_BUILD_TESTS=OFF -DGGML_NATIVE=OFF -DGGML_OPENCL=OFF -DGGML_OPENMP=OFF \
  -DGGML_CPU_ARM_ARCH="$ARM_ARCH" -DLLAMA_CURL=OFF >/dev/null
"$CMAKE_BIN/cmake" --build "$BMOE/build-boar-android" -j "${JOBS:-6}"
cp "$BMOE/build-boar-android/cli/bmoe-cli" "$JNI/libbmoe_cli.so"
for name in libggml.so libggml-base.so libggml-cpu.so libllama.so libllama-common.so; do
  cp "$(find "$BMOE/build-boar-android" -name "$name" | head -1)" "$JNI/$name"
done
# libc++_shared.so is not copied: React Native already ships it in the APK.

"$STRIP" "$JNI"/*.so
ls -la "$JNI"
