package com.pockref.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * A single row in the settings sheet — title on top, helper text below,
 * Switch on the right. Mirrors the M3 list-item pattern with a small
 * vertical padding gap so the rows feel separated without using
 * dividers.
 *
 * Originally lived inline in [SettingsPanel] (PureRefApp.kt). Extracted
 * here so every tab (通用 /外观 / 手势) shares the same row geometry
 * and a future per-row tweak (e.g. accessibility / typography pass) is
 * one edit instead of N.
 */
@Composable
internal fun SettingsToggleRow(
 title: String,
 subtitle: String,
 checked: Boolean,
 onCheckedChange: (Boolean) -> Unit,
) {
 Row(
 modifier = Modifier.fillMaxWidth(),
 verticalAlignment = Alignment.CenterVertically,
 ) {
 Column(modifier = Modifier.weight(1f)) {
 Text(text = title, style = MaterialTheme.typography.titleSmall)
 Text(
 text = subtitle,
 style = MaterialTheme.typography.bodySmall,
 color = MaterialTheme.colorScheme.onSurfaceVariant,
 )
 }
 Switch(
 checked = checked,
 onCheckedChange = onCheckedChange,
 )
 }
}
