package dev.pdfforge.feature.merge_split

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pdfforge.data.impl.SafFileAdapter
import dev.pdfforge.data.worker.WorkManagerHelper
import dev.pdfforge.domain.core.OperationResult
import dev.pdfforge.domain.models.OperationPayload
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
                is OperationResult.Success -> {
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
        _uiState.value.activeWorkId?.let { 
            workManagerHelper.cancelWork(it)
        }
        _uiState.update { it.copy(isProcessing = false, activeWorkId = null) }
    }

    fun splitPdf(outputName: String) {
        val currentState = _uiState.value
        val file = currentState.selectedFile ?: return
        if (currentState.pageRanges.isEmpty()) return

        // In a real implementation, we would parse the ranges string into a List<PageRange>
        // and pass it to the worker via a new OperationPayload type.
        
        _uiState.update { 
            it.copy(
                isProcessing = true, 
                error = null, 
                progress = 0.1f,
                statusText = "Analyzing page ranges..."
            ) 
        }
        
        // Simulating the worker flow
        viewModelScope.launch {
            _uiState.update { it.copy(progress = 0.5f, statusText = "Extracting pages...") }
            // worker logic would go here
        }
    }
}
