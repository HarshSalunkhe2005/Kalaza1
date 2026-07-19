package com.kalazacare.app.ui.summary

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kalazacare.app.data.model.Patient
import com.kalazacare.app.ui.PatientRangeSummary
import com.kalazacare.app.ui.SummaryStats
import com.kalazacare.app.ui.SummaryViewModel
import com.kalazacare.app.ui.components.KalazaTopBar
import com.kalazacare.app.ui.theme.KalazaRed
import com.kalazacare.app.util.DownloadsSaver
import com.kalazacare.app.util.XlsxWriter
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// CHANGE 3: date-range picker + xlsx export straight to Downloads

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(
    viewModel: SummaryViewModel,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onPatientClick: (String) -> Unit,
) {
    val stats      by viewModel.stats.collectAsState()
    val startDate  by viewModel.startDate.collectAsState()
    val endDate    by viewModel.endDate.collectAsState()
    val patients   by viewModel.patients.collectAsState()
    val context    = LocalContext.current

    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            KalazaTopBar(
                title = "Summary Report",
                onBack = onBack,
                onLogout = onLogout,
                actions = {
                    IconButton(onClick = {
                        val report = viewModel.buildRangeReport()
                        val savedName = exportXlsxToDownloads(context, startDate, endDate, stats, report)
                        val message = if (savedName != null) "Saved to Downloads: $savedName" else "Export failed"
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Download Report (.xlsx)",
                            tint = com.kalazacare.app.ui.theme.White)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {

            // CHANGE 3: date-range header → start/end date pickers
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = { pickingStart = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.DateRange, contentDescription = null, tint = KalazaRed, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(startDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy")))
                    }
                    Text("to", style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = { pickingEnd = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.DateRange, contentDescription = null, tint = KalazaRed, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(endDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy")))
                    }
                }
            }

            // Stats Grid
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatCard("Vitals Logged",  stats.vitalsRecorded.toString(),   Modifier.weight(1f))
                    StatCard("Meds Given",     stats.medsAdministered.toString(), Modifier.weight(1f))
                }
                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatCard("Meds Pending",  stats.medsPending.toString(),      Modifier.weight(1f), isAlert = stats.medsPending > 0)
                    StatCard("Utility Logs",  stats.utilityLogs.toString(),      Modifier.weight(1f))
                }
                Spacer(Modifier.height(16.dp))
                StatCard("Approvals Pending", stats.pendingApprovals.toString(), Modifier.fillMaxWidth(), isAlert = stats.pendingApprovals > 0)
            }

            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Text("Patient Breakdown", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(patients) { patient ->
                    PatientSummaryCard(patient, onViewDetails = { onPatientClick(patient.id) })
                }
            }
        }
    }

    if (pickingStart) {
        val state = rememberDatePickerState(initialSelectedDateMillis = startDate.toEpochDay() * 86_400_000L)
        DatePickerDialog(
            onDismissRequest = { pickingStart = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        viewModel.load(LocalDate.ofEpochDay(millis / 86_400_000L), endDate)
                    }
                    pickingStart = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingStart = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }

    if (pickingEnd) {
        val state = rememberDatePickerState(initialSelectedDateMillis = endDate.toEpochDay() * 86_400_000L)
        DatePickerDialog(
            onDismissRequest = { pickingEnd = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        viewModel.load(startDate, LocalDate.ofEpochDay(millis / 86_400_000L))
                    }
                    pickingEnd = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingEnd = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }
}

// CHANGE 3: xlsx workbook — a Summary tab plus one tab per patient — saved
// straight to Downloads (no share sheet).
private fun exportXlsxToDownloads(
    context: Context,
    startDate: LocalDate,
    endDate: LocalDate,
    stats: SummaryStats,
    report: List<PatientRangeSummary>,
): String? {
    val rangeLabel = if (startDate == endDate) startDate.toString() else "${startDate}_to_${endDate}"
    val dateFmt = DateTimeFormatter.ofPattern("dd MMM yyyy")

    val writer = XlsxWriter()

    val summaryRows = mutableListOf(
        listOf("Kalaza Care — Summary Report"),
        listOf("Range", "${startDate.format(dateFmt)} to ${endDate.format(dateFmt)}"),
        listOf("Generated", java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm"))),
        listOf(),
        listOf("Metric", "Value"),
        listOf("Vitals Recorded", stats.vitalsRecorded.toString()),
        listOf("Medications Given", stats.medsAdministered.toString()),
        listOf("Medications Pending", stats.medsPending.toString()),
        listOf("Utility Logs", stats.utilityLogs.toString()),
        listOf("Approvals Pending", stats.pendingApprovals.toString()),
        listOf(),
        listOf("Patient", "Room", "Primary Diagnosis"),
    )
    report.forEach { r ->
        summaryRows.add(listOf(r.patient.name, r.patient.roomNo, r.patient.primaryDiagnosis))
    }
    writer.addSheet("Summary", summaryRows)

    report.forEach { r ->
        val rows = mutableListOf<List<String>>()
        rows.add(listOf(r.patient.name))
        rows.add(listOf())
        rows.add(listOf("Vitals"))
        rows.add(listOf("Date", "Time", "Pulse", "BP", "SpO2", "Temp", "Fasting", "PP", "Signed By"))
        r.vitals.forEach { v ->
            rows.add(listOf(v.date.toString(), v.time.toString(), v.pulse, v.bp, v.spo2, v.temperature, v.sugarFasting, v.sugarPP, v.signedBy))
        }
        rows.add(listOf())
        rows.add(listOf("Medications"))
        rows.add(listOf("Date", "Time", "Medicine", "Dose", "Status", "Administered By"))
        r.medications.forEach { m ->
            rows.add(listOf(m.scheduledDate.toString(), m.scheduleTime.toString(), m.medicineName, m.dose, m.status.name, m.administeredBy))
        }
        rows.add(listOf())
        rows.add(listOf("Utility Records"))
        rows.add(listOf("Date", "Time", "Issued To", "Issued By", "Checked By"))
        r.utility.forEach { u ->
            rows.add(listOf(u.date.toString(), u.time.toString(), u.issuedToCaregiver, u.issuedBySupervisor, u.checkedBy))
        }
        rows.add(listOf())
        rows.add(listOf("Doctor Visits"))
        rows.add(listOf("Date", "Time", "Doctor", "Specialty", "Notes"))
        r.visits.forEach { v ->
            rows.add(listOf(v.date.toString(), v.time.toString(), v.doctorName, v.specialty, v.notes))
        }
        rows.add(listOf())
        rows.add(listOf("Care Notes"))
        rows.add(listOf("Timestamp", "Staff", "Note"))
        r.notes.forEach { n ->
            rows.add(listOf(n.timestamp.toString(), n.staffName, n.note))
        }
        writer.addSheet(r.patient.name, rows)
    }

    val bytes = writer.build()
    val filename = "KalazaCare_Report_$rangeLabel.xlsx"
    val mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    return try {
        val uri = DownloadsSaver.saveToDownloads(context, filename, mimeType, bytes)
        if (uri != null) filename else null
    } catch (e: Exception) {
        null
    }
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier = Modifier, isAlert: Boolean = false) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isAlert) MaterialTheme.colorScheme.errorContainer
                             else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (isAlert) MaterialTheme.colorScheme.error else KalazaRed)
            Spacer(Modifier.height(4.dp))
            Text(title, style = MaterialTheme.typography.bodyMedium,
                color = if (isAlert) MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun PatientSummaryCard(patient: Patient, onViewDetails: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(patient.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Room ${patient.roomNo}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onViewDetails) { Text("View Details", color = KalazaRed) }
        }
    }
}
