package com.moodified.app.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.ui.graphics.vector.ImageVector
import com.moodified.app.core.navigation.AppRoutes

sealed class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val isAction: Boolean = false
) {
    data object CheckIn : BottomNavItem(
        route          = AppRoutes.CheckIn.route,
        label          = "Check-in",
        selectedIcon   = Icons.Rounded.Favorite,
        unselectedIcon = Icons.Outlined.FavoriteBorder
    )
    data object Insight : BottomNavItem(
        route          = AppRoutes.Insight.route,
        label          = "Insight",
        selectedIcon   = Icons.Rounded.AutoAwesome,
        unselectedIcon = Icons.Outlined.AutoAwesome
    )
    data object QuickLog : BottomNavItem(
        route          = AppRoutes.QuickLog.route,
        label          = "",
        selectedIcon   = Icons.Rounded.AddCircle,
        unselectedIcon = Icons.Outlined.AddCircleOutline,
        isAction       = true
    )
    data object Care : BottomNavItem(
        route          = AppRoutes.Care.route,
        label          = "Care",
        selectedIcon   = Icons.Rounded.SelfImprovement,
        unselectedIcon = Icons.Outlined.SelfImprovement
    )
    data object More : BottomNavItem(
        route          = AppRoutes.More.route,
        label          = "More",
        selectedIcon   = Icons.Rounded.MoreHoriz,
        unselectedIcon = Icons.Outlined.MoreHoriz
    )
}