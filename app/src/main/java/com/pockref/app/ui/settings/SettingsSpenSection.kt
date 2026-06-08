package com.pockref.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.pockref.app.model.PockRefUiState
import com.pockref.app.spen.SpenMode
import com.pockref.app.ui.PockRefAction

/**
 * S Pen side-button mode radio.3 options (PRESS_AND_HOLD /
 * TOGGLE / DOUBLE_TAP) with their human descriptions. Extracted
 * from the old monolithic [com.pockref.app.ui.SettingsPanel]
 * (was inside [com.pockref.app.ui.PureRefApp.kt]) so the sheet
 * renders uniformly under both the phone (TabRow on top) and the
 * tablet (NavigationRail on the left) layouts.
 *
 * Each row pairs a [RadioButton] with a mode label + a one-line
 * explanation. The explanation text is `bodySmall` + onSurfaceVariant
 * so the radio row reads as "primary action + helper context".
 */
@Composable
internal fun SettingsSpenSection(
 state: PockRefUiState,
 onAction: (PockRefAction) -> Unit,
) {
 val currentSpenMode = state.spenMode
 Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
 Text(text = "S Pen按钮", style = MaterialTheme.typography.titleSmall)
 Text(
 text = "选择笔身按钮的触发方式,需 S Pen实体按钮支持(部分 Samsung机型支持)",
 style = MaterialTheme.typography.bodySmall,
 )
 SpenMode.values().forEach { mode ->
 Row(
 modifier = Modifier
 .fillMaxWidth()
 .selectable(
 selected = (currentSpenMode == mode),
 onClick = { onAction(PockRefAction.SetSpenMode(mode)) },
 role = Role.RadioButton,
 )
 .padding(vertical =4.dp),
 verticalAlignment = Alignment.CenterVertically,
 ) {
 RadioButton(
 selected = (currentSpenMode == mode),
 onClick = null,
 )
 Spacer(modifier = Modifier.width(8.dp))
 Column {
 Text(
 text = when (mode) {
 SpenMode.PRESS_AND_HOLD -> "按住开启,松开关闭"
 SpenMode.TOGGLE -> "按一下开,再按一下关"
 SpenMode.DOUBLE_TAP -> "点按开启,双击关闭"
 },
 style = MaterialTheme.typography.bodyLarge,
 )
 Text(
 text = when (mode) {
 SpenMode.PRESS_AND_HOLD ->
 "按住笔身按钮进入框选,松开退出。松手时如果有2+ 张图被选中则切到多选,否则单选。"
 SpenMode.TOGGLE ->
 "点按笔身按钮进入框选,再次点按退出。关闭时如果有2+ 张图被选中则切到多选,否则单选。"
 SpenMode.DOUBLE_TAP ->
 "点按笔身按钮进入框选,在200ms 内再次点按可退出并强制单选;超时后保持框选状态,需要再来一次双击才能退出。"
 },
 style = MaterialTheme.typography.bodySmall,
 color = MaterialTheme.colorScheme.onSurfaceVariant,
 )
 }
 }
 }
 }
}
