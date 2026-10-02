package com.kalazacare.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kalazacare.app.ui.theme.KalazaRed
import com.kalazacare.app.ui.theme.StatusSuccess

/**
 * Top app bar with the Kalaza Care dark maroon stripe at the top.
 * Supports optional back navigation, notification bell, and logout action.
 */
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import com.kalazacare.app.R
import com.kalazacare.app.ui.theme.White

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KalazaTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    onLogout: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    var showLogoutConfirm by remember { mutableStateOf(false) }

    Column {
        // Material3's TopAppBar enforces its own ~64dp minimum height no matter what's put
        // inside it, so resizing the logo/text alone couldn't make the bar itself slimmer --
        // a plain Row with an explicit height is used instead, giving full control.
        Surface(color = KalazaRed) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = White
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Image(
                    painter = painterResource(id = R.drawable.logo_kalaza),
                    contentDescription = "Kalaza Care Logo",
                    modifier = Modifier.size(30.dp).clip(RoundedCornerShape(7.dp)),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Kalaza Care",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = White,
                        maxLines = 1,
                    )
                    if (title.isNotEmpty() && title != "Kalaza Care") {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelSmall,
                            color = White.copy(alpha = 0.85f),
                            maxLines = 1,
                        )
                    }
                }

                actions()
                if (onRefresh != null) {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh",
                            tint = White
                        )
                    }
                }
                if (onLogout != null) {
                    IconButton(onClick = { showLogoutConfirm = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Logout",
                            tint = White
                        )
                    }
                }
            }
        }
    }

    // ── Logout confirmation dialog ──
    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("Logout", style = MaterialTheme.typography.titleLarge) },
            text = { Text("Are you sure you want to logout?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutConfirm = false
                        onLogout?.invoke()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Logout")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLogoutConfirm = false },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = StatusSuccess
                    )
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Notification bell icon with a badge count, opening the Notifications screen.
 */
@Composable
fun NotificationBell(
    count: Int,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        BadgedBox(
            badge = {
                if (count > 0) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ) {
                        Text(if (count > 99) "99+" else count.toString())
                    }
                }
            }
        ) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = "Notifications ($count pending)",
                tint = White
            )
        }
    }
}
