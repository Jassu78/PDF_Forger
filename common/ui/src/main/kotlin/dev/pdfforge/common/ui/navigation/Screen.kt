package dev.pdfforge.common.ui.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object ImageToPdf : Screen("image_to_pdf")
    data object MergePdf : Screen("merge_pdf")
    data object CompressPdf : Screen("compress_pdf")
    data object ConvertPdf : Screen("convert_pdf")
    data object SplitPdf : Screen("split_pdf")
    data object ReorderPages : Screen("reorder_pages")
}
