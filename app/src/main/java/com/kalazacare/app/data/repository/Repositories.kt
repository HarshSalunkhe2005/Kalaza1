package com.kalazacare.app.data.repository

import com.kalazacare.app.data.model.*
import java.time.LocalDate

// ─────────────────────────────────────────────────────────────────────────────
// Auth
// ─────────────────────────────────────────────────────────────────────────────

interface AuthRepository {
    /** Null on any failure — wrong name, wrong password, or a revoked (inactive) account. */
    suspend fun login(name: String, password: String): Staff?
    fun logout()
    fun currentStaff(): Staff?
}

// ─────────────────────────────────────────────────────────────────────────────
// Patients
// ─────────────────────────────────────────────────────────────────────────────

interface PatientRepository {
    suspend fun getAllPatients(includeArchived: Boolean = false): List<Patient>
    suspend fun getPatientById(id: String): Patient?
    /** Returns the created patient with its server-assigned id. */
    suspend fun addPatient(patient: Patient): Patient
    suspend fun updatePatient(patient: Patient)
    suspend fun archivePatient(id: String)
    suspend fun unarchivePatient(id: String)
    suspend fun searchPatients(query: String, includeArchived: Boolean = false): List<Patient>
}

// ─────────────────────────────────────────────────────────────────────────────
// Vitals
// ─────────────────────────────────────────────────────────────────────────────

interface VitalsRepository {
    suspend fun getVitalsForPatient(patientId: String): List<VitalRecord>
    suspend fun getVitalById(id: String): VitalRecord?
    suspend fun addVital(record: VitalRecord)
    suspend fun updateVital(record: VitalRecord)
}

// ─────────────────────────────────────────────────────────────────────────────
// Medication (MAR)
// ─────────────────────────────────────────────────────────────────────────────

interface MedicationRepository {
    suspend fun getMedicationsForPatient(patientId: String, date: LocalDate): List<MedicationEntry>
    suspend fun getMedicationsForPatient(patientId: String): List<MedicationEntry>
    suspend fun getMedicationsForDate(date: LocalDate): List<MedicationEntry>
    suspend fun getAllMedications(): List<MedicationEntry>
    suspend fun getMedicationById(id: String): MedicationEntry?
    suspend fun addMedication(entry: MedicationEntry)
    suspend fun updateMedication(entry: MedicationEntry)
    suspend fun deleteMedication(id: String)
    suspend fun markAdministered(id: String, staffName: String, photoUrl: String, photoExpiresAt: java.time.LocalDateTime)
    suspend fun allotMedication(id: String, staffId: String, staffName: String, photoUrl: String, photoExpiresAt: java.time.LocalDateTime)
}

// ─────────────────────────────────────────────────────────────────────────────
// Utility Records
// ─────────────────────────────────────────────────────────────────────────────

interface UtilityRepository {
    suspend fun getUtilityForPatient(patientId: String): List<UtilityRecord>
    suspend fun getUtilityRecordById(id: String): UtilityRecord?
    suspend fun addUtilityRecord(record: UtilityRecord)
    suspend fun updateUtilityRecord(record: UtilityRecord)
    suspend fun getUtilityItems(): List<UtilityItem>
    /** Includes deactivated items too, so historical records referencing them stay readable. */
    suspend fun getAllUtilityItems(): List<UtilityItem>
    suspend fun addUtilityItem(item: UtilityItem)
    suspend fun deleteUtilityItem(id: String)
}

// ─────────────────────────────────────────────────────────────────────────────
// Doctor Visits & Care Notes
// ─────────────────────────────────────────────────────────────────────────────

interface DoctorVisitRepository {
    suspend fun getVisitsForPatient(patientId: String): List<DoctorVisit>
    suspend fun getVisitById(id: String): DoctorVisit?
    suspend fun addVisit(visit: DoctorVisit)
    suspend fun updateVisit(visit: DoctorVisit)
    suspend fun deleteVisit(id: String)
}

interface CareNoteRepository {
    suspend fun getNotesForPatient(patientId: String): List<CareNote>
    suspend fun addNote(note: CareNote)
    suspend fun updateNote(note: CareNote)
    suspend fun getNoteById(id: String): CareNote?
}

// ─────────────────────────────────────────────────────────────────────────────
// Approval & Audit
// ─────────────────────────────────────────────────────────────────────────────

interface ApprovalRepository {
    suspend fun getAllRequests(): List<ApprovalRequest>
    suspend fun getPendingRequests(): List<ApprovalRequest>
    suspend fun getRequestById(id: String): ApprovalRequest?
    suspend fun approve(id: String, reviewerId: String, reviewerName: String)
    suspend fun reject(id: String, reviewerId: String, reviewerName: String, reason: String)
    suspend fun submitRequest(request: ApprovalRequest)
}

interface AllotmentRequestRepository {
    suspend fun getAllRequests(): List<AllotmentRequest>
    suspend fun getPendingRequests(): List<AllotmentRequest>
    suspend fun submitRequest(request: AllotmentRequest)
    suspend fun fulfillRequest(id: String, staffId: String, staffName: String)
    suspend fun getByMedicationEntryId(medicationEntryId: String): AllotmentRequest?
}

interface NotificationRepository {
    suspend fun getForRecipient(staffId: String, role: UserRole): List<AppNotification>
    suspend fun getUnreadCountForRecipient(staffId: String, role: UserRole): Int
    suspend fun add(notification: AppNotification)
    suspend fun markRead(id: String)
    suspend fun markAllReadForRecipient(staffId: String, role: UserRole)
}

interface AuditRepository {
    suspend fun getAllLogs(): List<AuditLogEntry>
    suspend fun addLog(entry: AuditLogEntry)
}

// ─────────────────────────────────────────────────────────────────────────────
// Staff (Admin)
// ─────────────────────────────────────────────────────────────────────────────

/** Names are the login key, so two staff members can't share one. */
class DuplicateStaffNameException(name: String) : Exception("A staff member named \"$name\" already exists.")

interface StaffRepository {
    suspend fun getAllStaff(): List<Staff>
    /**
     * Creates the staff member and assigns [password] as their login credential —
     * done here, at creation time, per the Super-Admin-assigns-passwords policy.
     * Throws [DuplicateStaffNameException] if the name is already taken.
     */
    suspend fun addStaff(name: String, email: String, phone: String, role: UserRole, password: String): Staff
    suspend fun revokeStaff(id: String)
    suspend fun unrevokeStaff(id: String)
    suspend fun deleteStaff(id: String)
    suspend fun updateStaff(staff: Staff)
    /** Called after login and whenever Firebase Messaging rotates this device's token. */
    suspend fun updateFcmToken(staffId: String, token: String)
}
