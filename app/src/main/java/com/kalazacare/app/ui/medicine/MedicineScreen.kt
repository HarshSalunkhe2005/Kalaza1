package com.kalazacare.app.ui.medicine

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationImportant
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kalazacare.app.data.model.AllotmentRequest
import com.kalazacare.app.ui.MedicineRoundItem
import com.kalazacare.app.ui.MedicineViewModel
import com.kalazacare.app.ui.components.EmptyState
import com.kalazacare.app.ui.components.KalazaTopBar
import com.kalazacare.app.ui.components.NotificationBell
import com.kalazacare.app.ui.components.PhotoConfirmDialog
import com.kalazacare.app.ui.theme.KalazaRed
import com.kalazacare.app.ui.theme.StatusSuccess
import com.kalazacare.app.util.DateUtils

@Composable
fun MedicineScreen(
    viewModel: MedicineViewModel,
    unreadNotifications: Int,
    onNotificationsClick: () -> Unit,
    onLogout: () -> Unit,
) {
    val dueForAllotment by viewModel.dueForAllotment.collectAsState()
    val pendingRequests  by viewModel.pendingRequests.collectAsState()

    // CHANGE 2: track which entry is being allotted (from rounds list)
    var allotTarget: MedicineRoundItem? by remember { mutableStateOf(null) }
    // CHANGE 1 FIX: track request being fulfilled — no longer requires entry lookup
    var fulfillTarget: AllotmentRequest? by remember { mutableStateOf(null) }

    Scaffold(
        topBar = {
            KalazaTopBar(
                title = "Supervisor Rounds",   // CHANGE 8: renamed
                onLogout = onLogout,
                actions = { NotificationBell(count = unreadNotifications, onClick = onNotificationsClick) }
            )
        }
    ) { innerPadding ->
        if (dueForAllotment.isEmpty() && pendingRequests.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                EmptyState(title = "All Caught Up", message = "No doses awaiting allotment right now.", icon = Icons.Default.Medication)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ── Allotment Requests (raised by staff, not yet fulfilled) ──
                if (pendingRequests.isNotEmpty()) {
                    item {
                        Text("Allotment Requests", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold, color = KalazaRed)
                    }
                    items(pendingRequests, key = { it.id }) { request ->
                        // CHANGE 1 FIX: button always enabled — ViewModel handles the null-entry case
                        AllotmentRequestCard(
                            request = request,
                            onFulfill = { fulfillTarget = request }
                        )
                    }
                    item { HorizontalDivider() }
                }

                // ── Due For Allotment (supervisor's own rounds) ──
                item {
                    Text("Due For Allotment Today", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, color = KalazaRed)
                }
                items(dueForAllotment, key = { it.entry.id }) { item ->
                    MedicineRoundCard(item = item, onAllot = { allotTarget = item })
                }
            }
        }
    }

    // Dialog: supervisor allotting from their own rounds list
    allotTarget?.let { roundItem ->
        PhotoConfirmDialog(
            title = "Confirm Allotment",
            message = "Confirm you've prepared ${roundItem.entry.medicineName} ${roundItem.entry.dose} " +
                      "for ${roundItem.patientName} (Room ${roundItem.patientRoom}) " +
                      "at ${DateUtils.formatTime(roundItem.entry.scheduleTime)}.",
            onConfirm = {
                viewModel.allot(roundItem.entry)
                allotTarget = null
            },
            onDismiss = { allotTarget = null }
        )
    }

    // Dialog: fulfilling a staff's allotment request
    // CHANGE 1 FIX: pass only the request; ViewModel does the entry lookup
    fulfillTarget?.let { request ->
        PhotoConfirmDialog(
            title = "Fulfill Allotment Request",
            message = "${request.requestedByName} flagged that ${request.medicineName} ${request.dose} " +
                      "for ${request.patientName} wasn't allotted. Confirm now.",
            onConfirm = {
                viewModel.fulfillRequest(request)
                fulfillTarget = null
            },
            onDismiss = { fulfillTarget = null }
        )
    }
}

// CHANGE 2: show "Allotted ✓" once fulfilled instead of button
@Composable
private fun AllotmentRequestCard(request: AllotmentRequest, onFulfill: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.NotificationImportant, contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${request.medicineName} ${request.dose} • ${request.patientName}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = "Requested by ${request.requestedByName} — scheduled ${DateUtils.formatTime(request.scheduledTime)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            // Always show the button (request is only in this list when status == PENDING)
            Button(onClick = onFulfill, colors = ButtonDefaults.buttonColors(containerColor = KalazaRed)) {
                Text("Allot")
            }
        }
    }
}

// CHANGE 2: after allot() the item disappears from dueForAllotment (ViewModel reloads)
// so we don't need disabled-state; just show the card normally.
@Composable
private fun MedicineRoundCard(item: MedicineRoundItem, onAllot: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("${item.entry.medicineName} — ${item.entry.dose}",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("${item.patientName} • Room ${item.patientRoom}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text("Scheduled: ${DateUtils.formatTime(item.entry.scheduleTime)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = onAllot, colors = ButtonDefaults.buttonColors(containerColor = KalazaRed)) {
                Text("Allot")
            }
        }
    }
}
