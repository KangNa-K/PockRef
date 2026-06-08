package com.pockref.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pockref.app.model.MinimapDisplayMode
import com.pockref.app.model.MinimapSliderOrientation
import com.pockref.app.model.PockRefUiState
import com.pockref.app.ui.PockRefAction

/**
 * "小地图显示" sub-section inside the "外观" tab. Two-level
 * control:
 * Level1: Display mode (MINIMAP vs SLIDER) via segmented button.
 * Level2 (only when SLIDER is active): Orientation
 * (ADAPTIVE / HORIZONTAL / VERTICAL) via radio list.
 *
 * Originally inlined inside [SettingsAppearanceTab] inside the old
 * [com.pockref.app.ui.SettingsPanel] (PureRefApp.kt). Extracted so
 * the appearance tab can stay focused on theme + background colour
 * and so this section's two-level state can evolve independently
 * (e.g. adding a "translucent minimap" toggle in the future won't
 * touch the appearance tab body).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsMinimapSection(
 state: PockRefUiState,
 onAction: (PockRefAction) -> Unit,
) {
 Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
 Text(
 text = "小地图显示",
 style = MaterialTheme.typography.titleSmall,
 )
 // Level1: Display mode (Minimap vs Slider)
 val displayOptions = listOf(
 MinimapDisplayMode.MINIMAP to "地图",
 MinimapDisplayMode.SLIDER to "滑块",
 )
 SingleChoiceSegmentedButtonRow {
 displayOptions.forEachIndexed { index, (mode, label) ->
 SegmentedButton(
 selected = state.minimapDisplayMode == mode,
 onClick = {
 onAction(PockRefAction.SetMinimapDisplayMode(mode))
 },
 shape = SegmentedButtonDefaults.itemShape(
 index = index,
 count = displayOptions.size,
 ),
 modifier = Modifier.heightIn(min =48.dp),
 icon = { },
 ) {
 Text(
 text = label,
 maxLines =1,
 overflow = TextOverflow.Ellipsis,
 )
 }
 }
 }
 // Level2: Slider orientation (only when Slider is selected)
 AnimatedVisibility(
 visible = state.minimapDisplayMode == MinimapDisplayMode.SLIDER,
 enter = expandVertically() + fadeIn(),
 exit = shrinkVertically() + fadeOut(),
 ) {
 Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
 Spacer(modifier = Modifier.height(4.dp))
 Text(
 text = "滑块方向",
 style = MaterialTheme.typography.titleSmall,
 )
 val orientationOptions = listOf(
 MinimapSliderOrientation.ADAPTIVE to "自适应",
 MinimapSliderOrientation.HORIZONTAL to "横向",
 MinimapSliderOrientation.VERTICAL to "纵向",
 )
 orientationOptions.forEach { (orientation, label) ->
 Row(
 modifier = Modifier
 .fillMaxWidth()
 .selectable(
 selected = state.minimapSliderOrientation == orientation,
 onClick = {
 onAction(
 PockRefAction.SetMinimapSliderOrientation(orientation),
 )
 },
 role = Role.RadioButton,
 )
 .padding(vertical =2.dp),
 verticalAlignment = Alignment.CenterVertically,
 ) {
 RadioButton(
 selected = state.minimapSliderOrientation == orientation,
 onClick = null,
 )
 Spacer(modifier = Modifier.width(8.dp))
 Text(
 text = when (orientation) {
 MinimapSliderOrientation.ADAPTIVE -> "$label — 根据内容方向自动调整"
 MinimapSliderOrientation.HORIZONTAL -> "$label —滑块水平方向显示"
 MinimapSliderOrientation.VERTICAL -> "$label —滑块垂直方向显示"
 },
 style = MaterialTheme.typography.bodyLarge,
 )
 }
 }
 }
 }
 }
}
