#!/bin/bash
# Run from anywhere; builds image if needed, outputs to engine/mupdf/prebuilt
set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
cd "$SCRIPT_DIR"
docker build -t mupdf-android .
docker run --rm -v "$REPO_ROOT/engine/mupdf/prebuilt:/out" mupdf-android
