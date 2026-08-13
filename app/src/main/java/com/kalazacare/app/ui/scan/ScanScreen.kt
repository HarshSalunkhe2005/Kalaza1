package com.kalazacare.app.ui.scan

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kalazacare.app.data.model.DoseTag
import com.kalazacare.app.data.model.MedStatus
import com.kalazacare.app.data.model.MedicationEntry
import com.kalazacare.app.ui.ScanViewModel
import com.kalazacare.app.ui.components.DoseTagPicker
import com.kalazacare.app.ui.components.EmptyState
import com.kalazacare.app.ui.components.KalazaTopBar
import com.kalazacare.app.ui.components.MedStatusBadge
import com.kalazacare.app.ui.components.NotificationBell
import com.kalazacare.app.ui.components.QrScanDialog
import com.kalazacare.app.ui.components.label
import com.kalazacare.app.ui.theme.KalazaRed
import com.kalazacare.app.util.DateUtils

/**
 * Batch-QR administration tab. One scan (patient name + dose tag) unlocks
 * that patient's meds for that time-of-day bucket; every dose is confirmed
 * right here, no per-dose QR. A wrong/unmatched QR hard-blocks — the list
 * never shows until the scan (or the manual fallback) resolves to a real
 * patient + tag. Camera flaky? Manual entry fields do the exact same lookup.
 */
@Composable
fun ScanScreen(
    viewModel: ScanViewModel,
    unreadNotifications: Int,
    onNotificationsClick: () -> Unit,
    onLogout: () -> Unit,
) {
    val patientName by viewModel.matchedPatientName.collectAsState()
    val tag by viewModel.matchedTag.collectAsState()
    val meds by viewModel.meds.collectAsState()
    val error by viewModel.error.collectAsState()
    val loading by viewModel.loading.collectAsState()

    var showQrDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            KalazaTopBar(
                title = "Scan",
                onLogout = onLogout,
                actions = { NotificationBell(count = unreadNotifications, onClick = onNotificationsClick) }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (error != null) {
                ErrorBanner(message = error!!, onDismiss = { viewModel.reset() })
            }

            if (patientName == null) {
                ScanEntry(
                    loading = loading,
                    onScanClick = { showQrDialog = true },
                    onManualSubmit = { name, tag -> viewModel.onManualEntry(name, tag) },
                )
            } else {
                MatchedMedsList(
                    patientName = patientName!!,
                    tag = tag!!,
                    meds = meds,
                    onMarkGiven = { id -> viewModel.markGiven(id, "SCAN_TAB:${patientName}|${tag!!.name}") },
                    onScanAnother = { viewModel.reset() },
                )
            }
        }
    }

    if (showQrDialog) {
        QrScanDialog(
            title = "Scan Patient QR",
            message = "Scan the patient's printed QR for this time of day (Morning / Afternoon / Evening).",
            onConfirm = { scannedCode ->
                viewModel.onQrDecoded(scannedCode)
                showQrDialog = false
            },
            onDismiss = { showQrDialog = false },
        )
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth().padding(16.dp, 16.dp, 16.dp, 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(12.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss) { Text("Try Again") }
        }
    }
}

@Composable
private fun ScanEntry(
    loading: Boolean,
    onScanClick: () -> Unit,
    onManualSubmit: (name: String, tag: DoseTag) -> Unit,
) {
    var manualName by remember { mutableStateOf("") }
    var manualTag by remember { mutableStateOf<DoseTag?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.QrCodeScanner, contentDescription = null,
            tint = KalazaRed, modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Scan a patient's dosing QR to pull up their meds for this round.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onScanClick,
            enabled = !loading,
            colors = ButtonDefaults.buttonColors(containerColor = KalazaRed),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.QrCodeScanner, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Scan QR Code")
        }

        Spacer(Modifier.height(32.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))
        Text("Or enter manually if the QR won't scan:", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = manualName,
            onValueChange = { manualName = it },
            label = { Text("Patient Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        DoseTagPicker(selected = manualTag, onSelect = { manualTag = it })
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { manualTag?.let { onManualSubmit(manualName, it) } },
            enabled = !loading && manualName.isNotBlank() && manualTag != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Fetch Meds") }

        if (loading) {
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator(color = KalazaRed)
        }
    }
}

@Composable
private fun MatchedMedsList(
    patientName: String,
    tag: DoseTag,
    meds: List<MedicationEntry>,
    onMarkGiven: (String) -> Unit,
    onScanAnother: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(patientName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("${tag.label()} round", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onScanAnother) { Text("Scan Another") }
        }

        if (meds.isEmpty()) {
            EmptyState(
                icon = Icons.Default.QrCodeScanner,
                title = "Nothing Tagged for This Round",
                message = "No ${tag.label().lowercase()} meds are scheduled for $patientName today.",
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(meds, key = { it.id }) { entry ->
                    val given = entry.status == MedStatus.ADMINISTERED
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (given) MaterialTheme.colorScheme.surfaceVariant
                            else MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = if (given) 0.dp else 2.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val contentColor = if (given) Color.Gray else MaterialTheme.colorScheme.onSurface
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry.medicineName, style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold, color = contentColor)
                                Text("Dose: ${entry.dose} • Qty: ${entry.quantity}",
                                    style = MaterialTheme.typography.bodyMedium, color = contentColor)
                                Text("Scheduled: ${DateUtils.formatTime(entry.scheduleTime)}",
                                    style = MaterialTheme.typography.labelMedium, color = contentColor)
                            }
                            Spacer(Modifier.width(12.dp))
                            if (given) {
                                MedStatusBadge(status = entry.status)
                            } else {
                                Button(
                                    onClick = { onMarkGiven(entry.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = KalazaRed),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                ) { Text("Mark Given") }
                            }
                        }
                    }
                }
            }
        }
    }
}
