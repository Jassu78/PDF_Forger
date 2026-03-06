package dev.pdfforge.engine.mupdf

import android.content.Context
import android.net.Uri
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Image
import com.artifex.mupdf.fitz.Matrix
import com.artifex.mupdf.fitz.Rect
import com.artifex.mupdf.fitz.StructuredTextWalker
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.pdfforge.data.storage.TempFileManager
import dev.pdfforge.domain.core.ValidationResult
import dev.pdfforge.domain.core.tools.ConvertPdfParams
import dev.pdfforge.domain.core.tools.ConvertPdfTool
import dev.pdfforge.domain.models.ErrorCode
import dev.pdfforge.domain.models.OperationResult
import org.apache.poi.util.Units
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.ByteArrayInputStream
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_IMAGE_POINTS = 400f

@Singleton
class MuPdfPoiConvertTool @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tempFileManager: TempFileManager
) : ConvertPdfTool {

    override val id: String = "convert_pdf"
    override val nameRes: Int = 0
    override val iconRes: Int = 0
    override val category = dev.pdfforge.domain.core.ToolCategory.CONVERSION

    override suspend fun execute(params: ConvertPdfParams): OperationResult<Uri> {
        if (params.targetFormat.uppercase() != "DOCX") {
            return OperationResult.Error(
                ErrorCode.ENGINE_INTERNAL_ERROR,
                "${params.targetFormat} export is not yet implemented. Use DOCX for now."
            )
        }
        val tempInput = MuPdfHelper.copyUriToTempFile(context, params.sourceUri, tempFileManager)
            ?: return OperationResult.Error(ErrorCode.FILE_NOT_FOUND)

        try {
            val doc = Document.openDocument(tempInput.absolutePath)
            val pageCount = doc.countPages()
            val wordDoc = XWPFDocument()

            for (i in 0 until pageCount) {
                val page = doc.loadPage(i)
                val stext = page.toStructuredText("preserve-images")
                val walker = DocxBuilderWalker(wordDoc)
                stext.walk(walker)
                stext.destroy()
                page.destroy()
            }

            val outputFile = tempFileManager.createOutputFile(params.outputName)
            FileOutputStream(outputFile).use { out ->
                wordDoc.write(out)
            }

            doc.destroy()
            return OperationResult.Success(Uri.fromFile(outputFile))
        } catch (e: Exception) {
            return OperationResult.Error(ErrorCode.ENGINE_INTERNAL_ERROR, e.message ?: "Conversion failed", e)
        } finally {
            tempInput.delete()
        }
    }

    override fun validate(params: ConvertPdfParams): ValidationResult {
        return ValidationResult(true)
    }

    override fun cancel() {}
}

private class DocxBuilderWalker(
    private val wordDoc: XWPFDocument
) : StructuredTextWalker {

    private val lineChars = mutableListOf<Int>()

    override fun beginTextBlock(bbox: Rect?) {
        lineChars.clear()
    }

    override fun endTextBlock() {
        flushLine()
    }

    override fun beginLine(bbox: Rect?, wmode: Int, dir: com.artifex.mupdf.fitz.Point?) {
        flushLine()
        lineChars.clear()
    }

    override fun endLine() {
        // Line complete; flushLine will be called at next beginLine or endTextBlock
    }

    private fun flushLine() {
        if (lineChars.isEmpty()) return
        val paragraph = wordDoc.createParagraph()
        val run = paragraph.createRun()
        run.setText(buildString {
            for (c in lineChars) {
                append(Character.toChars(c))
            }
        })
        lineChars.clear()
    }

    override fun onChar(c: Int, origin: com.artifex.mupdf.fitz.Point?, font: com.artifex.mupdf.fitz.Font?, size: Float, quad: com.artifex.mupdf.fitz.Quad?, argb: Int, flags: Int) {
        lineChars.add(c)
    }

    override fun onImageBlock(bbox: Rect?, transform: Matrix?, image: Image?) {
        flushLine()
        if (bbox == null || image == null) return
        try {
            val pixmap = image.toPixmap()
            val (bytes, pictureType, filename) = try {
                val buf = pixmap.asPNG()
                Triple(buf.asByteArray(), XWPFDocument.PICTURE_TYPE_PNG, "img.png").also {
                    buf.destroy()
                }
            } catch (_: Exception) {
                val buf = pixmap.asJPEG(85, false)
                Triple(buf.asByteArray(), XWPFDocument.PICTURE_TYPE_JPEG, "img.jpg").also {
                    buf.destroy()
                }
            }
            pixmap.destroy()

            val w = (bbox.x1 - bbox.x0).toFloat()
            val h = (bbox.y1 - bbox.y0).toFloat()
            val (displayW, displayH) = scaleToFit(w, h)

            val widthEMU = Units.toEMU(displayW.toDouble()).toInt().coerceAtLeast(Units.EMU_PER_POINT)
            val heightEMU = Units.toEMU(displayH.toDouble()).toInt().coerceAtLeast(Units.EMU_PER_POINT)

            val paragraph = wordDoc.createParagraph()
            val run = paragraph.createRun()
            run.addPicture(ByteArrayInputStream(bytes), pictureType, filename, widthEMU, heightEMU)
        } catch (_: Exception) {
            // Skip image if extraction fails
        }
    }

    private fun scaleToFit(w: Float, h: Float): Pair<Float, Float> {
        if (w <= 0 || h <= 0) return MAX_IMAGE_POINTS to MAX_IMAGE_POINTS
        val max = maxOf(w, h)
        if (max <= MAX_IMAGE_POINTS) return w to h
        val scale = MAX_IMAGE_POINTS / max
        return (w * scale) to (h * scale)
    }

    override fun beginStruct(standard: String?, raw: String?, index: Int) {}
    override fun endStruct() {}
    override fun onVector(bbox: Rect?, info: com.artifex.mupdf.fitz.StructuredTextWalker.VectorInfo?, argb: Int) {}
}
