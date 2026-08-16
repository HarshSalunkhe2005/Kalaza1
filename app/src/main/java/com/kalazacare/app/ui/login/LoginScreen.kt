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
import com.kalazacare.app.ui.BypassState
import com.kalazacare.app.ui.LoginState
import com.kalazacare.app.ui.LoginViewModel
import com.kalazacare.app.ui.components.KalazaTextField
import com.kalazacare.app.ui.theme.KalazaRed
import com.kalazacare.app.util.ALLOWED_GATEWAY_IPS
import com.kalazacare.app.util.WIFI_GATE_ENABLED
import com.kalazacare.app.util.currentWifiGatewayIp
import kotlinx.coroutines.delay

private enum class WifiGateState { CHECKING, ALLOWED, WRONG_NETWORK }

/**
 * Collapsed by default (a single text link) so the dialog reads cleanly for the
 * overwhelming majority of staff who'll never need it. Expanding it reveals its own
 * Name/Password fields rather than reusing the ones on the login form behind it —
 * this dialog is a separate modal window, so those fields can't be reached anyway
 * (see the old TestingSkipSwitchRow this replaced for the same note). Replaces the
 * previous "Skip Wi-Fi check (testing)" switch, which any staff account could flip
 * with zero verification — this re-checks real credentials and the Super Admin role
 * before letting anyone through.
 */
@Composable
private fun SuperAdminBypassSection(
    bypassState: BypassState,
    onAttempt: (name: String, password: String) -> Unit,
    onReset: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    if (!expanded) {
        TextButton(
            onClick = { expanded = true },
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text("Super Admin? Bypass with your password", style = MaterialTheme.typography.labelMedium)
        }
        return
    }

    Column(modifier = Modifier.padding(top = 8.dp).fillMaxWidth()) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        if (bypassState is BypassState.Error) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(bypassState.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = { expanded = false; name = ""; password = ""; onReset() }) { Text("Cancel") }
            Spacer(modifier = Modifier.width(4.dp))
            Button(
                onClick = { onAttempt(name, password) },
                enabled = bypassState != BypassState.Loading,
                colors = ButtonDefaults.buttonColors(containerColor = KalazaRed),
            ) {
                if (bypassState == BypassState.Loading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Bypass")
                }
            }
        }
    }
}

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onLoginSuccess: () -> Unit
) {
    val loginState by viewModel.loginState.collectAsState()
    val bypassState by viewModel.bypassState.collectAsState()
    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val context = LocalContext.current
    var gateState by remember { mutableStateOf(if (WIFI_GATE_ENABLED) WifiGateState.CHECKING else WifiGateState.ALLOWED) }
    var detectedGatewayIp by remember { mutableStateOf<String?>(null) }
    // A successful Super Admin bypass (see SuperAdminBypassSection) signs the user
    // straight in via loginState becoming Success, so the Wi-Fi gate itself never
    // needs to flip to ALLOWED for that path — this is just the gate's own state.
    val effectiveGateState = gateState

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
                            SuperAdminBypassSection(
                                bypassState = bypassState,
                                onAttempt = { n, p -> viewModel.attemptSuperAdminBypass(n, p) },
                                onReset = { viewModel.resetBypassState() },
                            )
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
