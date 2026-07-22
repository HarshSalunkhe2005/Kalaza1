package com.kalazacare.app

import android.app.Application
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.kalazacare.app.data.repository.*

/** Application class — every repository is now backed by real Firebase (Auth + Firestore). */
class KalazaApp : Application() {

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
        staffRepository       = FirestoreStaffRepository(firebaseAuth, firestore, applicationContext)
        patientRepository     = FirestorePatientRepository(firestore)
        vitalsRepository      = FirestoreVitalsRepository(firestore)
        medicationRepository  = FirestoreMedicationRepository(firestore)
        utilityRepository     = FirestoreUtilityRepository(firestore)
        doctorVisitRepository = FirestoreDoctorVisitRepository(firestore)
        careNoteRepository    = FirestoreCareNoteRepository(firestore)
        approvalRepository    = FirestoreApprovalRepository(firestore)
        auditRepository       = FirestoreAuditRepository(firestore)
        allotmentRequestRepository = FirestoreAllotmentRequestRepository(firestore)
        notificationRepository = FirestoreNotificationRepository(firestore)
    }
}
