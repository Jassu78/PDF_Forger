package dev.pdfforge.feature.merge_split

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pdfforge.data.impl.SafFileAdapter
import dev.pdfforge.data.worker.WorkManagerHelper
import dev.pdfforge.data.worker.PdfWorker
import dev.pdfforge.domain.core.usecases.GetPdfPageCountUseCase
import dev.pdfforge.domain.models.OperationPayload
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
    val error: String? = null,
    val resultUri: Uri? = null,
    val activeWorkId: UUID? = null
)

@HiltViewModel
class ReorderPagesViewModel @Inject constructor(
    private val safFileAdapter: SafFileAdapter,
    private val workManagerHelper: WorkManagerHelper,
    private val getPdfPageCountUseCase: GetPdfPageCountUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReorderPagesUiState())
    val uiState: StateFlow<ReorderPagesUiState> = _uiState.asStateFlow()

    fun onFileSelected(uri: Uri) {
        viewModelScope.launch {
            when (val result = safFileAdapter.getPdfMetadata(uri)) {
                is OperationResult.Success<PdfDocument> -> {
                    val file = result.data
                    when (val countResult = getPdfPageCountUseCase(file.uri)) {
                        is OperationResult.Success -> {
                            val pageList = (0 until countResult.data).map { PageItem(it) }
                            _uiState.update { it.copy(selectedFile = file, pages = pageList, error = null) }
                        }
                        is OperationResult.Error -> {
                            _uiState.update { it.copy(selectedFile = file, pages = emptyList(), error = countResult.message) }
                        }
                        else -> { _uiState.update { it.copy(selectedFile = file, pages = emptyList()) } }
                    }
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
        val currentState = _uiState.value
        val file = currentState.selectedFile ?: return
        if (currentState.pages.isEmpty()) return

        val pageOrder = currentState.pages.map {
            dev.pdfforge.domain.models.PageOrderItemPayload(pageIndex = it.index, rotation = it.rotation)
        }
        val payload = OperationPayload.ReorderPdf(
            sourceUri = file.uri.toString(),
            outputName = outputName,
            pageOrder = pageOrder
        )
        val workId = workManagerHelper.enqueuePdfOperation(payload)
        _uiState.update {
            it.copy(
                isProcessing = true,
                error = null,
                progress = 0.1f,
                statusText = "Reordering pages...",
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
                            val message = workInfo.outputData.getString(PdfWorker.KEY_ERROR) ?: "Reorder failed."
                            _uiState.update { it.copy(error = message) }
                        }
                        else -> { /* Cancelled */ }
                    }
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearResult() {
        _uiState.update { it.copy(resultUri = null) }
    }

    fun cancelOperation() {
        _uiState.value.activeWorkId?.let { workManagerHelper.cancelWork(it) }
        _uiState.update { it.copy(isProcessing = false, activeWorkId = null) }
    }
}
