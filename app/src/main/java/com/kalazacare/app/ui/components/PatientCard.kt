package com.kalazacare.app.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kalazacare.app.data.model.Gender
import com.kalazacare.app.data.model.Patient
import com.kalazacare.app.ui.theme.*
import com.kalazacare.app.util.DateUtils
import com.kalazacare.app.util.toInitials

@Composable
fun PatientCard(
    patient: Patient,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {

            // Red left accent strip
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(
                        color = if (patient.gender == Gender.MALE) KalazaRed else KalazaLightRed,
                        shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {

                // Avatar with initials
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Text(
                        text  = patient.name.toInitials(),
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = KalazaDarkMaroon,
                            fontWeight = FontWeight.Bold,
                        )
                    )
                }

                Spacer(Modifier.width(14.dp))

                // Patient info
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text  = patient.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = OnSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (patient.isArchived) {
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(50.dp),
                            ) {
                                Text(
                                    text = "Archived",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = OnSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text  = "${patient.age} yrs • ${patient.gender.name.lowercase().replaceFirstChar { it.uppercase() }} • Room ${patient.roomNo}",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    // Diagnosis badge
                    if (patient.primaryDiagnosis.isNotBlank()) {
                        Surface(
                            color  = MaterialTheme.colorScheme.primaryContainer,
                            shape  = RoundedCornerShape(50.dp),
                        ) {
                            Text(
                                text     = patient.primaryDiagnosis,
                                style    = MaterialTheme.typography.labelSmall,
                                color    = KalazaDarkMaroon,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))

                // Admission date
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text  = "Since",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceVariant,
                    )
                    Text(
                        text  = DateUtils.formatDateLong(patient.admissionDate),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = OnSurface,
                    )
                }
            }
        }
    }
}
