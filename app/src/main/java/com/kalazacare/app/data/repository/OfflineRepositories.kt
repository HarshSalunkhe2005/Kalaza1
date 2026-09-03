package com.kalazacare.app.data.repository

import com.kalazacare.app.data.local.CachedRowDao
import com.kalazacare.app.data.local.CachedRowEntity
import com.kalazacare.app.data.model.*
import com.kalazacare.app.data.sync.*
import com.kalazacare.app.util.ConnectivityObserver
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.time.LocalDate
import java.util.UUID

/**
 * Every Offline*Repository below follows the same shape: reads go to Supabase and
 * write through to [CachedRowDao] when online, or fall back to the cache when
 * offline; appends and mutations call Supabase directly when online (unchanged
 * behavior), or apply optimistically to the cache and enqueue a [SyncManager]
 * replay job when offline. Table name constants match the live schema documented
 * in context/project_state_and_workflows.md.
 */
private object Tables {
    const val PATIENTS = "patients"
    const val VITALS = "vitals"
    const val UTILITY_RECORDS = "utility_records"
    const val UTILITY_ITEMS = "utility_items"
    const val DOCTOR_VISITS = "doctor_visits"
    const val CARE_NOTES = "care_notes"
    const val APPROVAL_REQUESTS = "approval_requests"
    const val ALLOTMENT_REQUESTS = "allotment_requests"
    const val NOTIFICATIONS = "notifications"
    const val AUDIT_LOG = "audit_log"
    const val STAFF = "staff"
}

private fun newLocalId() = UUID.randomUUID().toString()

private suspend inline fun <reified T> CachedRowDao.upsertRow(table: String, id: String, row: T) =
    upsert(CachedRowEntity(table, id, syncJson.encodeToString(row), System.currentTimeMillis()))

private suspend inline fun <reified T> CachedRowDao.readRow(table: String, id: String): T? =
    getById(table, id)?.let { syncJson.decodeFromString<T>(it.json) }

private suspend inline fun <reified T> CachedRowDao.readAllRows(table: String): List<T> =
    getAll(table).map { syncJson.decodeFromString<T>(it.json) }

// ─────────────────────────────────────────────────────────────────────────────
// Patients
// ─────────────────────────────────────────────────────────────────────────────

class OfflinePatientRepository(
    private val remote: PatientRepository,
    private val cache: CachedRowDao,
    private val connectivity: ConnectivityObserver,
    private val sync: SyncManager,
) : PatientRepository {
    override suspend fun getAllPatients(includeArchived: Boolean): List<Patient> {
        if (connectivity.isOnline.value) {
            val patients = remote.getAllPatients(includeArchived = true)
            patients.forEach { cache.upsertRow(Tables.PATIENTS, it.id, it.toRow()) }
            return if (includeArchived) patients.sortedBy { it.name } else patients.filter { !it.isArchived }.sortedBy { it.name }
        }
        val all = cache.readAllRows<PatientRow>(Tables.PATIENTS).map { it.toDomain() }
        return (if (includeArchived) all else all.filter { !it.isArchived }).sortedBy { it.name }
    }

    override suspend fun getPatientById(id: String): Patient? {
        if (connectivity.isOnline.value) {
            return remote.getPatientById(id)?.also { cache.upsertRow(Tables.PATIENTS, it.id, it.toRow()) }
        }
        return cache.readRow<PatientRow>(Tables.PATIENTS, id)?.toDomain()
    }

    override suspend fun addPatient(patient: Patient): Patient {
        if (connectivity.isOnline.value) {
            val saved = remote.addPatient(patient)
            cache.upsertRow(Tables.PATIENTS, saved.id, saved.toRow())
            return saved
        }
        val saved = patient.copy(id = newLocalId())
        cache.upsertRow(Tables.PATIENTS, saved.id, saved.toRow())
        sync.enqueue(PendingOpType.ADD_PATIENT, syncJson.encodeToString(saved.toRow()))
        return saved
    }

    override suspend fun updatePatient(patient: Patient) {
        if (connectivity.isOnline.value) {
            remote.updatePatient(patient)
            cache.upsertRow(Tables.PATIENTS, patient.id, patient.toRow())
            return
        }
        val oldJson = cache.getById(Tables.PATIENTS, patient.id)?.json
            ?: syncJson.encodeToString(patient.toRow())
        cache.upsertRow(Tables.PATIENTS, patient.id, patient.toRow())
        sync.enqueue(PendingOpType.EDIT_PATIENT, syncJson.encodeToString(EditPayload(patient.id, oldJson, syncJson.encodeToString(patient.toRow()))))
    }

    override suspend fun archivePatient(id: String) {
        if (connectivity.isOnline.value) {
            remote.archivePatient(id)
            cache.readRow<PatientRow>(Tables.PATIENTS, id)?.let { cache.upsertRow(Tables.PATIENTS, id, it.copy(isArchived = true)) }
            return
        }
        cache.readRow<PatientRow>(Tables.PATIENTS, id)?.let { cache.upsertRow(Tables.PATIENTS, id, it.copy(isArchived = true)) }
        sync.enqueue(PendingOpType.ARCHIVE_PATIENT, syncJson.encodeToString(IdPayload(id)))
    }

    override suspend fun unarchivePatient(id: String) {
        // Rare admin-console action, not part of the offline write set — requires connectivity.
        remote.unarchivePatient(id)
        cache.readRow<PatientRow>(Tables.PATIENTS, id)?.let { cache.upsertRow(Tables.PATIENTS, id, it.copy(isArchived = false)) }
    }

    override suspend fun searchPatients(query: String, includeArchived: Boolean): List<Patient> =
        getAllPatients(includeArchived).filter {
            it.name.contains(query, ignoreCase = true) || it.roomNo.contains(query, ignoreCase = true)
        }
}

// ─────────────────────────────────────────────────────────────────────────────
// Vitals
// ─────────────────────────────────────────────────────────────────────────────

class OfflineVitalsRepository(
    private val remote: VitalsRepository,
    private val cache: CachedRowDao,
    private val connectivity: ConnectivityObserver,
    private val sync: SyncManager,
) : VitalsRepository {
    override suspend fun getVitalsForPatient(patientId: String): List<VitalRecord> {
        if (connectivity.isOnline.value) {
            val records = remote.getVitalsForPatient(patientId)
            records.forEach { cache.upsertRow(Tables.VITALS, it.id, it.toRow()) }
            return records
        }
        return cache.readAllRows<VitalRow>(Tables.VITALS).map { it.toDomain() }
            .filter { it.patientId == patientId }.sortedByDescending { it.date }
    }

    override suspend fun getVitalsForDate(date: LocalDate): List<VitalRecord> =
        if (connectivity.isOnline.value) remote.getVitalsForDate(date)
        else cache.readAllRows<VitalRow>(Tables.VITALS).map { it.toDomain() }.filter { it.date == date }

    override suspend fun getVitalById(id: String): VitalRecord? =
        if (connectivity.isOnline.value) remote.getVitalById(id)
        else cache.readRow<VitalRow>(Tables.VITALS, id)?.toDomain()

    override suspend fun addVital(record: VitalRecord) {
        if (connectivity.isOnline.value) {
            remote.addVital(record)
            return
        }
        val saved = record.copy(id = newLocalId())
        cache.upsertRow(Tables.VITALS, saved.id, saved.toRow())
        sync.enqueue(PendingOpType.ADD_VITAL, syncJson.encodeToString(saved.toRow()))
    }

    override suspend fun updateVital(record: VitalRecord) {
        if (connectivity.isOnline.value) {
            remote.updateVital(record)
            cache.upsertRow(Tables.VITALS, record.id, record.toRow())
            return
        }
        val oldJson = cache.getById(Tables.VITALS, record.id)?.json ?: syncJson.encodeToString(record.toRow())
        cache.upsertRow(Tables.VITALS, record.id, record.toRow())
        sync.enqueue(PendingOpType.EDIT_VITAL, syncJson.encodeToString(EditPayload(record.id, oldJson, syncJson.encodeToString(record.toRow()))))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Medication — reads cached like everything else; add/edit/delete of MAR
// entries is a Super-Admin-only, desk-side action and stays online-only
// (never queued). Mark-given / allot are the two offline-safety-critical
// mutations, so those get the full conflict-checked queue treatment.
// ─────────────────────────────────────────────────────────────────────────────

class OfflineMedicationRepository(
    private val remote: MedicationRepository,
    private val cache: CachedRowDao,
    private val connectivity: ConnectivityObserver,
    private val sync: SyncManager,
) : MedicationRepository {
    private val table = "medications"

    override suspend fun getMedicationsForPatient(patientId: String, date: LocalDate): List<MedicationEntry> {
        if (connectivity.isOnline.value) {
            val meds = remote.getMedicationsForPatient(patientId, date)
            meds.forEach { cache.upsertRow(table, it.id, it.toRow()) }
            return meds
        }
        return cache.readAllRows<MedicationRow>(table).map { it.toDomain() }
            .filter { it.patientId == patientId }.sortedBy { it.scheduleTime }
    }

    override suspend fun getMedicationsForPatient(patientId: String): List<MedicationEntry> =
        if (connectivity.isOnline.value) remote.getMedicationsForPatient(patientId)
        else cache.readAllRows<MedicationRow>(table).map { it.toDomain() }
            .filter { it.patientId == patientId }.sortedBy { it.scheduleTime }

    override suspend fun getMedicationsForPatientAndTag(patientId: String, tag: DoseTag, date: LocalDate): List<MedicationEntry> =
        getMedicationsForPatient(patientId, date).filter { it.tag == tag }

    override suspend fun getMedicationsForDate(date: LocalDate): List<MedicationEntry> =
        if (connectivity.isOnline.value) remote.getMedicationsForDate(date)
        else cache.readAllRows<MedicationRow>(table).map { it.toDomain() }.sortedBy { it.scheduleTime }

    override suspend fun getMedicationById(id: String): MedicationEntry? =
        if (connectivity.isOnline.value) remote.getMedicationById(id)?.also { cache.upsertRow(table, it.id, it.toRow()) }
        else cache.readRow<MedicationRow>(table, id)?.toDomain()

    override suspend fun addMedication(entry: MedicationEntry) = remote.addMedication(entry) // Super-Admin desk action, online-only
    override suspend fun updateMedication(entry: MedicationEntry) = remote.updateMedication(entry)
    override suspend fun deleteMedication(id: String) = remote.deleteMedication(id)

    override suspend fun markAdministered(id: String, staffName: String, scannedCode: String) {
        if (connectivity.isOnline.value) {
            remote.markAdministered(id, staffName, scannedCode)
            return
        }
        cache.readRow<MedicationRow>(table, id)?.let {
            cache.upsertRow(table, id, it.copy(
                status = MedStatus.ADMINISTERED.name, administeredBy = staffName,
                administeredAt = java.time.LocalDateTime.now().toString(), administeredScannedCode = scannedCode,
            ))
        }
        sync.enqueue(PendingOpType.MED_MARK_ADMINISTERED, syncJson.encodeToString(MarkAdministeredPayload(id, staffName, scannedCode)))
    }

    override suspend fun allotMedication(id: String, staffId: String, staffName: String, scannedCode: String) {
        if (connectivity.isOnline.value) {
            remote.allotMedication(id, staffId, staffName, scannedCode)
            return
        }
        cache.readRow<MedicationRow>(table, id)?.let {
            cache.upsertRow(table, id, it.copy(
                allotmentStatus = AllotmentStatus.ALLOTTED.name, allottedById = staffId, allottedByName = staffName,
                allottedAt = java.time.LocalDateTime.now().toString(), allotmentScannedCode = scannedCode,
            ))
        }
        sync.enqueue(PendingOpType.MED_ALLOT, syncJson.encodeToString(AllotPayload(id, staffId, staffName, scannedCode)))
    }

    // Compliance report — not cached; unavailable while offline (an empty result, not a crash).
    override suspend fun getEvidenceLog(): List<MedicationEvidenceEvent> =
        if (connectivity.isOnline.value) remote.getEvidenceLog() else emptyList()
}

// ─────────────────────────────────────────────────────────────────────────────
// Utility Records & Items
// ─────────────────────────────────────────────────────────────────────────────

class OfflineUtilityRepository(
    private val remote: UtilityRepository,
    private val cache: CachedRowDao,
    private val connectivity: ConnectivityObserver,
    private val sync: SyncManager,
) : UtilityRepository {
    override suspend fun getUtilityForPatient(patientId: String): List<UtilityRecord> {
        if (connectivity.isOnline.value) {
            val records = remote.getUtilityForPatient(patientId)
            records.forEach { cache.upsertRow(Tables.UTILITY_RECORDS, it.id, it.toRow()) }
            return records
        }
        return cache.readAllRows<UtilityRecordRow>(Tables.UTILITY_RECORDS).map { it.toDomain() }
            .filter { it.patientId == patientId }.sortedByDescending { it.date }
    }

    override suspend fun getUtilityForDate(date: LocalDate): List<UtilityRecord> =
        if (connectivity.isOnline.value) remote.getUtilityForDate(date)
        else cache.readAllRows<UtilityRecordRow>(Tables.UTILITY_RECORDS).map { it.toDomain() }.filter { it.date == date }

    override suspend fun getUtilityRecordById(id: String): UtilityRecord? =
        if (connectivity.isOnline.value) remote.getUtilityRecordById(id)
        else cache.readRow<UtilityRecordRow>(Tables.UTILITY_RECORDS, id)?.toDomain()

    override suspend fun addUtilityRecord(record: UtilityRecord) {
        if (connectivity.isOnline.value) { remote.addUtilityRecord(record); return }
        val saved = record.copy(id = newLocalId())
        cache.upsertRow(Tables.UTILITY_RECORDS, saved.id, saved.toRow())
        sync.enqueue(PendingOpType.ADD_UTILITY_RECORD, syncJson.encodeToString(saved.toRow()))
    }

    override suspend fun updateUtilityRecord(record: UtilityRecord) {
        if (connectivity.isOnline.value) {
            remote.updateUtilityRecord(record)
            cache.upsertRow(Tables.UTILITY_RECORDS, record.id, record.toRow())
            return
        }
        val oldJson = cache.getById(Tables.UTILITY_RECORDS, record.id)?.json ?: syncJson.encodeToString(record.toRow())
        cache.upsertRow(Tables.UTILITY_RECORDS, record.id, record.toRow())
        sync.enqueue(PendingOpType.EDIT_UTILITY, syncJson.encodeToString(EditPayload(record.id, oldJson, syncJson.encodeToString(record.toRow()))))
    }

    // Config-time item catalog management — desk-side admin action, online-only.
    override suspend fun getUtilityItems(): List<UtilityItem> = getAllUtilityItems().filter { it.isActive }
    override suspend fun getAllUtilityItems(): List<UtilityItem> {
        if (connectivity.isOnline.value) {
            val items = remote.getAllUtilityItems()
            items.forEach { cache.upsertRow(Tables.UTILITY_ITEMS, it.id, it.toRow()) }
            return items
        }
        return cache.readAllRows<UtilityItemRow>(Tables.UTILITY_ITEMS).map { it.toDomain() }.sortedBy { it.displayOrder }
    }
    override suspend fun addUtilityItem(item: UtilityItem) = remote.addUtilityItem(item)
    override suspend fun updateUtilityItem(item: UtilityItem) = remote.updateUtilityItem(item)
    override suspend fun deleteUtilityItem(id: String) = remote.deleteUtilityItem(id)
}

// ─────────────────────────────────────────────────────────────────────────────
// Doctor Visits
// ─────────────────────────────────────────────────────────────────────────────

class OfflineDoctorVisitRepository(
    private val remote: DoctorVisitRepository,
    private val cache: CachedRowDao,
    private val connectivity: ConnectivityObserver,
    private val sync: SyncManager,
) : DoctorVisitRepository {
    override suspend fun getVisitsForPatient(patientId: String): List<DoctorVisit> {
        if (connectivity.isOnline.value) {
            val visits = remote.getVisitsForPatient(patientId)
            visits.forEach { cache.upsertRow(Tables.DOCTOR_VISITS, it.id, it.toRow()) }
            return visits
        }
        return cache.readAllRows<DoctorVisitRow>(Tables.DOCTOR_VISITS).map { it.toDomain() }
            .filter { it.patientId == patientId }.sortedByDescending { it.date }
    }

    override suspend fun getVisitsForDate(date: LocalDate): List<DoctorVisit> =
        if (connectivity.isOnline.value) remote.getVisitsForDate(date)
        else cache.readAllRows<DoctorVisitRow>(Tables.DOCTOR_VISITS).map { it.toDomain() }.filter { it.date == date }

    override suspend fun getVisitById(id: String): DoctorVisit? =
        if (connectivity.isOnline.value) remote.getVisitById(id)
        else cache.readRow<DoctorVisitRow>(Tables.DOCTOR_VISITS, id)?.toDomain()

    override suspend fun addVisit(visit: DoctorVisit) {
        if (connectivity.isOnline.value) { remote.addVisit(visit); return }
        val saved = visit.copy(id = newLocalId())
        cache.upsertRow(Tables.DOCTOR_VISITS, saved.id, saved.toRow())
        sync.enqueue(PendingOpType.ADD_DOCTOR_VISIT, syncJson.encodeToString(saved.toRow()))
    }

    override suspend fun updateVisit(visit: DoctorVisit) {
        if (connectivity.isOnline.value) {
            remote.updateVisit(visit)
            cache.upsertRow(Tables.DOCTOR_VISITS, visit.id, visit.toRow())
            return
        }
        val oldJson = cache.getById(Tables.DOCTOR_VISITS, visit.id)?.json ?: syncJson.encodeToString(visit.toRow())
        cache.upsertRow(Tables.DOCTOR_VISITS, visit.id, visit.toRow())
        sync.enqueue(PendingOpType.EDIT_DOCTOR_VISIT, syncJson.encodeToString(EditPayload(visit.id, oldJson, syncJson.encodeToString(visit.toRow()))))
    }

    override suspend fun deleteVisit(id: String) {
        if (connectivity.isOnline.value) {
            remote.deleteVisit(id)
            cache.delete(Tables.DOCTOR_VISITS, id)
            return
        }
        cache.delete(Tables.DOCTOR_VISITS, id)
        sync.enqueue(PendingOpType.DELETE_DOCTOR_VISIT, syncJson.encodeToString(IdPayload(id)))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Care Notes
// ─────────────────────────────────────────────────────────────────────────────

class OfflineCareNoteRepository(
    private val remote: CareNoteRepository,
    private val cache: CachedRowDao,
    private val connectivity: ConnectivityObserver,
    private val sync: SyncManager,
) : CareNoteRepository {
    override suspend fun getNotesForPatient(patientId: String): List<CareNote> {
        if (connectivity.isOnline.value) {
            val notes = remote.getNotesForPatient(patientId)
            notes.forEach { cache.upsertRow(Tables.CARE_NOTES, it.id, it.toRow()) }
            return notes
        }
        return cache.readAllRows<CareNoteRow>(Tables.CARE_NOTES).map { it.toDomain() }
            .filter { it.patientId == patientId }.sortedByDescending { it.timestamp }
    }

    override suspend fun addNote(note: CareNote) {
        if (connectivity.isOnline.value) { remote.addNote(note); return }
        val saved = note.copy(id = newLocalId())
        cache.upsertRow(Tables.CARE_NOTES, saved.id, saved.toRow())
        sync.enqueue(PendingOpType.ADD_CARE_NOTE, syncJson.encodeToString(saved.toRow()))
    }

    override suspend fun updateNote(note: CareNote) {
        if (connectivity.isOnline.value) {
            remote.updateNote(note)
            cache.upsertRow(Tables.CARE_NOTES, note.id, note.toRow())
            return
        }
        val oldJson = cache.getById(Tables.CARE_NOTES, note.id)?.json ?: syncJson.encodeToString(note.toRow())
        cache.upsertRow(Tables.CARE_NOTES, note.id, note.toRow())
        sync.enqueue(PendingOpType.EDIT_CARE_NOTE, syncJson.encodeToString(EditPayload(note.id, oldJson, syncJson.encodeToString(note.toRow()))))
    }

    override suspend fun getNoteById(id: String): CareNote? =
        if (connectivity.isOnline.value) remote.getNoteById(id)
        else cache.readRow<CareNoteRow>(Tables.CARE_NOTES, id)?.toDomain()
}

// ─────────────────────────────────────────────────────────────────────────────
// Approval Queue
// ─────────────────────────────────────────────────────────────────────────────

class OfflineApprovalRepository(
    private val remote: ApprovalRepository,
    private val cache: CachedRowDao,
    private val connectivity: ConnectivityObserver,
    private val sync: SyncManager,
) : ApprovalRepository {
    override suspend fun getAllRequests(): List<ApprovalRequest> {
        if (connectivity.isOnline.value) {
            val all = remote.getAllRequests()
            all.forEach { cache.upsertRow(Tables.APPROVAL_REQUESTS, it.id, it.toRow()) }
            return all
        }
        return cache.readAllRows<ApprovalRequestRow>(Tables.APPROVAL_REQUESTS).map { it.toDomain() }.sortedByDescending { it.timestamp }
    }

    override suspend fun getPendingRequests(): List<ApprovalRequest> =
        if (connectivity.isOnline.value) remote.getPendingRequests()
        else getAllRequests().filter { it.status == ApprovalStatus.PENDING }

    override suspend fun getRequestById(id: String): ApprovalRequest? =
        if (connectivity.isOnline.value) remote.getRequestById(id)
        else cache.readRow<ApprovalRequestRow>(Tables.APPROVAL_REQUESTS, id)?.toDomain()

    override suspend fun approve(id: String, reviewerId: String, reviewerName: String) {
        if (connectivity.isOnline.value) { remote.approve(id, reviewerId, reviewerName); return }
        cache.readRow<ApprovalRequestRow>(Tables.APPROVAL_REQUESTS, id)?.let {
            cache.upsertRow(Tables.APPROVAL_REQUESTS, id, it.copy(status = ApprovalStatus.APPROVED.name, reviewedById = reviewerId, reviewedByName = reviewerName))
        }
        sync.enqueue(PendingOpType.APPROVAL_REVIEW, syncJson.encodeToString(ApprovalReviewPayload(id, reviewerId, reviewerName, approve = true)))
    }

    override suspend fun reject(id: String, reviewerId: String, reviewerName: String, reason: String) {
        if (connectivity.isOnline.value) { remote.reject(id, reviewerId, reviewerName, reason); return }
        cache.readRow<ApprovalRequestRow>(Tables.APPROVAL_REQUESTS, id)?.let {
            cache.upsertRow(Tables.APPROVAL_REQUESTS, id, it.copy(status = ApprovalStatus.REJECTED.name, reviewedById = reviewerId, reviewedByName = reviewerName, rejectionReason = reason))
        }
        sync.enqueue(PendingOpType.APPROVAL_REVIEW, syncJson.encodeToString(ApprovalReviewPayload(id, reviewerId, reviewerName, approve = false, reason = reason)))
    }

    override suspend fun submitRequest(request: ApprovalRequest) {
        if (connectivity.isOnline.value) { remote.submitRequest(request); return }
        val saved = request.copy(id = newLocalId())
        cache.upsertRow(Tables.APPROVAL_REQUESTS, saved.id, saved.toRow())
        sync.enqueue(PendingOpType.SUBMIT_APPROVAL_REQUEST, syncJson.encodeToString(saved.toRow()))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Allotment Requests
// ─────────────────────────────────────────────────────────────────────────────

class OfflineAllotmentRequestRepository(
    private val remote: AllotmentRequestRepository,
    private val cache: CachedRowDao,
    private val connectivity: ConnectivityObserver,
    private val sync: SyncManager,
) : AllotmentRequestRepository {
    override suspend fun getAllRequests(): List<AllotmentRequest> {
        if (connectivity.isOnline.value) {
            val all = remote.getAllRequests()
            all.forEach { cache.upsertRow(Tables.ALLOTMENT_REQUESTS, it.id, it.toRow()) }
            return all
        }
        return cache.readAllRows<AllotmentRequestRow>(Tables.ALLOTMENT_REQUESTS).map { it.toDomain() }.sortedByDescending { it.timestamp }
    }

    override suspend fun getPendingRequests(): List<AllotmentRequest> =
        if (connectivity.isOnline.value) remote.getPendingRequests()
        else getAllRequests().filter { it.status == AllotmentRequestStatus.PENDING }

    override suspend fun submitRequest(request: AllotmentRequest) {
        if (connectivity.isOnline.value) { remote.submitRequest(request); return }
        val saved = request.copy(id = newLocalId())
        cache.upsertRow(Tables.ALLOTMENT_REQUESTS, saved.id, saved.toRow())
        sync.enqueue(PendingOpType.SUBMIT_ALLOTMENT_REQUEST, syncJson.encodeToString(saved.toRow()))
    }

    override suspend fun fulfillRequest(id: String, staffId: String, staffName: String) {
        if (connectivity.isOnline.value) { remote.fulfillRequest(id, staffId, staffName); return }
        cache.readRow<AllotmentRequestRow>(Tables.ALLOTMENT_REQUESTS, id)?.let {
            cache.upsertRow(Tables.ALLOTMENT_REQUESTS, id, it.copy(status = AllotmentRequestStatus.FULFILLED.name, fulfilledById = staffId, fulfilledByName = staffName))
        }
        sync.enqueue(PendingOpType.ALLOTMENT_FULFILL, syncJson.encodeToString(FulfillPayload(id, staffId, staffName)))
    }

    override suspend fun getByMedicationEntryId(medicationEntryId: String): AllotmentRequest? =
        getPendingRequests().firstOrNull { it.medicationEntryId == medicationEntryId }
}

// ─────────────────────────────────────────────────────────────────────────────
// Notifications & Audit — supplementary, not clinically load-bearing, so
// writes made offline are best-effort (never queued, never thrown) rather
// than added to the sync queue; reads still fall back to cache.
// ─────────────────────────────────────────────────────────────────────────────

class OfflineNotificationRepository(
    private val remote: NotificationRepository,
    private val cache: CachedRowDao,
    private val connectivity: ConnectivityObserver,
) : NotificationRepository {
    override suspend fun getForRecipient(staffId: String, role: UserRole): List<AppNotification> {
        if (connectivity.isOnline.value) {
            val list = remote.getForRecipient(staffId, role)
            list.forEach { cache.upsertRow(Tables.NOTIFICATIONS, it.id, it.toRow()) }
            return list
        }
        return cache.readAllRows<NotificationRow>(Tables.NOTIFICATIONS).map { it.toDomain() }
            .filter { it.recipientStaffId == staffId || it.recipientRole == role }.sortedByDescending { it.timestamp }
    }

    override suspend fun getUnreadCountForRecipient(staffId: String, role: UserRole): Int =
        if (connectivity.isOnline.value) remote.getUnreadCountForRecipient(staffId, role)
        else getForRecipient(staffId, role).count { !it.isRead }

    override suspend fun add(notification: AppNotification) {
        if (!connectivity.isOnline.value) return // best-effort only; skipped silently while offline
        remote.add(notification)
    }

    override suspend fun markRead(id: String) {
        if (!connectivity.isOnline.value) return
        remote.markRead(id)
    }

    override suspend fun markAllReadForRecipient(staffId: String, role: UserRole) {
        if (!connectivity.isOnline.value) return
        remote.markAllReadForRecipient(staffId, role)
    }
}

class OfflineAuditRepository(
    private val remote: AuditRepository,
    private val cache: CachedRowDao,
    private val connectivity: ConnectivityObserver,
) : AuditRepository {
    override suspend fun getAllLogs(): List<AuditLogEntry> {
        if (connectivity.isOnline.value) {
            val logs = remote.getAllLogs()
            logs.forEach { cache.upsertRow(Tables.AUDIT_LOG, it.id, it.toRow()) }
            return logs
        }
        return cache.readAllRows<AuditLogRow>(Tables.AUDIT_LOG).map { it.toDomain() }.sortedByDescending { it.timestamp }
    }

    override suspend fun addLog(entry: AuditLogEntry) {
        val saved = entry.copy(id = entry.id.ifBlank { newLocalId() })
        cache.upsertRow(Tables.AUDIT_LOG, saved.id, saved.toRow())
        if (connectivity.isOnline.value) runCatching { remote.addLog(saved) }
        // else: recorded locally only — audit history is supplementary reporting,
        // not queued for replay, so an offline entry made on this device won't
        // appear in Supabase's audit_log until the same device is next online
        // and happens to write another entry (out of scope to special-case further).
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Staff — reads cache for the roster; every mutation (add/revoke/delete/reset
// password) touches Supabase Auth via Edge Functions, not a plain Postgrest
// row, so none of it is queueable and all of it stays online-only.
// ─────────────────────────────────────────────────────────────────────────────

class OfflineStaffRepository(
    private val remote: StaffRepository,
    private val cache: CachedRowDao,
    private val connectivity: ConnectivityObserver,
) : StaffRepository {
    override suspend fun getAllStaff(): List<Staff> {
        if (connectivity.isOnline.value) {
            val staff = remote.getAllStaff()
            staff.forEach { cache.upsertRow(Tables.STAFF, it.id, it.toDomainRowSafe()) }
            return staff
        }
        return cache.readAllRows<StaffRow>(Tables.STAFF).map { it.toDomain() }.sortedBy { it.name }
    }

    override suspend fun addStaff(name: String, email: String, phone: String, role: UserRole, password: String): Staff =
        remote.addStaff(name, email, phone, role, password)
    override suspend fun revokeStaff(id: String) = remote.revokeStaff(id)
    override suspend fun unrevokeStaff(id: String) = remote.unrevokeStaff(id)
    override suspend fun deleteStaff(id: String) = remote.deleteStaff(id)
    override suspend fun updateFcmToken(staffId: String, token: String) {
        if (connectivity.isOnline.value) remote.updateFcmToken(staffId, token)
        // else: skipped — this device's push token will just re-register on next online login.
    }
    override suspend fun resetPassword(staffId: String, newPassword: String) = remote.resetPassword(staffId, newPassword)
}

/** [StaffRow] has no public toRow(); build one from the domain object for cache storage. */
private fun Staff.toDomainRowSafe() = StaffRow(
    id = id, name = name, email = email, role = role.name, phone = phone, isActive = isActive,
    joinedDate = joinedDate.toString(), authEmail = authEmail, fcmToken = fcmToken,
)
