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
import android.widget.Toast
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kalazacare.app.data.model.DOSE_WINDOW_MINUTES
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
import com.kalazacare.app.util.TrustedClock
import kotlinx.coroutines.delay
import java.time.LocalDateTime

/** Staff must read the confirmation popup before it lets them through. */
private const val CONFIRM_HOLD_SECONDS = 6

/**
 * Batch-QR administration tab. One scan (patient name + dose tag) unlocks
 * that patient's meds for that time-of-day bucket. Doses are picked via
 * checkbox and confirmed together — e.g. 3 meds all due at 9am get one
 * "Confirm Given" pass — behind a review popup that lists exactly what's
 * about to be marked and holds its OK button briefly so it can't be
 * reflex-tapped past. A wrong/unmatched QR hard-blocks; the manual fallback
 * does the identical lookup when the camera won't cooperate.
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

    LaunchedEffect(Unit) { TrustedClock.syncAsync() }

    var showQrDialog by remember { mutableStateOf(false) }
    var selectedIds by remember(patientName, tag) { mutableStateOf<Set<String>>(emptySet()) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

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
                    selectedIds = selectedIds,
                    onToggleSelected = { id ->
                        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                    },
                    onConfirmSelected = { showConfirmDialog = true },
                    onScanAnother = { viewModel.reset() },
                )
            }
        }
    }

    if (showQrDialog) {
        QrScanDialog(
            onConfirm = { scannedCode ->
                viewModel.onQrDecoded(scannedCode)
                showQrDialog = false
            },
            onDismiss = { showQrDialog = false },
        )
    }

    if (showConfirmDialog) {
        val selectedMeds = meds.filter { it.id in selectedIds }
        ConfirmGivenDialog(
            meds = selectedMeds,
            onConfirm = {
                // The list may have sat open past the window's end (or the 6s review took it there),
                // so re-check eligibility at the moment of confirming rather than trusting the selection.
                val now = TrustedClock.nowIstOrNull()
                val stillDue = selectedMeds.filter { it.eligibility(now) == DoseEligibility.DUE }
                if (stillDue.size < selectedMeds.size) {
                    Toast.makeText(context, "The window has closed — ${selectedMeds.size - stillDue.size} dose(s) were not marked.", Toast.LENGTH_LONG).show()
                }
                viewModel.markGivenBatch(stillDue.map { it.id }, "SCAN_TAB:${patientName}|${tag?.name}")
                selectedIds = emptySet()
                showConfirmDialog = false
            },
            onDismiss = { showConfirmDialog = false },
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

/** Why a dose can't be checked off right now — drives both its visual state and whether it's selectable. */
private enum class DoseEligibility { GIVEN, NOT_YET_DUE, DUE, WINDOW_CLOSED, NO_CLOCK }

/**
 * A dose can only be marked given from its own scheduled time T until T + [DOSE_WINDOW_MINUTES]
 * (strict; [now] is the server-anchored IST time from util/TrustedClock). Before T it's not due yet,
 * after the window it's locked until tomorrow's occurrence, and with no trusted clock (never synced
 * since this phone booted) nothing can be given. Only the Admin override on the Med tab can mark a
 * dose outside its window. A window that crosses midnight (e.g. 11:50 PM) counts its after-midnight
 * part against the previous day's T.
 */
private fun MedicationEntry.eligibility(now: LocalDateTime?): DoseEligibility {
    if (status == MedStatus.ADMINISTERED) return DoseEligibility.GIVEN
    if (now == null) return DoseEligibility.NO_CLOCK
    val dueMin = scheduleTime.hour * 60 + scheduleTime.minute
    val nowMin = now.hour * 60 + now.minute
    var sinceDue = nowMin - dueMin
    if (sinceDue < 0 && dueMin + DOSE_WINDOW_MINUTES > 1440 && nowMin < dueMin + DOSE_WINDOW_MINUTES - 1440) sinceDue += 1440
    return when {
        sinceDue < 0 -> DoseEligibility.NOT_YET_DUE
        sinceDue < DOSE_WINDOW_MINUTES -> DoseEligibility.DUE
        else -> DoseEligibility.WINDOW_CLOSED
    }
}

@Composable
private fun MatchedMedsList(
    patientName: String,
    tag: DoseTag,
    meds: List<MedicationEntry>,
    selectedIds: Set<String>,
    onToggleSelected: (String) -> Unit,
    onConfirmSelected: () -> Unit,
    onScanAnother: () -> Unit,
) {
    // Ticks every 10s so a dose opens (and closes) on its own as the clock reaches T / T+30 — no fresh
    // scan needed. Null until the trusted clock has been anchored to the server since this phone booted.
    val now by produceState(initialValue = TrustedClock.nowIstOrNull()) {
        while (true) { delay(10_000); value = TrustedClock.nowIstOrNull() }
    }

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

        if (now == null) {
            Text(
                "Clock not synced — connect to the internet once so doses can be timed. Nothing can be given until then.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
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
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(meds, key = { it.id }) { entry ->
                    val eligibility = entry.eligibility(now)
                    val contentColor = if (eligibility == DoseEligibility.DUE) MaterialTheme.colorScheme.onSurface else Color.Gray
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (eligibility == DoseEligibility.DUE) MaterialTheme.colorScheme.surface
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = if (eligibility == DoseEligibility.DUE) 2.dp else 0.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = entry.id in selectedIds,
                                onCheckedChange = { onToggleSelected(entry.id) },
                                enabled = eligibility == DoseEligibility.DUE,
                                colors = CheckboxDefaults.colors(checkedColor = KalazaRed),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry.medicineName, style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold, color = contentColor)
                                Text("Dose: ${entry.dose} • Qty: ${entry.quantity}",
                                    style = MaterialTheme.typography.bodyMedium, color = contentColor)
                                Text("Scheduled: ${DateUtils.formatTime(entry.scheduleTime)}",
                                    style = MaterialTheme.typography.labelMedium, color = contentColor)
                            }
                            Spacer(Modifier.width(8.dp))
                            when (eligibility) {
                                DoseEligibility.GIVEN -> MedStatusBadge(status = entry.status)
                                DoseEligibility.NOT_YET_DUE -> Text(
                                    "Not due yet", style = MaterialTheme.typography.labelSmall, color = Color.Gray,
                                )
                                DoseEligibility.WINDOW_CLOSED -> Text(
                                    "Window closed", style = MaterialTheme.typography.labelSmall, color = Color.Gray,
                                )
                                DoseEligibility.NO_CLOCK -> Text(
                                    "Clock not synced", style = MaterialTheme.typography.labelSmall, color = Color.Gray,
                                )
                                DoseEligibility.DUE -> {}
                            }
                        }
                    }
                }
            }

            if (selectedIds.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp,
                ) {
                    Button(
                        onClick = onConfirmSelected,
                        colors = ButtonDefaults.buttonColors(containerColor = KalazaRed),
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    ) { Text("Confirm ${selectedIds.size} Given") }
                }
            }
        }
    }
}

/**
 * Lists exactly what's about to be marked given and holds OK for
 * [CONFIRM_HOLD_SECONDS] so a staff member can't reflex-tap through a batch
 * without actually reading it — Cancel stays live the whole time.
 */
@Composable
private fun ConfirmGivenDialog(
    meds: List<MedicationEntry>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var secondsLeft by remember { mutableStateOf(CONFIRM_HOLD_SECONDS) }
    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1000)
            secondsLeft -= 1
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm Medicines Given", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Double-check this list against what's actually in hand before confirming:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                meds.forEach { entry ->
                    Text(
                        "• ${entry.medicineName} — ${entry.dose}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = secondsLeft == 0,
                colors = ButtonDefaults.buttonColors(containerColor = KalazaRed),
            ) { Text(if (secondsLeft > 0) "OK ($secondsLeft)" else "OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
