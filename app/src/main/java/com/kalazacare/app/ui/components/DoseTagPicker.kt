package com.kalazacare.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kalazacare.app.data.model.DoseTag
import com.kalazacare.app.ui.theme.KalazaRed

/** Human-facing label for a [DoseTag] — used everywhere the tag is shown, not just here. */
fun DoseTag.label(): String = when (this) {
    DoseTag.MORNING   -> "Morning"
    DoseTag.AFTERNOON -> "Afternoon"
    DoseTag.EVENING   -> "Evening"
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
