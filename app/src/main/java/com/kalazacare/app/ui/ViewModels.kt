package com.kalazacare.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kalazacare.app.data.model.*
import com.kalazacare.app.data.repository.*
import com.kalazacare.app.util.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

// ─────────────────────────────────────────────────────────────────────────────
// Login
// ─────────────────────────────────────────────────────────────────────────────

class LoginViewModel(private val authRepo: AuthRepository) : ViewModel() {
    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    fun login(name: String, password: String) {
        if (name.isBlank() || password.isBlank()) {
            _loginState.value = LoginState.Error("Please fill in all fields")
            return
        }
        _loginState.value = LoginState.Loading
        viewModelScope.launch {
            val staff = authRepo.login(name, password)
            if (staff != null) {
                SessionManager.setCurrentStaff(staff)
                _loginState.value = LoginState.Success(staff)
            } else {
                _loginState.value = LoginState.Error("Invalid credentials or account inactive")
            }
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
    private val _showArchived  = MutableStateFlow(false)
    val showArchived: StateFlow<Boolean> = _showArchived.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            val allPatients = patientRepo.getAllPatients()
            _totalPatients.value = allPatients.size
            _pendingApprovals.value = approvalRepo.getPendingRequests().size
            _pendingMeds.value = allPatients.sumOf { p ->
                medRepo.getMedicationsForPatient(p.id, LocalDate.now())
                    .count { it.status == MedStatus.PENDING || it.status == MedStatus.OVERDUE }
            }
            applyFilters()
            _isLoading.value = false
        }
    }
    fun search(query: String) { _searchQuery.value = query; applyFilters() }
    fun setShowArchived(show: Boolean) { _showArchived.value = show; applyFilters() }
    private fun applyFilters() {
        viewModelScope.launch {
            val query = _searchQuery.value
            _patients.value = if (query.isBlank()) patientRepo.getAllPatients(includeArchived = _showArchived.value)
                              else patientRepo.searchPatients(query, includeArchived = _showArchived.value)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Patient Profile
// ─────────────────────────────────────────────────────────────────────────────

class PatientViewModel(
    private val patientRepo: PatientRepository,
    private val approvalRepo: ApprovalRepository,
    private val auditRepo: AuditRepository,
    private val notificationRepo: NotificationRepository,
) : ViewModel() {
    private val _patient = MutableStateFlow<Patient?>(null)
    val patient: StateFlow<Patient?> = _patient.asStateFlow()

    fun load(patientId: String) {
        viewModelScope.launch { _patient.value = patientRepo.getPatientById(patientId) }
    }

    // All roles can edit; staff edits go to the approval queue, Super Admin saves directly.
    fun saveOrRequestApproval(original: Patient, updated: Patient, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            if (SessionManager.isAdmin()) {
                if (updated.id.isEmpty()) {
                    val saved = patientRepo.addPatient(updated)
                    auditRepo.addLog(AuditLogEntry(
                        action = "Patient Added",
                        performedById = SessionManager.getCurrentStaffId(),
                        performedByName = SessionManager.getCurrentStaffName(),
                        targetPatientName = saved.name,
                        details = "New patient admitted — Room ${saved.roomNo}",
                        iconName = "person_add"
                    ))
                    _patient.value = saved
                } else {
                    patientRepo.updatePatient(updated)
                    auditRepo.addLog(AuditLogEntry(
                        action = "Patient Record Updated",
                        performedById = SessionManager.getCurrentStaffId(),
                        performedByName = SessionManager.getCurrentStaffName(),
                        targetPatientId = updated.id,
                        targetPatientName = updated.name,
                        details = "Patient record updated directly by Admin",
                        iconName = "edit"
                    ))
                    _patient.value = updated
                }
                onResult(true, "Patient saved successfully")
            } else {
                // Staff & Supervisor: create approval requests for each changed field
                val changes = mutableListOf<Pair<String, Pair<String, String>>>()
                if (original.name != updated.name) changes.add("Name" to (original.name to updated.name))
                if (original.age != updated.age) changes.add("Age" to (original.age.toString() to updated.age.toString()))
                if (original.gender != updated.gender) changes.add("Gender" to (original.gender.name to updated.gender.name))
                if (original.roomNo != updated.roomNo) changes.add("Room No" to (original.roomNo to updated.roomNo))
                if (original.medicalHistory != updated.medicalHistory) changes.add("Medical History" to (original.medicalHistory to updated.medicalHistory))
                if (original.currentIssues != updated.currentIssues) changes.add("Current Issues" to (original.currentIssues to updated.currentIssues))
                if (original.allergies != updated.allergies) changes.add("Allergies" to (original.allergies to updated.allergies))
                if (original.emergencyContact != updated.emergencyContact) changes.add("Emergency Contact" to (original.emergencyContact to updated.emergencyContact))
                if (original.emergencyPhone != updated.emergencyPhone) changes.add("Emergency Phone" to (original.emergencyPhone to updated.emergencyPhone))
                if (original.primaryDiagnosis != updated.primaryDiagnosis) changes.add("Primary Diagnosis" to (original.primaryDiagnosis to updated.primaryDiagnosis))
                if (original.admissionDate != updated.admissionDate) changes.add("Admission Date" to (original.admissionDate.toString() to updated.admissionDate.toString()))
                if (changes.isEmpty()) { onResult(false, "No changes detected"); return@launch }
                changes.forEach { (field, vals) ->
                    approvalRepo.submitRequest(ApprovalRequest(
                        patientId = original.id,
                        patientName = original.name,
                        requestedById = SessionManager.getCurrentStaffId(),
                        requestedByName = SessionManager.getCurrentStaffName(),
                        fieldChanged = field,
                        oldValue = vals.first,
                        newValue = vals.second
                    ))
                }
                notificationRepo.add(AppNotification(
                    recipientRole = UserRole.SUPER_ADMIN,
                    type = NotificationType.APPROVAL_REQUESTED,
                    title = "New Edit Request",
                    message = "${SessionManager.getCurrentStaffName()} requested ${changes.size} change(s) to ${original.name}",
                    targetRoute = "approval",
                ))
                onResult(true, "${changes.size} edit request(s) submitted for admin approval")
            }
        }
    }

    fun archivePatient(patient: Patient) {
        viewModelScope.launch {
            patientRepo.archivePatient(patient.id)
            auditRepo.addLog(AuditLogEntry(
                action = "Patient Archived",
                performedById = SessionManager.getCurrentStaffId(),
                performedByName = SessionManager.getCurrentStaffName(),
                targetPatientId = patient.id,
                targetPatientName = patient.name,
                details = "Patient record archived",
                iconName = "archive",
            ))
        }
    }

    fun unarchivePatient(patient: Patient) {
        viewModelScope.launch {
            patientRepo.unarchivePatient(patient.id)
            auditRepo.addLog(AuditLogEntry(
                action = "Patient Unarchived",
                performedById = SessionManager.getCurrentStaffId(),
                performedByName = SessionManager.getCurrentStaffName(),
                targetPatientId = patient.id,
                targetPatientName = patient.name,
                details = "Patient record restored from archive",
                iconName = "person",
            ))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Vitals
// ─────────────────────────────────────────────────────────────────────────────

class VitalsViewModel(
    private val repo: VitalsRepository,
    private val approvalRepo: ApprovalRepository,
    private val auditRepo: AuditRepository,
    private val notificationRepo: NotificationRepository,
    private val patientRepo: PatientRepository,
) : ViewModel() {
    private val _vitals = MutableStateFlow<List<VitalRecord>>(emptyList())
    val vitals: StateFlow<List<VitalRecord>> = _vitals.asStateFlow()

    fun load(patientId: String) { viewModelScope.launch { _vitals.value = repo.getVitalsForPatient(patientId) } }
    fun addVital(record: VitalRecord) {
        viewModelScope.launch { repo.addVital(record); _vitals.value = repo.getVitalsForPatient(record.patientId) }
    }

    // Edits within 24h of the original entry apply directly for every role (mistakes
    // happen, all changes are logged); after 24h, non-admin edits go through approval.
    fun updateVital(original: VitalRecord, updated: VitalRecord, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val withinGraceWindow = LocalDateTime.of(original.date, original.time).plusHours(24).isAfter(LocalDateTime.now())
            if (SessionManager.isAdmin() || withinGraceWindow) {
                repo.updateVital(updated)
                auditRepo.addLog(AuditLogEntry(
                    action = "Vitals Record Updated",
                    performedById = SessionManager.getCurrentStaffId(),
                    performedByName = SessionManager.getCurrentStaffName(),
                    targetPatientId = updated.patientId,
                    targetPatientName = patientRepo.getPatientById(updated.patientId)?.name ?: "",
                    details = if (SessionManager.isAdmin()) "Vitals record updated directly by Admin"
                              else "Vitals record edited within 24h of entry",
                    iconName = "edit",
                ))
                _vitals.value = repo.getVitalsForPatient(updated.patientId)
                onResult(true, "Vitals updated")
                return@launch
            }
            val changes = mutableListOf<Pair<String, Pair<String, String>>>()
            if (original.pulse != updated.pulse) changes.add("Pulse" to (original.pulse to updated.pulse))
            if (original.bp != updated.bp) changes.add("BP" to (original.bp to updated.bp))
            if (original.spo2 != updated.spo2) changes.add("SpO2" to (original.spo2 to updated.spo2))
            if (original.temperature != updated.temperature) changes.add("Temperature" to (original.temperature to updated.temperature))
            if (original.sugarFasting != updated.sugarFasting) changes.add("Sugar (Fasting)" to (original.sugarFasting to updated.sugarFasting))
            if (original.sugarPP != updated.sugarPP) changes.add("Sugar (PP)" to (original.sugarPP to updated.sugarPP))
            if (changes.isEmpty()) { onResult(false, "No changes detected"); return@launch }
            val patientName = patientRepo.getPatientById(original.patientId)?.name ?: ""
            changes.forEach { (field, vals) ->
                approvalRepo.submitRequest(ApprovalRequest(
                    entityType = ApprovalEntityType.VITAL,
                    entityId = original.id,
                    patientId = original.patientId,
                    patientName = patientName,
                    requestedById = SessionManager.getCurrentStaffId(),
                    requestedByName = SessionManager.getCurrentStaffName(),
                    fieldChanged = field,
                    oldValue = vals.first,
                    newValue = vals.second,
                ))
            }
            notificationRepo.add(AppNotification(
                recipientRole = UserRole.SUPER_ADMIN,
                type = NotificationType.APPROVAL_REQUESTED,
                title = "New Edit Request",
                message = "${SessionManager.getCurrentStaffName()} requested ${changes.size} change(s) to a vitals record for $patientName",
                targetRoute = "approval",
            ))
            onResult(true, "${changes.size} edit request(s) submitted for admin approval (entry is more than 24h old)")
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MAR (Medication)
// ─────────────────────────────────────────────────────────────────────────────

class MarViewModel(
    private val repo: MedicationRepository,
    private val allotmentRequestRepo: AllotmentRequestRepository,
    private val patientRepo: PatientRepository,
    private val notificationRepo: NotificationRepository,
) : ViewModel() {
    private val _medications  = MutableStateFlow<List<MedicationEntry>>(emptyList())
    val medications: StateFlow<List<MedicationEntry>> = _medications.asStateFlow()
    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    fun load(patientId: String, date: LocalDate = LocalDate.now()) {
        _selectedDate.value = date
        viewModelScope.launch { _medications.value = repo.getMedicationsForPatient(patientId, date) }
    }

    fun markAdministered(id: String, photoUrl: String, photoExpiresAt: LocalDateTime) {
        viewModelScope.launch {
            repo.markAdministered(id, SessionManager.getCurrentStaffName(), photoUrl, photoExpiresAt)
            val patientId = _medications.value.firstOrNull { it.id == id }?.patientId
            if (patientId != null) load(patientId, _selectedDate.value)
        }
    }

    fun requestAllotment(entry: MedicationEntry) {
        viewModelScope.launch {
            if (entry.allotmentStatus == AllotmentStatus.ALLOTTED) return@launch
            // Don't duplicate an already-pending request for the same entry
            if (allotmentRequestRepo.getByMedicationEntryId(entry.id) != null) return@launch
            val patientName = patientRepo.getPatientById(entry.patientId)?.name ?: ""
            allotmentRequestRepo.submitRequest(AllotmentRequest(
                medicationEntryId = entry.id,
                patientId = entry.patientId,
                patientName = patientName,
                medicineName = entry.medicineName,
                dose = entry.dose,
                scheduledTime = entry.scheduleTime,
                requestedById = SessionManager.getCurrentStaffId(),
                requestedByName = SessionManager.getCurrentStaffName(),
            ))
            notificationRepo.add(AppNotification(
                recipientRole = UserRole.SUPERVISOR,
                type = NotificationType.ALLOTMENT_REQUESTED,
                title = "Allotment Needed",
                message = "${SessionManager.getCurrentStaffName()} flagged ${entry.medicineName} for $patientName as not yet allotted",
                targetRoute = "medicine",
            ))
        }
    }

    fun addMedication(entry: MedicationEntry, onResult: (warning: String?) -> Unit = {}) {
        if (!SessionManager.isAdmin()) return
        viewModelScope.launch {
            val admissionDate = patientRepo.getPatientById(entry.patientId)?.admissionDate
            val warning = if (admissionDate != null && entry.scheduledDate.isBefore(admissionDate))
                "Warning: this dose is scheduled before the patient's admission date ($admissionDate)"
            else null
            repo.addMedication(entry)
            load(entry.patientId, entry.scheduledDate)
            onResult(warning)
        }
    }

    // Edit existing medication entry (SuperAdmin only)
    fun updateMedication(entry: MedicationEntry) {
        if (!SessionManager.isAdmin()) return
        viewModelScope.launch { repo.updateMedication(entry); load(entry.patientId, entry.scheduledDate) }
    }

    // Add/edit/delete of MAR entries is SuperAdmin-only (enforced in the UI too,
    // but the ViewModel double-checks — same defense-in-depth pattern as every
    // other privileged mutation in this file).
    fun deleteMedication(entry: MedicationEntry) {
        if (!SessionManager.isAdmin()) return
        viewModelScope.launch { repo.deleteMedication(entry.id); load(entry.patientId, entry.scheduledDate) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Medicine (supervisor allotment rounds)
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
    private val notificationRepo: NotificationRepository,
) : ViewModel() {
    private val _dueForAllotment = MutableStateFlow<List<MedicineRoundItem>>(emptyList())
    val dueForAllotment: StateFlow<List<MedicineRoundItem>> = _dueForAllotment.asStateFlow()
    private val _pendingRequests = MutableStateFlow<List<AllotmentRequest>>(emptyList())
    val pendingRequests: StateFlow<List<AllotmentRequest>> = _pendingRequests.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
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
    }

    fun allot(entry: MedicationEntry, photoUrl: String, photoExpiresAt: LocalDateTime) {
        viewModelScope.launch { allotWithoutReload(entry, photoUrl, photoExpiresAt); load() }
    }

    // fulfillRequest takes the request and looks up the entry itself so it never
    // silently fails when the entry isn't in dueForAllotment (e.g. already allotted)
    fun fulfillRequest(request: AllotmentRequest, photoUrl: String, photoExpiresAt: LocalDateTime) {
        viewModelScope.launch {
            val entry = medRepo.getMedicationById(request.medicationEntryId)
            if (entry != null && entry.allotmentStatus == AllotmentStatus.NOT_ALLOTTED) {
                allotWithoutReload(entry, photoUrl, photoExpiresAt)
            }
            allotmentRequestRepo.fulfillRequest(
                request.id,
                SessionManager.getCurrentStaffId(),
                SessionManager.getCurrentStaffName()
            )
            notificationRepo.add(AppNotification(
                recipientStaffId = request.requestedById,
                type = NotificationType.ALLOTMENT_FULFILLED,
                title = "Allotment Done",
                message = "${SessionManager.getCurrentStaffName()} allotted ${request.medicineName} for ${request.patientName}",
                targetRoute = "patient/${request.patientId}",
            ))
            load()
        }
    }

    private suspend fun allotWithoutReload(entry: MedicationEntry, photoUrl: String, photoExpiresAt: LocalDateTime) {
        medRepo.allotMedication(
            entry.id,
            SessionManager.getCurrentStaffId(),
            SessionManager.getCurrentStaffName(),
            photoUrl, photoExpiresAt,
        )
        auditRepo.addLog(AuditLogEntry(
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
// Notifications
// ─────────────────────────────────────────────────────────────────────────────

class NotificationViewModel(private val repo: NotificationRepository) : ViewModel() {
    private val _notifications = MutableStateFlow<List<AppNotification>>(emptyList())
    val notifications: StateFlow<List<AppNotification>> = _notifications.asStateFlow()
    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()
    init { load() }
    fun load() {
        val staff = SessionManager.getCurrentStaff() ?: return
        viewModelScope.launch {
            _notifications.value = repo.getForRecipient(staff.id, staff.role)
            _unreadCount.value = repo.getUnreadCountForRecipient(staff.id, staff.role)
        }
    }
    fun markRead(id: String) { viewModelScope.launch { repo.markRead(id); load() } }
    fun markAllRead() {
        val staff = SessionManager.getCurrentStaff() ?: return
        viewModelScope.launch { repo.markAllReadForRecipient(staff.id, staff.role); load() }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Utility
// ─────────────────────────────────────────────────────────────────────────────

class UtilityViewModel(
    private val repo: UtilityRepository,
    private val approvalRepo: ApprovalRepository,
    private val auditRepo: AuditRepository,
    private val notificationRepo: NotificationRepository,
    private val patientRepo: PatientRepository,
) : ViewModel() {
    private val _records = MutableStateFlow<List<UtilityRecord>>(emptyList())
    val records: StateFlow<List<UtilityRecord>> = _records.asStateFlow()
    private val _items = MutableStateFlow<List<UtilityItem>>(emptyList())
    val items: StateFlow<List<UtilityItem>> = _items.asStateFlow()
    // Includes deactivated items too, so the table can still show a column for
    // historical quantities logged against an item type that's since been removed.
    private val _allItems = MutableStateFlow<List<UtilityItem>>(emptyList())
    val allItems: StateFlow<List<UtilityItem>> = _allItems.asStateFlow()
    fun load(patientId: String) {
        viewModelScope.launch {
            _records.value = repo.getUtilityForPatient(patientId)
            _items.value = repo.getUtilityItems()
            _allItems.value = repo.getAllUtilityItems()
        }
    }
    fun addRecord(record: UtilityRecord) {
        viewModelScope.launch { repo.addUtilityRecord(record); load(record.patientId) }
    }

    // Same 24h-grace-then-approval policy as Vitals.
    fun updateRecord(original: UtilityRecord, updated: UtilityRecord, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val withinGraceWindow = LocalDateTime.of(original.date, original.time).plusHours(24).isAfter(LocalDateTime.now())
            if (SessionManager.isAdmin() || withinGraceWindow) {
                repo.updateUtilityRecord(updated)
                auditRepo.addLog(AuditLogEntry(
                    action = "Utility Record Updated",
                    performedById = SessionManager.getCurrentStaffId(),
                    performedByName = SessionManager.getCurrentStaffName(),
                    targetPatientId = updated.patientId,
                    targetPatientName = patientRepo.getPatientById(updated.patientId)?.name ?: "",
                    details = if (SessionManager.isAdmin()) "Utility record updated directly by Admin"
                              else "Utility record edited within 24h of entry",
                    iconName = "edit",
                ))
                load(updated.patientId)
                onResult(true, "Utility record updated")
                return@launch
            }
            val changes = mutableListOf<Pair<String, Pair<String, String>>>()
            if (original.issuedToCaregiver != updated.issuedToCaregiver) changes.add("Issued To" to (original.issuedToCaregiver to updated.issuedToCaregiver))
            if (original.issuedBySupervisor != updated.issuedBySupervisor) changes.add("Issued By" to (original.issuedBySupervisor to updated.issuedBySupervisor))
            if (original.checkedBy != updated.checkedBy) changes.add("Checked By" to (original.checkedBy to updated.checkedBy))
            if (changes.isEmpty()) { onResult(false, "No changes detected"); return@launch }
            val patientName = patientRepo.getPatientById(original.patientId)?.name ?: ""
            changes.forEach { (field, vals) ->
                approvalRepo.submitRequest(ApprovalRequest(
                    entityType = ApprovalEntityType.UTILITY,
                    entityId = original.id,
                    patientId = original.patientId,
                    patientName = patientName,
                    requestedById = SessionManager.getCurrentStaffId(),
                    requestedByName = SessionManager.getCurrentStaffName(),
                    fieldChanged = field,
                    oldValue = vals.first,
                    newValue = vals.second,
                ))
            }
            notificationRepo.add(AppNotification(
                recipientRole = UserRole.SUPER_ADMIN,
                type = NotificationType.APPROVAL_REQUESTED,
                title = "New Edit Request",
                message = "${SessionManager.getCurrentStaffName()} requested ${changes.size} change(s) to a utility record for $patientName",
                targetRoute = "approval",
            ))
            onResult(true, "${changes.size} edit request(s) submitted for admin approval (entry is more than 24h old)")
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Doctor Visits
// ─────────────────────────────────────────────────────────────────────────────

class DoctorVisitViewModel(
    private val repo: DoctorVisitRepository,
    private val approvalRepo: ApprovalRepository,
    private val auditRepo: AuditRepository,
    private val notificationRepo: NotificationRepository,
    private val patientRepo: PatientRepository,
) : ViewModel() {
    private val _visits = MutableStateFlow<List<DoctorVisit>>(emptyList())
    val visits: StateFlow<List<DoctorVisit>> = _visits.asStateFlow()
    fun load(patientId: String) { viewModelScope.launch { _visits.value = repo.getVisitsForPatient(patientId) } }
    fun addVisit(visit: DoctorVisit) {
        viewModelScope.launch { repo.addVisit(visit); load(visit.patientId) }
    }

    // Non-admin edits to a visit go through approval, same as Patient edits
    fun updateVisit(original: DoctorVisit, updated: DoctorVisit, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            if (SessionManager.isAdmin()) {
                repo.updateVisit(updated)
                auditRepo.addLog(AuditLogEntry(
                    action = "Doctor Visit Updated",
                    performedById = SessionManager.getCurrentStaffId(),
                    performedByName = SessionManager.getCurrentStaffName(),
                    targetPatientId = updated.patientId,
                    targetPatientName = patientRepo.getPatientById(updated.patientId)?.name ?: "",
                    details = "Doctor visit record updated directly by Admin",
                    iconName = "edit",
                ))
                load(updated.patientId)
                onResult(true, "Visit updated successfully")
            } else {
                val changes = mutableListOf<Pair<String, Pair<String, String>>>()
                if (original.doctorName != updated.doctorName) changes.add("Doctor Name" to (original.doctorName to updated.doctorName))
                if (original.specialty != updated.specialty) changes.add("Specialty" to (original.specialty to updated.specialty))
                if (original.date != updated.date) changes.add("Visit Date" to (original.date.toString() to updated.date.toString()))
                if (original.time != updated.time) changes.add("Visit Time" to (original.time.toString() to updated.time.toString()))
                if (original.notes != updated.notes) changes.add("Notes" to (original.notes to updated.notes))
                if (original.nextVisitDate != updated.nextVisitDate) changes.add("Next Visit Date" to ((original.nextVisitDate?.toString() ?: "") to (updated.nextVisitDate?.toString() ?: "")))
                if (original.prescriptionChanges != updated.prescriptionChanges) changes.add("Prescription Changes" to (original.prescriptionChanges to updated.prescriptionChanges))
                if (changes.isEmpty()) { onResult(false, "No changes detected"); return@launch }
                val patientName = patientRepo.getPatientById(original.patientId)?.name ?: ""
                changes.forEach { (field, vals) ->
                    approvalRepo.submitRequest(ApprovalRequest(
                        entityType = ApprovalEntityType.DOCTOR_VISIT,
                        entityId = original.id,
                        patientId = original.patientId,
                        patientName = patientName,
                        requestedById = SessionManager.getCurrentStaffId(),
                        requestedByName = SessionManager.getCurrentStaffName(),
                        fieldChanged = field,
                        oldValue = vals.first,
                        newValue = vals.second,
                    ))
                }
                notificationRepo.add(AppNotification(
                    recipientRole = UserRole.SUPER_ADMIN,
                    type = NotificationType.APPROVAL_REQUESTED,
                    title = "New Edit Request",
                    message = "${SessionManager.getCurrentStaffName()} requested ${changes.size} change(s) to a doctor visit for $patientName",
                    targetRoute = "approval",
                ))
                onResult(true, "${changes.size} edit request(s) submitted for admin approval")
            }
        }
    }

    fun confirmVisit(visit: DoctorVisit) {
        viewModelScope.launch {
            // Mark confirmed + archive if date has passed
            val shouldArchive = !visit.date.isAfter(LocalDate.now())
            repo.updateVisit(visit.copy(isConfirmed = true, isArchived = shouldArchive))
            auditRepo.addLog(AuditLogEntry(
                action = "Doctor Visit Confirmed",
                performedById = SessionManager.getCurrentStaffId(),
                performedByName = SessionManager.getCurrentStaffName(),
                targetPatientId = visit.patientId,
                targetPatientName = patientRepo.getPatientById(visit.patientId)?.name ?: "",
                details = "Confirmed visit with ${visit.doctorName} on ${visit.date}",
                iconName = "check_circle",
            ))
            load(visit.patientId)
        }
    }

    // Delete always goes through approval for non-admin; SuperAdmin deletes directly.
    fun requestDelete(visit: DoctorVisit, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val patientName = patientRepo.getPatientById(visit.patientId)?.name ?: ""
            if (SessionManager.isAdmin()) {
                repo.deleteVisit(visit.id)
                auditRepo.addLog(AuditLogEntry(
                    action = "Doctor Visit Deleted",
                    performedById = SessionManager.getCurrentStaffId(),
                    performedByName = SessionManager.getCurrentStaffName(),
                    targetPatientId = visit.patientId,
                    targetPatientName = patientName,
                    details = "Doctor visit with ${visit.doctorName} deleted directly by Admin",
                    iconName = "delete",
                ))
                load(visit.patientId)
                onResult(true, "Visit deleted")
            } else {
                approvalRepo.submitRequest(ApprovalRequest(
                    entityType = ApprovalEntityType.DOCTOR_VISIT,
                    entityId = visit.id,
                    action = ApprovalAction.DELETE,
                    patientId = visit.patientId,
                    patientName = patientName,
                    requestedById = SessionManager.getCurrentStaffId(),
                    requestedByName = SessionManager.getCurrentStaffName(),
                    fieldChanged = "Doctor Visit",
                    oldValue = "${visit.doctorName} — ${visit.date}",
                    newValue = "",
                ))
                notificationRepo.add(AppNotification(
                    recipientRole = UserRole.SUPER_ADMIN,
                    type = NotificationType.APPROVAL_REQUESTED,
                    title = "New Delete Request",
                    message = "${SessionManager.getCurrentStaffName()} requested to delete a doctor visit for $patientName",
                    targetRoute = "approval",
                ))
                onResult(true, "Delete request submitted for admin approval")
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Care Notes
// ─────────────────────────────────────────────────────────────────────────────

class CareNoteViewModel(
    private val repo: CareNoteRepository,
    private val approvalRepo: ApprovalRepository,
    private val auditRepo: AuditRepository,
    private val notificationRepo: NotificationRepository,
    private val patientRepo: PatientRepository,
) : ViewModel() {
    private val _notes = MutableStateFlow<List<CareNote>>(emptyList())
    val notes: StateFlow<List<CareNote>> = _notes.asStateFlow()
    fun load(patientId: String) { viewModelScope.launch { _notes.value = repo.getNotesForPatient(patientId) } }
    fun addNote(patientId: String, noteText: String) {
        viewModelScope.launch {
            repo.addNote(CareNote(
                patientId = patientId,
                staffId   = SessionManager.getCurrentStaffId(),
                staffName = SessionManager.getCurrentStaffName(),
                note      = noteText
            ))
            load(patientId)
        }
    }

    // Same 24h-grace-then-approval policy as Vitals/Utility.
    fun updateNote(original: CareNote, newText: String, onResult: (Boolean, String) -> Unit) {
        if (original.note == newText) { onResult(false, "No changes detected"); return }
        viewModelScope.launch {
            val withinGraceWindow = original.timestamp.plusHours(24).isAfter(LocalDateTime.now())
            if (SessionManager.isAdmin() || withinGraceWindow) {
                repo.updateNote(original.copy(note = newText))
                auditRepo.addLog(AuditLogEntry(
                    action = "Care Note Updated",
                    performedById = SessionManager.getCurrentStaffId(),
                    performedByName = SessionManager.getCurrentStaffName(),
                    targetPatientId = original.patientId,
                    targetPatientName = patientRepo.getPatientById(original.patientId)?.name ?: "",
                    details = if (SessionManager.isAdmin()) "Care note updated directly by Admin"
                              else "Care note edited within 24h of entry",
                    iconName = "edit",
                ))
                load(original.patientId)
                onResult(true, "Note updated")
                return@launch
            }
            val patientName = patientRepo.getPatientById(original.patientId)?.name ?: ""
            approvalRepo.submitRequest(ApprovalRequest(
                entityType = ApprovalEntityType.CARE_NOTE,
                entityId = original.id,
                patientId = original.patientId,
                patientName = patientName,
                requestedById = SessionManager.getCurrentStaffId(),
                requestedByName = SessionManager.getCurrentStaffName(),
                fieldChanged = "Care Note",
                oldValue = original.note,
                newValue = newText,
            ))
            notificationRepo.add(AppNotification(
                recipientRole = UserRole.SUPER_ADMIN,
                type = NotificationType.APPROVAL_REQUESTED,
                title = "New Edit Request",
                message = "${SessionManager.getCurrentStaffName()} requested a change to a care note for $patientName",
                targetRoute = "approval",
            ))
            onResult(true, "Edit request submitted for admin approval (entry is more than 24h old)")
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Approval Queue
// ─────────────────────────────────────────────────────────────────────────────

class ApprovalViewModel(
    private val repo: ApprovalRepository,
    private val patientRepo: PatientRepository,
    private val auditRepo: AuditRepository,
    private val notificationRepo: NotificationRepository,
    private val doctorVisitRepo: DoctorVisitRepository,
    private val vitalsRepo: VitalsRepository,
    private val utilityRepo: UtilityRepository,
    private val careNoteRepo: CareNoteRepository,
) : ViewModel() {
    private val _requests = MutableStateFlow<List<ApprovalRequest>>(emptyList())
    val requests: StateFlow<List<ApprovalRequest>> = _requests.asStateFlow()
    init { load() }
    fun load() { viewModelScope.launch { _requests.value = repo.getAllRequests() } }

    /**
     * Reads the field's *current* value straight from the live record, so [approve] can
     * tell whether the request is still safe to apply. Returns null if the underlying
     * record (patient, visit, vital, utility record, or note) no longer exists.
     */
    private suspend fun currentFieldValue(request: ApprovalRequest): String? = when (request.entityType) {
        ApprovalEntityType.PATIENT -> patientRepo.getPatientById(request.patientId)?.let { p ->
            when (request.fieldChanged) {
                "Name"              -> p.name
                "Age"               -> p.age.toString()
                "Gender"            -> p.gender.name
                "Room No"           -> p.roomNo
                "Medical History"   -> p.medicalHistory
                "Current Issues"    -> p.currentIssues
                "Allergies"         -> p.allergies
                "Emergency Contact" -> p.emergencyContact
                "Emergency Phone"   -> p.emergencyPhone
                "Primary Diagnosis" -> p.primaryDiagnosis
                "Admission Date"    -> p.admissionDate.toString()
                else                -> request.oldValue
            }
        }
        ApprovalEntityType.DOCTOR_VISIT -> doctorVisitRepo.getVisitById(request.entityId)?.let { v ->
            if (request.action == ApprovalAction.DELETE) request.oldValue else when (request.fieldChanged) {
                "Doctor Name"          -> v.doctorName
                "Specialty"            -> v.specialty
                "Visit Date"           -> v.date.toString()
                "Visit Time"           -> v.time.toString()
                "Notes"                -> v.notes
                "Next Visit Date"      -> v.nextVisitDate?.toString() ?: ""
                "Prescription Changes" -> v.prescriptionChanges
                else                   -> request.oldValue
            }
        }
        ApprovalEntityType.VITAL -> vitalsRepo.getVitalById(request.entityId)?.let { v ->
            when (request.fieldChanged) {
                "Pulse"           -> v.pulse
                "BP"              -> v.bp
                "SpO2"            -> v.spo2
                "Temperature"     -> v.temperature
                "Sugar (Fasting)" -> v.sugarFasting
                "Sugar (PP)"      -> v.sugarPP
                else              -> request.oldValue
            }
        }
        ApprovalEntityType.UTILITY -> utilityRepo.getUtilityRecordById(request.entityId)?.let { u ->
            when (request.fieldChanged) {
                "Issued To"  -> u.issuedToCaregiver
                "Issued By"  -> u.issuedBySupervisor
                "Checked By" -> u.checkedBy
                else         -> request.oldValue
            }
        }
        ApprovalEntityType.CARE_NOTE -> careNoteRepo.getNoteById(request.entityId)?.note
    }

    fun approve(id: String) {
        viewModelScope.launch {
            val request = repo.getRequestById(id) ?: return@launch

            // The record may have been deleted, or edited again, by someone else while this
            // request sat pending — applying it blindly would either no-op (but still claim
            // "approved") or silently clobber newer data. Check the live value first.
            val liveValue = currentFieldValue(request)
            if (liveValue == null) {
                rejectStale(request, "the record this change targeted no longer exists")
                return@launch
            }
            if (request.action != ApprovalAction.DELETE && liveValue != request.oldValue) {
                rejectStale(request, "the record changed since this request was submitted")
                return@launch
            }

            repo.approve(id, SessionManager.getCurrentStaffId(), SessionManager.getCurrentStaffName())
            when (request.entityType) {
                ApprovalEntityType.PATIENT -> {
                    val patient = patientRepo.getPatientById(request.patientId)
                    if (patient != null) patientRepo.updatePatient(applyFieldChange(patient, request.fieldChanged, request.newValue))
                }
                ApprovalEntityType.DOCTOR_VISIT -> {
                    if (request.action == ApprovalAction.DELETE) {
                        doctorVisitRepo.deleteVisit(request.entityId)
                    } else {
                        val visit = doctorVisitRepo.getVisitById(request.entityId)
                        if (visit != null) doctorVisitRepo.updateVisit(applyDoctorVisitFieldChange(visit, request.fieldChanged, request.newValue))
                    }
                }
                ApprovalEntityType.VITAL -> {
                    val vital = vitalsRepo.getVitalById(request.entityId)
                    if (vital != null) vitalsRepo.updateVital(applyVitalFieldChange(vital, request.fieldChanged, request.newValue))
                }
                ApprovalEntityType.UTILITY -> {
                    val record = utilityRepo.getUtilityRecordById(request.entityId)
                    if (record != null) utilityRepo.updateUtilityRecord(applyUtilityFieldChange(record, request.fieldChanged, request.newValue))
                }
                ApprovalEntityType.CARE_NOTE -> {
                    val note = careNoteRepo.getNoteById(request.entityId)
                    if (note != null) careNoteRepo.updateNote(note.copy(note = request.newValue))
                }
            }
            auditRepo.addLog(AuditLogEntry(
                action = "Edit Request Approved",
                performedById = SessionManager.getCurrentStaffId(),
                performedByName = SessionManager.getCurrentStaffName(),
                targetPatientId = request.patientId,
                targetPatientName = request.patientName,
                details = "Approved change to ${request.fieldChanged} requested by ${request.requestedByName}",
                iconName = "check_circle",
            ))
            notificationRepo.add(AppNotification(
                recipientStaffId = request.requestedById,
                type = NotificationType.APPROVAL_APPROVED,
                title = "Edit Request Approved",
                message = "Your ${request.fieldChanged} change for ${request.patientName} was approved",
                targetRoute = "patient/${request.patientId}",
            ))
            load()
        }
    }

    /** Auto-rejects a request that can no longer be safely applied, with a system reason. */
    private suspend fun rejectStale(request: ApprovalRequest, reason: String) {
        repo.reject(request.id, SessionManager.getCurrentStaffId(), SessionManager.getCurrentStaffName(), reason)
        auditRepo.addLog(AuditLogEntry(
            action = "Edit Request Auto-Rejected",
            performedById = SessionManager.getCurrentStaffId(),
            performedByName = SessionManager.getCurrentStaffName(),
            targetPatientId = request.patientId,
            targetPatientName = request.patientName,
            details = "Could not approve change to ${request.fieldChanged} — $reason",
            iconName = "cancel",
        ))
        notificationRepo.add(AppNotification(
            recipientStaffId = request.requestedById,
            type = NotificationType.APPROVAL_REJECTED,
            title = "Edit Request Could Not Be Applied",
            message = "Your ${request.fieldChanged} change for ${request.patientName} could not be applied — $reason",
            targetRoute = "patient/${request.patientId}",
        ))
        load()
    }

    fun reject(id: String, reason: String) {
        viewModelScope.launch {
            val request = repo.getRequestById(id) ?: return@launch
            repo.reject(id, SessionManager.getCurrentStaffId(), SessionManager.getCurrentStaffName(), reason)
            auditRepo.addLog(AuditLogEntry(
                action = "Edit Request Rejected",
                performedById = SessionManager.getCurrentStaffId(),
                performedByName = SessionManager.getCurrentStaffName(),
                targetPatientId = request.patientId,
                targetPatientName = request.patientName,
                details = "Rejected change to ${request.fieldChanged} — $reason",
                iconName = "cancel",
            ))
            notificationRepo.add(AppNotification(
                recipientStaffId = request.requestedById,
                type = NotificationType.APPROVAL_REJECTED,
                title = "Edit Request Rejected",
                message = "Your ${request.fieldChanged} change for ${request.patientName} was rejected — $reason",
                targetRoute = "patient/${request.patientId}",
            ))
            load()
        }
    }

    private fun applyFieldChange(patient: Patient, field: String, newValue: String): Patient = when (field) {
        "Name"              -> patient.copy(name = newValue)
        "Age"               -> patient.copy(age = newValue.toIntOrNull() ?: patient.age)
        "Gender"            -> patient.copy(gender = runCatching { Gender.valueOf(newValue) }.getOrDefault(patient.gender))
        "Room No"           -> patient.copy(roomNo = newValue)
        "Medical History"   -> patient.copy(medicalHistory = newValue)
        "Current Issues"    -> patient.copy(currentIssues = newValue)
        "Allergies"         -> patient.copy(allergies = newValue)
        "Emergency Contact" -> patient.copy(emergencyContact = newValue)
        "Emergency Phone"   -> patient.copy(emergencyPhone = newValue)
        "Primary Diagnosis" -> patient.copy(primaryDiagnosis = newValue)
        "Admission Date"    -> patient.copy(admissionDate = runCatching { LocalDate.parse(newValue) }.getOrDefault(patient.admissionDate))
        else                -> patient
    }

    private fun applyDoctorVisitFieldChange(visit: DoctorVisit, field: String, newValue: String): DoctorVisit = when (field) {
        "Doctor Name"           -> visit.copy(doctorName = newValue)
        "Specialty"             -> visit.copy(specialty = newValue)
        "Visit Date"            -> visit.copy(date = runCatching { LocalDate.parse(newValue) }.getOrDefault(visit.date))
        "Visit Time"            -> visit.copy(time = runCatching { java.time.LocalTime.parse(newValue) }.getOrDefault(visit.time))
        "Notes"                 -> visit.copy(notes = newValue)
        "Next Visit Date"       -> visit.copy(nextVisitDate = newValue.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() })
        "Prescription Changes"  -> visit.copy(prescriptionChanges = newValue)
        else                    -> visit
    }

    private fun applyVitalFieldChange(record: VitalRecord, field: String, newValue: String): VitalRecord = when (field) {
        "Pulse"           -> record.copy(pulse = newValue)
        "BP"              -> record.copy(bp = newValue)
        "SpO2"            -> record.copy(spo2 = newValue)
        "Temperature"     -> record.copy(temperature = newValue)
        "Sugar (Fasting)" -> record.copy(sugarFasting = newValue)
        "Sugar (PP)"      -> record.copy(sugarPP = newValue)
        else              -> record
    }

    private fun applyUtilityFieldChange(record: UtilityRecord, field: String, newValue: String): UtilityRecord = when (field) {
        "Issued To"  -> record.copy(issuedToCaregiver = newValue)
        "Issued By"  -> record.copy(issuedBySupervisor = newValue)
        "Checked By" -> record.copy(checkedBy = newValue)
        else         -> record
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Audit Log
// ─────────────────────────────────────────────────────────────────────────────

class AuditLogViewModel(private val repo: AuditRepository) : ViewModel() {
    private val _logs = MutableStateFlow<List<AuditLogEntry>>(emptyList())
    val logs: StateFlow<List<AuditLogEntry>> = _logs.asStateFlow()
    init { load() }
    fun load() { viewModelScope.launch { _logs.value = repo.getAllLogs() } }
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
    fun load() { viewModelScope.launch { refreshStaff(); refreshItems() } }
    private suspend fun refreshStaff() { _staffList.value = staffRepo.getAllStaff() }
    private suspend fun refreshItems() { _utilItems.value = utilityRepo.getAllUtilityItems() }

    /** [password] is assigned by the Super Admin right now, at creation time — there's no separate invite/setup step. */
    fun addStaff(name: String, email: String, phone: String, role: UserRole, password: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                staffRepo.addStaff(name, email, phone, role, password)
                refreshStaff()
                onResult(true, "Staff member added")
            } catch (e: DuplicateStaffNameException) {
                onResult(false, e.message ?: "That name is already taken")
            } catch (e: Exception) {
                onResult(false, "Could not add staff member: ${e.message}")
            }
        }
    }
    fun revokeStaff(id: String) { viewModelScope.launch { staffRepo.revokeStaff(id); refreshStaff() } }
    fun unrevokeStaff(id: String) { viewModelScope.launch { staffRepo.unrevokeStaff(id); refreshStaff() } }
    fun deleteStaff(id: String) { viewModelScope.launch { staffRepo.deleteStaff(id); refreshStaff() } }
    fun addUtilityItem(item: UtilityItem) { viewModelScope.launch { utilityRepo.addUtilityItem(item); refreshItems() } }
    fun deleteUtilityItem(id: String) { viewModelScope.launch { utilityRepo.deleteUtilityItem(id); refreshItems() } }
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

data class PatientRangeSummary(
    val patient: Patient,
    val vitals: List<VitalRecord>,
    val medications: List<MedicationEntry>,
    val utility: List<UtilityRecord>,
    val visits: List<DoctorVisit>,
    val notes: List<CareNote>,
)

class SummaryViewModel(
    private val medRepo: MedicationRepository,
    private val vitalsRepo: VitalsRepository,
    private val approvalRepo: ApprovalRepository,
    private val patientRepo: PatientRepository,
    private val utilityRepo: UtilityRepository,
    private val doctorVisitRepo: DoctorVisitRepository,
    private val careNoteRepo: CareNoteRepository,
) : ViewModel() {
    private val _stats       = MutableStateFlow(SummaryStats())
    val stats: StateFlow<SummaryStats> = _stats.asStateFlow()
    private val _startDate = MutableStateFlow(LocalDate.now())
    val startDate: StateFlow<LocalDate> = _startDate.asStateFlow()
    private val _endDate = MutableStateFlow(LocalDate.now())
    val endDate: StateFlow<LocalDate> = _endDate.asStateFlow()
    private val _patients    = MutableStateFlow<List<Patient>>(emptyList())
    val patients: StateFlow<List<Patient>> = _patients.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init { load(LocalDate.now(), LocalDate.now()) }

    fun load(start: LocalDate, end: LocalDate) {
        viewModelScope.launch {
            _isLoading.value = true
            val from = if (start.isAfter(end)) end else start
            val to   = if (start.isAfter(end)) start else end
            _startDate.value = from
            _endDate.value = to
            val pts = patientRepo.getAllPatients()
            _patients.value = pts
            val allMeds    = pts.flatMap { medRepo.getMedicationsForPatient(it.id).filter { m -> m.scheduledDate in from..to } }
            val allVitals  = pts.flatMap { vitalsRepo.getVitalsForPatient(it.id).filter { v -> v.date in from..to } }
            val allUtility = pts.sumOf { utilityRepo.getUtilityForPatient(it.id).count { u -> u.date in from..to } }
            _stats.value = SummaryStats(
                vitalsRecorded   = allVitals.size,
                medsAdministered = allMeds.count { it.status == MedStatus.ADMINISTERED },
                medsPending      = allMeds.count { it.status == MedStatus.PENDING || it.status == MedStatus.OVERDUE },
                utilityLogs      = allUtility,
                pendingApprovals = approvalRepo.getPendingRequests().size
            )
            _isLoading.value = false
        }
    }

    /** Full per-patient breakdown for the currently selected range, for the xlsx export. */
    suspend fun buildRangeReport(): List<PatientRangeSummary> {
        val from = _startDate.value
        val to = _endDate.value
        return _patients.value.map { p ->
            PatientRangeSummary(
                patient = p,
                vitals = vitalsRepo.getVitalsForPatient(p.id).filter { it.date in from..to },
                medications = medRepo.getMedicationsForPatient(p.id).filter { it.scheduledDate in from..to },
                utility = utilityRepo.getUtilityForPatient(p.id).filter { it.date in from..to },
                visits = doctorVisitRepo.getVisitsForPatient(p.id).filter { it.date in from..to },
                notes = careNoteRepo.getNotesForPatient(p.id).filter { it.timestamp.toLocalDate() in from..to },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Photo Audit — the only screen the restricted, photo-audit-only Admin role sees
// ─────────────────────────────────────────────────────────────────────────────

data class PhotoAuditEntry(
    val medicationEntryId: String,
    val patientName: String,
    val medicineName: String,
    val kind: String,       // "Allotment" or "Administration"
    val staffName: String,
    val photoUrl: String,
    val capturedAt: LocalDateTime,
    val expiresAt: LocalDateTime,
)

class PhotoAuditViewModel(
    private val medRepo: MedicationRepository,
    private val patientRepo: PatientRepository,
) : ViewModel() {
    private val _entries = MutableStateFlow<List<PhotoAuditEntry>>(emptyList())
    val entries: StateFlow<List<PhotoAuditEntry>> = _entries.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            val patients = patientRepo.getAllPatients(includeArchived = true).associateBy { it.id }
            val result = mutableListOf<PhotoAuditEntry>()
            medRepo.getAllMedications().forEach { entry ->
                val patientName = patients[entry.patientId]?.name ?: "Unknown"
                if (entry.allotmentPhotoUrl.isNotBlank()) {
                    result += PhotoAuditEntry(
                        entry.id, patientName, entry.medicineName, "Allotment",
                        entry.allottedByName, entry.allotmentPhotoUrl,
                        entry.allottedAt ?: LocalDateTime.now(), entry.allotmentPhotoExpiresAt ?: LocalDateTime.now()
                    )
                }
                if (entry.administeredPhotoUrl.isNotBlank()) {
                    result += PhotoAuditEntry(
                        entry.id, patientName, entry.medicineName, "Administration",
                        entry.administeredBy, entry.administeredPhotoUrl,
                        entry.administeredAt ?: LocalDateTime.now(), entry.administeredPhotoExpiresAt ?: LocalDateTime.now()
                    )
                }
            }
            _entries.value = result.sortedByDescending { it.capturedAt }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ViewModelFactory
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
    private val notificationRepo: NotificationRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(LoginViewModel::class.java)       -> LoginViewModel(authRepo) as T
        modelClass.isAssignableFrom(DashboardViewModel::class.java)   -> DashboardViewModel(patientRepo, medRepo, approvalRepo) as T
        modelClass.isAssignableFrom(PatientViewModel::class.java)     -> PatientViewModel(patientRepo, approvalRepo, auditRepo, notificationRepo) as T
        modelClass.isAssignableFrom(VitalsViewModel::class.java)      -> VitalsViewModel(vitalsRepo, approvalRepo, auditRepo, notificationRepo, patientRepo) as T
        modelClass.isAssignableFrom(MarViewModel::class.java)         -> MarViewModel(medRepo, allotmentRequestRepo, patientRepo, notificationRepo) as T
        modelClass.isAssignableFrom(MedicineViewModel::class.java)    -> MedicineViewModel(medRepo, patientRepo, allotmentRequestRepo, auditRepo, notificationRepo) as T
        modelClass.isAssignableFrom(UtilityViewModel::class.java)     -> UtilityViewModel(utilityRepo, approvalRepo, auditRepo, notificationRepo, patientRepo) as T
        modelClass.isAssignableFrom(DoctorVisitViewModel::class.java) -> DoctorVisitViewModel(doctorVisitRepo, approvalRepo, auditRepo, notificationRepo, patientRepo) as T
        modelClass.isAssignableFrom(CareNoteViewModel::class.java)    -> CareNoteViewModel(careNoteRepo, approvalRepo, auditRepo, notificationRepo, patientRepo) as T
        modelClass.isAssignableFrom(ApprovalViewModel::class.java)    -> ApprovalViewModel(approvalRepo, patientRepo, auditRepo, notificationRepo, doctorVisitRepo, vitalsRepo, utilityRepo, careNoteRepo) as T
        modelClass.isAssignableFrom(AuditLogViewModel::class.java)    -> AuditLogViewModel(auditRepo) as T
        modelClass.isAssignableFrom(ConfigViewModel::class.java)      -> ConfigViewModel(staffRepo, utilityRepo) as T
        modelClass.isAssignableFrom(SummaryViewModel::class.java)     -> SummaryViewModel(medRepo, vitalsRepo, approvalRepo, patientRepo, utilityRepo, doctorVisitRepo, careNoteRepo) as T
        modelClass.isAssignableFrom(NotificationViewModel::class.java)-> NotificationViewModel(notificationRepo) as T
        modelClass.isAssignableFrom(PhotoAuditViewModel::class.java)  -> PhotoAuditViewModel(medRepo, patientRepo) as T
        else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}
