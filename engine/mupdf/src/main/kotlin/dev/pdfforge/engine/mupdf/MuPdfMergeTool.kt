package dev.pdfforge.engine.mupdf

import android.net.Uri
import android.os.ParcelFileDescriptor
import dev.pdfforge.data.impl.SafFileAdapter
import dev.pdfforge.data.storage.TempFileManager
import dev.pdfforge.domain.core.OperationResult
import dev.pdfforge.domain.core.ValidationResult
import dev.pdfforge.domain.core.tools.MergePdfParams
import dev.pdfforge.domain.core.tools.MergePdfTool
import dev.pdfforge.domain.models.ErrorCode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MuPdfMergeTool @Inject constructor(
    private val safFileAdapter: SafFileAdapter,
    private val tempFileManager: TempFileManager
) : MergePdfTool {

    override val id: String = "merge_pdf"
    override val nameRes: Int = 0 
    override val iconRes: Int = 0 
    override val category = dev.pdfforge.domain.core.ToolCategory.OPERATIONS

    override suspend fun execute(params: MergePdfParams): OperationResult<Uri> {
        if (params.sourceUris.isEmpty()) {
            return OperationResult.Error(ErrorCode.INSUFFICIENT_INPUT)
        }

        val destDocHandle = MuPdfJni.createNewDocument()
        if (destDocHandle == 0L) {
            return OperationResult.Error(ErrorCode.ENGINE_INTERNAL_ERROR, "Failed to create destination PDF")
        }

        try {
            params.sourceUris.forEach { uri ->
                val pfd = safFileAdapter.openFileDescriptor(uri)
                if (pfd != null) {
                    val srcDocHandle = MuPdfJni.openFromFd(pfd.fd)
                    if (srcDocHandle != 0L) {
                        val pageCount = MuPdfJni.getPageCount(srcDocHandle)
                        for (i in 0 until pageCount) {
                            val success = MuPdfJni.copyPage(srcDocHandle, i, destDocHandle)
                            if (!success) {
                                MuPdfJni.closeDocument(srcDocHandle)
                                pfd.close()
                                return OperationResult.Error(ErrorCode.ENGINE_INTERNAL_ERROR, "Failed to copy page from ${uri.path}")
                            }
                        }
                        MuPdfJni.closeDocument(srcDocHandle)
                    } else {
                        pfd.close()
                        return OperationResult.Error(ErrorCode.CANNOT_OPEN_FILE, "Failed to open source PDF: ${uri.path}")
                    }
                    pfd.close()
                } else {
                    return OperationResult.Error(ErrorCode.FILE_NOT_FOUND, "Source file not found: ${uri.path}")
                }
            }

            // Save the merged document to a temporary file
            val tempFile = tempFileManager.createTempFile(".pdf")
            val outputPfd = ParcelFileDescriptor.open(
                tempFile,
                ParcelFileDescriptor.MODE_READ_WRITE
            )
            
            val saved = MuPdfJni.saveToFd(destDocHandle, outputPfd.fd)
            outputPfd.close()

            return if (saved) {
                OperationResult.Success(Uri.fromFile(tempFile))
            } else {
                OperationResult.Error(ErrorCode.CANNOT_WRITE_FILE)
            }
        } catch (e: Exception) {
            return OperationResult.Error(ErrorCode.ENGINE_INTERNAL_ERROR, e.message ?: "Unknown error during merge", e)
        } finally {
            MuPdfJni.closeDocument(destDocHandle)
        }
    }

    override fun validate(params: MergePdfParams): ValidationResult {
        return if (params.sourceUris.size < 2) {
            ValidationResult(false)
        } else {
            ValidationResult(true)
        }
    }

    override fun cancel() {
        // Native cancellation logic if applicable
    }
}
