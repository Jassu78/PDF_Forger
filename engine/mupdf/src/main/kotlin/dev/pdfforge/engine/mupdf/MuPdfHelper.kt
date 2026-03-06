package dev.pdfforge.engine.mupdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.graphics.ImageDecoder
import dev.pdfforge.data.storage.TempFileManager
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

internal object MuPdfHelper {

    /**
     * Reads image bytes suitable for MuPDF's Image() constructor.
     * For HEIF/HEIC (not natively supported by MuPDF), decodes via Android and converts to PNG.
     */
    fun readImageBytesForPdf(context: Context, uri: Uri): ByteArray? {
        val mime = context.contentResolver.getType(uri)?.lowercase() ?: ""
        val path = uri.toString().lowercase()
        val isHeif = mime == "image/heif" || mime == "image/heic" ||
            path.endsWith(".heic") || path.endsWith(".heif")
        return if (isHeif) decodeToPngViaBitmap(context, uri) else readUriToBytes(context, uri)
    }

    /** Decodes any Android-supported image (incl. HEIF) to PNG bytes. Use when MuPDF Image() fails. */
    fun decodeUriToPngBytes(context: Context, uri: Uri): ByteArray? = decodeToPngViaBitmap(context, uri)

    private fun decodeToPngViaBitmap(context: Context, uri: Uri): ByteArray? {
        val bitmap = if (Build.VERSION.SDK_INT >= 28) {
            try {
                ImageDecoder.decodeBitmap(
                    ImageDecoder.createSource(context.contentResolver, uri)
                )
            } catch (_: Exception) {
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it)
                }
            }
        } else {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it)
            }
        } ?: return null
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
            out.toByteArray()
        }
    }

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
