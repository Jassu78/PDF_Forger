package dev.pdfforge.engine.mupdf

import android.net.Uri
import android.os.ParcelFileDescriptor
import dev.pdfforge.data.impl.SafFileAdapter
import dev.pdfforge.data.storage.TempFileManager
import dev.pdfforge.domain.models.OperationResult
import dev.pdfforge.domain.core.ValidationResult
import dev.pdfforge.domain.core.tools.CompressPdfParams
import dev.pdfforge.domain.core.tools.CompressPdfTool
import dev.pdfforge.domain.models.ErrorCode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MuPdfCompressTool @Inject constructor(
    private val safFileAdapter: SafFileAdapter,
    private val tempFileManager: TempFileManager
) : CompressPdfTool {

    override val id: String = "compress_pdf"
    override val nameRes: Int = 0 
    override val iconRes: Int = 0 
    override val category = dev.pdfforge.domain.core.ToolCategory.COMPRESSION

    override suspend fun execute(params: CompressPdfParams): OperationResult<Uri> {
        val pfd = safFileAdapter.openFileDescriptor(params.sourceUri)
            ?: return OperationResult.Error(ErrorCode.FILE_NOT_FOUND)

        val docHandle = MuPdfJni.openFromFd(pfd.fd)
        if (docHandle == 0L) {
            pfd.close()
            return OperationResult.Error(ErrorCode.CANNOT_OPEN_FILE)
        }

        try {
            val tempFile = tempFileManager.createTempFile(".pdf")
            val outputPfd = ParcelFileDescriptor.open(
                tempFile,
                ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE
            )

            val success = MuPdfJni.optimizeDocument(
                docHandle = docHandle,
                outputFd = outputPfd.fd,
                quality = params.strategy.imageQualityPercent,
                targetDpi = params.strategy.targetDpi,
                stripMetadata = params.strategy.removeMetadata,
                fontSubsetting = params.strategy.fontSubsetting
            )
            
            outputPfd.close()

            return if (success) {
                OperationResult.Success(Uri.fromFile(tempFile))
            } else {
                OperationResult.Error(ErrorCode.ENGINE_INTERNAL_ERROR, "Compression failed")
            }
        } catch (e: Exception) {
            return OperationResult.Error(ErrorCode.ENGINE_INTERNAL_ERROR, e.message ?: "Unknown error", e)
        } finally {
            MuPdfJni.closeDocument(docHandle)
            pfd.close()
        }
    }

    override fun validate(params: CompressPdfParams): ValidationResult {
        return ValidationResult(true)
    }

    override fun cancel() {
        // Native cancellation
    }
}
