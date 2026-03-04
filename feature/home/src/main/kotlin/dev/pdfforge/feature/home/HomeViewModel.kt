package dev.pdfforge.feature.home

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pdfforge.domain.core.ToolCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class ToolItem(
    val id: String,
    val title: String,
    val description: String,
    val category: ToolCategory,
    val colorHex: Long
)

data class HomeUiState(
    val tools: List<ToolItem> = emptyList()
)

@HiltViewModel
class HomeViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadTools()
    }

    private fun loadTools() {
        _uiState.value = HomeUiState(
            tools = listOf(
                ToolItem("image_to_pdf", "Image to PDF", "Convert images to a single PDF", ToolCategory.CREATION, 0xFF58A6FF),
                ToolItem("merge_pdf", "Merge PDFs", "Combine multiple PDFs into one", ToolCategory.OPERATIONS, 0xFFBC8CFF),
                ToolItem("compress_pdf", "Compress PDF", "Reduce PDF file size", ToolCategory.COMPRESSION, 0xFF3FB950),
                ToolItem("convert_pdf", "Convert PDF", "Convert PDF to other formats", ToolCategory.CONVERSION, 0xFFF0883E),
                ToolItem("split_pdf", "Split PDF", "Extract pages from a PDF", ToolCategory.OPERATIONS, 0xFF39D353),
                ToolItem("reorder_pages", "Reorder Pages", "Rotate or rearrange pages", ToolCategory.OPERATIONS, 0xFFFF7B72)
            )
        )
    }
}
