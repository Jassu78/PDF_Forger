package dev.pdfforge.common.ui.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object ImageToPdf : Screen("image_to_pdf")
    data object ImageToPdfResult : Screen("image_to_pdf_result/{pdfUri}")
    data object PdfResult : Screen("pdf_result/{pdfUri}")
    data object MergePdf : Screen("merge_pdf")
    data object CompressPdf : Screen("compress_pdf")
    data object ConvertPdf : Screen("convert_pdf")
    data object DocumentResult : Screen("document_result/{documentUri}/{mimeKey}")
    data object SplitPdf : Screen("split_pdf")
    data object ReorderPages : Screen("reorder_pages")
}
