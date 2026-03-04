package dev.pdfforge.feature.pdf_creation

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pdfforge.domain.core.OperationResult
import dev.pdfforge.domain.core.tools.ImageToPdfParams
import dev.pdfforge.domain.core.tools.PageSize
import dev.pdfforge.domain.core.usecases.CreatePdfUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ImageToPdfUiState(
    val selectedImages: List<Uri> = emptyList(),
    val quality: Int = 80,
    val pageSize: PageSize = PageSize.A4,
    val isProcessing: Boolean = false,
    val error: String? = null,
    val resultUri: Uri? = null
)

@HiltViewModel
class ImageToPdfViewModel @Inject constructor(
    private val createPdfUseCase: CreatePdfUseCase
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

    fun updatePageSize(pageSize: PageSize) {
        _uiState.update { it.copy(pageSize = pageSize) }
    }

    fun createPdf(outputName: String) {
        val currentState = _uiState.value
        if (currentState.selectedImages.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, error = null) }
            
            val params = ImageToPdfParams(
                imageUris = currentState.selectedImages,
                outputName = outputName,
                quality = currentState.quality,
                pageSize = currentState.pageSize
            )

            when (val result = createPdfUseCase(params)) {
                is OperationResult.Success -> {
                    _uiState.update { it.copy(isProcessing = false, resultUri = result.data) }
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
