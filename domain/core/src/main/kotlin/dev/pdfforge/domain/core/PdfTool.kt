package dev.pdfforge.domain.core

import android.net.Uri
import dev.pdfforge.domain.models.OperationResult

/**
 * Core plugin interface for all PDF operations.
 * Every engine implementation (MuPDF, PDFBox, etc.) must implement this.
 */
interface PdfTool<in P : ToolParams> {
    val id: String
    val nameRes: Int
    val iconRes: Int
    val category: ToolCategory

    /**
     * Executes the PDF operation.
     * @param params The parameters for the specific operation.
     * @return OperationResult containing the Uri of the result file.
     */
    suspend fun execute(params: P): OperationResult<Uri>

    /**
     * Validates if the operation can be performed with given params.
     */
    fun validate(params: P): ValidationResult
    
    /**
     * Cancels the ongoing operation if possible.
     */
    fun cancel()
}

interface ToolParams

sealed class ToolCategory {
    data object CREATION : ToolCategory()
    data object OPERATIONS : ToolCategory()
    data object COMPRESSION : ToolCategory()
    data object CONVERSION : ToolCategory()
}

data class ValidationResult(
    val isValid: Boolean,
    val errorRes: Int? = null
)
