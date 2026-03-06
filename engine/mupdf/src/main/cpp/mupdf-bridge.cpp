// JNI bridge for MuPDF. When USE_MUPDF is defined and MuPDF is linked, real implementation is used.
#include <jni.h>
#include <android/log.h>
#include <cstdio>
#include <cstring>

#define LOG_TAG "MuPdfBridge"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#ifdef USE_MUPDF
#include "mupdf/fitz.h"
#include "mupdf/pdf/document.h"
#include "mupdf/pdf/graft.h"
#include "mupdf/pdf/page.h"
#include "mupdf/pdf/object.h"
#endif

extern "C" {

#ifdef USE_MUPDF
static fz_context *s_ctx = nullptr;

static fz_context *get_context() {
    if (!s_ctx) {
        s_ctx = fz_new_context(nullptr, nullptr, FZ_STORE_UNLIMITED);
        if (s_ctx)
            fz_register_document_handlers(s_ctx);
    }
    return s_ctx;
}
#endif

JNIEXPORT jlong JNICALL
Java_dev_pdfforge_engine_mupdf_MuPdfJni_openFromFd(JNIEnv *env, jobject, jint fd) {
#ifdef USE_MUPDF
    fz_context *ctx = get_context();
    if (!ctx || fd < 0) return 0;
    char path[64];
    if (snprintf(path, sizeof(path), "/proc/self/fd/%d", fd) < 0) return 0;
    fz_try(ctx) {
        fz_document *doc = fz_open_document(ctx, path);
        return reinterpret_cast<jlong>(doc);
    }
    fz_catch(ctx) {
        LOGE("openFromFd failed: %s", fz_caught_message(ctx));
        return 0;
    }
#else
    (void)env;
    return 0;
#endif
}

JNIEXPORT void JNICALL
Java_dev_pdfforge_engine_mupdf_MuPdfJni_closeDocument(JNIEnv *, jobject, jlong handle) {
#ifdef USE_MUPDF
    if (handle == 0) return;
    fz_context *ctx = get_context();
    if (ctx)
        fz_drop_document(ctx, reinterpret_cast<fz_document*>(handle));
#else
    (void)handle;
#endif
}

JNIEXPORT jint JNICALL
Java_dev_pdfforge_engine_mupdf_MuPdfJni_getPageCount(JNIEnv *, jobject, jlong handle) {
#ifdef USE_MUPDF
    if (handle == 0) return 0;
    fz_context *ctx = get_context();
    if (!ctx) return 0;
    fz_try(ctx) {
        return fz_count_pages(ctx, reinterpret_cast<fz_document*>(handle));
    }
    fz_catch(ctx) { return 0; }
#else
    (void)handle;
    return 0;
#endif
}

JNIEXPORT jobjectArray JNICALL
Java_dev_pdfforge_engine_mupdf_MuPdfJni_extractTextBlocks(JNIEnv *env, jobject, jlong handle, jint pageNum) {
#ifdef USE_MUPDF
    (void)handle;
    (void)pageNum;
    /* Text extraction: use fz_load_page + fz_new_stext_page_from_page + iterate blocks when needed */
#endif
    (void)env;
    jclass stringClass = env->FindClass("java/lang/String");
    return (jobjectArray)env->NewObjectArray(0, stringClass, nullptr);
}

JNIEXPORT jlong JNICALL
Java_dev_pdfforge_engine_mupdf_MuPdfJni_createNewDocument(JNIEnv *, jobject) {
#ifdef USE_MUPDF
    fz_context *ctx = get_context();
    if (!ctx) return 0;
    fz_try(ctx) {
        pdf_document *doc = pdf_create_document(ctx);
        return reinterpret_cast<jlong>(doc);
    }
    fz_catch(ctx) {
        LOGE("createNewDocument failed: %s", fz_caught_message(ctx));
        return 0;
    }
#else
    return 0;
#endif
}

JNIEXPORT jboolean JNICALL
Java_dev_pdfforge_engine_mupdf_MuPdfJni_addImagePage(JNIEnv *, jobject, jlong docHandle, jint imageFd, jint quality, jint width, jint height) {
#ifdef USE_MUPDF
    (void)docHandle; (void)imageFd; (void)quality; (void)width; (void)height;
    /* TODO: open image from fd, create PDF page with image, insert into doc */
    return JNI_FALSE;
#else
    (void)docHandle; (void)imageFd; (void)quality; (void)width; (void)height;
    return JNI_FALSE;
#endif
}

JNIEXPORT jboolean JNICALL
Java_dev_pdfforge_engine_mupdf_MuPdfJni_copyPage(JNIEnv *, jobject, jlong srcDocHandle, jint pageNum, jlong destDocHandle) {
#ifdef USE_MUPDF
    if (srcDocHandle == 0 || destDocHandle == 0) return JNI_FALSE;
    fz_context *ctx = get_context();
    if (!ctx) return JNI_FALSE;
    pdf_document *src = pdf_document_from_fz_document(ctx, reinterpret_cast<fz_document*>(srcDocHandle));
    pdf_document *dst = pdf_document_from_fz_document(ctx, reinterpret_cast<fz_document*>(destDocHandle));
    if (!src || !dst) return JNI_FALSE;
    fz_try(ctx) {
        pdf_graft_page(ctx, dst, -1, src, pageNum);
        return JNI_TRUE;
    }
    fz_catch(ctx) {
        LOGE("copyPage failed: %s", fz_caught_message(ctx));
        return JNI_FALSE;
    }
#else
    (void)srcDocHandle; (void)pageNum; (void)destDocHandle;
    return JNI_FALSE;
#endif
}

JNIEXPORT jboolean JNICALL
Java_dev_pdfforge_engine_mupdf_MuPdfJni_deletePage(JNIEnv *, jobject, jlong docHandle, jint pageNum) {
#ifdef USE_MUPDF
    if (docHandle == 0) return JNI_FALSE;
    fz_context *ctx = get_context();
    if (!ctx) return JNI_FALSE;
    pdf_document *doc = pdf_document_from_fz_document(ctx, reinterpret_cast<fz_document*>(docHandle));
    if (!doc) return JNI_FALSE;
    fz_try(ctx) {
        pdf_delete_page(ctx, doc, pageNum);
        return JNI_TRUE;
    }
    fz_catch(ctx) {
        LOGE("deletePage failed: %s", fz_caught_message(ctx));
        return JNI_FALSE;
    }
#else
    (void)docHandle; (void)pageNum;
    return JNI_FALSE;
#endif
}

JNIEXPORT jboolean JNICALL
Java_dev_pdfforge_engine_mupdf_MuPdfJni_rotatePage(JNIEnv *, jobject, jlong docHandle, jint pageNum, jint rotation) {
#ifdef USE_MUPDF
    if (docHandle == 0) return JNI_FALSE;
    fz_context *ctx = get_context();
    if (!ctx) return JNI_FALSE;
    pdf_document *doc = pdf_document_from_fz_document(ctx, reinterpret_cast<fz_document*>(docHandle));
    if (!doc) return JNI_FALSE;
    fz_try(ctx) {
        pdf_obj *page_ref = pdf_lookup_page_obj(ctx, doc, pageNum);
        if (!page_ref) return JNI_FALSE;
        pdf_obj *page = pdf_resolve_indirect(ctx, page_ref);
        if (!page) return JNI_FALSE;
        int rot = (pdf_to_int(ctx, pdf_dict_get(ctx, page, PDF_NAME(Rotate))) + rotation) % 360;
        if (rot < 0) rot += 360;
        pdf_dict_put_int(ctx, page, PDF_NAME(Rotate), rot);
        return JNI_TRUE;
    }
    fz_catch(ctx) {
        LOGE("rotatePage failed: %s", fz_caught_message(ctx));
        return JNI_FALSE;
    }
#else
    (void)docHandle; (void)pageNum; (void)rotation;
    return JNI_FALSE;
#endif
}

JNIEXPORT jboolean JNICALL
Java_dev_pdfforge_engine_mupdf_MuPdfJni_optimizeDocument(JNIEnv *, jobject, jlong docHandle, jint outputFd, jint quality, jint targetDpi, jboolean stripMetadata, jboolean fontSubsetting) {
#ifdef USE_MUPDF
    (void)quality; (void)targetDpi; (void)stripMetadata; (void)fontSubsetting;
    if (docHandle == 0) return JNI_FALSE;
    fz_context *ctx = get_context();
    if (!ctx) return JNI_FALSE;
    pdf_document *doc = pdf_document_from_fz_document(ctx, reinterpret_cast<fz_document*>(docHandle));
    if (!doc) return JNI_FALSE;
    char path[64];
    if (snprintf(path, sizeof(path), "/proc/self/fd/%d", outputFd) < 0) return JNI_FALSE;
    pdf_write_options opts = { 0 };
    opts.do_compress = 1;
    opts.do_garbage = 2;
    fz_try(ctx) {
        pdf_save_document(ctx, doc, path, &opts);
        return JNI_TRUE;
    }
    fz_catch(ctx) {
        LOGE("optimizeDocument failed: %s", fz_caught_message(ctx));
        return JNI_FALSE;
    }
#else
    (void)docHandle; (void)outputFd; (void)quality; (void)targetDpi; (void)stripMetadata; (void)fontSubsetting;
    return JNI_FALSE;
#endif
}

JNIEXPORT jboolean JNICALL
Java_dev_pdfforge_engine_mupdf_MuPdfJni_saveToFd(JNIEnv *, jobject, jlong docHandle, jint outputFd) {
#ifdef USE_MUPDF
    if (docHandle == 0) return JNI_FALSE;
    fz_context *ctx = get_context();
    if (!ctx) return JNI_FALSE;
    pdf_document *doc = pdf_document_from_fz_document(ctx, reinterpret_cast<fz_document*>(docHandle));
    if (!doc) return JNI_FALSE;
    char path[64];
    if (snprintf(path, sizeof(path), "/proc/self/fd/%d", outputFd) < 0) return JNI_FALSE;
    pdf_write_options opts = { 0 };
    opts.do_compress = 1;
    fz_try(ctx) {
        pdf_save_document(ctx, doc, path, &opts);
        return JNI_TRUE;
    }
    fz_catch(ctx) {
        LOGE("saveToFd failed: %s", fz_caught_message(ctx));
        return JNI_FALSE;
    }
#else
    (void)docHandle; (void)outputFd;
    return JNI_FALSE;
#endif
}

}
