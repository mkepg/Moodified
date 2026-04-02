package com.karamay.app.di

import android.content.Context
import androidx.room.Room
import com.karamay.app.data.local.dao.MoodEntryDao
import com.karamay.app.data.local.dao.SleepSegmentDao
import com.karamay.app.data.local.database.KaramayDatabase
import com.karamay.app.data.repository.MoodRepositoryImpl
import com.karamay.app.domain.repository.MoodRepository
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
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun provideMoodEntryDao(db: KaramayDatabase): MoodEntryDao = db.moodEntryDao()

    @Provides
    @Singleton
    fun provideSleepSegmentDao(db: KaramayDatabase): SleepSegmentDao = db.sleepSegmentDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindMoodRepository(impl: MoodRepositoryImpl): MoodRepository
}
