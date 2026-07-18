package com.kalazacare.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kalazacare.app.data.model.*
import com.kalazacare.app.data.repository.*
import com.kalazacare.app.util.PhotoCapture
import com.kalazacare.app.util.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.LocalDateTime

// ─────────────────────────────────────────────────────────────────────────────
// Login
// ─────────────────────────────────────────────────────────────────────────────

class LoginViewModel(
    private val authRepo: AuthRepository
) : ViewModel() {

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _loginState.value = LoginState.Error("Please fill in all fields")
            return
        }
        val staff = authRepo.login(email, password)
        if (staff != null) {
            SessionManager.setCurrentStaff(staff)
            _loginState.value = LoginState.Success(staff)
        } else {
            _loginState.value = LoginState.Error("Invalid credentials or account inactive")
        }
    }

    fun resetState() { _loginState.value = LoginState.Idle }
}

sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    data class Success(val staff: Staff) : LoginState()
    data class Error(val message: String) : LoginState()
}

// ─────────────────────────────────────────────────────────────────────────────
// Dashboard
// ─────────────────────────────────────────────────────────────────────────────

class DashboardViewModel(
    private val patientRepo: PatientRepository,
    private val medRepo: MedicationRepository,
    private val approvalRepo: ApprovalRepository,
) : ViewModel() {

    private val _patients      = MutableStateFlow<List<Patient>>(emptyList())
    val patients: StateFlow<List<Patient>> = _patients.asStateFlow()

    private val _searchQuery   = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _pendingMeds   = MutableStateFlow(0)
    val pendingMeds: StateFlow<Int> = _pendingMeds.asStateFlow()

    private val _pendingApprovals = MutableStateFlow(0)
    val pendingApprovals: StateFlow<Int> = _pendingApprovals.asStateFlow()

    private val _totalPatients = MutableStateFlow(0)
    val totalPatients: StateFlow<Int> = _totalPatients.asStateFlow()

    init { load() }

    fun load() {
        val allPatients = patientRepo.getAllPatients()
        _patients.value = allPatients
        _totalPatients.value = allPatients.size
        _pendingApprovals.value = approvalRepo.getPendingRequests().size
        // Count all pending medications across patients for today
        _pendingMeds.value = allPatients.sumOf { p ->
            medRepo.getMedicationsForPatient(p.id, LocalDate.now())
                .count { it.status == MedStatus.PENDING || it.status == MedStatus.OVERDUE }
        }
    }

    fun search(query: String) {
        _searchQuery.value = query
        _patients.value = if (query.isBlank()) patientRepo.getAllPatients()
                          else patientRepo.searchPatients(query)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Patient Profile
// ─────────────────────────────────────────────────────────────────────────────

class PatientViewModel(
    private val patientRepo: PatientRepository,
    private val approvalRepo: ApprovalRepository,
    private val auditRepo: AuditRepository,
) : ViewModel() {

    private val _patient = MutableStateFlow<Patient?>(null)
    val patient: StateFlow<Patient?> = _patient.asStateFlow()

    fun load(patientId: String) {
        _patient.value = patientRepo.getPatientById(patientId)
    }

    /**
     * Admin saves directly; Staff creates an ApprovalRequest for each changed field.
     */
    fun saveOrRequestApproval(
        original: Patient,
        updated: Patient,
        onResult: (Boolean, String) -> Unit
    ) {
        if (SessionManager.isAdmin()) {
            // Admin: save directly
            if (updated.id.isEmpty()) {
                patientRepo.addPatient(updated.copy(id = "p_${System.currentTimeMillis()}"))
                auditRepo.addLog(AuditLogEntry(
                    id = "al_${System.currentTimeMillis()}",
                    action = "Patient Added",
                    performedById = SessionManager.getCurrentStaffId(),
                    performedByName = SessionManager.getCurrentStaffName(),
                    targetPatientName = updated.name,
                    details = "New patient admitted — Room ${updated.roomNo}",
                    iconName = "person_add"
                ))
            } else {
                patientRepo.updatePatient(updated)
                auditRepo.addLog(AuditLogEntry(
                    id = "al_${System.currentTimeMillis()}",
                    action = "Patient Record Updated",
                    performedById = SessionManager.getCurrentStaffId(),
                    performedByName = SessionManager.getCurrentStaffName(),
                    targetPatientId = updated.id,
                    targetPatientName = updated.name,
                    details = "Patient record updated directly by Admin",
                    iconName = "edit"
                ))
            }
            _patient.value = updated
            onResult(true, "Patient saved successfully")
        } else {
            // Staff: create approval requests for each changed field
            val changes = mutableListOf<Pair<String, Pair<String, String>>>()
            if (original.name != updated.name)
                changes.add("Name" to (original.name to updated.name))
            if (original.age != updated.age)
                changes.add("Age" to (original.age.toString() to updated.age.toString()))
            if (original.gender != updated.gender)
                changes.add("Gender" to (original.gender.name to updated.gender.name))
            if (original.roomNo != updated.roomNo)
                changes.add("Room No" to (original.roomNo to updated.roomNo))
            if (original.medicalHistory != updated.medicalHistory)
                changes.add("Medical History" to (original.medicalHistory to updated.medicalHistory))
            if (original.currentIssues != updated.currentIssues)
                changes.add("Current Issues" to (original.currentIssues to updated.currentIssues))
            if (original.allergies != updated.allergies)
                changes.add("Allergies" to (original.allergies to updated.allergies))
            if (original.emergencyContact != updated.emergencyContact)
                changes.add("Emergency Contact" to (original.emergencyContact to updated.emergencyContact))
            if (original.emergencyPhone != updated.emergencyPhone)
                changes.add("Emergency Phone" to (original.emergencyPhone to updated.emergencyPhone))
            if (original.primaryDiagnosis != updated.primaryDiagnosis)
                changes.add("Primary Diagnosis" to (original.primaryDiagnosis to updated.primaryDiagnosis))

            if (changes.isEmpty()) {
                onResult(false, "No changes detected")
                return
            }

            changes.forEach { (field, vals) ->
                approvalRepo.submitRequest(
                    ApprovalRequest(
                        id = "ar_${System.currentTimeMillis()}_${field.hashCode()}",
                        patientId = original.id,
                        patientName = original.name,
                        requestedById = SessionManager.getCurrentStaffId(),
                        requestedByName = SessionManager.getCurrentStaffName(),
                        fieldChanged = field,
                        oldValue = vals.first,
                        newValue = vals.second
                    )
                )
            }
            onResult(true, "${changes.size} edit request(s) submitted for admin approval")
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Vitals
// ─────────────────────────────────────────────────────────────────────────────

class VitalsViewModel(
    private val repo: VitalsRepository
) : ViewModel() {

    private val _vitals = MutableStateFlow<List<VitalRecord>>(emptyList())
    val vitals: StateFlow<List<VitalRecord>> = _vitals.asStateFlow()

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    fun load(patientId: String) {
        _vitals.value = repo.getVitalsForPatient(patientId)
    }

    fun addVital(record: VitalRecord) {
        repo.addVital(record)
        load(record.patientId)
    }

    fun updateVital(record: VitalRecord) {
        repo.updateVital(record)
        load(record.patientId)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MAR (Medication)
// ─────────────────────────────────────────────────────────────────────────────

class MarViewModel(
    private val repo: MedicationRepository,
    private val allotmentRequestRepo: AllotmentRequestRepository,
    private val patientRepo: PatientRepository,
) : ViewModel() {

    private val _medications = MutableStateFlow<List<MedicationEntry>>(emptyList())
    val medications: StateFlow<List<MedicationEntry>> = _medications.asStateFlow()

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    fun load(patientId: String, date: LocalDate = LocalDate.now()) {
        _selectedDate.value  = date
        _medications.value   = repo.getMedicationsForPatient(patientId, date)
    }

    /** Called once the caller has captured photo evidence for the "given" checkpoint. */
    fun markAdministered(id: String) {
        val photo = PhotoCapture.capture()
        repo.markAdministered(id, SessionManager.getCurrentStaffName(), photo.url, photo.expiresAt)
        val patientId = _medications.value.firstOrNull { it.id == id }?.patientId
        if (patientId != null) load(patientId, _selectedDate.value)
    }

    /** Regular staff flagging that medicine-staff forgot to allot this dose ahead of time. */
    fun requestAllotment(entry: MedicationEntry) {
        if (entry.allotmentStatus == AllotmentStatus.ALLOTTED) return
        val patientName = patientRepo.getPatientById(entry.patientId)?.name ?: ""
        allotmentRequestRepo.submitRequest(
            AllotmentRequest(
                id = "arq_${System.currentTimeMillis()}",
                medicationEntryId = entry.id,
                patientId = entry.patientId,
                patientName = patientName,
                medicineName = entry.medicineName,
                scheduledTime = entry.scheduleTime,
                requestedById = SessionManager.getCurrentStaffId(),
                requestedByName = SessionManager.getCurrentStaffName(),
            )
        )
    }

    fun addMedication(entry: MedicationEntry) {
        repo.addMedication(entry.copy(id = "m_${System.currentTimeMillis()}"))
        load(entry.patientId, entry.scheduledDate)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Medicine (medicine-staff allotment rounds)
// ─────────────────────────────────────────────────────────────────────────────

data class MedicineRoundItem(
    val entry: MedicationEntry,
    val patientName: String,
    val patientRoom: String,
)

class MedicineViewModel(
    private val medRepo: MedicationRepository,
    private val patientRepo: PatientRepository,
    private val allotmentRequestRepo: AllotmentRequestRepository,
    private val auditRepo: AuditRepository,
) : ViewModel() {

    private val _dueForAllotment = MutableStateFlow<List<MedicineRoundItem>>(emptyList())
    val dueForAllotment: StateFlow<List<MedicineRoundItem>> = _dueForAllotment.asStateFlow()

    private val _pendingRequests = MutableStateFlow<List<AllotmentRequest>>(emptyList())
    val pendingRequests: StateFlow<List<AllotmentRequest>> = _pendingRequests.asStateFlow()

    init { load() }

    fun load() {
        val today = medRepo.getMedicationsForDate(LocalDate.now())
        _dueForAllotment.value = today
            .filter { it.allotmentStatus == AllotmentStatus.NOT_ALLOTTED && it.status != MedStatus.ADMINISTERED }
            .sortedBy { it.scheduleTime }
            .map { entry ->
                val patient = patientRepo.getPatientById(entry.patientId)
                MedicineRoundItem(entry, patient?.name ?: "Unknown", patient?.roomNo ?: "—")
            }
        _pendingRequests.value = allotmentRequestRepo.getPendingRequests()
    }

    /** Medicine-staff confirms they've prepared a dose (photo evidence already captured by caller). */
    fun allot(entry: MedicationEntry) {
        allotWithoutReload(entry)
        load()
    }

    /** Fulfils a regular staff's "medicine-staff forgot" request, allotting the dose in one step. */
    fun fulfillRequest(request: AllotmentRequest, entry: MedicationEntry) {
        allotWithoutReload(entry)
        allotmentRequestRepo.fulfillRequest(request.id, SessionManager.getCurrentStaffId(), SessionManager.getCurrentStaffName())
        load()
    }

    private fun allotWithoutReload(entry: MedicationEntry) {
        val photo = PhotoCapture.capture()
        medRepo.allotMedication(
            entry.id,
            SessionManager.getCurrentStaffId(),
            SessionManager.getCurrentStaffName(),
            photo.url,
            photo.expiresAt,
        )
        auditRepo.addLog(AuditLogEntry(
            id = "al_${System.currentTimeMillis()}",
            action = "Medication Allotted",
            performedById = SessionManager.getCurrentStaffId(),
            performedByName = SessionManager.getCurrentStaffName(),
            targetPatientId = entry.patientId,
            details = "${entry.medicineName} ${entry.dose} allotted for ${entry.patientId}",
            iconName = "medication",
        ))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Utility
// ─────────────────────────────────────────────────────────────────────────────

class UtilityViewModel(
    private val repo: UtilityRepository
) : ViewModel() {

    private val _records = MutableStateFlow<List<UtilityRecord>>(emptyList())
    val records: StateFlow<List<UtilityRecord>> = _records.asStateFlow()

    private val _items = MutableStateFlow<List<UtilityItem>>(emptyList())
    val items: StateFlow<List<UtilityItem>> = _items.asStateFlow()

    fun load(patientId: String) {
        _records.value = repo.getUtilityForPatient(patientId)
        _items.value   = repo.getUtilityItems()
    }

    fun addRecord(record: UtilityRecord) {
        repo.addUtilityRecord(record.copy(id = "u_${System.currentTimeMillis()}"))
        load(record.patientId)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Doctor Visits
// ─────────────────────────────────────────────────────────────────────────────

class DoctorVisitViewModel(
    private val repo: DoctorVisitRepository
) : ViewModel() {

    private val _visits = MutableStateFlow<List<DoctorVisit>>(emptyList())
    val visits: StateFlow<List<DoctorVisit>> = _visits.asStateFlow()

    fun load(patientId: String) { _visits.value = repo.getVisitsForPatient(patientId) }

    fun addVisit(visit: DoctorVisit) {
        repo.addVisit(visit.copy(id = "dv_${System.currentTimeMillis()}"))
        load(visit.patientId)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Care Notes
// ─────────────────────────────────────────────────────────────────────────────

class CareNoteViewModel(
    private val repo: CareNoteRepository
) : ViewModel() {

    private val _notes = MutableStateFlow<List<CareNote>>(emptyList())
    val notes: StateFlow<List<CareNote>> = _notes.asStateFlow()

    fun load(patientId: String) { _notes.value = repo.getNotesForPatient(patientId) }

    fun addNote(patientId: String, noteText: String) {
        repo.addNote(CareNote(
            id = "cn_${System.currentTimeMillis()}",
            patientId = patientId,
            staffId   = SessionManager.getCurrentStaffId(),
            staffName = SessionManager.getCurrentStaffName(),
            note      = noteText
        ))
        load(patientId)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Approval Queue
// ─────────────────────────────────────────────────────────────────────────────

class ApprovalViewModel(
    private val repo: ApprovalRepository
) : ViewModel() {

    private val _requests = MutableStateFlow<List<ApprovalRequest>>(emptyList())
    val requests: StateFlow<List<ApprovalRequest>> = _requests.asStateFlow()

    init { load() }

    fun load() { _requests.value = repo.getAllRequests() }

    fun approve(id: String) {
        repo.approve(id, SessionManager.getCurrentStaffName())
        load()
    }

    fun reject(id: String, reason: String) {
        repo.reject(id, SessionManager.getCurrentStaffName(), reason)
        load()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Audit Log
// ─────────────────────────────────────────────────────────────────────────────

class AuditLogViewModel(
    private val repo: AuditRepository
) : ViewModel() {

    private val _logs = MutableStateFlow<List<AuditLogEntry>>(emptyList())
    val logs: StateFlow<List<AuditLogEntry>> = _logs.asStateFlow()

    init { load() }

    fun load() { _logs.value = repo.getAllLogs() }
}

// ─────────────────────────────────────────────────────────────────────────────
// Config / Admin
// ─────────────────────────────────────────────────────────────────────────────

class ConfigViewModel(
    private val staffRepo: StaffRepository,
    private val utilityRepo: UtilityRepository,
) : ViewModel() {

    private val _staffList = MutableStateFlow<List<Staff>>(emptyList())
    val staffList: StateFlow<List<Staff>> = _staffList.asStateFlow()

    private val _utilItems = MutableStateFlow<List<UtilityItem>>(emptyList())
    val utilItems: StateFlow<List<UtilityItem>> = _utilItems.asStateFlow()

    init { load() }

    fun load() {
        _staffList.value = staffRepo.getAllStaff()
        _utilItems.value = utilityRepo.getUtilityItems()
    }

    fun addStaff(staff: Staff) {
        staffRepo.addStaff(staff.copy(id = "staff_${System.currentTimeMillis()}"))
        _staffList.value = staffRepo.getAllStaff()
    }

    fun revokeStaff(id: String) {
        staffRepo.revokeStaff(id)
        _staffList.value = staffRepo.getAllStaff()
    }

    fun unrevokeStaff(id: String) {
        staffRepo.unrevokeStaff(id)
        _staffList.value = staffRepo.getAllStaff()
    }

    fun deleteStaff(id: String) {
        staffRepo.deleteStaff(id)
        _staffList.value = staffRepo.getAllStaff()
    }

    fun addUtilityItem(item: UtilityItem) {
        utilityRepo.addUtilityItem(item.copy(id = "ui_${System.currentTimeMillis()}"))
        _utilItems.value = utilityRepo.getUtilityItems()
    }

    fun deleteUtilityItem(id: String) {
        utilityRepo.deleteUtilityItem(id)
        _utilItems.value = utilityRepo.getUtilityItems()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Summary
// ─────────────────────────────────────────────────────────────────────────────

data class SummaryStats(
    val vitalsRecorded: Int = 0,
    val medsAdministered: Int = 0,
    val medsPending: Int = 0,
    val utilityLogs: Int = 0,
    val pendingApprovals: Int = 0,
)

class SummaryViewModel(
    private val medRepo: MedicationRepository,
    private val vitalsRepo: VitalsRepository,
    private val approvalRepo: ApprovalRepository,
    private val patientRepo: PatientRepository,
    private val utilityRepo: UtilityRepository,
) : ViewModel() {

    private val _stats = MutableStateFlow(SummaryStats())
    val stats: StateFlow<SummaryStats> = _stats.asStateFlow()

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _patients = MutableStateFlow<List<Patient>>(emptyList())
    val patients: StateFlow<List<Patient>> = _patients.asStateFlow()

    init { load(LocalDate.now()) }

    fun load(date: LocalDate) {
        _selectedDate.value = date
        val pts = patientRepo.getAllPatients()
        _patients.value = pts

        val allMeds = pts.flatMap { medRepo.getMedicationsForPatient(it.id, date) }
        val allVitals = pts.flatMap { vitalsRepo.getVitalsForDate(it.id, date) }
        val allUtility = pts.sumOf { utilityRepo.getUtilityForPatient(it.id).count { u -> u.date == date } }

        _stats.value = SummaryStats(
            vitalsRecorded   = allVitals.size,
            medsAdministered = allMeds.count { it.status == MedStatus.ADMINISTERED },
            medsPending      = allMeds.count { it.status == MedStatus.PENDING || it.status == MedStatus.OVERDUE },
            utilityLogs      = allUtility,
            pendingApprovals = approvalRepo.getPendingRequests().size
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ViewModelFactory — wires singleton repositories from KalazaApp
// ─────────────────────────────────────────────────────────────────────────────

class KalazaViewModelFactory(
    private val authRepo: AuthRepository,
    private val patientRepo: PatientRepository,
    private val vitalsRepo: VitalsRepository,
    private val medRepo: MedicationRepository,
    private val utilityRepo: UtilityRepository,
    private val doctorVisitRepo: DoctorVisitRepository,
    private val careNoteRepo: CareNoteRepository,
    private val approvalRepo: ApprovalRepository,
    private val auditRepo: AuditRepository,
    private val staffRepo: StaffRepository,
    private val allotmentRequestRepo: AllotmentRequestRepository,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(LoginViewModel::class.java) ->
            LoginViewModel(authRepo) as T
        modelClass.isAssignableFrom(DashboardViewModel::class.java) ->
            DashboardViewModel(patientRepo, medRepo, approvalRepo) as T
        modelClass.isAssignableFrom(PatientViewModel::class.java) ->
            PatientViewModel(patientRepo, approvalRepo, auditRepo) as T
        modelClass.isAssignableFrom(VitalsViewModel::class.java) ->
            VitalsViewModel(vitalsRepo) as T
        modelClass.isAssignableFrom(MarViewModel::class.java) ->
            MarViewModel(medRepo, allotmentRequestRepo, patientRepo) as T
        modelClass.isAssignableFrom(MedicineViewModel::class.java) ->
            MedicineViewModel(medRepo, patientRepo, allotmentRequestRepo, auditRepo) as T
        modelClass.isAssignableFrom(UtilityViewModel::class.java) ->
            UtilityViewModel(utilityRepo) as T
        modelClass.isAssignableFrom(DoctorVisitViewModel::class.java) ->
            DoctorVisitViewModel(doctorVisitRepo) as T
        modelClass.isAssignableFrom(CareNoteViewModel::class.java) ->
            CareNoteViewModel(careNoteRepo) as T
        modelClass.isAssignableFrom(ApprovalViewModel::class.java) ->
            ApprovalViewModel(approvalRepo) as T
        modelClass.isAssignableFrom(AuditLogViewModel::class.java) ->
            AuditLogViewModel(auditRepo) as T
        modelClass.isAssignableFrom(ConfigViewModel::class.java) ->
            ConfigViewModel(staffRepo, utilityRepo) as T
        modelClass.isAssignableFrom(SummaryViewModel::class.java) ->
            SummaryViewModel(medRepo, vitalsRepo, approvalRepo, patientRepo, utilityRepo) as T
        else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
