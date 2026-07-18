package com.kalazacare.app.ui.medicine

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationImportant
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kalazacare.app.data.model.AllotmentRequest
import com.kalazacare.app.data.model.MedicationEntry
import com.kalazacare.app.ui.MedicineRoundItem
import com.kalazacare.app.ui.MedicineViewModel
import com.kalazacare.app.ui.components.EmptyState
import com.kalazacare.app.ui.components.KalazaTopBar
import com.kalazacare.app.ui.components.PhotoConfirmDialog
import com.kalazacare.app.ui.theme.KalazaRed
import com.kalazacare.app.util.DateUtils

@Composable
fun MedicineScreen(
    viewModel: MedicineViewModel,
    onLogout: () -> Unit,
) {
    val dueForAllotment by viewModel.dueForAllotment.collectAsState()
    val pendingRequests by viewModel.pendingRequests.collectAsState()

    var allotTarget by remember { mutableStateOf<MedicationEntry?>(null) }
    var fulfillTarget by remember { mutableStateOf<Pair<AllotmentRequest, MedicationEntry>?>(null) }

    Scaffold(
        topBar = {
            KalazaTopBar(title = "Medicine Rounds", onLogout = onLogout)
        }
    ) { innerPadding ->
        if (dueForAllotment.isEmpty() && pendingRequests.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    title = "All Caught Up",
                    message = "No doses awaiting allotment right now.",
                    icon = Icons.Default.Medication
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (pendingRequests.isNotEmpty()) {
                    item {
                        Text(
                            text = "Allotment Requests",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = KalazaRed
                        )
                    }
                    items(pendingRequests) { request ->
                        val entry = dueForAllotment.firstOrNull { it.entry.id == request.medicationEntryId }?.entry
                        AllotmentRequestCard(
                            request = request,
                            onFulfill = { if (entry != null) fulfillTarget = request to entry }
                        )
                    }
                    item { HorizontalDivider() }
                }

                item {
                    Text(
                        text = "Due For Allotment Today",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = KalazaRed
                    )
                }
                items(dueForAllotment, key = { it.entry.id }) { item ->
                    MedicineRoundCard(
                        item = item,
                        onAllot = { allotTarget = item.entry }
                    )
                }
            }
        }
    }

    allotTarget?.let { entry ->
        PhotoConfirmDialog(
            title = "Confirm Allotment",
            message = "Confirm you've prepared ${entry.medicineName} ${entry.dose} for handoff at ${DateUtils.formatTime(entry.scheduleTime)}.",
            onConfirm = {
                viewModel.allot(entry)
                allotTarget = null
            },
            onDismiss = { allotTarget = null }
        )
    }

    fulfillTarget?.let { (request, entry) ->
        PhotoConfirmDialog(
            title = "Fulfill Allotment Request",
            message = "${request.requestedByName} flagged that ${request.medicineName} for ${request.patientName} wasn't allotted yet. Confirm now.",
            onConfirm = {
                viewModel.fulfillRequest(request, entry)
                fulfillTarget = null
            },
            onDismiss = { fulfillTarget = null }
        )
    }
}

@Composable
private fun AllotmentRequestCard(
    request: AllotmentRequest,
    onFulfill: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.NotificationImportant, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${request.medicineName} • ${request.patientName}",
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
            Button(
                onClick = onFulfill,
                colors = ButtonDefaults.buttonColors(containerColor = KalazaRed)
            ) { Text("Allot") }
        }
    }
}

@Composable
private fun MedicineRoundCard(
    item: MedicineRoundItem,
    onAllot: () -> Unit,
) {
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
                Text(
                    text = "${item.entry.medicineName} — ${item.entry.dose}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${item.patientName} • Room ${item.patientRoom}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Scheduled: ${DateUtils.formatTime(item.entry.scheduleTime)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = onAllot,
                colors = ButtonDefaults.buttonColors(containerColor = KalazaRed)
            ) { Text("Allot") }
        }
    }
}
