package com.kalazacare.app.ui.utility

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kalazacare.app.data.model.UtilityItem
import com.kalazacare.app.data.model.UtilityRecord
import com.kalazacare.app.ui.components.EmptyState
import com.kalazacare.app.ui.theme.KalazaRed
import com.kalazacare.app.ui.theme.OnSurface
import com.kalazacare.app.ui.theme.SurfaceVariant
import com.kalazacare.app.ui.theme.White
import com.kalazacare.app.util.DateUtils

/**
 * Columns are built from [items] (the configurable list from Config → Utility
 * Items) rather than a fixed set, so a newly added item type shows up here
 * immediately without a code change.
 */
@Composable
fun UtilityTable(
    records: List<UtilityRecord>,
    items: List<UtilityItem>,
    modifier: Modifier = Modifier,
    onEdit: (UtilityRecord) -> Unit = {},
) {
    val scrollState = rememberScrollState()

    Column(modifier = modifier) {
        // Table Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(KalazaRed)
                .horizontalScroll(scrollState)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderCell("Date", width = 100.dp)
            HeaderCell("Time", width = 80.dp)
            items.forEach { item -> HeaderCell(item.name, width = 110.dp) }
            HeaderCell("Issued To", width = 120.dp)
            HeaderCell("Issued By", width = 120.dp)
            HeaderCell("Checked By", width = 120.dp)
            HeaderCell("", width = 56.dp)
        }

        // Table Body
        if (records.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Inventory2,
                title = "No Utility Records",
                message = "Log the first entry using the + button.",
                modifier = Modifier.weight(1f),
            )
            return@Column
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(records) { index, record ->
                val backgroundColor = if (index % 2 == 0) White else SurfaceVariant

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(backgroundColor)
                        .horizontalScroll(scrollState)
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DataCell(DateUtils.formatDate(record.date), width = 100.dp)
                    DataCell(DateUtils.formatTime(record.time), width = 80.dp)
                    items.forEach { item ->
                        val qty = record.quantities[item.id] ?: 0
                        DataCell(if (qty > 0) qty.toString() else "-", width = 110.dp)
                    }
                    DataCell(record.issuedToCaregiver, width = 120.dp)
                    DataCell(record.issuedBySupervisor, width = 120.dp)
                    DataCell(record.checkedBy, width = 120.dp)
                    IconButton(onClick = { onEdit(record) }, modifier = Modifier.width(56.dp)) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit utility record", tint = KalazaRed)
                    }
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
private fun DataCell(text: String, width: androidx.compose.ui.unit.Dp) {
    Text(
        text = text.ifBlank { "-" },
        color = OnSurface,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .width(width)
            .padding(horizontal = 16.dp)
    )
}
