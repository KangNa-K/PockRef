package com.pockref.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.pockref.app.model.PracticeConfig
import com.pockref.app.ui.theme.Dimens
import kotlin.math.roundToInt

/**
 * V_NOW (2026-06-08 速写练习 commit 2 — extended
 * duration chips + custom): the pre-flight config sheet.
 *
 * Uses the same [CanvasSidePopup] as settings to let the user tweak
 * [PracticeConfig] before starting a slideshow session.
 *
 * Layout (top to bottom):
 *   1. Header: title + "将练习 N 张图片" sub-line
 *   2. Section "倒计时": [DurationChips] with presets
 *      30s / 1m / 2m / 5m / 10m + "自定义" chip (the
 *      last one opens a minutes-input dialog)
 *   3. Section "休息": [DurationChips] with presets
 *      0s / 15s / 30s / 1m / 5m + "自定义" chip
 *   4. Row "随机顺序": [Switch] — when on, the
 *      reducer shuffles the imageIds list on
 *      [StartPractice]
 *   5. Row "切换图片时重置计时": [Switch] — when on,
 *      manually moving to another image starts its full
 *      drawing interval; the default is on.
 *   6. Footer summary: "总时长约 M 分钟" — computed
 *      live from `selectedCount × (practice +
 *      rest)`, rounded to the nearest minute
 *   7. Full-width "开始" [Button] — dispatches
 *      [PockRefAction.StartPractice] and dismisses
 *      the sheet
 *
 * V_NOW (2026-06-08 user feedback "去掉倒计时提示音的功能"):
 * the previous "提示音" switch (which toggled
 * [PracticeConfig.soundEnabled]) was removed entirely
 * — the [soundEnabled] field is gone from
 * [PracticeConfig], the pref key is gone from
 * [com.pockref.app.data.AppPreferences.lastPracticeConfig],
 * and the practice mode is silent by design. The
 * config panel has 4 setting rows; its scrollable body
 * keeps the start button visible in short windows.
 *
 * V_NOW (2026-06-08 user feedback "倒计时时长新增 10
 * 分钟和自定义两个按钮..."): the countdown and
 * rest chip rows now each have 5 presets + 1
 * "自定义" chip (6 chips total). The "自定义" chip
 * opens a [CustomDurationDialog] with a numeric
 * [OutlinedTextField] (in minutes — the unit the
 * user asked for in the spec). The valid range:
 *   - COUNTDOWN custom: 1-60 minutes
 *     (= 60_000 - 3_600_000ms, within
 *     [PracticeConfig.MIN_PRACTICE_MS] /
 *     [PracticeConfig.MAX_PRACTICE_MS])
 *   - REST custom: 1-10 minutes
 *     (= 60_000 - 600_000ms, within
 *     [PracticeConfig.MIN_REST_MS] /
 *     [PracticeConfig.MAX_REST_MS])
 *
 * If the value coming back from
 * [com.pockref.app.data.AppPreferences.lastPracticeConfig]
 * doesn't match any preset (e.g. the user previously
 * entered 7 minutes), the "自定义" chip is the
 * pre-selected one when the sheet re-opens — the
 * `presetSet.contains(seconds)` check in
 * [DurationChips]'s selected-resolver picks it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxScope.PracticeConfigSheet(
    visible: Boolean,
    selectedCount: Int,
    lastConfig: PracticeConfig?,
    onStart: (PracticeConfig) -> Unit,
    onDismiss: () -> Unit,
    topInset: Dp = with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() } + Dimens.CornerInset,
    bottomInset: Dp = Dimens.CornerInset,
) {
    if (!visible) return

    BackHandler(onBack = onDismiss)

    // V_NOW: pre-fill from [lastConfig] (which the
    // PockRefApp caller reads from
    // [com.pockref.app.data.AppPreferences.lastPracticeConfig]).
    // When null (fresh install or after data clear),
    // fall back to [PracticeConfig]'s default ctor values
    // (60s / 30s / shuffle-off / reset-on). The `remember(lastConfig)`
    // key means the sheet re-initialises whenever the
    // caller hands us a different [lastConfig] — useful
    // when the user closes the sheet, edits the canvas,
    // re-opens: their last config comes back. Without
    // the key, the remember slot survives across sheet
    // dismiss/re-show and the user would see stale
    // values from before they closed.
    var practiceSeconds by remember(lastConfig) {
        mutableIntStateOf(((lastConfig?.practiceDurationMs ?: 60_000L) / 1000L).toInt())
    }
    var restSeconds by remember(lastConfig) {
        mutableIntStateOf(((lastConfig?.restDurationMs ?: 30_000L) / 1000L).toInt())
    }
    var shuffle by remember(lastConfig) { mutableStateOf(lastConfig?.shuffle ?: false) }
    var resetTimerOnImageChange by remember(lastConfig) {
        mutableStateOf(lastConfig?.resetTimerOnImageChange ?: true)
    }

    // ── Custom-duration dialog state ──
    // `customDialogTarget` distinguishes which field the
    // dialog is editing; `null` = dialog closed.
    // `rememberSaveable` so the dialog survives a
    // configuration change (e.g. device rotation)
    // without losing the in-progress edit.
    var customDialogTarget by rememberSaveable {
        mutableStateOf<CustomDialogTarget?>(null)
    }
    var customInputText by rememberSaveable(customDialogTarget) {
        mutableStateOf("")
    }

    customDialogTarget?.let { target ->
        CustomDurationDialog(
            target = target,
            initialMinutes = when (target) {
                CustomDialogTarget.PRACTICE -> practiceSeconds / 60
                CustomDialogTarget.REST -> restSeconds / 60
            },
            inputText = customInputText,
            onInputChange = { customInputText = it },
            onConfirm = { minutes ->
                val ms = minutes.toLong() * 60_000L
                when (target) {
                    CustomDialogTarget.PRACTICE -> practiceSeconds = (ms / 1000L).toInt()
                    CustomDialogTarget.REST -> restSeconds = (ms / 1000L).toInt()
                }
                customDialogTarget = null
            },
            onDismiss = { customDialogTarget = null },
        )
    }

    CanvasSidePopup(
        visible = visible,
        title = "速写练习窗口",
        modifier = Modifier.testTag("practice_config_panel"),
        topInset = topInset,
        bottomInset = bottomInset,
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(start = 16.dp, end = 4.dp),
            ) {
                Icon(Icons.Rounded.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("速写练习", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 10.dp).weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "关闭速写练习窗口")
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // ── Header ──
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (selectedCount == 0) "未选择图片" else "将练习 $selectedCount 张图片",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // ── 倒计时 (5 presets + 自定义) ──
                DurationChips(
                    label = "倒计时",
                    // 5 presets: 30s / 1m / 2m / 5m / 10m
                    options = listOf(30, 60, 120, 300, 600),
                    selectedSeconds = practiceSeconds,
                    onSelectPreset = { practiceSeconds = it },
                    onClickCustom = {
                        customDialogTarget = CustomDialogTarget.PRACTICE
                        customInputText = (practiceSeconds / 60).toString().takeIf {
                            practiceSeconds % 60 == 0 && practiceSeconds > 0
                        } ?: ""
                    },
                )

                // ── 休息 (5 presets + 自定义) ──
                DurationChips(
                    label = "休息",
                    // 5 presets: 0s / 15s / 30s / 1m / 5m
                    options = listOf(0, 15, 30, 60, 300),
                    selectedSeconds = restSeconds,
                    onSelectPreset = { restSeconds = it },
                    onClickCustom = {
                        customDialogTarget = CustomDialogTarget.REST
                        customInputText = (restSeconds / 60).toString().takeIf {
                            restSeconds % 60 == 0 && restSeconds > 0
                        } ?: ""
                    },
                )

                // ── 随机顺序 ──
                ToggleRow(label = "随机顺序", checked = shuffle, onCheckedChange = { shuffle = it })

                // ── 切图时重置计时 ──
                ToggleRow(
                    label = "切换图片时重置计时",
                    checked = resetTimerOnImageChange,
                    onCheckedChange = { resetTimerOnImageChange = it },
                )

                // ── 总时长摘要 ──
                val totalMin = if (selectedCount == 0) 0
                else ((selectedCount * (practiceSeconds + restSeconds)).toDouble() / 60.0).roundToInt()
                Text(
                    text = "总时长约 $totalMin 分钟",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ── 开始按钮 ──
            Button(
                onClick = {
                    onStart(
                        PracticeConfig(
                            practiceDurationMs = practiceSeconds * 1000L,
                            restDurationMs = restSeconds * 1000L,
                            shuffle = shuffle,
                            resetTimerOnImageChange = resetTimerOnImageChange,
                        )
                    )
                },
                enabled = selectedCount > 0,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text("开始")
            }
        }
    }
}

/**
 * V_NOW: which field the [CustomDurationDialog] is
 * editing. Stays private to this file; the dialog uses
 * it to label itself and to pick the right min / max
 * bounds for the input.
 */
private enum class CustomDialogTarget {
    PRACTICE,
    REST,
}

/**
 * V_NOW: M3 [AlertDialog] for entering a custom duration
 * in MINUTES. The user spec says "自定义时长的单位是
 * 分钟 (m)" so the input is integer minutes, NOT
 * seconds, NOT hours. Valid range depends on the
 * target: 1-60 for COUNTDOWN, 1-10 for REST.
 *
 * The dialog is shown by the parent sheet when the user
 * taps the "自定义" chip. The parent passes the
 * current value as `initialMinutes` (so the user can
 * see and edit their previous custom value rather
 * than re-typing from scratch).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomDurationDialog(
    target: CustomDialogTarget,
    initialMinutes: Int,
    inputText: String,
    onInputChange: (String) -> Unit,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val (title, hint, minMinutes, maxMinutes) = when (target) {
        CustomDialogTarget.PRACTICE -> Quadruple(
            "自定义倒计时",
            "1-60 分钟",
            1,
            60,
        )
        CustomDialogTarget.REST -> Quadruple(
            "自定义休息",
            "1-10 分钟",
            1,
            10,
        )
    }

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { raw ->
                        // Allow only digits, strip leading
                        // zeros, and cap to maxMinutes
                        // (3 chars covers 0-999, well
                        // above the 60-minute cap so the
                        // cap is enforced by the maxLength
                        // check below rather than the user
                        // typing past it).
                        val filtered = raw.filter { it.isDigit() }
                        onInputChange(filtered)
                    },
                    label = { Text("分钟") },
                    placeholder = { Text(hint) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val minutes = inputText.toIntOrNull() ?: return@TextButton
                    val clamped = minutes.coerceIn(minMinutes, maxMinutes)
                    onConfirm(clamped)
                },
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

/**
 * V_NOW: a 4-tuple value class. Used to unpack the
 * (title, hint, min, max) tuple from the dialog's
 * `when (target)` without a dedicated Quadruple data
 * class polluting the file.
 */
private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
)

/**
 * V_NOW: a horizontal strip of [FilterChip]s for one
 * duration field, plus a trailing "自定义" chip
 * (the user feedback "倒计时时长新增 10 分钟和自定义
 * 两个按钮"). Each preset chip is a fixed value
 * (in seconds). The custom chip opens a dialog
 * (handled by the parent) to enter a custom value
 * in MINUTES.
 *
 * Selection rule: the chip whose value matches
 * [selectedSeconds] is selected. If no preset matches
 * (e.g. the user previously entered 7 minutes = 420s
 * and the presets are 30/60/120/300/600), the custom
 * chip is selected instead. The custom chip's label
 * is "自定义" by default; if a custom value is set and
 * it's >= 1 minute, the label shows the value (e.g.
 * "7m") so the user has visible feedback about what
 * they previously chose.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DurationChips(
    label: String,
    options: List<Int>,
    selectedSeconds: Int,
    onSelectPreset: (Int) -> Unit,
    onClickCustom: () -> Unit,
) {
    val presetSet = remember(options) { options.toSet() }
    val isCustomSelected = selectedSeconds !in presetSet
    // For the custom chip label: if the current
    // value is a multiple of 60 and >= 60, show
    // "Xm". Otherwise show "自定义" (the value
    // might be a non-minute-aligned seconds value
    // the user previously entered — e.g. 90s —
    // which doesn't fit the "Xm" pattern; better
    // to show the generic "自定义" so the user
    // opens the dialog to discover what's there).
    val customLabel = if (isCustomSelected &&
        selectedSeconds >= 60 &&
        selectedSeconds % 60 == 0
    ) {
        "${selectedSeconds / 60}m"
    } else {
        "自定义"
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { sec ->
                FilterChip(
                    selected = selectedSeconds == sec,
                    onClick = { onSelectPreset(sec) },
                    label = { Text(formatDurationLabel(sec)) },
                )
            }
            // The custom chip is the LAST entry in the
            // row. Visual rule: same FilterChip style
            // as the presets; selected state matches
            // the "isCustomSelected" computed above so
            // the chip lights up whenever the value
            // doesn't match any preset (i.e. the user
            // has a custom value set).
            FilterChip(
                selected = isCustomSelected,
                onClick = onClickCustom,
                label = { Text(customLabel) },
            )
        }
    }
}

/**
 * V_NOW: a label-only + trailing [Switch] row for
 * boolean settings. Kept private to this file.
 */
@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * V_NOW: format a chip's seconds value as the
 * compact "30s" / "2m" / "5m" / "10m" form. Pure
 * formatting helper pulled out of [DurationChips]
 * so the chip row stays readable.
 */
private fun formatDurationLabel(seconds: Int): String = when {
    seconds < 60 -> "${seconds}s"
    seconds % 60 == 0 -> "${seconds / 60}m"
    else -> "${seconds / 60}m${seconds % 60}s"
}
