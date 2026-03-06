# PDF Forger — Implementation Audit Report

**Date:** March 6, 2025  
**Scope:** Full codebase review, line-level analysis  
**Purpose:** Rate current implementation, identify what works, what is dummy/unwritten, and blocking issues.

---

## Executive Summary

| Metric | Assessment |
|--------|------------|
| **Overall implementation** | **~35–40%** complete (structure in place, many paths stubbed or broken) |
| **Build status** | **Will not compile** as-is (missing modules, wrong imports, missing types) |
| **What will run** | Only after fixing blocking issues: Home → Image to PDF / Merge / Compress flows can run in principle; Convert/Split/Reorder are partial or placeholder |
| **Critical bugs** | Wrong `OperationResult` package across 14+ files; missing `ConvertPdfParams`/`ConvertPdfTool`; missing `build.gradle.kts` for `:data:impl` and `:engine:mupdf`; missing Coil dependency; use-case validation not returning on failure |

---

## 1. Build & Module Configuration

### 1.1 Missing build files (blocking)

| Module | Status | Impact |
|--------|--------|--------|
| **`:data:impl`** | **No `build.gradle.kts`** | `settings.gradle.kts` includes it; Gradle will fail configuring the project. |
| **`:engine:mupdf`** | **No `build.gradle.kts`** | Same; module is included but has no build script (only `src/` and `.cxx/`). |

**Conclusion:** A full `./gradlew assembleDebug` will fail at project configuration for `:data:impl` and `:engine:mupdf`.

### 1.2 App module dependencies

- **App** depends on: `:domain:models`, `:domain:core`, **`:data:impl`**, `:data:storage`, `:common:ui`, `:common:utils`.
- App does **not** depend on `:engine:mupdf` or `:engine:converter`.  
- `EngineModule` (Hilt) lives in `engine/mupdf` and binds `ImageToPdfTool`, `MergePdfTool`, `CompressPdfTool`, `ConvertPdfTool`.  
- So even if the build succeeded, **Hilt would not see `EngineModule`** unless the app (or a shared layer) depends on the engine modules. **DI would fail at runtime** for all tools.

### 1.3 MuPDF native (engine/mupdf)

- **`engine/mupdf/src/main/cpp/CMakeLists.txt`**: Builds `mupdf_bridge` shared library; **does not link MuPDF**. Comment says: *"MuPDF static library placeholder"*.
- **`mupdf-bridge.cpp`**: Includes `mupdf/fitz.h` and `mupdf/pdf.h` and uses `fz_*` / `pdf_*` APIs. **Without linking MuPDF, native build will fail** (missing headers/lib).
- So the **native layer is written for MuPDF but not wired**; the C++ code is real, the build and linkage are placeholder.

---

## 2. Domain Layer

### 2.1 domain/models — ✅ Largely correct

| File | Status | Notes |
|------|--------|--------|
| `OperationResult.kt` | ✅ | Clean sealed class: Success, Error, Cancelled. |
| `ErrorCode.kt` | ✅ | Good coverage. |
| `PdfDocument.kt` | ✅ | Uses `Uri` (Android dependency in “models” — design doc preferred pure Kotlin here). |
| `OperationPayload.kt` | ✅ | ImageToPdf, MergePdf, CompressPdf + CompressionStrategyPayload; no ConvertPdf/Split payload yet. |

- **Issue:** `PdfDocument` uses `android.net.Uri`; design aimed for domain models to stay Android-free. Acceptable for current phase but worth noting.

### 2.2 domain/core — ⚠️ Bugs and missing types

| File | Status | Notes |
|------|--------|--------|
| `PdfTool.kt` | ✅ | Correct; imports `OperationResult` from `domain.models`. |
| `ImageToPdfTool.kt` | ✅ | Params + PageSize. |
| `MergePdfTool.kt` | ✅ | MergePdfParams. |
| `CompressPdfTool.kt` | ✅ | CompressPdfParams + CompressionStrategy. |
| `SplitPdfTool.kt` | ✅ | SplitPdfParams + PageRange. |
| **ConvertPdfTool / ConvertPdfParams** | ❌ **MISSING** | **Not defined anywhere.** Referenced by: `ConvertPdfUseCase`, `PoiConvertPdfTool`, `MuPdfPoiConvertTool`, `ConversionViewModel`, `PdfWorker`, `EngineModule`. **Compilation will fail.** |

### 2.3 Use cases — ❌ Wrong import + logic bug

- **OperationResult import:**  
  `CreatePdfUseCase`, `MergePdfUseCase`, `CompressPdfUseCase`, `ConvertPdfUseCase` all use:
  `import dev.pdfforge.domain.core.OperationResult`  
  **But `OperationResult` lives in `dev.pdfforge.domain.models`.** There is no `OperationResult` in `domain.core`. **Compile error in all four use cases.**

- **CreatePdfUseCase.kt (and similar pattern in Merge/Compress/Convert):**
  ```kotlin
  val validation = imageToPdfTool.validate(params)
  if (!validation.isValid) {
      // Return error based on validation   <-- COMMENT ONLY; NO RETURN
  }
  return imageToPdfTool.execute(params)   <-- ALWAYS EXECUTES
  ```
  When validation fails, the use case **does not return**; it still calls `execute()`. **Logic bug** in all four use cases (validation is effectively ignored).

### 2.4 domain/core test

- **CreatePdfUseCaseTest.kt:** Uses `dev.pdfforge.domain.core.OperationResult` and `ValidationResult` (correct).  
- `OperationResult` import is wrong (same as main code).  
- Test also uses `OperationResult.Error(ErrorCode.ENGINE_INTERNAL_ERROR)` — in `domain.models`, `Error` has `(code, message, cause)`; single-arg call is valid.

---

## 3. Data Layer

### 3.1 data/storage — ✅ Implemented

- **TempFileManager.kt:** Create temp file, clear all, delete single file. Correct and used by engine/features.

### 3.2 data/impl — ✅ Code present, ❌ No build script

- **SafFileAdapter.kt:** Implements `getPdfMetadata`, `openFileDescriptor`, `openInputStream`, `openOutputStream`. Uses `domain.models.OperationResult` correctly.  
  - **Risk:** `cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)` can return `-1` on some providers; then `getString(-1)` can misbehave. Defensive checks recommended.
- **DataModule.kt:** Provides `SafFileAdapter` and `TempFileManager`. Fine.

**Missing:** `data/impl/build.gradle.kts`. Without it, `:data:impl` cannot be built.

### 3.3 data/worker — ⚠️ Implemented but incomplete

- **PdfWorker.kt:** Handles `OP_IMAGE_TO_PDF`, `OP_MERGE_PDF`, `OP_COMPRESS_PDF`; **no branch for `OP_CONVERT_PDF`** (falls to `else -> Result.failure()`).  
- Imports `dev.pdfforge.domain.core.OperationResult` (wrong package).  
- Imports `ConvertPdfParams` (type does not exist).  
- **WorkManagerHelper.kt:** `enqueuePdfOperation` only handles `ImageToPdf`, `MergePdf`, `CompressPdf`. No `ConvertPdf` or Split payload type.

---

## 4. Engine Layer

### 4.1 engine/mupdf (Kotlin + JNI)

- **MuPdfJni.kt:** Declares `external` methods; loads `libmupdf_bridge`. **Will fail at runtime** if native lib is not built (and currently native does not link MuPDF).
- **MuPdfImageToPdfTool, MuPdfMergeTool, MuPdfSplitTool, MuPdfCompressTool:** Implement domain tool interfaces; call into `MuPdfJni`. Logic is consistent with the intended design.  
- All engine tools import `dev.pdfforge.domain.core.OperationResult` — **wrong package** (should be `domain.models`).  
- **EngineModule.kt:** Binds tools and **`ConvertPdfTool`**; depends on missing `ConvertPdfParams`/`ConvertPdfTool` from domain.

### 4.2 engine/converter

- **PoiConvertPdfTool.kt:** Creates a DOCX with a single placeholder paragraph (*"Converted from PDF using PDFForge..."*). **Does not read the source PDF.** Comment: *"Placeholder for MuPDF Text Extraction"*. So **conversion is dummy** (output is not based on input).  
- **MuPdfPoiConvertTool.kt:** Uses `MuPdfJni.openFromFd`, `getPageCount`, `extractTextBlocks`, builds DOCX from text blocks. **Real structure** but `run.addBreak()` is a *"Basic grouping placeholder"*; layout/heuristics are minimal.  
- Both import `domain.core.OperationResult` (wrong) and `ConvertPdfParams` (missing type).

### 4.3 engine/mupdf C++

- **mupdf-bridge.cpp:** Implements JNI for openFromFd, closeDocument, getPageCount, createNewDocument, copyPage, saveToFd. **Does not implement** `extractTextBlocks`, `addImagePage`, `deletePage`, `rotatePage`, `optimizeDocument`. So **Kotlin `external` methods for those have no native implementation** — runtime will throw UnsatisfiedLinkError when those are called.
- **CMakeLists.txt:** Does not link MuPDF; includes expect MuPDF headers. Build will fail without MuPDF.

---

## 5. Feature Layer & UI

### 5.1 common/ui — ✅ Theming and nav

- **Screen.kt:** All routes defined.  
- **Theme (Color, Theme.kt, Type.kt):** Dark theme, Forge colors. **Type.kt:** Comments *"Placeholder for Syne"* / *"Placeholder for JetBrains Mono"*; uses `FontFamily.Default` / `Monospace`.  
- **ProgressScreen.kt:** Implemented and used.

### 5.2 feature/home — ✅

- **HomeScreen.kt:** Grid of tools; bottom nav has empty `onClick` and no icons (placeholders).  
- **HomeViewModel.kt:** Loads tool list (hardcoded). Works.

### 5.3 feature/pdf_creation — ⚠️ Coil missing

- **ImageToPdfScreen.kt:** Uses `coil.compose.AsyncImage` for thumbnails.  
- **feature/pdf_creation/build.gradle.kts** does **not** declare Coil. **Unresolved reference** at compile time.

### 5.4 feature/merge_split

- **MergePdfScreen / MergePdfViewModel:** PDF picker, list, merge via WorkManager. **Merge flow is wired** (except for wrong `OperationResult` import in ViewModel).  
- **SplitPdfScreen / SplitPdfViewModel:** UI for file + page range; **SplitPdfViewModel.splitPdf()** only updates progress text and does **not** call any worker or use case. Comment: *"worker logic would go here"*. **Split is UI-only; no backend.**  
- **ReorderPagesScreen / ReorderPagesViewModel:** File selection, page list, rotate/move in UI; **saveChanges()** only sets `isProcessing` and status — no WorkManager or use case. **Reorder is stub.**

### 5.5 feature/compression — ✅ Flow wired

- **CompressionViewModel** uses `CompressPdfUseCase` directly (no worker). Strategy toggles and compress button are wired.  
- Imports `domain.core.OperationResult` (wrong package).

### 5.6 feature/conversion — ⚠️ Depends on missing type

- **ConversionViewModel** uses `ConvertPdfParams(sourceUri, targetFormat, outputName)`.  
- **ConvertPdfParams** is not defined in the repo; **ConvertPdfTool** is not defined. So this **will not compile** until those are added to domain.

### 5.7 MainActivity & navigation

- **MainActivity.kt:** NavHost with Home, ImageToPdf, MergePdf, CompressPdf; **ConvertPdf, SplitPdf, ReorderPages** are **`/* TODO */`** or empty composables. So **Convert / Split / Reorder screens are not shown**; only placeholders in the graph.

---

## 6. Dummy / Placeholder / Unwritten Code (Summary)

| Location | Type | Description |
|----------|------|-------------|
| **MainActivity.kt** | Placeholder | `composable(Screen.ConvertPdf.route) { /* TODO */ }` and same for SplitPdf, ReorderPages. |
| **engine/converter PoiConvertPdfTool** | Dummy | Does not read PDF; writes fixed placeholder text to DOCX. |
| **engine/converter MuPdfPoiConvertTool** | Placeholder | Text extraction + simple grouping; comment *"Basic grouping placeholder"*. |
| **engine/mupdf C++** | Unimplemented | `extractTextBlocks`, `addImagePage`, `deletePage`, `rotatePage`, `optimizeDocument` have **no JNI implementation** in `mupdf-bridge.cpp`. |
| **engine/mupdf CMakeLists.txt** | Placeholder | MuPDF not linked; comment *"MuPDF static library placeholder"*. |
| **common/ui/theme/Type.kt** | Placeholder | Font comments: *"Placeholder for Syne"* / *"JetBrains Mono"*. |
| **feature/merge_split SplitPdfViewModel** | Stub | `splitPdf()` only updates progress; no worker, no use case. |
| **feature/merge_split ReorderPagesViewModel** | Stub | `saveChanges()` only sets state; no persistence or engine. |
| **domain use cases** | Incomplete | Validation failure path does not return; always call `execute()`. |
| **PdfWorker** | Incomplete | No `OP_CONVERT_PDF` branch; no Split/Reorder payloads. |
| **WorkManagerHelper** | Incomplete | No ConvertPdf or Split payload handling. |

---

## 7. What Will Work (After Fixes)

If you:

1. Add **`build.gradle.kts`** for `:data:impl` and `:engine:mupdf` (and fix app’s dependency on engine modules so Hilt sees `EngineModule`).
2. Fix **all `OperationResult` imports** from `domain.core` to `domain.models`.
3. **Define `ConvertPdfParams` and `ConvertPdfTool`** in `domain/core/tools` (and use them in converter/engine).
4. Add **Coil** to `feature/pdf_creation` (or replace `AsyncImage` with something already on classpath).
5. Fix **use-case validation** so that on `!validation.isValid` you return an error result instead of calling `execute()`.

Then:

- **Home** → navigation to Image to PDF, Merge, Compress will work from a UI perspective.  
- **Image to PDF:** Worker + CreatePdfUseCase + MuPdfImageToPdfTool will **only work if** native `addImagePage` (and MuPDF) is implemented and linked; otherwise you get UnsatisfiedLinkError or build failure.  
- **Merge:** Same: depends on native `openFromFd`, `copyPage`, `saveToFd` — implemented in C++ but **MuPDF must be linked**.  
- **Compress:** Uses use case directly; native `optimizeDocument` is **not** implemented in C++; will fail at runtime when compress is run.  
- **Convert:** Depends on missing domain types (and optionally MuPDF text extraction); currently dummy/placeholder in engine.  
- **Split / Reorder:** No backend; only UI. Reorder also needs native rotate/save.

So **“will work”** today is limited to: **build passing, app launching, Home and Compose navigation**, and **merge/image-to-PDF only if native is fully built and linked**. Compress and convert need more engine work; split and reorder need full implementation.

---

## 8. File-by-File Quick Reference

| Module | File | Rating | Notes |
|--------|------|--------|--------|
| domain/models | All | ✅ | Solid; only Uri in models. |
| domain/core | PdfTool, *ToPdfTool, *PdfTool (except Convert) | ✅ | Good. |
| domain/core | ConvertPdfTool / ConvertPdfParams | ❌ | Missing. |
| domain/core | CreatePdfUseCase, MergePdfUseCase, CompressPdfUseCase, ConvertPdfUseCase | ❌ | Wrong OperationResult import; validation never returns. |
| data/storage | TempFileManager | ✅ | Good. |
| data/impl | SafFileAdapter, DataModule | ✅ | Code ok; module has no build file. |
| data/worker | PdfWorker, WorkManagerHelper | ⚠️ | Wrong import; no Convert/Split; ConvertPdfParams missing. |
| engine/mupdf | MuPdfJni, *Tool.kt, EngineModule | ⚠️ | Wrong OperationResult; module no build; native incomplete. |
| engine/mupdf | mupdf-bridge.cpp, CMakeLists.txt | ⚠️ | Many JNI stubs missing; MuPDF not linked. |
| engine/converter | PoiConvertPdfTool, MuPdfPoiConvertTool | ⚠️ | Placeholder/dummy; wrong import; missing ConvertPdfParams. |
| common/ui | All | ✅ | Theme/nav/progress ok; font placeholders. |
| feature/home | All | ✅ | Works. |
| feature/pdf_creation | ImageToPdfScreen | ❌ | Coil not in deps. |
| feature/pdf_creation | ImageToPdfViewModel | ✅ | WorkManager wired. |
| feature/merge_split | MergePdf* | ✅ | Wired. |
| feature/merge_split | SplitPdf*, ReorderPages* | ⚠️ | UI only; no backend. |
| feature/compression | All | ✅ | Wired; wrong import. |
| feature/conversion | All | ⚠️ | Uses missing ConvertPdfParams. |
| app | MainActivity | ⚠️ | Convert/Split/Reorder composables TODO. |
| app | PdfForgeApp | ✅ | Hilt + StrictMode. |

---

## 9. Recommendations (Priority Order)

1. **Fix compilation blockers**  
   - Add `ConvertPdfParams` and `ConvertPdfTool` in `domain/core/tools`.  
   - Change every `import dev.pdfforge.domain.core.OperationResult` to `import dev.pdfforge.domain.models.OperationResult` (and fix `OperationResult.Error` if needed for `message`/`cause`).  
   - Add `build.gradle.kts` for `:data:impl` and `:engine:mupdf`.  
   - Add Coil to `feature/pdf_creation` or remove `AsyncImage`.

2. **Fix use-case validation**  
   - In all four use cases, when `!validation.isValid`, return e.g. `OperationResult.Error(..., validation.errorRes)` instead of falling through to `execute()`.

3. **Wire app to engine**  
   - Add `implementation(project(":engine:mupdf"))` and `implementation(project(":engine:converter"))` (or equivalent) so Hilt loads `EngineModule`.

4. **Native / MuPDF**  
   - Either link MuPDF in `engine/mupdf` and implement missing JNI (extractTextBlocks, addImagePage, rotatePage, optimizeDocument, etc.) or temporarily stub those calls so the app runs without native PDF ops.

5. **Implement or hide incomplete flows**  
   - Split: add payload type, worker branch, and use case wiring; or hide the screen until ready.  
   - Reorder: same (worker + use case + native rotate/save).  
   - Convert: already uses ConvertPdfUseCase once types exist; improve engine from placeholder to real extraction.

6. **MainActivity**  
   - Replace `/* TODO */` composables for Convert/Split/Reorder with real screens (or a single “Coming soon” screen) so navigation is consistent.

After these, you can get a **buildable, runnable app** with Home, Image to PDF, Merge, and Compress wired, and a clear path to finishing Convert, Split, and Reorder.
