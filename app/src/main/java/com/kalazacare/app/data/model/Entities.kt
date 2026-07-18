package com.kalazacare.app.data.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

// ─────────────────────────────────────────────────────────────────────────────
// Enumerations
// ─────────────────────────────────────────────────────────────────────────────

enum class UserRole { ADMIN, STAFF, MEDICINE_STAFF }

fun UserRole.displayLabel(): String = when (this) {
    UserRole.ADMIN          -> "Admin"
    UserRole.STAFF          -> "Regular Staff"
    UserRole.MEDICINE_STAFF -> "Medicine Staff"
}

enum class Gender { MALE, FEMALE, OTHER }

enum class ApprovalStatus { PENDING, APPROVED, REJECTED }

enum class MedStatus { PENDING, ADMINISTERED, OVERDUE }

enum class AllotmentStatus { NOT_ALLOTTED, ALLOTTED }

enum class AllotmentRequestStatus { PENDING, FULFILLED }

// ─────────────────────────────────────────────────────────────────────────────
// Core Entities
// ─────────────────────────────────────────────────────────────────────────────

data class Patient(
    val id: String = "",
    val name: String = "",
    val age: Int = 0,
    val gender: Gender = Gender.MALE,
    val roomNo: String = "",
    val photoUrl: String = "",
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
    val temperature: String = "",    // °F
    val sugarFasting: String = "",   // mg/dL
    val sugarPP: String = "",        // mg/dL
    val signedBy: String = "",       // Staff name
)

data class MedicationEntry(
    val id: String = "",
    val patientId: String = "",
    val medicineName: String = "",
    val dose: String = "",           // e.g. "500mg"
    val quantity: String = "",       // e.g. "1 tablet"
    val scheduleTime: LocalTime = LocalTime.now(),
    val scheduledDate: LocalDate = LocalDate.now(),
    val status: MedStatus = MedStatus.PENDING,
    val administeredBy: String = "",
    val administeredAt: LocalDateTime? = null,
    val notes: String = "",
    // ── Medicine-staff allotment checkpoint (prepared for handoff to whoever administers) ──
    val allotmentStatus: AllotmentStatus = AllotmentStatus.NOT_ALLOTTED,
    val allottedById: String = "",
    val allottedByName: String = "",
    val allottedAt: LocalDateTime? = null,
    val allotmentPhotoUrl: String = "",
    val allotmentPhotoExpiresAt: LocalDateTime? = null,
    // ── Administration checkpoint photo (proof medicine was actually given) ──
    val administeredPhotoUrl: String = "",
    val administeredPhotoExpiresAt: LocalDateTime? = null,
)

/**
 * Raised by regular staff when the assigned medicine-staff member forgot
 * to allot a dose ahead of its scheduled time. Stands in for a push
 * notification to medicine-staff until FCM is wired.
 */
data class AllotmentRequest(
    val id: String = "",
    val medicationEntryId: String = "",
    val patientId: String = "",
    val patientName: String = "",
    val medicineName: String = "",
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
    val faceMask: Int = 0,
    val diaperPant: Int = 0,
    val diaperStitch: Int = 0,
    val handGloves: Int = 0,
    val tinaBed: Int = 0,
    val wetWipes: Int = 0,
    val issuedToCaregiver: String = "",
    val issuedBySupervisor: String = "",
    val checkedBy: String = "",
)

data class DoctorVisit(
    val id: String = "",
    val patientId: String = "",
    val doctorName: String = "",
    val specialty: String = "",
    val date: LocalDate = LocalDate.now(),
    val notes: String = "",
    val nextVisitDate: LocalDate? = null,
    val prescriptionChanges: String = "",
)

data class CareNote(
    val id: String = "",
    val patientId: String = "",
    val staffId: String = "",
    val staffName: String = "",
    val date: LocalDate = LocalDate.now(),
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val note: String = "",
)

// ─────────────────────────────────────────────────────────────────────────────
// Admin / Workflow Entities
// ─────────────────────────────────────────────────────────────────────────────

data class ApprovalRequest(
    val id: String = "",
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
    val iconName: String = "edit",   // Material icon name for display
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
