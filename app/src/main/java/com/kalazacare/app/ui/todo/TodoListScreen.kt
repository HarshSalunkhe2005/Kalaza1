package com.kalazacare.app.ui.todo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kalazacare.app.ui.MedicineRoundItem
import com.kalazacare.app.ui.TodoListViewModel
import com.kalazacare.app.ui.components.EmptyState
import com.kalazacare.app.ui.components.KalazaTopBar
import com.kalazacare.app.ui.components.MedStatusBadge
import com.kalazacare.app.ui.components.NotificationBell
import com.kalazacare.app.ui.theme.KalazaRed
import com.kalazacare.app.util.DateUtils

/**
 * Landing screen for STAFF and SUPERVISOR after login -- today's medication tasks across every
 * patient, in one flat time-sorted list, instead of having to browse into each patient's tab to
 * find what's due. Medicine-only for now; tapping a row jumps into that patient's profile where
 * the existing allot/administer flow (photo evidence etc.) already lives, so nothing about how
 * a dose actually gets marked done is duplicated here.
 */
@Composable
fun TodoListScreen(
    viewModel: TodoListViewModel,
    unreadNotifications: Int,
    onNotificationsClick: () -> Unit,
    onTaskClick: (patientId: String) -> Unit,
    onLogout: () -> Unit,
) {
    val tasks by viewModel.tasks.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Scaffold(
        topBar = {
            KalazaTopBar(
                title = "Today's Tasks",
                onLogout = onLogout,
                onRefresh = { viewModel.load() },
                actions = { NotificationBell(count = unreadNotifications, onClick = onNotificationsClick) }
            )
        }
    ) { innerPadding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = KalazaRed)
            }
        } else if (tasks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                EmptyState(
                    title = "All Caught Up",
                    message = "No medications due today.",
                    icon = Icons.Filled.CheckCircle,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(tasks) { task ->
                    TodoTaskCard(task = task, onClick = { onTaskClick(task.entry.patientId) })
                }
            }
        }
    }
}

@Composable
private fun TodoTaskCard(task: MedicineRoundItem, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = DateUtils.formatTime(task.entry.scheduleTime),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = KalazaRed,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${task.entry.medicineName} — ${task.patientName} (Room ${task.patientRoom})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            MedStatusBadge(task.entry.status)
            Spacer(modifier = Modifier.width(4.dp))
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
