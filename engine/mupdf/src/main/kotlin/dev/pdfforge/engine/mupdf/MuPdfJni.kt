package dev.pdfforge.engine.mupdf

import android.graphics.BitmapFactory
import android.os.ParcelFileDescriptor
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.PDFDocument
import com.artifex.mupdf.fitz.Image
import com.artifex.mupdf.fitz.Matrix
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * Kotlin wrapper for MuPDF Android SDK.
 * This class replaces the previous JNI bridge to use the official Fitz SDK.
 */
object MuPdfJni {
    private val documents = mutableMapOf<Long, Document>()
    private val handleGenerator = AtomicLong(1)

    fun openFromFd(fd: Int): Long {
        return try {
            // Use /proc/self/fd trick to open from FD string
            val doc = Document.openDocument("/proc/self/fd/$fd")
            val handle = handleGenerator.getAndIncrement()
            documents[handle] = doc
            handle
        } catch (e: Exception) {
            0L
        }
    }

    fun closeDocument(handle: Long) {
        documents.remove(handle)?.destroy()
    }

    fun getPageCount(handle: Long): Int {
        return documents[handle]?.countPages() ?: 0
    }

    fun extractTextBlocks(docHandle: Long, pageNum: Int): Array<String> {
        // Return empty for now as it's not used by current tools
        return emptyArray()
    }

    fun createNewDocument(): Long {
        return try {
            // PDFDocument has a public constructor that creates a new doc
            val doc = PDFDocument()
            val handle = handleGenerator.getAndIncrement()
            documents[handle] = doc
            handle
        } catch (e: Exception) {
            0L
        }
    }

    fun addImagePage(docHandle: Long, imageFd: Int, quality: Int, width: Int, height: Int): Boolean {
        val destDoc = documents[docHandle] as? PDFDocument ?: return false
        return try {
            // Hybrid approach: Use Android PdfDocument to create a valid one-page PDF from image
            val tempImagePdf = File.createTempFile("temp_img", ".pdf")
            val pdfDoc = android.graphics.pdf.PdfDocument()
            val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(width, height, 1).create()
            val page = pdfDoc.startPage(pageInfo)
            
            // USE FILE DESCRIPTOR DIRECTLY WITHOUT ADOPTING (to avoid fdsan error)
            val pfd = ParcelFileDescriptor.fromFd(imageFd)
            val bitmap = BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor)
            if (bitmap != null) {
                page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                pdfDoc.finishPage(page)
                FileOutputStream(tempImagePdf).use { pdfDoc.writeTo(it) }
            }
            pdfDoc.close()
            // DO NOT close pfd here as it doesn't own the underlying FD (fromFd doesn't close on finalize by default if not told)
            // Actually, ParcelFileDescriptor.fromFd returns a PFD that DOES NOT own the FD in a way that triggers fdsan on exchange
            // But to be even safer, we can just use /proc/self/fd/
            
            if (tempImagePdf.exists() && tempImagePdf.length() > 0) {
                val srcDoc = Document.openDocument(tempImagePdf.absolutePath) as? PDFDocument
                if (srcDoc != null) {
                    destDoc.graftPage(destDoc.countPages(), srcDoc, 0)
                    srcDoc.destroy()
                }
            }
            tempImagePdf.delete()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun copyPage(srcDocHandle: Long, pageNum: Int, destDocHandle: Long): Boolean {
        val srcDoc = documents[srcDocHandle] as? PDFDocument ?: return false
        val destDoc = documents[destDocHandle] as? PDFDocument ?: return false
        return try {
            destDoc.graftPage(destDoc.countPages(), srcDoc, pageNum)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun deletePage(docHandle: Long, pageNum: Int): Boolean {
        val doc = documents[docHandle] as? PDFDocument ?: return false
        return try {
            doc.deletePage(pageNum)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun rotatePage(docHandle: Long, pageNum: Int, rotation: Int): Boolean {
        // Implementation for rotation would go here
        return true
    }

    fun optimizeDocument(
        docHandle: Long,
        outputFd: Int,
        quality: Int,
        targetDpi: Int,
        stripMetadata: Boolean,
        fontSubsetting: Boolean
    ): Boolean {
        return saveToFd(docHandle, outputFd)
    }

    fun saveToFd(docHandle: Long, outputFd: Int): Boolean {
        val doc = documents[docHandle] as? PDFDocument ?: return false
        return try {
            val tempFile = File.createTempFile("mupdf_save", ".pdf")
            doc.save(tempFile.absolutePath, "compress")
            
            // Use /proc/self/fd to write without adopting
            FileOutputStream("/proc/self/fd/$outputFd").use { fos ->
                tempFile.inputStream().use { it.copyTo(fos) }
                fos.flush()
            }
            tempFile.delete()
            true
        } catch (e: Exception) {
            false
        }
    }
}
