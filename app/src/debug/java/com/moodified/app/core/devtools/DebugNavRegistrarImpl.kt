package com.moodified.app.core.devtools

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.moodified.app.presentation.devtools.activitymonitor.ActivityMonitorScreen
import com.moodified.app.presentation.devtools.interactionmonitor.InteractionMonitorScreen
import com.moodified.app.presentation.devtools.sleepmonitor.SleepMonitorScreen
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DebugNavRegistrarImpl
    @Inject
    constructor() : DebugNavRegistrar {
        override val isAvailable: Boolean = true
        override val drawerRoute: String = DebugRoutes.DRAWER
        override val activityMonitorRoute: String = DebugRoutes.ACTIVITY_MONITOR
        override val sleepMonitorRoute: String = DebugRoutes.SLEEP_MONITOR
        override val interactionMonitorRoute: String = DebugRoutes.INTERACTION_MONITOR
        override val routes: Set<String> =
            setOf(
                DebugRoutes.DRAWER,
                DebugRoutes.ACTIVITY_MONITOR,
                DebugRoutes.SLEEP_MONITOR,
                DebugRoutes.INTERACTION_MONITOR,
            )

        override fun register(
            graph: NavGraphBuilder,
            navController: NavController,
        ) {
            graph.composable(DebugRoutes.ACTIVITY_MONITOR) {
                ActivityMonitorScreen(onBack = { navController.popBackStack() })
            }
            graph.composable(DebugRoutes.SLEEP_MONITOR) {
                SleepMonitorScreen(onBack = { navController.popBackStack() })
            }
            graph.composable(DebugRoutes.INTERACTION_MONITOR) {
                InteractionMonitorScreen(onBack = { navController.popBackStack() })
            }
            // DRAWER composable registered in Task 7 once DebugDrawerScreen exists.
        }
    }
