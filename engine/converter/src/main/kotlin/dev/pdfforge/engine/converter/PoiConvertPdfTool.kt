package dev.pdfforge.engine.converter

import android.net.Uri
import dev.pdfforge.data.impl.SafFileAdapter
import dev.pdfforge.data.storage.TempFileManager
import dev.pdfforge.domain.models.OperationResult
import dev.pdfforge.domain.core.ValidationResult
import dev.pdfforge.domain.core.tools.ConvertPdfParams
import dev.pdfforge.domain.core.tools.ConvertPdfTool
import dev.pdfforge.domain.models.ErrorCode
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PoiConvertPdfTool @Inject constructor(
    private val safFileAdapter: SafFileAdapter,
    private val tempFileManager: TempFileManager
) : ConvertPdfTool {

    override val id: String = "convert_pdf"
    override val nameRes: Int = 0 
    override val iconRes: Int = 0 
    override val category = dev.pdfforge.domain.core.ToolCategory.CONVERSION

    override suspend fun execute(params: ConvertPdfParams): OperationResult<Uri> {
        return try {
            // 1. Create temporary output file
            val tempFile = tempFileManager.createTempFile(".docx")
            
            // 2. Initialize Apache POI Document
            val document = XWPFDocument()
            
            // 3. Simple Extraction Heuristic (Placeholder for MuPDF Text Extraction)
            val paragraph = document.createParagraph()
            val run = paragraph.createRun()
            run.setText("Converted from PDF using PDFForge Offline Engine.")
            
            // 4. Save the document
            FileOutputStream(tempFile).use { out: FileOutputStream ->
                document.write(out)
            }
            
            OperationResult.Success(Uri.fromFile(tempFile))
        } catch (e: Exception) {
            OperationResult.Error(ErrorCode.ENGINE_INTERNAL_ERROR, e.message ?: "Conversion failed", e)
        }
    }

    override fun validate(params: ConvertPdfParams): ValidationResult {
        return ValidationResult(true)
    }

    override fun cancel() {
        // Cancellation logic
    }
}
