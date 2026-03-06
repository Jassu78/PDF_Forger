package dev.pdfforge.data.worker

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.pdfforge.domain.models.OperationResult
import dev.pdfforge.domain.core.tools.CompressPdfParams
import dev.pdfforge.domain.core.tools.CompressionStrategy
import dev.pdfforge.domain.core.tools.ConvertPdfParams
import dev.pdfforge.domain.core.tools.ImageToPdfParams
import dev.pdfforge.domain.core.tools.MergePdfParams
import dev.pdfforge.domain.core.tools.PageRange
import dev.pdfforge.domain.core.tools.PageSize
import dev.pdfforge.domain.core.tools.SplitPdfParams
import dev.pdfforge.domain.core.tools.ReorderPdfParams
import dev.pdfforge.domain.core.tools.PageOrderItem
import dev.pdfforge.domain.core.usecases.CreatePdfUseCase
import dev.pdfforge.domain.core.usecases.MergePdfUseCase
import dev.pdfforge.domain.core.usecases.CompressPdfUseCase
import dev.pdfforge.domain.core.usecases.ConvertPdfUseCase
import dev.pdfforge.domain.core.usecases.SplitPdfUseCase
import dev.pdfforge.domain.core.usecases.ReorderPdfUseCase
import dev.pdfforge.domain.models.OperationPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

@HiltWorker
class PdfWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val createPdfUseCase: CreatePdfUseCase,
    private val mergePdfUseCase: MergePdfUseCase,
    private val compressPdfUseCase: CompressPdfUseCase,
    private val convertPdfUseCase: ConvertPdfUseCase,
    private val splitPdfUseCase: SplitPdfUseCase,
    private val reorderPdfUseCase: ReorderPdfUseCase
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_OP_TYPE = "operation_type"
        const val KEY_PAYLOAD = "operation_payload"
        const val KEY_RESULT_URI = "result_uri"
        const val KEY_ERROR = "error_message"
        const val KEY_PROGRESS = "progress"
        const val KEY_STATUS = "status_text"
        
        const val OP_IMAGE_TO_PDF = "image_to_pdf"
        const val OP_MERGE_PDF = "merge_pdf"
        const val OP_COMPRESS_PDF = "compress_pdf"
        const val OP_CONVERT_PDF = "convert_pdf"
        const val OP_SPLIT_PDF = "split_pdf"
        const val OP_REORDER_PDF = "reorder_pdf"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val opType = inputData.getString(KEY_OP_TYPE) ?: return@withContext Result.failure()
        val payloadJson = inputData.getString(KEY_PAYLOAD) ?: return@withContext Result.failure()

        return@withContext when (opType) {
            OP_IMAGE_TO_PDF -> {
                val payload = Json.decodeFromString<OperationPayload.ImageToPdf>(payloadJson)
                executeImageToPdf(payload)
            }
            OP_MERGE_PDF -> {
                val payload = Json.decodeFromString<OperationPayload.MergePdf>(payloadJson)
                executeMergePdf(payload)
            }
            OP_COMPRESS_PDF -> {
                val payload = Json.decodeFromString<OperationPayload.CompressPdf>(payloadJson)
                executeCompressPdf(payload)
            }
            OP_SPLIT_PDF -> {
                val payload = Json.decodeFromString<OperationPayload.SplitPdf>(payloadJson)
                executeSplitPdf(payload)
            }
            OP_REORDER_PDF -> {
                val payload = Json.decodeFromString<OperationPayload.ReorderPdf>(payloadJson)
                executeReorderPdf(payload)
            }
            else -> Result.failure()
        }
    }

    private suspend fun executeImageToPdf(payload: OperationPayload.ImageToPdf): Result {
        setProgress(workDataOf(KEY_PROGRESS to 0.2f, KEY_STATUS to "Converting images to PDF..."))
        val params = ImageToPdfParams(
            imageUris = payload.imageUris.map { Uri.parse(it) },
            outputName = payload.outputName,
            quality = payload.quality,
            pageSize = PageSize.A4 // Simplified mapping
        )
        return when (val result = createPdfUseCase(params)) {
            is OperationResult.Success -> Result.success(workDataOf(KEY_RESULT_URI to result.data.toString()))
            is OperationResult.Error -> Result.failure(workDataOf(KEY_ERROR to result.message))
            OperationResult.Cancelled -> Result.retry()
        }
    }

    private suspend fun executeMergePdf(payload: OperationPayload.MergePdf): Result {
        setProgress(workDataOf(KEY_PROGRESS to 0.2f, KEY_STATUS to "Merging PDFs..."))
        val params = MergePdfParams(
            sourceUris = payload.sourceUris.map { Uri.parse(it) },
            outputName = payload.outputName
        )
        return when (val result = mergePdfUseCase(params)) {
            is OperationResult.Success -> Result.success(workDataOf(KEY_RESULT_URI to result.data.toString()))
            is OperationResult.Error -> Result.failure(workDataOf(KEY_ERROR to result.message))
            OperationResult.Cancelled -> Result.retry()
        }
    }

    private suspend fun executeCompressPdf(payload: OperationPayload.CompressPdf): Result {
        setProgress(workDataOf(KEY_PROGRESS to 0.2f, KEY_STATUS to "Compressing PDF..."))
        val strategy = CompressionStrategy(
            reduceImageQuality = payload.strategy.reduceImageQuality,
            imageQualityPercent = payload.strategy.imageQualityPercent,
            downscaleImages = payload.strategy.downscaleImages,
            targetDpi = payload.strategy.targetDpi,
            removeMetadata = payload.strategy.removeMetadata,
            fontSubsetting = payload.strategy.fontSubsetting
        )
        val params = CompressPdfParams(
            sourceUri = Uri.parse(payload.sourceUri),
            outputName = payload.outputName,
            strategy = strategy
        )
        return when (val result = compressPdfUseCase(params)) {
            is OperationResult.Success -> Result.success(workDataOf(KEY_RESULT_URI to result.data.toString()))
            is OperationResult.Error -> Result.failure(workDataOf(KEY_ERROR to result.message))
            OperationResult.Cancelled -> Result.retry()
        }
    }

    private suspend fun executeSplitPdf(payload: OperationPayload.SplitPdf): Result {
        setProgress(workDataOf(KEY_PROGRESS to 0.2f, KEY_STATUS to "Splitting PDF..."))
        val pageRanges = payload.pageRanges.map { PageRange(start = it.start, end = it.end) }
        val params = SplitPdfParams(
            sourceUri = Uri.parse(payload.sourceUri),
            outputName = payload.outputName,
            pageRanges = pageRanges
        )
        return when (val result = splitPdfUseCase(params)) {
            is OperationResult.Success -> Result.success(workDataOf(KEY_RESULT_URI to result.data.toString()))
            is OperationResult.Error -> Result.failure(workDataOf(KEY_ERROR to result.message))
            OperationResult.Cancelled -> Result.retry()
        }
    }

    private suspend fun executeReorderPdf(payload: OperationPayload.ReorderPdf): Result {
        setProgress(workDataOf(KEY_PROGRESS to 0.2f, KEY_STATUS to "Reordering pages..."))
        val pageOrder = payload.pageOrder.map { PageOrderItem(pageIndex = it.pageIndex, rotation = it.rotation) }
        val params = ReorderPdfParams(
            sourceUri = Uri.parse(payload.sourceUri),
            outputName = payload.outputName,
            pageOrder = pageOrder
        )
        return when (val result = reorderPdfUseCase(params)) {
            is OperationResult.Success -> Result.success(workDataOf(KEY_RESULT_URI to result.data.toString()))
            is OperationResult.Error -> Result.failure(workDataOf(KEY_ERROR to result.message))
            OperationResult.Cancelled -> Result.retry()
        }
    }
}
