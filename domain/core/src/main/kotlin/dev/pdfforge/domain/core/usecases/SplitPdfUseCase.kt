package dev.pdfforge.domain.core.usecases

import android.net.Uri
import dev.pdfforge.domain.core.tools.SplitPdfParams
import dev.pdfforge.domain.core.tools.SplitPdfTool
import dev.pdfforge.domain.models.ErrorCode
import dev.pdfforge.domain.models.OperationResult
import javax.inject.Inject

/**
 * Use case for splitting a PDF by page ranges (extract selected pages into a new PDF).
 */
class SplitPdfUseCase @Inject constructor(
    private val splitPdfTool: SplitPdfTool
) {
    suspend operator fun invoke(params: SplitPdfParams): OperationResult<Uri> {
        val validation = splitPdfTool.validate(params)
        if (!validation.isValid) {
            return OperationResult.Error(
                code = ErrorCode.INSUFFICIENT_INPUT,
                message = "Validation failed: provide at least one page range."
            )
        }
        return splitPdfTool.execute(params)
    }
}
