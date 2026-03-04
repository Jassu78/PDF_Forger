package dev.pdfforge.domain.models

sealed class OperationResult<out T> {
    data class Success<T>(
        val data: T,
        val metadata: OperationMetadata = OperationMetadata()
    ) : OperationResult<T>()

    data class Error(
        val code: ErrorCode,
        val message: String = "",
        val cause: Throwable? = null
    ) : OperationResult<Nothing>()

    data object Cancelled : OperationResult<Nothing>()
}

data class OperationMetadata(
    val durationMs: Long = 0L,
    val inputSizeBytes: Long = 0L,
    val outputSizeBytes: Long = 0L
)
