package dev.pdfforge.feature.compression

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pdfforge.data.impl.SafFileAdapter
import dev.pdfforge.domain.core.OperationResult
import dev.pdfforge.domain.core.tools.CompressPdfParams
import dev.pdfforge.domain.core.tools.CompressionStrategy
import dev.pdfforge.domain.core.usecases.CompressPdfUseCase
import dev.pdfforge.domain.models.PdfDocument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CompressionUiState(
    val selectedFile: PdfDocument? = null,
    val strategy: CompressionStrategy = CompressionStrategy(),
    val isProcessing: Boolean = false,
    val progress: Float = 0f,
    val error: String? = null,
    val resultUri: Uri? = null
)

@HiltViewModel
class CompressionViewModel @Inject constructor(
    private val safFileAdapter: SafFileAdapter,
    private val compressPdfUseCase: CompressPdfUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(CompressionUiState())
    val uiState: StateFlow<CompressionUiState> = _uiState.asStateFlow()

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

    fun updateStrategy(update: (CompressionStrategy) -> CompressionStrategy) {
        _uiState.update { it.copy(strategy = update(it.strategy)) }
    }

    fun compress() {
        val currentState = _uiState.value
        val file = currentState.selectedFile ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, error = null, progress = 0f) }
            
            val params = CompressPdfParams(
                sourceUri = file.uri,
                outputName = "Compressed_${file.name}",
                strategy = currentState.strategy
            )

            when (val result = compressPdfUseCase(params)) {
                is OperationResult.Success -> {
                    _uiState.update { it.copy(isProcessing = false, resultUri = result.data, progress = 1f) }
                }
                is OperationResult.Error -> {
                    _uiState.update { it.copy(isProcessing = false, error = result.message) }
                }
                OperationResult.Cancelled -> {
                    _uiState.update { it.copy(isProcessing = false) }
                }
            }
        }
    }
}
