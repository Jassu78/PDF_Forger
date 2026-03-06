package dev.pdfforge.domain.core.tools

import android.net.Uri
import dev.pdfforge.domain.core.PdfTool
import dev.pdfforge.domain.core.ToolParams

interface ConvertPdfTool : PdfTool<ConvertPdfParams>

data class ConvertPdfParams(
    val sourceUri: Uri,
    val targetFormat: String,
    val outputName: String
) : ToolParams
