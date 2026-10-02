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
import com.kalazacare.app.ui.components.EmptyState
import com.kalazacare.app.ui.components.KalazaTopBar
import com.kalazacare.app.ui.theme.KalazaRed
import com.kalazacare.app.util.DateUtils
import com.kalazacare.app.util.DownloadsSaver
import com.kalazacare.app.util.XlsxWriter
import kotlinx.coroutines.launch
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
    val isLoading  by viewModel.isLoading.collectAsState()
    val context    = LocalContext.current
    val scope      = rememberCoroutineScope()
    var isExporting by remember { mutableStateOf(false) }

    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            KalazaTopBar(
                title = "Summary Report",
                onBack = onBack,
                onLogout = onLogout,
                actions = {
                    IconButton(
                        enabled = !isExporting,
                        onClick = {
                            scope.launch {
                                isExporting = true
                                val report = viewModel.buildRangeReport()
                                val savedCount = exportPerPatientReportsToDownloads(context, startDate, endDate, report)
                                val message = if (savedCount == report.size) "Saved $savedCount patient report(s) to Downloads"
                                    else "Saved $savedCount of ${report.size} patient report(s) — some failed"
                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                isExporting = false
                            }
                        }
                    ) {
                        if (isExporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = com.kalazacare.app.ui.theme.White,
                            )
                        } else {
                            Icon(Icons.Default.FileDownload, contentDescription = "Download Report (.xlsx)",
                                tint = com.kalazacare.app.ui.theme.White)
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = KalazaRed)
            }
            return@Scaffold
        }
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
                    StatCard("Meds Missed",   stats.medsMissed.toString(),       Modifier.weight(1f), isAlert = stats.medsMissed > 0)
                }
                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatCard("Utility Logs",  stats.utilityLogs.toString(),      Modifier.weight(1f))
                    StatCard("Approvals Pending", stats.pendingApprovals.toString(), Modifier.weight(1f), isAlert = stats.pendingApprovals > 0)
                }
            }

            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Text("Patient Breakdown", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

            if (patients.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.DateRange,
                    title = "No Patients Yet",
                    message = "Patient breakdowns will appear here once patients are added.",
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
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

private val DASH = "—"

/** "value" if present, else the dash placeholder — matches the reference report template. */
private fun field(value: String, suffix: String = ""): String = if (value.isBlank()) DASH else "$value$suffix"

private fun vitalsLine(v: com.kalazacare.app.data.model.VitalRecord): String =
    "Pulse: ${field(v.pulse)} | BP: ${field(v.bp)} | SpO₂: ${field(v.spo2, "%")} | " +
        "Temp: ${field(v.temperature, "°F")} | Fasting: ${field(v.sugarFasting)} | PP: ${field(v.sugarPP)}"

private fun utilityBlock(u: com.kalazacare.app.data.model.UtilityRecord): String =
    "Issued To: ${field(u.issuedToCaregiver)}\nIssued By: ${field(u.issuedBySupervisor)} | Checked: ${field(u.checkedBy)}"

private fun noteLine(n: com.kalazacare.app.data.model.CareNote): String =
    "${n.note} (${DateUtils.formatTime(n.timestamp.toLocalTime())})"

/**
 * A recurring dose's `medications` row only reflects *today's* live status (it resets daily --
 * see MedicationRepository.withComputedStatus), so for any other day in range, whether it was
 * actually given comes from the permanent `medication_evidence_log` instead (every real
 * administration requires a QR-code scan, so a real administration always has a log entry).
 */
private fun medicationLineForDay(
    m: com.kalazacare.app.data.model.MedicationEntry,
    date: LocalDate,
    administeredEvidence: com.kalazacare.app.data.model.MedicationEvidenceEvent?,
): String {
    // Scheduled time is always shown now, regardless of status -- previously it only appeared
    // for Scheduled/PENDING rows, so OVERDUE and "Not Given" rows gave no timing at all.
    val base = "${m.medicineName} — ${m.dose} — ${DateUtils.formatTime(m.scheduleTime)}"
    return when {
        administeredEvidence != null ->
            "$base — ADMINISTERED (${DateUtils.formatTime(administeredEvidence.occurredAt.toLocalTime())})"
        date.isAfter(LocalDate.now()) -> "$base — Scheduled"
        date.isEqual(LocalDate.now()) -> "$base — " + when (m.status) {
            com.kalazacare.app.data.model.MedStatus.OVERDUE -> "Missed"
            com.kalazacare.app.data.model.MedStatus.PENDING -> "Pending"
            com.kalazacare.app.data.model.MedStatus.ADMINISTERED -> "Given"
        }
        else -> "$base — Not Given"
    }
}

private fun sanitizeFileNameComponent(s: String): String =
    s.trim().replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').ifBlank { "Patient" }

/**
 * Replaces the old single combined workbook with one styled "Simple Patient Report" .xlsx
 * per patient (matching the reference template), each saved straight to Downloads. Returns
 * how many of the N patients' files were saved successfully.
 */
private fun exportPerPatientReportsToDownloads(
    context: Context,
    startDate: LocalDate,
    endDate: LocalDate,
    report: List<PatientRangeSummary>,
): Int {
    val dateFmt = DateTimeFormatter.ofPattern("dd MMM yyyy")
    val rangeLabel = if (startDate == endDate) startDate.format(dateFmt) else "${startDate.format(dateFmt)} – ${endDate.format(dateFmt)}"
    val fileRangeLabel = if (startDate == endDate) startDate.toString() else "${startDate}_to_$endDate"
    val mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    val dates = mutableListOf<LocalDate>()
    var d = startDate
    while (!d.isAfter(endDate)) { dates.add(d); d = d.plusDays(1) }

    var savedCount = 0
    report.forEach { r ->
        val rows = dates.map { date ->
            // Recurring doses apply to every day unless narrowed to specific weekdays
            // (recurringDays); one-time doses only on their own scheduled date.
            val medsToday = r.medications.filter { m ->
                if (m.isRecurring) m.recurringDays.isEmpty() || date.dayOfWeek.value in m.recurringDays
                else m.scheduledDate == date
            }
            val vitalsToday = r.vitals.filter { it.date == date }
            val utilityToday = r.utility.filter { it.date == date }
            val notesToday = r.notes.filter { it.timestamp.toLocalDate() == date }.sortedBy { it.timestamp }

            XlsxWriter.ReportRow(
                date = date.format(dateFmt),
                diagnosis = field(r.patient.primaryDiagnosis),
                medication = if (medsToday.isEmpty()) DASH else medsToday.joinToString("\n") { m ->
                    val evidence = r.administrationEvidence
                        .filter { it.medicationId == m.id && it.occurredAt.toLocalDate() == date }
                        .maxByOrNull { it.occurredAt }
                    medicationLineForDay(m, date, evidence)
                },
                vitals = if (vitalsToday.isEmpty()) DASH else vitalsToday.joinToString("\n") { vitalsLine(it) },
                utilities = if (utilityToday.isEmpty()) DASH else utilityToday.joinToString("\n\n") { utilityBlock(it) },
                notes = if (notesToday.isEmpty()) DASH else notesToday.joinToString("\n") { noteLine(it) },
                signedBy = notesToday.lastOrNull()?.staffName?.let { field(it) } ?: DASH,
            )
        }

        val bytes = XlsxWriter().buildPatientReport(r.patient.name, rangeLabel, rows)
        val filename = "KalazaCare_${sanitizeFileNameComponent(r.patient.name)}_$fileRangeLabel.xlsx"
        val saved = try {
            DownloadsSaver.saveToDownloads(context, filename, mimeType, bytes) != null
        } catch (e: Exception) {
            false
        }
        if (saved) savedCount++
    }
    return savedCount
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
