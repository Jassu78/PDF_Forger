package dev.pdfforge.data.worker

import android.content.Context
import androidx.work.*
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.pdfforge.domain.models.OperationPayload
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkManagerHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val workManager = WorkManager.getInstance(context)

    suspend inline fun <reified T : OperationPayload> enqueuePdfOperation(payload: T): UUID = withContext(Dispatchers.IO) {
        val payloadJson = Json.encodeToString(payload)
        val opType = when (payload) {
            is OperationPayload.ImageToPdf -> PdfWorker.OP_IMAGE_TO_PDF
            is OperationPayload.MergePdf -> PdfWorker.OP_MERGE_PDF
            is OperationPayload.CompressPdf -> PdfWorker.OP_COMPRESS_PDF
            else -> throw IllegalArgumentException("Unsupported operation payload")
        }

        val workRequest = OneTimeWorkRequestBuilder<PdfWorker>()
            .setInputData(
                workDataOf(
                    PdfWorker.KEY_OP_TYPE to opType,
                    PdfWorker.KEY_PAYLOAD to payloadJson
                )
            )
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        workManager.enqueue(workRequest)
        workRequest.id
    }

    fun getWorkInfoById(id: UUID) = workManager.getWorkInfoByIdFlow(id)

    fun cancelWork(id: UUID) = workManager.cancelWorkById(id)
}
