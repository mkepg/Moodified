package com.moodified.app.core.devtools

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder

/**
 * Registers debug-only routes (Activity / Sleep / Interaction monitor screens)
 * into the app's NavHost. Real implementation lives in `src/debug/`; release
 * builds receive a no-op stub so the screens never ship.
 */
interface DebugNavRegistrar {
    val isAvailable: Boolean
    val routes: Set<String>
    val activityMonitorRoute: String?
    val sleepMonitorRoute: String?
    val interactionMonitorRoute: String?
    fun register(graph: NavGraphBuilder, navController: NavController)
}
