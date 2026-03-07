package dev.pdfforge.engine.converter.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.pdfforge.domain.core.tools.DocumentToPdfTool
import dev.pdfforge.engine.converter.DocxToPdfTool
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ConverterModule {

    @Binds
    @Singleton
    abstract fun bindDocumentToPdfTool(
        docxToPdfTool: DocxToPdfTool
    ): DocumentToPdfTool
}
