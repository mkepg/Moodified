package com.moodified.app.presentation.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.moodified.app.core.devtools.DebugNavRegistrar
import com.moodified.app.core.navigation.AppRoutes
import com.moodified.app.core.theme.*
import com.moodified.app.presentation.calendar.CalendarScreen
import com.moodified.app.presentation.care.CareScreen
import com.moodified.app.presentation.checkin.CheckInScreen
import com.moodified.app.presentation.insight.InsightScreen
import com.moodified.app.presentation.insight.InsightTab
import com.moodified.app.presentation.more.MoreScreen
import com.moodified.app.presentation.privacy.PrivacyScreen
import com.moodified.app.presentation.quicklog.QuickLogSheet
import com.moodified.app.presentation.support.AboutScreen
import com.moodified.app.presentation.support.HelpScreen
import com.moodified.app.presentation.support.LicensesScreen
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

private val navItems =
    listOf(
        BottomNavItem.CheckIn,
        BottomNavItem.Insight,
        BottomNavItem.QuickLog,
        BottomNavItem.Care,
        BottomNavItem.More,
    )

@EntryPoint
@InstallIn(SingletonComponent::class)
interface NavHostEntryPoint {
    fun debugNavRegistrar(): DebugNavRegistrar
}

@Composable
fun MoodifiedNavHost(quickLogTrigger: SharedFlow<Unit> = MutableSharedFlow()) {
    val context = LocalContext.current
    val debugNavRegistrar =
        remember(context) {
            EntryPointAccessors
                .fromApplication(context.applicationContext, NavHostEntryPoint::class.java)
                .debugNavRegistrar()
        }

    val navController = rememberNavController()
    val navBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route?.substringBefore('?')
    var showQuickLog by rememberSaveable { mutableStateOf(false) }

    val fullScreenRoutes =
        remember(debugNavRegistrar) {
            debugNavRegistrar.routes +
                setOf(
                    AppRoutes.Calendar.route,
                    AppRoutes.Privacy.route,
                    AppRoutes.Support.HELP,
                    AppRoutes.Support.ABOUT,
                    AppRoutes.Support.LICENSES,
                )
        }
    val showBottomBar = currentRoute !in fullScreenRoutes

    LaunchedEffect(quickLogTrigger) {
        quickLogTrigger.collect {
            showQuickLog = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MilkWhite,
            bottomBar = {
                if (showBottomBar) {
                    MoodifiedBottomBar(
                        items = navItems,
                        currentRoute = currentRoute,
                        onItemClick = { item ->
                            if (item.isAction) {
                                showQuickLog = true
                            } else {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                    )
                }
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = AppRoutes.CheckIn.route,
                modifier = Modifier.padding(innerPadding),
                enterTransition = { fadeIn(animationSpec = tween(150)) },
                exitTransition = { fadeOut(animationSpec = tween(150)) },
                popEnterTransition = { fadeIn(animationSpec = tween(150)) },
                popExitTransition = { fadeOut(animationSpec = tween(150)) },
            ) {
                composable(AppRoutes.CheckIn.route) {
                    CheckInScreen(
                        onQuickLog = { showQuickLog = true },
                        onViewCalendar = { navController.navigate(AppRoutes.Calendar.route) },
                    )
                }
                composable(
                    route =
                        AppRoutes.Insight.ROUTE_WITH_ARGS,
                    arguments =
                        listOf(
                            navArgument(AppRoutes.Insight.TAB_ARG) {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            },
                        ),
                ) { entry ->
                    val tabArg = entry.arguments?.getString(AppRoutes.Insight.TAB_ARG)
                    InsightScreen(initialTab = InsightTab.fromQueryParam(tabArg))
                }
                composable(AppRoutes.Care.route) {
                    CareScreen()
                }
                composable(AppRoutes.More.route) {
                    MoreScreen(
                        onNavigateToActivityInsight = { navController.navigate(AppRoutes.Insight.withTab(InsightTab.ACTIVITY)) },
                        onNavigateToSleepInsight = { navController.navigate(AppRoutes.Insight.withTab(InsightTab.SLEEP)) },
                        onNavigateToScreenUseInsight = { navController.navigate(AppRoutes.Insight.withTab(InsightTab.SCREEN_USE)) },
                        onNavigateToPrivacy = { navController.navigate(AppRoutes.Privacy.route) },
                        onNavigateToDebugDrawer =
                            debugNavRegistrar.drawerRoute?.let { route ->
                                { navController.navigate(route) }
                            },
                        onNavigateToActivityMonitor =
                            debugNavRegistrar.activityMonitorRoute?.let { route ->
                                { navController.navigate(route) }
                            },
                        onNavigateToSleepMonitor =
                            debugNavRegistrar.sleepMonitorRoute?.let { route ->
                                { navController.navigate(route) }
                            },
                        onNavigateToInteractionMonitor =
                            debugNavRegistrar.interactionMonitorRoute?.let { route ->
                                { navController.navigate(route) }
                            },
                    )
                }
                composable(AppRoutes.Calendar.route) {
                    CalendarScreen(onBack = { navController.popBackStack() })
                }
                composable(AppRoutes.Privacy.route) {
                    PrivacyScreen(onBack = { navController.popBackStack() })
                }
                composable(AppRoutes.Support.HELP) {
                    HelpScreen(onBack = { navController.popBackStack() })
                }
                composable(AppRoutes.Support.LICENSES) {
                    LicensesScreen(onBack = { navController.popBackStack() })
                }
                composable(AppRoutes.Support.ABOUT) {
                    AboutScreen(
                        onBack = { navController.popBackStack() },
                        onNavigateToLicenses = { navController.navigate(AppRoutes.Support.LICENSES) },
                    )
                }

                // Debug-only routes are registered here when the build type provides them.
                debugNavRegistrar.register(this, navController)
            }
        }

        AnimatedVisibility(
            visible = showQuickLog,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            QuickLogSheet(onDismiss = { showQuickLog = false })
        }
    }
}

@Composable
private fun MoodifiedBottomBar(
    items: List<BottomNavItem>,
    currentRoute: String?,
    onItemClick: (BottomNavItem) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MilkWhite,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                val isSelected = currentRoute == item.route
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    if (item.isAction) {
                        ActionNavItem(
                            item = item,
                            onClick = { onItemClick(item) },
                        )
                    } else {
                        RegularNavItem(
                            item = item,
                            isSelected = isSelected,
                            onClick = { onItemClick(item) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RegularNavItem(
    item: BottomNavItem,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "navScale",
    )
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .padding(vertical = 8.dp)
                .scale(scale),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
            contentDescription = item.label,
            tint = if (isSelected) DeepSage else TextTertiary,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = item.label,
            style =
                MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = 10.sp,
                ),
            color = if (isSelected) DeepSage else TextTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ActionNavItem(
    item: BottomNavItem,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier =
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(DeepSage)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = item.selectedIcon,
            contentDescription = item.label,
            tint = MilkWhite,
            modifier = Modifier.size(26.dp),
        )
    }
}
