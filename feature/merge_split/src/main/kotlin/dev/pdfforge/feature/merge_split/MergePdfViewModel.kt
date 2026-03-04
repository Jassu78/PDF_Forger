package dev.pdfforge.feature.merge_split

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pdfforge.data.impl.SafFileAdapter
import dev.pdfforge.domain.core.OperationResult
import dev.pdfforge.domain.core.tools.MergePdfParams
import dev.pdfforge.domain.models.PdfDocument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MergePdfUiState(
    val selectedFiles: List<PdfDocument> = emptyList(),
    val isProcessing: Boolean = false,
    val error: String? = null,
    val resultUri: Uri? = null
)

@HiltViewModel
class MergePdfViewModel @Inject constructor(
    private val safFileAdapter: SafFileAdapter,
    // Note: MergePdfUseCase will be injected here once implemented
) : ViewModel() {

    private val _uiState = MutableStateFlow(MergePdfUiState())
    val uiState: StateFlow<MergePdfUiState> = _uiState.asStateFlow()

    fun onFilesSelected(uris: List<Uri>) {
        viewModelScope.launch {
            val newFiles = uris.mapNotNull { uri ->
                when (val result = safFileAdapter.getPdfMetadata(uri)) {
                    is OperationResult.Success -> result.data
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

    fun mergeFiles(outputName: String) {
        // Implementation will call MergePdfUseCase
    }
}
