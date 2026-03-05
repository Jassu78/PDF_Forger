package dev.pdfforge.engine.converter.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.pdfforge.domain.core.tools.ConvertPdfTool
import dev.pdfforge.engine.converter.MuPdfPoiConvertTool
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ConverterModule {

    @Binds
    @Singleton
    abstract fun bindConvertPdfTool(
        muPdfPoiConvertTool: MuPdfPoiConvertTool
    ): ConvertPdfTool
}
