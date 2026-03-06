package dev.pdfforge.engine.mupdf

import android.net.Uri
import dev.pdfforge.data.impl.SafFileAdapter
import dev.pdfforge.data.storage.TempFileManager
import dev.pdfforge.domain.models.OperationResult
import dev.pdfforge.domain.core.ValidationResult
import dev.pdfforge.domain.core.tools.ImageToPdfParams
import dev.pdfforge.domain.core.tools.ImageToPdfTool
import dev.pdfforge.domain.models.ErrorCode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MuPdfImageToPdfTool @Inject constructor(
    private val safFileAdapter: SafFileAdapter,
    private val tempFileManager: TempFileManager
) : ImageToPdfTool {

    override val id: String = "image_to_pdf"
    override val nameRes: Int = 0 // Resource ID for name
    override val iconRes: Int = 0 // Resource ID for icon
    override val category = dev.pdfforge.domain.core.ToolCategory.CREATION

    override suspend fun execute(params: ImageToPdfParams): OperationResult<Uri> {
        val docHandle = MuPdfJni.createNewDocument()
        if (docHandle == 0L) {
            return OperationResult.Error(ErrorCode.ENGINE_INTERNAL_ERROR, "Failed to create PDF document")
        }

        try {
            params.imageUris.forEach { uri ->
                val pfd = safFileAdapter.openFileDescriptor(uri)
                if (pfd != null) {
                    val success = MuPdfJni.addImagePage(
                        docHandle = docHandle,
                        imageFd = pfd.fd,
                        quality = params.quality,
                        width = 595, // A4 Width in points
                        height = 842 // A4 Height in points
                    )
                    pfd.close()
                    if (!success) {
                        return OperationResult.Error(ErrorCode.ENGINE_INTERNAL_ERROR, "Failed to add image to PDF")
                    }
                }
            }

            // Save to a temporary file first
            val tempFile = tempFileManager.createTempFile(".pdf")
            val outputPfd = android.os.ParcelFileDescriptor.open(
                tempFile,
                android.os.ParcelFileDescriptor.MODE_READ_WRITE
            )
            
            val saved = MuPdfJni.saveToFd(docHandle, outputPfd.fd)
            outputPfd.close()

            return if (saved) {
                OperationResult.Success(Uri.fromFile(tempFile))
            } else {
                OperationResult.Error(ErrorCode.CANNOT_WRITE_FILE)
            }
        } finally {
            MuPdfJni.closeDocument(docHandle)
        }
    }

    override fun validate(params: ImageToPdfParams): ValidationResult {
        return if (params.imageUris.isEmpty()) {
            ValidationResult(false)
        } else {
            ValidationResult(true)
        }
    }

    override fun cancel() {
        // Cancellation logic for native engine
    }
}
