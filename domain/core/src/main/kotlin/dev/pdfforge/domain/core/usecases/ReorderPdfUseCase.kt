package dev.pdfforge.domain.core.usecases

import android.net.Uri
import dev.pdfforge.domain.core.tools.ReorderPdfParams
import dev.pdfforge.domain.core.tools.ReorderPdfTool
import dev.pdfforge.domain.models.ErrorCode
import dev.pdfforge.domain.models.OperationResult
import javax.inject.Inject

/**
 * Use case for reordering and rotating PDF pages into a new PDF.
 */
class ReorderPdfUseCase @Inject constructor(
    private val reorderPdfTool: ReorderPdfTool
) {
    suspend operator fun invoke(params: ReorderPdfParams): OperationResult<Uri> {
        val validation = reorderPdfTool.validate(params)
        if (!validation.isValid) {
            return OperationResult.Error(
                code = ErrorCode.INSUFFICIENT_INPUT,
                message = "Validation failed: provide at least one page."
            )
        }
        return reorderPdfTool.execute(params)
    }
}
