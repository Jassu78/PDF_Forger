package dev.pdfforge.engine.mupdf

import android.net.Uri
import android.os.ParcelFileDescriptor
import dev.pdfforge.data.impl.SafFileAdapter
import dev.pdfforge.data.storage.TempFileManager
import dev.pdfforge.domain.core.OperationResult
import dev.pdfforge.domain.core.ValidationResult
import dev.pdfforge.domain.core.tools.SplitPdfParams
import dev.pdfforge.domain.core.tools.SplitPdfTool
import dev.pdfforge.domain.models.ErrorCode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MuPdfSplitTool @Inject constructor(
    private val safFileAdapter: SafFileAdapter,
    private val tempFileManager: TempFileManager
) : SplitPdfTool {

    override val id: String = "split_pdf"
    override val nameRes: Int = 0 
    override val iconRes: Int = 0 
    override val category = dev.pdfforge.domain.core.ToolCategory.OPERATIONS

    override suspend fun execute(params: SplitPdfParams): OperationResult<Uri> {
        val pfd = safFileAdapter.openFileDescriptor(params.sourceUri)
            ?: return OperationResult.Error(ErrorCode.FILE_NOT_FOUND)

        val srcDocHandle = MuPdfJni.openFromFd(pfd.fd)
        if (srcDocHandle == 0L) {
            pfd.close()
            return OperationResult.Error(ErrorCode.CANNOT_OPEN_FILE)
        }

        val destDocHandle = MuPdfJni.createNewDocument()
        if (destDocHandle == 0L) {
            MuPdfJni.closeDocument(srcDocHandle)
            pfd.close()
            return OperationResult.Error(ErrorCode.ENGINE_INTERNAL_ERROR)
        }

        try {
            val pageCount = MuPdfJni.getPageCount(srcDocHandle)
            
            params.pageRanges.forEach { range ->
                for (i in range.start..range.end) {
                    if (i in 0 until pageCount) {
                        MuPdfJni.copyPage(srcDocHandle, i, destDocHandle)
                    }
                }
            }

            val tempFile = tempFileManager.createTempFile(".pdf")
            val outputPfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_WRITE)
            
            val saved = MuPdfJni.saveToFd(destDocHandle, outputPfd.fd)
            outputPfd.close()

            return if (saved) {
                OperationResult.Success(Uri.fromFile(tempFile))
            } else {
                OperationResult.Error(ErrorCode.CANNOT_WRITE_FILE)
            }
        } finally {
            MuPdfJni.closeDocument(srcDocHandle)
            MuPdfJni.closeDocument(destDocHandle)
            pfd.close()
        }
    }

    override fun validate(params: SplitPdfParams): ValidationResult {
        return ValidationResult(params.pageRanges.isNotEmpty())
    }

    override fun cancel() {}
}
