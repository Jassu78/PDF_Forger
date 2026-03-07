# PDF Forger

[![CI](https://github.com/Jassu78/PDF_Forger/actions/workflows/ci.yml/badge.svg)](https://github.com/Jassu78/PDF_Forger/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

**Android app** for PDF operations: merge, split, compress, image-to-PDF, DOCX-to-PDF, reorder pages, and convert formats.

## Features

| Feature | Description |
|---------|-------------|
| **Merge PDFs** | Combine multiple PDFs into one |
| **Split PDF** | Extract selected pages into a new file |
| **Compress PDF** | Reduce file size |
| **Image to PDF** | Convert images to PDF |
| **Doc to PDF** | Convert Word (.docx) to PDF with formatting, tables, and images |
| **Reorder / Rotate** | Rearrange and rotate pages |

## Requirements

- **minSdk:** 29 (Android 10)
- **targetSdk:** 34
- **Kotlin:** 2.1
- **Java:** 17

## Build

```bash
./gradlew assembleDebug
# or
./gradlew bundleRelease
```

### First-time setup

If `gradlew` is missing:

```bash
gradle wrapper
```

## Project structure

```
app/               # Application entry point
domain/            # Models, use cases (no Android)
  models/          # Shared models
  core/            # Tool interfaces, validation
data/              # File I/O, storage, background work
  impl/            # SAF file adapter
  storage/         # Temp file management
  worker/          # WorkManager jobs
engine/            # PDF engines
  mupdf/           # MuPDF JNI (merge, split, compress, image-to-PDF)
  converter/       # DOCX→PDF (POI + WebView)
common/            # UI components, utilities
feature/           # Feature modules (home, merge, split, etc.)
```

## Release

Create a version tag and push:

```bash
git tag v1.0.0
git push origin v1.0.0
```

GitHub Actions will build the release and publish AAB/APK to [Releases](https://github.com/Jassu78/PDF_Forger/releases).

## CI / CD

| Workflow | Trigger | Actions |
|----------|---------|---------|
| **CI** | Push, PR | Lint, test, build debug APK |
| **Release** | Tag `v*` | Build AAB/APK, create GitHub Release |
| **Pages** | Push to main | Deploy project page |

See [.github/README.md](.github/README.md) for details.

## Native engine (MuPDF)

PDF operations use **MuPDF** (Artifex) from Maven. No NDK or native build required. See [engine/mupdf/MUPDF_INTEGRATION.md](engine/mupdf/MUPDF_INTEGRATION.md).

## License

This project is licensed under the **Apache License 2.0**. See [LICENSE](LICENSE).

Third-party components and their licenses are listed in [NOTICE](NOTICE). MuPDF (AGPL v3) is used under its own terms.
