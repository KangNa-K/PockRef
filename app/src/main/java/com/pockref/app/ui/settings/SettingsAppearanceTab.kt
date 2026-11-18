package com.pockref.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.pockref.app.data.ThemeMode
import com.pockref.app.model.CanvasStyle
import com.pockref.app.model.PockRefUiState
import com.pockref.app.ui.CompactSegmentedControl
import com.pockref.app.ui.PockRefAction

/**
 * "外观" tab — visual look-and-feel:
 * -主题 (system / light / dark segmented row)
 * -毛玻璃效果 (persistent toolbar and popup backdrop-blur switch)
 * -画布样式 (solid background / world-aligned grid)
 * -背景颜色 (7 preset swatches + inline HSV wheel on the
 * "Custom" chip)
 *
 * The HSV wheel is the only section that may scroll off the
 * bottom of a portrait phone — [LazyColumn] keeps the rest of
 * the tab cheap and the wheel's draggable canvas uses its own
 * internal height (200dp) so it doesn't fight the LazyColumn for
 * vertical space.
 */
@Composable
internal fun SettingsAppearanceTab(
 state: PockRefUiState,
 onAction: (PockRefAction) -> Unit,
) {
 LazyColumn(
 modifier = Modifier.fillMaxSize(),
 contentPadding = PaddingValues(
 horizontal =24.dp,
 vertical =16.dp,
 ),
 verticalArrangement = Arrangement.spacedBy(16.dp),
 ) {
 //主题
 item {
 Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
 SettingsSectionHeading("主题", divided = false)
 val themeOptions = listOf(
 ThemeMode.SYSTEM to "系统",
 ThemeMode.LIGHT to "浅色",
 ThemeMode.DARK to "深色",
 )
 CompactSegmentedControl(
 options = themeOptions.map { it.second },
 selectedIndex = themeOptions.indexOfFirst { it.first == state.themeMode },
 onOptionClick = { index ->
 onAction(PockRefAction.SetThemeMode(themeOptions[index].first))
 },
 modifier = Modifier.testTag("theme-mode-toggle"),
 )
 }
 }
 item {
 SettingsToggleRow(
 title = "毛玻璃效果",
 subtitle = if (android.os.Build.VERSION.SDK_INT >= 31) {
 "模糊弹窗和工具栏后方的内容，关闭后使用不透明背景"
 } else {
 "当前系统不支持背景模糊，将使用不透明背景"
 },
 checked = state.frostedGlassEnabled,
 onCheckedChange = { onAction(PockRefAction.SetFrostedGlassEnabled(it)) },
 )
 }
 item {
 Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
 SettingsSectionHeading("画布样式")
 val canvasStyles = listOf(CanvasStyle.SOLID, CanvasStyle.GRID)
 CompactSegmentedControl(
 options = listOf("纯色", "网格"),
 selectedIndex = canvasStyles.indexOf(state.canvasStyle),
 onOptionClick = { index ->
 onAction(PockRefAction.SetCanvasStyle(canvasStyles[index]))
 },
 modifier = Modifier.testTag("canvas-style-toggle"),
 )
 }
 }
 //背景颜色
 item {
 Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
 SettingsSectionHeading("背景颜色")
 BackgroundColorPanel(
 currentColor = state.backgroundColor,
 onPreset = { preset ->
 onAction(PockRefAction.SetBackgroundPreset(preset))
 },
 )
 if (state.showBackgroundPicker) {
 CustomColorWheel(
 currentColor = state.backgroundColor,
 // "确定" (preview/commit pattern) — apply the composed
 // preview colour. The [setBackgroundColor] reducer both
 // writes [state.backgroundColor] AND sets
 // [showBackgroundPicker = false] in the same update, so
 // the picker closes on commit (and the next time the user
 // taps Custom, the picker reopens with the just-saved
 // colour as the new starting point).
 onConfirm = { color ->
 onAction(PockRefAction.SetBackgroundColor(color))
 },
 // "取消" (preview/commit pattern) — discard the preview
 // (it's local state inside [CustomColorWheel] and goes
 // away with the composable) and close the picker without
 // touching [state.backgroundColor]. Uses the pre-existing
 // [ShowBackgroundPicker] action — no new reducer needed.
 onCancel = {
 onAction(PockRefAction.ShowBackgroundPicker(false))
 },
 )
 }
 }
 }
    // V_NOW (2026-06-16 user feedback "去掉设置里的启动
    // 页显示导出日志的开关"): the start-page log-export FAB
    // is now an easter-egg feature — hidden by default, made
    // visible by tapping the PockRef title 7 times. The
    // toggle that previously lived here is removed entirely:
    // the FAB visibility is now session-local (a Compose
    // remember in [EmptyCanvasState]) and doesn't belong in
    // persistent settings anymore. Removed the
    // [SettingsToggleRow] block + the
    // [PockRefAction.SetShowStartPageLogExportFab] action +
    // the [PockRefUiState.showStartPageLogExportFab] field
    // + the [AppPreferences.showStartPageLogExportFab]
    // backing store in one go.
    }
}
