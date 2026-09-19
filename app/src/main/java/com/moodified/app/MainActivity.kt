package com.moodified.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.moodified.app.core.navigation.AppRoutes
import com.moodified.app.core.theme.MoodifiedTheme
import com.moodified.app.data.local.datasource.OnboardingPreferencesDataSource
import com.moodified.app.presentation.navigation.MoodifiedNavHost
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var onboardingPrefs: OnboardingPreferencesDataSource

    private val quickLogTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val openInboxTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIntent(intent)

        val startDestination =
            runBlocking {
                if (onboardingPrefs.hasCompletedOnboarding()) {
                    AppRoutes.CheckIn.route
                } else {
                    AppRoutes.Onboarding.route
                }
            }

        setContent {
            MoodifiedTheme {
                MoodifiedNavHost(
                    quickLogTrigger = quickLogTrigger,
                    openInboxTrigger = openInboxTrigger,
                    startDestination = startDestination,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.data?.toString()) {
            "moodified://quicklog" -> quickLogTrigger.tryEmit(Unit)
            "moodified://inbox" -> openInboxTrigger.tryEmit(Unit)
        }
    }
}
