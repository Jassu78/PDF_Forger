package dev.pdfforge.feature.merge_split

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pdfforge.data.impl.SafFileAdapter
import dev.pdfforge.data.worker.WorkManagerHelper
import dev.pdfforge.data.worker.PdfWorker
import dev.pdfforge.domain.models.OperationResult
import dev.pdfforge.domain.models.OperationPayload
import dev.pdfforge.domain.models.PageRangePayload
import dev.pdfforge.domain.models.PdfDocument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

data class SplitPdfUiState(
    val selectedFile: PdfDocument? = null,
    val pageRanges: String = "", // e.g., "1-5, 8, 10-12"
    val isProcessing: Boolean = false,
    val progress: Float = 0f,
    val statusText: String = "",
    val error: String? = null,
    val resultUri: Uri? = null,
    val activeWorkId: UUID? = null
)

@HiltViewModel
class SplitPdfViewModel @Inject constructor(
    private val safFileAdapter: SafFileAdapter,
    private val workManagerHelper: WorkManagerHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(SplitPdfUiState())
    val uiState: StateFlow<SplitPdfUiState> = _uiState.asStateFlow()

    fun onFileSelected(uri: Uri) {
        viewModelScope.launch {
            when (val result = safFileAdapter.getPdfMetadata(uri)) {
                is OperationResult.Success<PdfDocument> -> {
                    _uiState.update { it.copy(selectedFile = result.data) }
                }
                else -> { /* Handle error */ }
            }
        }
    }

    fun updatePageRanges(ranges: String) {
        _uiState.update { it.copy(pageRanges = ranges) }
    }

    fun cancelOperation() {
        _uiState.value.activeWorkId?.let { id: UUID ->
            workManagerHelper.cancelWork(id)
        }
        _uiState.update { it.copy(isProcessing = false, activeWorkId = null) }
    }

    fun splitPdf(outputName: String) {
        val currentState = _uiState.value
        val file = currentState.selectedFile ?: return
        if (currentState.pageRanges.isBlank()) return

        val parsed = parsePageRanges(currentState.pageRanges)
        if (parsed.isEmpty()) {
            _uiState.update { it.copy(error = "Invalid page range. Use format: 1-5, 8, 10-12") }
            return
        }

        val payload = OperationPayload.SplitPdf(
            sourceUri = file.uri.toString(),
            outputName = outputName,
            pageRanges = parsed
        )
        val workId = workManagerHelper.enqueuePdfOperation(payload)
        _uiState.update {
            it.copy(
                isProcessing = true,
                error = null,
                progress = 0.1f,
                statusText = "Splitting PDF...",
                activeWorkId = workId
            )
        }

        viewModelScope.launch {
            workManagerHelper.getWorkInfoById(workId).collect { workInfo ->
                if (workInfo == null) return@collect
                workInfo.progress.getFloat(PdfWorker.KEY_PROGRESS, 0f).let { p ->
                    _uiState.update { it.copy(progress = p, statusText = workInfo.progress.getString(PdfWorker.KEY_STATUS) ?: it.statusText) }
                }
                if (workInfo.state.isFinished) {
                    _uiState.update { state ->
                        state.copy(isProcessing = false, activeWorkId = null)
                    }
                    when (workInfo.state) {
                        WorkInfo.State.SUCCEEDED -> {
                            workInfo.outputData.getString(PdfWorker.KEY_RESULT_URI)?.let { uriString ->
                                _uiState.update { it.copy(resultUri = Uri.parse(uriString)) }
                            }
                        }
                        WorkInfo.State.FAILED -> {
                            val message = workInfo.outputData.getString(PdfWorker.KEY_ERROR) ?: "Split failed."
                            _uiState.update { it.copy(error = message) }
                        }
                        else -> { /* Cancelled */ }
                    }
                }
            }
        }
    }

    /** Parse "1-5, 8, 10-12" (1-based) into 0-based PageRangePayload list. */
    private fun parsePageRanges(input: String): List<PageRangePayload> {
        val result = mutableListOf<PageRangePayload>()
        for (part in input.split(",").map { it.trim() }.filter { it.isNotEmpty() }) {
            if (part.contains("-")) {
                val tokens = part.split("-", limit = 2).map { it.trim().toIntOrNull() }
                if (tokens.size == 2 && tokens[0] != null && tokens[1] != null) {
                    val a = tokens[0]!!.coerceAtLeast(1)
                    val b = tokens[1]!!.coerceAtLeast(1)
                    result.add(PageRangePayload(start = minOf(a, b) - 1, end = maxOf(a, b) - 1))
                }
            } else {
                val n = part.toIntOrNull()?.coerceAtLeast(1) ?: continue
                result.add(PageRangePayload(start = n - 1, end = n - 1))
            }
        }
        return result
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearResult() {
        _uiState.update { it.copy(resultUri = null) }
    }
}
