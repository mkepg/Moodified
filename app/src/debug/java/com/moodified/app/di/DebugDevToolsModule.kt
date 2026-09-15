package com.moodified.app.di

import com.moodified.app.core.devtools.DebugMockDataSeeder
import com.moodified.app.core.devtools.DebugNavRegistrar
import com.moodified.app.core.devtools.DebugNavRegistrarImpl
import com.moodified.app.core.devtools.DiagnosticsRepository
import com.moodified.app.core.devtools.DiagnosticsRepositoryImpl
import com.moodified.app.core.devtools.MockDataSeeder
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DebugDevToolsModule {
    @Binds @Singleton
    abstract fun bindMockDataSeeder(impl: DebugMockDataSeeder): MockDataSeeder

    @Binds @Singleton
    abstract fun bindDebugNavRegistrar(impl: DebugNavRegistrarImpl): DebugNavRegistrar

    @Binds @Singleton
    abstract fun bindDiagnosticsRepository(impl: DiagnosticsRepositoryImpl): DiagnosticsRepository
}
