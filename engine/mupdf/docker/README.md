# MuPDF Android build (Docker)

Build MuPDF for Android in Docker. No NDK or MuPDF source on your machine.

## Quick start

From anywhere in the repo:

```bash
bash engine/mupdf/docker/run.sh
```

Or manually:

```bash
cd engine/mupdf/docker
docker build -t mupdf-android .
docker run --rm -v "$(pwd)/../prebuilt:/out" mupdf-android
```

Then rebuild the app; Merge, Split, Compress, Reorder will produce real PDFs.

## Cleanup

When you no longer need MuPDF prebuilts:

```bash
# Remove prebuilts from your project
rm -rf engine/mupdf/prebuilt/*

# Remove the Docker image
docker rmi mupdf-android
```

No MuPDF source or NDK remains on your system.
