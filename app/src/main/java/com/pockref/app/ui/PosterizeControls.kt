package com.pockref.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val posterizeLevelOptions = listOf(2, 4, 8, 16)

@Composable
internal fun PosterizeLevelButtons(
    selectedLevels: Int?,
    onLevelClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        posterizeLevelOptions.forEach { levels ->
            FilterChip(
                selected = selectedLevels == levels,
                onClick = { onLevelClick(levels) },
                label = {
                    Text(
                        text = "${levels}级",
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                modifier = Modifier
                    .widthIn(min = 48.dp)
                    .heightIn(min = 40.dp),
            )
        }
    }
}
