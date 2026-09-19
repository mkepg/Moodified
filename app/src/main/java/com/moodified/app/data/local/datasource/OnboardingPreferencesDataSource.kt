package com.moodified.app.data.local.datasource

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.onboardingDataStore by preferencesDataStore(name = "onboarding_prefs")

private val KEY_COMPLETED = booleanPreferencesKey("has_completed_onboarding")

@Singleton
class OnboardingPreferencesDataSource
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        suspend fun hasCompletedOnboarding(): Boolean =
            context.onboardingDataStore.data.map { prefs -> prefs[KEY_COMPLETED] ?: false }.first()

        fun observe(): Flow<Boolean> = context.onboardingDataStore.data.map { prefs -> prefs[KEY_COMPLETED] ?: false }

        suspend fun markCompleted() {
            context.onboardingDataStore.edit { prefs -> prefs[KEY_COMPLETED] = true }
        }
    }
