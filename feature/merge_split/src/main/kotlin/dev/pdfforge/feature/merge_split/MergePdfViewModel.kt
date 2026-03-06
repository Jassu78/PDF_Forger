package dev.pdfforge.feature.merge_split

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pdfforge.data.impl.SafFileAdapter
import dev.pdfforge.data.worker.WorkManagerHelper
import dev.pdfforge.domain.models.OperationResult
import dev.pdfforge.domain.models.OperationPayload
import dev.pdfforge.domain.models.PdfDocument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

data class MergePdfUiState(
    val selectedFiles: List<PdfDocument> = emptyList(),
    val isProcessing: Boolean = false,
    val progress: Float = 0f,
    val statusText: String = "",
    val error: String? = null,
    val resultUri: Uri? = null,
    val activeWorkId: UUID? = null
)

@HiltViewModel
class MergePdfViewModel @Inject constructor(
    private val safFileAdapter: SafFileAdapter,
    private val workManagerHelper: WorkManagerHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(MergePdfUiState())
    val uiState: StateFlow<MergePdfUiState> = _uiState.asStateFlow()

    fun onFilesSelected(uris: List<Uri>) {
        viewModelScope.launch {
            val newFiles = uris.mapNotNull { uri ->
                when (val result = safFileAdapter.getPdfMetadata(uri)) {
                    is OperationResult.Success<PdfDocument> -> result.data
                    else -> null
                }
            }
            _uiState.update { it.copy(selectedFiles = it.selectedFiles + newFiles) }
        }
    }

    fun removeFile(file: PdfDocument) {
        _uiState.update { state ->
            state.copy(selectedFiles = state.selectedFiles.filter { it.uri != file.uri })
        }
    }

    fun moveFile(fromIndex: Int, toIndex: Int) {
        _uiState.update { state ->
            val newList = state.selectedFiles.toMutableList()
            val item = newList.removeAt(fromIndex)
            newList.add(toIndex, item)
            state.copy(selectedFiles = newList)
        }
    }

    fun cancelOperation() {
        _uiState.value.activeWorkId?.let { id: UUID ->
            workManagerHelper.cancelWork(id)
        }
        _uiState.update { it.copy(isProcessing = false, activeWorkId = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearResult() {
        _uiState.update { it.copy(resultUri = null) }
    }

    fun mergeFiles(outputName: String) {
        val currentState = _uiState.value
        if (currentState.selectedFiles.size < 2) return

        val payload = OperationPayload.MergePdf(
            sourceUris = currentState.selectedFiles.map { it.uri.toString() },
            outputName = outputName
        )

        val workId = workManagerHelper.enqueuePdfOperation(payload)
        
        _uiState.update { it.copy(isProcessing = true, activeWorkId = workId) }

        viewModelScope.launch {
            workManagerHelper.getWorkInfoById(workId).collect { workInfo: WorkInfo? ->
                if (workInfo != null) {
                    val progress = workInfo.progress.getFloat("progress", 0f)
                    val status = workInfo.progress.getString("status_text") ?: ""
                    
                    _uiState.update { state ->
                        state.copy(progress = progress, statusText = status)
                    }

                    if (workInfo.state.isFinished) {
                        _uiState.update { state ->
                            state.copy(isProcessing = false, activeWorkId = null)
                        }
                        when (workInfo.state) {
                            androidx.work.WorkInfo.State.SUCCEEDED -> {
                                workInfo.outputData.getString("result_uri")?.let { uriString ->
                                    _uiState.update { it.copy(resultUri = Uri.parse(uriString)) }
                                }
                            }
                            androidx.work.WorkInfo.State.FAILED -> {
                                val message = workInfo.outputData.getString("error_message") ?: "Merge failed."
                                _uiState.update { it.copy(error = message) }
                            }
                            else -> { /* Cancelled or other */ }
                        }
                    }
                }
            }
        }
    }
}
