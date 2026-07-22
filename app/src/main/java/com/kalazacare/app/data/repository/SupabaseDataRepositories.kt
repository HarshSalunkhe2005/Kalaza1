package com.kalazacare.app.data.repository

import com.kalazacare.app.data.model.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.util.UUID

// ─────────────────────────────────────────────────────────────────────────────
// Shared string <-> java.time helpers. PostgREST hands back plain JSON strings
// for date/time/timestamptz columns, so every row here is decoded into a DTO
// with String fields first, then converted by hand — the same shape as the
// old Firestore field-map parsing, just typed via kotlinx.serialization DTOs.
// ─────────────────────────────────────────────────────────────────────────────

private fun parseDate(s: String): LocalDate = runCatching { LocalDate.parse(s) }.getOrDefault(LocalDate.now())
private fun parseDateOrNull(s: String?): LocalDate? = s?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
private fun parseTime(s: String): LocalTime = runCatching { LocalTime.parse(s) }.getOrDefault(LocalTime.now())
private fun parseTimestamp(s: String): LocalDateTime = runCatching { OffsetDateTime.parse(s).toLocalDateTime() }
    .recoverCatching { LocalDateTime.parse(s) }.getOrDefault(LocalDateTime.now())
private fun parseTimestampOrNull(s: String?): LocalDateTime? = s?.let {
    runCatching { OffsetDateTime.parse(it).toLocalDateTime() }.recoverCatching { LocalDateTime.parse(it) }.getOrNull()
}
private fun newId() = UUID.randomUUID().toString()

// ─────────────────────────────────────────────────────────────────────────────
// Patients
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
private data class PatientRow(
    val id: String,
    val name: String = "",
    val age: Int = 0,
    val gender: String = "MALE",
    @SerialName("room_no") val roomNo: String = "",
    @SerialName("medical_history") val medicalHistory: String = "",
    @SerialName("current_issues") val currentIssues: String = "",
    val allergies: String = "",
    @SerialName("emergency_contact") val emergencyContact: String = "",
    @SerialName("emergency_phone") val emergencyPhone: String = "",
    @SerialName("admission_date") val admissionDate: String = LocalDate.now().toString(),
    @SerialName("is_archived") val isArchived: Boolean = false,
    @SerialName("primary_diagnosis") val primaryDiagnosis: String = "",
)

private fun PatientRow.toDomain() = Patient(
    id = id, name = name, age = age,
    gender = runCatching { Gender.valueOf(gender) }.getOrDefault(Gender.MALE),
    roomNo = roomNo, medicalHistory = medicalHistory, currentIssues = currentIssues, allergies = allergies,
    emergencyContact = emergencyContact, emergencyPhone = emergencyPhone,
    admissionDate = parseDate(admissionDate), isArchived = isArchived, primaryDiagnosis = primaryDiagnosis,
)
private fun Patient.toRow() = PatientRow(
    id = id, name = name, age = age, gender = gender.name, roomNo = roomNo, medicalHistory = medicalHistory,
    currentIssues = currentIssues, allergies = allergies, emergencyContact = emergencyContact,
    emergencyPhone = emergencyPhone, admissionDate = admissionDate.toString(), isArchived = isArchived,
    primaryDiagnosis = primaryDiagnosis,
)

class SupabasePatientRepository(private val client: SupabaseClient) : PatientRepository {
    private val table = "patients"
    override suspend fun getAllPatients(includeArchived: Boolean): List<Patient> {
        val all = client.postgrest.from(table).select().decodeList<PatientRow>().map { it.toDomain() }
        return (if (includeArchived) all else all.filter { !it.isArchived }).sortedBy { it.name }
    }
    override suspend fun getPatientById(id: String): Patient? =
        client.postgrest.from(table).select { filter { eq("id", id) } }
            .decodeSingleOrNull<PatientRow>()?.toDomain()
    override suspend fun addPatient(patient: Patient): Patient {
        val saved = patient.copy(id = newId())
        client.postgrest.from(table).insert(saved.toRow())
        return saved
    }
    override suspend fun updatePatient(patient: Patient) {
        client.postgrest.from(table).update(patient.toRow()) { filter { eq("id", patient.id) } }
    }
    override suspend fun archivePatient(id: String) {
        client.postgrest.from(table).update(mapOf("is_archived" to true)) { filter { eq("id", id) } }
    }
    override suspend fun unarchivePatient(id: String) {
        client.postgrest.from(table).update(mapOf("is_archived" to false)) { filter { eq("id", id) } }
    }
    override suspend fun searchPatients(query: String, includeArchived: Boolean): List<Patient> =
        getAllPatients(includeArchived).filter {
            it.name.contains(query, ignoreCase = true) || it.roomNo.contains(query, ignoreCase = true)
        }
}

// ─────────────────────────────────────────────────────────────────────────────
// Vitals
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
private data class VitalRow(
    val id: String,
    @SerialName("patient_id") val patientId: String,
    val date: String = LocalDate.now().toString(),
    val time: String = LocalTime.now().toString(),
    val pulse: String = "", val bp: String = "", val spo2: String = "", val temperature: String = "",
    @SerialName("sugar_fasting") val sugarFasting: String = "",
    @SerialName("sugar_pp") val sugarPP: String = "",
    @SerialName("signed_by") val signedBy: String = "",
)
private fun VitalRow.toDomain() = VitalRecord(
    id = id, patientId = patientId, date = parseDate(date), time = parseTime(time),
    pulse = pulse, bp = bp, spo2 = spo2, temperature = temperature,
    sugarFasting = sugarFasting, sugarPP = sugarPP, signedBy = signedBy,
)
private fun VitalRecord.toRow() = VitalRow(
    id = id, patientId = patientId, date = date.toString(), time = time.toString(),
    pulse = pulse, bp = bp, spo2 = spo2, temperature = temperature,
    sugarFasting = sugarFasting, sugarPP = sugarPP, signedBy = signedBy,
)

class SupabaseVitalsRepository(private val client: SupabaseClient) : VitalsRepository {
    private val table = "vitals"
    override suspend fun getVitalsForPatient(patientId: String): List<VitalRecord> =
        client.postgrest.from(table).select { filter { eq("patient_id", patientId) } }
            .decodeList<VitalRow>().map { it.toDomain() }.sortedByDescending { it.date }
    override suspend fun getVitalById(id: String): VitalRecord? =
        client.postgrest.from(table).select { filter { eq("id", id) } }.decodeSingleOrNull<VitalRow>()?.toDomain()
    override suspend fun addVital(record: VitalRecord) {
        client.postgrest.from(table).insert(record.copy(id = newId()).toRow())
    }
    override suspend fun updateVital(record: VitalRecord) {
        client.postgrest.from(table).update(record.toRow()) { filter { eq("id", record.id) } }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Medication (MAR)
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
private data class MedicationRow(
    val id: String,
    @SerialName("patient_id") val patientId: String,
    @SerialName("medicine_name") val medicineName: String = "",
    val dose: String = "", val quantity: String = "",
    @SerialName("schedule_time") val scheduleTime: String = LocalTime.now().toString(),
    @SerialName("scheduled_date") val scheduledDate: String = LocalDate.now().toString(),
    val status: String = "PENDING",
    @SerialName("administered_by") val administeredBy: String = "",
    @SerialName("administered_at") val administeredAt: String? = null,
    val notes: String = "",
    @SerialName("allotment_status") val allotmentStatus: String = "NOT_ALLOTTED",
    @SerialName("allotted_by_id") val allottedById: String? = null,
    @SerialName("allotted_by_name") val allottedByName: String = "",
    @SerialName("allotted_at") val allottedAt: String? = null,
    @SerialName("allotment_photo_url") val allotmentPhotoUrl: String = "",
    @SerialName("allotment_photo_expires_at") val allotmentPhotoExpiresAt: String? = null,
    @SerialName("administered_photo_url") val administeredPhotoUrl: String = "",
    @SerialName("administered_photo_expires_at") val administeredPhotoExpiresAt: String? = null,
)
private fun MedicationRow.toDomain(): MedicationEntry {
    val entry = MedicationEntry(
        id = id, patientId = patientId, medicineName = medicineName, dose = dose, quantity = quantity,
        scheduleTime = parseTime(scheduleTime), scheduledDate = parseDate(scheduledDate),
        status = runCatching { MedStatus.valueOf(status) }.getOrDefault(MedStatus.PENDING),
        administeredBy = administeredBy, administeredAt = parseTimestampOrNull(administeredAt), notes = notes,
        allotmentStatus = runCatching { AllotmentStatus.valueOf(allotmentStatus) }.getOrDefault(AllotmentStatus.NOT_ALLOTTED),
        allottedById = allottedById ?: "", allottedByName = allottedByName,
        allottedAt = parseTimestampOrNull(allottedAt), allotmentPhotoUrl = allotmentPhotoUrl,
        allotmentPhotoExpiresAt = parseTimestampOrNull(allotmentPhotoExpiresAt),
        administeredPhotoUrl = administeredPhotoUrl,
        administeredPhotoExpiresAt = parseTimestampOrNull(administeredPhotoExpiresAt),
    )
    return entry.withComputedStatus()
}
private fun MedicationEntry.toRow() = MedicationRow(
    id = id, patientId = patientId, medicineName = medicineName, dose = dose, quantity = quantity,
    scheduleTime = scheduleTime.toString(), scheduledDate = scheduledDate.toString(), status = status.name,
    administeredBy = administeredBy, administeredAt = administeredAt?.toString(), notes = notes,
    allotmentStatus = allotmentStatus.name, allottedById = allottedById.ifBlank { null }, allottedByName = allottedByName,
    allottedAt = allottedAt?.toString(), allotmentPhotoUrl = allotmentPhotoUrl,
    allotmentPhotoExpiresAt = allotmentPhotoExpiresAt?.toString(), administeredPhotoUrl = administeredPhotoUrl,
    administeredPhotoExpiresAt = administeredPhotoExpiresAt?.toString(),
)

/**
 * PENDING/OVERDUE is a live-computed view of the schedule, not a persisted fact —
 * recomputed on every read so editing a dose's time (e.g. moving it later)
 * un-overdues it instead of leaving it stuck OVERDUE forever. ADMINISTERED
 * entries are left untouched regardless of schedule.
 */
private fun MedicationEntry.withComputedStatus(): MedicationEntry {
    if (status != MedStatus.PENDING && status != MedStatus.OVERDUE) return this
    val scheduledAt = LocalDateTime.of(scheduledDate, scheduleTime)
    val computed = if (scheduledAt.isBefore(LocalDateTime.now())) MedStatus.OVERDUE else MedStatus.PENDING
    return if (computed != status) copy(status = computed) else this
}

class SupabaseMedicationRepository(private val client: SupabaseClient) : MedicationRepository {
    private val table = "medications"
    override suspend fun getMedicationsForPatient(patientId: String, date: LocalDate): List<MedicationEntry> =
        client.postgrest.from(table).select { filter { eq("patient_id", patientId) } }
            .decodeList<MedicationRow>().map { it.toDomain() }
            .filter { it.scheduledDate == date }.sortedBy { it.scheduleTime }
    override suspend fun getMedicationsForPatient(patientId: String): List<MedicationEntry> =
        client.postgrest.from(table).select { filter { eq("patient_id", patientId) } }
            .decodeList<MedicationRow>().map { it.toDomain() }.sortedBy { it.scheduleTime }
    override suspend fun getMedicationsForDate(date: LocalDate): List<MedicationEntry> =
        getAllMedications().filter { it.scheduledDate == date }.sortedBy { it.scheduleTime }
    override suspend fun getAllMedications(): List<MedicationEntry> =
        client.postgrest.from(table).select().decodeList<MedicationRow>()
            .map { it.toDomain() }.sortedByDescending { it.scheduledDate }
    override suspend fun getMedicationById(id: String): MedicationEntry? =
        client.postgrest.from(table).select { filter { eq("id", id) } }.decodeSingleOrNull<MedicationRow>()?.toDomain()
    override suspend fun addMedication(entry: MedicationEntry) {
        client.postgrest.from(table).insert(entry.copy(id = newId()).toRow())
    }
    override suspend fun updateMedication(entry: MedicationEntry) {
        client.postgrest.from(table).update(entry.toRow()) { filter { eq("id", entry.id) } }
    }
    override suspend fun deleteMedication(id: String) {
        client.postgrest.from(table).delete { filter { eq("id", id) } }
    }
    override suspend fun markAdministered(id: String, staffName: String, photoUrl: String, photoExpiresAt: LocalDateTime) {
        client.postgrest.from(table).update(
            mapOf(
                "status" to MedStatus.ADMINISTERED.name,
                "administered_by" to staffName,
                "administered_at" to LocalDateTime.now().toString(),
                "administered_photo_url" to photoUrl,
                "administered_photo_expires_at" to photoExpiresAt.toString(),
            )
        ) { filter { eq("id", id) } }
    }
    override suspend fun allotMedication(id: String, staffId: String, staffName: String, photoUrl: String, photoExpiresAt: LocalDateTime) {
        client.postgrest.from(table).update(
            mapOf(
                "allotment_status" to AllotmentStatus.ALLOTTED.name,
                "allotted_by_id" to staffId,
                "allotted_by_name" to staffName,
                "allotted_at" to LocalDateTime.now().toString(),
                "allotment_photo_url" to photoUrl,
                "allotment_photo_expires_at" to photoExpiresAt.toString(),
            )
        ) { filter { eq("id", id) } }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Utility Records & Items
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
private data class UtilityRecordRow(
    val id: String,
    @SerialName("patient_id") val patientId: String,
    val date: String = LocalDate.now().toString(),
    val time: String = LocalTime.now().toString(),
    val quantities: Map<String, Int> = emptyMap(),
    @SerialName("issued_to_caregiver") val issuedToCaregiver: String = "",
    @SerialName("issued_by_supervisor") val issuedBySupervisor: String = "",
    @SerialName("checked_by") val checkedBy: String = "",
)
private fun UtilityRecordRow.toDomain() = UtilityRecord(
    id = id, patientId = patientId, date = parseDate(date), time = parseTime(time), quantities = quantities,
    issuedToCaregiver = issuedToCaregiver, issuedBySupervisor = issuedBySupervisor, checkedBy = checkedBy,
)
private fun UtilityRecord.toRow() = UtilityRecordRow(
    id = id, patientId = patientId, date = date.toString(), time = time.toString(), quantities = quantities,
    issuedToCaregiver = issuedToCaregiver, issuedBySupervisor = issuedBySupervisor, checkedBy = checkedBy,
)

@Serializable
private data class UtilityItemRow(
    val id: String, val name: String = "", val unit: String = "pcs",
    @SerialName("display_order") val displayOrder: Int = 0,
    @SerialName("is_active") val isActive: Boolean = true,
)
private fun UtilityItemRow.toDomain() = UtilityItem(id = id, name = name, unit = unit, displayOrder = displayOrder, isActive = isActive)
private fun UtilityItem.toRow() = UtilityItemRow(id = id, name = name, unit = unit, displayOrder = displayOrder, isActive = isActive)

class SupabaseUtilityRepository(private val client: SupabaseClient) : UtilityRepository {
    private val recordsTable = "utility_records"
    private val itemsTable = "utility_items"
    override suspend fun getUtilityForPatient(patientId: String): List<UtilityRecord> =
        client.postgrest.from(recordsTable).select { filter { eq("patient_id", patientId) } }
            .decodeList<UtilityRecordRow>().map { it.toDomain() }.sortedByDescending { it.date }
    override suspend fun getUtilityRecordById(id: String): UtilityRecord? =
        client.postgrest.from(recordsTable).select { filter { eq("id", id) } }
            .decodeSingleOrNull<UtilityRecordRow>()?.toDomain()
    override suspend fun addUtilityRecord(record: UtilityRecord) {
        client.postgrest.from(recordsTable).insert(record.copy(id = newId()).toRow())
    }
    override suspend fun updateUtilityRecord(record: UtilityRecord) {
        client.postgrest.from(recordsTable).update(record.toRow()) { filter { eq("id", record.id) } }
    }
    override suspend fun getUtilityItems(): List<UtilityItem> = getAllUtilityItems().filter { it.isActive }
    override suspend fun getAllUtilityItems(): List<UtilityItem> =
        client.postgrest.from(itemsTable).select().decodeList<UtilityItemRow>()
            .map { it.toDomain() }.sortedBy { it.displayOrder }
    override suspend fun addUtilityItem(item: UtilityItem) {
        client.postgrest.from(itemsTable).insert(item.copy(id = newId()).toRow())
    }
    override suspend fun deleteUtilityItem(id: String) {
        client.postgrest.from(itemsTable).update(mapOf("is_active" to false)) { filter { eq("id", id) } }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Doctor Visits & Care Notes
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
private data class DoctorVisitRow(
    val id: String,
    @SerialName("patient_id") val patientId: String,
    @SerialName("doctor_name") val doctorName: String = "",
    val specialty: String = "",
    val date: String = LocalDate.now().toString(),
    val time: String = LocalTime.now().toString(),
    val notes: String = "",
    @SerialName("next_visit_date") val nextVisitDate: String? = null,
    @SerialName("prescription_changes") val prescriptionChanges: String = "",
    @SerialName("is_confirmed") val isConfirmed: Boolean = false,
    @SerialName("is_archived") val isArchived: Boolean = false,
)
private fun DoctorVisitRow.toDomain() = DoctorVisit(
    id = id, patientId = patientId, doctorName = doctorName, specialty = specialty,
    date = parseDate(date), time = parseTime(time), notes = notes,
    nextVisitDate = parseDateOrNull(nextVisitDate), prescriptionChanges = prescriptionChanges,
    isConfirmed = isConfirmed, isArchived = isArchived,
)
private fun DoctorVisit.toRow() = DoctorVisitRow(
    id = id, patientId = patientId, doctorName = doctorName, specialty = specialty,
    date = date.toString(), time = time.toString(), notes = notes,
    nextVisitDate = nextVisitDate?.toString(), prescriptionChanges = prescriptionChanges,
    isConfirmed = isConfirmed, isArchived = isArchived,
)

class SupabaseDoctorVisitRepository(private val client: SupabaseClient) : DoctorVisitRepository {
    private val table = "doctor_visits"
    override suspend fun getVisitsForPatient(patientId: String): List<DoctorVisit> =
        client.postgrest.from(table).select { filter { eq("patient_id", patientId) } }
            .decodeList<DoctorVisitRow>().map { it.toDomain() }.sortedByDescending { it.date }
    override suspend fun getVisitById(id: String): DoctorVisit? =
        client.postgrest.from(table).select { filter { eq("id", id) } }.decodeSingleOrNull<DoctorVisitRow>()?.toDomain()
    override suspend fun addVisit(visit: DoctorVisit) {
        client.postgrest.from(table).insert(visit.copy(id = newId()).toRow())
    }
    override suspend fun updateVisit(visit: DoctorVisit) {
        client.postgrest.from(table).update(visit.toRow()) { filter { eq("id", visit.id) } }
    }
    override suspend fun deleteVisit(id: String) {
        client.postgrest.from(table).delete { filter { eq("id", id) } }
    }
}

@Serializable
private data class CareNoteRow(
    val id: String,
    @SerialName("patient_id") val patientId: String,
    @SerialName("staff_id") val staffId: String? = null,
    @SerialName("staff_name") val staffName: String = "",
    val timestamp: String = LocalDateTime.now().toString(),
    val note: String = "",
)
private fun CareNoteRow.toDomain() = CareNote(
    id = id, patientId = patientId, staffId = staffId ?: "", staffName = staffName,
    timestamp = parseTimestamp(timestamp), note = note,
)
private fun CareNote.toRow() = CareNoteRow(
    id = id, patientId = patientId, staffId = staffId.ifBlank { null }, staffName = staffName,
    timestamp = timestamp.toString(), note = note,
)

class SupabaseCareNoteRepository(private val client: SupabaseClient) : CareNoteRepository {
    private val table = "care_notes"
    override suspend fun getNotesForPatient(patientId: String): List<CareNote> =
        client.postgrest.from(table).select { filter { eq("patient_id", patientId) } }
            .decodeList<CareNoteRow>().map { it.toDomain() }.sortedByDescending { it.timestamp }
    override suspend fun addNote(note: CareNote) {
        client.postgrest.from(table).insert(note.copy(id = newId()).toRow())
    }
    override suspend fun updateNote(note: CareNote) {
        client.postgrest.from(table).update(note.toRow()) { filter { eq("id", note.id) } }
    }
    override suspend fun getNoteById(id: String): CareNote? =
        client.postgrest.from(table).select { filter { eq("id", id) } }.decodeSingleOrNull<CareNoteRow>()?.toDomain()
}

// ─────────────────────────────────────────────────────────────────────────────
// Approval Queue
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
private data class ApprovalRequestRow(
    val id: String,
    @SerialName("entity_type") val entityType: String = "PATIENT",
    @SerialName("entity_id") val entityId: String = "",
    val action: String = "EDIT",
    @SerialName("patient_id") val patientId: String? = null,
    @SerialName("patient_name") val patientName: String = "",
    @SerialName("requested_by_id") val requestedById: String? = null,
    @SerialName("requested_by_name") val requestedByName: String = "",
    @SerialName("field_changed") val fieldChanged: String = "",
    @SerialName("old_value") val oldValue: String = "",
    @SerialName("new_value") val newValue: String = "",
    val status: String = "PENDING",
    @SerialName("reviewed_by_id") val reviewedById: String? = null,
    @SerialName("reviewed_by_name") val reviewedByName: String = "",
    val timestamp: String = LocalDateTime.now().toString(),
    @SerialName("reviewed_at") val reviewedAt: String? = null,
    @SerialName("rejection_reason") val rejectionReason: String = "",
)
private fun ApprovalRequestRow.toDomain() = ApprovalRequest(
    id = id, entityType = runCatching { ApprovalEntityType.valueOf(entityType) }.getOrDefault(ApprovalEntityType.PATIENT),
    entityId = entityId, action = runCatching { ApprovalAction.valueOf(action) }.getOrDefault(ApprovalAction.EDIT),
    patientId = patientId ?: "", patientName = patientName, requestedById = requestedById ?: "",
    requestedByName = requestedByName, fieldChanged = fieldChanged, oldValue = oldValue, newValue = newValue,
    status = runCatching { ApprovalStatus.valueOf(status) }.getOrDefault(ApprovalStatus.PENDING),
    reviewedById = reviewedById ?: "", reviewedByName = reviewedByName, timestamp = parseTimestamp(timestamp),
    reviewedAt = parseTimestampOrNull(reviewedAt), rejectionReason = rejectionReason,
)
private fun ApprovalRequest.toRow() = ApprovalRequestRow(
    id = id, entityType = entityType.name, entityId = entityId, action = action.name,
    patientId = patientId.ifBlank { null }, patientName = patientName, requestedById = requestedById.ifBlank { null },
    requestedByName = requestedByName, fieldChanged = fieldChanged, oldValue = oldValue, newValue = newValue,
    status = status.name, reviewedById = reviewedById.ifBlank { null }, reviewedByName = reviewedByName,
    timestamp = timestamp.toString(), reviewedAt = reviewedAt?.toString(), rejectionReason = rejectionReason,
)

class SupabaseApprovalRepository(private val client: SupabaseClient) : ApprovalRepository {
    private val table = "approval_requests"
    override suspend fun getAllRequests(): List<ApprovalRequest> =
        client.postgrest.from(table).select().decodeList<ApprovalRequestRow>()
            .map { it.toDomain() }.sortedByDescending { it.timestamp }
    override suspend fun getPendingRequests(): List<ApprovalRequest> =
        getAllRequests().filter { it.status == ApprovalStatus.PENDING }
    override suspend fun getRequestById(id: String): ApprovalRequest? =
        client.postgrest.from(table).select { filter { eq("id", id) } }
            .decodeSingleOrNull<ApprovalRequestRow>()?.toDomain()
    override suspend fun approve(id: String, reviewerId: String, reviewerName: String) {
        client.postgrest.from(table).update(
            mapOf(
                "status" to ApprovalStatus.APPROVED.name,
                "reviewed_by_id" to reviewerId,
                "reviewed_by_name" to reviewerName,
                "reviewed_at" to LocalDateTime.now().toString(),
            )
        ) { filter { eq("id", id) } }
    }
    override suspend fun reject(id: String, reviewerId: String, reviewerName: String, reason: String) {
        client.postgrest.from(table).update(
            mapOf(
                "status" to ApprovalStatus.REJECTED.name,
                "reviewed_by_id" to reviewerId,
                "reviewed_by_name" to reviewerName,
                "reviewed_at" to LocalDateTime.now().toString(),
                "rejection_reason" to reason,
            )
        ) { filter { eq("id", id) } }
    }
    override suspend fun submitRequest(request: ApprovalRequest) {
        client.postgrest.from(table).insert(request.copy(id = newId()).toRow())
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Allotment Requests
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
private data class AllotmentRequestRow(
    val id: String,
    @SerialName("medication_entry_id") val medicationEntryId: String,
    @SerialName("patient_id") val patientId: String,
    @SerialName("patient_name") val patientName: String = "",
    @SerialName("medicine_name") val medicineName: String = "",
    val dose: String = "",
    @SerialName("scheduled_time") val scheduledTime: String = LocalTime.now().toString(),
    @SerialName("requested_by_id") val requestedById: String? = null,
    @SerialName("requested_by_name") val requestedByName: String = "",
    val status: String = "PENDING",
    @SerialName("fulfilled_by_id") val fulfilledById: String? = null,
    @SerialName("fulfilled_by_name") val fulfilledByName: String = "",
    val timestamp: String = LocalDateTime.now().toString(),
    @SerialName("fulfilled_at") val fulfilledAt: String? = null,
)
private fun AllotmentRequestRow.toDomain() = AllotmentRequest(
    id = id, medicationEntryId = medicationEntryId, patientId = patientId, patientName = patientName,
    medicineName = medicineName, dose = dose, scheduledTime = parseTime(scheduledTime),
    requestedById = requestedById ?: "", requestedByName = requestedByName,
    status = runCatching { AllotmentRequestStatus.valueOf(status) }.getOrDefault(AllotmentRequestStatus.PENDING),
    fulfilledById = fulfilledById ?: "", fulfilledByName = fulfilledByName,
    timestamp = parseTimestamp(timestamp), fulfilledAt = parseTimestampOrNull(fulfilledAt),
)
private fun AllotmentRequest.toRow() = AllotmentRequestRow(
    id = id, medicationEntryId = medicationEntryId, patientId = patientId, patientName = patientName,
    medicineName = medicineName, dose = dose, scheduledTime = scheduledTime.toString(),
    requestedById = requestedById.ifBlank { null }, requestedByName = requestedByName, status = status.name,
    fulfilledById = fulfilledById.ifBlank { null }, fulfilledByName = fulfilledByName,
    timestamp = timestamp.toString(), fulfilledAt = fulfilledAt?.toString(),
)

class SupabaseAllotmentRequestRepository(private val client: SupabaseClient) : AllotmentRequestRepository {
    private val table = "allotment_requests"
    override suspend fun getAllRequests(): List<AllotmentRequest> =
        client.postgrest.from(table).select().decodeList<AllotmentRequestRow>()
            .map { it.toDomain() }.sortedByDescending { it.timestamp }
    override suspend fun getPendingRequests(): List<AllotmentRequest> =
        getAllRequests().filter { it.status == AllotmentRequestStatus.PENDING }
    override suspend fun submitRequest(request: AllotmentRequest) {
        client.postgrest.from(table).insert(request.copy(id = newId()).toRow())
    }
    override suspend fun fulfillRequest(id: String, staffId: String, staffName: String) {
        client.postgrest.from(table).update(
            mapOf(
                "status" to AllotmentRequestStatus.FULFILLED.name,
                "fulfilled_by_id" to staffId,
                "fulfilled_by_name" to staffName,
                "fulfilled_at" to LocalDateTime.now().toString(),
            )
        ) { filter { eq("id", id) } }
    }
    override suspend fun getByMedicationEntryId(medicationEntryId: String): AllotmentRequest? =
        getPendingRequests().firstOrNull { it.medicationEntryId == medicationEntryId }
}

// ─────────────────────────────────────────────────────────────────────────────
// Notifications
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
private data class NotificationRow(
    val id: String,
    @SerialName("recipient_staff_id") val recipientStaffId: String? = null,
    @SerialName("recipient_role") val recipientRole: String? = null,
    val type: String = "APPROVAL_REQUESTED",
    val title: String = "", val message: String = "",
    val timestamp: String = LocalDateTime.now().toString(),
    @SerialName("is_read") val isRead: Boolean = false,
    @SerialName("target_route") val targetRoute: String = "",
)
private fun NotificationRow.toDomain() = AppNotification(
    id = id, recipientStaffId = recipientStaffId ?: "",
    recipientRole = recipientRole?.let { runCatching { UserRole.valueOf(it) }.getOrNull() },
    type = runCatching { NotificationType.valueOf(type) }.getOrDefault(NotificationType.APPROVAL_REQUESTED),
    title = title, message = message, timestamp = parseTimestamp(timestamp), isRead = isRead, targetRoute = targetRoute,
)
private fun AppNotification.toRow() = NotificationRow(
    id = id, recipientStaffId = recipientStaffId.ifBlank { null }, recipientRole = recipientRole?.name,
    type = type.name, title = title, message = message, timestamp = timestamp.toString(),
    isRead = isRead, targetRoute = targetRoute,
)

class SupabaseNotificationRepository(private val client: SupabaseClient) : NotificationRepository {
    private val table = "notifications"
    override suspend fun getForRecipient(staffId: String, role: UserRole): List<AppNotification> =
        client.postgrest.from(table).select {
            filter { or { eq("recipient_staff_id", staffId); eq("recipient_role", role.name) } }
        }.decodeList<NotificationRow>().map { it.toDomain() }.sortedByDescending { it.timestamp }
    override suspend fun getUnreadCountForRecipient(staffId: String, role: UserRole): Int =
        getForRecipient(staffId, role).count { !it.isRead }
    override suspend fun add(notification: AppNotification) {
        client.postgrest.from(table).insert(notification.copy(id = newId()).toRow())
    }
    override suspend fun markRead(id: String) {
        client.postgrest.from(table).update(mapOf("is_read" to true)) { filter { eq("id", id) } }
    }
    override suspend fun markAllReadForRecipient(staffId: String, role: UserRole) {
        val unread = getForRecipient(staffId, role).filter { !it.isRead }
        unread.forEach { client.postgrest.from(table).update(mapOf("is_read" to true)) { filter { eq("id", it.id) } } }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Audit Log
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
private data class AuditLogRow(
    val id: String, val action: String = "",
    @SerialName("performed_by_id") val performedById: String? = null,
    @SerialName("performed_by_name") val performedByName: String = "",
    @SerialName("target_patient_id") val targetPatientId: String = "",
    @SerialName("target_patient_name") val targetPatientName: String = "",
    val details: String = "", val timestamp: String = LocalDateTime.now().toString(),
    @SerialName("icon_name") val iconName: String = "edit",
)
private fun AuditLogRow.toDomain() = AuditLogEntry(
    id = id, action = action, performedById = performedById ?: "", performedByName = performedByName,
    targetPatientId = targetPatientId, targetPatientName = targetPatientName, details = details,
    timestamp = parseTimestamp(timestamp), iconName = iconName.ifBlank { "edit" },
)
private fun AuditLogEntry.toRow() = AuditLogRow(
    id = id, action = action, performedById = performedById.ifBlank { null }, performedByName = performedByName,
    targetPatientId = targetPatientId, targetPatientName = targetPatientName, details = details,
    timestamp = timestamp.toString(), iconName = iconName,
)

class SupabaseAuditRepository(private val client: SupabaseClient) : AuditRepository {
    private val table = "audit_log"
    override suspend fun getAllLogs(): List<AuditLogEntry> =
        client.postgrest.from(table).select().decodeList<AuditLogRow>()
            .map { it.toDomain() }.sortedByDescending { it.timestamp }
    override suspend fun addLog(entry: AuditLogEntry) {
        client.postgrest.from(table).insert(entry.copy(id = newId()).toRow())
    }
}
