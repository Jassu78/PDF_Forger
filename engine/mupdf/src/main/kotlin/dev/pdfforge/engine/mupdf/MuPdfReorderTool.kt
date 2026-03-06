package dev.pdfforge.engine.mupdf

import android.content.Context
import android.net.Uri
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.PDFDocument
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.pdfforge.data.storage.TempFileManager
import dev.pdfforge.domain.models.OperationResult
import dev.pdfforge.domain.core.ValidationResult
import dev.pdfforge.domain.core.tools.ReorderPdfParams
import dev.pdfforge.domain.core.tools.ReorderPdfTool
import dev.pdfforge.domain.models.ErrorCode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MuPdfReorderTool @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tempFileManager: TempFileManager
) : ReorderPdfTool {

    override val id: String = "reorder_pdf"
    override val nameRes: Int = 0
    override val iconRes: Int = 0
    override val category = dev.pdfforge.domain.core.ToolCategory.OPERATIONS

    override suspend fun execute(params: ReorderPdfParams): OperationResult<Uri> {
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
            val srcPageCount = pdfSrc.countPages()

            for (item in params.pageOrder) {
                if (item.pageIndex !in 0 until srcPageCount) continue
                destDoc.graftPage(-1, pdfSrc, item.pageIndex)

                if (item.rotation != 0) {
                    val destPageCount = destDoc.countPages()
                    val pageObj = destDoc.findPage(destPageCount - 1)
                    val currentRotation = pageObj.get("Rotate").asInteger()
                    pageObj.put("Rotate", destDoc.newInteger((currentRotation + item.rotation) % 360))
                }
            }

            val outputFile = tempFileManager.createOutputFile(params.outputName)
            destDoc.save(outputFile.absolutePath, "compress")

            destDoc.destroy()
            srcDoc.destroy()

            return OperationResult.Success(Uri.fromFile(outputFile))
        } catch (e: Exception) {
            return OperationResult.Error(ErrorCode.ENGINE_INTERNAL_ERROR, e.message ?: "Reorder failed", e)
        } finally {
            tempInput.delete()
        }
    }

    override fun validate(params: ReorderPdfParams): ValidationResult {
        return ValidationResult(params.pageOrder.isNotEmpty())
    }

    override fun cancel() {}
}
