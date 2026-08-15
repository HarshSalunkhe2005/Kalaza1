package com.kalazacare.app.ui.login

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.kalazacare.app.R
import com.kalazacare.app.ui.LoginState
import com.kalazacare.app.ui.LoginViewModel
import com.kalazacare.app.ui.components.KalazaTextField
import com.kalazacare.app.ui.theme.KalazaRed
import com.kalazacare.app.util.ALLOWED_GATEWAY_IPS
import com.kalazacare.app.util.WIFI_GATE_ENABLED
import com.kalazacare.app.util.currentWifiGatewayIp
import com.kalazacare.app.util.wifiDebugDump
import kotlinx.coroutines.delay

private enum class WifiGateState { CHECKING, ALLOWED, WRONG_NETWORK }

/**
 * Testing-only bypass switch. Placed inside each blocking dialog's own content (not just
 * floating behind it) because a real AlertDialog is a separate modal window -- nothing behind
 * it, including a switch floating elsewhere in the same Box, can receive touches.
 */
@Composable
private fun TestingSkipSwitchRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
        Text(
            text = "Skip Wi-Fi check (testing)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = KalazaRed,
                checkedBorderColor = KalazaRed,
                uncheckedThumbColor = KalazaRed,
                uncheckedTrackColor = MaterialTheme.colorScheme.surface,
                uncheckedBorderColor = KalazaRed,
            ),
        )
    }
}

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onLoginSuccess: () -> Unit
) {
    val loginState by viewModel.loginState.collectAsState()
    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val context = LocalContext.current
    var gateState by remember { mutableStateOf(if (WIFI_GATE_ENABLED) WifiGateState.CHECKING else WifiGateState.ALLOWED) }
    var detectedGatewayIp by remember { mutableStateOf<String?>(null) }
    // On-screen override for quick testing -- tap the switch at the bottom of the screen to skip
    // the whole Wi-Fi gate without touching code. Resets to off every fresh app launch.
    var skipWifiCheckForTesting by remember { mutableStateOf(false) }
    val effectiveGateState = if (skipWifiCheckForTesting) WifiGateState.ALLOWED else gateState

    fun checkWifiNow() {
        detectedGatewayIp = currentWifiGatewayIp(context)
        gateState = if (detectedGatewayIp != null && ALLOWED_GATEWAY_IPS.contains(detectedGatewayIp)) {
            WifiGateState.ALLOWED
        } else {
            WifiGateState.WRONG_NETWORK
        }
    }

    LaunchedEffect(Unit) {
        if (!WIFI_GATE_ENABLED) return@LaunchedEffect
        delay(500) // brief, deliberate pause so the "checking" spinner is actually visible
        checkWifiNow()
    }

    LaunchedEffect(loginState) {
        if (loginState is LoginState.Success) {
            onLoginSuccess()
            viewModel.resetState()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .systemBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
                .blur(if (effectiveGateState == WifiGateState.ALLOWED) 0.dp else 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo area
            Image(
                painter = painterResource(id = R.drawable.logo_kalaza),
                contentDescription = "Kalaza Care Logo",
                modifier = Modifier.size(120.dp).clip(RoundedCornerShape(16.dp)),
            )

            Text(
                text = "Kalaza Care",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = KalazaRed
            )
            Text(
                text = "A Place Like Home",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Error message
            if (loginState is LoginState.Error) {
                Text(
                    text = (loginState as LoginState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            // Input fields
            KalazaTextField(
                value = name,
                onValueChange = { name = it },
                label = "Name",
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = "Name") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )

            Spacer(modifier = Modifier.height(16.dp))

            KalazaTextField(
                value = password,
                onValueChange = { password = it },
                label = "Password",
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Password") },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password"
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Login button
            Button(
                onClick = { viewModel.login(name, password) },
                enabled = effectiveGateState == WifiGateState.ALLOWED,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = KalazaRed),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (loginState is LoginState.Loading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Text(
                        text = "LOGIN",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        when (effectiveGateState) {
            WifiGateState.CHECKING -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = KalazaRed)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Checking Wi-Fi network…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            WifiGateState.WRONG_NETWORK -> {
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text("Wrong Wi-Fi Network") },
                    text = {
                        Column(modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                            Text("Please connect to the facility's Wi-Fi network to continue.")
                            // Raw SDK/manufacturer/permission/gateway-IP dump was shown to every
                            // staff member unconditionally — meaningless (and slightly leaky)
                            // for actual end users. Debug builds still get it for troubleshooting.
                            if (com.kalazacare.app.BuildConfig.DEBUG) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Debug info:\n" + wifiDebugDump(context),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            TestingSkipSwitchRow(skipWifiCheckForTesting) { skipWifiCheckForTesting = it }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) },
                            colors = ButtonDefaults.buttonColors(containerColor = KalazaRed),
                        ) { Text("Open Wi-Fi Settings") }
                    },
                    dismissButton = {
                        TextButton(onClick = { checkWifiNow() }) { Text("Retry") }
                    },
                )
            }
            WifiGateState.ALLOWED -> {}
        }
    }
}
