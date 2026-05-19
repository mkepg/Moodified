package com.moodified.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.moodified.app.core.theme.MoodifiedTheme
import com.moodified.app.presentation.navigation.MoodifiedNavHost
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableSharedFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Emits events when the app is opened via the micro-prompt deep link
    private val quickLogTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle potential deep link from a cold start
        handleIntent(intent)

        setContent {
            MoodifiedTheme {
                MoodifiedNavHost(quickLogTrigger = quickLogTrigger)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle potential deep link when activity is already running (singleTop)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.data?.toString() == "moodified://quicklog") {
            quickLogTrigger.tryEmit(Unit)
        }
    }
}