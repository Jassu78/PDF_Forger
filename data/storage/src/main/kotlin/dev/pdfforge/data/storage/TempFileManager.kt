package dev.pdfforge.data.storage

import android.content.Context
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages temporary files used during PDF processing.
 * Ensures that intermediate files are cleaned up to prevent storage bloat.
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

    /**
     * Creates a new temporary file with a unique name.
     * @param suffix The file extension (e.g., ".pdf", ".jpg").
     */
    fun createTempFile(suffix: String = ".tmp"): File {
        val fileName = "temp_${UUID.randomUUID()}$suffix"
        return File(tempDir, fileName)
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
