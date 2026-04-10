package com.karamay.app.presentation.navigation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.karamay.app.core.navigation.AppRoutes
import com.karamay.app.core.theme.*
import com.karamay.app.presentation.calendar.CalendarScreen
import com.karamay.app.presentation.checkin.CheckInScreen
import com.karamay.app.presentation.devtools.activitymonitor.ActivityMonitorScreen
import com.karamay.app.presentation.devtools.interactionmonitor.InteractionMonitorScreen
import com.karamay.app.presentation.devtools.sleepmonitor.SleepMonitorScreen
import com.karamay.app.presentation.insight.InsightScreen
import com.karamay.app.presentation.intervention.InterventionScreen
import com.karamay.app.presentation.more.MoreScreen
import com.karamay.app.presentation.quicklog.QuickLogSheet

private val navItems = listOf(
    BottomNavItem.CheckIn,
    BottomNavItem.Insight,
    BottomNavItem.QuickLog,
    BottomNavItem.Intervention,
    BottomNavItem.More,
)

private val devRoutes = setOf(
    AppRoutes.ActivityMonitor.route,
    AppRoutes.SleepMonitor.route,
    AppRoutes.InteractionMonitor.route,
)

private val fullScreenRoutes = devRoutes + setOf(
    AppRoutes.Calendar.route,
)

@Composable
fun KaramayNavHost() {
    val navController = rememberNavController()
    val navBackStack  by navController.currentBackStackEntryAsState()
    val currentRoute  = navBackStack?.destination?.route

    var showQuickLog  by rememberSaveable { mutableStateOf(false) }
    val showBottomBar = currentRoute !in fullScreenRoutes

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MilkWhite,
            bottomBar = {
                if (showBottomBar) {
                    KaramayBottomBar(
                        items        = navItems,
                        currentRoute = currentRoute,
                        onItemClick  = { item ->
                            if (item.isAction) {
                                showQuickLog = true
                            } else {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState    = true
                                }
                            }
                        },
                    )
                }
            },
        ) { innerPadding ->
            NavHost(
                navController      = navController,
                startDestination   = AppRoutes.CheckIn.route,
                modifier           = Modifier.padding(innerPadding),
                enterTransition    = { fadeIn(animationSpec = tween(150)) },
                exitTransition     = { fadeOut(animationSpec = tween(150)) },
                popEnterTransition = { fadeIn(animationSpec = tween(150)) },
                popExitTransition  = { fadeOut(animationSpec = tween(150)) },
            ) {
                composable(AppRoutes.CheckIn.route) {
                    CheckInScreen(
                        onQuickLog     = { showQuickLog = true },
                        onViewCalendar = { navController.navigate(AppRoutes.Calendar.route) },
                    )
                }

                composable(AppRoutes.Insight.route) {
                    InsightScreen()
                }

                composable(AppRoutes.Intervention.route) {
                    InterventionScreen()
                }

                composable(AppRoutes.More.route) {
                    MoreScreen(
                        onNavigateToActivityMonitor    = {
                            navController.navigate(AppRoutes.ActivityMonitor.route)
                        },
                        onNavigateToSleepMonitor       = {
                            navController.navigate(AppRoutes.SleepMonitor.route)
                        },
                        onNavigateToInteractionMonitor = {
                            navController.navigate(AppRoutes.InteractionMonitor.route)
                        },
                    )
                }

                composable(AppRoutes.Calendar.route) {
                    CalendarScreen(
                        onBack = { navController.popBackStack() },
                    )
                }

                // FIXED: Screens now use their own navigation-scoped ViewModels to prevent stale state
                composable(AppRoutes.ActivityMonitor.route) {
                    ActivityMonitorScreen(
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(AppRoutes.SleepMonitor.route) {
                    SleepMonitorScreen(
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(AppRoutes.InteractionMonitor.route) {
                    InteractionMonitorScreen(
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showQuickLog,
            enter   = fadeIn(),
            exit    = fadeOut(),
        ) {
            QuickLogSheet(onDismiss = { showQuickLog = false })
        }
    }
}

@Composable
private fun KaramayBottomBar(
    items:        List<BottomNavItem>,
    currentRoute: String?,
    onItemClick:  (BottomNavItem) -> Unit,
) {
    Surface(
        modifier        = Modifier.fillMaxWidth(),
        color           = MilkWhite,
        shadowElevation = 0.dp,
        tonalElevation  = 0.dp,
    ) {
        Row(
            modifier            = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                val isSelected = currentRoute == item.route
                if (item.isAction) {
                    ActionNavItem(
                        item      = item,
                        onClick   = { onItemClick(item) },
                    )
                } else {
                    RegularNavItem(
                        item       = item,
                        isSelected = isSelected,
                        onClick    = { onItemClick(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RegularNavItem(
    item:       BottomNavItem,
    isSelected: Boolean,
    onClick:    () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue   = if (isSelected) 1.08f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label         = "navScale",
    )

    Column(
        modifier            = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null,
                onClick           = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .scale(scale),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector        = if (isSelected) item.selectedIcon else item.unselectedIcon,
            contentDescription = item.label,
            tint               = if (isSelected) DeepSage else TextTertiary,
            modifier           = Modifier.size(22.dp),
        )
        Text(
            text  = item.label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                fontSize   = 10.sp,
            ),
            color = if (isSelected) DeepSage else TextTertiary,
        )
    }
}

@Composable
private fun ActionNavItem(
    item:    BottomNavItem,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier         = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(DeepSage)
            .clickable(
                interactionSource = interactionSource,
                indication        = null,
                onClick           = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector        = item.selectedIcon,
            contentDescription = item.label,
            tint               = MilkWhite,
            modifier           = Modifier.size(26.dp),
        )
    }
}