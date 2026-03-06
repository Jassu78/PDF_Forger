# PDF Forger (PdfForge)

**Standalone Android app** for PDF operations: merge, split, compress, image-to-PDF, reorder pages, and convert (e.g. to DOCX/PPTX).

This project is **not linked to** and is **separate from** any other project or app named "pdfworker" or similar. All code and background processing live in this repository only.

## Structure

- **app** — Android application entry point
- **domain** — Models and use cases (no Android)
- **data** — File I/O (`:data:impl`), temp storage (`:data:storage`), and **background work** (`:data:worker`). The worker that runs PDF jobs is defined and used only inside this app.
- **engine** — PDF engine (MuPDF JNI bridge, converter)
- **feature** — UI modules (home, merge, split, compress, convert, image-to-PDF, reorder)

## Build and run

- Open in Android Studio (or use Gradle with NDK if you build native code).
- Build and run on device or emulator. No external "pdfworker" dependency or link is required.

## Native PDF engine (optional)

PDF operations use a JNI bridge in `engine/mupdf`. For real output (not stubs), see `engine/mupdf/MUPDF_INTEGRATION.md` for linking MuPDF.

---

*Project name: PDF Forger. Root Gradle project: `PDF Forger`.*
