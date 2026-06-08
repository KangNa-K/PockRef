package com.pockref.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pockref.app.model.PockRefUiState
import com.pockref.app.ui.PockRefAction

/**
 * "通用" tab — device-input behaviors and persistence:
 * -边缘吸附 (edge snap to other images)
 * -旋转角度吸附 (rotation snap to0/90/180/270°)
 * - 自动保存 (interval slider,1 min ~1 h)
 * - S Pen按钮 (PRESS_AND_HOLD vs TOGGLE vs DOUBLE_TAP)
 *
 * Uses [LazyColumn] so the long auto-save + S Pen sections
 * compose lazily on phone (portrait, ~700dp tall — the content
 * is ~900dp and used to clip the bottom2 rows under the old
 * [verticalScroll] approach).
 */
@Composable
internal fun SettingsGeneralTab(
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
 item {
 SettingsToggleRow(
 title = "吸附图像",
 subtitle = if (state.edgeSnapEnabled) "拖动图片时贴近其他图片的边/中线" else "已关闭",
 checked = state.edgeSnapEnabled,
 onCheckedChange = { checked ->
 onAction(PockRefAction.SetEdgeSnapEnabled(checked))
 },
 )
 }
 item {
 SettingsToggleRow(
 title = "吸附旋转",
 subtitle = if (state.rotationSnapEnabled) "双指旋转时自动吸附0/90/180/270°" else "已关闭",
 checked = state.rotationSnapEnabled,
 onCheckedChange = { checked ->
 onAction(PockRefAction.SetRotationSnapEnabled(checked))
 },
 )
 }
 item {
 SettingsAutoSaveSection(state = state, onAction = onAction)
 }
 item {
 SettingsSpenSection(state = state, onAction = onAction)
 }
 }
}
