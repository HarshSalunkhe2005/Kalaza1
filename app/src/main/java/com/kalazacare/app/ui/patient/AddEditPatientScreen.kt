package com.kalazacare.app.ui.patient

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kalazacare.app.data.model.Gender
import com.kalazacare.app.data.model.Patient
import com.kalazacare.app.ui.PatientViewModel
import com.kalazacare.app.ui.components.KalazaTextField
import com.kalazacare.app.ui.components.KalazaTopBar
import com.kalazacare.app.ui.theme.KalazaRed
import com.kalazacare.app.util.SessionManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditPatientScreen(
    patientId: String?,
    viewModel: PatientViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val isEditing = !patientId.isNullOrBlank()
    val patient by viewModel.patient.collectAsState()
    val context = LocalContext.current

    var name by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var roomNo by remember { mutableStateOf("") }
    var primaryDiagnosis by remember { mutableStateOf("") }
    var medicalHistory by remember { mutableStateOf("") }
    var currentIssues by remember { mutableStateOf("") }
    var allergies by remember { mutableStateOf("") }
    var emergencyContact by remember { mutableStateOf("") }
    var emergencyPhone by remember { mutableStateOf("") }
    var selectedGender by remember { mutableStateOf(Gender.MALE) }
    var admissionDate by remember { mutableStateOf(java.time.LocalDate.now()) }
    var showAdmissionDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(patientId) {
        if (isEditing && patientId != null) {
            viewModel.load(patientId)
        }
    }

    LaunchedEffect(patient) {
        if (isEditing && patient != null) {
            val p = patient!!
            name = p.name
            age = p.age.toString()
            roomNo = p.roomNo
            primaryDiagnosis = p.primaryDiagnosis
            medicalHistory = p.medicalHistory
            currentIssues = p.currentIssues
            allergies = p.allergies
            emergencyContact = p.emergencyContact
            emergencyPhone = p.emergencyPhone
            selectedGender = p.gender
            admissionDate = p.admissionDate
        }
    }

    Scaffold(
        topBar = {
            KalazaTopBar(
                title = if (isEditing) "Edit Patient" else "Add Patient",
                onBack = onBack
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Basic Info", style = MaterialTheme.typography.titleMedium, color = KalazaRed)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    KalazaTextField(value = name, onValueChange = { name = it }, label = "Full Name")
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row {
                        KalazaTextField(
                            value = age,
                            onValueChange = { age = it.filter { c -> c.isDigit() }.take(3) },
                            label = "Age",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        KalazaTextField(
                            value = roomNo, 
                            onValueChange = { roomNo = it }, 
                            label = "Room No",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text("Gender", style = MaterialTheme.typography.bodyMedium)
                    Row {
                        Gender.values().forEach { gender ->
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                RadioButton(
                                    selected = selectedGender == gender,
                                    onClick = { selectedGender = gender },
                                    colors = RadioButtonDefaults.colors(selectedColor = KalazaRed)
                                )
                                Text(gender.name.lowercase().replaceFirstChar { it.uppercase() })
                                Spacer(modifier = Modifier.width(16.dp))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Admission Date", style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(
                        onClick = { showAdmissionDatePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(admissionDate.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy")))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Medical Details", style = MaterialTheme.typography.titleMedium, color = KalazaRed)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    KalazaTextField(value = primaryDiagnosis, onValueChange = { primaryDiagnosis = it }, label = "Primary Diagnosis")
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    KalazaTextField(value = medicalHistory, onValueChange = { medicalHistory = it }, label = "Medical History", singleLine = false)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    KalazaTextField(value = currentIssues, onValueChange = { currentIssues = it }, label = "Current Issues", singleLine = false)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    KalazaTextField(value = allergies, onValueChange = { allergies = it }, label = "Allergies")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Emergency Contact", style = MaterialTheme.typography.titleMedium, color = KalazaRed)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    KalazaTextField(value = emergencyContact, onValueChange = { emergencyContact = it }, label = "Contact Name")
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    KalazaTextField(
                        value = emergencyPhone,
                        onValueChange = { emergencyPhone = it.filter { c -> c.isDigit() }.take(10) },
                        label = "Phone Number",
                        errorMessage = if (emergencyPhone.isNotEmpty() && emergencyPhone.length < 10) "Must be 10 digits" else null,
                        isError = emergencyPhone.isNotEmpty() && emergencyPhone.length < 10,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            val isAgeValid = (age.toIntOrNull() ?: 0) in 1..120
            val isPhoneValid = emergencyPhone.isEmpty() || emergencyPhone.length == 10
            val canSave = name.isNotBlank() && roomNo.isNotBlank() && isAgeValid && isPhoneValid && (!isEditing || patient != null)

            Button(
                onClick = {
                    if (name.isBlank() || roomNo.isBlank()) {
                        Toast.makeText(context, "Name and Room No are required", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (!isAgeValid) {
                        Toast.makeText(context, "Age must be between 1 and 120", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (!isPhoneValid) {
                        Toast.makeText(context, "Emergency phone must be exactly 10 digits", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val currentPatient = patient
                    if (isEditing && currentPatient == null) {
                        Toast.makeText(context, "Patient data still loading, please wait", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    val p = Patient(
                        id = if (isEditing) patientId!! else "",
                        name = name,
                        age = age.toIntOrNull() ?: 0,
                        gender = selectedGender,
                        roomNo = roomNo,
                        primaryDiagnosis = primaryDiagnosis,
                        medicalHistory = medicalHistory,
                        currentIssues = currentIssues,
                        allergies = allergies,
                        emergencyContact = emergencyContact,
                        emergencyPhone = emergencyPhone,
                        admissionDate = admissionDate
                    )

                    if (isEditing && currentPatient != null) {
                        viewModel.saveOrRequestApproval(currentPatient, p) { success, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            if (success) onSaved()
                        }
                    } else {
                        // New patients are only reachable via the Admin-only FAB, so this
                        // always takes the direct-save + audit-log branch below.
                        viewModel.saveOrRequestApproval(Patient(), p) { success, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            if (success) onSaved()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = KalazaRed),
                enabled = canSave,
            ) {
                Text(if (isEditing) "Save Changes" else "Add Patient")
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showAdmissionDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = admissionDate.toEpochDay() * 86_400_000L
        )
        DatePickerDialog(
            onDismissRequest = { showAdmissionDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        admissionDate = java.time.LocalDate.ofEpochDay(millis / 86_400_000L)
                    }
                    showAdmissionDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showAdmissionDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
