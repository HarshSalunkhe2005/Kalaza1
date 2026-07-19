package com.kalazacare.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kalazacare.app.ui.theme.KalazaRed
import java.time.LocalTime

/**
 * 12-hour clock input (HH : MM + AM/PM) that reports the result as a 24-hour
 * [LocalTime] via [onChange] — callers never deal with 24-hour text.
 */
@Composable
fun TimeOfDayField(
    initial: LocalTime,
    onChange: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    val initialHour12 = when (val h = initial.hour % 12) { 0 -> 12; else -> h }
    var hourText by remember { mutableStateOf(initialHour12.toString()) }
    var minuteText by remember { mutableStateOf(initial.minute.toString().padStart(2, '0')) }
    var isPm by remember { mutableStateOf(initial.hour >= 12) }

    fun emit() {
        val hour12 = hourText.toIntOrNull()?.coerceIn(1, 12) ?: initialHour12
        val minute = minuteText.toIntOrNull()?.coerceIn(0, 59) ?: initial.minute
        val hour24 = when {
            hour12 == 12 && !isPm -> 0
            hour12 == 12 && isPm  -> 12
            isPm                  -> hour12 + 12
            else                  -> hour12
        }
        onChange(LocalTime.of(hour24, minute))
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = hourText,
            onValueChange = { hourText = it.filter { c -> c.isDigit() }.take(2); emit() },
            label = { Text("HH") },
            modifier = Modifier.width(72.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Text(":", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = minuteText,
            onValueChange = { minuteText = it.filter { c -> c.isDigit() }.take(2); emit() },
            label = { Text("MM") },
            modifier = Modifier.width(72.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Spacer(modifier = Modifier.width(4.dp))
        FilterChip(
            selected = !isPm,
            onClick = { isPm = false; emit() },
            label = { Text("AM") },
            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = KalazaRed.copy(alpha = 0.15f)),
        )
        FilterChip(
            selected = isPm,
            onClick = { isPm = true; emit() },
            label = { Text("PM") },
            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = KalazaRed.copy(alpha = 0.15f)),
        )
    }
}
