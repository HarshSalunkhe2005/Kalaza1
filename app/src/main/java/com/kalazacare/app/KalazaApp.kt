package com.kalazacare.app

import android.app.Application
import com.kalazacare.app.data.remote.SupabaseClients
import com.kalazacare.app.data.repository.*

/** Application class — every data repository is backed by Supabase; Firebase Cloud Messaging remains for push. */
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
        val client = SupabaseClients.main

        authRepository        = SupabaseAuthRepository(client)
        staffRepository       = SupabaseStaffRepository(client)
        patientRepository     = SupabasePatientRepository(client)
        vitalsRepository      = SupabaseVitalsRepository(client)
        medicationRepository  = SupabaseMedicationRepository(client)
        utilityRepository     = SupabaseUtilityRepository(client)
        doctorVisitRepository = SupabaseDoctorVisitRepository(client)
        careNoteRepository    = SupabaseCareNoteRepository(client)
        approvalRepository    = SupabaseApprovalRepository(client)
        auditRepository       = SupabaseAuditRepository(client)
        allotmentRequestRepository = SupabaseAllotmentRequestRepository(client)
        notificationRepository = SupabaseNotificationRepository(client)
    }
}
