package com.kalazacare.app.ui.navigation

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.kalazacare.app.ui.theme.KalazaRed
import com.kalazacare.app.util.SessionManager

data class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

@Composable
fun KalazaBottomNavBar(navController: NavController, currentRoute: String?) {
    val isAdmin = SessionManager.isAdmin()
    val isSupervisor = SessionManager.isSupervisor()   // CHANGE 8

    val staffItems = listOf(
        BottomNavItem(Routes.DASHBOARD, "Patients", Icons.Filled.People, Icons.Outlined.People),
    )
    val supervisorItems = staffItems + listOf(
        BottomNavItem(Routes.MEDICINE, "Medicine", Icons.Filled.Medication, Icons.Outlined.Medication),
    )
    val adminItems = listOf(
        BottomNavItem(Routes.DASHBOARD,      "Patients",  Icons.Filled.People,    Icons.Outlined.People),
        BottomNavItem(Routes.APPROVAL_QUEUE, "Approvals", Icons.Filled.Approval,  Icons.Outlined.Approval),
        BottomNavItem(Routes.AUDIT_LOG,      "Audit Log", Icons.Filled.History,   Icons.Outlined.History),
        BottomNavItem(Routes.CONFIG,         "Config",    Icons.Filled.Settings,  Icons.Outlined.Settings),
        BottomNavItem(Routes.SUMMARY,        "Summary",   Icons.Filled.BarChart,  Icons.Outlined.BarChart),
    )
    val items = when {
        isAdmin      -> adminItems
        isSupervisor -> supervisorItems
        else         -> staffItems
    }

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        windowInsets = NavigationBarDefaults.windowInsets
    ) {
        items.forEach { item ->
            val selected = currentRoute == item.route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (currentRoute != item.route) {
                        navController.navigate(item.route) {
                            popUpTo(Routes.DASHBOARD) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = {
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label,
                        modifier = Modifier.size(22.dp)
                    )
                },
                label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor   = KalazaRed,
                    selectedTextColor   = KalazaRed,
                    indicatorColor      = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            )
        }
    }
}
