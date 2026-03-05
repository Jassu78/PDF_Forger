package dev.pdfforge.domain.models

import android.net.Uri
import kotlinx.serialization.Serializable

/**
 * Payload for background operations.
 * Used to pass parameters to the WorkManager.
 */
@Serializable
sealed class OperationPayload {
    
    @Serializable
    data class ImageToPdf(
        val imageUris: List<String>,
        val outputName: String,
        val quality: Int,
        val pageSize: String // Store as String for serialization
    ) : OperationPayload()

    @Serializable
    data class MergePdf(
        val sourceUris: List<String>,
        val outputName: String
    ) : OperationPayload()

    @Serializable
    data class CompressPdf(
        val sourceUri: String,
        val outputName: String,
        val strategy: CompressionStrategyPayload
    ) : OperationPayload()
}

@Serializable
data class CompressionStrategyPayload(
    val reduceImageQuality: Boolean,
    val imageQualityPercent: Int,
    val downscaleImages: Boolean,
    val targetDpi: Int,
    val removeMetadata: Boolean,
    val fontSubsetting: Boolean
)
