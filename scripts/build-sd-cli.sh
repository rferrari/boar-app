#!/usr/bin/env bash
# Builds stable-diffusion.cpp's sd-cli for the image lab into the image plugin, android-native/app/src/labs/plugins/image/jniLibs/arm64-v8a (gitignored):
#   libsdcli.so       armv8.2 + dotprod + fp16 (any recent phone, e.g. the POCO F3)
#   libsdcli_i8mm.so  armv8.6 + i8mm (e.g. the X6 Pro); the app picks it when the CPU has i8mm
# Static, no libc++_shared. Then build the app with -Pboar.experimentalFeatures=true.
#
# Usage: scripts/build-sd-cli.sh <path to a stable-diffusion.cpp checkout>   (MIT; the tested commit is SD_PIN in
# scripts/engine-pins.env, ALLOW_UNPINNED=1 to build another)
set -euo pipefail
SD=${1:?usage: $0 <stable-diffusion.cpp checkout>}
ROOT=$(cd "$(dirname "$0")/.." && pwd)
. "$ROOT/scripts/engine-pins.env"
AT=$(git -C "$SD" rev-parse HEAD)
if [ "$AT" != "$SD_PIN" ] && [ "${ALLOW_UNPINNED:-0}" != 1 ]; then
  echo "stable-diffusion.cpp is at ${AT:0:8}, pinned ${SD_PIN:0:8} (scripts/engine-pins.env); ALLOW_UNPINNED=1 to build it" >&2; exit 1
fi
SDK=${ANDROID_HOME:-$HOME/Android/Sdk}
NDK=$SDK/ndk/27.1.12297006
CMAKE_BIN=$(ls -d "$SDK"/cmake/*/bin | tail -1)
OUT=$ROOT/android-native/app/src/labs/plugins/image/jniLibs/arm64-v8a
mkdir -p "$OUT"

build() { # <build dir> <arch flags> <output name>
  PATH=$CMAKE_BIN:$PATH cmake -S "$SD" -B "$SD/$1" -G Ninja -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-28 \
    -DANDROID_STL=c++_static -DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON -DBUILD_SHARED_LIBS=OFF -DGGML_NATIVE=OFF -DGGML_OPENMP=OFF -DGGML_CPU_ARM_ARCH="$2" >/dev/null
  PATH=$CMAKE_BIN:$PATH cmake --build "$SD/$1" --target sd-cli -j"$(nproc)" >/dev/null
  "$NDK"/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip -o "$OUT/$3" "$SD/$1/bin/sd-cli"
  # 16 KB pages (issue #88): every LOAD segment aligned to 0x4000.
  if "$NDK"/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf -lW "$OUT/$3" | awk '/LOAD/{print $NF}' | grep -qv 0x4000; then
    echo "$OUT/$3 is not 16 KB-aligned" >&2; exit 1
  fi
  echo "$OUT/$3 $(du -h "$OUT/$3" | cut -f1)"
}
build build-android-v82 armv8.2-a+dotprod+fp16 libsdcli.so
build build-android armv8.6-a+dotprod+i8mm+fp16 libsdcli_i8mm.so
