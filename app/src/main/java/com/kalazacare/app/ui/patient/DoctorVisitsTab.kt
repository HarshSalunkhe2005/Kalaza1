package com.kalazacare.app.ui.patient

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kalazacare.app.data.model.DoctorVisit
import com.kalazacare.app.ui.DoctorVisitViewModel
import com.kalazacare.app.ui.components.ConfirmDialog
import com.kalazacare.app.ui.components.EmptyState
import com.kalazacare.app.ui.components.TimeOfDayField
import com.kalazacare.app.ui.theme.KalazaRed
import com.kalazacare.app.ui.theme.StatusSuccess
import com.kalazacare.app.util.DateUtils
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// CHANGE 4: full rewrite — upcoming vs archived tabs, edit, confirm, calendar date picker

@Composable
fun DoctorVisitsTab(
    visits: List<DoctorVisit>,
    patientId: String,
    viewModel: DoctorVisitViewModel,
) {
    var showAddDialog  by remember { mutableStateOf(false) }
    var editTarget     by remember { mutableStateOf<DoctorVisit?>(null) }
    var deleteTarget   by remember { mutableStateOf<DoctorVisit?>(null) }
    var selectedTab    by remember { mutableIntStateOf(0) }   // 0=Upcoming, 1=Archived
    val context = LocalContext.current

    val upcoming = visits.filter { !it.isArchived }.sortedBy { it.date }
    val archived = visits.filter { it.isArchived  }.sortedByDescending { it.date }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Tab strip
            TabRow(
                selectedTabIndex = selectedTab,
                contentColor = KalazaRed,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = KalazaRed, height = 3.dp
                    )
                }
            ) {
                listOf("Upcoming (${upcoming.size})", "Past (${archived.size})").forEachIndexed { idx, title ->
                    Tab(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        text = { Text(title, fontWeight = if (selectedTab == idx) FontWeight.Bold else FontWeight.Normal) },
                        selectedContentColor = KalazaRed,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val list = if (selectedTab == 0) upcoming else archived
            if (list.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    EmptyState(
                        title = if (selectedTab == 0) "No Upcoming Visits" else "No Past Visits",
                        message = if (selectedTab == 0) "Schedule a visit using the + button." else "Completed visits appear here.",
                        icon = Icons.Default.CalendarMonth
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 80.dp),
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(list, key = { it.id }) { visit ->
                        DoctorVisitCard(
                            visit = visit,
                            onEdit = { editTarget = visit },
                            onDelete = { deleteTarget = visit },
                            onConfirm = { viewModel.confirmVisit(visit) }
                        )
                    }
                }
            }
        }

        // FAB — all roles can add/plan a visit (CHANGE 7: not restricted to admin)
        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = KalazaRed,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) { Icon(Icons.Default.Add, "Schedule Visit") }
    }

    if (showAddDialog) {
        VisitDialog(
            title = "Schedule Doctor Visit",
            initial = null,
            patientId = patientId,
            onDismiss = { showAddDialog = false },
            onSave = { visit -> viewModel.addVisit(visit); showAddDialog = false }
        )
    }

    editTarget?.let { v ->
        VisitDialog(
            title = "Edit Doctor Visit",
            initial = v,
            patientId = patientId,
            onDismiss = { editTarget = null },
            onSave = { updated ->
                viewModel.updateVisit(v, updated) { _, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
                editTarget = null
            }
        )
    }

    deleteTarget?.let { v ->
        ConfirmDialog(
            title = "Delete Doctor Visit",
            message = "Delete the visit with Dr. ${v.doctorName}? Non-admin requests go through Admin approval first.",
            confirmText = "Delete",
            isDestructive = true,
            onConfirm = {
                viewModel.requestDelete(v) { _, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null }
        )
    }
}

@Composable
private fun DoctorVisitCard(
    visit: DoctorVisit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onConfirm: () -> Unit,
) {
    val isPast = !visit.date.isAfter(LocalDate.now())
    val dateStr = visit.date.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (visit.isArchived) MaterialTheme.colorScheme.surfaceVariant
                             else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Dr. ${visit.doctorName}", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, color = KalazaRed)
                    Text(visit.specialty, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null,
                            tint = if (isPast) MaterialTheme.colorScheme.error else KalazaRed,
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("$dateStr, ${DateUtils.formatTime(visit.time)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isPast && !visit.isArchived) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (visit.isConfirmed) {
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null,
                                tint = StatusSuccess, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(2.dp))
                            Text("Confirmed", style = MaterialTheme.typography.labelSmall, color = StatusSuccess)
                        }
                    }
                }
            }

            if (visit.notes.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text("Notes: ${visit.notes}", style = MaterialTheme.typography.bodyMedium)
            }
            if (visit.prescriptionChanges.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text("Rx Changes: ${visit.prescriptionChanges}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error)
            }
            if (visit.nextVisitDate != null) {
                Spacer(Modifier.height(4.dp))
                Text("Next Visit: ${visit.nextVisitDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))}",
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }

            // Action row — only for non-archived visits
            if (!visit.isArchived) {
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = null,
                            tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Edit")
                    }
                    if (!visit.isConfirmed) {
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = onConfirm,
                            colors = ButtonDefaults.buttonColors(containerColor = KalazaRed)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Confirm Visit")
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Archive, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Archived", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// Shared dialog for Add and Edit — includes date picker
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VisitDialog(
    title: String,
    initial: DoctorVisit?,
    patientId: String,
    onDismiss: () -> Unit,
    onSave: (DoctorVisit) -> Unit,
) {
    var doctorName          by remember { mutableStateOf(initial?.doctorName ?: "") }
    var specialty           by remember { mutableStateOf(initial?.specialty ?: "") }
    var notes               by remember { mutableStateOf(initial?.notes ?: "") }
    var prescriptionChanges by remember { mutableStateOf(initial?.prescriptionChanges ?: "") }
    var visitDate           by remember { mutableStateOf(initial?.date ?: LocalDate.now().plusDays(7)) }
    var visitTime           by remember { mutableStateOf(initial?.time ?: java.time.LocalTime.of(10, 0)) }
    var showDatePicker      by remember { mutableStateOf(false) }

    // Date picker state
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = visitDate.toEpochDay() * 86_400_000L
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = doctorName, onValueChange = { doctorName = it },
                    label = { Text("Doctor Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = specialty, onValueChange = { specialty = it },
                    label = { Text("Specialty") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

                // CHANGE 4: date picker button
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Visit Date: ${visitDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))}")
                }
                Text("Visit Time:", style = MaterialTheme.typography.bodyMedium)
                TimeOfDayField(initial = visitTime, onChange = { visitTime = it })

                OutlinedTextField(value = notes, onValueChange = { notes = it },
                    label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = prescriptionChanges, onValueChange = { prescriptionChanges = it },
                    label = { Text("Prescription Changes") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        (initial ?: DoctorVisit(patientId = patientId)).copy(
                            doctorName          = doctorName,
                            specialty           = specialty,
                            date                = visitDate,
                            time                = visitTime,
                            notes               = notes,
                            prescriptionChanges = prescriptionChanges,
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = KalazaRed),
                enabled = doctorName.isNotBlank()
            ) { Text(if (initial == null) "Schedule" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    // CHANGE 4: date picker dialog
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        visitDate = LocalDate.ofEpochDay(millis / 86_400_000L)
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
