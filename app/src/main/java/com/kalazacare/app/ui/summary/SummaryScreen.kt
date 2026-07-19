package com.kalazacare.app.ui.summary

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.kalazacare.app.data.model.Patient
import com.kalazacare.app.ui.SummaryStats
import com.kalazacare.app.ui.SummaryViewModel
import com.kalazacare.app.ui.components.KalazaTopBar
import com.kalazacare.app.ui.theme.KalazaRed
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// CHANGE 3: date picker + export report

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(
    viewModel: SummaryViewModel,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onPatientClick: (String) -> Unit,
) {
    val stats        by viewModel.stats.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val patients     by viewModel.patients.collectAsState()
    val context      = LocalContext.current

    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = selectedDate.toEpochDay() * 86_400_000L
    )

    Scaffold(
        topBar = {
            KalazaTopBar(
                title = "Daily Summary",
                onBack = onBack,
                onLogout = onLogout,
                actions = {
                    // CHANGE 3: export button
                    IconButton(onClick = { exportReport(context, selectedDate, stats, patients) }) {
                        Icon(Icons.Default.Share, contentDescription = "Export Report",
                            tint = com.kalazacare.app.ui.theme.White)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {

            // CHANGE 3: clickable date header → date picker
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = selectedDate.format(DateTimeFormatter.ofPattern("EEEE, dd MMM yyyy")),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.DateRange, contentDescription = "Pick Date", tint = KalazaRed)
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

    // CHANGE 3: date picker dialog
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        viewModel.load(LocalDate.ofEpochDay(millis / 86_400_000L))
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

// CHANGE 3: plain-text report export via Android share sheet
private fun exportReport(
    context: Context,
    date: LocalDate,
    stats: SummaryStats,
    patients: List<Patient>
) {
    val dateStr = date.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
    val sb = StringBuilder()
    sb.appendLine("KALAZA CARE — DAILY REPORT")
    sb.appendLine("Date : $dateStr")
    sb.appendLine("Generated : ${java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm"))}")
    sb.appendLine("=".repeat(40))
    sb.appendLine()
    sb.appendLine("SUMMARY STATISTICS")
    sb.appendLine("  Vitals Recorded     : ${stats.vitalsRecorded}")
    sb.appendLine("  Medications Given   : ${stats.medsAdministered}")
    sb.appendLine("  Medications Pending : ${stats.medsPending}")
    sb.appendLine("  Utility Logs        : ${stats.utilityLogs}")
    sb.appendLine("  Approvals Pending   : ${stats.pendingApprovals}")
    sb.appendLine()
    sb.appendLine("ACTIVE PATIENTS (${patients.size})")
    patients.forEach { p ->
        sb.appendLine("  • ${p.name}  |  Room ${p.roomNo}  |  ${p.primaryDiagnosis}")
    }
    sb.appendLine()
    sb.appendLine("--- End of Report ---")

    try {
        val dir  = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: context.filesDir
        val file = File(dir, "KalazaCare_Report_${date}.txt")
        file.writeText(sb.toString())
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Kalaza Care Daily Report — $dateStr")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Report"))
    } catch (e: Exception) {
        // Fallback: share as plain text
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, sb.toString())
            putExtra(Intent.EXTRA_SUBJECT, "Kalaza Care Daily Report — $dateStr")
        }
        context.startActivity(Intent.createChooser(intent, "Share Report"))
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
