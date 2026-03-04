package dev.pdfforge.domain.core.usecases

import android.net.Uri
import dev.pdfforge.domain.core.OperationResult
import dev.pdfforge.domain.core.tools.ConvertPdfParams
import dev.pdfforge.domain.core.tools.ConvertPdfTool
import javax.inject.Inject

/**
 * UseCase for converting a PDF file into other formats (DOCX, PPTX, etc.).
 */
class ConvertPdfUseCase @Inject constructor(
    private val convertPdfTool: ConvertPdfTool
) {
    suspend operator fun invoke(params: ConvertPdfParams): OperationResult<Uri> {
        val validation = convertPdfTool.validate(params)
        if (!validation.isValid) {
            // Handle validation error
        }
        
        return convertPdfTool.execute(params)
    }
}
