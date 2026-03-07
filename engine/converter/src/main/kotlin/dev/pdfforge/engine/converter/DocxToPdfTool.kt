package dev.pdfforge.engine.converter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.print.PrintAttributes
import android.print.pdf.PrintedPdfDocument
import android.util.Base64
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.pdfforge.data.impl.SafFileAdapter
import dev.pdfforge.data.storage.TempFileManager
import dev.pdfforge.domain.core.ValidationResult
import dev.pdfforge.domain.core.tools.DocumentToPdfParams
import dev.pdfforge.domain.core.tools.DocumentToPdfTool
import dev.pdfforge.domain.models.ErrorCode
import dev.pdfforge.domain.models.OperationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.UnderlinePatterns
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFTable
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

private const val RENDER_TIMEOUT_MS = 30_000L
private const val PAGE_WIDTH_PX = 1240 // ~A4 at 150 DPI (reasonable for WebView)

@Singleton
class DocxToPdfTool @Inject constructor(
    @ApplicationContext private val context: Context,
    private val safFileAdapter: SafFileAdapter,
    private val tempFileManager: TempFileManager
) : DocumentToPdfTool {

    override val id: String = "document_to_pdf"
    override val nameRes: Int = 0
    override val iconRes: Int = 0
    override val category = dev.pdfforge.domain.core.ToolCategory.CONVERSION

    private val docxMimeTypes = setOf(
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/msword"
    )

    override fun supportsMimeType(mimeType: String?): Boolean =
        mimeType?.lowercase() in docxMimeTypes

    override suspend fun execute(params: DocumentToPdfParams): OperationResult<Uri> {
        return try {
            val html = withContext(Dispatchers.IO) {
                val inputStream = safFileAdapter.openInputStream(params.sourceUri)
                    ?: return@withContext null
                inputStream.use { stream ->
                    val docx = XWPFDocument(stream)
                    val result = docxToHtml(docx)
                    docx.close()
                    result
                }
            } ?: return OperationResult.Error(ErrorCode.FILE_NOT_FOUND)

            val outputFile = withContext(Dispatchers.IO) {
                tempFileManager.createOutputFile(params.outputName)
            }

            withTimeout(RENDER_TIMEOUT_MS) {
                renderHtmlToPdf(html, outputFile.absolutePath)
            }
            OperationResult.Success(Uri.fromFile(outputFile))
        } catch (e: Exception) {
            OperationResult.Error(ErrorCode.ENGINE_INTERNAL_ERROR, e.message ?: "DOCX to PDF failed", e)
        }
    }

    // ── DOCX → HTML ────────────────────────────────────────────────

    private fun docxToHtml(docx: XWPFDocument): String {
        val body = StringBuilder()

        for (element in docx.bodyElements) {
            when (element) {
                is org.apache.poi.xwpf.usermodel.XWPFParagraph -> {
                    body.append(renderParagraph(element))
                }
                is XWPFTable -> {
                    body.append(renderTable(element))
                }
            }
        }

        return buildString {
            append("<!DOCTYPE html><html><head><meta charset='utf-8'>")
            append("<meta name='viewport' content='width=$PAGE_WIDTH_PX'>")
            append("<style>")
            append("body{font-family:sans-serif;font-size:11pt;margin:40px 50px;color:#222;line-height:1.5}")
            append("table{border-collapse:collapse;width:100%;margin:8px 0}")
            append("td,th{border:1px solid #888;padding:6px 8px;text-align:left;vertical-align:top}")
            append("th{background:#f0f0f0;font-weight:bold}")
            append("img{max-width:100%;height:auto}")
            append("p{margin:4px 0}")
            append(".align-center{text-align:center}")
            append(".align-right{text-align:right}")
            append(".align-justify{text-align:justify}")
            append("</style></head><body>")
            append(body)
            append("</body></html>")
        }
    }

    private fun renderParagraph(para: org.apache.poi.xwpf.usermodel.XWPFParagraph): String {
        val alignClass = when (para.alignment) {
            ParagraphAlignment.CENTER -> " class='align-center'"
            ParagraphAlignment.RIGHT -> " class='align-right'"
            ParagraphAlignment.BOTH, ParagraphAlignment.DISTRIBUTE -> " class='align-justify'"
            else -> ""
        }

        val style = buildString {
            val indent = para.indentationLeft
            if (indent > 0) append("margin-left:${indent / 20}pt;")
            val spaceBefore = para.spacingBefore
            if (spaceBefore > 0) append("margin-top:${spaceBefore / 20}pt;")
            val spaceAfter = para.spacingAfter
            if (spaceAfter > 0) append("margin-bottom:${spaceAfter / 20}pt;")
        }
        val styleAttr = if (style.isNotEmpty()) " style='$style'" else ""

        val tag = when {
            para.style?.startsWith("Heading1") == true -> "h1"
            para.style?.startsWith("Heading2") == true -> "h2"
            para.style?.startsWith("Heading3") == true -> "h3"
            para.style?.contains("Heading") == true -> "h4"
            else -> "p"
        }

        val content = StringBuilder()
        for (run in para.runs) {
            var text = escapeHtml(run.text() ?: "")
            if (text.isEmpty() && run.embeddedPictures.isEmpty()) continue

            for (pic in run.embeddedPictures) {
                try {
                    val data = pic.pictureData.data
                    val mime = pic.pictureData.suggestFileExtension().let {
                        when (it.lowercase()) {
                            "png" -> "image/png"
                            "jpg", "jpeg" -> "image/jpeg"
                            "gif" -> "image/gif"
                            else -> "image/png"
                        }
                    }
                    val b64 = Base64.encodeToString(data, Base64.NO_WRAP)
                    content.append("<img src='data:$mime;base64,$b64'/>")
                } catch (_: Exception) { }
            }

            if (text.isEmpty()) continue

            if (run.isBold) text = "<b>$text</b>"
            if (run.isItalic) text = "<i>$text</i>"
            if (run.underline != UnderlinePatterns.NONE) text = "<u>$text</u>"
            if (run.isStrikeThrough) text = "<s>$text</s>"

            val fontSize = run.fontSize
            val color = run.color
            val runStyle = buildString {
                if (fontSize > 0) append("font-size:${fontSize}pt;")
                if (!color.isNullOrBlank() && color != "000000") append("color:#$color;")
            }
            text = if (runStyle.isNotEmpty()) "<span style='$runStyle'>$text</span>" else text

            content.append(text)
        }

        if (content.isEmpty()) return "<p>&nbsp;</p>"
        return "<$tag$alignClass$styleAttr>$content</$tag>"
    }

    private fun renderTable(table: XWPFTable): String {
        val sb = StringBuilder("<table>")
        for ((rowIdx, row) in table.rows.withIndex()) {
            sb.append("<tr>")
            val tag = if (rowIdx == 0) "th" else "td"
            for (cell in row.tableCells) {
                sb.append("<$tag>")
                for (para in cell.paragraphs) {
                    sb.append(renderParagraph(para))
                }
                sb.append("</$tag>")
            }
            sb.append("</tr>")
        }
        sb.append("</table>")
        return sb.toString()
    }

    private fun escapeHtml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("\n", "<br/>")
        .replace("\r", "")

    // ── HTML → PDF via WebView ─────────────────────────────────────

    private suspend fun renderHtmlToPdf(html: String, outputPath: String) {
        suspendCancellableCoroutine { cont ->
            Handler(Looper.getMainLooper()).post {
                WebView.enableSlowWholeDocumentDraw()

                val webView = WebView(context).apply {
                    settings.javaScriptEnabled = false
                    settings.allowFileAccess = false
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    setInitialScale(100)
                    layoutParams = ViewGroup.LayoutParams(PAGE_WIDTH_PX, ViewGroup.LayoutParams.WRAP_CONTENT)
                }

                // Pre-measure so WebView has dimensions before loading
                webView.measure(
                    android.view.View.MeasureSpec.makeMeasureSpec(PAGE_WIDTH_PX, android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
                )
                webView.layout(0, 0, PAGE_WIDTH_PX, 1)

                var finished = false

                val mainHandler = Handler(Looper.getMainLooper())

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        if (finished) return
                        finished = true
                        mainHandler.postDelayed({
                            try {
                                view.measure(
                                    android.view.View.MeasureSpec.makeMeasureSpec(PAGE_WIDTH_PX, android.view.View.MeasureSpec.EXACTLY),
                                    android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
                                )
                                view.layout(0, 0, PAGE_WIDTH_PX, view.measuredHeight.coerceAtLeast(1))

                                writePdfFromWebView(view, outputPath)
                            } catch (e: Exception) {
                                if (cont.isActive) cont.resume(Unit)
                            } finally {
                                view.destroy()
                                if (cont.isActive) cont.resume(Unit)
                            }
                        }, 800)
                    }
                }

                // Safety timeout on the main thread in case onPageFinished never fires
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!finished) {
                        finished = true
                        webView.stopLoading()
                        webView.destroy()
                        if (cont.isActive) cont.resume(Unit)
                    }
                }, RENDER_TIMEOUT_MS - 2000)

                webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }
        }
    }

    private fun writePdfFromWebView(webView: WebView, outputPath: String) {
        val totalHeight = webView.measuredHeight.coerceAtLeast(1)

        val contentBitmap = Bitmap.createBitmap(PAGE_WIDTH_PX, totalHeight, Bitmap.Config.ARGB_8888)
        val bitmapCanvas = Canvas(contentBitmap)
        webView.draw(bitmapCanvas)

        val attrs = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setResolution(PrintAttributes.Resolution("pdf", "pdf", 150, 150))
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()

        val pdfDoc = PrintedPdfDocument(context, attrs)

        // A4 at 150 DPI: 8.27in * 150 = 1240px wide, 11.69in * 150 = 1754px tall
        val scaledPageHeight = (1754f * (PAGE_WIDTH_PX.toFloat() / 1240f)).toInt()

        var yOffset = 0
        var pageNum = 0
        while (yOffset < totalHeight) {
            val page = pdfDoc.startPage(pageNum)
            val canvas = page.canvas
            val scale = canvas.width.toFloat() / PAGE_WIDTH_PX.toFloat()
            canvas.save()
            canvas.scale(scale, scale)
            canvas.translate(0f, -yOffset.toFloat())
            canvas.drawBitmap(contentBitmap, 0f, 0f, null)
            canvas.restore()
            pdfDoc.finishPage(page)
            yOffset += scaledPageHeight
            pageNum++
        }

        contentBitmap.recycle()

        FileOutputStream(outputPath).use { pdfDoc.writeTo(it) }
        pdfDoc.close()
    }

    override fun validate(params: DocumentToPdfParams): ValidationResult = ValidationResult(true)
    override fun cancel() {}
}
