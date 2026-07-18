package com.kalazacare.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.kalazacare.app.KalazaApp
import com.kalazacare.app.ui.*
import com.kalazacare.app.ui.approval.ApprovalQueueScreen
import com.kalazacare.app.ui.auditlog.AuditLogScreen
import com.kalazacare.app.ui.config.ConfigScreen
import com.kalazacare.app.ui.dashboard.DashboardScreen
import com.kalazacare.app.ui.login.LoginScreen
import com.kalazacare.app.ui.medicine.MedicineScreen
import com.kalazacare.app.ui.patient.AddEditPatientScreen
import com.kalazacare.app.ui.patient.PatientProfileScreen
import com.kalazacare.app.ui.summary.SummaryScreen
import com.kalazacare.app.util.SessionManager

object Routes {
    const val LOGIN           = "login"
    const val DASHBOARD       = "dashboard"
    const val PATIENT_PROFILE = "patient/{patientId}"
    const val PATIENT_NEW     = "patient/new"
    const val PATIENT_EDIT    = "patient/{patientId}/edit"
    const val APPROVAL_QUEUE  = "approval"
    const val AUDIT_LOG       = "auditlog"
    const val CONFIG          = "config"
    const val SUMMARY         = "summary"
    const val MEDICINE        = "medicine"

    fun patientProfile(id: String) = "patient/$id"
    fun patientEdit(id: String)    = "patient/$id/edit"
}

/**
 * Bottom-nav destinations stay alive in the backstack (saveState/restoreState),
 * so their ViewModel's init{} only runs once. Without this, stats/lists go
 * stale after e.g. approving a request or adding a patient elsewhere and
 * returning via a tab. Re-run [onResume] whenever the destination resumes.
 */
@Composable
private fun ReloadOnResume(onResume: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) onResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

@Composable
fun KalazaNavHost() {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    // Build the ViewModelFactory from the Application singletons
    val context = LocalContext.current
    val app = context.applicationContext as KalazaApp
    val factory = remember {
        KalazaViewModelFactory(
            authRepo        = app.authRepository,
            patientRepo     = app.patientRepository,
            vitalsRepo      = app.vitalsRepository,
            medRepo         = app.medicationRepository,
            utilityRepo     = app.utilityRepository,
            doctorVisitRepo = app.doctorVisitRepository,
            careNoteRepo    = app.careNoteRepository,
            approvalRepo    = app.approvalRepository,
            auditRepo       = app.auditRepository,
            staffRepo       = app.staffRepository,
            allotmentRequestRepo = app.allotmentRequestRepository,
        )
    }

    // Logout handler — clears session and navigates back to login
    val onLogout: () -> Unit = {
        SessionManager.logout()
        navController.navigate(Routes.LOGIN) {
            popUpTo(0) { inclusive = true }
        }
    }

    // Routes where bottom nav should be visible
    val bottomNavRoutes = setOf(
        Routes.DASHBOARD, Routes.APPROVAL_QUEUE,
        Routes.AUDIT_LOG, Routes.CONFIG, Routes.SUMMARY, Routes.MEDICINE
    )
    val showBottomNav = currentRoute in bottomNavRoutes

    Scaffold(
        bottomBar = {
            if (showBottomNav) {
                KalazaBottomNavBar(navController = navController, currentRoute = currentRoute)
            }
        }
    ) { innerPadding ->

        NavHost(
            navController   = navController,
            startDestination = Routes.LOGIN,
            modifier         = Modifier.padding(innerPadding),
        ) {

            // ── Login ──────────────────────────────────────────────────────────
            composable(Routes.LOGIN) {
                val vm: LoginViewModel = viewModel(factory = factory)
                LoginScreen(
                    viewModel = vm,
                    onLoginSuccess = {
                        navController.navigate(Routes.DASHBOARD) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    }
                )
            }

            // ── Dashboard ──────────────────────────────────────────────────────
            composable(Routes.DASHBOARD) {
                val vm: DashboardViewModel = viewModel(factory = factory)
                ReloadOnResume { vm.load() }
                DashboardScreen(
                    viewModel = vm,
                    onPatientClick = { patientId ->
                        navController.navigate(Routes.patientProfile(patientId))
                    },
                    onAddPatient = {
                        navController.navigate(Routes.PATIENT_NEW)
                    },
                    onLogout = onLogout
                )
            }

            // ── Patient Profile ────────────────────────────────────────────────
            composable(
                route = Routes.PATIENT_PROFILE,
                arguments = listOf(navArgument("patientId") { type = NavType.StringType })
            ) { backStack ->
                val patientId = backStack.arguments?.getString("patientId") ?: ""
                PatientProfileScreen(
                    patientId = patientId,
                    factory = factory,
                    onBack = { navController.popBackStack() },
                    onEditPatient = { navController.navigate(Routes.patientEdit(patientId)) }
                )
            }

            // ── Add/Edit Patient ───────────────────────────────────────────────
            composable(Routes.PATIENT_NEW) {
                val vm: PatientViewModel = viewModel(factory = factory)
                AddEditPatientScreen(
                    patientId = null,
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() }
                )
            }
            composable(
                route = Routes.PATIENT_EDIT,
                arguments = listOf(navArgument("patientId") { type = NavType.StringType })
            ) { backStack ->
                val patientId = backStack.arguments?.getString("patientId") ?: ""
                val vm: PatientViewModel = viewModel(factory = factory)
                AddEditPatientScreen(
                    patientId = patientId,
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() }
                )
            }

            // ── Approval Queue ─────────────────────────────────────────────────
            composable(Routes.APPROVAL_QUEUE) {
                val vm: ApprovalViewModel = viewModel(factory = factory)
                ReloadOnResume { vm.load() }
                ApprovalQueueScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onLogout = onLogout
                )
            }

            // ── Audit Log ──────────────────────────────────────────────────────
            composable(Routes.AUDIT_LOG) {
                val vm: AuditLogViewModel = viewModel(factory = factory)
                ReloadOnResume { vm.load() }
                AuditLogScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onLogout = onLogout
                )
            }

            // ── Config ─────────────────────────────────────────────────────────
            composable(Routes.CONFIG) {
                val vm: ConfigViewModel = viewModel(factory = factory)
                ReloadOnResume { vm.load() }
                ConfigScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onLogout = onLogout
                )
            }

            // ── Summary ────────────────────────────────────────────────────────
            composable(Routes.SUMMARY) {
                val vm: SummaryViewModel = viewModel(factory = factory)
                ReloadOnResume { vm.load(vm.selectedDate.value) }
                SummaryScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onLogout = onLogout
                )
            }

            // ── Medicine (medicine-staff allotment rounds) ────────────────────────
            composable(Routes.MEDICINE) {
                val vm: MedicineViewModel = viewModel(factory = factory)
                ReloadOnResume { vm.load() }
                MedicineScreen(
                    viewModel = vm,
                    onLogout = onLogout
                )
            }
        }
    }
}
