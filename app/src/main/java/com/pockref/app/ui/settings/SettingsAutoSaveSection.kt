package com.pockref.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.pockref.app.data.AutoSaveManager
import com.pockref.app.model.PockRefUiState
import com.pockref.app.ui.PockRefAction
import kotlin.math.abs

/**
 * Auto-save master switch + interval slider. Extracted from the old
 * monolithic [com.pockref.app.ui.SettingsPanel] (was inside
 * PureRefApp.kt) so the sheet renders uniformly under both the
 * phone (TabRow on top) and the tablet (NavigationRail on the left)
 * layouts.
 *
 * Layout:
 * ```
 * [Switch row] 启用自动保存 / subtitle: 已开启 ↔ 已关闭
 * [Label]   当前:X 分钟 (greyed out when switch off)
 * [Slider]  discrete stops,1 /5 /10 /15 / … /60 min
 * (13 entries; see [AutoSaveManager.INTERVAL_STOPS_MINUTES])
 * `enabled = state.autoSaveEnabled` so flipping the
 * switch off also dims the slider + refuses touch.
 * [Text]    上次自动保存:[刚刚 / N 秒前 / N 分钟前 / …]   (only when on)
 * ```
 *
 * Slider uses an *index-based* range
 * (`0f..(stops.size -1).toFloat()`, `steps = stops.size -2`)
 * rather than a millisecond-based range — this matches the contract
 * documented in [AutoSaveManager.INTERVAL_STOPS_MINUTES]. Going
 * index-based keeps the auto-save manager as the single source of
 * truth for the available intervals; a future "add90 min /120 min
 * stop" edit is one line in that file.
 *
 * Source of truth: [PockRefUiState.autoSaveIntervalMs]. The slider
 * reads the persisted ms value from uiState (not the
 * SharedPreferences directly) so dragging the slider triggers a
 * recomposition and the thumb visibly moves. The auto-save coroutine
 * itself still reads the pref directly on every cycle, so slider
 * position == the value the next save will use.
 */
@Composable
internal fun SettingsAutoSaveSection(
 state: PockRefUiState,
 onAction: (PockRefAction) -> Unit,
) {
 val stops = AutoSaveManager.INTERVAL_STOPS_MINUTES
 val currentAutoSaveMs = state.autoSaveIntervalMs

 // Snap the persisted ms value to the nearest stop's index. If a
 // future code path writes an out-of-stops value (e.g. a debug
 // "save every5s" toggle), the slider rounds to the closest stop
 // rather than throwing or showing a position outside its
 // [valueRange].
 val currentIndex = stops
 .mapIndexed { index, minutes -> index to minutes *60_000L }
 .minByOrNull { abs(it.second - currentAutoSaveMs) }
 ?.first
 ?:0

 Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
 // Master switch on top so it's the first thing the user sees
 // (matches吸附图像 /吸附旋转 which all put the Switch on the
 // same row as the title via [SettingsToggleRow]).
 SettingsToggleRow(
 title = "自动保存",
 subtitle = if (state.autoSaveEnabled) "已开启,定时把画布快照写到磁盘" else "已关闭,画布不会自动保存",
 checked = state.autoSaveEnabled,
 onCheckedChange = { checked ->
 onAction(PockRefAction.SetAutoSaveEnabled(checked))
 },
 )
 // Dim the value label together with the slider so the user sees
 // the whole interval control "go dark" when they flip the master
 // switch off. `MaterialTheme.colorScheme.onSurface` at0.38 alpha
 // is the standard M3 disabled affordance (matches what the system
 // Slider uses internally for its disabled state).
 Text(
 text = "当前:${stops[currentIndex]} 分钟",
 style = MaterialTheme.typography.bodySmall,
 color = if (state.autoSaveEnabled) {
 MaterialTheme.colorScheme.onSurface
 } else {
 MaterialTheme.colorScheme.onSurface.copy(alpha =0.38f)
 },
 )
 Slider(
 value = currentIndex.toFloat(),
 onValueChange = { newValue ->
 val snappedIndex = newValue.toInt().coerceIn(0, stops.size -1)
 val snappedMs = stops[snappedIndex] *60_000L
 onAction(PockRefAction.SetAutoSaveIntervalMs(snappedMs))
 },
 valueRange =0f..(stops.size -1).toFloat(),
 //13 stops →11 internal divisions.
 // `steps = size -2` matches the INTERVAL_STOPS_MINUTES contract.
 steps = stops.size -2,
 enabled = state.autoSaveEnabled,
 )
 // Show when the most recent auto-save ran so the user can tell
 // at a glance whether the feature is actually doing its job.
 // Hidden when the master switch is off — the disabled subtitle
 // already covers that case; showing a stale "last saved" would
 // be misleading.
 if (state.autoSaveEnabled) {
 val lastSaved = state.lastAutoSaveAt
 Text(
 text = if (lastSaved == null) {
 "上次自动保存:从未(画布有改动时,会在 ${stops[currentIndex]}分钟后自动写盘)"
 } else {
 "上次自动保存:" + formatRelativeAge(
 lastSaved,
 System.currentTimeMillis(),
 )
 },
 style = MaterialTheme.typography.bodySmall,
 color = MaterialTheme.colorScheme.onSurfaceVariant,
 )
 }
 }
}

/**
 * Format a wall-clock age as a human-friendly relative duration
 * ("刚刚" / "12秒前" / "5分钟前" / "3小时前" / "2天前"). Bucketing
 * is60-second granularity up to1 hour, then hours, then days,
 * then falls back to an absolute timestamp for anything older than
 *30 days. Negative diffs (clock skew — the auto-save's
 * `System.currentTimeMillis()` is ahead of the renderer's) clamp
 * to "刚刚" rather than emitting a negative number.
 *
 * Self-contained (file-private) helper — duplicated here from the
 * original `formatRelativeAge` in
 * [com.pockref.app.ui.PureRefApp.kt] so this section doesn't have
 * to reach into the main file. Both implementations are kept in
 * sync; if either is changed the other should be too.
 */
private fun formatRelativeAge(thenMs: Long, nowMs: Long): String {
 val diffMs = (nowMs - thenMs).coerceAtLeast(0L)
 val seconds = diffMs /1000L
 return when {
 seconds <5L -> "刚刚"
 seconds <60L -> "${seconds}秒前"
 seconds <3600L -> "${seconds /60L}分钟前"
 seconds <86_400L -> "${seconds /3600L}小时前"
 seconds <30L *86_400L -> "${seconds /86_400L}天前"
 else -> formatTimestamp(thenMs)
 }
}

/**
 * Absolute timestamp formatter used as the long-tail fallback for
 * [formatRelativeAge]. Pure helper, no Compose dependency. Empty
 * input clamps to "" (matches the original
 * `formatTimestamp` behaviour in [com.pockref.app.ui.PureRefApp.kt]).
 */
private fun formatTimestamp(timestamp: Long): String {
 if (timestamp <=0L) return ""
 val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
 return sdf.format(java.util.Date(timestamp))
}
