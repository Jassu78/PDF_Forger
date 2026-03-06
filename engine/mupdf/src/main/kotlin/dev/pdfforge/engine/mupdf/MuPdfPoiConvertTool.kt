package dev.pdfforge.engine.mupdf

import android.content.Context
import android.net.Uri
import com.artifex.mupdf.fitz.Document
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.pdfforge.data.storage.TempFileManager
import dev.pdfforge.domain.core.ValidationResult
import dev.pdfforge.domain.core.tools.ConvertPdfParams
import dev.pdfforge.domain.core.tools.ConvertPdfTool
import dev.pdfforge.domain.models.ErrorCode
import dev.pdfforge.domain.models.OperationResult
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MuPdfPoiConvertTool @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tempFileManager: TempFileManager
) : ConvertPdfTool {

    override val id: String = "convert_pdf"
    override val nameRes: Int = 0
    override val iconRes: Int = 0
    override val category = dev.pdfforge.domain.core.ToolCategory.CONVERSION

    override suspend fun execute(params: ConvertPdfParams): OperationResult<Uri> {
        if (params.targetFormat.uppercase() != "DOCX") {
            return OperationResult.Error(
                ErrorCode.ENGINE_INTERNAL_ERROR,
                "${params.targetFormat} export is not yet implemented. Use DOCX for now."
            )
        }
        val tempInput = MuPdfHelper.copyUriToTempFile(context, params.sourceUri, tempFileManager)
            ?: return OperationResult.Error(ErrorCode.FILE_NOT_FOUND)

        try {
            val doc = Document.openDocument(tempInput.absolutePath)
            val pageCount = doc.countPages()

            val wordDoc = XWPFDocument()

            for (i in 0 until pageCount) {
                val page = doc.loadPage(i)
                val stext = page.toStructuredText()
                val blocks = stext.getBlocks()

                for (block in blocks) {
                    for (line in block.lines) {
                        val paragraph = wordDoc.createParagraph()
                        val run = paragraph.createRun()
                        val lineText = buildString {
                            for (ch in line.chars) {
                                append(ch.c.toChar())
                            }
                        }
                        run.setText(lineText)
                    }
                }
                stext.destroy()
                page.destroy()
            }

            val outputFile = tempFileManager.createOutputFile(params.outputName)
            FileOutputStream(outputFile).use { out ->
                wordDoc.write(out)
            }

            doc.destroy()
            return OperationResult.Success(Uri.fromFile(outputFile))
        } catch (e: Exception) {
            return OperationResult.Error(ErrorCode.ENGINE_INTERNAL_ERROR, e.message ?: "Conversion failed", e)
        } finally {
            tempInput.delete()
        }
    }

    override fun validate(params: ConvertPdfParams): ValidationResult {
        return ValidationResult(true)
    }

    override fun cancel() {}
}
