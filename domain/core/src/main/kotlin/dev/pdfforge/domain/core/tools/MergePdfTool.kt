package dev.pdfforge.domain.core.tools

import android.net.Uri
import dev.pdfforge.domain.core.PdfTool
import dev.pdfforge.domain.core.ToolParams

interface MergePdfTool : PdfTool<MergePdfParams>

data class MergePdfParams(
    val sourceUris: List<Uri>,
    val outputName: String,
    val stripMetadata: Boolean = false
) : ToolParams
