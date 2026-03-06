#!/bin/bash
# Build MuPDF for Android inside Docker. Output goes to /out (mounted volume).
# Run: docker run --rm -v "$(pwd)/prebuilt:/out" mupdf-android
set -e

OUT="${MUPDF_OUT:-/out}"
if [ ! -d "$OUT" ]; then
  echo "Mount prebuilt dir: docker run -v /path/to/engine/mupdf/prebuilt:/out ..."
  exit 1
fi

echo "=== MuPDF Android build (Docker) ==="
echo "Output: $OUT"

# Clone and generate
if [ ! -d mupdf ]; then
  git clone --depth 1 https://github.com/ArtifexSoftware/mupdf.git
  cd mupdf
  git submodule update --init
  make generate
  cd ..
fi

cd mupdf
export PATH="${ANDROID_NDK_HOME}:${PATH}"

# Build for all ABIs (ndk-build must run from mupdf root)
"${ANDROID_NDK_HOME}/ndk-build" -j$(nproc) \
  APP_BUILD_SCRIPT="$(pwd)/platform/java/Android.mk" \
  APP_PROJECT_PATH="$(pwd)/build/android" \
  APP_PLATFORM=android-21 \
  APP_OPTIM=release \
  APP_ABI=arm64-v8a,armeabi-v7a,x86_64,x86

# Merge static libs -> libmupdf.a per ABI
OBJ="build/android/obj/local"
for ABI in arm64-v8a armeabi-v7a x86_64 x86; do
  [ ! -d "$OBJ/$ABI" ] && continue
  echo "Merging $ABI..."
  cd "$OBJ/$ABI"
  mkdir -p merge
  cd merge
  for a in ../libmupdf_*.a; do [ -f "$a" ] && ar -x "$a"; done
  ar -rcs libmupdf.a *.o 2>/dev/null || true
  if [ -f libmupdf.a ]; then
    mkdir -p "$OUT/$ABI"
    cp libmupdf.a "$OUT/$ABI/"
    echo "  -> $OUT/$ABI/libmupdf.a"
  fi
  cd /build/mupdf
done

cd /build
# Copy headers
rm -rf "$OUT/include"
cp -r mupdf/include "$OUT/include"
echo ""
echo "Done. Prebuilts in $OUT"
echo "Rebuild the app to link MuPDF."
