package dev.pdfforge.feature.conversion

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pdfforge.data.impl.SafFileAdapter
import dev.pdfforge.domain.models.OperationResult
import dev.pdfforge.domain.core.tools.ConvertPdfParams
import dev.pdfforge.domain.core.usecases.ConvertPdfUseCase
import dev.pdfforge.domain.models.PdfDocument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class OutputFormat {
    DOCX, PPTX, IMAGES, TXT, MD
}

data class ConversionUiState(
    val selectedFile: PdfDocument? = null,
    val selectedFormat: OutputFormat = OutputFormat.DOCX,
    val isProcessing: Boolean = false,
    val progress: Float = 0f,
    val statusText: String = "",
    val error: String? = null,
    val resultUri: Uri? = null
)

@HiltViewModel
class ConversionViewModel @Inject constructor(
    private val safFileAdapter: SafFileAdapter,
    private val convertPdfUseCase: ConvertPdfUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConversionUiState())
    val uiState: StateFlow<ConversionUiState> = _uiState.asStateFlow()

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

    fun selectFormat(format: OutputFormat) {
        _uiState.update { it.copy(selectedFormat = format) }
    }

    fun cancelOperation() {
        _uiState.update { it.copy(isProcessing = false) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearResult() {
        _uiState.update { it.copy(resultUri = null) }
    }

    fun convert() {
        val currentState = _uiState.value
        val file = currentState.selectedFile ?: return

        if (currentState.selectedFormat == OutputFormat.PPTX) {
            _uiState.update { it.copy(error = "PPTX export coming in future") }
            return
        }

        viewModelScope.launch {
            _uiState.update { 
                it.copy(
                    isProcessing = true, 
                    error = null, 
                    progress = 0.1f,
                    statusText = "Initializing converter engine..."
                ) 
            }
            
            val baseName = file.name.replace(Regex("\\.pdf$", RegexOption.IGNORE_CASE), "")
            val ext = when (currentState.selectedFormat) {
                OutputFormat.DOCX -> "docx"
                OutputFormat.PPTX -> "pptx"
                OutputFormat.TXT -> "txt"
                OutputFormat.IMAGES -> "zip"
                OutputFormat.MD -> "md"
            }
            val params = ConvertPdfParams(
                sourceUri = file.uri,
                targetFormat = currentState.selectedFormat.name,
                outputName = "Converted_$baseName.$ext"
            )

            // Simulating progress steps for Apache POI reconstruction
            _uiState.update { it.copy(progress = 0.4f, statusText = "Extracting text and structure...") }

            when (val result = convertPdfUseCase(params)) {
                is OperationResult.Success<Uri> -> {
                    _uiState.update { 
                        it.copy(
                            isProcessing = false, 
                            resultUri = result.data,
                            progress = 1.0f,
                            statusText = "Conversion Successful!"
                        ) 
                    }
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
