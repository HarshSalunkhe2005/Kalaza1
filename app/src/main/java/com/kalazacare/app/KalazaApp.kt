package com.kalazacare.app

import android.app.Application
import com.kalazacare.app.data.repository.*

/**
 * Application class – initialises all mock repositories as singletons.
 * Will be replaced with Dependency Injection (Hilt/Koin) when Firebase is wired.
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

    override fun onCreate() {
        super.onCreate()
        authRepository        = MockAuthRepository()
        patientRepository     = MockPatientRepository()
        vitalsRepository      = MockVitalsRepository()
        medicationRepository  = MockMedicationRepository()
        utilityRepository     = MockUtilityRepository()
        doctorVisitRepository = MockDoctorVisitRepository()
        careNoteRepository    = MockCareNoteRepository()
        approvalRepository    = MockApprovalRepository()
        auditRepository       = MockAuditRepository()
        staffRepository       = MockStaffRepository()
        allotmentRequestRepository = MockAllotmentRequestRepository()
    }
}
