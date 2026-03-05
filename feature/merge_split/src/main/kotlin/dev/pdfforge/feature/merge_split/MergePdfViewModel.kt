package dev.pdfforge.feature.merge_split

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.work.WorkInfo
import dev.pdfforge.data.impl.SafFileAdapter
import dev.pdfforge.data.worker.PdfWorker
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
        _uiState.value.activeWorkId?.let { 
            workManagerHelper.cancelWork(it)
        }
        _uiState.update { it.copy(isProcessing = false, activeWorkId = null) }
    }

    fun clearResult() {
        _uiState.update { it.copy(resultUri = null, error = null) }
    }

    fun mergeFiles(outputName: String) {
        val currentState = _uiState.value
        if (currentState.selectedFiles.size < 2) return

        val payload = OperationPayload.MergePdf(
            sourceUris = currentState.selectedFiles.map { it.uri.toString() },
            outputName = outputName
        )

        viewModelScope.launch {
            val workId: UUID = workManagerHelper.enqueuePdfOperation(payload)
            _uiState.update { it.copy(isProcessing = true, activeWorkId = workId) }

            workManagerHelper.getWorkInfoById(workId).collect { workInfo: WorkInfo? ->
                if (workInfo != null) {
                    val progress = workInfo.progress.getFloat("progress", 0f)
                    val status = workInfo.progress.getString("status_text") ?: ""
                    
                    _uiState.update { it.copy(progress = progress, statusText = status) }

                    if (workInfo.state.isFinished) {
                        val outputUriString = workInfo.outputData.getString(PdfWorker.KEY_RESULT_URI)
                        val resultUri = outputUriString?.let { Uri.parse(it) }

                        _uiState.update { it.copy(
                            isProcessing = false,
                            activeWorkId = null,
                            resultUri = resultUri,
                            error = if (workInfo.state == WorkInfo.State.FAILED) "Merge operation failed" else null
                        ) }
                    }
                }
            }
        }
    }
}
