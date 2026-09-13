package com.kalazacare.app.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.kalazacare.app.KalazaApp
import com.kalazacare.app.service.EXTRA_TARGET_ROUTE
import com.kalazacare.app.ui.navigation.KalazaNavHost
import com.kalazacare.app.ui.theme.KalazaRed
import com.kalazacare.app.ui.theme.KalazaTheme
import com.kalazacare.app.util.AppErrors
import com.kalazacare.app.util.SessionManager

class MainActivity : ComponentActivity() {
    private var pendingRouteState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Blocks screenshots and screen recording, and shows a blank thumbnail in the
        // Recents switcher — patient medical/personal data appears on nearly every
        // screen of this single-Activity app (Dashboard, Med tab, Summary, Scan, Audit
        // Log...), so this is applied window-wide rather than toggled per-screen.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        pendingRouteState.value = intent?.getStringExtra(EXTRA_TARGET_ROUTE)

        // ── Hide system navigation bar (Home / Back / Recent buttons) ──
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.hide(WindowInsetsCompat.Type.navigationBars())
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        setContent {
            KalazaTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // SessionManager.currentStaff is in-memory only — it does NOT survive the
                    // OS killing this process in the background, unlike Supabase Auth's own
                    // session (persisted by the SDK) and Compose Navigation's back stack (restored
                    // from saved state). Without this gate, a process restart could resurrect the
                    // user straight onto e.g. the Scan tab with a null SessionManager, silently
                    // attributing every write to "Unknown" instead of their real name. Block
                    // rendering the (possibly restored) nav graph until this resolves one way or
                    // the other: either SessionManager gets repopulated from the still-valid
                    // Supabase session, or KalazaNavHost's own guard below sends them to Login.
                    var sessionReady by remember { mutableStateOf(false) }
                    val app = LocalContext.current.applicationContext as KalazaApp
                    LaunchedEffect(Unit) {
                        if (SessionManager.getCurrentStaff() == null) {
                            runCatching { app.authRepository.restoreSession() }
                                .getOrNull()
                                ?.let { SessionManager.setCurrentStaff(it) }
                        }
                        sessionReady = true
                    }

                    if (!sessionReady) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = KalazaRed)
                        }
                        return@Surface
                    }

                    val requestNotificationPermission = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { /* no-op either way — notifications just won't show if denied */ }

                    // Single collector for every ViewModel's safeLaunch failures — see
                    // AppErrors.kt. Without this, a caught-but-unreported exception would
                    // fail exactly as silently as an uncaught one used to crash loudly.
                    val toastContext = LocalContext.current
                    LaunchedEffect(Unit) {
                        AppErrors.events.collect { message ->
                            Toast.makeText(toastContext, message, Toast.LENGTH_LONG).show()
                        }
                    }

                    LaunchedEffect(Unit) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val granted = ContextCompat.checkSelfPermission(
                                this@MainActivity, Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED
                            if (!granted) requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }

                    val pendingRoute by pendingRouteState
                    KalazaNavHost(
                        pendingDeepLink = pendingRoute,
                        onDeepLinkConsumed = { pendingRouteState.value = null },
                    )
                }
            }
        }
    }

    // MainActivity is launchMode="singleTask" (see manifest) — a notification tap while
    // the app's already running arrives here instead of spawning a new instance.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingRouteState.value = intent.getStringExtra(EXTRA_TARGET_ROUTE)
    }
}
