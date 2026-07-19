package com.kalazacare.app.ui.patient

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kalazacare.app.ui.*
import com.kalazacare.app.ui.components.ConfirmDialog
import com.kalazacare.app.ui.components.KalazaTopBar
import com.kalazacare.app.ui.components.PhotoConfirmDialog
import com.kalazacare.app.ui.mar.MarTable
import com.kalazacare.app.ui.theme.KalazaRed
import com.kalazacare.app.ui.utility.UtilityTable
import com.kalazacare.app.ui.vitals.VitalsTable
import com.kalazacare.app.util.SessionManager
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.foundation.ExperimentalFoundationApi

/**
 * Patient Profile — the central hub for all patient interactions.
 * Uses a tabbed layout with 6 tabs: Info | Vitals | MAR | Utilities | Visits | Notes
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PatientProfileScreen(
    patientId: String,
    factory: KalazaViewModelFactory,
    onBack: () -> Unit,
    onEditPatient: () -> Unit,
) {
    val patientVm: PatientViewModel = viewModel(factory = factory)
    val vitalsVm: VitalsViewModel = viewModel(factory = factory)
    val marVm: MarViewModel = viewModel(factory = factory)
    val utilityVm: UtilityViewModel = viewModel(factory = factory)
    val doctorVisitVm: DoctorVisitViewModel = viewModel(factory = factory)
    val careNoteVm: CareNoteViewModel = viewModel(factory = factory)

    val patient by patientVm.patient.collectAsState()
    val vitals by vitalsVm.vitals.collectAsState()
    val medications by marVm.medications.collectAsState()
    val utilityRecords by utilityVm.records.collectAsState()
    val utilityItems by utilityVm.items.collectAsState()
    val doctorVisits by doctorVisitVm.visits.collectAsState()
    val careNotes by careNoteVm.notes.collectAsState()

    var loadAttempted by remember { mutableStateOf(false) }
    LaunchedEffect(patientId) {
        patientVm.load(patientId)
        vitalsVm.load(patientId)
        marVm.load(patientId)
        utilityVm.load(patientId)
        doctorVisitVm.load(patientId)
        careNoteVm.load(patientId)
        loadAttempted = true
    }

    val tabs = listOf("Info", "Vitals", "MAR", "Utilities", "Visits", "Notes")
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val coroutineScope = rememberCoroutineScope()

    val p = patient
    var showMenu by remember { mutableStateOf(false) }
    var showArchiveConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            KalazaTopBar(
                title = p?.name ?: "Patient",
                onBack = onBack,
                actions = {
                    IconButton(onClick = onEditPatient) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Patient",
                            tint = KalazaRed
                        )
                    }
                    if (SessionManager.isAdmin() && p != null) {
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More Options",
                                    tint = com.kalazacare.app.ui.theme.White
                                )
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(if (p.isArchived) "Already Archived" else "Archive Patient") },
                                    enabled = !p.isArchived,
                                    leadingIcon = { Icon(Icons.Default.Archive, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        showArchiveConfirm = true
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (showArchiveConfirm && p != null) {
            ConfirmDialog(
                title = "Archive Patient",
                message = "Archive ${p.name}'s record? They will be hidden from the main patient list but can still be found via \"Show Archived\" on the Dashboard.",
                confirmText = "Archive",
                isDestructive = true,
                onConfirm = {
                    patientVm.archivePatient(p)
                    showArchiveConfirm = false
                    onBack()
                },
                onDismiss = { showArchiveConfirm = false }
            )
        }
        if (p == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                if (loadAttempted) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Patient not found",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = onBack) { Text("Go Back") }
                    }
                } else {
                    CircularProgressIndicator(color = KalazaRed)
                }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ── Patient Header Card ──
            PatientHeaderCard(patient = p)

            // ── Tab Row ──
            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = KalazaRed,
                edgePadding = 0.dp,
                indicator = { tabPositions ->
                    if (pagerState.currentPage < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                            color = KalazaRed,
                            height = 3.dp
                        )
                    }
                },
                divider = {
                    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                        text = {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                        selectedContentColor = KalazaRed,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ── Tab Content ──
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                // Tabs are already switchable by tapping; disabling the pager's own
                // swipe gesture stops it from swallowing horizontal drags meant for
                // the Vitals/Utility tables' own sideways scroll underneath.
                userScrollEnabled = false,
            ) { page ->
                when (page) {
                    0 -> InfoTab(patient = p)
                    1 -> VitalsTabContent(
                        vitals = vitals,
                        patientId = patientId,
                        vitalsVm = vitalsVm,
                        patientName = p.name,
                        patientAge = p.age,
                        patientGender = p.gender.name,
                        patientRoom = p.roomNo,
                    )
                    2 -> MarTabContent(
                        medications = medications,
                        patientId = patientId,
                        marVm = marVm,
                    )
                    3 -> UtilityTabContent(
                        records = utilityRecords,
                        items = utilityItems,
                        patientId = patientId,
                        utilityVm = utilityVm,
                    )
                    4 -> DoctorVisitsTab(
                        visits = doctorVisits,
                        patientId = patientId,
                        viewModel = doctorVisitVm,
                    )
                    5 -> CareNotesTab(
                        notes = careNotes,
                        patientId = patientId,
                        viewModel = careNoteVm,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Header Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PatientHeaderCard(patient: com.kalazacare.app.data.model.Patient) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(KalazaRed.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = patient.name.take(1).uppercase(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = KalazaRed,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = patient.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${patient.age}${patient.gender.name[0]} • Room ${patient.roomNo}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (patient.primaryDiagnosis.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = KalazaRed.copy(alpha = 0.1f),
                    ) {
                        Text(
                            text = patient.primaryDiagnosis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = KalazaRed,
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Info Tab
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun InfoTab(patient: com.kalazacare.app.data.model.Patient) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PatientSection("Medical History", patient.medicalHistory)
        PatientSection("Current Issues", patient.currentIssues)
        PatientSection("Allergies", patient.allergies.ifBlank { "None known" })
        PatientSection("Emergency Contact", "${patient.emergencyContact}\n${patient.emergencyPhone}")
        PatientSection("Admission Date", patient.admissionDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy")))
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun PatientSection(title: String, content: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = KalazaRed,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = content.ifBlank { "—" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Vitals Tab
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun VitalsTabContent(
    vitals: List<com.kalazacare.app.data.model.VitalRecord>,
    patientId: String,
    vitalsVm: VitalsViewModel,
    patientName: String,
    patientAge: Int,
    patientGender: String,
    patientRoom: String,
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Patient header for chart context
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ) {
                Text(
                    text = "$patientName • ${patientAge}${patientGender[0]} • Room $patientRoom",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            VitalsTable(vitals = vitals)
        }

        // FAB
        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = KalazaRed,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Icon(Icons.Default.Edit, "Add Vital")
        }
    }

    if (showAddDialog) {
        AddVitalsDialog(
            patientId = patientId,
            onDismiss = { showAddDialog = false },
            onSave = { record ->
                vitalsVm.addVital(record)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun AddVitalsDialog(
    patientId: String,
    onDismiss: () -> Unit,
    onSave: (com.kalazacare.app.data.model.VitalRecord) -> Unit,
) {
    var pulse by remember { mutableStateOf("") }
    var bpSystolic by remember { mutableStateOf("") }
    var bpDiastolic by remember { mutableStateOf("") }
    var spo2 by remember { mutableStateOf("") }
    var temp by remember { mutableStateOf("") }
    var sugarFasting by remember { mutableStateOf("") }
    var sugarPP by remember { mutableStateOf("") }

    // Plausible clinical ranges — out-of-range values are almost always a typo,
    // not a real reading, so they're rejected rather than silently stored.
    val pulseError = pulse.isNotBlank() && (pulse.toIntOrNull() ?: -1) !in 30..220
    val bpSysError = bpSystolic.isNotBlank() && (bpSystolic.toIntOrNull() ?: -1) !in 60..260
    val bpDiaError = bpDiastolic.isNotBlank() && (bpDiastolic.toIntOrNull() ?: -1) !in 30..160
    val spo2Error = spo2.isNotBlank() && (spo2.toIntOrNull() ?: -1) !in 0..100
    val tempError = temp.isNotBlank() && (temp.toDoubleOrNull() ?: -1.0) !in 90.0..110.0
    val sugarFastingError = sugarFasting.isNotBlank() && (sugarFasting.toIntOrNull() ?: -1) !in 20..600
    val sugarPPError = sugarPP.isNotBlank() && (sugarPP.toIntOrNull() ?: -1) !in 20..600
    val hasAnyError = pulseError || bpSysError || bpDiaError || spo2Error || tempError || sugarFastingError || sugarPPError

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Vitals", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = pulse, onValueChange = { pulse = it.filter { c -> c.isDigit() }.take(3) }, label = { Text("Pulse (bpm)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, isError = pulseError, supportingText = if (pulseError) { { Text("30–220 bpm") } } else null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = bpSystolic, onValueChange = { bpSystolic = it.filter { c -> c.isDigit() }.take(3) }, label = { Text("BP Sys") }, modifier = Modifier.weight(1f), singleLine = true, isError = bpSysError, supportingText = if (bpSysError) { { Text("60–260") } } else null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(value = bpDiastolic, onValueChange = { bpDiastolic = it.filter { c -> c.isDigit() }.take(3) }, label = { Text("BP Dia") }, modifier = Modifier.weight(1f), singleLine = true, isError = bpDiaError, supportingText = if (bpDiaError) { { Text("30–160") } } else null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                OutlinedTextField(value = spo2, onValueChange = { spo2 = it.filter { c -> c.isDigit() }.take(3) }, label = { Text("SpO2 (%)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, isError = spo2Error, supportingText = if (spo2Error) { { Text("0–100%") } } else null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(value = temp, onValueChange = { input -> temp = input.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Temperature (°F)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, isError = tempError, supportingText = if (tempError) { { Text("90–110 °F") } } else null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = sugarFasting, onValueChange = { sugarFasting = it.filter { c -> c.isDigit() }.take(3) }, label = { Text("Sugar Fast") }, modifier = Modifier.weight(1f), singleLine = true, isError = sugarFastingError, supportingText = if (sugarFastingError) { { Text("20–600") } } else null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(value = sugarPP, onValueChange = { sugarPP = it.filter { c -> c.isDigit() }.take(3) }, label = { Text("Sugar PP") }, modifier = Modifier.weight(1f), singleLine = true, isError = sugarPPError, supportingText = if (sugarPPError) { { Text("20–600") } } else null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        com.kalazacare.app.data.model.VitalRecord(
                            id = "v_${System.currentTimeMillis()}",
                            patientId = patientId,
                            pulse = pulse,
                            bp = "$bpSystolic/$bpDiastolic",
                            spo2 = spo2,
                            temperature = temp,
                            sugarFasting = sugarFasting,
                            sugarPP = sugarPP,
                            signedBy = SessionManager.getCurrentStaffName(),
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = KalazaRed),
                enabled = (pulse.isNotBlank() || bpSystolic.isNotBlank()) && !hasAnyError,
            ) { Text("Sign & Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// MAR Tab
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MarTabContent(
    medications: List<com.kalazacare.app.data.model.MedicationEntry>,
    patientId: String,
    marVm: MarViewModel,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var administerTargetId by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        MarTable(
            medications = medications,
            onMarkAdministered = { id -> administerTargetId = id },
            onRequestAllotment = { entry -> marVm.requestAllotment(entry) },
            onEditMedication   = { updated -> marVm.updateMedication(updated) }
        )

        // Only Admin can add medications
        if (SessionManager.isAdmin()) {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = KalazaRed,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Default.Edit, "Add Medication")
            }
        }
    }

    if (showAddDialog) {
        AddMedicationDialog(
            patientId = patientId,
            onDismiss = { showAddDialog = false },
            onSave = { entry ->
                marVm.addMedication(entry) { warning ->
                    if (warning != null) Toast.makeText(context, warning, Toast.LENGTH_LONG).show()
                }
                showAddDialog = false
            }
        )
    }

    administerTargetId?.let { id ->
        PhotoConfirmDialog(
            title = "Confirm Medication Given",
            message = "Confirm the dose was given to the patient. A photo is required as proof.",
            onConfirm = {
                marVm.markAdministered(id)
                administerTargetId = null
            },
            onDismiss = { administerTargetId = null }
        )
    }
}

@Composable
private fun AddMedicationDialog(
    patientId: String,
    onDismiss: () -> Unit,
    onSave: (com.kalazacare.app.data.model.MedicationEntry) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var dose by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var scheduleTime by remember { mutableStateOf(java.time.LocalTime.of(8, 0)) }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Medication", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Medicine Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = dose, onValueChange = { dose = it }, label = { Text("Dose") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = quantity, onValueChange = { quantity = it }, label = { Text("Qty") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                Text("Time:", style = MaterialTheme.typography.bodyMedium)
                com.kalazacare.app.ui.components.TimeOfDayField(initial = scheduleTime, onChange = { scheduleTime = it })
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        com.kalazacare.app.data.model.MedicationEntry(
                            patientId = patientId,
                            medicineName = name,
                            dose = dose,
                            quantity = quantity,
                            scheduleTime = scheduleTime,
                            notes = notes,
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = KalazaRed),
                enabled = name.isNotBlank(),
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Utility Tab
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun UtilityTabContent(
    records: List<com.kalazacare.app.data.model.UtilityRecord>,
    items: List<com.kalazacare.app.data.model.UtilityItem>,
    patientId: String,
    utilityVm: UtilityViewModel,
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        UtilityTable(records = records, items = items)

        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = KalazaRed,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Icon(Icons.Default.Edit, "Add Utility Record")
        }
    }

    if (showAddDialog) {
        AddUtilityDialog(
            patientId = patientId,
            items = items,
            onDismiss = { showAddDialog = false },
            onSave = { record ->
                utilityVm.addRecord(record)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun AddUtilityDialog(
    patientId: String,
    items: List<com.kalazacare.app.data.model.UtilityItem>,
    onDismiss: () -> Unit,
    onSave: (com.kalazacare.app.data.model.UtilityRecord) -> Unit,
) {
    // One quantity field per configured utility item — whatever Admin adds in
    // Config → Utility Items shows up here automatically.
    val quantities = remember(items) { mutableStateMapOf<String, String>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Utility Record", style = MaterialTheme.typography.titleLarge) },
        text = {
            if (items.isEmpty()) {
                Text("No utility items configured yet. Ask an Admin to add some in Config → Utility Items.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items.chunked(2).forEach { rowItems ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowItems.forEach { item ->
                                OutlinedTextField(
                                    value = quantities[item.id] ?: "",
                                    onValueChange = { input ->
                                        quantities[item.id] = input.filter { it.isDigit() }.take(4)
                                    },
                                    label = { Text("${item.name} (${item.unit})") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                )
                            }
                            if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        com.kalazacare.app.data.model.UtilityRecord(
                            patientId = patientId,
                            quantities = quantities.mapValues { it.value.toIntOrNull() ?: 0 }
                                .filterValues { it > 0 },
                            issuedToCaregiver = SessionManager.getCurrentStaffName(),
                            issuedBySupervisor = if (SessionManager.isAdmin()) SessionManager.getCurrentStaffName() else "",
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = KalazaRed),
                enabled = items.isNotEmpty(),
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
