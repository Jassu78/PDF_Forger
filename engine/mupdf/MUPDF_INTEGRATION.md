# MuPDF native integration (P4)

The app’s PDF engine uses a **JNI bridge** (`engine/mupdf`) that compiles in two modes:

- **Without MuPDF:** Stub implementations in `mupdf-bridge.cpp` (always built). Operations return 0/false/empty; no real PDF output.
- **With MuPDF:** When prebuilts are present, CMake links `libmupdf.a` and defines `USE_MUPDF`; the same `mupdf-bridge.cpp` uses the real MuPDF C API.

## Quick start: use prebuilts

**Option A: Docker (recommended, easy cleanup)**

```bash
# From repo root
cd engine/mupdf/docker
docker build -t mupdf-android .
docker run --rm -v "$(pwd)/../prebuilt:/out" mupdf-android
```

See `engine/mupdf/docker/README.md` for details.

**Option B: Manual**

1. `bash engine/mupdf/scripts/build_mupdf_android.sh` — clones MuPDF, copies headers.
2. Build `libmupdf.a` per ABI using the [official guide](https://mupdf.readthedocs.io/en/latest/guide/using-with-android.html) or mupdf-android-viewer.
3. Place each `libmupdf.a` in `engine/mupdf/prebuilt/<ABI>/`.

**Then** rebuild the app. CMake links MuPDF when `prebuilt/include/mupdf/fitz.h` and `prebuilt/${ANDROID_ABI}/libmupdf.a` exist. Merge, Split, Compress, Reorder will produce real PDFs. Image-to-PDF still requires `addImagePage` (TODO).

## Current state

- **Kotlin:** `MuPdfJni` declares native methods; `MuPdf*Tool` classes call them.
- **C++:** `mupdf-bridge.cpp` implements those JNI symbols. When `USE_MUPDF` is not set, they are stubs. When set, they call MuPDF (open/close/count, create doc, copy page, delete page, rotate page, save, optimize). `addImagePage` is still a stub; `extractTextBlocks` returns an empty array.
- **CMake:** `engine/mupdf/src/main/cpp/CMakeLists.txt` builds `mupdf_bridge`; if `prebuilt/include` and `prebuilt/${ANDROID_ABI}/libmupdf.a` exist, it links MuPDF and defines `USE_MUPDF`.

## Steps to integrate MuPDF

### 1. Obtain MuPDF for Android

Choose one:

- **Prebuilt libraries**  
  Build MuPDF for each Android ABI (e.g. with Android NDK / CMake or the [official Android build](https://mupdf.readthedocs.io/en/latest/guide/using-with-android.html)) and get static or shared libs (e.g. `libmupdf.a` or `libmupdf.so`) per ABI.

- **Maven (if available)**  
  Some setups publish MuPDF artifacts; you can add them as a dependency and use their headers/libs if the layout matches.

- **Submodule / source**  
  Add MuPDF as a submodule or copy source under `engine/mupdf/third_party/mupdf` and build it from CMake (more work: MuPDF’s own build is Makefile-based; you may need a custom CMake or build script).

### 2. Point CMake at MuPDF

Prebuilts are detected automatically when present:

- **Layout:** `engine/mupdf/prebuilt/include/` (MuPDF headers) and `engine/mupdf/prebuilt/<ABI>/libmupdf.a` for each ABI.
- **Detection:** CMake checks `prebuilt/include/mupdf/fitz.h` and `prebuilt/${ANDROID_ABI}/libmupdf.a`. If both exist, it creates an `IMPORTED` static library `mupdf`, adds include dirs, links `mupdf_bridge` to `mupdf`, and defines `USE_MUPDF`.
- No extra CMake options are required; just ensure the script (or your own build) has produced the above layout.

### 3. Implement JNI in `mupdf-bridge.cpp`

When `USE_MUPDF` is defined, the bridge already implements:

- **Context:** A global `fz_context*` created on first use; `fz_register_document_handlers` is called once.
- **Open from FD:** `fz_open_document(ctx, "/proc/self/fd/%d", fd)` (Linux/Android).
- **Close:** `fz_drop_document(ctx, doc)`.
- **Page count:** `fz_count_pages(ctx, doc)`.
- **New document:** `pdf_create_document(ctx)`.
- **Copy page:** `pdf_graft_page(ctx, dst_doc, -1, src_doc, page_from)`.
- **Delete page:** `pdf_delete_page(ctx, doc, number)`.
- **Rotate page:** Look up page obj, get/set `Rotate` key.
- **Save / optimize:** `pdf_save_document(ctx, doc, "/proc/self/fd/%d", &opts)` with `do_compress` / `do_garbage`.
- **extractTextBlocks:** Currently returns an empty array (can be extended with `fz_new_stext_page_from_page` and iteration).
- **addImagePage:** Stub (TODO: open image from fd, create PDF page with image, insert).

### 4. 16 KB page size

The project already sets:

```cmake
target_link_options(mupdf_bridge PRIVATE "-Wl,-z,max-page-size=16384")
```

Keep this when linking MuPDF so the combined binary stays compatible with Android 15+ and store requirements.

### 5. Build and test

- Sync Gradle and build the `:engine:mupdf` native target.
- Run Merge / Split / Compress / Image-to-PDF / Reorder; confirm output PDFs are generated and valid.

## References

- [MuPDF – Using with Android](https://mupdf.readthedocs.io/en/latest/guide/using-with-android.html)
- [MuPDF C API (e.g. document, streams)](https://mupdf.readthedocs.io/en/latest/C-API.html) (and related I/O / buffer docs)
- [Building MuPDF with Android NDK (example)](https://nascenia.com/building-mupdf-library-with-android-ndk-linux/)

Once MuPDF is linked and the JNI is implemented, all tools that go through `MuPdfJni` will produce real PDFs.
