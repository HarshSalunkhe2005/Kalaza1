package com.kalazacare.app.data.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

// ─────────────────────────────────────────────────────────────────────────────
// Enumerations
// ─────────────────────────────────────────────────────────────────────────────

// CHANGE 8: MEDICINE_STAFF → SUPERVISOR
// CHANGE 9: ADMIN → SUPER_ADMIN (keeps all prior admin powers); new ADMIN is a
// restricted, photo-audit-only role (sees only medicine allotment/administration
// evidence photos, nothing else).
enum class UserRole { SUPER_ADMIN, ADMIN, STAFF, SUPERVISOR }

fun UserRole.displayLabel(): String = when (this) {
    UserRole.SUPER_ADMIN -> "Super Admin"
    UserRole.ADMIN       -> "Admin"
    UserRole.STAFF       -> "Regular Staff"
    UserRole.SUPERVISOR  -> "Supervisor"
}

enum class Gender { MALE, FEMALE, OTHER }

enum class ApprovalStatus { PENDING, APPROVED, REJECTED }

/** What kind of record an [ApprovalRequest] applies its diff to once approved. */
enum class ApprovalEntityType { PATIENT, DOCTOR_VISIT, VITAL, UTILITY, CARE_NOTE }

/** What an [ApprovalRequest] is asking for — a field edit, or removing the whole record. */
enum class ApprovalAction { EDIT, DELETE }

enum class MedStatus { PENDING, ADMINISTERED, OVERDUE }

enum class AllotmentStatus { NOT_ALLOTTED, ALLOTTED }

enum class AllotmentRequestStatus { PENDING, FULFILLED }

// CHANGE 4: visits get an isConfirmed + isArchived flag
// ─────────────────────────────────────────────────────────────────────────────
// Core Entities
// ─────────────────────────────────────────────────────────────────────────────

data class Patient(
    val id: String = "",
    val name: String = "",
    val age: Int = 0,
    val gender: Gender = Gender.MALE,
    val roomNo: String = "",
    val medicalHistory: String = "",
    val currentIssues: String = "",
    val allergies: String = "",
    val emergencyContact: String = "",
    val emergencyPhone: String = "",
    val admissionDate: LocalDate = LocalDate.now(),
    val isArchived: Boolean = false,
    val primaryDiagnosis: String = "",
)

data class Staff(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val role: UserRole = UserRole.STAFF,
    val phone: String = "",
    val isActive: Boolean = true,
    val joinedDate: LocalDate = LocalDate.now(),
    // Synthetic email used only to authenticate this staff member against Firebase
    // Auth's email/password provider — login itself is still by [name] + password;
    // this never appears in the UI.
    val authEmail: String = "",
    // This device's current push token, refreshed on every login and whenever
    // Firebase Messaging rotates it. Used server-side (Cloud Function) to target
    // a push at this specific staff member.
    val fcmToken: String = "",
)

// ─────────────────────────────────────────────────────────────────────────────
// Clinical Records
// ─────────────────────────────────────────────────────────────────────────────

data class VitalRecord(
    val id: String = "",
    val patientId: String = "",
    val date: LocalDate = LocalDate.now(),
    val time: LocalTime = LocalTime.now(),
    val pulse: String = "",          // bpm
    val bp: String = "",             // e.g. "120/80"
    val spo2: String = "",           // %
    val temperature: String = "",    // °F  (CHANGE 6: always Fahrenheit)
    val sugarFasting: String = "",   // mg/dL
    val sugarPP: String = "",        // mg/dL
    val signedBy: String = "",       // Staff name
)

data class MedicationEntry(
    val id: String = "",
    val patientId: String = "",
    val medicineName: String = "",
    val dose: String = "",
    val quantity: String = "",
    val scheduleTime: LocalTime = LocalTime.now(),
    val scheduledDate: LocalDate = LocalDate.now(),
    // Most medications repeat every day — for those, [scheduledDate] is just
    // "the day this entry was created" and is ignored when deciding whether
    // it's due today. Set this false only for a genuine one-off dose that
    // should appear solely on [scheduledDate].
    val isRecurring: Boolean = true,
    val status: MedStatus = MedStatus.PENDING,
    val administeredBy: String = "",
    val administeredAt: LocalDateTime? = null,
    val notes: String = "",
    val allotmentStatus: AllotmentStatus = AllotmentStatus.NOT_ALLOTTED,
    val allottedById: String = "",
    val allottedByName: String = "",
    val allottedAt: LocalDateTime? = null,
    val allotmentPhotoUrl: String = "",
    val allotmentPhotoExpiresAt: LocalDateTime? = null,
    val administeredPhotoUrl: String = "",
    val administeredPhotoExpiresAt: LocalDateTime? = null,
)

data class AllotmentRequest(
    val id: String = "",
    val medicationEntryId: String = "",
    val patientId: String = "",
    val patientName: String = "",
    val medicineName: String = "",
    val dose: String = "",           // added so card can show dose
    val scheduledTime: LocalTime = LocalTime.now(),
    val requestedById: String = "",
    val requestedByName: String = "",
    val status: AllotmentRequestStatus = AllotmentRequestStatus.PENDING,
    val fulfilledById: String = "",
    val fulfilledByName: String = "",
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val fulfilledAt: LocalDateTime? = null,
)

data class UtilityRecord(
    val id: String = "",
    val patientId: String = "",
    val date: LocalDate = LocalDate.now(),
    val time: LocalTime = LocalTime.now(),
    val quantities: Map<String, Int> = emptyMap(),
    val issuedToCaregiver: String = "",
    val issuedBySupervisor: String = "",
    val checkedBy: String = "",
)

// CHANGE 4: DoctorVisit gets isConfirmed + isArchived + editable plannedDate
data class DoctorVisit(
    val id: String = "",
    val patientId: String = "",
    val doctorName: String = "",
    val specialty: String = "",
    val date: LocalDate = LocalDate.now(),          // actual/planned visit date
    val time: LocalTime = LocalTime.now(),
    val notes: String = "",
    val nextVisitDate: LocalDate? = null,
    val prescriptionChanges: String = "",
    val isConfirmed: Boolean = false,               // confirmed = visit actually happened
    val isArchived: Boolean = false,                // auto-archived once confirmed & date passed
)

data class CareNote(
    val id: String = "",
    val patientId: String = "",
    val staffId: String = "",
    val staffName: String = "",
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val note: String = "",
)

// ─────────────────────────────────────────────────────────────────────────────
// Admin / Workflow Entities
// ─────────────────────────────────────────────────────────────────────────────

data class ApprovalRequest(
    val id: String = "",
    val entityType: ApprovalEntityType = ApprovalEntityType.PATIENT,
    val entityId: String = "",
    val action: ApprovalAction = ApprovalAction.EDIT,
    val patientId: String = "",
    val patientName: String = "",
    val requestedById: String = "",
    val requestedByName: String = "",
    val fieldChanged: String = "",
    val oldValue: String = "",
    val newValue: String = "",
    val status: ApprovalStatus = ApprovalStatus.PENDING,
    val reviewedById: String = "",
    val reviewedByName: String = "",
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val reviewedAt: LocalDateTime? = null,
    val rejectionReason: String = "",
)

data class AuditLogEntry(
    val id: String = "",
    val action: String = "",
    val performedById: String = "",
    val performedByName: String = "",
    val targetPatientId: String = "",
    val targetPatientName: String = "",
    val details: String = "",
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val iconName: String = "edit",
)

// ─────────────────────────────────────────────────────────────────────────────
// Config
// ─────────────────────────────────────────────────────────────────────────────

data class UtilityItem(
    val id: String = "",
    val name: String = "",
    val unit: String = "pcs",
    val displayOrder: Int = 0,
    val isActive: Boolean = true,
)

// ─────────────────────────────────────────────────────────────────────────────
// Notifications
// ─────────────────────────────────────────────────────────────────────────────

enum class NotificationType {
    APPROVAL_REQUESTED, APPROVAL_APPROVED, APPROVAL_REJECTED,
    ALLOTMENT_REQUESTED, ALLOTMENT_FULFILLED,
}

data class AppNotification(
    val id: String = "",
    val recipientStaffId: String = "",
    val recipientRole: UserRole? = null,
    val type: NotificationType = NotificationType.APPROVAL_REQUESTED,
    val title: String = "",
    val message: String = "",
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val isRead: Boolean = false,
    val targetRoute: String = "",
)
