package com.kalazacare.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.kalazacare.app.ui.dashboard.SuperAdminOverviewScreen
import com.kalazacare.app.ui.login.LoginScreen
import com.kalazacare.app.ui.medicine.MedicineScreen
import com.kalazacare.app.ui.notifications.NotificationScreen
import com.kalazacare.app.ui.patient.AddEditPatientScreen
import com.kalazacare.app.ui.patient.PatientProfileScreen
import com.kalazacare.app.ui.summary.SummaryScreen
import com.kalazacare.app.ui.todo.TodoListScreen
import com.kalazacare.app.util.SessionManager
import kotlinx.coroutines.delay

/** Same 15 minutes for every role — no per-role variance. */
private const val AUTO_LOGOUT_TIMEOUT_MS = 15 * 60 * 1000L
/** How often the idle check runs; doesn't need to be finer than this. */
private const val AUTO_LOGOUT_POLL_MS = 15_000L

object Routes {
    const val LOGIN           = "login"
    const val DASHBOARD       = "dashboard"
    const val SUPER_ADMIN_OVERVIEW = "super_admin_overview"
    const val TODO_LIST       = "todo_list"
    const val PATIENT_PROFILE = "patient/{patientId}"
    const val PATIENT_NEW     = "patient/new"
    const val PATIENT_EDIT    = "patient/{patientId}/edit"
    const val APPROVAL_QUEUE  = "approval"
    const val AUDIT_LOG       = "auditlog"
    const val CONFIG          = "config"
    const val SUMMARY         = "summary"
    const val MEDICINE        = "medicine"
    const val SCAN            = "scan"
    const val NOTIFICATIONS   = "notifications"

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
fun KalazaNavHost(
    pendingDeepLink: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
) {
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
            notificationRepo = app.notificationRepository,
            syncManager = app.syncManager,
        )
    }

    // Logout handler — clears local session, signs out of Supabase Auth
    // server-side, and navigates back to login
    val onLogout: () -> Unit = {
        app.authRepository.logout()
        SessionManager.logout()
        navController.navigate(Routes.LOGIN) {
            popUpTo(0) { inclusive = true }
        }
    }

    // A notification's targetRoute is either a static route (e.g. "approval") or
    // "patient/{id}" — both navigate the same way.
    val onNotificationTarget: (String) -> Unit = { route ->
        if (route.isNotBlank()) navController.navigate(route)
    }

    // A push notification tapped while already logged in (app foreground or
    // background, not killed) navigates straight there. If the app was killed,
    // Login shows first and its onLoginSuccess handles the pending route instead.
    LaunchedEffect(pendingDeepLink, currentRoute) {
        if (pendingDeepLink != null && SessionManager.isLoggedIn() && currentRoute != Routes.LOGIN) {
            onNotificationTarget(pendingDeepLink)
            onDeepLinkConsumed()
        }
    }

    // Routes where bottom nav should be visible
    val bottomNavRoutes = setOf(
        Routes.DASHBOARD, Routes.SUPER_ADMIN_OVERVIEW, Routes.TODO_LIST, Routes.APPROVAL_QUEUE,
        Routes.AUDIT_LOG, Routes.CONFIG, Routes.SUMMARY, Routes.MEDICINE, Routes.SCAN
    )
    val showBottomNav = currentRoute in bottomNavRoutes

    // ── Offline banner + "saved offline" snackbar ────────────────────────────
    val isOnline by app.connectivityObserver.isOnline.collectAsState()
    val pendingSyncCount by app.syncManager.pendingCount.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        app.syncManager.offlineSavedMessages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // ── Auto-logout after 15 min idle, same for every role ──────────────────
    // A session persisted on-device indefinitely otherwise; no timeout, no
    // re-auth prompt — see the security backlog. lastInteractionAt is bumped
    // by any pointer event anywhere in the app (Initial pass, never consumed,
    // so it never interferes with normal touch/scroll/click handling) and
    // checked on a plain poll rather than per-event, since a 15-second
    // granularity is more than enough for a 15-minute timeout.
    var lastInteractionAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(AUTO_LOGOUT_POLL_MS)
            if (SessionManager.isLoggedIn() &&
                System.currentTimeMillis() - lastInteractionAt >= AUTO_LOGOUT_TIMEOUT_MS
            ) {
                onLogout()
                lastInteractionAt = System.currentTimeMillis()
            }
        }
    }

    Scaffold(
        modifier = Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial)
                    lastInteractionAt = System.currentTimeMillis()
                }
            }
        },
        bottomBar = {
            if (showBottomNav) {
                KalazaBottomNavBar(navController = navController, currentRoute = currentRoute)
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->

        Column(modifier = Modifier.padding(innerPadding)) {
            if (currentRoute != Routes.LOGIN && (!isOnline || pendingSyncCount > 0)) {
                val (bg, label) = when {
                    !isOnline -> Color(0xFF7A1F1F) to "Offline — showing cached data"
                    else -> Color(0xFF8A6D1F) to "Syncing $pendingSyncCount pending change${if (pendingSyncCount == 1) "" else "s"}…"
                }
                Box(
                    modifier = Modifier.fillMaxWidth().background(bg).padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center)
                }
            }

        NavHost(
            navController   = navController,
            startDestination = Routes.LOGIN,
            modifier         = Modifier.weight(1f),
        ) {

            // ── Login ──────────────────────────────────────────────────────────
            composable(Routes.LOGIN) {
                val vm: LoginViewModel = viewModel(factory = factory)
                LoginScreen(
                    viewModel = vm,
                    onLoginSuccess = {
                        val destination = when {
                            pendingDeepLink != null -> pendingDeepLink
                            SessionManager.isAdmin() -> Routes.SUPER_ADMIN_OVERVIEW
                            else -> Routes.TODO_LIST   // STAFF and SUPERVISOR land on today's tasks
                        }
                        if (pendingDeepLink != null) onDeepLinkConsumed()
                        navController.navigate(destination) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    }
                )
            }

            // ── Dashboard ──────────────────────────────────────────────────────
            composable(Routes.DASHBOARD) {
                val vm: DashboardViewModel = viewModel(factory = factory)
                val notificationVm: NotificationViewModel = viewModel(factory = factory)
                ReloadOnResume { vm.load(); notificationVm.load() }
                DashboardScreen(
                    viewModel = vm,
                    unreadNotifications = notificationVm.unreadCount.collectAsState().value,
                    onPatientClick = { patientId ->
                        navController.navigate(Routes.patientProfile(patientId))
                    },
                    onAddPatient = {
                        navController.navigate(Routes.PATIENT_NEW)
                    },
                    onNotificationsClick = { navController.navigate(Routes.NOTIFICATIONS) },
                    onLogout = onLogout
                )
            }

            // ── Super Admin Overview (Super Admin's landing screen) ─────────────
            composable(Routes.SUPER_ADMIN_OVERVIEW) {
                val dashboardVm: DashboardViewModel = viewModel(factory = factory)
                val dailySummaryVm: DailySummaryViewModel = viewModel(factory = factory)
                ReloadOnResume { dashboardVm.load(); dailySummaryVm.load() }
                SuperAdminOverviewScreen(
                    dashboardViewModel = dashboardVm,
                    dailySummaryViewModel = dailySummaryVm,
                    onPatientClick = { patientId ->
                        navController.navigate(Routes.patientProfile(patientId))
                    },
                    onLogout = onLogout
                )
            }

            // ── Todo List (Staff/Supervisor landing screen) ─────────────────────
            composable(Routes.TODO_LIST) {
                val vm: TodoListViewModel = viewModel(factory = factory)
                val notificationVm: NotificationViewModel = viewModel(factory = factory)
                ReloadOnResume { vm.load(); notificationVm.load() }
                TodoListScreen(
                    viewModel = vm,
                    unreadNotifications = notificationVm.unreadCount.collectAsState().value,
                    onNotificationsClick = { navController.navigate(Routes.NOTIFICATIONS) },
                    onTaskClick = { patientId ->
                        navController.navigate(Routes.patientProfile(patientId))
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
                ReloadOnResume { vm.load(vm.startDate.value, vm.endDate.value) }
                SummaryScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onLogout = onLogout,
                    onPatientClick = { patientId ->
                        navController.navigate(Routes.patientProfile(patientId))
                    }
                )
            }

            // ── Medicine (medicine-staff allotment rounds) ────────────────────────
            composable(Routes.MEDICINE) {
                val vm: MedicineViewModel = viewModel(factory = factory)
                val notificationVm: NotificationViewModel = viewModel(factory = factory)
                ReloadOnResume { vm.load(); notificationVm.load() }
                MedicineScreen(
                    viewModel = vm,
                    unreadNotifications = notificationVm.unreadCount.collectAsState().value,
                    onNotificationsClick = { navController.navigate(Routes.NOTIFICATIONS) },
                    onLogout = onLogout
                )
            }

            // ── Scan (batch QR administration) ───────────────────────────────────
            composable(Routes.SCAN) {
                val vm: com.kalazacare.app.ui.ScanViewModel = viewModel(factory = factory)
                val notificationVm: NotificationViewModel = viewModel(factory = factory)
                ReloadOnResume { notificationVm.load() }
                com.kalazacare.app.ui.scan.ScanScreen(
                    viewModel = vm,
                    unreadNotifications = notificationVm.unreadCount.collectAsState().value,
                    onNotificationsClick = { navController.navigate(Routes.NOTIFICATIONS) },
                    onLogout = onLogout
                )
            }

            // ── Notifications ──────────────────────────────────────────────────
            composable(Routes.NOTIFICATIONS) {
                val vm: NotificationViewModel = viewModel(factory = factory)
                ReloadOnResume { vm.load() }
                NotificationScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onLogout = onLogout,
                    onNotificationClick = { route ->
                        navController.popBackStack()
                        onNotificationTarget(route)
                    }
                )
            }
        }
        }
    }
}
