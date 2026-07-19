package com.kalazacare.app.ui.mar

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kalazacare.app.data.model.AllotmentStatus
import com.kalazacare.app.data.model.MedStatus
import com.kalazacare.app.data.model.MedicationEntry
import com.kalazacare.app.ui.components.MedStatusBadge
import com.kalazacare.app.ui.theme.KalazaRed
import com.kalazacare.app.ui.theme.OnSurface
import com.kalazacare.app.ui.theme.OnSurfaceVariant
import com.kalazacare.app.ui.theme.White
import com.kalazacare.app.util.DateUtils
import com.kalazacare.app.util.SessionManager

@Composable
fun MarTable(
    medications: List<MedicationEntry>,
    onMarkAdministered: (String) -> Unit,
    onRequestAllotment: (MedicationEntry) -> Unit = {},
    onEditMedication: ((MedicationEntry) -> Unit)? = null,   // CHANGE 5
    modifier: Modifier = Modifier
) {
    var editTarget by remember { mutableStateOf<MedicationEntry?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(medications) { entry ->
            Card(
                colors = CardDefaults.cardColors(containerColor = White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(entry.medicineName, style = MaterialTheme.typography.titleMedium,
                                color = OnSurface, fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f))
                            // CHANGE 5: edit pencil for admin
                            if (SessionManager.isAdmin()) {
                                IconButton(
                                    onClick = { editTarget = entry },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit Medication",
                                        tint = KalazaRed, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("Dose: ${entry.dose} • Qty: ${entry.quantity}",
                            style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text("Scheduled: ${DateUtils.formatTime(entry.scheduleTime)}",
                            style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant)
                        if (entry.notes.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text("Note: ${entry.notes}", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                        }
                        if (entry.status == MedStatus.ADMINISTERED && entry.administeredAt != null) {
                            Spacer(Modifier.height(4.dp))
                            Text("Administered by ${entry.administeredBy} at ${DateUtils.formatTime(entry.administeredAt.toLocalTime())}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (entry.allotmentStatus == AllotmentStatus.ALLOTTED)
                                "Allotted by ${entry.allottedByName}"
                            else "Not allotted yet",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (entry.allotmentStatus == AllotmentStatus.ALLOTTED)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        )
                        if (entry.allotmentStatus == AllotmentStatus.NOT_ALLOTTED && entry.status != MedStatus.ADMINISTERED) {
                            TextButton(
                                onClick = { onRequestAllotment(entry) },
                                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp)
                            ) {
                                Text("Request Allotment", color = KalazaRed,
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    Spacer(Modifier.width(16.dp))

                    Column(horizontalAlignment = Alignment.End) {
                        MedStatusBadge(status = entry.status)
                        if (entry.status == MedStatus.PENDING || entry.status == MedStatus.OVERDUE) {
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { onMarkAdministered(entry.id) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) { Text("Mark Given") }
                        }
                    }
                }
            }
        }
    }

    // CHANGE 5: edit dialog (admin only)
    editTarget?.let { entry ->
        EditMedicationDialog(
            entry = entry,
            onDismiss = { editTarget = null },
            onSave = { updated ->
                onEditMedication?.invoke(updated)
                editTarget = null
            }
        )
    }
}

@Composable
private fun EditMedicationDialog(
    entry: MedicationEntry,
    onDismiss: () -> Unit,
    onSave: (MedicationEntry) -> Unit,
) {
    var name     by remember { mutableStateOf(entry.medicineName) }
    var dose     by remember { mutableStateOf(entry.dose) }
    var quantity by remember { mutableStateOf(entry.quantity) }
    var hour     by remember { mutableStateOf(entry.scheduleTime.hour.toString()) }
    var minute   by remember { mutableStateOf(entry.scheduleTime.minute.toString().padStart(2, '0')) }
    var notes    by remember { mutableStateOf(entry.notes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Medication", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("Medicine Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = dose, onValueChange = { dose = it },
                        label = { Text("Dose") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = quantity, onValueChange = { quantity = it },
                        label = { Text("Qty") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Time:", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(value = hour, onValueChange = { hour = it },
                        label = { Text("HH") }, modifier = Modifier.width(72.dp), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    Text(":", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(value = minute, onValueChange = { minute = it },
                        label = { Text("MM") }, modifier = Modifier.width(72.dp), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                OutlinedTextField(value = notes, onValueChange = { notes = it },
                    label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(entry.copy(
                        medicineName = name,
                        dose = dose,
                        quantity = quantity,
                        scheduleTime = java.time.LocalTime.of(
                            hour.toIntOrNull()?.coerceIn(0, 23) ?: entry.scheduleTime.hour,
                            minute.toIntOrNull()?.coerceIn(0, 59) ?: entry.scheduleTime.minute
                        ),
                        notes = notes,
                    ))
                },
                colors = ButtonDefaults.buttonColors(containerColor = KalazaRed),
                enabled = name.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
