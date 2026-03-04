#include <jni.h>
#include <string>
#include <android/log.h>
#include "mupdf/fitz.h"
#include "mupdf/pdf.h"

#define LOG_TAG "MuPdfBridge"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jlong JNICALL
Java_dev_pdfforge_engine_mupdf_MuPdfJni_openFromFd(JNIEnv *env, jobject thiz, jint fd) {
    fz_context *ctx = fz_new_context(NULL, NULL, FZ_STORE_DEFAULT);
    if (!ctx) return 0;

    fz_try(ctx) {
        fz_stream *stream = fz_open_fd(ctx, fd);
        fz_document *doc = fz_open_document_with_stream(ctx, "pdf", stream);
        return (jlong)doc;
    }
    fz_catch(ctx) {
        LOGE("Failed to open document from FD: %s", fz_caught_message(ctx));
        fz_drop_context(ctx);
        return 0;
    }
}

JNIEXPORT void JNICALL
Java_dev_pdfforge_engine_mupdf_MuPdfJni_closeDocument(JNIEnv *env, jobject thiz, jlong handle) {
    fz_document *doc = (fz_document *)handle;
    if (doc) {
        fz_context *ctx = fz_new_context(NULL, NULL, FZ_STORE_DEFAULT);
        fz_drop_document(ctx, doc);
        fz_drop_context(ctx);
    }
}

JNIEXPORT jint JNICALL
Java_dev_pdfforge_engine_mupdf_MuPdfJni_getPageCount(JNIEnv *env, jobject thiz, jlong handle) {
    fz_document *doc = (fz_document *)handle;
    if (!doc) return 0;
    fz_context *ctx = fz_new_context(NULL, NULL, FZ_STORE_DEFAULT);
    int count = fz_count_pages(ctx, doc);
    fz_drop_context(ctx);
    return count;
}

JNIEXPORT jlong JNICALL
Java_dev_pdfforge_engine_mupdf_MuPdfJni_createNewDocument(JNIEnv *env, jobject thiz) {
    fz_context *ctx = fz_new_context(NULL, NULL, FZ_STORE_DEFAULT);
    if (!ctx) return 0;
    
    fz_try(ctx) {
        pdf_document *doc = pdf_create_document(ctx);
        return (jlong)doc;
    }
    fz_catch(ctx) {
        fz_drop_context(ctx);
        return 0;
    }
}

JNIEXPORT jboolean JNICALL
Java_dev_pdfforge_engine_mupdf_MuPdfJni_copyPage(JNIEnv *env, jobject thiz, jlong srcHandle, jint pageNum, jlong destHandle) {
    fz_document *src = (fz_document *)srcHandle;
    pdf_document *dest = (pdf_document *)destHandle;
    fz_context *ctx = fz_new_context(NULL, NULL, FZ_STORE_DEFAULT);
    
    fz_try(ctx) {
        pdf_graft_page(ctx, dest, -1, (pdf_document *)src, pageNum);
        fz_drop_context(ctx);
        return JNI_TRUE;
    }
    fz_catch(ctx) {
        fz_drop_context(ctx);
        return JNI_FALSE;
    }
}

JNIEXPORT jboolean JNICALL
Java_dev_pdfforge_engine_mupdf_MuPdfJni_saveToFd(JNIEnv *env, jobject thiz, jlong handle, jint outputFd) {
    fz_document *doc = (fz_document *)handle;
    fz_context *ctx = fz_new_context(NULL, NULL, FZ_STORE_DEFAULT);
    
    fz_try(ctx) {
        fz_write_options opts = fz_default_write_options;
        opts.do_incremental = 0;
        
        fz_stream *out = fz_open_fd(ctx, outputFd);
        fz_write_document(ctx, doc, out, &opts);
        fz_drop_stream(ctx, out);
        fz_drop_context(ctx);
        return JNI_TRUE;
    }
    fz_catch(ctx) {
        fz_drop_context(ctx);
        return JNI_FALSE;
    }
}

}
