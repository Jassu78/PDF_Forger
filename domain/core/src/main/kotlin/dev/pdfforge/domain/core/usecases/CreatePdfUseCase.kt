package dev.pdfforge.domain.core.usecases

import android.net.Uri
import dev.pdfforge.domain.core.tools.ImageToPdfParams
import dev.pdfforge.domain.core.tools.ImageToPdfTool
import dev.pdfforge.domain.models.ErrorCode
import dev.pdfforge.domain.models.OperationResult
import javax.inject.Inject

/**
 * UseCase for creating a PDF from a list of images.
 * Coordinates between the UI and the PDF Engine.
 */
class CreatePdfUseCase @Inject constructor(
    private val imageToPdfTool: ImageToPdfTool
) {
    suspend operator fun invoke(params: ImageToPdfParams): OperationResult<Uri> {
        val validation = imageToPdfTool.validate(params)
        if (!validation.isValid) {
            return OperationResult.Error(
                code = ErrorCode.INSUFFICIENT_INPUT,
                message = "Validation failed: no images or invalid parameters."
            )
        }
        return imageToPdfTool.execute(params)
    }
}
