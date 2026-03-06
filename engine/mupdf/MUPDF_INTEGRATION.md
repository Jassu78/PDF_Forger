# MuPDF integration (Maven AAR)

The app uses **MuPDF's pre-built AAR** from Maven for all PDF operations. No native build, Docker, or NDK is required.

## Setup

The MuPDF dependency is declared in `engine/mupdf/build.gradle.kts`:

```kotlin
implementation("com.artifex.mupdf:fitz:1.27.1")
```

The Maven repository is configured in `settings.gradle.kts`:

```kotlin
maven { url = uri("https://maven.ghostscript.com") }
```

## Architecture

```
Kotlin Tools → MuPDF Java API (com.artifex.mupdf.fitz.*) → pre-built .so in AAR
```

All PDF processing happens on-device. No network calls at runtime.

## Tools

| Tool | MuPDF API used |
|------|----------------|
| Merge | `PDFDocument.graftPage()` |
| Split | `PDFDocument.graftPage()` (selected pages) |
| Compress | `PDFDocument.save()` with compress options |
| Reorder | `PDFDocument.graftPage()` + page rotation via `findPage().put("Rotate", ...)` |
| Image-to-PDF | `Image(bytes)` + `addImage()` + `addPage()` + `insertPage()` |
| PDF Info | `Document.countPages()` |
| Convert (PDF→DOCX) | `Page.toStructuredText()` + Apache POI |

## Updating MuPDF

Change the version number in `engine/mupdf/build.gradle.kts`:

```kotlin
implementation("com.artifex.mupdf:fitz:NEW_VERSION")
```

Available versions: https://maven.ghostscript.com/com/artifex/mupdf/fitz/

## Licensing

MuPDF is AGPL-3.0. If the app is closed-source, a commercial license from Artifex is required.
