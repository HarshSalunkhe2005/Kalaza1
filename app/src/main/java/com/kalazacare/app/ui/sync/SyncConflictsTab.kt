package com.kalazacare.app.ui.sync

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kalazacare.app.data.local.PendingOperationEntity
import com.kalazacare.app.ui.theme.KalazaRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Queued offline writes that couldn't auto-apply once this device reconnected —
 * another device changed the same record first (see SyncManager's per-opType
 * conflict rules). Nothing here was silently dropped or double-applied; Super
 * Admin reviews each one against the affected patient's actual current record
 * and dismisses it once satisfied.
 */
@Composable
fun SyncConflictsTab(
    conflicts: List<PendingOperationEntity>,
    onDismiss: (String) -> Unit,
) {
    if (conflicts.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                "No sync conflicts. Offline changes that couldn't be safely re-applied on reconnect will show up here for review.",
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(conflicts, key = { it.id }) { conflict ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.SyncProblem, contentDescription = null, tint = KalazaRed)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            opLabel(conflict.opType),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(conflict.conflictReason ?: "Unknown conflict.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Queued by ${conflict.staffName} · ${formatTime(conflict.createdAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { onDismiss(conflict.id) }) {
                            Text("Dismiss (reconciled manually)")
                        }
                    }
                }
            }
        }
    }
}

private fun opLabel(opType: String): String = when (opType) {
    "MED_MARK_ADMINISTERED" -> "Mark Dose Given"
    "MED_ALLOT" -> "Allot Dose"
    "APPROVAL_REVIEW" -> "Approve/Reject Request"
    "ALLOTMENT_FULFILL" -> "Fulfill Allotment Request"
    "EDIT_VITAL" -> "Edit Vital"
    "EDIT_UTILITY" -> "Edit Utility Record"
    "EDIT_CARE_NOTE" -> "Edit Care Note"
    "EDIT_DOCTOR_VISIT" -> "Edit Doctor Visit"
    "EDIT_PATIENT" -> "Edit Patient"
    "DELETE_DOCTOR_VISIT" -> "Delete Doctor Visit"
    "ARCHIVE_PATIENT" -> "Archive Patient"
    else -> opType
}

private fun formatTime(epochMillis: Long): String =
    SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(epochMillis))
