package dev.pdfforge.engine.mupdf

/**
 * JNI bridge for MuPDF C library.
 * This class contains the native methods that call into the C++ layer.
 */
object MuPdfJni {
    init {
        System.loadLibrary("mupdf_bridge")
    }

    /**
     * Opens a PDF document from a File Descriptor.
     * @param fd The file descriptor from ParcelFileDescriptor.
     * @return A handle (Long) to the native document object, or 0 if it fails.
     */
    external fun openFromFd(fd: Int): Long

    /**
     * Closes a document and releases its native resources.
     * @param handle The handle returned by openFromFd.
     */
    external fun closeDocument(handle: Long)

    /**
     * Returns the total number of pages in the document.
     */
    external fun getPageCount(handle: Long): Int

    /**
     * Extracts text blocks from a specific page.
     * @param docHandle The handle to the native document.
     * @param pageNum The page number (0-indexed).
     * @return A list of strings representing text blocks.
     */
    external fun extractTextBlocks(docHandle: Long, pageNum: Int): Array<String>

    /**
     * Creates a new empty PDF document.
     * @return A handle (Long) to the new native document object.
     */
    external fun createNewDocument(): Long

    /**
     * Adds an image as a new page to the document.
     * @param docHandle The handle to the native document.
     * @param imageFd The file descriptor of the image file.
     * @param quality The compression quality (0-100).
     * @param width Target page width in points.
     * @param height Target page height in points.
     */
    external fun addImagePage(docHandle: Long, imageFd: Int, quality: Int, width: Int, height: Int): Boolean

    /**
     * Copies a page from a source document to a destination document.
     * @param srcDocHandle The source document handle.
     * @param pageNum The page number to copy (0-indexed).
     * @param destDocHandle The destination document handle.
     */
    external fun copyPage(srcDocHandle: Long, pageNum: Int, destDocHandle: Long): Boolean

    /**
     * Deletes a page from the document.
     * @param docHandle The handle to the native document.
     * @param pageNum The page number to delete (0-indexed).
     */
    external fun deletePage(docHandle: Long, pageNum: Int): Boolean

    /**
     * Rotates a page in the document.
     * @param docHandle The handle to the native document.
     * @param pageNum The page number to rotate (0-indexed).
     * @param rotation Relative rotation in degrees (e.g., 90, 180, 270).
     */
    external fun rotatePage(docHandle: Long, pageNum: Int, rotation: Int): Boolean

    /**
     * Optimizes and compresses the document based on provided parameters.
     * @param docHandle The handle to the native document.
     * @param outputFd The file descriptor of the output file.
     * @param quality Image quality (0-100).
     * @param targetDpi Target DPI for downscaling.
     * @param stripMetadata Whether to remove metadata.
     * @param fontSubsetting Whether to subset fonts.
     * @return true if optimization was successful.
     */
    external fun optimizeDocument(
        docHandle: Long,
        outputFd: Int,
        quality: Int,
        targetDpi: Int,
        stripMetadata: Boolean,
        fontSubsetting: Boolean
    ): Boolean

    /**
     * Saves the document to a file descriptor.
     * @param docHandle The handle to the native document.
     * @param outputFd The file descriptor of the output file.
     */
    external fun saveToFd(docHandle: Long, outputFd: Int): Boolean
}
