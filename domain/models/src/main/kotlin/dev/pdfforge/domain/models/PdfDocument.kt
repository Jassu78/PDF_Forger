package dev.pdfforge.domain.models

import android.net.Uri

data class PdfDocument(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val pageCount: Int = 0,
    val isEncrypted: Boolean = false,
    val isCorrupted: Boolean = false,
    val lastModified: Long = System.currentTimeMillis()
)

data class PageInfo(
    val index: Int,
    val width: Int,
    val height: Int,
    val rotation: Int = 0
)
