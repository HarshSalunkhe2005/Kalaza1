package com.kalazacare.app.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kalazacare.app.ui.theme.KalazaRed
import com.kalazacare.app.ui.theme.StatusSuccess
import com.kalazacare.app.ui.theme.White
import com.kalazacare.app.util.PhotoUploader
import com.kalazacare.app.util.createCaptureUri
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * Photo-evidence gate used before marking a dose allotted or administered.
 * Launches the real camera, uploads the capture to Firebase Storage, and only
 * enables Confirm once that upload succeeds — [onConfirm] receives the real
 * download URL and expiry, not a mock one.
 */
@Composable
fun PhotoConfirmDialog(
    title: String,
    message: String,
    onConfirm: (photoUrl: String, expiresAt: LocalDateTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var captureUri by remember { mutableStateOf<Uri?>(null) }
    var isUploading by remember { mutableStateOf(false) }
    var uploaded by remember { mutableStateOf<Pair<String, LocalDateTime>?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = captureUri
        if (success && uri != null) {
            isUploading = true
            errorMessage = null
            scope.launch {
                try {
                    val evidence = PhotoUploader.upload(uri)
                    uploaded = evidence.url to evidence.expiresAt
                } catch (_: Exception) {
                    errorMessage = "Upload failed — try again"
                } finally {
                    isUploading = false
                }
            }
        }
    }

    fun launchCamera() {
        val uri = createCaptureUri(context)
        captureUri = uri
        takePicture.launch(uri)
    }

    val requestCameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else errorMessage = "Camera permission is needed to capture evidence"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(16.dp))
                when {
                    uploaded != null -> {
                        captureUri?.let { uri ->
                            AsyncImage(
                                model = uri,
                                contentDescription = "Captured evidence photo",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusSuccess)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Photo uploaded — auto-deleted after 48h",
                                style = MaterialTheme.typography.labelMedium,
                                color = StatusSuccess,
                            )
                        }
                    }
                    isUploading -> {
                        Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = KalazaRed)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Uploading photo…", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    else -> {
                        OutlinedButton(onClick = {
                            val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                                PackageManager.PERMISSION_GRANTED
                            if (hasPermission) launchCamera() else requestCameraPermission.launch(Manifest.permission.CAMERA)
                        }) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Take Photo")
                        }
                        if (errorMessage != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(errorMessage!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { uploaded?.let { (url, expiresAt) -> onConfirm(url, expiresAt) } },
                enabled = uploaded != null,
                colors = ButtonDefaults.buttonColors(containerColor = KalazaRed, contentColor = White),
            ) { Text("Confirm") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
