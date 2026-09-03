package com.kalazacare.app

import android.app.Application
import com.kalazacare.app.data.local.KalazaCacheDb
import com.kalazacare.app.data.remote.SupabaseClients
import com.kalazacare.app.data.repository.*
import com.kalazacare.app.data.sync.SyncManager
import com.kalazacare.app.util.ConnectivityObserver

/**
 * Application class — every data repository is backed by Supabase, wrapped in an
 * Offline*Repository that falls back to a local Room cache when there's no
 * connectivity and queues writes for [SyncManager] to replay once it returns
 * (see context/project_state_and_workflows.md's offline-support section).
 * Firebase Cloud Messaging remains independent, for push only.
 */
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
    lateinit var connectivityObserver: ConnectivityObserver
    lateinit var syncManager: SyncManager

    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this)
        val client = SupabaseClients.main

        val cacheDb = KalazaCacheDb.get(this)
        val cacheDao = cacheDb.cachedRowDao()
        connectivityObserver = ConnectivityObserver(this).also { it.start() }

        val supabaseAuth = SupabaseAuthRepository(client)
        val supabaseStaff = SupabaseStaffRepository(client)
        val supabasePatient = SupabasePatientRepository(client)
        val supabaseVitals = SupabaseVitalsRepository(client)
        val supabaseMedication = SupabaseMedicationRepository(client)
        val supabaseUtility = SupabaseUtilityRepository(client)
        val supabaseDoctorVisit = SupabaseDoctorVisitRepository(client)
        val supabaseCareNote = SupabaseCareNoteRepository(client)
        val supabaseApproval = SupabaseApprovalRepository(client)
        val supabaseAudit = SupabaseAuditRepository(client)
        val supabaseAllotmentRequest = SupabaseAllotmentRequestRepository(client)
        val supabaseNotification = SupabaseNotificationRepository(client)

        authRepository = supabaseAuth
        staffRepository = OfflineStaffRepository(supabaseStaff, cacheDao, connectivityObserver)

        syncManager = SyncManager(
            client = client,
            pendingDao = cacheDb.pendingOperationDao(),
            connectivity = connectivityObserver,
            patientRepo = supabasePatient,
            vitalsRepo = supabaseVitals,
            medicationRepo = supabaseMedication,
            utilityRepo = supabaseUtility,
            doctorVisitRepo = supabaseDoctorVisit,
            careNoteRepo = supabaseCareNote,
            approvalRepo = supabaseApproval,
            allotmentRequestRepo = supabaseAllotmentRequest,
            auditRepo = supabaseAudit,
        ).also { it.start() }

        patientRepository = OfflinePatientRepository(supabasePatient, cacheDao, connectivityObserver, syncManager)
        vitalsRepository = OfflineVitalsRepository(supabaseVitals, cacheDao, connectivityObserver, syncManager)
        medicationRepository = OfflineMedicationRepository(supabaseMedication, cacheDao, connectivityObserver, syncManager)
        utilityRepository = OfflineUtilityRepository(supabaseUtility, cacheDao, connectivityObserver, syncManager)
        doctorVisitRepository = OfflineDoctorVisitRepository(supabaseDoctorVisit, cacheDao, connectivityObserver, syncManager)
        careNoteRepository = OfflineCareNoteRepository(supabaseCareNote, cacheDao, connectivityObserver, syncManager)
        approvalRepository = OfflineApprovalRepository(supabaseApproval, cacheDao, connectivityObserver, syncManager)
        auditRepository = OfflineAuditRepository(supabaseAudit, cacheDao, connectivityObserver)
        allotmentRequestRepository = OfflineAllotmentRequestRepository(supabaseAllotmentRequest, cacheDao, connectivityObserver, syncManager)
        notificationRepository = OfflineNotificationRepository(supabaseNotification, cacheDao, connectivityObserver)
    }
}
