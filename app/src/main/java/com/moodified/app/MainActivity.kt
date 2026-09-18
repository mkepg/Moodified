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
    // Emits events when the app is opened via a deep link
    private val quickLogTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val openInboxTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIntent(intent)

        setContent {
            MoodifiedTheme {
                MoodifiedNavHost(
                    quickLogTrigger = quickLogTrigger,
                    openInboxTrigger = openInboxTrigger,
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
