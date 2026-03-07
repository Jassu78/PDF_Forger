package dev.pdfforge.domain.core.usecases

import dev.pdfforge.domain.core.tools.DocumentToPdfParams
import dev.pdfforge.domain.core.tools.DocumentToPdfTool
import dev.pdfforge.domain.models.ErrorCode
import dev.pdfforge.domain.models.OperationResult
import android.net.Uri
import javax.inject.Inject

/**
 * UseCase for converting documents (DOCX, PPTX) to PDF.
 */
class DocumentToPdfUseCase @Inject constructor(
    private val documentToPdfTool: DocumentToPdfTool
) {
    suspend operator fun invoke(params: DocumentToPdfParams): OperationResult<Uri> {
        if (!documentToPdfTool.supportsMimeType(params.mimeType)) {
            return OperationResult.Error(
                code = ErrorCode.ENGINE_INTERNAL_ERROR,
                message = "Format not supported: ${params.mimeType}"
            )
        }
        val validation = documentToPdfTool.validate(params)
        if (!validation.isValid) {
            return OperationResult.Error(
                code = ErrorCode.INSUFFICIENT_INPUT,
                message = "Validation failed"
            )
        }
        return documentToPdfTool.execute(params)
    }
}
