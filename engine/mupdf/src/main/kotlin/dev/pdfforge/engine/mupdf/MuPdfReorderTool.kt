package dev.pdfforge.engine.mupdf

import android.net.Uri
import android.os.ParcelFileDescriptor
import dev.pdfforge.data.impl.SafFileAdapter
import dev.pdfforge.data.storage.TempFileManager
import dev.pdfforge.domain.models.OperationResult
import dev.pdfforge.domain.core.ValidationResult
import dev.pdfforge.domain.core.tools.PageOrderItem
import dev.pdfforge.domain.core.tools.ReorderPdfParams
import dev.pdfforge.domain.core.tools.ReorderPdfTool
import dev.pdfforge.domain.models.ErrorCode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MuPdfReorderTool @Inject constructor(
    private val safFileAdapter: SafFileAdapter,
    private val tempFileManager: TempFileManager
) : ReorderPdfTool {

    override val id: String = "reorder_pdf"
    override val nameRes: Int = 0
    override val iconRes: Int = 0
    override val category = dev.pdfforge.domain.core.ToolCategory.OPERATIONS

    override suspend fun execute(params: ReorderPdfParams): OperationResult<Uri> {
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
            val srcPageCount = MuPdfJni.getPageCount(srcDocHandle)
            var destPageIndex = 0
            for (item in params.pageOrder) {
                if (item.pageIndex !in 0 until srcPageCount) continue
                val ok = MuPdfJni.copyPage(srcDocHandle, item.pageIndex, destDocHandle)
                if (!ok) {
                    MuPdfJni.closeDocument(srcDocHandle)
                    MuPdfJni.closeDocument(destDocHandle)
                    pfd.close()
                    return OperationResult.Error(ErrorCode.ENGINE_INTERNAL_ERROR, "Failed to copy page ${item.pageIndex}")
                }
                if (item.rotation != 0) {
                    MuPdfJni.rotatePage(destDocHandle, destPageIndex, item.rotation)
                }
                destPageIndex++
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

    override fun validate(params: ReorderPdfParams): ValidationResult {
        return ValidationResult(params.pageOrder.isNotEmpty())
    }

    override fun cancel() {}
}
