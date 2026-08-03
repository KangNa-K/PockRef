package com.pockref.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.pockref.app.model.PockRefUiState
import com.pockref.app.ui.PockRefAction
import com.pockref.app.ui.theme.Dimens

/**
 *5-section settings sheet, hosted in [com.pockref.app.ui.settings]
 * (extracted from the old monolithic [com.pockref.app.ui.PureRefApp.kt]).
 * Renders3 top-level tabs (通用 /外观 / 手势) via [SettingsTabContent].
 *
 * V_NOW (#1 optimisation): the sheet content switches between three
 * layout tiers based on window width:
 *
 * - **Phone** (windowWidthDp <600dp, e.g.360x780 portrait): TabRow
 * at the top of the sheet, sheet takes the full window width
 * (no max-width cap) for thumb-reachability.
 * - **Tablet portrait / small landscape** (600dp <= windowWidthDp <
 *840dp, e.g.800x1280 portrait or compact landscape): same TabRow
 * layout as phone (a NavigationRail would feel cramped at this
 * width), but the sheet is **centered** with `widthIn(max =
 *720.dp)` so it doesn't sprawl edge-to-edge across a600dp+ window.
 * - **Tablet landscape** (windowWidthDp >=840dp, e.g.2800x1752 on
 * the Galaxy Tab S10+): a left-side [SettingsNavigationRail]
 * plus a right-side content pane. Sheet is centered with
 * `widthIn(max = Dimens.SheetMaxWidth)` AND `heightIn(max =600.dp)` so the
 * panel reads as a "settings window" rather than a sheet that
 * hangs from the bottom edge of a landscape tablet.
 *
 * The width cap is [Dimens.SheetMaxWidth] (560dp) — the M3 sheet
 * standard (see [com.pockref.app.ui.theme.Dimens] KDoc). Without
 * the cap, settings rows stretch to ~2700dp on a Tab S10+ landscape
 * and become visually hard to scan.
 *
 * Sections (rendered inside the active tab composable):
 * -通用:边缘吸附 /旋转吸附 / 自动保存 / S Pen按钮
 * -外观:主题 /背景颜色 (含 HSV) / 小地图显示
 * -手势:7 per-gesture toggles grouped into3 buckets
 * (缩放 /旋转 / 编辑)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsPanel(
 open: Boolean,
 onDismiss: () -> Unit,
 state: PockRefUiState,
 onAction: (PockRefAction) -> Unit,
 onChooseDefaultSaveDirectory: () -> Unit,
) {
 if (!open) return
 val sheetState = rememberModalBottomSheetState()
 ModalBottomSheet(
 onDismissRequest = onDismiss,
 sheetState = sheetState,
 ) {
 val configuration = LocalConfiguration.current
 val widthDp = configuration.screenWidthDp
 // Three layout tiers (see KDoc above for the rationale).
 val useNavigationRail = widthDp >=840
 val useWidthCap = widthDp >=600
 val sheetModifier = if (useWidthCap) {
 Modifier.widthIn(max = Dimens.SheetMaxWidth)
 } else {
 Modifier.fillMaxWidth()
 }
 if (useNavigationRail) {
 // Tablet landscape: header on top, then NavigationRail (left) +
 // content (right). The whole sheet is width-capped at [Dimens.SheetMaxWidth] (560dp) and
 // height-capped at600dp so it reads as a centred "settings
 // window" rather than a half-screen slab hanging from the
 // bottom edge of a landscape tablet.
 Column(
 modifier = sheetModifier.heightIn(max =600.dp),
 ) {
 SettingsSheetHeader(onClose = onDismiss)
 HorizontalDivider()
 Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
 SettingsNavigationRail(
 selectedTab = state.settingsTab,
 onTabSelected = { tab ->
 onAction(PockRefAction.SetSettingsTab(tab))
 },
 )
 Box(
 modifier = Modifier
 .weight(1f)
 .fillMaxHeight()
 .padding(horizontal =16.dp, vertical =8.dp),
 ) {
 SettingsTabContent(
 state = state,
 onAction = onAction,
 selectedTab = state.settingsTab,
 onChooseDefaultSaveDirectory = onChooseDefaultSaveDirectory,
 )
 }
 }
 }
 } else {
 // Phone + tablet portrait: TabRow on top, header above.
 Column(modifier = sheetModifier) {
 SettingsSheetHeader(onClose = onDismiss)
 SettingsTabRow(
 selectedTab = state.settingsTab,
 onTabSelected = { tab ->
 onAction(PockRefAction.SetSettingsTab(tab))
 },
 )
 HorizontalDivider()
 SettingsTabContent(
 state = state,
 onAction = onAction,
 selectedTab = state.settingsTab,
 onChooseDefaultSaveDirectory = onChooseDefaultSaveDirectory,
 )
 }
 }
 }
 }

/**
 * Top header bar for the settings sheet — title on the left, close
 * IconButton on the right. Shared by the phone (TabRow-on-top)
 * layout and the tablet-landscape (NavigationRail-on-left)
 * layout so the two layouts always agree on the title's size,
 * padding, and the close button's hit-target.
 *
 * V_NOW (#1 optimisation): extracted from the inline duplicate
 * that existed in the old [com.pockref.app.ui.SettingsPanel]
 * inside [com.pockref.app.ui.PureRefApp.kt] — before this, the
 * tablet layout had no header at all, so opening settings on a
 * Galaxy Tab S10+ landscape showed a NavigationRail with no
 * "设置" context, making the user wonder what they were looking
 * at. Now both layouts have a header.
 */
@Composable
internal fun SettingsSheetHeader(
 onClose: () -> Unit,
 modifier: Modifier = Modifier,
) {
 Row(
 verticalAlignment = Alignment.CenterVertically,
 modifier = modifier
 .fillMaxWidth()
 .padding(start =24.dp, end =8.dp, top =16.dp),
 ) {
 Text(
 text = "设置",
 style = MaterialTheme.typography.titleMedium,
 modifier = Modifier.weight(1f),
 )
 IconButton(onClick = onClose) {
 Icon(
 Icons.Rounded.Close,
 contentDescription = "关闭",
 )
 }
 }
}
