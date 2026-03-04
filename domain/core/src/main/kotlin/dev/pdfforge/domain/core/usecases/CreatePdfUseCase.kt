package dev.pdfforge.domain.core.usecases

import android.net.Uri
import dev.pdfforge.domain.core.OperationResult
import dev.pdfforge.domain.core.tools.ImageToPdfParams
import dev.pdfforge.domain.core.tools.ImageToPdfTool
import javax.inject.Inject

/**
 * UseCase for creating a PDF from a list of images.
 * Coordinates between the UI and the PDF Engine.
 */
class CreatePdfUseCase @Inject constructor(
    private val imageToPdfTool: ImageToPdfTool
) {
    suspend operator fun invoke(params: ImageToPdfParams): OperationResult<Uri> {
        // Validation logic can be added here
        val validation = imageToPdfTool.validate(params)
        if (!validation.isValid) {
            // Return error based on validation
        }
        
        return imageToPdfTool.execute(params)
    }
}
