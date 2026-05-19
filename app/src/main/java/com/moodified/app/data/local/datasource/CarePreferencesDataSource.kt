package com.moodified.app.data.local.datasource

import android.content.Context
import android.content.SharedPreferences
import com.moodified.app.domain.model.intervention.WellBeingDomain
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CarePreferencesDataSource @Inject constructor(
    @ApplicationContext context: Context
) {
    companion object {
        const val PREFS_NAME = "care_preferences"
        const val KEY_ACTIVE_DOMAIN = "active_domain"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var activeDomain: WellBeingDomain
        get() {
            val saved = prefs.getString(KEY_ACTIVE_DOMAIN, WellBeingDomain.MENTAL.name)
            return runCatching { WellBeingDomain.valueOf(saved!!) }.getOrDefault(WellBeingDomain.MENTAL)
        }
        set(value) = prefs.edit().putString(KEY_ACTIVE_DOMAIN, value.name).apply()
}