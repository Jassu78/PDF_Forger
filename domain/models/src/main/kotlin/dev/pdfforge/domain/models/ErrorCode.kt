package dev.pdfforge.domain.models

sealed class ErrorCode {
    // File errors
    data object FILE_NOT_FOUND : ErrorCode()
    data object FILE_TOO_LARGE : ErrorCode()
    data object FILE_TOO_SMALL : ErrorCode()
    data object NOT_A_PDF : ErrorCode()
    data object CANNOT_OPEN_FILE : ErrorCode()
    data object CANNOT_WRITE_FILE : ErrorCode()
    data object STORAGE_PERMISSION_LOST : ErrorCode()
    
    // Input errors
    data object INSUFFICIENT_INPUT : ErrorCode()
    data object INVALID_PAGE_RANGE : ErrorCode()
    data object PAGE_INDEX_OUT_OF_BOUNDS : ErrorCode()
    
    // Engine errors
    data object INVALID_PDF : ErrorCode()
    data object PDF_ENCRYPTED : ErrorCode()
    data object PDF_CORRUPTED : ErrorCode()
    data object ENGINE_OUT_OF_MEMORY : ErrorCode()
    data object ENGINE_INTERNAL_ERROR : ErrorCode()
    
    // Conversion errors
    data object CONVERSION_FAILED : ErrorCode()
    data object UNSUPPORTED_FORMAT : ErrorCode()
}
