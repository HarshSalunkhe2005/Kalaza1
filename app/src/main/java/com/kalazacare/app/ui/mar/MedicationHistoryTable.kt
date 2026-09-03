package com.kalazacare.app.ui.mar

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kalazacare.app.data.model.AdministrationOutcome
import com.kalazacare.app.data.model.MedicationHistoryEntry
import com.kalazacare.app.ui.components.EmptyState
import com.kalazacare.app.ui.components.label
import com.kalazacare.app.ui.theme.KalazaRed
import com.kalazacare.app.ui.theme.OnSurface
import com.kalazacare.app.ui.theme.SurfaceVariant
import com.kalazacare.app.ui.theme.White
import com.kalazacare.app.util.DateUtils

/**
 * Per-day given/missed outcomes for a patient's medications — the counterpart to [MarTable]'s
 * live "today" view. Structurally identical to ui/utility/UtilityTable.kt (same header/row
 * banding/horizontal-scroll shape) for visual consistency across the app's data tables.
 */
@Composable
fun MedicationHistoryTable(
    entries: List<MedicationHistoryEntry>,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(KalazaRed)
                .horizontalScroll(scrollState)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderCell("Date", width = 100.dp)
            HeaderCell("Medicine", width = 160.dp)
            HeaderCell("Dose", width = 100.dp)
            HeaderCell("Tag", width = 100.dp)
            HeaderCell("Status", width = 110.dp)
            HeaderCell("By", width = 120.dp)
            HeaderCell("Time", width = 90.dp)
        }

        if (entries.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.History,
                title = "No History Yet",
                message = "Given/missed doses will appear here once at least one day has passed.",
                modifier = Modifier.weight(1f),
            )
            return@Column
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(entries) { index, entry ->
                val backgroundColor = if (index % 2 == 0) White else SurfaceVariant
                val statusColor = if (entry.status == AdministrationOutcome.ADMINISTERED)
                    MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(backgroundColor)
                        .horizontalScroll(scrollState)
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DataCell(DateUtils.formatDate(entry.date), width = 100.dp)
                    DataCell(entry.medicineName, width = 160.dp)
                    DataCell(entry.dose, width = 100.dp)
                    DataCell(entry.tag.label(), width = 100.dp)
                    DataCell(
                        if (entry.status == AdministrationOutcome.ADMINISTERED) "Given" else "Missed",
                        width = 110.dp, color = statusColor,
                    )
                    DataCell(entry.administeredBy, width = 120.dp)
                    DataCell(
                        entry.administeredAt?.toLocalTime()?.let { DateUtils.formatTime(it) } ?: "-",
                        width = 90.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderCell(text: String, width: androidx.compose.ui.unit.Dp) {
    Text(
        text = text,
        color = White,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier
            .width(width)
            .padding(horizontal = 16.dp)
    )
}

@Composable
private fun DataCell(text: String, width: androidx.compose.ui.unit.Dp, color: Color = OnSurface) {
    Text(
        text = text.ifBlank { "-" },
        color = color,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .width(width)
            .padding(horizontal = 16.dp)
    )
}
