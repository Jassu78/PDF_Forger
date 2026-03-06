# PDF Forger — Master TODO List

All items that need to be done for a buildable, runnable, production-ready app.  
Based on: `IMPLEMENTATION_AUDIT_REPORT.md`.

Use `[ ]` for pending and `[x]` for done.

---

## Why operations don’t complete (current state)

- **UI & navigation:** App loads, Home shows, all 6 tool screens (Image to PDF, Merge, Compress, Convert, Split, Reorder) are reachable and show their UI.
- **WorkManager:** Image to PDF / Merge / Compress enqueue background work; the worker runs and calls the use cases.
- **Engine layer:** The PDF work is done by `engine/mupdf` (MuPdf*Tool + JNI). The **native library is a stub**: `mupdf-bridge.cpp` does **not** link MuPDF and every JNI function returns `0` / `false` / empty (e.g. `openFromFd` → 0, `addImagePage` → false, `saveToFd` → false). So use cases get “failure” or no real output.
- **To make operations actually work:** Implement **P4** (obtain/build MuPDF, link it in CMake, implement real JNI in `mupdf-bridge.cpp` for open, getPageCount, addImagePage, copyPage, saveToFd, etc.). Until then, operations will either fail or produce no real PDF.

---

## Recommended next steps (in order)

| Order | Priority | What to do | Why |
|-------|----------|------------|-----|
| **1** | **P1** | Use-case validation: in all 4 use cases, when `!validation.isValid` **return** `OperationResult.Error(...)` instead of falling through to `execute()`. | Prevents invalid input from reaching the engine; correct behavior and easier to debug. |
| **2** | **P1** | SafFileAdapter: ensure `getPdfMetadata` guards against `getColumnIndex` returning `-1` before using columns (may already be done). | Avoids crashes on some content resolvers. |
| **3** | **P6** | Error UX: show `OperationResult.Error.message` in UI (Snackbar or inline) when an operation fails. | User sees *why* something failed instead of silent failure. |
| **4** | **P6** | Result handling: when Image to PDF / Merge / Compress complete successfully, show result (e.g. share intent or “Open file”) instead of only clearing progress. | Makes success visible; works once P4 is done. |
| **5** | **P4** | MuPDF: obtain/build MuPDF for Android, link in CMake, implement real JNI in `mupdf-bridge.cpp`. | **Only way to get real PDF output**; biggest lift but unlocks all tools. |
| **6** | **P3** | Add Split/Convert payloads and worker branches; implement `SplitPdfViewModel.splitPdf()` and `ReorderPagesViewModel.saveChanges()`. | When P4 is done, Split/Convert/Reorder flows are already wired. |

**Optional:** P6 typography/fonts and HomeScreen bottom nav; P7 tests; P8 README/NOTICE/ProGuard when moving toward release.

---

## P0 — Blocking (must fix to compile)

- [x] **Add `ConvertPdfParams` and `ConvertPdfTool`** in `domain/core/src/main/kotlin/dev/pdfforge/domain/core/tools/` (e.g. new file `ConvertPdfTool.kt` with interface + data class; include `sourceUri`, `targetFormat`, `outputName` to match `ConversionViewModel`).
- [x] **Fix `OperationResult` import** in all files that use `dev.pdfforge.domain.core.OperationResult` → change to `dev.pdfforge.domain.models.OperationResult` in:
  - [x] `domain/core/usecases/CreatePdfUseCase.kt`
  - [x] `domain/core/usecases/MergePdfUseCase.kt`
  - [x] `domain/core/usecases/CompressPdfUseCase.kt`
  - [x] `domain/core/usecases/ConvertPdfUseCase.kt`
  - [x] `domain/core/src/test/.../CreatePdfUseCaseTest.kt`
  - [x] `data/worker/PdfWorker.kt`
  - [x] `engine/mupdf/MuPdfImageToPdfTool.kt`
  - [x] `engine/mupdf/MuPdfMergeTool.kt`
  - [x] `engine/mupdf/MuPdfSplitTool.kt`
  - [x] `engine/mupdf/MuPdfCompressTool.kt`
  - [x] `engine/converter/PoiConvertPdfTool.kt`
  - [x] `engine/converter/MuPdfPoiConvertTool.kt`
  - [x] `feature/merge_split/MergePdfViewModel.kt`
  - [x] `feature/merge_split/SplitPdfViewModel.kt`
  - [x] `feature/merge_split/ReorderPagesViewModel.kt`
  - [x] `feature/compression/CompressionViewModel.kt`
  - [x] `feature/conversion/ConversionViewModel.kt`
- [x] **Add `build.gradle.kts` for `:data:impl`** (Android library; depend on `:domain:models`, `:domain:core`? or minimal deps; provide `SafFileAdapter` and `DataModule`).
- [x] **Add `build.gradle.kts` for `:engine:mupdf`** (Android library with NDK; depend on `:domain:models`, `:domain:core`, `:data:impl`, `:data:storage`; apply NDK/CMake so `mupdf_bridge` builds; add Hilt if EngineModule stays here).
- [x] **Add Coil dependency** to `feature/pdf_creation/build.gradle.kts` (e.g. `implementation("io.coil-kt:coil-compose:2.x.x")`) so `AsyncImage` in `ImageToPdfScreen.kt` resolves.
- [x] **Wire app to engine modules**: in `app/build.gradle.kts` add `implementation(project(":engine:mupdf"))` and `implementation(project(":engine:converter"))` so Hilt sees `EngineModule` and can provide PDF tools.

---

## P1 — Logic & validation (correct behavior)

- [x] **Use-case validation**: In `CreatePdfUseCase`, `MergePdfUseCase`, `CompressPdfUseCase`, `ConvertPdfUseCase`, when `!validation.isValid` return an error (e.g. `OperationResult.Error` with appropriate `ErrorCode` / `validation.errorRes`) instead of falling through to `execute()`.
- [x] **SafFileAdapter defensive checks**: In `getPdfMetadata`, guard against `getColumnIndex` returning `-1` before using name/size columns (or use safe column access) to avoid crashes on some content resolvers.

---

## P2 — MainActivity & navigation

- [x] **Implement ConvertPdf composable** in `MainActivity.kt`: show `ConversionScreen` with `ConversionViewModel` and `onBackClick`, same pattern as CompressPdf.
- [x] **Implement SplitPdf composable** in `MainActivity.kt`: show `SplitPdfScreen` with `SplitPdfViewModel` and `onBackClick`.
- [x] **Implement ReorderPages composable** in `MainActivity.kt`: show `ReorderPagesScreen` with `ReorderPagesViewModel` and `onBackClick`.
- [ ] **(Optional)** Add proper bottom nav icons and `onClick` navigation for Home / Files / Settings in `HomeScreen.kt`.

---

## P3 — Worker & background operations

- [ ] **Add `OperationPayload` variant for Split** (e.g. `OperationPayload.SplitPdf(sourceUri, outputName, pageRanges)`) in `domain/models/OperationPayload.kt`; add `@Serializable` and update `WorkManagerHelper` and `PdfWorker` to handle it.
- [ ] **Add `OperationPayload` variant for Convert** if running convert in worker (e.g. `OperationPayload.ConvertPdf(sourceUri, targetFormat, outputName)`); then add branch in `PdfWorker` for `OP_CONVERT_PDF` and in `WorkManagerHelper.enqueuePdfOperation`.
- [ ] **Implement `SplitPdfViewModel.splitPdf()`**: parse page range string (e.g. "1-5, 8, 10-12") into `List<PageRange>`, build payload, enqueue worker (or call use case directly), observe work result and update UI.
- [ ] **Implement `ReorderPagesViewModel.saveChanges()`**: build payload (or call use case) for reorder/rotate, enqueue worker or use case, then update UI on result.
- [ ] **(Optional)** Add payload type for Reorder if using WorkManager (e.g. page order + rotations).

---

## P4 — Native / MuPDF engine

- [ ] **Obtain or build MuPDF** for Android (e.g. prebuilt libs per ABI or add MuPDF as submodule and build in CMake).
- [ ] **Update `engine/mupdf/src/main/cpp/CMakeLists.txt`**: link MuPDF static/lib and add include dirs so `#include "mupdf/fitz.h"` etc. resolve.
- [ ] **Implement missing JNI in `mupdf-bridge.cpp`**:
  - [ ] `extractTextBlocks(docHandle, pageNum)` → return array of text blocks for a page.
  - [ ] `addImagePage(docHandle, imageFd, quality, width, height)` → add image as new page.
  - [ ] `deletePage(docHandle, pageNum)`.
  - [ ] `rotatePage(docHandle, pageNum, rotation)`.
  - [ ] `optimizeDocument(docHandle, outputFd, quality, targetDpi, stripMetadata, fontSubsetting)`.
- [ ] **Ensure context/lifecycle** in C++ is correct (e.g. shared `fz_context` or per-call context as in current snippets) and no leaks on close.

---

## P5 — Conversion engine (real behavior)

- [ ] **PoiConvertPdfTool**: Use MuPDF (or another engine) to extract text/structure from the source PDF and feed it into POI DOCX/PPTX instead of writing fixed placeholder text.
- [ ] **MuPdfPoiConvertTool**: Improve layout heuristics (paragraph grouping by Y-proximity, headings, lists) and remove "Basic grouping placeholder" so DOCX structure matches PDF better.
- [ ] **(Later)** PDF → PPTX path (one slide per page or structured slides).
- [ ] **(Later)** PDF → Images export if required by product.

---

## P6 — UI polish & assets

- [ ] **Typography**: Add Syne and JetBrains Mono fonts under `common/ui` (or app) `res/font` and use them in `Type.kt` instead of "Placeholder for Syne" / "JetBrains Mono".
- [ ] **HomeScreen bottom nav**: Add real icons and navigation for "Files" and "Settings" (or remove if not in scope).
- [x] **Result handling**: After Image to PDF / Merge / Compress / Convert complete, show result (e.g. share intent, save via SAF, or open) instead of only clearing progress.
- [x] **Error UX**: Show user-facing error messages (from `OperationResult.Error.message` or `errorRes`) in screens (e.g. Snackbar or inline text).

---

## P7 — Testing & quality

- [ ] **Fix CreatePdfUseCaseTest** after fixing `OperationResult` import (use `domain.models.OperationResult`).
- [ ] Add unit tests for other use cases (Merge, Compress, Convert) and validation paths.
- [ ] Add tests for SafFileAdapter (e.g. getPdfMetadata with mock ContentResolver).
- [ ] **(Optional)** Instrumented tests for critical flows (pick file → run tool → check result).

---

## P8 — Documentation & release

- [ ] **README.md**: Build instructions (including NDK/MuPDF if needed), run, and feature list.
- [ ] **NOTICE / licenses**: MuPDF (AGPL-3.0), POI, Coil, etc. as required.
- [ ] **ProGuard**: Ensure keep rules for Hilt, serialization, and native if needed for release build.

---

## Summary checklist

| Priority | Description                    | Count (approx) |
|----------|--------------------------------|----------------|
| P0       | Blocking compile/build/DI      | 6 tasks        |
| P1       | Logic & validation             | 2 tasks        |
| P2       | MainActivity & nav             | 3–4 tasks      |
| P3       | Worker & background            | 4–5 tasks      |
| P4       | Native / MuPDF                 | 5+ tasks       |
| P5       | Conversion engine              | 2–4 tasks      |
| P6       | UI polish                      | 4 tasks        |
| P7       | Testing                        | 4 tasks        |
| P8       | Docs & release                 | 3 tasks        |

Start with **P0** until `./gradlew assembleDebug` succeeds and the app launches with Home and at least one tool path working; then P1–P2, then P3–P5 as you complete each feature.
