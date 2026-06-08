package com.pockref.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pockref.app.model.BackgroundPreset

/**
 * Row of preset swatches + a "Custom" chip. Tapping any swatch
 * dispatches a preset selection via [onPreset]. The "Custom" chip
 * opens the HSV wheel inline (handled by the parent
 * [SettingsAppearanceTab] which conditionally renders
 * [CustomColorWheel] when [PockRefUiState.showBackgroundPicker] is
 * true).
 */
@Composable
internal fun BackgroundColorPanel(
 currentColor: Color,
 onPreset: (BackgroundPreset) -> Unit,
) {
 val activePreset = BackgroundPreset.fromColor(currentColor)
 val customDisplayColor = if (activePreset == BackgroundPreset.Custom) currentColor else Color(0xFF808080)
 Row(
 verticalAlignment = Alignment.CenterVertically,
 horizontalArrangement = Arrangement.spacedBy(8.dp),
 ) {
 BackgroundPreset.values().forEach { preset ->
 if (preset == BackgroundPreset.Custom) {
 BackgroundColorChip(
 color = customDisplayColor,
 label = stringResource(preset.labelRes),
 isSelected = activePreset == BackgroundPreset.Custom,
 isCustom = true,
 onClick = { onPreset(preset) },
 )
 } else {
 BackgroundColorChip(
 color = preset.color,
 label = stringResource(preset.labelRes),
 isSelected = activePreset == preset,
 isCustom = false,
 onClick = { onPreset(preset) },
 )
 }
 }
 }
}

@Composable
private fun BackgroundColorChip(
 color: Color,
 label: String,
 isSelected: Boolean,
 isCustom: Boolean,
 onClick: () -> Unit,
) {
 val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color(0x44000000)
 val borderWidth = if (isSelected)2.dp else 1.dp
 Box(
 modifier = Modifier
 .size(30.dp)
 .clip(RoundedCornerShape(8.dp))
 .border(borderWidth, borderColor, RoundedCornerShape(8.dp))
 .background(
 color = if (isCustom) Color.Transparent else color,
 shape = RoundedCornerShape(8.dp),
 )
 .clickable(onClick = onClick),
 contentAlignment = Alignment.Center,
 ) {
 if (isCustom) {
 Box(
 modifier = Modifier
 .fillMaxSize()
 .background(
 brush = Brush.linearGradient(
 colors = listOf(
 color,
 Color(0xFFE53935),
 Color(0xFFFB8C00),
 Color(0xFFFDD835),
 Color(0xFF43A047),
 Color(0xFF1E88E5),
 Color(0xFF8E24AA),
 ),
 ),
 shape = RoundedCornerShape(8.dp),
 ),
 )
 }
 Text(
 text = label,
 color = if (isCustom) Color.Transparent else pickChipLabelColor(color),
 style = MaterialTheme.typography.labelSmall,
 maxLines =1,
 textAlign = TextAlign.Center,
 )
 }
}

/**
 * Pick a black/white label colour based on the swatch luminance so
 * labels stay readable on both light and dark preset colours.
 */
private fun pickChipLabelColor(swatch: Color): Color {
 val argb = swatch.toArgb()
 val r = ((argb shr 16) and 0xFF) / 255f
 val g = ((argb shr 8) and 0xFF) / 255f
 val b = (argb and 0xFF) / 255f
 val luminance =0.299f * r + 0.587f * g + 0.114f * b
 return if (luminance > 0.6f) Color(0xFF111111) else Color(0xFFFFFFFF)
}
