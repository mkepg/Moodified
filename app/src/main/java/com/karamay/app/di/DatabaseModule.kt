package com.karamay.app.di

import android.content.Context
import androidx.room.Room
import com.karamay.app.data.local.dao.ActivityTelemetryDao
import com.karamay.app.data.local.dao.MoodEntryDao
import com.karamay.app.data.local.dao.SleepSegmentDao
import com.karamay.app.data.local.dao.SleepTelemetryDao
import com.karamay.app.data.local.database.KaramayDatabase
import com.karamay.app.data.repository.ActivityRepositoryImpl
import com.karamay.app.data.repository.MoodRepositoryImpl
import com.karamay.app.data.repository.SleepRepositoryImpl
import com.karamay.app.domain.repository.ActivityRepository
import com.karamay.app.domain.repository.MoodRepository
import com.karamay.app.domain.repository.SleepRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): KaramayDatabase =
        Room.databaseBuilder(
            context,
            KaramayDatabase::class.java,
            KaramayDatabase.DATABASE_NAME
        )
            // TODO: Replace with proper Migration objects before production release.
            // fallbackToDestructiveMigration() will wipe all user data on schema changes.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun provideMoodEntryDao(db: KaramayDatabase): MoodEntryDao = db.moodEntryDao()

    @Provides
    @Singleton
    fun provideSleepSegmentDao(db: KaramayDatabase): SleepSegmentDao = db.sleepSegmentDao()

    @Provides
    @Singleton
    fun provideSleepTelemetryDao(db: KaramayDatabase): SleepTelemetryDao = db.sleepTelemetryDao()

    // Fix #6: Wire the new ActivityTelemetryDao so the repository can flush
    // telemetry rows and PurgeOldTelemetryUseCase can housekeep them.
    @Provides
    @Singleton
    fun provideActivityTelemetryDao(db: KaramayDatabase): ActivityTelemetryDao =
        db.activityTelemetryDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindMoodRepository(impl: MoodRepositoryImpl): MoodRepository

    @Binds
    @Singleton
    abstract fun bindSleepRepository(impl: SleepRepositoryImpl): SleepRepository

    @Binds
    @Singleton
    abstract fun bindActivityRepository(impl: ActivityRepositoryImpl): ActivityRepository
}

// ActivityModule.kt and SleepModule.kt are merged here since all three repository
// bindings are in one place, reducing the number of module files to maintain.
// SleepSignalBus and ActivitySignalBus are both @Singleton with @Inject constructors,
// so Hilt provides them automatically — no explicit @Provides bindings needed.
