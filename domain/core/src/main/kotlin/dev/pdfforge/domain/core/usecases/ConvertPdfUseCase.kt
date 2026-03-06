package dev.pdfforge.domain.core.usecases

import android.net.Uri
import dev.pdfforge.domain.core.tools.ConvertPdfParams
import dev.pdfforge.domain.core.tools.ConvertPdfTool
import dev.pdfforge.domain.models.ErrorCode
import dev.pdfforge.domain.models.OperationResult
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
            return OperationResult.Error(
                code = ErrorCode.UNSUPPORTED_FORMAT,
                message = "Validation failed: invalid source or unsupported conversion target."
            )
        }
        return convertPdfTool.execute(params)
    }
}
