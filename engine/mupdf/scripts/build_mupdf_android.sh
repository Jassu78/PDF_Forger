#!/usr/bin/env bash
# Prepare MuPDF headers and directory layout for Android prebuilts.
# Run from repo root: bash engine/mupdf/scripts/build_mupdf_android.sh
#
# This script clones MuPDF and copies include/ to engine/mupdf/prebuilt/include/.
# You still need to build libmupdf.a for each ABI (arm64-v8a, armeabi-v7a, x86_64, x86)
# and place them in engine/mupdf/prebuilt/<ABI>/libmupdf.a
# See MUPDF_INTEGRATION.md and https://mupdf.readthedocs.io/en/latest/guide/using-with-android.html

set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENGINE_MUPDF="$(cd "$SCRIPT_DIR/../.." && pwd)"
PREBUILT="$ENGINE_MUPDF/prebuilt"
BUILD_DIR="${MUPDF_BUILD_DIR:-/tmp/mupdf-pdf-forger-build}"

echo "=== MuPDF Android prebuilt setup ==="
echo "Prebuilt dir: $PREBUILT"

mkdir -p "$PREBUILT"
mkdir -p "$BUILD_DIR"
cd "$BUILD_DIR"

if [ ! -d "mupdf" ]; then
  echo "Cloning MuPDF..."
  git clone --depth 1 https://github.com/ArtifexSoftware/mupdf.git
  cd mupdf
  git submodule update --init
  make generate
  cd ..
fi

echo "Copying MuPDF headers to prebuilt/include..."
rm -rf "$PREBUILT/include"
cp -r "$BUILD_DIR/mupdf/include" "$PREBUILT/include"

for ABI in arm64-v8a armeabi-v7a x86_64 x86; do
  mkdir -p "$PREBUILT/$ABI"
done

echo ""
echo "Headers are in $PREBUILT/include/"
echo "Build libmupdf.a for each ABI and put in $PREBUILT/<ABI>/libmupdf.a"
echo "Then rebuild the app; CMake will link MuPDF when prebuilts are present."
