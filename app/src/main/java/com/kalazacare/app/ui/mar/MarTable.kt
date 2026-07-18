package com.kalazacare.app.ui.mar

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kalazacare.app.data.model.AllotmentStatus
import com.kalazacare.app.data.model.MedStatus
import com.kalazacare.app.data.model.MedicationEntry
import com.kalazacare.app.ui.components.MedStatusBadge
import com.kalazacare.app.ui.theme.KalazaRed
import com.kalazacare.app.ui.theme.OnSurface
import com.kalazacare.app.ui.theme.OnSurfaceVariant
import com.kalazacare.app.ui.theme.White
import com.kalazacare.app.util.DateUtils

@Composable
fun MarTable(
    medications: List<MedicationEntry>,
    onMarkAdministered: (String) -> Unit,
    onRequestAllotment: (MedicationEntry) -> Unit = {},
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(medications) { entry ->
            Card(
                colors = CardDefaults.cardColors(containerColor = White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.medicineName,
                            style = MaterialTheme.typography.titleMedium,
                            color = OnSurface,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Dose: ${entry.dose} • Qty: ${entry.quantity}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Scheduled: ${DateUtils.formatTime(entry.scheduleTime)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = OnSurfaceVariant
                        )
                        if (entry.notes.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Note: ${entry.notes}",
                                style = MaterialTheme.typography.labelSmall,
                                color = OnSurfaceVariant
                            )
                        }
                        if (entry.status == MedStatus.ADMINISTERED && entry.administeredAt != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Administered by ${entry.administeredBy} at ${DateUtils.formatTime(entry.administeredAt.toLocalTime())}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (entry.allotmentStatus == AllotmentStatus.ALLOTTED)
                                "Allotted by ${entry.allottedByName}"
                            else
                                "Not allotted yet",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (entry.allotmentStatus == AllotmentStatus.ALLOTTED)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.error
                        )
                        if (entry.allotmentStatus == AllotmentStatus.NOT_ALLOTTED && entry.status != MedStatus.ADMINISTERED) {
                            TextButton(
                                onClick = { onRequestAllotment(entry) },
                                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp)
                            ) {
                                Text("Request Allotment", color = KalazaRed, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(horizontalAlignment = Alignment.End) {
                        MedStatusBadge(status = entry.status)
                        if (entry.status == MedStatus.PENDING || entry.status == MedStatus.OVERDUE) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { onMarkAdministered(entry.id) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Mark Given")
                            }
                        }
                    }
                }
            }
        }
    }
}
