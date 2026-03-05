package dev.pdfforge.feature.merge_split

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pdfforge.data.impl.SafFileAdapter
import dev.pdfforge.data.worker.WorkManagerHelper
import androidx.work.WorkInfo
import androidx.work.Operation
import dev.pdfforge.domain.models.OperationResult
import dev.pdfforge.domain.models.PdfDocument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

data class PageItem(
    val index: Int,
    val rotation: Int = 0
)

data class ReorderPagesUiState(
    val selectedFile: PdfDocument? = null,
    val pages: List<PageItem> = emptyList(),
    val isProcessing: Boolean = false,
    val progress: Float = 0f,
    val statusText: String = "",
    val activeWorkId: UUID? = null
)

@HiltViewModel
class ReorderPagesViewModel @Inject constructor(
    private val safFileAdapter: SafFileAdapter,
    private val workManagerHelper: WorkManagerHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReorderPagesUiState())
    val uiState: StateFlow<ReorderPagesUiState> = _uiState.asStateFlow()

    fun onFileSelected(uri: Uri) {
        viewModelScope.launch {
            when (val result = safFileAdapter.getPdfMetadata(uri)) {
                is OperationResult.Success<PdfDocument> -> {
                    val file = result.data
                    val pageList = (0 until file.pageCount).map { PageItem(it) }
                    _uiState.update { it.copy(selectedFile = file, pages = pageList) }
                }
                else -> { /* Handle error */ }
            }
        }
    }

    fun rotatePage(pageIndex: Int) {
        _uiState.update { state ->
            val newList = state.pages.map { 
                if (it.index == pageIndex) it.copy(rotation = (it.rotation + 90) % 360)
                else it
            }
            state.copy(pages = newList)
        }
    }

    fun movePage(from: Int, to: Int) {
        _uiState.update { state ->
            val newList = state.pages.toMutableList()
            val item = newList.removeAt(from)
            newList.add(to, item)
            state.copy(pages = newList)
        }
    }

    fun saveChanges(outputName: String) {
        // Wiring to WorkManager logic
        _uiState.update { it.copy(isProcessing = true, statusText = "Saving changes...") }
    }

    fun cancelOperation() {
        _uiState.update { it.copy(isProcessing = false) }
    }
}
