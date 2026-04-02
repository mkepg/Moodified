package com.karamay.app.di

import com.karamay.app.data.repository.SleepRepositoryImpl
import com.karamay.app.domain.repository.SleepRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SleepModule {

    @Binds
    @Singleton
    abstract fun bindSleepRepository(impl: SleepRepositoryImpl): SleepRepository
}