# PDFForge Android — Production System Design

> **Version:** 1.0  
> **Status:** Canonical Reference  
> **License:** AGPL-3.0 (open-source, forever)  
> **Minimum SDK:** Android 10 (API 29)  
> **Target APK Size:** < 150 MB (base < 75 MB per ABI)  

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [High-Level Architecture](#2-high-level-architecture)
3. [Tech Stack with Justifications](#3-tech-stack-with-justifications)
4. [Native vs JVM Strategy](#4-native-vs-jvm-strategy)
5. [Operation Flows — Every Feature](#5-operation-flows--every-feature)
   - 5.1 [Image to PDF](#51-image-to-pdf)
   - 5.2 [PDF Merge](#52-pdf-merge)
   - 5.3 [PDF Split](#53-pdf-split)
   - 5.4 [PDF Compression](#54-pdf-compression)
   - 5.5 [PDF to DOCX Conversion](#55-pdf-to-docx-conversion)
   - 5.6 [PDF to PPTX Conversion](#56-pdf-to-pptx-conversion)
   - 5.7 [DOCX / PPTX to PDF](#57-docx--pptx-to-pdf)
   - 5.8 [Rotate & Reorder Pages](#58-rotate--reorder-pages)
   - 5.9 [Extract Specific Pages](#59-extract-specific-pages)
6. [Performance & Memory Strategy](#6-performance--memory-strategy)
7. [APK Size Optimization](#7-apk-size-optimization)
8. [Security & Privacy](#8-security--privacy)
9. [Testing Strategy](#9-testing-strategy)
10. [Open Source Strategy](#10-open-source-strategy)
11. [Folder Structure](#11-folder-structure)
12. [Risk & Tradeoffs](#12-risk--tradeoffs)
13. [Long-Term Maintainability](#13-long-term-maintainability)
14. [Final Recommendation Stack](#14-final-recommendation-stack)

---

## 1. Executive Summary

PDFForge is a **production-grade, fully offline, permanently open-source Android PDF toolkit**. Every operation — creation, compression, conversion, merging, splitting — runs entirely on-device. No file ever leaves the device. No cloud API is called. No analytics are phoned home.

This document is the canonical engineering reference for the project. It is written assuming the codebase will:

- Be maintained long-term under open-source governance
- Receive contributions from the community
- Scale to millions of users across diverse Android hardware
- Remain free for everyone, forever

### Core Constraints (Non-Negotiable)

| Constraint | Value |
|---|---|
| Network access | **Zero** — `INTERNET` permission not declared |
| File storage | **SAF only** — user controls where files go |
| Analytics | **None** — no telemetry, no crash reporting that needs network |
| Minimum Android | **API 29** (Android 10) |
| Max base APK | **< 75 MB per ABI**, < 150 MB absolute max |
| Large PDF support | **500+ pages**, handled via streaming |
| License | **AGPL-3.0** (open-source, copyleft, community-protective) |
| RAM safety | **Must run on 2 GB RAM devices** without OOM |

### Why AGPL-3.0 for an Always-Open Project

Since this project is permanently open-source, AGPL-3.0 is the **ideal license**:

- It forces any modified distributions to also be open-source (protects the community)
- It is fully compatible with MuPDF's license (the best available offline PDF engine)
- It prevents commercial actors from taking the code, closing it, and profiting without contributing back
- It does NOT prevent users from using the app — only from distributing closed forks
- The FSF and OSI both approve it as a genuine open-source license

If a module contributor wants Apache 2.0 for their specific contribution and it doesn't touch MuPDF, that is acceptable for standalone modules (e.g., `:common:utils`), clearly documented per-module.

### Phase 1 Feature Set

| Category | Features |
|---|---|
| **PDF Creation** | Image to PDF (single & batch), Page reorder, Rotate pages |
| **PDF Operations** | Merge PDFs, Split PDF, Extract page ranges |
| **PDF Compression** | Image quality reduction, Image downscale, Metadata strip, Object stream compression, Font subsetting |
| **PDF Conversion** | PDF → DOCX, PDF → PPTX, PDF → Images, DOCX → PDF, PPTX → PDF |
| **File Handling** | SAF-based save, Share via Intent, No forced caching |

---

## 2. High-Level Architecture

### 2.1 Architectural Pattern: Clean Architecture + Modular Gradle

The project uses **Clean Architecture** with module boundaries enforced by Gradle. The dependency rule is absolute: outer layers depend on inner layers. No reverse dependencies exist.

```
┌─────────────────────────────────────────────────────────┐
│               Presentation Layer                        │
│         Jetpack Compose + ViewModels                    │
│   :feature:pdf_creation  :feature:compression  ...     │
└─────────────────────────┬───────────────────────────────┘
                          │ depends on (interfaces only)
┌─────────────────────────▼───────────────────────────────┐
│                 Domain Layer                            │
│          Pure Kotlin — Zero Android imports             │
│   UseCases │ Repository Interfaces │ PdfTool Plugin     │
│         :domain:core   :domain:models                  │
└──────────────┬──────────────────┬──────────────────────┘
               │                  │
┌──────────────▼──────┐  ┌────────▼──────────────────────┐
│    Engine Layer     │  │        Data Layer              │
│  NDK + JVM Bridges  │  │  SAF Adapter │ TempFileManager │
│  :engine:mupdf      │  │  DataStore   │ Repository Impl │
│  :engine:pdfbox     │  │    :data:impl  :data:storage   │
│  :engine:converter  │  └───────────────────────────────┘
│  :engine:tesseract  │
└─────────────────────┘
```

### 2.2 Layer Contracts

**Domain Layer** — Pure Kotlin, zero Android/engine imports:
- One `UseCase` class per operation, single `execute()` method
- All use cases return `sealed class OperationResult<T>`
- Repository interfaces only — no implementations
- All functions are `suspend` — coroutine-native from the ground up

**Engine Layer** — NDK + JVM adapters:
- JNI wrappers are thin; all business logic lives in Kotlin
- Every native resource (`fz_context`, `fz_document`) wrapped in `Closeable`
- Engines are interface-backed: domain never knows which engine is executing
- Engine errors mapped to domain `ErrorCode` sealed class before crossing boundary

**Data Layer** — Android-aware, no business logic:
- `SafFileAdapter`: wraps `ContentResolver` for all file I/O
- `TempFileManager`: scoped temp file lifecycle, guaranteed cleanup
- `PdfRepositoryImpl`: wires use cases to engines via DI

### 2.3 Plugin Tool System

Every PDF tool is a `PdfTool` plugin. Adding a new tool (e.g., PDF watermarking) requires:
1. Implement `PdfTool` interface in the relevant engine module
2. Register in Hilt DI module
3. Add a Compose screen in the feature module
4. Register in the navigation graph

No changes to core domain or engine infrastructure.

```kotlin
// :domain:core — PdfTool plugin contract
interface PdfTool {
    val id: String                    // "merge_pdf", "compress_pdf", etc.
    val nameRes: Int                  // Localizable display name
    val iconRes: Int
    val category: ToolCategory        // CREATION, COMPRESSION, CONVERSION, OPERATIONS
    val requiresDynamicModule: String? // null, or DFM module name if needed

    suspend fun execute(params: ToolParams): OperationResult<Uri>
    fun validate(params: ToolParams): ValidationResult
    fun estimateOutputSize(params: ToolParams): Long  // For user pre-flight info
    fun cancel()                      // Propagates CancellationSignal to native
}
```

### 2.4 Module Dependency Graph

```
:app
 ├── :feature:pdf_creation
 ├── :feature:compression
 ├── :feature:conversion
 └── :feature:merge_split
      │
      ├── :domain:core  ──── :domain:models
      │        │
      │        └── :data:impl ─── :data:storage
      │
      ├── :engine:mupdf    (NDK — core PDF ops)
      ├── :engine:pdfbox   (JVM — structural ops)
      ├── :engine:imageproc (Bitmap + Skia)
      ├── :engine:converter (Apache POI — DOCX/PPTX)
      └── :engine:tesseract (NDK — OCR, optional DFM)

:common:ui      ← all feature modules depend on this
:common:utils   ← all modules depend on this
```

---

## 3. Tech Stack with Justifications

### 3.1 Complete Stack Decision Table

| Component | Chosen | Alternative(s) | Why Chosen | License |
|---|---|---|---|---|
| **Primary language** | Kotlin 1.9+ | Java | Coroutines, sealed classes, null safety, Compose. No new Java. | Apache 2.0 |
| **Native layer** | C/C++17 via NDK r26 | JVM only | Required for MuPDF and Tesseract. JVM cannot match C for large PDF throughput. | Apache 2.0 |
| **PDF engine (core)** | MuPDF (libmupdf) | iText7, PDFBox-only | Battle-tested, streaming API, 500+ page support, form/annotation support, corrupted PDF recovery, best open-source PDF engine in existence. | AGPL-3.0 ✅ |
| **PDF structural ops** | Apache PDFBox 3.x | iText7 Community | Pure JVM, Apache 2.0, excellent for metadata editing, font subsetting, object streams. Complements MuPDF for ops that don't need rendering. | Apache 2.0 ✅ |
| **DOCX/PPTX output** | Apache POI 5.x (poi-ooxml-lite) | docx4j | Only viable offline DOCX/PPTX writer. Mature. poi-ooxml-lite is 80% smaller than full poi-ooxml-schemas. | Apache 2.0 ✅ |
| **OCR** | Tesseract 5.x NDK | ML Kit (offline) | Apache 2.0, LSTM-based, 100+ languages, ~90-96% accuracy on clean Latin text. ML Kit offline model is 5.8 MB but accuracy is lower on non-standard fonts. | Apache 2.0 ✅ |
| **Image processing** | Android Bitmap API + Skia (via MuPDF NDK) | Coil (display only) | BitmapFactory + BitmapRegionDecoder for large image streaming. Skia for NDK-layer image ops. Coil not appropriate for processing (display caching only). | BSD / Android Open Source |
| **Font subsetting** | HarfBuzz (via MuPDF NDK) | fonttools (Python, not usable) | HarfBuzz is already linked into MuPDF. Industry standard for font shaping and subsetting. Used by Chrome and Android itself. | MIT ✅ |
| **Background processing** | WorkManager 2.9+ | Foreground Service only | Survives process death, Doze-mode aware, battery-conscious, testable with `TestWorkerBuilder`. Use Foreground Service only for progress notification — WorkManager handles the actual work. | Apache 2.0 ✅ |
| **DI framework** | Hilt 2.48+ | Koin | Compile-time validation, zero reflection overhead, official Google recommendation, Hilt-WorkManager integration. Koin is runtime — acceptable alternative if contributors prefer, but Hilt is chosen for prod safety. | Apache 2.0 ✅ |
| **UI framework** | Jetpack Compose 1.5+ | XML Views | Declarative, better for drag-reorder UI, progress animations, dynamic tool grids. XML is legacy for new projects. Compose Stability API prevents unnecessary recompositions. | Apache 2.0 ✅ |
| **Navigation** | Navigation Compose 2.7+ (type-safe) | Compose Destinations (third-party) | Official, type-safe routes with `@Serializable` destinations in 2.7+. Deep link support. Works with multi-module navigation graphs. | Apache 2.0 ✅ |
| **Storage** | Android SAF + DataStore Preferences | SharedPreferences, direct File API | SAF is mandatory for Android 10+ scoped storage. DataStore replaces SharedPreferences (flow-based, type-safe). Zero use of `MANAGE_EXTERNAL_STORAGE`. | Apache 2.0 ✅ |
| **Build system** | Gradle 8.x + Kotlin DSL + Version Catalogs | Groovy DSL | Type-safe build scripts. `libs.versions.toml` as single source of truth for all versions. Convention plugins for shared build logic. | Apache 2.0 ✅ |

### 3.2 PDF Engine Deep Dive: MuPDF vs Alternatives

```
MuPDF
  + Written in C — fastest possible for Android NDK
  + fz_try/fz_catch — robust error isolation, no JNI crash propagation
  + Streaming page API — never loads entire doc into memory
  + Handles corrupted PDFs gracefully (XRef repair mode)
  + Supports: rendering, writing, forms, annotations, compression, encryption
  + Active Artifex maintenance — security patches upstream
  - AGPL-3.0 — requires app source to be open (which it is — not a problem)
  - JNI integration complexity — requires NDK build setup
  - Binary size: ~8 MB per ABI

Apache PDFBox (JVM)
  + Apache 2.0 — cleanest possible license
  + Pure JVM — no NDK setup
  + Excellent for: metadata editing, structural analysis, font operations
  + Good DOCX-to-PDF via rendering bridge
  - Slower than MuPDF for large documents (JVM overhead)
  - Rendering quality lower than MuPDF
  - Higher RAM usage for large docs (loads more into heap)

Decision: Use BOTH. MuPDF for all rendering and core PDF manipulation.
PDFBox for structural operations (metadata, font subsetting analysis,
DOCX→PDF output). This hybrid gives best-of-both-worlds.
```

### 3.3 Third-Party License Compatibility Matrix

All dependencies are AGPL-compatible for an open-source app:

| Library | License | AGPL-3.0 App Compatible | Notes |
|---|---|---|---|
| MuPDF | AGPL-3.0 | ✅ Yes | App must be AGPL — it is |
| Apache PDFBox | Apache-2.0 | ✅ Yes | Permissive, no conflict |
| Apache POI | Apache-2.0 | ✅ Yes | Permissive, no conflict |
| Tesseract | Apache-2.0 | ✅ Yes | Permissive, no conflict |
| HarfBuzz | MIT | ✅ Yes | Permissive, no conflict |
| Hilt / Dagger | Apache-2.0 | ✅ Yes | |
| Jetpack Compose | Apache-2.0 | ✅ Yes | |
| WorkManager | Apache-2.0 | ✅ Yes | |
| Kotlin Coroutines | Apache-2.0 | ✅ Yes | |
| Kotlin STD | Apache-2.0 | ✅ Yes | |
| Navigation Compose | Apache-2.0 | ✅ Yes | |
| DataStore | Apache-2.0 | ✅ Yes | |

**No GPL-2.0-only, no proprietary, no BSL dependencies.** FOSSA automated scan in CI enforces this on every PR.

---

## 4. Native vs JVM Strategy

### 4.1 Decision Framework

The key question for each operation: **does this need native performance, or can JVM handle it safely?**

```
Operation                         | Layer    | Reason
──────────────────────────────────┼──────────┼────────────────────────────────────────
PDF page parsing                  | NDK      | Memory-critical, MuPDF C API
PDF page rendering to bitmap      | NDK      | Pixel throughput — C is 3-5x faster
PDF writing / rewriting           | NDK      | Stream-based, MuPDF handles safely
Image re-encoding (JPEG compress) | JVM      | Android Bitmap.compress is sufficient
Bitmap scaling / sampling         | JVM      | BitmapFactory handles well, Skia via NDK for PDFs
Font subsetting                   | NDK      | HarfBuzz (linked into MuPDF)
OCR                               | NDK      | Tesseract is C-based, JVM wrappers are leaky
Metadata editing                  | JVM      | PDFBox handles fine
DOCX/PPTX building                | JVM      | Apache POI is JVM-only anyway
Object stream compression         | NDK      | MuPDF's fz_write with compression flags
Layout analysis (PDF→DOCX)        | JVM      | Kotlin heuristics, no perf requirement
```

### 4.2 Binary Size Control — NDK

MuPDF is the largest native binary. Control strategies:

```cmake
# CMakeLists.txt — strip unused MuPDF components
set(MuPDF_NO_JAVASCRIPT ON)     # Remove JS interpreter (~800KB)
set(MuPDF_NO_XPS ON)            # Remove XPS support (~200KB)
set(MuPDF_NO_SVG ON)            # Remove SVG renderer if not needed
set(MuPDF_NO_HTML ON)           # Remove HTML renderer

# Result: libmupdf.a drops from ~12MB to ~8MB per ABI
```

```bash
# Strip debug symbols from release .so files
# Done automatically by Gradle release build type
# Additional manual strip for CI verification:
${ANDROID_NDK}/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip \
  --strip-unneeded app/build/intermediates/stripped_native_libs/release/**/*.so
```

### 4.3 JNI Bridge Design

JNI wrappers follow the **thin bridge** pattern: no logic in C++, just call MuPDF APIs and marshal data.

```kotlin
// :engine:mupdf — Kotlin-facing API (clean, no JNI leakage)
class MuPdfEngine @Inject constructor(
    private val jni: MuPdfJni,
    private val tempFileManager: TempFileManager
) : PdfEngine {

    override suspend fun openDocument(uri: Uri, context: Context): PdfDocumentHandle {
        val fd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw PdfEngineException(ErrorCode.CANNOT_OPEN_FILE)
        val handle = jni.openFromFd(fd.fd)
        if (handle == 0L) throw PdfEngineException(ErrorCode.INVALID_PDF)
        return PdfDocumentHandle(handle, fd)
    }

    override suspend fun copyPage(
        source: PdfDocumentHandle,
        targetDoc: PdfDocumentHandle,
        pageIndex: Int
    ) = withContext(AppDispatchers.PDF_IO) {
        jni.copyPage(source.handle, targetDoc.handle, pageIndex)
    }

    override fun close(handle: PdfDocumentHandle) {
        jni.closeDocument(handle.handle)
        handle.fd.close()
    }
}

// :engine:mupdf — JNI declarations (flat, C-style naming)
object MuPdfJni {
    external fun openFromFd(fd: Int): Long          // returns fz_document* as Long
    external fun openNew(): Long                     // returns empty fz_document*
    external fun copyPage(src: Long, dst: Long, pageIdx: Int)
    external fun writeToCacheFile(doc: Long, path: String): Boolean
    external fun closeDocument(doc: Long)
    external fun getPageCount(doc: Long): Int

    init { System.loadLibrary("mupdf_bridge") }
}
```

```cpp
// :engine:mupdf/src/main/cpp/mupdf_bridge.cpp
// JNI implementation — thin wrappers only
extern "C" JNIEXPORT jlong JNICALL
Java_dev_pdfforge_engine_mupdf_MuPdfJni_openFromFd(JNIEnv *env, jobject, jint fd) {
    fz_context *ctx = fz_new_context(NULL, NULL, FZ_STORE_DEFAULT);
    if (!ctx) return 0L;
    fz_document *doc = NULL;
    fz_try(ctx) {
        doc = fz_open_document_with_stream(ctx, NULL, fz_open_file_ptr_progressive(ctx, fdopen(fd, "r")));
    } fz_catch(ctx) {
        fz_drop_context(ctx);
        return 0L;  // Return 0 — Kotlin side throws PdfEngineException
    }
    // Pack ctx + doc into a single opaque Long handle via struct heap allocation
    auto *handle = new DocHandle{ctx, doc};
    return reinterpret_cast<jlong>(handle);
}
```

---

## 5. Operation Flows — Every Feature

### 5.1 Image to PDF

**Tech stack:** Android SAF → BitmapFactory / BitmapRegionDecoder → WorkManager → MuPDF JNI → SAF write

```
User selects images (SAF multi-picker)
    │
    ├─ For each image URI:
    │   ├─ openInputStream via ContentResolver
    │   ├─ BitmapFactory.Options { inJustDecodeBounds = true }  ← get dimensions first
    │   ├─ calculateInSampleSize(opts, targetWidth=2480px)       ← A4 at 300 DPI
    │   ├─ If image > 30MB: use BitmapRegionDecoder tile approach
    │   ├─ Apply user transforms (rotation Matrix, crop Rect)
    │   └─ Bitmap.Config.RGB_565 for photos (saves 50% vs ARGB_8888)
    │
    ├─ WorkManager: ImageToPdfWorker (handles batch, survives config change)
    │   ├─ fz_new_context(NULL, NULL, FZ_STORE_DEFAULT)
    │   ├─ fz_new_document(ctx)
    │   ├─ For each processed bitmap:
    │   │   ├─ Convert to byte array (Bitmap.compress JPEG/PNG per settings)
    │   │   ├─ fz_new_image_from_data(ctx, bytes, len, w, h, ...)
    │   │   └─ fz_insert_page(ctx, doc, pageIndex, ...)
    │   ├─ fz_write_document(ctx, doc, temp_path, opts) ← linearized
    │   └─ Stream temp file to SAF URI via ContentResolver.openOutputStream
    │
    └─ Cleanup: recycle all bitmaps, delete temp file, close fz_document
```

**Memory guard:** Max in-memory bitmaps at one time = 3. WorkManager processes queue serially per worker slot. `limitedParallelism(2)` on `Dispatchers.IO` for PDF_IO.

**Error cases:**
- Image unreadable → `OperationResult.Error(ErrorCode.IMAGE_DECODE_FAILED)` per image, rest proceed
- MuPDF out of memory → WorkManager `Result.retry()` with halved batch size
- SAF write permission revoked → `OperationResult.Error(ErrorCode.STORAGE_PERMISSION_LOST)`

---

### 5.2 PDF Merge

**Tech stack:** SAF multi-picker → File validation → WorkManager → MuPDF JNI stream copy → SAF write

```
User selects N PDFs, sets merge order (drag-reorder UI)
    │
    ├─ Validation pass (before any processing):
    │   ├─ Check magic bytes: 0x25 0x50 0x44 0x46 ("%PDF")
    │   ├─ Check minimum size: > 512 bytes
    │   ├─ Attempt fz_open_document — catch fz_catch errors
    │   └─ Report per-file status. User can skip invalid files or abort.
    │
    ├─ fz_new_context with 64MB heap cap
    ├─ fz_new_document(ctx) ← empty output document
    │
    ├─ For doc_i in ordered list:
    │   ├─ openFromFd(fd_i) → source_handle
    │   ├─ pageCount = fz_count_pages(ctx, source_doc)
    │   ├─ For page 0..pageCount-1:
    │   │   ├─ fz_copy_page(ctx, dst_doc, -1, src_doc, page_i, 0)
    │   │   │   ← -1 means append. Streams page content — no full render.
    │   │   └─ setProgressAsync(workDataOf("progress" to calcProgress()))
    │   └─ fz_drop_document(ctx, src_doc) ← release immediately after copy
    │
    ├─ Metadata handling (user choice):
    │   ├─ Option A: Preserve first doc's metadata
    │   ├─ Option B: Strip all metadata (fz_clean_file with no_info=true)
    │   └─ Option C: Set custom title/author
    │
    ├─ fz_write_document with opts: { linearize=1, compress=1, compress_images=0 }
    └─ Stream to SAF URI. Delete temp. Drop context.
```

**Critical design note:** Pages are copied via MuPDF's `fz_copy_page` which copies the page's PDF object tree, not a rendered bitmap. Links, annotations, and vector content are fully preserved. This is a structural copy, not a re-render.

---

### 5.3 PDF Split

**Tech stack:** SAF picker → MuPDF page analysis → WorkManager → Per-range MuPDF write → SAF folder write

```
User selects PDF, chooses split mode:

  Mode A — Split every N pages:
    ├─ totalPages = fz_count_pages(ctx, doc)
    └─ ranges = [(0, N-1), (N, 2N-1), ...] ← calculate automatically

  Mode B — Split by PDF bookmarks:
    ├─ outline = fz_load_outline(ctx, doc)
    ├─ Walk outline tree to get top-level chapters
    └─ ranges = bookmark page ranges

  Mode C — Custom ranges (user input: "1-3, 5, 7-end"):
    ├─ Parse input string → List<IntRange>
    ├─ Validate against totalPages
    └─ Report invalid ranges before processing

For each range (i, start, end):
    ├─ outDoc = fz_new_document(ctx)
    ├─ For page in start..end:
    │   └─ fz_copy_page(ctx, outDoc, -1, srcDoc, page, 0)
    ├─ fz_write_document → temp_i.pdf
    ├─ SAF write to user-chosen folder:
    │   └─ Filename: "${originalName}_part${i+1}.pdf"
    └─ Delete temp_i.pdf

Total output: N files, all in user-chosen folder via SAF DocumentFile API.
```

---

### 5.4 PDF Compression

**Tech stack:** MuPDF for image extraction/rewrite + PDFBox for metadata/font ops + HarfBuzz (via MuPDF) for font subsetting

PDFForge uses a **Strategy Pattern** for compression. Each strategy is an independent `CompressionStrategy` implementation. Users can combine any subset.

```
┌─────────────────────────────────────────────────────────────────┐
│                  CompressionPipeline                            │
│                                                                 │
│  Strategy 1: ReduceImageQuality                                │
│    ├─ Extract each image: fz_get_pixmap(ctx, doc, img_ref)     │
│    ├─ Convert to Android Bitmap                                 │
│    ├─ Bitmap.compress(JPEG, quality=userSlider, outputStream)   │
│    └─ Replace image bytes in PDF object stream                  │
│                                                                 │
│  Strategy 2: DownscaleImages                                   │
│    ├─ Get image natural DPI from image dict                     │
│    ├─ Target DPI: 150 (screen) or 96 (web) or 300 (print)      │
│    ├─ If naturalDPI > targetDPI:                                │
│    │   ├─ scaleFactor = targetDPI / naturalDPI                  │
│    │   └─ Bitmap.createScaledBitmap(bmp, newW, newH, true)      │
│    └─ Re-embed at new dimensions                                │
│                                                                 │
│  Strategy 3: StripMetadata                                     │
│    └─ fz_write with opts: { no_info=1, no_xmp=1 }              │
│       Removes: Author, Creator, Producer, Subject,              │
│                CreationDate, XMP packet (~1-50KB saved)         │
│                                                                 │
│  Strategy 4: ObjectStreamCompression                           │
│    └─ fz_write with opts: { compress=1, compress_images=0 }     │
│       Flate-compresses all indirect PDF objects                 │
│       Significant for text-heavy PDFs (15-40% savings)         │
│                                                                 │
│  Strategy 5: FontSubsetting                                    │
│    ├─ Enumerate embedded fonts: fz_get_font_list(ctx, doc)     │
│    ├─ For each embedded font with full embedding:               │
│    │   ├─ Extract used Unicode codepoints from page content     │
│    │   ├─ HarfBuzz hb_subset_input_create_or_fail()            │
│    │   ├─ hb_subset(face, input) → subsetted font blob         │
│    │   └─ Replace font object in PDF with subset               │
│    └─ Savings: 50-80% per embedded font                        │
│                                                                 │
│  Final step: fz_write_document (all strategies merged)         │
│  Show user: before size, after size, % saved                   │
└─────────────────────────────────────────────────────────────────┘
```

**Realistic compression expectations:**

| PDF Type | Expected Reduction |
|---|---|
| Image-heavy PDF, JPEG quality 80→50 | 30–60% |
| Image-heavy PDF, downscale 300→150 DPI | 50–75% |
| Text PDF with full embedded fonts | 15–40% (font subsetting) |
| Already-compressed PDF | 2–8% (object streams only) |
| Mixed PDF, all strategies | 40–70% |

---

### 5.5 PDF to DOCX Conversion

> ⚠️ **Honest limitation:** Offline PDF-to-DOCX conversion is heuristic-based. It cannot match cloud ML-powered layout analysis. Quality depends heavily on PDF complexity. Users must be informed.

**Tech stack:** MuPDF text extraction → Kotlin layout analyzer → Tesseract (if scanned) → Apache POI XWPFDocument

```
Input PDF
    │
    ├─ Detect PDF type:
    │   ├─ fz_count_chars(ctx, doc, page) > 50 per page → native text PDF
    │   └─ < 50 chars per page → likely scanned → route to OCR path
    │
    ├─── NATIVE TEXT PATH:
    │   ├─ fz_new_stext_page(ctx, page, opts) ← structured text extraction
    │   ├─ Walk stext_page tree:
    │   │   ├─ fz_stext_block (text block with bounding box)
    │   │   │   └─ fz_stext_line → fz_stext_char (char + font + size + bbox)
    │   │   └─ fz_stext_block (image block → extract separately)
    │   └─ Build TextBlock list: {text, font, size, bold, italic, bbox, pageNum}
    │
    ├─── OCR PATH (if Tesseract DFM installed):
    │   ├─ fz_new_pixmap_from_page(ctx, page, fz_scale(2,2), cs, 0) ← 2x scale for OCR
    │   ├─ Feed pixmap bytes to TesseractEngine.recognize(bitmap, lang)
    │   ├─ Parse HOCR output → TextBlock list with word-level bboxes
    │   └─ If Tesseract not installed: prompt user to download OCR module
    │
    ├─ Layout Analysis Engine (Kotlin):
    │   ├─ Column detection: cluster block x-coordinates → 1 or N columns
    │   ├─ Reading order sort: by (column_band, y_position)
    │   ├─ Heading detection: fontSize > body_avg * 1.3 → heading
    │   │   fontSize ratio mapping: *1.8 → H1, *1.5 → H2, *1.3 → H3
    │   ├─ Table detection: blocks with aligned left-edges across rows
    │   ├─ List detection: blocks starting with "•", "-", "1.", etc.
    │   └─ Image block extraction: fz_get_image_from_resource
    │
    ├─ Apache POI XWPFDocument builder:
    │   ├─ For each analyzed block:
    │   │   ├─ HEADING → XWPFParagraph with StyleID "Heading1/2/3"
    │   │   ├─ BODY → XWPFParagraph, XWPFRun with bold/italic flags
    │   │   ├─ TABLE → XWPFTable with detected rows/cells
    │   │   ├─ LIST → XWPFParagraph with numbering (bullet or decimal)
    │   │   └─ IMAGE → XWPFRun.addPicture(imageBytes, PICTURE_TYPE_JPEG, w, h)
    │   └─ XWPFDocument.write(safOutputStream)
    │
    └─ Show conversion quality warning if complex layout detected
```

**Conversion quality by PDF type:**

| PDF Source Type | Layout Fidelity | Recommended Use |
|---|---|---|
| Simple text report, 1 column | 85–92% | Production use |
| Academic paper with 2 columns | 60–75% | Needs manual review |
| Magazine-style layout | 40–60% | Content extraction only |
| Scanned document (Tesseract) | 80–92% text accuracy | Check OCR errors |
| PDF with embedded tables | 65–80% | Tables may need reformatting |
| PDF with SmartArt / charts | 20–40% | Images extracted, not reconstructed |

---

### 5.6 PDF to PPTX Conversion

> ⚠️ **Honest limitation:** PDF slides cannot be reliably reconstructed as editable PPTX. This feature extracts content per page and places it as PPTX slides — it does **not** recreate the original design layout.

**Tech stack:** MuPDF page render → MuPDF text extraction → Apache POI XMLSlideShow

```
For each PDF page:
    ├─ Render to bitmap: fz_new_pixmap_from_page(ctx, page, fz_scale(1,1), cs, 0)
    ├─ Option A (Image mode): Place full-page bitmap as XSLFPictureData on slide
    │   └─ Result: visually accurate, but not editable text
    │
    └─ Option B (Text mode, default):
        ├─ Extract text blocks (same as DOCX path)
        ├─ Create XSLFSlide with blank layout
        ├─ Add XSLFTextBox for each text block at approximate position
        ├─ Scale PDF coordinate space → PPTX EMU coordinate space
        │   (PDF points → EMU: multiply by 12700)
        ├─ Embed images found on page as XSLFPictureData
        └─ Result: editable text, approximate positioning

Output: .pptx file with 1 slide per PDF page
```

---

### 5.7 DOCX / PPTX to PDF

**Tech stack:** Apache POI load → PDFBox PDDocument renderer → SAF write

```
DOCX → PDF:
    ├─ XWPFDocument docx = new XWPFDocument(contentResolver.openInputStream(uri))
    ├─ PDDocument pdf = new PDDocument()
    ├─ For each XWPFParagraph:
    │   ├─ Detect style (heading, body, list)
    │   ├─ Create PDPage (A4 default, or match docx page size)
    │   ├─ PDPageContentStream stream
    │   ├─ Load font: PDType0Font.load(pdf, fontStream, true) ← embed font
    │   ├─ stream.showText(run.getText())
    │   └─ Handle page breaks, margins from docx section properties
    ├─ For each image in docx:
    │   └─ PDImageXObject.createFromByteArray(pdf, imageBytes, "img")
    ├─ pdf.save(safOutputStream)
    └─ Limitation: complex DOCX features (SmartArt, tracked changes, macros)
       are ignored — content extracted, formatting approximated

PPTX → PDF:
    ├─ XMLSlideShow pptx = new XMLSlideShow(inputStream)
    ├─ For each XSLFSlide:
    │   ├─ Create PDPage matching slide dimensions
    │   ├─ For each shape: render text, images, basic fills
    │   └─ Complex animations, transitions: stripped (PDF is static)
    └─ pdf.save(safOutputStream)
```

---

### 5.8 Rotate & Reorder Pages

**Tech stack:** MuPDF page copy with rotation matrix → fz_write

```
Rotate pages:
    ├─ User selects pages to rotate (checkboxes on page grid)
    ├─ User selects angle: 90, 180, 270
    ├─ For each page to rotate:
    │   ├─ fz_page *page = fz_load_page(ctx, doc, pageIndex)
    │   ├─ Modify page's /Rotate entry in PDF dict
    │   │   (or apply rotation matrix to page transform)
    │   └─ fz_drop_page(ctx, page)
    ├─ fz_write_document (rewrite with rotation applied)
    └─ Pages not selected: unchanged

Reorder pages (drag-and-drop UI):
    ├─ User drags pages in a LazyVerticalGrid thumbnail view
    ├─ New order stored as List<Int> (newIndex → originalPageIndex)
    ├─ outDoc = fz_new_document(ctx)
    ├─ For newIndex in 0..newOrder.size-1:
    │   └─ fz_copy_page(ctx, outDoc, -1, srcDoc, newOrder[newIndex], 0)
    └─ fz_write_document to SAF URI
```

---

### 5.9 Extract Specific Pages

```
User inputs page range (e.g. "1, 3-5, 8, 10-end"):
    ├─ Parse → List<Int> of 0-based page indices
    ├─ Validate all indices < fz_count_pages(ctx, srcDoc)
    ├─ outDoc = fz_new_document(ctx)
    ├─ For pageIndex in selectedIndices:
    │   └─ fz_copy_page(ctx, outDoc, -1, srcDoc, pageIndex, 0)
    ├─ fz_write_document → SAF URI
    └─ Single output file containing only extracted pages
```

---

## 6. Performance & Memory Strategy

### 6.1 Memory Architecture for Large PDFs

```
Incoming PDF
    │
    ├─ Check device available RAM: ActivityManager.getMemoryInfo()
    │
    ├─ RAM < 200 MB available → EXTREME_LOW mode
    │   ├─ MuPDF context heap: 32 MB cap
    │   ├─ Process 1 page at a time, mandatory
    │   ├─ Immediate bitmap recycle after each page write
    │   └─ WorkManager will be slow but will not OOM
    │
    ├─ RAM 200–512 MB available → STANDARD mode
    │   ├─ MuPDF context heap: 64 MB cap
    │   ├─ Process 10 pages per batch
    │   └─ LRU bitmap cache: max 3 bitmaps in cache at once
    │
    └─ RAM > 512 MB available → PERFORMANCE mode
        ├─ MuPDF context heap: 128 MB cap
        ├─ Process 50 pages per batch
        └─ Thumbnail cache for page preview UI
```

### 6.2 Threading Model

```kotlin
object AppDispatchers {
    // PDF file I/O — limited parallelism to prevent I/O starvation
    val PDF_IO = Dispatchers.IO.limitedParallelism(2)

    // Image processing — CPU-bound, limited to prevent OOM from parallel large bitmaps
    val IMAGE_PROC = Dispatchers.Default.limitedParallelism(2)

    // OCR — extremely CPU-heavy, single-threaded to protect battery and RAM
    val OCR = Dispatchers.Default.limitedParallelism(1)

    // SAF write operations — standard I/O
    val SAF_IO = Dispatchers.IO
}
```

**WorkManager configuration:**

```kotlin
// In Application class — WorkManager configuration
override fun getWorkManagerConfiguration() = Configuration.Builder()
    .setMinimumLoggingLevel(Log.ERROR)
    .setWorkerFactory(hiltWorkerFactory)
    .setMaxSchedulerLimit(2)        // Max 2 concurrent workers — prevents resource contention
    .setJobSchedulerJobIdRange(1000, 2000)
    .build()
```

### 6.3 ANR Prevention Rules

All of these are enforced by StrictMode in debug builds and code review in CI:

| Rule | Enforcement |
|---|---|
| Zero file I/O on main thread | StrictMode.ThreadPolicy detects in debug |
| Zero bitmap decode on main thread | Custom lint rule: `BitmapDecodeOnMainThreadDetector` |
| All use cases are `suspend` | Enforced by interface contract |
| No `runBlocking` in Activity/Fragment/ViewModel | Detekt rule |
| No `Thread.sleep` anywhere | Detekt rule |
| WorkManager for all ops > 3 seconds | Code review gate |

### 6.4 Bitmap Safe Loading

```kotlin
// Safe large image loading — prevents OOM on all device classes
fun Context.safeDecodeBitmap(uri: Uri, targetWidthPx: Int): Bitmap? {
    // Step 1: Get dimensions without loading
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, opts)
    }

    // Step 2: Calculate safe sample size
    opts.inSampleSize = calculateInSampleSize(opts.outWidth, opts.outHeight, targetWidthPx)
    opts.inJustDecodeBounds = false
    // RGB_565 saves 50% RAM vs ARGB_8888 — acceptable for photos, not for graphics with transparency
    opts.inPreferredConfig = Bitmap.Config.RGB_565

    // Step 3: Decode at reduced size
    return contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, opts)
    }
}

// For images genuinely too large for BitmapFactory sampling (e.g., 50 MP camera shots)
fun Context.tileDecodeLargeImage(uri: Uri, tileHeightPx: Int = 512, consumer: (Bitmap) -> Unit) {
    val decoder = contentResolver.openInputStream(uri)
        ?.let { BitmapRegionDecoder.newInstance(it, false) } ?: return
    try {
        var y = 0
        while (y < decoder.height) {
            val region = Rect(0, y, decoder.width, minOf(y + tileHeightPx, decoder.height))
            val tile = decoder.decodeRegion(region, null) ?: break
            consumer(tile)
            tile.recycle()  // Explicit recycle — critical for large image processing
            y += tileHeightPx
        }
    } finally {
        decoder.recycle()
    }
}
```

### 6.5 Battery Optimization

```kotlin
// WorkManager constraints — respect battery and storage
val compressionConstraints = Constraints.Builder()
    .setRequiresBatteryNotLow(true)        // Don't run on < 20% battery (batch operations)
    .setRequiresStorageNotLow(true)        // Ensure output storage available
    .build()

// For immediate user-triggered operations (not batch):
// Use BATTERY_NOT_LOW = false — user explicitly triggered, respect their intent

// CPU performance hint (Android 12+ only, graceful degradation on older):
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    val perfManager = getSystemService(PerformanceHintManager::class.java)
    val session = perfManager?.createHintSession(
        intArrayOf(Process.myTid()),
        targetWorkDurationNanos = 16_666_666L  // 60fps equivalent
    )
    // Use session during active PDF processing, close after
}
```

---

## 7. APK Size Optimization

### 7.1 Size Budget

| Component | Size | Notes |
|---|---|---|
| Kotlin + Compose UI (after R8) | ~12 MB | |
| libmupdf.so (per ABI) | ~8 MB | Stripped, no debug, JS/XPS/SVG disabled |
| Android Framework classes | ~5 MB | |
| App resources (icons, fonts) | ~2 MB | System fonts only, WebP for raster assets |
| Apache POI lite (base, in-core) | ~4 MB | After R8 dead code elimination |
| Misc (Hilt, coroutines, etc.) | ~3 MB | All shrunk by R8 |
| **Base APK per ABI** | **~34 MB** | |
| Play Store AAB overhead | ~5 MB | |
| **Play delivery per user** | **~39 MB** | Only their ABI delivered |

| Dynamic Feature Module | Size | Trigger |
|---|---|---|
| `feature_ocr` | ~22 MB | User first requests scanned PDF processing |
| `feature_conversion` | ~16 MB | User first requests DOCX/PPTX conversion |
| `feature_signing` (Phase 4) | ~5 MB | User first opens signing screen |

**Total if all DFMs installed: ~82 MB. Well under 150 MB target.**

### 7.2 Build Configuration

```kotlin
// app/build.gradle.kts
android {
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")  // Skip x86/x86_64 for release
            isUniversalApk = false                 // No fat APK for distribution
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // Strip unused locale configs (Apache POI ships with many message bundles)
    defaultConfig {
        // Add more as community contributions add translations
        resConfigs("en", "hi", "ar", "zh", "es", "fr", "de", "pt", "ru", "ja")
    }
}

// Use poi-ooxml-lite instead of full poi-ooxml (80% smaller)
dependencies {
    implementation("org.apache.poi:poi-ooxml-lite:5.2.5") {
        // Exclude ooxml-schemas — replace with lite version
        exclude(group = "org.apache.poi", module = "poi-ooxml-schemas")
    }
    // Also exclude unused XML binding schemas
    configurations.all {
        exclude(group = "xml-apis", module = "xml-apis")
        exclude(group = "stax", module = "stax-api")
    }
}
```

```pro
# proguard-rules.pro — Critical rules
# Keep JNI-called methods (MuPDF JNI bridge)
-keepclassmembers class dev.pdfforge.engine.mupdf.MuPdfJni {
    native <methods>;
}
# Keep Apache POI reflection-accessed classes
-keep class org.apache.poi.** { *; }
-dontwarn org.apache.poi.**
# Keep WorkManager workers
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }
```

### 7.3 F-Droid / GitHub Release APK Strategy

Since this is always open-source, F-Droid distribution is important:

```bash
# scripts/build_release_apks.sh
# Builds per-ABI APKs for GitHub Releases and F-Droid

./gradlew assembleRelease

# Resulting APKs:
# app/build/outputs/apk/release/app-armeabi-v7a-release.apk  (~34 MB)
# app/build/outputs/apk/release/app-arm64-v8a-release.apk    (~38 MB)

# F-Droid metadata in: fastlane/metadata/android/
# F-Droid build recipe in: .github/f-droid/build.yml
```

---

## 8. Security & Privacy

### 8.1 Threat Model

| Threat | Attack Vector | Mitigation | Residual Risk |
|---|---|---|---|
| Malicious PDF exploit | Crafted PDF triggers buffer overflow in MuPDF parser | fz_try/fz_catch around ALL MuPDF calls; process isolation; ASLR on Android; magic bytes validation before any parsing | Low — MuPDF has active security maintenance |
| ZIP bomb / decompression bomb | PDF with deeply nested compressed streams | fz_context memory cap (64 MB heap limit); 500 MB total input size limit; kill worker if over threshold | Low |
| Temp file data leak | App crashes before cleanup, sensitive temp data persists | TempFileManager uses finally block; WorkManager completion callback deletes temps; all temps in getCacheDir() (app-private) | Very Low |
| Path traversal via URI | Malicious URI escaping out of sandbox | SAF ContentResolver used exclusively — no raw file path handling; URI validation before use | None — SAF prevents this by design |
| Network exfiltration | App sends file bytes to remote server | `INTERNET` permission not declared in manifest; verified via Network Security Config; NDK code has no socket APIs | None |
| Unauthorized file access | App reads files beyond user's selection | SAF permission grants are per-URI, scoped. `READ_EXTERNAL_STORAGE` not requested on API 33+. No `MANAGE_EXTERNAL_STORAGE`. | None — by design |
| Memory-resident sensitive content | Decrypted PDF content lingers in RAM | Bitmap.recycle() called immediately after use; WeakReference for page thumbnails; no LRU cache for sensitive docs | Low — Android eventually GCs |

### 8.2 Manifest Permissions (Complete, Exhaustive)

```xml
<!-- AndroidManifest.xml — declared permissions, FULL LIST -->

<!-- Android 9 and below: needed for writing to Downloads -->
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="28"/>

<!-- Android 12 and below: needed for file picker integration -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32"/>

<!-- Android 13+: Uses SAF and photo picker exclusively — ZERO storage permissions needed -->

<!-- Foreground service for progress notification on long operations -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC"/>

<!-- EXPLICITLY NOT DECLARED — for auditability: -->
<!-- android.permission.INTERNET — app has ZERO network capability -->
<!-- android.permission.ACCESS_NETWORK_STATE -->
<!-- android.permission.READ_PHONE_STATE -->
<!-- android.permission.CAMERA -->
<!-- android.permission.MANAGE_EXTERNAL_STORAGE -->
<!-- android.permission.READ_MEDIA_IMAGES — use photo picker API instead -->
```

### 8.3 File Validation Layer

```kotlin
// :data:impl — FileValidator — executed before ANY engine processing
class FileValidator @Inject constructor() {

    private val PDF_MAGIC = byteArrayOf(0x25, 0x50, 0x44, 0x46) // %PDF

    fun validate(uri: Uri, context: Context): ValidationResult {
        val fileSize = context.contentResolver.query(uri, arrayOf(
            OpenableColumns.SIZE), null, null, null
        )?.use { cursor ->
            cursor.moveToFirst()
            cursor.getLong(0)
        } ?: return ValidationResult.Error(ErrorCode.CANNOT_STAT_FILE)

        if (fileSize < 512L)
            return ValidationResult.Error(ErrorCode.FILE_TOO_SMALL)
        if (fileSize > 500 * 1024 * 1024L)  // 500 MB limit
            return ValidationResult.Error(ErrorCode.FILE_TOO_LARGE)

        // Check magic bytes
        val magic = ByteArray(4)
        context.contentResolver.openInputStream(uri)?.use { it.read(magic) }
        if (!magic.contentEquals(PDF_MAGIC))
            return ValidationResult.Error(ErrorCode.NOT_A_PDF)

        return ValidationResult.Valid(fileSize)
    }
}
```

### 8.4 Temp File Lifecycle

```kotlin
// :data:impl — TempFileManager — strict lifecycle management
class TempFileManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val activeFiles = ConcurrentHashMap<String, File>()

    fun createTemp(prefix: String, suffix: String): File {
        val file = File.createTempFile(prefix, suffix, context.cacheDir)
        activeFiles[file.name] = file
        return file
    }

    fun delete(file: File) {
        file.delete()
        activeFiles.remove(file.name)
    }

    // Called from Application.onTrimMemory and WorkManager completion
    fun deleteAll() {
        activeFiles.values.forEach { it.delete() }
        activeFiles.clear()
        // Also sweep cacheDir for any orphaned temps (from crash recovery)
        context.cacheDir.listFiles()?.forEach { if (it.name.startsWith("pdfforge_")) it.delete() }
    }
}
```

---

## 9. Testing Strategy

### 9.1 Testing Pyramid

```
                    ┌──────────┐
                    │  E2E/UI  │  10% — Espresso/Compose UI + Macrobenchmark
                    └─────┬────┘
              ┌───────────┴───────────┐
              │   Integration Tests   │  20% — Robolectric + TestWorkerBuilder
              └───────────┬───────────┘
        ┌─────────────────┴─────────────────┐
        │           Unit Tests              │  70% — Pure JVM, fast, comprehensive
        └───────────────────────────────────┘
```

### 9.2 Unit Tests — Domain Layer

All domain use cases are pure Kotlin. Tests run in milliseconds with no device.

```kotlin
// :domain:core/test — MergePdfUseCaseTest.kt
class MergePdfUseCaseTest {
    private val mockEngine = mockk<PdfEngine>()
    private val mockRepo = mockk<PdfRepository>()
    private val useCase = MergePdfUseCase(mockRepo)

    @Test
    fun `merge fails when less than 2 pdfs provided`() = runTest {
        val result = useCase.execute(MergeParams(uris = listOf(singleUri)))
        assertThat(result).isInstanceOf(OperationResult.Error::class.java)
        assertThat((result as OperationResult.Error).code)
            .isEqualTo(ErrorCode.INSUFFICIENT_INPUT)
    }

    @Test
    fun `merge emits progress events in order`() = runTest {
        val progressEvents = mutableListOf<Int>()
        coEvery { mockRepo.mergeDocuments(any(), any()) } coAnswers {
            // Simulate 3 progress callbacks
            firstArg<(Int) -> Unit>().invoke(33)
            firstArg<(Int) -> Unit>().invoke(66)
            firstArg<(Int) -> Unit>().invoke(100)
            OperationResult.Success(mockOutputUri)
        }
        useCase.execute(MergeParams(listOf(uri1, uri2)))
        assertThat(progressEvents).containsExactly(33, 66, 100).inOrder()
    }
}
```

### 9.3 Engine Wrapper Tests — Mocked JNI

```kotlin
// :engine:mupdf/test — MuPdfEngineTest.kt
class MuPdfEngineTest {
    private val mockJni = mockk<MuPdfJni>()
    private val engine = MuPdfEngine(mockJni, FakeTempFileManager())

    @Test
    fun `openDocument returns error when JNI returns 0 handle`() = runTest {
        every { mockJni.openFromFd(any()) } returns 0L
        val result = runCatching { engine.openDocument(testUri, testContext) }
        assertThat(result.exceptionOrNull()).isInstanceOf(PdfEngineException::class.java)
        assertThat((result.exceptionOrNull() as PdfEngineException).code)
            .isEqualTo(ErrorCode.INVALID_PDF)
    }
}
```

### 9.4 Fuzz Testing — Corrupted PDF Corpus

```kotlin
// :engine:mupdf/test — FuzzCorpusTest.kt
@RunWith(Parameterized::class)
class FuzzCorpusTest(private val pdfFile: File) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun pdfCorpus(): List<File> {
            // corpus/ contains: valid PDFs, truncated PDFs, wrong magic bytes,
            // zero-byte files, PDF with corrupted XRef, PDF with null objects,
            // PDF with circular references, maximum depth object trees
            return File("src/test/resources/corpus").listFiles()!!.toList()
        }
    }

    @Test
    fun `engine never throws uncaught exception on corpus input`() {
        // Must return OperationResult.Error, must NEVER crash the process
        val result = runCatching {
            val engine = MuPdfEngine(realJni, FakeTempFileManager())
            engine.openDocument(pdfFile.toUri(), testContext)
        }
        // Either succeeds or throws PdfEngineException — never anything else
        if (result.isFailure) {
            assertThat(result.exceptionOrNull()).isInstanceOf(PdfEngineException::class.java)
        }
    }
}
```

**Corpus sources for fuzz testing:**
- Mozilla PDF.js test suite (PDFs)
- pdfium test corpus (from Chromium)
- Hand-crafted edge cases: truncated at byte 100, 500, 1000; null XRef table; circular object references; deeply nested streams

### 9.5 Performance Benchmarks

```kotlin
// :benchmarks — macrobenchmark module
class StartupBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    @Test
    fun coldStartup() = rule.measureRepeated(
        packageName = "dev.pdfforge",
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD
    ) {
        pressHome()
        startActivityAndWait()
    }
    // Target: cold startup < 800ms on mid-range device (Pixel 4a equivalent)
}

class PdfOperationBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    @Test
    fun merge10PDFsOf10Pages() = rule.measureRepeated(
        packageName = "dev.pdfforge",
        metrics = listOf(FrameTimingMetric(), TraceSectionMetric("PDF_MERGE")),
        iterations = 3,
        startupMode = StartupMode.WARM
    ) {
        // Trigger merge via intents, measure trace section duration
        // Target: 10 × 10-page PDFs merged in < 3s on mid-range device
    }
}
```

### 9.6 CI Test Matrix

```yaml
# .github/workflows/test.yml
strategy:
  matrix:
    api-level: [29, 31, 33, 34]  # Android 10, 12, 13, 14
    target: [default]
    arch: [x86_64]
    # Note: Run arm64 tests on physical device farm monthly (not every PR — slow)
```

---

## 10. Open Source Strategy

### 10.1 License: AGPL-3.0

This project is **permanently open-source**. AGPL-3.0 is chosen because:

1. **MuPDF compatibility**: MuPDF is AGPL-3.0. The app must be AGPL-3.0 (or hold a commercial Artifex license). Since the app is always open-source, AGPL is natural.
2. **Copyleft protection**: Prevents any actor from forking, closing the source, and distributing commercially without contributing back. This protects the community.
3. **User freedom**: AGPL guarantees users can always access the source, modify it, and redistribute modified versions — as long as they also release source.
4. **No impact on users**: End users are not distributing the app — they just use it. AGPL places no obligation on users.

**Per-module license strategy:**

| Module | License | Reason |
|---|---|---|
| `:engine:mupdf` | AGPL-3.0 | MuPDF itself is AGPL |
| All other modules | Apache-2.0 | Contributors may prefer permissive for pure-Kotlin modules |
| `:common:utils`, `:common:ui` | Apache-2.0 | Useful standalone, permissive allows broader reuse |
| App as a whole | AGPL-3.0 | Aggregate of AGPL and Apache-2.0 components |

Every source file begins with:

```kotlin
/*
 * PDFForge Android
 * Copyright (C) 2024 PDFForge Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * See LICENSE for the full license text.
 */
```

### 10.2 CI/CD Pipeline

```yaml
# .github/workflows/ci.yml

name: CI

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

jobs:
  lint-and-analyze:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '17', distribution: 'temurin' }
      - run: ./gradlew lint detekt
      - run: ./gradlew licenseReleaseReport  # FOSSA-compatible license scan
      # Fail build if any non-AGPL-compatible license detected

  unit-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with: { submodules: true }  # MuPDF is a git submodule
      - run: ./gradlew testReleaseUnitTest
      - uses: actions/upload-artifact@v4
        with:
          name: unit-test-results
          path: '**/build/reports/tests/'

  build-release:
    needs: [lint-and-analyze, unit-tests]
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with: { submodules: true }
      - run: ./gradlew assembleRelease  # Per-ABI APKs
      - name: Sign APKs
        uses: r0adkll/sign-android-release@v1
        with:
          releaseDirectory: app/build/outputs/apk/release
          signingKeyBase64: ${{ secrets.SIGNING_KEY }}
          alias: ${{ secrets.KEY_ALIAS }}
          keyStorePassword: ${{ secrets.KEY_STORE_PASSWORD }}

  instrumentation-tests:
    # Only runs on main branch — slow, uses emulator
    if: github.ref == 'refs/heads/main'
    runs-on: ubuntu-latest
    steps:
      - uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: 33
          script: ./gradlew connectedAndroidTest
```

### 10.3 Contribution Guidelines

**Repository structure for contributors:**

```
CONTRIBUTING.md          ← Start here
docs/
├── ARCHITECTURE.md      ← This document (system design)
├── ENGINE_INTEGRATION.md← How to add a new PDF engine
├── TOOL_PLUGIN_GUIDE.md ← How to add a new tool in 5 steps
├── BUILDING.md          ← How to build from source, including MuPDF
└── TESTING.md           ← How to run tests, add to corpus
```

**Good first issues strategy:**

Label issues with:
- `good-first-issue`: Isolated tasks, no NDK knowledge needed (UI, utility functions)
- `engine`: Requires JNI/NDK knowledge
- `conversion`: Requires Apache POI / PDFBox knowledge
- `testing`: Corpus expansion, new test cases

**PR review requirements:**
- All PRs require 1 code owner approval
- PRs touching `:engine:mupdf` require NDK-knowledgeable reviewer
- Automated checks must pass: lint, detekt, unit tests, license scan
- No PR merges if test coverage of changed domain classes drops below 80%

### 10.4 Versioning

Semantic versioning: `MAJOR.MINOR.PATCH`

- `MAJOR`: Breaking change to tool plugin API or engine interface (community must update plugins)
- `MINOR`: New features, backward-compatible
- `PATCH`: Bug fixes, performance improvements

Version managed in `libs.versions.toml`:
```toml
[versions]
pdfforge = "1.0.0"
```

Build number in CI: `versionCode = github.run_number` (monotonically increasing)

---

## 11. Folder Structure

```
pdfforge-android/
│
├── app/                                   # Application shell
│   ├── src/main/
│   │   ├── java/dev/pdfforge/app/
│   │   │   ├── PdfForgeApplication.kt     # Hilt app, TempFileManager init, WorkManager config
│   │   │   ├── MainActivity.kt            # Single activity host
│   │   │   └── navigation/
│   │   │       └── AppNavGraph.kt         # Root navigation graph, DFM deep links
│   │   ├── res/
│   │   │   ├── values/strings.xml
│   │   │   └── xml/network_security_config.xml  # Blocks all network (defense-in-depth)
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts                   # ABI splits, signing config, DFM declarations
│
├── feature/
│   ├── pdf_creation/                      # Image→PDF, page reorder, rotate
│   │   └── src/main/java/dev/pdfforge/feature/creation/
│   │       ├── CreationViewModel.kt
│   │       ├── ImageToPdfScreen.kt        # File picker, image grid, options
│   │       ├── ReorderPagesScreen.kt      # Drag-drop LazyGrid
│   │       └── RotatePagesScreen.kt
│   │
│   ├── compression/                       # Strategy selector + progress
│   │   └── src/main/java/dev/pdfforge/feature/compression/
│   │       ├── CompressionViewModel.kt
│   │       ├── CompressionScreen.kt       # Strategy toggles, quality slider
│   │       └── CompressionResultScreen.kt # Before/after size comparison
│   │
│   ├── conversion/                        # PDF↔DOCX/PPTX
│   │   └── src/main/java/dev/pdfforge/feature/conversion/
│   │       ├── ConversionViewModel.kt
│   │       ├── ConversionScreen.kt        # Format selector, DFM download prompt
│   │       └── ConversionLimitationsScreen.kt  # Honest quality disclosure
│   │
│   └── merge_split/                       # Merge N PDFs, split by mode
│       └── src/main/java/dev/pdfforge/feature/mergesplit/
│           ├── MergeSplitViewModel.kt
│           ├── MergeScreen.kt             # PDF list with drag-reorder handles
│           └── SplitScreen.kt             # Mode selector, range input, bookmark list
│
├── domain/
│   ├── core/
│   │   └── src/main/java/dev/pdfforge/domain/
│   │       ├── tool/
│   │       │   ├── PdfTool.kt             # Plugin interface
│   │       │   └── ToolCategory.kt
│   │       ├── usecase/
│   │       │   ├── CreatePdfFromImagesUseCase.kt
│   │       │   ├── MergePdfUseCase.kt
│   │       │   ├── SplitPdfUseCase.kt
│   │       │   ├── CompressPdfUseCase.kt
│   │       │   ├── ConvertPdfUseCase.kt
│   │       │   ├── RotatePagesUseCase.kt
│   │       │   └── ExtractPagesUseCase.kt
│   │       └── repository/
│   │           └── PdfRepository.kt       # Interface — no impl here
│   │
│   └── models/
│       └── src/main/java/dev/pdfforge/domain/model/
│           ├── PdfDocument.kt             # page count, size, metadata
│           ├── PageInfo.kt                # dimensions, rotation, thumbnail uri
│           ├── OperationResult.kt         # sealed: Success<T>, Error, Cancelled
│           ├── ErrorCode.kt              # sealed: all possible engine errors
│           ├── CompressionStrategy.kt     # sealed: per-strategy params
│           └── ToolParams.kt             # sealed: params per tool
│
├── data/
│   ├── impl/
│   │   └── src/main/java/dev/pdfforge/data/
│   │       ├── PdfRepositoryImpl.kt       # Wires use cases to engines via DI
│   │       ├── SafFileAdapter.kt          # ContentResolver wrapper — all file I/O
│   │       ├── FileValidator.kt           # Magic bytes + size + MIME validation
│   │       └── TempFileManager.kt         # Scoped temp file lifecycle
│   │
│   └── storage/
│       └── src/main/java/dev/pdfforge/data/storage/
│           ├── UserPreferencesDataStore.kt
│           └── UserPreferences.kt         # Default quality, DPI, output naming, etc.
│
├── engine/
│   ├── mupdf/                             # MuPDF JNI bindings
│   │   ├── src/main/
│   │   │   ├── java/dev/pdfforge/engine/mupdf/
│   │   │   │   ├── MuPdfEngine.kt         # Kotlin API — only this is used by domain
│   │   │   │   ├── MuPdfJni.kt           # JNI declarations (external fun)
│   │   │   │   └── DocHandle.kt          # Resource wrapper: fz_document + FileDescriptor
│   │   │   └── cpp/
│   │   │       ├── mupdf_bridge.cpp       # JNI implementation — thin wrappers
│   │   │       └── CMakeLists.txt
│   │   ├── libs/
│   │   │   ├── armeabi-v7a/libmupdf.a    # Pre-built static lib (or built from submodule)
│   │   │   └── arm64-v8a/libmupdf.a
│   │   └── build.gradle.kts              # CMake config, ABI filters
│   │
│   ├── pdfbox/                            # Apache PDFBox JVM wrapper
│   │   └── src/main/java/dev/pdfforge/engine/pdfbox/
│   │       ├── PdfBoxEngine.kt
│   │       └── MetadataEditor.kt
│   │
│   ├── imageproc/                         # Bitmap processing
│   │   └── src/main/java/dev/pdfforge/engine/imageproc/
│   │       ├── BitmapProcessor.kt         # Scale, rotate, format convert
│   │       └── ImageCompressor.kt         # JPEG quality re-encoding
│   │
│   ├── converter/                         # Apache POI DOCX/PPTX builder
│   │   └── src/main/java/dev/pdfforge/engine/converter/
│   │       ├── DocxBuilder.kt             # XWPFDocument assembly
│   │       ├── PptxBuilder.kt             # XMLSlideShow assembly
│   │       ├── LayoutAnalyzer.kt          # PDF text block → document structure heuristics
│   │       └── FontMapper.kt             # PDF font names → POI font styles
│   │
│   └── tesseract/                         # Tesseract NDK OCR wrapper
│       ├── src/main/
│       │   ├── java/dev/pdfforge/engine/tesseract/
│       │   │   ├── TesseractEngine.kt
│       │   │   └── OcrResult.kt          # Hocr parse output
│       │   └── cpp/
│       │       └── tesseract_bridge.cpp
│       └── assets/
│           └── tessdata/eng.traineddata   # English language pack (~10 MB)
│
├── common/
│   ├── ui/
│   │   └── src/main/java/dev/pdfforge/common/ui/
│   │       ├── theme/
│   │       │   ├── Theme.kt               # MaterialTheme, dark/light
│   │       │   ├── Color.kt
│   │       │   └── Type.kt
│   │       └── components/
│   │           ├── ToolCard.kt            # Home screen tool card
│   │           ├── FilePickerButton.kt    # SAF-backed file picker
│   │           ├── ProgressScreen.kt      # Reusable progress + cancel UI
│   │           ├── ErrorDialog.kt
│   │           └── DragHandle.kt          # For reorder lists
│   │
│   └── utils/
│       └── src/main/java/dev/pdfforge/common/utils/
│           ├── AppDispatchers.kt          # Coroutine dispatcher constants
│           ├── FlowExtensions.kt
│           ├── UriExtensions.kt          # SAF URI helpers
│           └── ByteSizeFormatter.kt      # "3.2 MB" formatting
│
├── dynamic_features/
│   ├── feature_ocr/                       # DFM: OCR (Tesseract + language data)
│   │   └── src/main/java/dev/pdfforge/feature/ocr/
│   │       └── OcrToolImpl.kt            # Implements PdfTool, uses :engine:tesseract
│   │
│   └── feature_conversion/               # DFM: DOCX/PPTX conversion (Apache POI)
│       └── src/main/java/dev/pdfforge/feature/conversion/impl/
│           └── ConversionToolImpl.kt
│
├── benchmarks/                            # Macrobenchmark module
│   └── src/main/java/dev/pdfforge/benchmarks/
│       ├── StartupBenchmark.kt
│       ├── PdfOperationBenchmark.kt
│       └── MemoryBenchmark.kt
│
├── buildSrc/
│   └── src/main/kotlin/
│       ├── convention/
│       │   ├── AndroidLibraryConventionPlugin.kt
│       │   ├── AndroidFeatureConventionPlugin.kt
│       │   ├── EngineModuleConventionPlugin.kt   # Adds NDK config defaults
│       │   └── TestConventionPlugin.kt
│       └── Dependencies.kt               # (Superseded by libs.versions.toml — legacy)
│
├── gradle/
│   └── libs.versions.toml                # Single source of truth for all versions
│
├── scripts/
│   ├── build_mupdf.sh                    # Compile MuPDF from source for all ABIs
│   ├── build_tesseract.sh                # Compile Tesseract NDK
│   └── generate_license_report.sh        # FOSSA-compatible license report
│
├── docs/
│   ├── ARCHITECTURE.md                   # = This document
│   ├── BUILDING.md                       # Build from source guide
│   ├── CONTRIBUTING.md                   # Contribution guide
│   ├── ENGINE_INTEGRATION.md             # How to swap/add engines
│   ├── TOOL_PLUGIN_GUIDE.md             # How to add a new tool (5 steps)
│   └── ROADMAP.md                        # Phase 1–4 roadmap
│
├── fastlane/
│   └── metadata/android/                 # F-Droid + Play Store metadata
│       ├── en-US/
│       │   ├── full_description.txt
│       │   └── short_description.txt
│       └── changelogs/
│
├── .github/
│   ├── workflows/
│   │   ├── ci.yml
│   │   ├── release.yml
│   │   └── f-droid-check.yml
│   ├── ISSUE_TEMPLATE/
│   │   ├── bug_report.md
│   │   └── feature_request.md
│   └── PULL_REQUEST_TEMPLATE.md
│
├── CHANGELOG.md
├── LICENSE                               # AGPL-3.0 full text
├── NOTICE                                # Third-party license attributions
└── README.md
```

---

## 12. Risk & Tradeoffs

### 12.1 Risk Register

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| MuPDF AGPL misunderstood by contributors | Medium | Medium | Clear `CONTRIBUTING.md`, FOSSA CI enforcement, per-file license headers |
| PDF→DOCX quality complaints from users | High | Medium | Proactive UI warnings, "best effort" labeling, raw text extraction fallback |
| APK size creep over time as features added | Medium | Low | APK size tracked in CI (fail build if base APK exceeds 80 MB limit) |
| Native crash from malformed PDF | Low | High | fz_try/fz_catch isolation, process isolation, crash caught as PdfEngineException |
| OOM on low-RAM devices (2 GB) | Medium | High | Adaptive memory strategy, WorkManager retry with halved batch |
| Apache POI OOM on large DOCX | Medium | Medium | SXSSF streaming API for output, 100 MB input size limit for conversion |
| Tesseract accuracy disappointment | Medium | Low | Per-language accuracy disclosure, quality confidence score shown to user |
| Dynamic Feature Module download fails (no network) | Medium | Low | Graceful fallback: show "feature requires one-time download" with clear network indicator |
| MuPDF upstream security vulnerability | Low | High | Subscribe to Artifex security advisories, automated Dependabot-equivalent for submodule |
| Community fork that violates AGPL | Low | Low | AGPL enforcement requires legal action; having clear license and NOTICE files is sufficient |

### 12.2 Offline Conversion — Honest Quality Analysis

This is the most important section for setting realistic user expectations.

```
PDF Feature                    | Offline Fidelity | Root Limitation
───────────────────────────────┼──────────────────┼─────────────────────────────────────────
Native text, single column     | 85–93%           | Font name mapping may differ
Native text, multi-column      | 60–75%           | Column detection is bbox-heuristic, not ML
Tables (lines visible)         | 70–80%           | Table cell detection via grid lines
Tables (whitespace-only)       | 40–60%           | No visual cues — hard to detect
Scanned text (Tesseract)       | 85–95%           | LSTM accuracy on clean printed text
Scanned handwriting            | 30–60%           | Tesseract not trained for general handwriting
Scanned non-Latin scripts      | 75–88%           | Varies by language traineddata quality
Embedded images                | 99%              | Direct binary extraction — no recompression
Hyperlinks in text             | 50%              | URL pattern matching, not full XObject parsing
Footnotes / endnotes           | 40–65%           | Position-based detection, often missed
Mathematical formulas          | 20–40%           | No LaTeX reconstruction — images only
SmartArt / charts              | 10–30%           | Rendered as images, not editable
PDF forms → DOCX fields        | 0%               | AcroForm→DOCX field mapping not implemented
Page headers/footers           | 60–75%           | Position detection, may merge into body

PDF→PPTX (any type)           | 30–55%           | Slide layout reconstruction is extremely hard
DOCX→PDF (simple)             | 88–95%           | Font embedding reliable, layout good
DOCX→PDF (complex tables)     | 70–85%           | Complex nested tables may misalign
PPTX→PDF (basic)              | 80–92%           | Static slide content rendered well
PPTX→PDF (animations)         | N/A              | Animations stripped — PDF is static
```

**The only production-quality operations (>98% fidelity):**
- Image to PDF
- PDF Merge
- PDF Split
- Page rotation
- PDF page reorder
- PDF compression (file size reduction — not content change)
- Image extraction from PDF

Everything involving content re-interpretation is heuristic and users must know this.

### 12.3 Known Technical Limitations

1. **Password-protected PDFs**: MuPDF can open password-protected PDFs if the password is provided. Phase 1 does not implement a password dialog — added in Phase 3.

2. **Right-to-left text (Arabic, Hebrew)**: Extraction works via MuPDF. POI rendering in DOCX may have RTL direction issues. HarfBuzz handles shaping correctly in the PDF path but not in POI output path.

3. **CJK fonts in conversion**: PDFs embed CJK fonts as subsets. Reconstruction in DOCX requires the host system to have compatible fonts installed. On most Android devices, Noto CJK fonts are available.

4. **JavaScript in PDFs**: MuPDF's JS interpreter is disabled in our build (reduces size, eliminates attack surface). PDFs with JS-triggered content will have that content unavailable.

5. **PDF 2.0 features**: MuPDF supports PDF 2.0. Some very new PDF 2.0 features (e.g., associated files, rich media) are not exposed through our JNI bridge in Phase 1.

---

## 13. Long-Term Maintainability

### 13.1 API Surface Management

**Internal APIs** (within modules) can change freely. **Cross-module APIs** (domain interfaces, PdfTool contract, engine interfaces) are versioned and documented.

When a cross-module API must break:
1. Deprecate the old API with `@Deprecated(replaceWith = ReplaceWith(...))`
2. Ship old + new in the same release for 1 MINOR version
3. Remove in the next MINOR version
4. Document in `CHANGELOG.md` under `### Breaking Changes`

### 13.2 Backward Compatibility

- **Minimum API 29**: Never raise this without a `MAJOR` version bump and community discussion
- **WorkManager data format**: If WorkData input/output format changes, old queued workers must be gracefully migrated (don't just crash)
- **DataStore schema**: Use `DataStore.Migration` for any proto schema changes
- **DFM API**: The interface a DFM implements must be backward-compatible — DFMs should not need updating every release

### 13.3 Dependency Update Strategy

```toml
# gradle/libs.versions.toml — version policy comments

[versions]
# MuPDF: Update only after security review of Artifex changelog
# Pin to specific commit hash in git submodule for reproducible builds
mupdf-commit = "abc123def"  # Update manually after review

# AndroidX: Update freely — Google maintains compatibility
compose-bom = "2024.01.00"  # Track BOM, not individual versions

# Apache POI: Update on minor versions. Test with corpus on major updates.
poi = "5.2.5"

# Tesseract: Rarely updated. Only update for accuracy improvements or security.
tesseract = "5.3.3"
```

### 13.4 Documentation Maintenance

- `ARCHITECTURE.md` (this file): Updated with every `MINOR` version PR
- `TOOL_PLUGIN_GUIDE.md`: Updated whenever `PdfTool` interface changes
- `ENGINE_INTEGRATION.md`: Updated when new engine is added or JNI bridge changes
- All public Kotlin APIs: KDoc on all `interface`, `data class`, and `sealed class` declarations
- NDK bridge functions: Doxygen comments on all `extern "C"` JNI functions

### 13.5 Community Governance

Since this is permanently open-source, governance matters as the community grows:

- **Maintainers group**: Initially project founders. Expand to active contributors after 6+ months of quality PRs.
- **Decision making**: Lazy consensus on implementation details. Discussion issue for architectural changes before PR.
- **Code of conduct**: Contributor Covenant (standard, well-understood).
- **Security reports**: Private disclosure via `security@pdfforge.dev` (or GitHub private vulnerability reporting). 90-day disclosure timeline.
- **Release cadence**: Monthly patch releases, quarterly minor releases, no fixed major release schedule.

---

## 14. Final Recommendation Stack

```
┌──────────────────────────────────────────────────────────────────────────┐
│                     PDFFORGE FINAL TECH STACK                            │
│                     Permanently Open-Source · AGPL-3.0                  │
├─────────────────────┬────────────────────────────────────────────────────┤
│ Language            │ Kotlin 1.9+ (all) · C/C++17 via NDK r26 (engine)  │
│ PDF Engine          │ MuPDF (NDK) — primary · Apache PDFBox 3.x (structural) │
│ DOCX/PPTX          │ Apache POI 5.x poi-ooxml-lite                       │
│ OCR                 │ Tesseract 5.x NDK (Dynamic Feature Module)         │
│ Font subsetting     │ HarfBuzz (via MuPDF NDK)                           │
│ Image processing    │ Android Bitmap API · BitmapRegionDecoder            │
│ Background work     │ WorkManager 2.9+ · Kotlin Coroutines               │
│ Dependency injection│ Hilt 2.48+                                         │
│ UI                  │ Jetpack Compose 1.5+ · Material 3                  │
│ Navigation          │ Navigation Compose 2.7+ (type-safe)                │
│ Storage             │ Android SAF · DataStore Preferences                │
│ Architecture        │ Clean Architecture · Modular Gradle                │
│ Plugin system       │ PdfTool interface — plugin-style tool registration  │
│ APK distribution    │ AAB (Play) · Per-ABI APK (F-Droid, GitHub)         │
│ Min SDK             │ API 29 (Android 10)                                │
│ Target APK size     │ Base < 75 MB per ABI · < 82 MB with all DFMs       │
│ License             │ AGPL-3.0 — open-source, forever, no exceptions     │
│ CI/CD               │ GitHub Actions · FOSSA license scan · Detekt        │
│ Testing             │ JUnit 5 · MockK · Robolectric · Macrobenchmark     │
└─────────────────────┴────────────────────────────────────────────────────┘
```

### 14.1 What This Stack Guarantees

- ✅ **100% offline** — `INTERNET` permission absent from manifest
- ✅ **No tracking** — no analytics, no telemetry, no crash reporting requiring network
- ✅ **No forced storage** — all output via SAF to user-chosen location
- ✅ **500+ page PDFs** — streaming page processing, adaptive memory
- ✅ **2 GB RAM devices** — adaptive memory strategy, bitmap pooling
- ✅ **Open to contributions** — plugin tool system, clean module boundaries, comprehensive docs
- ✅ **F-Droid eligible** — AGPL-3.0, reproducible builds, no proprietary SDKs, no tracking
- ✅ **Play Store eligible** — all Google Play policies met, DFM support
- ✅ **Scalable** — millions of users, all processing local, no backend to scale

### 14.2 What This Stack Cannot Guarantee

- ❌ **Perfect PDF→DOCX conversion** — heuristic layout analysis only. Cloud ML would improve this but violates offline constraint.
- ❌ **Handwriting OCR** — Tesseract is not reliable for handwriting. Offline handwriting recognition models exist but are 50–100 MB each.
- ❌ **PDF 3D / rich media** — Not supported by MuPDF in our config (disabled to reduce size).
- ❌ **Real-time collaboration** — Requires network. Out of scope forever.
- ❌ **AI-powered features** (summarization, chat, etc.) — Out of scope unless a fully offline on-device LLM integration is viable in future Android hardware. Not Phase 1–4.

---

*PDFForge Android — Production System Design*  
*Open-source, forever. No cloud. No tracking. No excuses.*  
*AGPL-3.0 · Contributions welcome · Built for everyone*
