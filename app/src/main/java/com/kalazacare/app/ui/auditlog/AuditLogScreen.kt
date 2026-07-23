package com.kalazacare.app.ui.auditlog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kalazacare.app.data.model.AuditLogEntry
import com.kalazacare.app.ui.AuditLogViewModel
import com.kalazacare.app.ui.components.EmptyState
import com.kalazacare.app.ui.components.KalazaTopBar
import com.kalazacare.app.ui.theme.KalazaRed
import com.kalazacare.app.util.timeAgo

@Composable
fun AuditLogScreen(
    viewModel: AuditLogViewModel,
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    val logs by viewModel.logs.collectAsState()

    Scaffold(
        topBar = {
            KalazaTopBar(
                title = "Audit Log",
                onBack = onBack,
                onLogout = onLogout
            )
        }
    ) { innerPadding ->
        if (logs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                EmptyState(
                    title = "No Logs",
                    message = "No audit logs available", 
                    icon = Icons.Default.Edit
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                items(logs) { log ->
                    AuditLogItem(log)
                }
            }
        }
    }
}

@Composable
private fun AuditLogItem(log: AuditLogEntry) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(KalazaRed.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getIconForName(log.iconName),
                    contentDescription = log.action,
                    tint = KalazaRed,
                    modifier = Modifier.size(16.dp)
                )
            }
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(64.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f).padding(bottom = 24.dp)) {
            Text(
                text = log.action,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${log.performedByName} → ${log.targetPatientName.ifBlank { "System" }}",
                style = MaterialTheme.typography.bodyMedium,
                color = KalazaRed
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = log.details,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = log.timestamp.timeAgo(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

private fun getIconForName(name: String): ImageVector {
    return when (name) {
        "person_add"   -> Icons.Default.PersonAdd
        "person"       -> Icons.Default.Person
        "medication"   -> Icons.Default.Medication
        "monitor_heart" -> Icons.Default.MonitorHeart
        "check_circle" -> Icons.Default.CheckCircle
        "cancel"       -> Icons.Default.Cancel
        "note_add"     -> Icons.Default.NoteAdd
        "archive"      -> Icons.Default.Archive
        else -> Icons.Default.Edit
    }
}
