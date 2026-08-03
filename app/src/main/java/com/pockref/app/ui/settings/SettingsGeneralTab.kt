package com.pockref.app.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
 * - 默认保存路径 (可开关,可在文件管理器中选择文件夹)
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
 onChooseDefaultSaveDirectory: () -> Unit,
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
 SettingsToggleRow(
 title = "使用默认保存路径",
 subtitle = when {
 !state.defaultSavePathEnabled -> "关闭时由系统文件管理器决定初始位置"
 state.defaultSaveDirectoryName != null -> "保存时从 ${state.defaultSaveDirectoryName} 打开"
 else -> "已开启,但尚未选择文件夹"
 },
 checked = state.defaultSavePathEnabled,
 onCheckedChange = { enabled ->
 onAction(PockRefAction.SetDefaultSavePathEnabled(enabled))
 },
 )
 }
 item {
 Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
 Column {
 Text("默认保存路径", style = MaterialTheme.typography.titleSmall)
 Text(
 text = state.defaultSaveDirectoryName ?: "尚未选择文件夹",
 style = MaterialTheme.typography.bodySmall,
 color = MaterialTheme.colorScheme.onSurfaceVariant,
 )
 }
 Row(
 modifier = Modifier.fillMaxWidth(),
 horizontalArrangement = Arrangement.spacedBy(8.dp),
 ) {
 FilledTonalButton(
 onClick = onChooseDefaultSaveDirectory,
 modifier = Modifier.weight(1f),
 ) {
 Icon(Icons.Rounded.FolderOpen, contentDescription = "选择文件夹")
 Spacer(Modifier.width(8.dp))
 Text("选择文件夹")
 }
 if (state.defaultSaveDirectoryUri != null) {
 OutlinedButton(
 onClick = {
 onAction(PockRefAction.SetDefaultSaveDirectory(null, null))
 },
 modifier = Modifier.weight(1f),
 ) {
 Icon(Icons.Rounded.Clear, contentDescription = "清除选择")
 Spacer(Modifier.width(8.dp))
 Text("清除选择")
 }
 }
 }
 }
 }
 item {
 SettingsSpenSection(state = state, onAction = onAction)
 }
 }
}
