package dev.pdfforge.engine.mupdf.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.pdfforge.domain.core.tools.CompressPdfTool
import dev.pdfforge.domain.core.tools.ConvertPdfTool
import dev.pdfforge.domain.core.tools.ImageToPdfTool
import dev.pdfforge.domain.core.tools.MergePdfTool
import dev.pdfforge.engine.converter.MuPdfPoiConvertTool
import dev.pdfforge.engine.mupdf.MuPdfCompressTool
import dev.pdfforge.engine.mupdf.MuPdfImageToPdfTool
import dev.pdfforge.engine.mupdf.MuPdfMergeTool
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
    abstract fun bindConvertPdfTool(
        muPdfPoiConvertTool: MuPdfPoiConvertTool
    ): ConvertPdfTool
}
