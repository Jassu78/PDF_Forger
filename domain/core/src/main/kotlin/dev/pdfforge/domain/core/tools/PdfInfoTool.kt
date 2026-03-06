package dev.pdfforge.domain.core.tools

import android.net.Uri
import dev.pdfforge.domain.models.OperationResult

/**
 * Lightweight tool to get PDF metadata such as page count without full document handling.
 */
interface PdfInfoTool {
    suspend fun getPageCount(uri: Uri): OperationResult<Int>
}
