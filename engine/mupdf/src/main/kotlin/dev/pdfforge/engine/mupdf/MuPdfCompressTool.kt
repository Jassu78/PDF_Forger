package dev.pdfforge.engine.mupdf

import android.content.Context
import android.net.Uri
import com.artifex.mupdf.fitz.Document
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.pdfforge.data.storage.TempFileManager
import dev.pdfforge.domain.models.OperationResult
import dev.pdfforge.domain.core.ValidationResult
import dev.pdfforge.domain.core.tools.CompressPdfParams
import dev.pdfforge.domain.core.tools.CompressPdfTool
import dev.pdfforge.domain.models.ErrorCode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MuPdfCompressTool @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tempFileManager: TempFileManager
) : CompressPdfTool {

    override val id: String = "compress_pdf"
    override val nameRes: Int = 0
    override val iconRes: Int = 0
    override val category = dev.pdfforge.domain.core.ToolCategory.COMPRESSION

    override suspend fun execute(params: CompressPdfParams): OperationResult<Uri> {
        val tempInput = MuPdfHelper.copyUriToTempFile(context, params.sourceUri, tempFileManager)
            ?: return OperationResult.Error(ErrorCode.FILE_NOT_FOUND)

        try {
            val doc = Document.openDocument(tempInput.absolutePath)
            val pdfDoc = doc.asPDF()
                ?: run {
                    doc.destroy()
                    return OperationResult.Error(ErrorCode.CANNOT_OPEN_FILE)
                }

            val saveOpts = buildString {
                append("compress,compress-images,compress-fonts,garbage")
                if (params.strategy.removeMetadata) {
                    append(",clean")
                }
            }

            val outputFile = tempFileManager.createTempFile(".pdf")
            pdfDoc.save(outputFile.absolutePath, saveOpts)
            doc.destroy()

            return OperationResult.Success(Uri.fromFile(outputFile))
        } catch (e: Exception) {
            return OperationResult.Error(ErrorCode.ENGINE_INTERNAL_ERROR, e.message ?: "Compression failed", e)
        } finally {
            tempInput.delete()
        }
    }

    override fun validate(params: CompressPdfParams): ValidationResult {
        return ValidationResult(true)
    }

    override fun cancel() {}
}
