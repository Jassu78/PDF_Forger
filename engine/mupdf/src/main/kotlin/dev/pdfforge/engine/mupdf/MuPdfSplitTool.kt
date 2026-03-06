package dev.pdfforge.engine.mupdf

import android.content.Context
import android.net.Uri
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.PDFDocument
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.pdfforge.data.storage.TempFileManager
import dev.pdfforge.domain.models.OperationResult
import dev.pdfforge.domain.core.ValidationResult
import dev.pdfforge.domain.core.tools.SplitPdfParams
import dev.pdfforge.domain.core.tools.SplitPdfTool
import dev.pdfforge.domain.models.ErrorCode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MuPdfSplitTool @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tempFileManager: TempFileManager
) : SplitPdfTool {

    override val id: String = "split_pdf"
    override val nameRes: Int = 0
    override val iconRes: Int = 0
    override val category = dev.pdfforge.domain.core.ToolCategory.OPERATIONS

    override suspend fun execute(params: SplitPdfParams): OperationResult<Uri> {
        val tempInput = MuPdfHelper.copyUriToTempFile(context, params.sourceUri, tempFileManager)
            ?: return OperationResult.Error(ErrorCode.FILE_NOT_FOUND)

        try {
            val srcDoc = Document.openDocument(tempInput.absolutePath)
            val pdfSrc = srcDoc.asPDF()
                ?: run {
                    srcDoc.destroy()
                    return OperationResult.Error(ErrorCode.CANNOT_OPEN_FILE)
                }

            val destDoc = PDFDocument()
            val pageCount = pdfSrc.countPages()

            for (range in params.pageRanges) {
                for (i in range.start..range.end) {
                    if (i in 0 until pageCount) {
                        destDoc.graftPage(-1, pdfSrc, i)
                    }
                }
            }

            val outputFile = tempFileManager.createOutputFile(params.outputName)
            destDoc.save(outputFile.absolutePath, "compress")

            destDoc.destroy()
            srcDoc.destroy()

            return OperationResult.Success(Uri.fromFile(outputFile))
        } catch (e: Exception) {
            return OperationResult.Error(ErrorCode.ENGINE_INTERNAL_ERROR, e.message ?: "Split failed", e)
        } finally {
            tempInput.delete()
        }
    }

    override fun validate(params: SplitPdfParams): ValidationResult {
        return ValidationResult(params.pageRanges.isNotEmpty())
    }

    override fun cancel() {}
}
