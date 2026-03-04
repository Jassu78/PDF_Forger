package dev.pdfforge.domain.core.usecases

import android.net.Uri
import dev.pdfforge.domain.core.OperationResult
import dev.pdfforge.domain.core.tools.CompressPdfParams
import dev.pdfforge.domain.core.tools.CompressPdfTool
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
            // Handle validation error
        }
        
        return compressPdfTool.execute(params)
    }
}
