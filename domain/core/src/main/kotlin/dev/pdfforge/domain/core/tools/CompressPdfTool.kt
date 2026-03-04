package dev.pdfforge.domain.core.tools

import android.net.Uri
import dev.pdfforge.domain.core.PdfTool
import dev.pdfforge.domain.core.ToolParams

interface CompressPdfTool : PdfTool<CompressPdfParams>

data class CompressPdfParams(
    val sourceUri: Uri,
    val outputName: String,
    val strategy: CompressionStrategy
) : ToolParams

data class CompressionStrategy(
    val reduceImageQuality: Boolean = false,
    val imageQualityPercent: Int = 70,
    val downscaleImages: Boolean = false,
    val targetDpi: Int = 150,
    val removeMetadata: Boolean = false,
    val fontSubsetting: Boolean = false,
    val compressObjectStreams: Boolean = true
)
