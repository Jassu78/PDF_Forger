package dev.pdfforge.engine.mupdf

import android.net.Uri
import dev.pdfforge.data.impl.SafFileAdapter
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
    private val safFileAdapter: SafFileAdapter,
    private val tempFileManager: TempFileManager
) : ConvertPdfTool {

    override val id: String = "convert_pdf"
    override val nameRes: Int = 0
    override val iconRes: Int = 0
    override val category = dev.pdfforge.domain.core.ToolCategory.CONVERSION

    override suspend fun execute(params: ConvertPdfParams): OperationResult<Uri> {
        val pfd = safFileAdapter.openFileDescriptor(params.sourceUri)
            ?: return OperationResult.Error(ErrorCode.FILE_NOT_FOUND)

        val docHandle = MuPdfJni.openFromFd(pfd.fd)
        if (docHandle == 0L) {
            pfd.close()
            return OperationResult.Error(ErrorCode.CANNOT_OPEN_FILE)
        }

        return try {
            val tempFile = tempFileManager.createTempFile(".docx")
            val doc = XWPFDocument()

            val pageCount = MuPdfJni.getPageCount(docHandle)

            for (i in 0 until pageCount) {
                val textBlocks = MuPdfJni.extractTextBlocks(docHandle, i)

                var lastY = -1f
                var currentParagraph = doc.createParagraph()

                textBlocks.forEach { block ->
                    val run = currentParagraph.createRun()
                    run.setText(block)
                    run.addBreak()
                }
            }

            FileOutputStream(tempFile).use { out: FileOutputStream ->
                doc.write(out)
            }

            OperationResult.Success(Uri.fromFile(tempFile))
        } catch (e: Exception) {
            OperationResult.Error(ErrorCode.ENGINE_INTERNAL_ERROR, e.message ?: "Conversion failed", e)
        } finally {
            MuPdfJni.closeDocument(docHandle)
            pfd.close()
        }
    }

    override fun validate(params: ConvertPdfParams): ValidationResult {
        return ValidationResult(true)
    }

    override fun cancel() {}
}
