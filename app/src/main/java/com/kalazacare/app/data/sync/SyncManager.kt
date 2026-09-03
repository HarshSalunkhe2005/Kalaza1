package com.kalazacare.app.data.sync

import com.kalazacare.app.data.local.PendingOpStatus
import com.kalazacare.app.data.local.PendingOperationDao
import com.kalazacare.app.data.local.PendingOperationEntity
import com.kalazacare.app.data.model.AuditLogEntry
import com.kalazacare.app.data.repository.*
import com.kalazacare.app.util.ConnectivityObserver
import com.kalazacare.app.util.SessionManager
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/** Every mutating repository call worth queuing offline — see the offline-support plan. */
object PendingOpType {
    // Pure appends — always safe to replay, a new row can't conflict.
    const val ADD_PATIENT = "ADD_PATIENT"
    const val ADD_VITAL = "ADD_VITAL"
    const val ADD_UTILITY_RECORD = "ADD_UTILITY_RECORD"
    const val ADD_DOCTOR_VISIT = "ADD_DOCTOR_VISIT"
    const val ADD_CARE_NOTE = "ADD_CARE_NOTE"
    const val SUBMIT_APPROVAL_REQUEST = "SUBMIT_APPROVAL_REQUEST"
    const val SUBMIT_ALLOTMENT_REQUEST = "SUBMIT_ALLOTMENT_REQUEST"

    // Mutations — need a conflict check against current server state before replay.
    const val MED_MARK_ADMINISTERED = "MED_MARK_ADMINISTERED"
    const val MED_ALLOT = "MED_ALLOT"
    const val APPROVAL_REVIEW = "APPROVAL_REVIEW"
    const val ALLOTMENT_FULFILL = "ALLOTMENT_FULFILL"
    const val EDIT_VITAL = "EDIT_VITAL"
    const val EDIT_UTILITY = "EDIT_UTILITY"
    const val EDIT_CARE_NOTE = "EDIT_CARE_NOTE"
    const val EDIT_DOCTOR_VISIT = "EDIT_DOCTOR_VISIT"
    const val EDIT_PATIENT = "EDIT_PATIENT"
    const val DELETE_DOCTOR_VISIT = "DELETE_DOCTOR_VISIT"
    const val ARCHIVE_PATIENT = "ARCHIVE_PATIENT"
}

internal val syncJson = Json { ignoreUnknownKeys = true }

@kotlinx.serialization.Serializable
internal data class EditPayload(val id: String, val oldRowJson: String, val newRowJson: String)

@kotlinx.serialization.Serializable
internal data class IdPayload(val id: String)

@kotlinx.serialization.Serializable
internal data class MarkAdministeredPayload(val id: String, val staffName: String, val scannedCode: String)

@kotlinx.serialization.Serializable
internal data class AllotPayload(val id: String, val staffId: String, val staffName: String, val scannedCode: String)

@kotlinx.serialization.Serializable
internal data class ApprovalReviewPayload(
    val id: String, val reviewerId: String, val reviewerName: String,
    val approve: Boolean, val reason: String = "",
)

@kotlinx.serialization.Serializable
internal data class FulfillPayload(val id: String, val staffId: String, val staffName: String)

/**
 * Drains [PendingOperationDao]'s queue once connectivity returns, replaying each write
 * against the real Supabase repositories in the order it was queued. Pure-append ops
 * (a new row) always succeed; mutating ops re-check current server state first and are
 * flagged CONFLICT (never silently double-applied or overwritten) if something else
 * changed the same record while this device was offline — see the plan's per-opType
 * conflict rules for exactly what's compared.
 */
class SyncManager(
    private val client: SupabaseClient,
    private val pendingDao: PendingOperationDao,
    private val connectivity: ConnectivityObserver,
    private val patientRepo: PatientRepository,
    private val vitalsRepo: VitalsRepository,
    private val medicationRepo: MedicationRepository,
    private val utilityRepo: UtilityRepository,
    private val doctorVisitRepo: DoctorVisitRepository,
    private val careNoteRepo: CareNoteRepository,
    private val approvalRepo: ApprovalRepository,
    private val allotmentRequestRepo: AllotmentRequestRepository,
    private val auditRepo: AuditRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val pendingCount: StateFlow<Int> = pendingDao.observePendingCount()
        .stateIn(scope, SharingStarted.Eagerly, 0)

    val conflicts: StateFlow<List<PendingOperationEntity>> = pendingDao.observeConflicts()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val _offlineSavedMessages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val offlineSavedMessages: SharedFlow<String> = _offlineSavedMessages

    /** Called by an Offline*Repository write method when it queues something instead of sending it live. */
    suspend fun enqueue(opType: String, payloadJson: String) {
        pendingDao.insert(
            PendingOperationEntity(
                id = UUID.randomUUID().toString(),
                createdAt = System.currentTimeMillis(),
                staffId = SessionManager.getCurrentStaffId(),
                staffName = SessionManager.getCurrentStaffName(),
                opType = opType,
                payloadJson = payloadJson,
                status = PendingOpStatus.PENDING,
            )
        )
        _offlineSavedMessages.tryEmit("Saved offline — will sync when you're back online.")
    }

    /** Start watching connectivity; drains the queue on every offline→online transition (and once at launch if already online). */
    fun start() {
        scope.launch {
            connectivity.isOnline.collect { online ->
                if (online) syncNow()
            }
        }
    }

    suspend fun syncNow() {
        if (!connectivity.isOnline.value) return
        for (op in pendingDao.getPending()) {
            try {
                val conflictReason = replay(op)
                if (conflictReason != null) {
                    pendingDao.updateStatus(op.id, PendingOpStatus.CONFLICT, conflictReason)
                    logConflict(op, conflictReason)
                } else {
                    pendingDao.delete(op.id)
                }
            } catch (e: Exception) {
                pendingDao.updateStatus(op.id, PendingOpStatus.FAILED, e.message ?: "Sync failed")
            }
        }
    }

    fun dismissConflict(id: String) {
        scope.launch { pendingDao.delete(id) }
    }

    private suspend fun logConflict(op: PendingOperationEntity, reason: String) {
        runCatching {
            auditRepo.addLog(
                AuditLogEntry(
                    action = "Sync Conflict",
                    performedById = op.staffId,
                    performedByName = op.staffName,
                    details = reason,
                    iconName = "sync_problem",
                )
            )
        }
    }

    /** Returns null on success (op is removed from the queue), or a human-readable conflict reason to flag instead of applying. */
    private suspend fun replay(op: PendingOperationEntity): String? = when (op.opType) {
        PendingOpType.ADD_PATIENT -> {
            client.postgrest.from("patients").insert(syncJson.decodeFromString<PatientRow>(op.payloadJson)); null
        }
        PendingOpType.ADD_VITAL -> {
            client.postgrest.from("vitals").insert(syncJson.decodeFromString<VitalRow>(op.payloadJson)); null
        }
        PendingOpType.ADD_UTILITY_RECORD -> {
            client.postgrest.from("utility_records").insert(syncJson.decodeFromString<UtilityRecordRow>(op.payloadJson)); null
        }
        PendingOpType.ADD_DOCTOR_VISIT -> {
            client.postgrest.from("doctor_visits").insert(syncJson.decodeFromString<DoctorVisitRow>(op.payloadJson)); null
        }
        PendingOpType.ADD_CARE_NOTE -> {
            client.postgrest.from("care_notes").insert(syncJson.decodeFromString<CareNoteRow>(op.payloadJson)); null
        }
        PendingOpType.SUBMIT_APPROVAL_REQUEST -> {
            client.postgrest.from("approval_requests").insert(syncJson.decodeFromString<ApprovalRequestRow>(op.payloadJson)); null
        }
        PendingOpType.SUBMIT_ALLOTMENT_REQUEST -> {
            client.postgrest.from("allotment_requests").insert(syncJson.decodeFromString<AllotmentRequestRow>(op.payloadJson)); null
        }

        PendingOpType.MED_MARK_ADMINISTERED -> {
            val p = syncJson.decodeFromString<MarkAdministeredPayload>(op.payloadJson)
            val current = medicationRepo.getMedicationById(p.id)
            when {
                current == null -> "Dose no longer exists — it may have been deleted."
                current.status == com.kalazacare.app.data.model.MedStatus.ADMINISTERED ->
                    "${current.medicineName} was already marked given by someone else while this device was offline."
                else -> { medicationRepo.markAdministered(p.id, p.staffName, p.scannedCode); null }
            }
        }
        PendingOpType.MED_ALLOT -> {
            val p = syncJson.decodeFromString<AllotPayload>(op.payloadJson)
            val current = medicationRepo.getMedicationById(p.id)
            when {
                current == null -> "Dose no longer exists — it may have been deleted."
                current.allotmentStatus == com.kalazacare.app.data.model.AllotmentStatus.ALLOTTED ->
                    "${current.medicineName} was already allotted by someone else while this device was offline."
                else -> { medicationRepo.allotMedication(p.id, p.staffId, p.staffName, p.scannedCode); null }
            }
        }
        PendingOpType.APPROVAL_REVIEW -> {
            val p = syncJson.decodeFromString<ApprovalReviewPayload>(op.payloadJson)
            val current = approvalRepo.getRequestById(p.id)
            when {
                current == null -> "Request no longer exists."
                current.status != com.kalazacare.app.data.model.ApprovalStatus.PENDING ->
                    "This request was already reviewed by someone else while this device was offline."
                p.approve -> { approvalRepo.approve(p.id, p.reviewerId, p.reviewerName); null }
                else -> { approvalRepo.reject(p.id, p.reviewerId, p.reviewerName, p.reason); null }
            }
        }
        PendingOpType.ALLOTMENT_FULFILL -> {
            val p = syncJson.decodeFromString<FulfillPayload>(op.payloadJson)
            val current = allotmentRequestRepo.getAllRequests().firstOrNull { it.id == p.id }
            when {
                current == null -> "Allotment request no longer exists."
                current.status != com.kalazacare.app.data.model.AllotmentRequestStatus.PENDING ->
                    "This allotment request was already fulfilled by someone else while this device was offline."
                else -> { allotmentRequestRepo.fulfillRequest(p.id, p.staffId, p.staffName); null }
            }
        }

        PendingOpType.EDIT_VITAL -> replayEdit(op.payloadJson,
            fetchCurrentRowJson = { id -> vitalsRepo.getVitalById(id)?.toRow()?.let { syncJson.encodeToString(it) } },
            apply = { row: VitalRow -> vitalsRepo.updateVital(row.toDomain()) },
        )
        PendingOpType.EDIT_UTILITY -> replayEdit(op.payloadJson,
            fetchCurrentRowJson = { id -> utilityRepo.getUtilityRecordById(id)?.toRow()?.let { syncJson.encodeToString(it) } },
            apply = { row: UtilityRecordRow -> utilityRepo.updateUtilityRecord(row.toDomain()) },
        )
        PendingOpType.EDIT_CARE_NOTE -> replayEdit(op.payloadJson,
            fetchCurrentRowJson = { id -> careNoteRepo.getNoteById(id)?.toRow()?.let { syncJson.encodeToString(it) } },
            apply = { row: CareNoteRow -> careNoteRepo.updateNote(row.toDomain()) },
        )
        PendingOpType.EDIT_DOCTOR_VISIT -> replayEdit(op.payloadJson,
            fetchCurrentRowJson = { id -> doctorVisitRepo.getVisitById(id)?.toRow()?.let { syncJson.encodeToString(it) } },
            apply = { row: DoctorVisitRow -> doctorVisitRepo.updateVisit(row.toDomain()) },
        )
        PendingOpType.EDIT_PATIENT -> replayEdit(op.payloadJson,
            fetchCurrentRowJson = { id -> patientRepo.getPatientById(id)?.toRow()?.let { syncJson.encodeToString(it) } },
            apply = { row: PatientRow -> patientRepo.updatePatient(row.toDomain()) },
        )

        PendingOpType.DELETE_DOCTOR_VISIT -> {
            val p = syncJson.decodeFromString<IdPayload>(op.payloadJson)
            if (doctorVisitRepo.getVisitById(p.id) != null) doctorVisitRepo.deleteVisit(p.id)
            null // already gone is an equally-valid end state, not a conflict
        }
        PendingOpType.ARCHIVE_PATIENT -> {
            val p = syncJson.decodeFromString<IdPayload>(op.payloadJson)
            patientRepo.archivePatient(p.id) // idempotent — safe to re-apply even if already archived
            null
        }
        else -> "Unknown queued operation type: ${op.opType}"
    }

    /** Shared optimistic-concurrency check for a plain field edit: apply only if nothing else changed the row since this edit was queued. */
    private suspend inline fun <reified T> replayEdit(
        payloadJson: String,
        fetchCurrentRowJson: suspend (id: String) -> String?,
        apply: suspend (T) -> Unit,
    ): String? {
        val p = syncJson.decodeFromString<EditPayload>(payloadJson)
        val currentJson = fetchCurrentRowJson(p.id) ?: return "Record no longer exists."
        if (currentJson != p.oldRowJson) return "This record was changed by someone else while this device was offline — edit not applied automatically."
        apply(syncJson.decodeFromString<T>(p.newRowJson))
        return null
    }
}
