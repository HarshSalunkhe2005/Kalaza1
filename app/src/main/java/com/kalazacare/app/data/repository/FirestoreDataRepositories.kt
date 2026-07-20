package com.kalazacare.app.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.kalazacare.app.data.model.*
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

// ─────────────────────────────────────────────────────────────────────────────
// Generic field-map helpers. Firestore's automatic POJO mapping doesn't
// understand java.time types, so every repository here reads/writes plain
// maps and converts LocalDate/LocalTime/LocalDateTime <-> ISO strings by hand.
// ─────────────────────────────────────────────────────────────────────────────

private fun Map<String, Any?>.str(key: String): String = this[key] as? String ?: ""
private fun Map<String, Any?>.strOrNull(key: String): String? = this[key] as? String
private fun Map<String, Any?>.int(key: String): Int = (this[key] as? Number)?.toInt() ?: 0
private fun Map<String, Any?>.bool(key: String, default: Boolean = false): Boolean = this[key] as? Boolean ?: default
private fun Map<String, Any?>.date(key: String): LocalDate =
    strOrNull(key)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
private fun Map<String, Any?>.dateOrNull(key: String): LocalDate? =
    strOrNull(key)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
private fun Map<String, Any?>.time(key: String): LocalTime =
    strOrNull(key)?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: LocalTime.now()
private fun Map<String, Any?>.dateTime(key: String): LocalDateTime =
    strOrNull(key)?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() } ?: LocalDateTime.now()
private fun Map<String, Any?>.dateTimeOrNull(key: String): LocalDateTime? =
    strOrNull(key)?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
private inline fun <reified E : Enum<E>> Map<String, Any?>.enum(key: String, default: E): E =
    strOrNull(key)?.let { name -> E::class.java.enumConstants.firstOrNull { it.name == name } } ?: default
private inline fun <reified E : Enum<E>> Map<String, Any?>.enumOrNull(key: String): E? =
    strOrNull(key)?.let { name -> E::class.java.enumConstants.firstOrNull { it.name == name } }
@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.intMap(key: String): Map<String, Int> =
    (this[key] as? Map<String, Any?>)?.mapValues { (_, v) -> (v as? Number)?.toInt() ?: 0 } ?: emptyMap()

// ─────────────────────────────────────────────────────────────────────────────
// Patients
// ─────────────────────────────────────────────────────────────────────────────

private fun Patient.toMap(): Map<String, Any?> = mapOf(
    "name" to name, "age" to age, "gender" to gender.name, "roomNo" to roomNo,
    "medicalHistory" to medicalHistory, "currentIssues" to currentIssues, "allergies" to allergies,
    "emergencyContact" to emergencyContact, "emergencyPhone" to emergencyPhone,
    "admissionDate" to admissionDate.toString(), "isArchived" to isArchived,
    "primaryDiagnosis" to primaryDiagnosis,
)
private fun patientFrom(id: String, d: Map<String, Any?>) = Patient(
    id = id, name = d.str("name"), age = d.int("age"), gender = d.enum("gender", Gender.MALE),
    roomNo = d.str("roomNo"), medicalHistory = d.str("medicalHistory"), currentIssues = d.str("currentIssues"),
    allergies = d.str("allergies"), emergencyContact = d.str("emergencyContact"), emergencyPhone = d.str("emergencyPhone"),
    admissionDate = d.date("admissionDate"), isArchived = d.bool("isArchived"),
    primaryDiagnosis = d.str("primaryDiagnosis"),
)

class FirestorePatientRepository(private val db: FirebaseFirestore) : PatientRepository {
    private val col = db.collection("patients")
    override suspend fun getAllPatients(includeArchived: Boolean): List<Patient> {
        val all = col.get().await().documents.mapNotNull { d -> d.data?.let { patientFrom(d.id, it) } }
        return (if (includeArchived) all else all.filter { !it.isArchived }).sortedBy { it.name }
    }
    override suspend fun getPatientById(id: String): Patient? {
        val doc = col.document(id).get().await()
        return doc.data?.let { patientFrom(doc.id, it) }
    }
    override suspend fun addPatient(patient: Patient): Patient {
        val ref = col.document()
        val saved = patient.copy(id = ref.id)
        ref.set(saved.toMap()).await()
        return saved
    }
    override suspend fun updatePatient(patient: Patient) { col.document(patient.id).set(patient.toMap()).await() }
    override suspend fun archivePatient(id: String) { col.document(id).update("isArchived", true).await() }
    override suspend fun unarchivePatient(id: String) { col.document(id).update("isArchived", false).await() }
    override suspend fun searchPatients(query: String, includeArchived: Boolean): List<Patient> =
        getAllPatients(includeArchived).filter {
            it.name.contains(query, ignoreCase = true) || it.roomNo.contains(query, ignoreCase = true)
        }
}

// ─────────────────────────────────────────────────────────────────────────────
// Vitals
// ─────────────────────────────────────────────────────────────────────────────

private fun VitalRecord.toMap(): Map<String, Any?> = mapOf(
    "patientId" to patientId, "date" to date.toString(), "time" to time.toString(),
    "pulse" to pulse, "bp" to bp, "spo2" to spo2, "temperature" to temperature,
    "sugarFasting" to sugarFasting, "sugarPP" to sugarPP, "signedBy" to signedBy,
)
private fun vitalFrom(id: String, d: Map<String, Any?>) = VitalRecord(
    id = id, patientId = d.str("patientId"), date = d.date("date"), time = d.time("time"),
    pulse = d.str("pulse"), bp = d.str("bp"), spo2 = d.str("spo2"), temperature = d.str("temperature"),
    sugarFasting = d.str("sugarFasting"), sugarPP = d.str("sugarPP"), signedBy = d.str("signedBy"),
)

class FirestoreVitalsRepository(private val db: FirebaseFirestore) : VitalsRepository {
    private val col = db.collection("vitals")
    override suspend fun getVitalsForPatient(patientId: String): List<VitalRecord> =
        col.whereEqualTo("patientId", patientId).get().await().documents
            .mapNotNull { d -> d.data?.let { vitalFrom(d.id, it) } }
            .sortedByDescending { it.date }
    override suspend fun getVitalById(id: String): VitalRecord? {
        val doc = col.document(id).get().await()
        return doc.data?.let { vitalFrom(doc.id, it) }
    }
    override suspend fun addVital(record: VitalRecord) {
        val ref = col.document()
        ref.set(record.copy(id = ref.id).toMap()).await()
    }
    override suspend fun updateVital(record: VitalRecord) { col.document(record.id).set(record.toMap()).await() }
}

// ─────────────────────────────────────────────────────────────────────────────
// Medication (MAR)
// ─────────────────────────────────────────────────────────────────────────────

private fun MedicationEntry.toMap(): Map<String, Any?> = mapOf(
    "patientId" to patientId, "medicineName" to medicineName, "dose" to dose, "quantity" to quantity,
    "scheduleTime" to scheduleTime.toString(), "scheduledDate" to scheduledDate.toString(),
    "status" to status.name, "administeredBy" to administeredBy,
    "administeredAt" to administeredAt?.toString(), "notes" to notes,
    "allotmentStatus" to allotmentStatus.name, "allottedById" to allottedById, "allottedByName" to allottedByName,
    "allottedAt" to allottedAt?.toString(), "allotmentPhotoUrl" to allotmentPhotoUrl,
    "allotmentPhotoExpiresAt" to allotmentPhotoExpiresAt?.toString(),
    "administeredPhotoUrl" to administeredPhotoUrl,
    "administeredPhotoExpiresAt" to administeredPhotoExpiresAt?.toString(),
)
private fun medicationFrom(id: String, d: Map<String, Any?>): MedicationEntry {
    val entry = MedicationEntry(
        id = id, patientId = d.str("patientId"), medicineName = d.str("medicineName"), dose = d.str("dose"),
        quantity = d.str("quantity"), scheduleTime = d.time("scheduleTime"), scheduledDate = d.date("scheduledDate"),
        status = d.enum("status", MedStatus.PENDING), administeredBy = d.str("administeredBy"),
        administeredAt = d.dateTimeOrNull("administeredAt"), notes = d.str("notes"),
        allotmentStatus = d.enum("allotmentStatus", AllotmentStatus.NOT_ALLOTTED),
        allottedById = d.str("allottedById"), allottedByName = d.str("allottedByName"),
        allottedAt = d.dateTimeOrNull("allottedAt"), allotmentPhotoUrl = d.str("allotmentPhotoUrl"),
        allotmentPhotoExpiresAt = d.dateTimeOrNull("allotmentPhotoExpiresAt"),
        administeredPhotoUrl = d.str("administeredPhotoUrl"),
        administeredPhotoExpiresAt = d.dateTimeOrNull("administeredPhotoExpiresAt"),
    )
    return entry.withComputedStatus()
}

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

class FirestoreMedicationRepository(private val db: FirebaseFirestore) : MedicationRepository {
    private val col = db.collection("medications")
    override suspend fun getMedicationsForPatient(patientId: String, date: LocalDate): List<MedicationEntry> =
        col.whereEqualTo("patientId", patientId).get().await().documents
            .mapNotNull { d -> d.data?.let { medicationFrom(d.id, it) } }
            .filter { it.scheduledDate == date }
            .sortedBy { it.scheduleTime }
    override suspend fun getMedicationsForPatient(patientId: String): List<MedicationEntry> =
        col.whereEqualTo("patientId", patientId).get().await().documents
            .mapNotNull { d -> d.data?.let { medicationFrom(d.id, it) } }
            .sortedBy { it.scheduleTime }
    override suspend fun getMedicationsForDate(date: LocalDate): List<MedicationEntry> =
        getAllMedications().filter { it.scheduledDate == date }.sortedBy { it.scheduleTime }
    override suspend fun getAllMedications(): List<MedicationEntry> =
        col.get().await().documents.mapNotNull { d -> d.data?.let { medicationFrom(d.id, it) } }
            .sortedByDescending { it.scheduledDate }
    override suspend fun getMedicationById(id: String): MedicationEntry? {
        val doc = col.document(id).get().await()
        return doc.data?.let { medicationFrom(doc.id, it) }
    }
    override suspend fun addMedication(entry: MedicationEntry) {
        val ref = col.document()
        ref.set(entry.copy(id = ref.id).toMap()).await()
    }
    override suspend fun updateMedication(entry: MedicationEntry) { col.document(entry.id).set(entry.toMap()).await() }
    override suspend fun deleteMedication(id: String) { col.document(id).delete().await() }
    override suspend fun markAdministered(id: String, staffName: String, photoUrl: String, photoExpiresAt: LocalDateTime) {
        col.document(id).update(
            mapOf(
                "status" to MedStatus.ADMINISTERED.name,
                "administeredBy" to staffName,
                "administeredAt" to LocalDateTime.now().toString(),
                "administeredPhotoUrl" to photoUrl,
                "administeredPhotoExpiresAt" to photoExpiresAt.toString(),
            )
        ).await()
    }
    override suspend fun allotMedication(id: String, staffId: String, staffName: String, photoUrl: String, photoExpiresAt: LocalDateTime) {
        col.document(id).update(
            mapOf(
                "allotmentStatus" to AllotmentStatus.ALLOTTED.name,
                "allottedById" to staffId,
                "allottedByName" to staffName,
                "allottedAt" to LocalDateTime.now().toString(),
                "allotmentPhotoUrl" to photoUrl,
                "allotmentPhotoExpiresAt" to photoExpiresAt.toString(),
            )
        ).await()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Utility Records & Items
// ─────────────────────────────────────────────────────────────────────────────

private fun UtilityRecord.toMap(): Map<String, Any?> = mapOf(
    "patientId" to patientId, "date" to date.toString(), "time" to time.toString(),
    "quantities" to quantities, "issuedToCaregiver" to issuedToCaregiver,
    "issuedBySupervisor" to issuedBySupervisor, "checkedBy" to checkedBy,
)
private fun utilityRecordFrom(id: String, d: Map<String, Any?>) = UtilityRecord(
    id = id, patientId = d.str("patientId"), date = d.date("date"), time = d.time("time"),
    quantities = d.intMap("quantities"), issuedToCaregiver = d.str("issuedToCaregiver"),
    issuedBySupervisor = d.str("issuedBySupervisor"), checkedBy = d.str("checkedBy"),
)
private fun UtilityItem.toMap(): Map<String, Any?> = mapOf(
    "name" to name, "unit" to unit, "displayOrder" to displayOrder, "isActive" to isActive,
)
private fun utilityItemFrom(id: String, d: Map<String, Any?>) = UtilityItem(
    id = id, name = d.str("name"), unit = d.str("unit"), displayOrder = d.int("displayOrder"),
    isActive = d.bool("isActive", true),
)

class FirestoreUtilityRepository(private val db: FirebaseFirestore) : UtilityRepository {
    private val recordsCol = db.collection("utilityRecords")
    private val itemsCol = db.collection("utilityItems")
    override suspend fun getUtilityForPatient(patientId: String): List<UtilityRecord> =
        recordsCol.whereEqualTo("patientId", patientId).get().await().documents
            .mapNotNull { d -> d.data?.let { utilityRecordFrom(d.id, it) } }
            .sortedByDescending { it.date }
    override suspend fun getUtilityRecordById(id: String): UtilityRecord? {
        val doc = recordsCol.document(id).get().await()
        return doc.data?.let { utilityRecordFrom(doc.id, it) }
    }
    override suspend fun addUtilityRecord(record: UtilityRecord) {
        val ref = recordsCol.document()
        ref.set(record.copy(id = ref.id).toMap()).await()
    }
    override suspend fun updateUtilityRecord(record: UtilityRecord) {
        recordsCol.document(record.id).set(record.toMap()).await()
    }
    override suspend fun getUtilityItems(): List<UtilityItem> = getAllUtilityItems().filter { it.isActive }
    override suspend fun getAllUtilityItems(): List<UtilityItem> =
        itemsCol.get().await().documents.mapNotNull { d -> d.data?.let { utilityItemFrom(d.id, it) } }
            .sortedBy { it.displayOrder }
    override suspend fun addUtilityItem(item: UtilityItem) {
        val ref = itemsCol.document()
        ref.set(item.copy(id = ref.id).toMap()).await()
    }
    override suspend fun deleteUtilityItem(id: String) { itemsCol.document(id).update("isActive", false).await() }
}

// ─────────────────────────────────────────────────────────────────────────────
// Doctor Visits & Care Notes
// ─────────────────────────────────────────────────────────────────────────────

private fun DoctorVisit.toMap(): Map<String, Any?> = mapOf(
    "patientId" to patientId, "doctorName" to doctorName, "specialty" to specialty,
    "date" to date.toString(), "time" to time.toString(), "notes" to notes,
    "nextVisitDate" to nextVisitDate?.toString(), "prescriptionChanges" to prescriptionChanges,
    "isConfirmed" to isConfirmed, "isArchived" to isArchived,
)
private fun doctorVisitFrom(id: String, d: Map<String, Any?>) = DoctorVisit(
    id = id, patientId = d.str("patientId"), doctorName = d.str("doctorName"), specialty = d.str("specialty"),
    date = d.date("date"), time = d.time("time"), notes = d.str("notes"),
    nextVisitDate = d.dateOrNull("nextVisitDate"), prescriptionChanges = d.str("prescriptionChanges"),
    isConfirmed = d.bool("isConfirmed"), isArchived = d.bool("isArchived"),
)

class FirestoreDoctorVisitRepository(private val db: FirebaseFirestore) : DoctorVisitRepository {
    private val col = db.collection("doctorVisits")
    override suspend fun getVisitsForPatient(patientId: String): List<DoctorVisit> =
        col.whereEqualTo("patientId", patientId).get().await().documents
            .mapNotNull { d -> d.data?.let { doctorVisitFrom(d.id, it) } }
            .sortedByDescending { it.date }
    override suspend fun getVisitById(id: String): DoctorVisit? {
        val doc = col.document(id).get().await()
        return doc.data?.let { doctorVisitFrom(doc.id, it) }
    }
    override suspend fun addVisit(visit: DoctorVisit) {
        val ref = col.document()
        ref.set(visit.copy(id = ref.id).toMap()).await()
    }
    override suspend fun updateVisit(visit: DoctorVisit) { col.document(visit.id).set(visit.toMap()).await() }
    override suspend fun deleteVisit(id: String) { col.document(id).delete().await() }
}

private fun CareNote.toMap(): Map<String, Any?> = mapOf(
    "patientId" to patientId, "staffId" to staffId, "staffName" to staffName,
    "timestamp" to timestamp.toString(), "note" to note,
)
private fun careNoteFrom(id: String, d: Map<String, Any?>) = CareNote(
    id = id, patientId = d.str("patientId"), staffId = d.str("staffId"), staffName = d.str("staffName"),
    timestamp = d.dateTime("timestamp"), note = d.str("note"),
)

class FirestoreCareNoteRepository(private val db: FirebaseFirestore) : CareNoteRepository {
    private val col = db.collection("careNotes")
    override suspend fun getNotesForPatient(patientId: String): List<CareNote> =
        col.whereEqualTo("patientId", patientId).get().await().documents
            .mapNotNull { d -> d.data?.let { careNoteFrom(d.id, it) } }
            .sortedByDescending { it.timestamp }
    override suspend fun addNote(note: CareNote) {
        val ref = col.document()
        ref.set(note.copy(id = ref.id).toMap()).await()
    }
    override suspend fun updateNote(note: CareNote) { col.document(note.id).set(note.toMap()).await() }
    override suspend fun getNoteById(id: String): CareNote? {
        val doc = col.document(id).get().await()
        return doc.data?.let { careNoteFrom(doc.id, it) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Approval Queue
// ─────────────────────────────────────────────────────────────────────────────

private fun ApprovalRequest.toMap(): Map<String, Any?> = mapOf(
    "entityType" to entityType.name, "entityId" to entityId, "action" to action.name,
    "patientId" to patientId, "patientName" to patientName, "requestedById" to requestedById,
    "requestedByName" to requestedByName, "fieldChanged" to fieldChanged, "oldValue" to oldValue,
    "newValue" to newValue, "status" to status.name, "reviewedById" to reviewedById,
    "reviewedByName" to reviewedByName, "timestamp" to timestamp.toString(),
    "reviewedAt" to reviewedAt?.toString(), "rejectionReason" to rejectionReason,
)
private fun approvalRequestFrom(id: String, d: Map<String, Any?>) = ApprovalRequest(
    id = id, entityType = d.enum("entityType", ApprovalEntityType.PATIENT), entityId = d.str("entityId"),
    action = d.enum("action", ApprovalAction.EDIT), patientId = d.str("patientId"), patientName = d.str("patientName"),
    requestedById = d.str("requestedById"), requestedByName = d.str("requestedByName"),
    fieldChanged = d.str("fieldChanged"), oldValue = d.str("oldValue"), newValue = d.str("newValue"),
    status = d.enum("status", ApprovalStatus.PENDING), reviewedById = d.str("reviewedById"),
    reviewedByName = d.str("reviewedByName"), timestamp = d.dateTime("timestamp"),
    reviewedAt = d.dateTimeOrNull("reviewedAt"), rejectionReason = d.str("rejectionReason"),
)

class FirestoreApprovalRepository(private val db: FirebaseFirestore) : ApprovalRepository {
    private val col = db.collection("approvalRequests")
    override suspend fun getAllRequests(): List<ApprovalRequest> =
        col.get().await().documents.mapNotNull { d -> d.data?.let { approvalRequestFrom(d.id, it) } }
            .sortedByDescending { it.timestamp }
    override suspend fun getPendingRequests(): List<ApprovalRequest> =
        getAllRequests().filter { it.status == ApprovalStatus.PENDING }
    override suspend fun getRequestById(id: String): ApprovalRequest? {
        val doc = col.document(id).get().await()
        return doc.data?.let { approvalRequestFrom(doc.id, it) }
    }
    override suspend fun approve(id: String, reviewerId: String, reviewerName: String) {
        col.document(id).update(
            mapOf(
                "status" to ApprovalStatus.APPROVED.name,
                "reviewedById" to reviewerId,
                "reviewedByName" to reviewerName,
                "reviewedAt" to LocalDateTime.now().toString(),
            )
        ).await()
    }
    override suspend fun reject(id: String, reviewerId: String, reviewerName: String, reason: String) {
        col.document(id).update(
            mapOf(
                "status" to ApprovalStatus.REJECTED.name,
                "reviewedById" to reviewerId,
                "reviewedByName" to reviewerName,
                "reviewedAt" to LocalDateTime.now().toString(),
                "rejectionReason" to reason,
            )
        ).await()
    }
    override suspend fun submitRequest(request: ApprovalRequest) {
        val ref = col.document()
        ref.set(request.copy(id = ref.id).toMap()).await()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Allotment Requests
// ─────────────────────────────────────────────────────────────────────────────

private fun AllotmentRequest.toMap(): Map<String, Any?> = mapOf(
    "medicationEntryId" to medicationEntryId, "patientId" to patientId, "patientName" to patientName,
    "medicineName" to medicineName, "dose" to dose, "scheduledTime" to scheduledTime.toString(),
    "requestedById" to requestedById, "requestedByName" to requestedByName, "status" to status.name,
    "fulfilledById" to fulfilledById, "fulfilledByName" to fulfilledByName,
    "timestamp" to timestamp.toString(), "fulfilledAt" to fulfilledAt?.toString(),
)
private fun allotmentRequestFrom(id: String, d: Map<String, Any?>) = AllotmentRequest(
    id = id, medicationEntryId = d.str("medicationEntryId"), patientId = d.str("patientId"),
    patientName = d.str("patientName"), medicineName = d.str("medicineName"), dose = d.str("dose"),
    scheduledTime = d.time("scheduledTime"), requestedById = d.str("requestedById"),
    requestedByName = d.str("requestedByName"), status = d.enum("status", AllotmentRequestStatus.PENDING),
    fulfilledById = d.str("fulfilledById"), fulfilledByName = d.str("fulfilledByName"),
    timestamp = d.dateTime("timestamp"), fulfilledAt = d.dateTimeOrNull("fulfilledAt"),
)

class FirestoreAllotmentRequestRepository(private val db: FirebaseFirestore) : AllotmentRequestRepository {
    private val col = db.collection("allotmentRequests")
    override suspend fun getAllRequests(): List<AllotmentRequest> =
        col.get().await().documents.mapNotNull { d -> d.data?.let { allotmentRequestFrom(d.id, it) } }
            .sortedByDescending { it.timestamp }
    override suspend fun getPendingRequests(): List<AllotmentRequest> =
        getAllRequests().filter { it.status == AllotmentRequestStatus.PENDING }
    override suspend fun submitRequest(request: AllotmentRequest) {
        val ref = col.document()
        ref.set(request.copy(id = ref.id).toMap()).await()
    }
    override suspend fun fulfillRequest(id: String, staffId: String, staffName: String) {
        col.document(id).update(
            mapOf(
                "status" to AllotmentRequestStatus.FULFILLED.name,
                "fulfilledById" to staffId,
                "fulfilledByName" to staffName,
                "fulfilledAt" to LocalDateTime.now().toString(),
            )
        ).await()
    }
    override suspend fun getByMedicationEntryId(medicationEntryId: String): AllotmentRequest? =
        getPendingRequests().firstOrNull { it.medicationEntryId == medicationEntryId }
}

// ─────────────────────────────────────────────────────────────────────────────
// Notifications
// ─────────────────────────────────────────────────────────────────────────────

private fun AppNotification.toMap(): Map<String, Any?> = mapOf(
    "recipientStaffId" to recipientStaffId, "recipientRole" to recipientRole?.name,
    "type" to type.name, "title" to title, "message" to message,
    "timestamp" to timestamp.toString(), "isRead" to isRead, "targetRoute" to targetRoute,
)
private fun notificationFrom(id: String, d: Map<String, Any?>) = AppNotification(
    id = id, recipientStaffId = d.str("recipientStaffId"), recipientRole = d.enumOrNull("recipientRole"),
    type = d.enum("type", NotificationType.APPROVAL_REQUESTED), title = d.str("title"), message = d.str("message"),
    timestamp = d.dateTime("timestamp"), isRead = d.bool("isRead"), targetRoute = d.str("targetRoute"),
)

class FirestoreNotificationRepository(private val db: FirebaseFirestore) : NotificationRepository {
    private val col = db.collection("notifications")
    private fun matches(n: AppNotification, staffId: String, role: UserRole) =
        (n.recipientStaffId.isNotEmpty() && n.recipientStaffId == staffId) ||
        (n.recipientRole != null && n.recipientRole == role)
    override suspend fun getForRecipient(staffId: String, role: UserRole): List<AppNotification> =
        col.get().await().documents.mapNotNull { d -> d.data?.let { notificationFrom(d.id, it) } }
            .filter { matches(it, staffId, role) }
            .sortedByDescending { it.timestamp }
    override suspend fun getUnreadCountForRecipient(staffId: String, role: UserRole): Int =
        getForRecipient(staffId, role).count { !it.isRead }
    override suspend fun add(notification: AppNotification) {
        val ref = col.document()
        ref.set(notification.copy(id = ref.id).toMap()).await()
    }
    override suspend fun markRead(id: String) { col.document(id).update("isRead", true).await() }
    override suspend fun markAllReadForRecipient(staffId: String, role: UserRole) {
        val unread = getForRecipient(staffId, role).filter { !it.isRead }
        unread.forEach { col.document(it.id).update("isRead", true).await() }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Audit Log
// ─────────────────────────────────────────────────────────────────────────────

private fun AuditLogEntry.toMap(): Map<String, Any?> = mapOf(
    "action" to action, "performedById" to performedById, "performedByName" to performedByName,
    "targetPatientId" to targetPatientId, "targetPatientName" to targetPatientName,
    "details" to details, "timestamp" to timestamp.toString(), "iconName" to iconName,
)
private fun auditLogFrom(id: String, d: Map<String, Any?>) = AuditLogEntry(
    id = id, action = d.str("action"), performedById = d.str("performedById"),
    performedByName = d.str("performedByName"), targetPatientId = d.str("targetPatientId"),
    targetPatientName = d.str("targetPatientName"), details = d.str("details"),
    timestamp = d.dateTime("timestamp"), iconName = d.str("iconName").ifBlank { "edit" },
)

class FirestoreAuditRepository(private val db: FirebaseFirestore) : AuditRepository {
    private val col = db.collection("auditLog")
    override suspend fun getAllLogs(): List<AuditLogEntry> =
        col.get().await().documents.mapNotNull { d -> d.data?.let { auditLogFrom(d.id, it) } }
            .sortedByDescending { it.timestamp }
    override suspend fun addLog(entry: AuditLogEntry) {
        val ref = col.document()
        ref.set(entry.copy(id = ref.id).toMap()).await()
    }
}
