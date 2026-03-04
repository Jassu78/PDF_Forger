package dev.pdfforge.domain.core.tools

import android.net.Uri
import dev.pdfforge.domain.core.PdfTool
import dev.pdfforge.domain.core.ToolParams

interface ImageToPdfTool : PdfTool<ImageToPdfParams>

data class ImageToPdfParams(
    val imageUris: List<Uri>,
    val outputName: String,
    val quality: Int = 80,
    val pageSize: PageSize = PageSize.A4,
    val maintainAspectRatio: Boolean = true
) : ToolParams

sealed class PageSize {
    data object A4 : PageSize()
    data object Letter : PageSize()
    data object Original : PageSize()
    data class Custom(val width: Int, val height: Int) : PageSize()
}
