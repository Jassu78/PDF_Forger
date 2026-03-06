package dev.pdfforge.engine.mupdf.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.pdfforge.domain.core.tools.CompressPdfTool
import dev.pdfforge.domain.core.tools.ConvertPdfTool
import dev.pdfforge.domain.core.tools.ImageToPdfTool
import dev.pdfforge.domain.core.tools.MergePdfTool
import dev.pdfforge.domain.core.tools.PdfInfoTool
import dev.pdfforge.domain.core.tools.SplitPdfTool
import dev.pdfforge.domain.core.tools.ReorderPdfTool
import dev.pdfforge.engine.mupdf.MuPdfPoiConvertTool
import dev.pdfforge.engine.mupdf.MuPdfCompressTool
import dev.pdfforge.engine.mupdf.MuPdfImageToPdfTool
import dev.pdfforge.engine.mupdf.MuPdfMergeTool
import dev.pdfforge.engine.mupdf.MuPdfSplitTool
import dev.pdfforge.engine.mupdf.MuPdfReorderTool
import dev.pdfforge.engine.mupdf.MuPdfPdfInfoTool
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class EngineModule {

    @Binds
    @Singleton
    abstract fun bindImageToPdfTool(
        muPdfImageToPdfTool: MuPdfImageToPdfTool
    ): ImageToPdfTool

    @Binds
    @Singleton
    abstract fun bindMergePdfTool(
        muPdfMergeTool: MuPdfMergeTool
    ): MergePdfTool

    @Binds
    @Singleton
    abstract fun bindCompressPdfTool(
        muPdfCompressTool: MuPdfCompressTool
    ): CompressPdfTool

    @Binds
    @Singleton
    abstract fun bindSplitPdfTool(
        muPdfSplitTool: MuPdfSplitTool
    ): SplitPdfTool

    @Binds
    @Singleton
    abstract fun bindReorderPdfTool(
        muPdfReorderTool: MuPdfReorderTool
    ): ReorderPdfTool

    @Binds
    @Singleton
    abstract fun bindPdfInfoTool(
        muPdfPdfInfoTool: MuPdfPdfInfoTool
    ): PdfInfoTool

    @Binds
    @Singleton
    abstract fun bindConvertPdfTool(
        muPdfPoiConvertTool: MuPdfPoiConvertTool
    ): ConvertPdfTool
}
