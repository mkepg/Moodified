package com.moodified.app.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Person
import androidx.compose.ui.graphics.vector.ImageVector
import com.moodified.app.core.navigation.AppRoutes

sealed class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val isAction: Boolean = false,
) {
    data object CheckIn : BottomNavItem(
        route = AppRoutes.CheckIn.route,
        label = "Check-in",
        selectedIcon = Icons.Rounded.Favorite,
        unselectedIcon = Icons.Outlined.FavoriteBorder,
    )

    data object Calendar : BottomNavItem(
        route = AppRoutes.Calendar.route,
        label = "Calendar",
        selectedIcon = Icons.Rounded.CalendarMonth,
        unselectedIcon = Icons.Outlined.CalendarMonth,
    )

    data object QuickLog : BottomNavItem(
        route = AppRoutes.QuickLog.route,
        label = "",
        selectedIcon = Icons.Rounded.AddCircle,
        unselectedIcon = Icons.Outlined.AddCircleOutline,
        isAction = true,
    )

    data object Insight : BottomNavItem(
        route = AppRoutes.Insight.route,
        label = "Insight",
        selectedIcon = Icons.Rounded.AutoAwesome,
        unselectedIcon = Icons.Outlined.AutoAwesome,
    )

    data object Profile : BottomNavItem(
        route = AppRoutes.Profile.route,
        label = "Profile",
        selectedIcon = Icons.Rounded.Person,
        unselectedIcon = Icons.Outlined.Person,
    )
}
