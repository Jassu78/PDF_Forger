package dev.pdfforge.engine.mupdf

import android.net.Uri
import dev.pdfforge.data.impl.SafFileAdapter
import dev.pdfforge.domain.models.OperationResult
import dev.pdfforge.domain.core.tools.PdfInfoTool
import dev.pdfforge.domain.models.ErrorCode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MuPdfPdfInfoTool @Inject constructor(
    private val safFileAdapter: SafFileAdapter
) : PdfInfoTool {

    override suspend fun getPageCount(uri: Uri): OperationResult<Int> {
        val pfd = safFileAdapter.openFileDescriptor(uri)
            ?: return OperationResult.Error(ErrorCode.FILE_NOT_FOUND)
        val handle = MuPdfJni.openFromFd(pfd.fd)
        if (handle == 0L) {
            pfd.close()
            return OperationResult.Error(ErrorCode.CANNOT_OPEN_FILE)
        }
        val count = MuPdfJni.getPageCount(handle)
        MuPdfJni.closeDocument(handle)
        pfd.close()
        return OperationResult.Success(count)
    }
}
