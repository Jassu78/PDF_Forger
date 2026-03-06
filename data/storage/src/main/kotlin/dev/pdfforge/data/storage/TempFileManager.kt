package dev.pdfforge.data.storage

import android.content.Context
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages temporary and output files used during PDF processing.
 * Temporary files go to cache (cleaned up by system). Output files go to a persistent directory.
 */
@Singleton
class TempFileManager @Inject constructor(
    private val context: Context
) {
    private val tempDir: File by lazy {
        File(context.cacheDir, "pdf_forge_temp").apply {
            if (!exists()) mkdirs()
        }
    }

    /** Persistent output directory for created PDFs (e.g. Image-to-PDF, Merge). */
    val outputDir: File
        get() = File(context.filesDir, "output").apply { if (!exists()) mkdirs() }

    /**
     * Creates a new temporary file with a unique name.
     * @param suffix The file extension (e.g., ".pdf", ".jpg").
     */
    fun createTempFile(suffix: String = ".tmp"): File {
        val fileName = "temp_${UUID.randomUUID()}$suffix"
        return File(tempDir, fileName)
    }

    private val knownExtensions = setOf(".pdf", ".docx", ".pptx", ".txt", ".md", ".png", ".jpg", ".jpeg", ".zip")

    /**
     * Creates a persistent output file for user-facing outputs. Use for final outputs
     * (Image-to-PDF, Merge, Convert, etc.) so they survive cache cleanup and can be opened/shared.
     * @param outputName Desired filename (e.g. "Export.pdf", "Converted.docx"); path separators sanitized.
     *                   If no known extension, ".pdf" is appended.
     */
    fun createOutputFile(outputName: String): File {
        val safeName = outputName
            .substringAfterLast('/')
            .takeIf { it.isNotBlank() } ?: "output_${UUID.randomUUID()}.pdf"
        val baseName = if (knownExtensions.any { safeName.lowercase().endsWith(it) }) {
            safeName
        } else {
            "$safeName.pdf"
        }
        return File(outputDir, baseName)
    }

    /**
     * Cleans up all files in the temporary directory.
     * Should be called on app startup or periodically.
     */
    fun clearAllTempFiles() {
        tempDir.listFiles()?.forEach { it.delete() }
    }

    /**
     * Deletes a specific temporary file.
     */
    fun deleteFile(file: File): Boolean {
        return if (file.exists() && file.parentFile == tempDir) {
            file.delete()
        } else false
    }
}
