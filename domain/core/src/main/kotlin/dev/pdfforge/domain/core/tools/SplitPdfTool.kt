package dev.pdfforge.domain.core.tools

import android.net.Uri
import dev.pdfforge.domain.core.PdfTool
import dev.pdfforge.domain.core.ToolParams

interface SplitPdfTool : PdfTool<SplitPdfParams>

data class SplitPdfParams(
    val sourceUri: Uri,
    val outputName: String,
    val pageRanges: List<PageRange>
) : ToolParams

data class PageRange(
    val start: Int,
    val end: Int
)
