package com.kalazacare.app

import android.app.Application
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.kalazacare.app.data.repository.*

/**
 * Application class – initialises all repositories as singletons.
 *
 * Auth and Staff are now backed by real Firebase (Auth + Firestore) — the first
 * step of the staged backend migration. Everything else is still the in-memory
 * mock, converted one repository at a time.
 */
class KalazaApp : Application() {

    // Repositories exposed as singletons
    lateinit var authRepository:       AuthRepository
    lateinit var patientRepository:    PatientRepository
    lateinit var vitalsRepository:     VitalsRepository
    lateinit var medicationRepository: MedicationRepository
    lateinit var utilityRepository:    UtilityRepository
    lateinit var doctorVisitRepository:DoctorVisitRepository
    lateinit var careNoteRepository:   CareNoteRepository
    lateinit var approvalRepository:   ApprovalRepository
    lateinit var auditRepository:      AuditRepository
    lateinit var staffRepository:      StaffRepository
    lateinit var allotmentRequestRepository: AllotmentRequestRepository
    lateinit var notificationRepository: NotificationRepository

    override fun onCreate() {
        super.onCreate()
        val firebaseAuth = FirebaseAuth.getInstance()
        val firestore = FirebaseFirestore.getInstance()

        authRepository        = FirebaseAuthRepository(firebaseAuth, firestore)
        staffRepository       = FirestoreStaffRepository(firebaseAuth, firestore)

        patientRepository     = MockPatientRepository()
        vitalsRepository      = MockVitalsRepository()
        medicationRepository  = MockMedicationRepository()
        utilityRepository     = MockUtilityRepository()
        doctorVisitRepository = MockDoctorVisitRepository()
        careNoteRepository    = MockCareNoteRepository()
        approvalRepository    = MockApprovalRepository()
        auditRepository       = MockAuditRepository()
        allotmentRequestRepository = MockAllotmentRequestRepository()
        notificationRepository = MockNotificationRepository()
    }
}
