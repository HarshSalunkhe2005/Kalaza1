package com.kalazacare.app.ui.config

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kalazacare.app.data.model.DoseTag
import com.kalazacare.app.ui.components.TimeOfDayField
import com.kalazacare.app.ui.components.label
import com.kalazacare.app.ui.theme.KalazaRed
import com.kalazacare.app.util.RoundWindow
import java.time.LocalTime

/**
 * Config → Round Times: the window each round's doses can be given in. Strict — outside it the
 * Scan tab locks the dose and, once it closes, an ungiven dose turns Missed. Reminders are NOT
 * affected (they follow each medicine's own time). Applies to every phone on its next login.
 */
@Composable
fun RoundTimesEditor(
    windows: Map<DoseTag, RoundWindow>,
    onSave: (DoseTag, LocalTime, LocalTime, (Boolean, String) -> Unit) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "Doses can only be given inside their round's window. After it closes, an ungiven dose is marked Missed. " +
                    "Reminders still follow each medicine's own time.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(DoseTag.values().toList(), key = { it.name }) { tag ->
            val current = windows[tag] ?: return@items
            // Re-created whenever the saved value changes, so the fields reset to what's stored.
            key(tag, current) { RoundRow(tag, current, onSave) }
        }
    }
}

@Composable
private fun RoundRow(
    tag: DoseTag,
    current: RoundWindow,
    onSave: (DoseTag, LocalTime, LocalTime, (Boolean, String) -> Unit) -> Unit,
) {
    var start by remember { mutableStateOf(current.start) }
    var end by remember { mutableStateOf(current.end) }
    var message by remember { mutableStateOf<String?>(null) }
    val valid = start.isBefore(end)
    val changed = start != current.start || end != current.end

    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tag.label(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Opens", style = MaterialTheme.typography.labelMedium)
            TimeOfDayField(initial = start, onChange = { start = it; message = null })
            Text("Closes", style = MaterialTheme.typography.labelMedium)
            TimeOfDayField(initial = end, onChange = { end = it; message = null })
            if (!valid) {
                Text("It must close after it opens.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
            message?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
            Button(
                onClick = { onSave(tag, start, end) { _, text -> message = text } },
                enabled = valid && changed,
                colors = ButtonDefaults.buttonColors(containerColor = KalazaRed),
            ) { Text("Save") }
        }
    }
}
