package dev.pdfforge.domain.core.tools

import android.net.Uri
import dev.pdfforge.domain.core.PdfTool
import dev.pdfforge.domain.core.ToolParams

/**
 * Converts documents (DOCX, PPTX) to PDF.
 */
interface DocumentToPdfTool : PdfTool<DocumentToPdfParams> {
    /** Supported MIME types, e.g. application/vnd.openxmlformats-officedocument.wordprocessingml.document */
    fun supportsMimeType(mimeType: String?): Boolean
}

data class DocumentToPdfParams(
    val sourceUri: Uri,
    val mimeType: String,
    val outputName: String
) : ToolParams
