package com.kalazacare.app.ui.photoaudit

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kalazacare.app.ui.PhotoAuditEntry
import com.kalazacare.app.ui.PhotoAuditViewModel
import com.kalazacare.app.ui.components.EmptyState
import com.kalazacare.app.ui.components.KalazaTopBar
import com.kalazacare.app.util.PhotoUploader
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * The only screen the restricted, photo-audit-only Admin role can see — a
 * read-only feed of every medicine allotment/administration evidence photo
 * across all patients, for compliance auditing. No other access.
 */
@Composable
fun PhotoAuditScreen(
    viewModel: PhotoAuditViewModel,
    onLogout: () -> Unit,
) {
    val entries by viewModel.entries.collectAsState()
    val formatter = remember { DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a") }

    Scaffold(
        topBar = {
            KalazaTopBar(title = "Photo Audit", onLogout = onLogout)
        }
    ) { padding ->
        if (entries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                EmptyState(
                    icon = Icons.Outlined.PhotoLibrary,
                    title = "No evidence photos yet",
                    message = "Allotment and administration photos will appear here as staff log doses.",
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(entries) { entry ->
                    PhotoAuditCard(entry, formatter)
                }
            }
        }
    }
}

@Composable
private fun PhotoAuditCard(entry: PhotoAuditEntry, formatter: DateTimeFormatter) {
    val expired = entry.expiresAt.isBefore(LocalDateTime.now())

    // The evidence bucket is private, so the stored value is a bare object
    // path, not a fetchable URL — mint a short-lived signed URL just for this
    // card's lifetime rather than persisting one.
    var signedUrl by remember(entry.photoUrl) { mutableStateOf<String?>(null) }
    LaunchedEffect(entry.photoUrl) {
        signedUrl = runCatching { PhotoUploader.signedUrl(entry.photoUrl) }.getOrNull()
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
            ) {
                if (signedUrl != null) {
                    AsyncImage(
                        model = signedUrl,
                        contentDescription = "${entry.kind} evidence photo",
                        modifier = Modifier.size(56.dp),
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.PhotoCamera,
                            contentDescription = "${entry.kind} evidence photo",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${entry.kind} — ${entry.medicineName}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text("Patient: ${entry.patientName}", style = MaterialTheme.typography.bodySmall)
                Text("By: ${entry.staffName}", style = MaterialTheme.typography.bodySmall)
                Text(
                    text = "Captured: ${entry.capturedAt.format(formatter)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (expired) {
                    Text(
                        text = "Evidence expired (retention window passed)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
