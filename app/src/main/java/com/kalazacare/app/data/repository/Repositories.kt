package com.kalazacare.app.data.repository

import com.kalazacare.app.data.database.MockData
import com.kalazacare.app.data.model.*
import java.time.LocalDate
import java.time.LocalDateTime

// ─────────────────────────────────────────────────────────────────────────────
// Auth
// ─────────────────────────────────────────────────────────────────────────────

interface AuthRepository {
    fun login(name: String, password: String): Staff?
    fun logout()
    fun currentStaff(): Staff?
}

class MockAuthRepository : AuthRepository {
    private var loggedIn: Staff? = null
    override fun login(name: String, password: String): Staff? {
        val trimmedName = name.trim()
        loggedIn = MockData.staffList.firstOrNull { it.name.trim().equals(trimmedName, ignoreCase = true) && it.isActive }
        return loggedIn
    }
    override fun logout() { loggedIn = null }
    override fun currentStaff(): Staff? = loggedIn
}

// ─────────────────────────────────────────────────────────────────────────────
// Patients
// ─────────────────────────────────────────────────────────────────────────────

interface PatientRepository {
    fun getAllPatients(includeArchived: Boolean = false): List<Patient>
    fun getPatientById(id: String): Patient?
    fun addPatient(patient: Patient)
    fun updatePatient(patient: Patient)
    fun archivePatient(id: String)
    fun searchPatients(query: String, includeArchived: Boolean = false): List<Patient>
}

class MockPatientRepository : PatientRepository {
    private val patients = MockData.patientList.toMutableList()
    override fun getAllPatients(includeArchived: Boolean) =
        if (includeArchived) patients.toList() else patients.filter { !it.isArchived }
    override fun getPatientById(id: String) = patients.firstOrNull { it.id == id }
    override fun addPatient(patient: Patient) { patients.add(patient) }
    override fun updatePatient(patient: Patient) {
        val idx = patients.indexOfFirst { it.id == patient.id }
        if (idx >= 0) patients[idx] = patient
    }
    override fun archivePatient(id: String) {
        val idx = patients.indexOfFirst { it.id == id }
        if (idx >= 0) patients[idx] = patients[idx].copy(isArchived = true)
    }
    override fun searchPatients(query: String, includeArchived: Boolean) =
        patients.filter { (includeArchived || !it.isArchived) &&
            (it.name.contains(query, ignoreCase = true) || it.roomNo.contains(query, ignoreCase = true)) }
}

// ─────────────────────────────────────────────────────────────────────────────
// Vitals
// ─────────────────────────────────────────────────────────────────────────────

interface VitalsRepository {
    fun getVitalsForPatient(patientId: String): List<VitalRecord>
    fun getVitalsForDate(patientId: String, date: LocalDate): List<VitalRecord>
    fun addVital(record: VitalRecord)
    fun updateVital(record: VitalRecord)
}

class MockVitalsRepository : VitalsRepository {
    private val records = MockData.vitalRecords.toMutableList()
    override fun getVitalsForPatient(patientId: String) =
        records.filter { it.patientId == patientId }.sortedByDescending { it.date }
    override fun getVitalsForDate(patientId: String, date: LocalDate) =
        records.filter { it.patientId == patientId && it.date == date }
    override fun addVital(record: VitalRecord) { records.add(record) }
    override fun updateVital(record: VitalRecord) {
        val idx = records.indexOfFirst { it.id == record.id }
        if (idx >= 0) records[idx] = record
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Medication (MAR)
// ─────────────────────────────────────────────────────────────────────────────

interface MedicationRepository {
    fun getMedicationsForPatient(patientId: String, date: LocalDate): List<MedicationEntry>
    fun getMedicationsForPatient(patientId: String): List<MedicationEntry>
    fun getMedicationsForDate(date: LocalDate): List<MedicationEntry>
    fun getMedicationById(id: String): MedicationEntry?
    fun addMedication(entry: MedicationEntry)
    fun updateMedication(entry: MedicationEntry)
    fun markAdministered(id: String, staffName: String, photoUrl: String, photoExpiresAt: LocalDateTime)
    fun allotMedication(id: String, staffId: String, staffName: String, photoUrl: String, photoExpiresAt: LocalDateTime)
}

/**
 * PENDING/OVERDUE is a live-computed view of the schedule, not a persisted
 * fact — recomputed both ways so editing a dose's time (e.g. moving it
 * later) un-overdues it instead of leaving it stuck OVERDUE forever.
 * ADMINISTERED entries are left untouched regardless of schedule.
 */
private fun MedicationEntry.withComputedStatus(): MedicationEntry {
    if (status != MedStatus.PENDING && status != MedStatus.OVERDUE) return this
    val scheduledAt = java.time.LocalDateTime.of(scheduledDate, scheduleTime)
    val computed = if (scheduledAt.isBefore(LocalDateTime.now())) MedStatus.OVERDUE else MedStatus.PENDING
    return if (computed != status) copy(status = computed) else this
}

class MockMedicationRepository : MedicationRepository {
    private val entries = MockData.medicationEntries.toMutableList()
    override fun getMedicationsForPatient(patientId: String, date: LocalDate) =
        entries.filter { it.patientId == patientId && it.scheduledDate == date }
            .sortedBy { it.scheduleTime }.map { it.withComputedStatus() }
    override fun getMedicationsForPatient(patientId: String) =
        entries.filter { it.patientId == patientId }.sortedBy { it.scheduleTime }
            .map { it.withComputedStatus() }
    override fun getMedicationsForDate(date: LocalDate) =
        entries.filter { it.scheduledDate == date }.sortedBy { it.scheduleTime }
            .map { it.withComputedStatus() }
    override fun getMedicationById(id: String) =
        entries.firstOrNull { it.id == id }?.withComputedStatus()
    override fun addMedication(entry: MedicationEntry) { entries.add(entry) }
    override fun updateMedication(entry: MedicationEntry) {
        val idx = entries.indexOfFirst { it.id == entry.id }
        if (idx >= 0) entries[idx] = entry
    }
    override fun markAdministered(id: String, staffName: String, photoUrl: String, photoExpiresAt: LocalDateTime) {
        val idx = entries.indexOfFirst { it.id == id }
        if (idx >= 0) entries[idx] = entries[idx].copy(
            status = MedStatus.ADMINISTERED,
            administeredBy = staffName,
            administeredAt = LocalDateTime.now(),
            administeredPhotoUrl = photoUrl,
            administeredPhotoExpiresAt = photoExpiresAt,
        )
    }
    override fun allotMedication(id: String, staffId: String, staffName: String, photoUrl: String, photoExpiresAt: LocalDateTime) {
        val idx = entries.indexOfFirst { it.id == id }
        if (idx >= 0) entries[idx] = entries[idx].copy(
            allotmentStatus = AllotmentStatus.ALLOTTED,
            allottedById = staffId,
            allottedByName = staffName,
            allottedAt = LocalDateTime.now(),
            allotmentPhotoUrl = photoUrl,
            allotmentPhotoExpiresAt = photoExpiresAt,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Utility Records
// ─────────────────────────────────────────────────────────────────────────────

interface UtilityRepository {
    fun getUtilityForPatient(patientId: String): List<UtilityRecord>
    fun addUtilityRecord(record: UtilityRecord)
    fun updateUtilityRecord(record: UtilityRecord)
    fun getUtilityItems(): List<UtilityItem>
    fun addUtilityItem(item: UtilityItem)
    fun updateUtilityItem(item: UtilityItem)
    fun deleteUtilityItem(id: String)
}

class MockUtilityRepository : UtilityRepository {
    private val records = MockData.utilityRecords.toMutableList()
    private val items   = MockData.utilityItems.toMutableList()
    override fun getUtilityForPatient(patientId: String) =
        records.filter { it.patientId == patientId }.sortedByDescending { it.date }
    override fun addUtilityRecord(record: UtilityRecord) { records.add(record) }
    override fun updateUtilityRecord(record: UtilityRecord) {
        val idx = records.indexOfFirst { it.id == record.id }
        if (idx >= 0) records[idx] = record
    }
    override fun getUtilityItems() = items.filter { it.isActive }.sortedBy { it.displayOrder }
    override fun addUtilityItem(item: UtilityItem) { items.add(item) }
    override fun updateUtilityItem(item: UtilityItem) {
        val idx = items.indexOfFirst { it.id == item.id }
        if (idx >= 0) items[idx] = item
    }
    override fun deleteUtilityItem(id: String) {
        val idx = items.indexOfFirst { it.id == id }
        if (idx >= 0) items[idx] = items[idx].copy(isActive = false)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Doctor Visits & Care Notes
// ─────────────────────────────────────────────────────────────────────────────

interface DoctorVisitRepository {
    fun getVisitsForPatient(patientId: String): List<DoctorVisit>
    fun getVisitById(id: String): DoctorVisit?
    fun addVisit(visit: DoctorVisit)
    fun updateVisit(visit: DoctorVisit)  // CHANGE 4: edit + confirm + archive
}

class MockDoctorVisitRepository : DoctorVisitRepository {
    private val visits = MockData.doctorVisits.toMutableList()
    override fun getVisitsForPatient(patientId: String) =
        visits.filter { it.patientId == patientId }.sortedByDescending { it.date }
    override fun getVisitById(id: String) = visits.firstOrNull { it.id == id }
    override fun addVisit(visit: DoctorVisit) { visits.add(visit) }
    override fun updateVisit(visit: DoctorVisit) {
        val idx = visits.indexOfFirst { it.id == visit.id }
        if (idx >= 0) visits[idx] = visit
    }
}

interface CareNoteRepository {
    fun getNotesForPatient(patientId: String): List<CareNote>
    fun addNote(note: CareNote)
}

class MockCareNoteRepository : CareNoteRepository {
    private val notes = MockData.careNotes.toMutableList()
    override fun getNotesForPatient(patientId: String) =
        notes.filter { it.patientId == patientId }.sortedByDescending { it.timestamp }
    override fun addNote(note: CareNote) { notes.add(note) }
}

// ─────────────────────────────────────────────────────────────────────────────
// Approval & Audit
// ─────────────────────────────────────────────────────────────────────────────

interface ApprovalRepository {
    fun getAllRequests(): List<ApprovalRequest>
    fun getPendingRequests(): List<ApprovalRequest>
    fun getRequestById(id: String): ApprovalRequest?
    fun approve(id: String, reviewerId: String, reviewerName: String)
    fun reject(id: String, reviewerId: String, reviewerName: String, reason: String)
    fun submitRequest(request: ApprovalRequest)
}

class MockApprovalRepository : ApprovalRepository {
    private val requests = MockData.approvalRequests.toMutableList()
    override fun getAllRequests() = requests.sortedByDescending { it.timestamp }
    override fun getPendingRequests() = requests.filter { it.status == ApprovalStatus.PENDING }
    override fun getRequestById(id: String) = requests.firstOrNull { it.id == id }
    override fun approve(id: String, reviewerId: String, reviewerName: String) {
        val idx = requests.indexOfFirst { it.id == id }
        if (idx >= 0) requests[idx] = requests[idx].copy(
            status = ApprovalStatus.APPROVED,
            reviewedById = reviewerId,
            reviewedByName = reviewerName,
            reviewedAt = LocalDateTime.now()
        )
    }
    override fun reject(id: String, reviewerId: String, reviewerName: String, reason: String) {
        val idx = requests.indexOfFirst { it.id == id }
        if (idx >= 0) requests[idx] = requests[idx].copy(
            status = ApprovalStatus.REJECTED,
            reviewedById = reviewerId,
            reviewedByName = reviewerName,
            reviewedAt = LocalDateTime.now(),
            rejectionReason = reason
        )
    }
    override fun submitRequest(request: ApprovalRequest) { requests.add(request) }
}

// CHANGE 1: AllotmentRequestRepository - fixed fulfillRequest to lookup by medicationEntryId too
interface AllotmentRequestRepository {
    fun getAllRequests(): List<AllotmentRequest>
    fun getPendingRequests(): List<AllotmentRequest>
    fun submitRequest(request: AllotmentRequest)
    fun fulfillRequest(id: String, staffId: String, staffName: String)
    fun getByMedicationEntryId(medicationEntryId: String): AllotmentRequest?
}

class MockAllotmentRequestRepository : AllotmentRequestRepository {
    private val requests = MockData.allotmentRequests.toMutableList()
    override fun getAllRequests() = requests.sortedByDescending { it.timestamp }
    override fun getPendingRequests() = requests.filter { it.status == AllotmentRequestStatus.PENDING }
    override fun submitRequest(request: AllotmentRequest) { requests.add(request) }
    override fun fulfillRequest(id: String, staffId: String, staffName: String) {
        val idx = requests.indexOfFirst { it.id == id }
        if (idx >= 0) requests[idx] = requests[idx].copy(
            status = AllotmentRequestStatus.FULFILLED,
            fulfilledById = staffId,
            fulfilledByName = staffName,
            fulfilledAt = LocalDateTime.now(),
        )
    }
    override fun getByMedicationEntryId(medicationEntryId: String) =
        requests.firstOrNull { it.medicationEntryId == medicationEntryId && it.status == AllotmentRequestStatus.PENDING }
}

interface NotificationRepository {
    fun getForRecipient(staffId: String, role: UserRole): List<AppNotification>
    fun getUnreadCountForRecipient(staffId: String, role: UserRole): Int
    fun add(notification: AppNotification)
    fun markRead(id: String)
    fun markAllReadForRecipient(staffId: String, role: UserRole)
}

class MockNotificationRepository : NotificationRepository {
    private val notifications = MockData.notifications.toMutableList()
    private fun matches(n: AppNotification, staffId: String, role: UserRole) =
        (n.recipientStaffId.isNotEmpty() && n.recipientStaffId == staffId) ||
        (n.recipientRole != null && n.recipientRole == role)
    override fun getForRecipient(staffId: String, role: UserRole) =
        notifications.filter { matches(it, staffId, role) }.sortedByDescending { it.timestamp }
    override fun getUnreadCountForRecipient(staffId: String, role: UserRole) =
        notifications.count { matches(it, staffId, role) && !it.isRead }
    override fun add(notification: AppNotification) { notifications.add(notification) }
    override fun markRead(id: String) {
        val idx = notifications.indexOfFirst { it.id == id }
        if (idx >= 0) notifications[idx] = notifications[idx].copy(isRead = true)
    }
    override fun markAllReadForRecipient(staffId: String, role: UserRole) {
        notifications.forEachIndexed { idx, n ->
            if (matches(n, staffId, role) && !n.isRead) notifications[idx] = n.copy(isRead = true)
        }
    }
}

interface AuditRepository {
    fun getAllLogs(): List<AuditLogEntry>
    fun addLog(entry: AuditLogEntry)
}

class MockAuditRepository : AuditRepository {
    private val logs = MockData.auditLog.toMutableList()
    override fun getAllLogs() = logs.sortedByDescending { it.timestamp }
    override fun addLog(entry: AuditLogEntry) { logs.add(0, entry) }
}

// ─────────────────────────────────────────────────────────────────────────────
// Staff (Admin)
// ─────────────────────────────────────────────────────────────────────────────

interface StaffRepository {
    fun getAllStaff(): List<Staff>
    fun addStaff(staff: Staff)
    fun revokeStaff(id: String)
    fun unrevokeStaff(id: String)
    fun deleteStaff(id: String)
    fun updateStaff(staff: Staff)
}

class MockStaffRepository : StaffRepository {
    private val staffList = MockData.staffList.toMutableList()
    override fun getAllStaff() = staffList.sortedBy { it.name }
    override fun addStaff(staff: Staff) { staffList.add(staff) }
    override fun revokeStaff(id: String) {
        val idx = staffList.indexOfFirst { it.id == id }
        if (idx >= 0) staffList[idx] = staffList[idx].copy(isActive = false)
    }
    override fun unrevokeStaff(id: String) {
        val idx = staffList.indexOfFirst { it.id == id }
        if (idx >= 0) staffList[idx] = staffList[idx].copy(isActive = true)
    }
    override fun deleteStaff(id: String) { staffList.removeAll { it.id == id } }
    override fun updateStaff(staff: Staff) {
        val idx = staffList.indexOfFirst { it.id == staff.id }
        if (idx >= 0) staffList[idx] = staff
    }
}
