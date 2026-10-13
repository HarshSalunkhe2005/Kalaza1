package com.kalazacare.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kalazacare.app.data.model.DoseTag
import com.kalazacare.app.ui.theme.KalazaRed
import java.time.LocalTime

/** Human-facing label for a [DoseTag] — used everywhere the tag is shown, not just here. */
fun DoseTag.label(): String = when (this) {
    DoseTag.MORNING   -> "Morning"
    DoseTag.AFTERNOON -> "Afternoon"
    DoseTag.EVENING   -> "Evening"
}

/**
 * Whether [time] falls in this tag's expected clock window — Morning
 * 12am–12pm, Afternoon 12pm–5pm, Evening 5pm–12am. Used to warn when a
 * medication's exact [com.kalazacare.app.data.model.MedicationEntry.scheduleTime]
 * and its [DoseTag] disagree (e.g. a 9pm dose tagged Morning), which would
 * otherwise sit silently wrong until a Scan-tab QR mismatch surfaced it.
 */
fun DoseTag.matches(time: LocalTime): Boolean = when (this) {
    DoseTag.MORNING   -> time.hour in 0..11
    DoseTag.AFTERNOON -> time.hour in 12..16
    DoseTag.EVENING   -> time.hour in 17..23
}

/**
 * Required Morning/Afternoon/Evening picker for a medication's [DoseTag].
 * [selected] is nullable so callers can force an explicit choice before save
 * — a medication's tag is mandatory (it's what the Scan-tab QR match is
 * keyed on), so there's no default that would silently be wrong.
 */
@Composable
fun DoseTagPicker(
    selected: DoseTag?,
    onSelect: (DoseTag) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DoseTag.values().forEach { tag ->
            FilterChip(
                selected = selected == tag,
                onClick = { onSelect(tag) },
                // A couple of extra dp of chip height so descenders (the "g" in
                // "Evening") render in full instead of getting clipped at the
                // chip's default tight bounds.
                modifier = Modifier.heightIn(min = 36.dp),
                label = { Text(tag.label()) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = KalazaRed,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    labelColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        }
    }
}
