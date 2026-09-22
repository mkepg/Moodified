package com.moodified.app.presentation.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import com.moodified.app.presentation.inbox.NotificationsInboxScreen
import com.moodified.app.presentation.insight.InsightScreen
import com.moodified.app.presentation.insight.InsightTab
import com.moodified.app.presentation.onboarding.OnboardingScreen
import com.moodified.app.presentation.privacy.PrivacyScreen
import com.moodified.app.presentation.profile.ProfileScreen
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
        BottomNavItem.Calendar,
        BottomNavItem.QuickLog,
        BottomNavItem.Insight,
        BottomNavItem.Profile,
    )

@EntryPoint
@InstallIn(SingletonComponent::class)
interface NavHostEntryPoint {
    fun debugNavRegistrar(): DebugNavRegistrar
}

@Composable
fun MoodifiedNavHost(
    quickLogTrigger: SharedFlow<Unit> = MutableSharedFlow(),
    openInboxTrigger: SharedFlow<Unit> = MutableSharedFlow(),
    startDestination: String = AppRoutes.CheckIn.route,
) {
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
    val snackbarHostState = remember { SnackbarHostState() }

    val fullScreenRoutes =
        remember(debugNavRegistrar) {
            debugNavRegistrar.routes +
                setOf(
                    AppRoutes.Privacy.route,
                    AppRoutes.Support.HELP,
                    AppRoutes.Support.ABOUT,
                    AppRoutes.Support.LICENSES,
                    AppRoutes.Inbox.route,
                    AppRoutes.Onboarding.route,
                    AppRoutes.Care.route,
                )
        }
    val showBottomBar = currentRoute !in fullScreenRoutes

    LaunchedEffect(quickLogTrigger) {
        quickLogTrigger.collect {
            showQuickLog = true
        }
    }
    LaunchedEffect(openInboxTrigger) {
        openInboxTrigger.collect {
            navController.navigate(AppRoutes.Inbox.route)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MilkWhite,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                // Animate the bar in/out so Scaffold's innerPadding.bottom transitions
                // smoothly. A plain `if (showBottomBar)` swap made the bar (and everything
                // hosted in innerPadding — NavHost content, snackbar) jump by ~64dp on
                // Onboarding → CheckIn navigation.
                AnimatedVisibility(
                    visible = showBottomBar,
                    enter = slideInVertically(animationSpec = tween(220)) { it } + fadeIn(tween(220)),
                    exit = slideOutVertically(animationSpec = tween(220)) { it } + fadeOut(tween(220)),
                ) {
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
                startDestination = startDestination,
                modifier = Modifier.padding(innerPadding),
                enterTransition = { fadeIn(animationSpec = tween(220)) },
                exitTransition = { fadeOut(animationSpec = tween(220)) },
                popEnterTransition = { fadeIn(animationSpec = tween(220)) },
                popExitTransition = { fadeOut(animationSpec = tween(220)) },
            ) {
                composable(AppRoutes.CheckIn.route) {
                    CheckInScreen(
                        onQuickLog = { showQuickLog = true },
                        onViewCalendar = { navController.navigate(AppRoutes.Calendar.route) },
                        onOpenCareAll = { navController.navigate(AppRoutes.Care.route) },
                    )
                }
                composable(AppRoutes.Onboarding.route) {
                    OnboardingScreen(
                        onFinished = {
                            navController.navigate(AppRoutes.CheckIn.route) {
                                popUpTo(AppRoutes.Onboarding.route) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
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
                composable(AppRoutes.Profile.route) {
                    ProfileScreen(
                        onNavigateToPrivacy = { navController.navigate(AppRoutes.Privacy.route) },
                        onNavigateToHelp = { navController.navigate(AppRoutes.Support.HELP) },
                        onNavigateToAbout = { navController.navigate(AppRoutes.Support.ABOUT) },
                        onNavigateToInbox = { navController.navigate(AppRoutes.Inbox.route) },
                        showSnackbar = { message -> snackbarHostState.showSnackbar(message) },
                    )
                }
                composable(AppRoutes.Calendar.route) {
                    CalendarScreen()
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
                        onNavigateToDebugDrawer =
                            debugNavRegistrar.drawerRoute?.let { route ->
                                { navController.navigate(route) }
                            },
                    )
                }
                composable(AppRoutes.Inbox.route) {
                    NotificationsInboxScreen(
                        onBack = { navController.popBackStack() },
                        onDeepLink = { uri ->
                            // Best-effort: only quicklog is currently reachable via deep link URI
                            if (uri == "moodified://quicklog") {
                                showQuickLog = true
                            }
                        },
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
