package com.karamay.app.di

import com.karamay.app.data.repository.ActivityRepositoryImpl
import com.karamay.app.domain.repository.ActivityRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Registers [ActivityRepositoryImpl] as the [ActivityRepository] singleton.
 * Mirrors the [RepositoryModule] pattern in DatabaseModule.kt.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ActivityModule {

    @Binds
    @Singleton
    abstract fun bindActivityRepository(impl: ActivityRepositoryImpl): ActivityRepository
}
