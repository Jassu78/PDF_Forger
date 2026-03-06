package dev.pdfforge.domain.core.usecases

import android.net.Uri
import dev.pdfforge.domain.core.tools.PdfInfoTool
import dev.pdfforge.domain.models.OperationResult
import javax.inject.Inject

/**
 * Returns the page count of a PDF (for UI that needs it before running an operation).
 */
class GetPdfPageCountUseCase @Inject constructor(
    private val pdfInfoTool: PdfInfoTool
) {
    suspend operator fun invoke(uri: Uri): OperationResult<Int> {
        return pdfInfoTool.getPageCount(uri)
    }
}
