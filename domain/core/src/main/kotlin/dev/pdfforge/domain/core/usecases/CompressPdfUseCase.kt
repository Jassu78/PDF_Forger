package dev.pdfforge.domain.core.usecases

import android.net.Uri
import dev.pdfforge.domain.core.tools.CompressPdfParams
import dev.pdfforge.domain.core.tools.CompressPdfTool
import dev.pdfforge.domain.models.ErrorCode
import dev.pdfforge.domain.models.OperationResult
import javax.inject.Inject

/**
 * UseCase for compressing a PDF file using multiple strategies.
 */
class CompressPdfUseCase @Inject constructor(
    private val compressPdfTool: CompressPdfTool
) {
    suspend operator fun invoke(params: CompressPdfParams): OperationResult<Uri> {
        val validation = compressPdfTool.validate(params)
        if (!validation.isValid) {
            return OperationResult.Error(
                code = ErrorCode.INVALID_PDF,
                message = "Validation failed: invalid or unsupported PDF for compression."
            )
        }
        return compressPdfTool.execute(params)
    }
}
