package dev.pdfforge.domain.core.tools

import android.net.Uri
import dev.pdfforge.domain.core.PdfTool
import dev.pdfforge.domain.core.ToolParams

/**
 * Reorder and rotate pages of a PDF: output a new PDF with pages in the given order and rotations.
 */
interface ReorderPdfTool : PdfTool<ReorderPdfParams> {
}

data class ReorderPdfParams(
    val sourceUri: Uri,
    val outputName: String,
    /** List of (original 0-based page index, rotation in degrees 0/90/180/270). Order = output page order. */
    val pageOrder: List<PageOrderItem>
) : ToolParams

data class PageOrderItem(
    val pageIndex: Int,
    val rotation: Int
)
