package dev.pdfforge.engine.mupdf

import android.content.Context
import android.net.Uri
import com.artifex.mupdf.fitz.Document
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.pdfforge.data.storage.TempFileManager
import dev.pdfforge.domain.models.OperationResult
import dev.pdfforge.domain.core.tools.PdfInfoTool
import dev.pdfforge.domain.models.ErrorCode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MuPdfPdfInfoTool @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tempFileManager: TempFileManager
) : PdfInfoTool {

    override suspend fun getPageCount(uri: Uri): OperationResult<Int> {
        val tempInput = MuPdfHelper.copyUriToTempFile(context, uri, tempFileManager)
            ?: return OperationResult.Error(ErrorCode.FILE_NOT_FOUND)

        return try {
            val doc = Document.openDocument(tempInput.absolutePath)
            val count = doc.countPages()
            doc.destroy()
            OperationResult.Success(count)
        } catch (e: Exception) {
            OperationResult.Error(ErrorCode.CANNOT_OPEN_FILE, e.message ?: "Cannot open PDF", e)
        } finally {
            tempInput.delete()
        }
    }
}
