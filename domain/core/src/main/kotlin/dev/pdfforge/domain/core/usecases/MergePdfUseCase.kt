package dev.pdfforge.domain.core.usecases

import android.net.Uri
import dev.pdfforge.domain.core.tools.MergePdfParams
import dev.pdfforge.domain.core.tools.MergePdfTool
import dev.pdfforge.domain.models.ErrorCode
import dev.pdfforge.domain.models.OperationResult
import javax.inject.Inject

/**
 * UseCase for merging multiple PDF files into one.
 * Orchestrates the merge operation between the UI and the PDF Engine.
 */
class MergePdfUseCase @Inject constructor(
    private val mergePdfTool: MergePdfTool
) {
    suspend operator fun invoke(params: MergePdfParams): OperationResult<Uri> {
        val validation = mergePdfTool.validate(params)
        if (!validation.isValid) {
            return OperationResult.Error(
                code = ErrorCode.INSUFFICIENT_INPUT,
                message = "Validation failed: at least two PDFs are required to merge."
            )
        }
        return mergePdfTool.execute(params)
    }
}
