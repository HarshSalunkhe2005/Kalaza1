package com.kalazacare.app.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.kalazacare.app.service.EXTRA_TARGET_ROUTE
import com.kalazacare.app.ui.navigation.KalazaNavHost
import com.kalazacare.app.ui.theme.KalazaTheme

class MainActivity : ComponentActivity() {
    private var pendingRouteState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingRouteState.value = intent?.getStringExtra(EXTRA_TARGET_ROUTE)

        // ── Hide system navigation bar (Home / Back / Recent buttons) ──
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.hide(WindowInsetsCompat.Type.navigationBars())
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        setContent {
            KalazaTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val requestNotificationPermission = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { /* no-op either way — notifications just won't show if denied */ }

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
