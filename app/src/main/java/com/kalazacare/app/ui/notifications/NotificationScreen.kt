package com.kalazacare.app.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kalazacare.app.data.model.AppNotification
import com.kalazacare.app.data.model.NotificationType
import com.kalazacare.app.ui.NotificationViewModel
import com.kalazacare.app.ui.components.EmptyState
import com.kalazacare.app.ui.components.KalazaTopBar
import com.kalazacare.app.ui.theme.KalazaRed
import com.kalazacare.app.util.timeAgo

@Composable
fun NotificationScreen(
    viewModel: NotificationViewModel,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onNotificationClick: (String) -> Unit,
) {
    val notifications by viewModel.notifications.collectAsState()

    Scaffold(
        topBar = {
            KalazaTopBar(
                title = "Notifications",
                onBack = onBack,
                onLogout = onLogout,
                actions = {
                    if (notifications.any { !it.isRead }) {
                        TextButton(onClick = { viewModel.markAllRead() }) {
                            Text("Mark all read", color = com.kalazacare.app.ui.theme.White)
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (notifications.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    title = "No Notifications",
                    message = "You're all caught up.",
                    icon = Icons.Default.NotificationsNone
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(notifications, key = { it.id }) { notification ->
                    NotificationCard(
                        notification = notification,
                        onClick = {
                            viewModel.markRead(notification.id)
                            if (notification.targetRoute.isNotBlank()) onNotificationClick(notification.targetRoute)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(
    notification: AppNotification,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (notification.isRead) MaterialTheme.colorScheme.surface
                              else MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (notification.isRead) 1.dp else 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(KalazaRed.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(iconForType(notification.type), contentDescription = null, tint = KalazaRed, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = notification.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = notification.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = notification.timestamp.timeAgo(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

private fun iconForType(type: NotificationType): ImageVector = when (type) {
    NotificationType.APPROVAL_REQUESTED          -> Icons.Default.CheckCircle
    NotificationType.APPROVAL_APPROVED           -> Icons.Default.CheckCircle
    NotificationType.APPROVAL_REJECTED           -> Icons.Default.Cancel
    NotificationType.ALLOTMENT_REQUESTED         -> Icons.Default.Medication
    NotificationType.ALLOTMENT_FULFILLED         -> Icons.Default.Medication
    NotificationType.MEDICATION_REMINDER         -> Icons.Default.Medication
    NotificationType.MEDICATION_MISSED_ALERT     -> Icons.Default.Cancel
    NotificationType.MEDICATION_MISSED_ESCALATION -> Icons.Default.Cancel
}
