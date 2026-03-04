package dev.pdfforge.engine.mupdf.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.pdfforge.domain.core.tools.ImageToPdfTool
import dev.pdfforge.engine.mupdf.MuPdfImageToPdfTool
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class EngineModule {

    @Binds
    @Singleton
    abstract fun bindImageToPdfTool(
        muPdfImageToPdfTool: MuPdfImageToPdfTool
    ): ImageToPdfTool
}
