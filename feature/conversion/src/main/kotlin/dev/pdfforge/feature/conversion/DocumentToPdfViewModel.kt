package dev.pdfforge.feature.conversion

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.pdfforge.domain.core.tools.DocumentToPdfParams
import dev.pdfforge.domain.core.usecases.DocumentToPdfUseCase
import dev.pdfforge.domain.models.OperationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DocumentToPdfUiState(
    val selectedFile: SelectedDocument? = null,
    val isProcessing: Boolean = false,
    val statusText: String = "",
    val error: String? = null,
    val resultUri: Uri? = null
)

data class SelectedDocument(
    val uri: Uri,
    val name: String,
    val mimeType: String?
)

@HiltViewModel
class DocumentToPdfViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val documentToPdfUseCase: DocumentToPdfUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(DocumentToPdfUiState())
    val uiState: StateFlow<DocumentToPdfUiState> = _uiState.asStateFlow()

    fun onFileSelected(uri: Uri) {
        viewModelScope.launch {
            val mimeType = context.contentResolver.getType(uri)
            val name = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else "document.docx"
            } ?: "document.docx"
            _uiState.update {
                it.copy(
                    selectedFile = SelectedDocument(uri, name, mimeType),
                    error = null
                )
            }
        }
    }

    fun convert() {
        val file = _uiState.value.selectedFile ?: return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    error = null,
                    statusText = "Converting to PDF..."
                )
            }

            val baseName = file.name.replace(Regex("\\.(docx?|doc)$", RegexOption.IGNORE_CASE), "")
            val params = DocumentToPdfParams(
                sourceUri = file.uri,
                mimeType = file.mimeType ?: "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                outputName = "Converted_$baseName.pdf"
            )

            when (val result = documentToPdfUseCase(params)) {
                is OperationResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            resultUri = result.data,
                            statusText = "Conversion complete!"
                        )
                    }
                }
                is OperationResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            error = result.message
                        )
                    }
                }
                OperationResult.Cancelled -> {
                    _uiState.update { it.copy(isProcessing = false) }
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
}
