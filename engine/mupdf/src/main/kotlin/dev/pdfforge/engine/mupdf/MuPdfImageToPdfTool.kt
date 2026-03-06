package dev.pdfforge.engine.mupdf

import android.content.Context
import android.net.Uri
import com.artifex.mupdf.fitz.Image
import com.artifex.mupdf.fitz.PDFDocument
import com.artifex.mupdf.fitz.Rect
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.pdfforge.data.storage.TempFileManager
import dev.pdfforge.domain.models.OperationResult
import dev.pdfforge.domain.core.ValidationResult
import dev.pdfforge.domain.core.tools.ImageToPdfParams
import dev.pdfforge.domain.core.tools.ImageToPdfTool
import dev.pdfforge.domain.models.ErrorCode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MuPdfImageToPdfTool @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tempFileManager: TempFileManager
) : ImageToPdfTool {

    override val id: String = "image_to_pdf"
    override val nameRes: Int = 0
    override val iconRes: Int = 0
    override val category = dev.pdfforge.domain.core.ToolCategory.CREATION

    override suspend fun execute(params: ImageToPdfParams): OperationResult<Uri> {
        val pdfDoc = PDFDocument()

        try {
            for (uri in params.imageUris) {
                val imageBytes = MuPdfHelper.readUriToBytes(context, uri)
                    ?: return OperationResult.Error(ErrorCode.FILE_NOT_FOUND, "Cannot read image: ${uri.path}")

                val image = Image(imageBytes)
                val imgW = image.width.toFloat()
                val imgH = image.height.toFloat()

                // A4 in points: 595 x 842. Scale image to fit while preserving aspect ratio.
                val pageW = 595f
                val pageH = 842f
                val scale = minOf(pageW / imgW, pageH / imgH)
                val scaledW = imgW * scale
                val scaledH = imgH * scale
                val offsetX = (pageW - scaledW) / 2f
                val offsetY = (pageH - scaledH) / 2f

                val imgObj = pdfDoc.addImage(image)
                image.destroy()

                val resources = pdfDoc.newDictionary()
                val xobjects = pdfDoc.newDictionary()
                xobjects.put("Img0", imgObj)
                resources.put("XObject", xobjects)

                val contents = String.format(
                    "q %.2f 0 0 %.2f %.2f %.2f cm /Img0 Do Q",
                    scaledW, scaledH, offsetX, offsetY
                )

                val mediabox = Rect(0f, 0f, pageW, pageH)
                val pageObj = pdfDoc.addPage(mediabox, 0, resources, contents)
                pdfDoc.insertPage(-1, pageObj)
            }

            val outputFile = tempFileManager.createOutputFile(params.outputName)
            pdfDoc.save(outputFile.absolutePath, "compress-images")

            return OperationResult.Success(Uri.fromFile(outputFile))
        } catch (e: Exception) {
            return OperationResult.Error(ErrorCode.ENGINE_INTERNAL_ERROR, e.message ?: "Image-to-PDF failed", e)
        } finally {
            pdfDoc.destroy()
        }
    }

    override fun validate(params: ImageToPdfParams): ValidationResult {
        return ValidationResult(params.imageUris.isNotEmpty())
    }

    override fun cancel() {}
}
