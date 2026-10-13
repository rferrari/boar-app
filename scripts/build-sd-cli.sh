#!/usr/bin/env bash
# Builds stable-diffusion.cpp's sd-cli for the image lab into android-native/labs/jniLibs/arm64-v8a (gitignored):
#   libsdcli.so       armv8.2 + dotprod + fp16 (any recent phone, e.g. the POCO F3)
#   libsdcli_i8mm.so  armv8.6 + i8mm (e.g. the X6 Pro); the app picks it when the CPU has i8mm
# Static, no libc++_shared. Then build the app with -Pboar.labs=true.
#
# Usage: scripts/build-sd-cli.sh <path to a stable-diffusion.cpp checkout>   (tested at d637cfb, MIT)
set -euo pipefail
SD=${1:?usage: $0 <stable-diffusion.cpp checkout>}
ROOT=$(cd "$(dirname "$0")/.." && pwd)
SDK=${ANDROID_HOME:-$HOME/Android/Sdk}
NDK=$SDK/ndk/27.1.12297006
CMAKE_BIN=$(ls -d "$SDK"/cmake/*/bin | tail -1)
OUT=$ROOT/android-native/labs/jniLibs/arm64-v8a
mkdir -p "$OUT"

build() { # <build dir> <arch flags> <output name>
  PATH=$CMAKE_BIN:$PATH cmake -S "$SD" -B "$SD/$1" -G Ninja -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-28 \
    -DANDROID_STL=c++_static -DBUILD_SHARED_LIBS=OFF -DGGML_NATIVE=OFF -DGGML_OPENMP=OFF -DGGML_CPU_ARM_ARCH="$2" >/dev/null
  PATH=$CMAKE_BIN:$PATH cmake --build "$SD/$1" --target sd-cli -j"$(nproc)" >/dev/null
  "$NDK"/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip -o "$OUT/$3" "$SD/$1/bin/sd-cli"
  echo "$OUT/$3 $(du -h "$OUT/$3" | cut -f1)"
}
build build-android-v82 armv8.2-a+dotprod+fp16 libsdcli.so
build build-android armv8.6-a+dotprod+i8mm+fp16 libsdcli_i8mm.so
