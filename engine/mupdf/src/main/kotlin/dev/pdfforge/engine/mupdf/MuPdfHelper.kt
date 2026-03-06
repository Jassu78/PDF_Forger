package dev.pdfforge.engine.mupdf

import android.content.Context
import android.net.Uri
import dev.pdfforge.data.storage.TempFileManager
import java.io.File
import java.io.FileOutputStream

internal object MuPdfHelper {

    fun copyUriToTempFile(
        context: Context,
        uri: Uri,
        tempFileManager: TempFileManager,
        extension: String = ".pdf"
    ): File? {
        val tempFile = tempFileManager.createTempFile(extension)
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        inputStream.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }
        return tempFile
    }

    fun readUriToBytes(context: Context, uri: Uri): ByteArray? {
        return context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    }
}
