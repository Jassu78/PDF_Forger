package dev.pdfforge.feature.pdf_creation

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pdfforge.data.worker.WorkManagerHelper
import dev.pdfforge.domain.core.tools.PageSize
import dev.pdfforge.domain.models.OperationPayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

data class ImageToPdfUiState(
    val selectedImages: List<Uri> = emptyList(),
    val quality: Int = 80,
    val pageSize: PageSize = PageSize.A4,
    val isProcessing: Boolean = false,
    val progress: Float = 0f,
    val statusText: String = "",
    val error: String? = null,
    val resultUri: Uri? = null,
    val activeWorkId: UUID? = null
)

@HiltViewModel
class ImageToPdfViewModel @Inject constructor(
    private val workManagerHelper: WorkManagerHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImageToPdfUiState())
    val uiState: StateFlow<ImageToPdfUiState> = _uiState.asStateFlow()

    fun onImagesSelected(uris: List<Uri>) {
        _uiState.update { it.copy(selectedImages = it.selectedImages + uris) }
    }

    fun onRemoveImage(uri: Uri) {
        _uiState.update { state ->
            state.copy(selectedImages = state.selectedImages.filter { it != uri })
        }
    }

    fun updateQuality(quality: Int) {
        _uiState.update { it.copy(quality = quality) }
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

    fun createPdf(outputName: String) {
        val currentState = _uiState.value
        if (currentState.selectedImages.isEmpty()) return

        val payload = OperationPayload.ImageToPdf(
            imageUris = currentState.selectedImages.map { it.toString() },
            outputName = outputName,
            quality = currentState.quality,
            pageSize = "A4"
        )

        val workId = workManagerHelper.enqueuePdfOperation(payload)
        
        _uiState.update { it.copy(isProcessing = true, activeWorkId = workId) }

        viewModelScope.launch {
            workManagerHelper.getWorkInfoById(workId).collect { workInfo: WorkInfo? ->
                if (workInfo != null) {
                    // Update progress and status from Worker
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
                                val message = workInfo.outputData.getString("error_message") ?: "Failed to create PDF."
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
