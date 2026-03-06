package dev.pdfforge.engine.mupdf

import android.content.Context
import android.net.Uri
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.PDFDocument
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.pdfforge.data.storage.TempFileManager
import dev.pdfforge.domain.models.OperationResult
import dev.pdfforge.domain.core.ValidationResult
import dev.pdfforge.domain.core.tools.MergePdfParams
import dev.pdfforge.domain.core.tools.MergePdfTool
import dev.pdfforge.domain.models.ErrorCode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MuPdfMergeTool @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tempFileManager: TempFileManager
) : MergePdfTool {

    override val id: String = "merge_pdf"
    override val nameRes: Int = 0
    override val iconRes: Int = 0
    override val category = dev.pdfforge.domain.core.ToolCategory.OPERATIONS

    override suspend fun execute(params: MergePdfParams): OperationResult<Uri> {
        if (params.sourceUris.isEmpty()) {
            return OperationResult.Error(ErrorCode.INSUFFICIENT_INPUT)
        }

        val destDoc = PDFDocument()
        val tempInputFiles = mutableListOf<java.io.File>()

        try {
            for (uri in params.sourceUris) {
                val tempInput = MuPdfHelper.copyUriToTempFile(context, uri, tempFileManager)
                    ?: return OperationResult.Error(ErrorCode.FILE_NOT_FOUND, "Cannot read: ${uri.path}")
                tempInputFiles.add(tempInput)

                val srcDoc = Document.openDocument(tempInput.absolutePath)
                val pdfSrc = srcDoc.asPDF()
                    ?: run {
                        srcDoc.destroy()
                        return OperationResult.Error(ErrorCode.CANNOT_OPEN_FILE, "Not a PDF: ${uri.path}")
                    }

                val pageCount = pdfSrc.countPages()
                for (i in 0 until pageCount) {
                    destDoc.graftPage(-1, pdfSrc, i)
                }
                srcDoc.destroy()
            }

            val outputFile = tempFileManager.createTempFile(".pdf")
            destDoc.save(outputFile.absolutePath, "compress")

            return OperationResult.Success(Uri.fromFile(outputFile))
        } catch (e: Exception) {
            return OperationResult.Error(ErrorCode.ENGINE_INTERNAL_ERROR, e.message ?: "Merge failed", e)
        } finally {
            destDoc.destroy()
            tempInputFiles.forEach { it.delete() }
        }
    }

    override fun validate(params: MergePdfParams): ValidationResult {
        return ValidationResult(params.sourceUris.size >= 2)
    }

    override fun cancel() {}
}
