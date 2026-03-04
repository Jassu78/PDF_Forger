package dev.pdfforge.data.impl.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.pdfforge.data.impl.SafFileAdapter
import dev.pdfforge.data.storage.TempFileManager
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideSafFileAdapter(@ApplicationContext context: Context): SafFileAdapter {
        return SafFileAdapter(context)
    }

    @Provides
    @Singleton
    fun provideTempFileManager(@ApplicationContext context: Context): TempFileManager {
        return TempFileManager(context)
    }
}
