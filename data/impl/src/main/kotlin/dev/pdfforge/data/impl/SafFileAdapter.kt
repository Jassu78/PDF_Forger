package dev.pdfforge.data.impl

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import dev.pdfforge.domain.models.ErrorCode
import dev.pdfforge.domain.models.OperationResult
import dev.pdfforge.domain.models.PdfDocument
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.InputStream
import javax.inject.Inject

/**
 * Implementation of file operations using Android Storage Access Framework.
 */
class SafFileAdapter @Inject constructor(
    private val context: Context
) {
    /**
     * Resolves metadata for a given URI.
     * Defensive against content resolvers that omit OpenableColumns (getColumnIndex returns -1).
     */
    fun getPdfMetadata(uri: Uri): OperationResult<PdfDocument> {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor: Cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex == -1 || sizeIndex == -1) {
                    return@use OperationResult.Error(
                        ErrorCode.CANNOT_OPEN_FILE,
                        message = "Content provider did not return required metadata columns."
                    )
                }
                if (cursor.moveToFirst()) {
                    val name = cursor.getString(nameIndex) ?: "unknown.pdf"
                    val size = cursor.getLong(sizeIndex)
                    OperationResult.Success(
                        PdfDocument(
                            uri = uri,
                            name = name,
                            sizeBytes = size
                        )
                    )
                } else {
                    OperationResult.Error(ErrorCode.FILE_NOT_FOUND)
                }
            } ?: OperationResult.Error(ErrorCode.FILE_NOT_FOUND)
        } catch (e: Exception) {
            OperationResult.Error(ErrorCode.CANNOT_OPEN_FILE, cause = e)
        }
    }

    /**
     * Opens a ParcelFileDescriptor for the engine to read.
     * Essential for NDK/MuPDF integration.
     */
    fun openFileDescriptor(uri: Uri, mode: String = "r"): ParcelFileDescriptor? {
        return try {
            context.contentResolver.openFileDescriptor(uri, mode)
        } catch (e: FileNotFoundException) {
            null
        }
    }

    /**
     * Provides an InputStream for JVM-based engines (PDFBox).
     */
    fun openInputStream(uri: Uri): InputStream? {
        return context.contentResolver.openInputStream(uri)
    }

    /**
     * Provides a FileOutputStream for saving results.
     */
    fun openOutputStream(uri: Uri): FileOutputStream? {
        return context.contentResolver.openOutputStream(uri) as? FileOutputStream
    }
}
