package com.pockref.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pockref.app.model.PockRefUiState
import com.pockref.app.ui.GestureId
import com.pockref.app.ui.PockRefAction

/**
 * Static grouping of the8 [GestureId]s into3 sub-categories
 * (gesture tab visual hierarchy). Mirrors Procreate's
 * "Gestures → [Zoom / Rotate / Edit]"3-bucket layout so users
 * coming from Procreate find the toggle they want without
 * scanning a flat list of7.
 */
private data class GestureGroupSpec(
 val title: String,
 val ids: List<String>,
)

private val gestureGroupSpecs: List<GestureGroupSpec> = listOf(
 GestureGroupSpec(
 title = "缩放",
 ids = listOf(
 GestureId.PINCH_ZOOM,
 ),
 ),
 GestureGroupSpec(
 title = "旋转",
 ids = listOf(
 GestureId.PINCH_ROTATE,
 ),
 ),
 GestureGroupSpec(
 title = "编辑",
 ids = listOf(
 GestureId.MOVE_IMAGE,
 GestureId.SINGLE_FINGER_PAN,
 GestureId.TWO_FINGER_TAP,
 GestureId.THREE_FINGER_TAP,
 GestureId.DOUBLE_TAP_EMPTY,
 GestureId.COLOR_PICKER,
 ),
 ),
)

/**
 * "手势" tab —8 per-gesture on/off switches grouped into
 *缩放 /旋转 /编辑 sub-categories. Each group has a small
 * section header (titleSmall, onSurfaceVariant) so the visual
 * rhythm reads as "3 buckets of1-4 toggles" rather than a single
 * flat list of8. Uses [LazyColumn] for lazy composition.
 */
@Composable
internal fun SettingsGesturesTab(
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
 gestureGroupSpecs.forEach { group ->
 item(key = "group_${group.title}") {
 SettingsSectionHeading(group.title, divided = group != gestureGroupSpecs.first())
 }
 items(
 items = group.ids,
 key = { id -> "gesture_$id" },
 ) { id ->
 val isOn = state.gestureToggles[id] ?: true
 val title = GestureId.label(id)
 val subtitle = gestureSubtitle(id, isOn)
 SettingsToggleRow(
 title = title,
 subtitle = subtitle,
 checked = isOn,
 onCheckedChange = { checked ->
 onAction(PockRefAction.SetGestureEnabled(id, checked))
 },
 )
 }
 }
 }
}

/**
 * Per-gesture helper subtitle for [SettingsGesturesTab].
 * Kept as a top-level private fun so the LazyColumn `items`
 * lambda stays a one-liner. Mirrors the original `when` block
 * that used to live inline in the old
 * [com.pockref.app.ui.SettingsPanel] (PureRefApp.kt).
 */
private fun gestureSubtitle(id: String, isOn: Boolean): String = when (id) {
 GestureId.MOVE_IMAGE -> if (isOn) "单指拖动选中的图片" else "已关闭"
 GestureId.SINGLE_FINGER_PAN -> if (isOn) "单指拖动空白处移动画布" else "双指拖动移动画布"
 GestureId.PINCH_ZOOM -> if (isOn) "双指捏合调整缩放" else "已关闭"
 GestureId.PINCH_ROTATE -> if (isOn) "双指旋转(吸附0/90/180/270°)" else "已关闭"
 GestureId.TWO_FINGER_TAP -> if (isOn) "撤销" else "已关闭"
 GestureId.THREE_FINGER_TAP -> if (isOn) "重做" else "已关闭"
 GestureId.DOUBLE_TAP_EMPTY -> if (isOn) "适应窗口" else "已关闭"
 GestureId.COLOR_PICKER -> if (isOn) "长按图片读取原图颜色" else "已关闭"
 else -> if (isOn) "已启用" else "已关闭"
}
