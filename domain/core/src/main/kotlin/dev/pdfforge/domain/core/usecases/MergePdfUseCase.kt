package dev.pdfforge.domain.core.usecases

import android.net.Uri
import dev.pdfforge.domain.models.OperationResult
import dev.pdfforge.domain.core.tools.MergePdfParams
import dev.pdfforge.domain.core.tools.MergePdfTool
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
            // Error handling for validation
        }
        
        return mergePdfTool.execute(params)
    }
}
