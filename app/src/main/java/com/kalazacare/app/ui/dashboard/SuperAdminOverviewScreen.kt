package com.kalazacare.app.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kalazacare.app.data.model.AllotmentRequest
import com.kalazacare.app.data.model.MedStatus
import com.kalazacare.app.ui.DailySummaryViewModel
import com.kalazacare.app.ui.DashboardViewModel
import com.kalazacare.app.ui.PatientDaySummary
import com.kalazacare.app.ui.components.KalazaTopBar
import com.kalazacare.app.ui.components.MedStatusBadge
import com.kalazacare.app.ui.components.QrScanDialog
import com.kalazacare.app.ui.theme.KalazaRed
import com.kalazacare.app.util.DateUtils

/**
 * Super Admin's landing screen after login — a single, information-dense view of today: key
 * numbers, what's outstanding across medications/vitals/utilities/doctor visits (grouped by
 * category or by patient), and the pending approval/allotment requests that actually need a
 * decision. The full patient list, weekly report, and utility-item configuration all still live
 * in their own screens (Patients, Summary, Config on the bottom nav) — this screen is
 * deliberately just "today", not a hub for everything.
 */
@Composable
fun SuperAdminOverviewScreen(
    dashboardViewModel: DashboardViewModel,
    dailySummaryViewModel: DailySummaryViewModel,
    onPatientClick: (String) -> Unit,
    onFulfillAllotment: (AllotmentRequest, scannedCode: String) -> Unit,
    onLogout: () -> Unit,
) {
    val totalPatients by dashboardViewModel.totalPatients.collectAsState()
    val pendingMedsCount by dashboardViewModel.pendingMeds.collectAsState()
    val pendingApprovalsCount by dashboardViewModel.pendingApprovals.collectAsState()
    val patientSummaries by dailySummaryViewModel.patientSummaries.collectAsState()
    val pendingApprovals by dailySummaryViewModel.pendingApprovals.collectAsState()
    val pendingAllotments by dailySummaryViewModel.pendingAllotments.collectAsState()
    val isLoading by dailySummaryViewModel.isLoading.collectAsState()

    var groupByPatient by remember { mutableStateOf(false) }
    var fulfillTarget by remember { mutableStateOf<AllotmentRequest?>(null) }

    Scaffold(
        topBar = {
            KalazaTopBar(
                title = "Today's Overview",
                onLogout = onLogout,
                onRefresh = { dashboardViewModel.load(); dailySummaryViewModel.load() }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OverviewStatCard(modifier = Modifier.weight(1f), title = "Total Patients", value = totalPatients.toString())
                    OverviewStatCard(
                        modifier = Modifier.weight(1f), title = "Pending Meds", value = pendingMedsCount.toString(),
                        isAlert = pendingMedsCount > 0,
                    )
                    OverviewStatCard(
                        modifier = Modifier.weight(1f), title = "Pending Approvals", value = pendingApprovalsCount.toString(),
                        isAlert = pendingApprovalsCount > 0,
                    )
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = KalazaRed)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (pendingApprovals.isNotEmpty() || pendingAllotments.isNotEmpty()) {
                        item {
                            Text(
                                text = "Needs Your Attention",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    if (pendingApprovals.isNotEmpty()) {
                        item {
                            CategorySection(title = "Approval Requests", done = 0, total = pendingApprovals.size, alwaysExpandable = true) {
                                pendingApprovals.forEach { req ->
                                    RemainingRow("${req.patientName}: ${req.fieldChanged} — requested by ${req.requestedByName}")
                                }
                            }
                        }
                    }
                    if (pendingAllotments.isNotEmpty()) {
                        item {
                            CategorySection(title = "Allotment Requests", done = 0, total = pendingAllotments.size, alwaysExpandable = true) {
                                pendingAllotments.forEach { req ->
                                    AllotmentRequestRow(req, onFulfill = { fulfillTarget = req })
                                }
                            }
                        }
                    }

                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Daily Breakdown",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            FilterChip(
                                selected = !groupByPatient,
                                onClick = { groupByPatient = false },
                                label = { Text("By Category") },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = KalazaRed, selectedLabelColor = MaterialTheme.colorScheme.onPrimary),
                            )
                            FilterChip(
                                selected = groupByPatient,
                                onClick = { groupByPatient = true },
                                label = { Text("By Patient") },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = KalazaRed, selectedLabelColor = MaterialTheme.colorScheme.onPrimary),
                            )
                        }
                    }

                    if (!groupByPatient) {
                        val allMeds = patientSummaries.flatMap { s -> s.meds.map { it to s.patient.name } }
                        item {
                            CategorySection(
                                title = "Medications",
                                done = allMeds.count { (m, _) -> m.status == MedStatus.ADMINISTERED },
                                total = allMeds.size,
                            ) {
                                allMeds.filter { (m, _) -> m.status != MedStatus.ADMINISTERED }
                                    .sortedBy { (m, _) -> m.scheduleTime }
                                    .forEach { (m, patientName) ->
                                        RemainingRow("${DateUtils.formatTime(m.scheduleTime)} — ${m.medicineName} for $patientName")
                                    }
                            }
                        }
                        item {
                            CategorySection(
                                title = "Vitals",
                                done = patientSummaries.count { it.vitalsRecordedToday },
                                total = patientSummaries.size,
                            ) {
                                patientSummaries.filter { !it.vitalsRecordedToday }
                                    .forEach { RemainingRow(it.patient.name) }
                            }
                        }
                        item {
                            CategorySection(
                                title = "Utilities",
                                done = patientSummaries.count { it.utilityLoggedToday },
                                total = patientSummaries.size,
                            ) {
                                patientSummaries.filter { !it.utilityLoggedToday }
                                    .forEach { RemainingRow(it.patient.name) }
                            }
                        }
                        val allVisits = patientSummaries.flatMap { s -> s.doctorVisitsToday.map { it to s.patient.name } }
                        if (allVisits.isNotEmpty()) {
                            item {
                                CategorySection(
                                    title = "Doctor Visits",
                                    done = allVisits.count { (v, _) -> v.isConfirmed },
                                    total = allVisits.size,
                                ) {
                                    allVisits.filter { (v, _) -> !v.isConfirmed }
                                        .forEach { (v, patientName) ->
                                            RemainingRow("${DateUtils.formatTime(v.time)} — Dr. ${v.doctorName} for $patientName")
                                        }
                                }
                            }
                        }
                    } else {
                        items(patientSummaries) { summary ->
                            PatientSummarySection(summary, onClick = { onPatientClick(summary.patient.id) })
                        }
                    }
                }
            }
        }
    }

    fulfillTarget?.let { req ->
        QrScanDialog(
            title = "Fulfill Allotment Request",
            message = "${req.medicineName} for ${req.patientName} — scan the medicine's QR code as evidence, or confirm manually.",
            onConfirm = { scannedCode ->
                onFulfillAllotment(req, scannedCode)
                fulfillTarget = null
            },
            onDismiss = { fulfillTarget = null }
        )
    }
}

@Composable
private fun CategorySection(
    title: String,
    done: Int,
    total: Int,
    alwaysExpandable: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (alwaysExpandable) "$total pending" else "$done of $total done",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (done == total && !alwaysExpandable) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                )
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                if (!alwaysExpandable && done == total) {
                    Text("Nothing remaining.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
                }
            }
        }
    }
}

@Composable
private fun RemainingRow(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
}

@Composable
private fun AllotmentRequestRow(request: AllotmentRequest, onFulfill: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "${request.patientName}: ${request.medicineName} (${DateUtils.formatTime(request.scheduledTime)}) — requested by ${request.requestedByName}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onFulfill, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
            Text("Fulfill", color = KalazaRed, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun PatientSummarySection(summary: PatientDaySummary, onClick: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val medsDone = summary.meds.count { it.status == MedStatus.ADMINISTERED }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(summary.patient.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "$medsDone of ${summary.meds.size} meds done",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                )
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                if (summary.meds.isEmpty()) {
                    Text("No medications scheduled today.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        summary.meds.sortedBy { it.scheduleTime }.forEach { m ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    "${DateUtils.formatTime(m.scheduleTime)} — ${m.medicineName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                )
                                MedStatusBadge(m.status)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Vitals recorded today: ${if (summary.vitalsRecordedToday) "Yes" else "No"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (summary.vitalsRecordedToday) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
                Text(
                    "Utility logged today: ${if (summary.utilityLoggedToday) "Yes" else "No"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (summary.utilityLoggedToday) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
                if (summary.doctorVisitsToday.isNotEmpty()) {
                    Text(
                        "Doctor visit today: Dr. ${summary.doctorVisitsToday.first().doctorName}" +
                            if (summary.doctorVisitsToday.first().isConfirmed) " (confirmed)" else " (not yet confirmed)",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (summary.doctorVisitsToday.first().isConfirmed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onClick) { Text("Open Patient", color = KalazaRed) }
            }
        }
    }
}

@Composable
private fun OverviewStatCard(title: String, value: String, modifier: Modifier = Modifier, isAlert: Boolean = false) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isAlert) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(text = title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
