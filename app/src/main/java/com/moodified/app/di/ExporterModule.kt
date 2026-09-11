package com.moodified.app.di

import com.moodified.app.data.exporter.UserDataExporterImpl
import com.moodified.app.domain.exporter.UserDataExporter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ExporterModule {
    @Binds
    @Singleton
    abstract fun bindUserDataExporter(impl: UserDataExporterImpl): UserDataExporter
}
