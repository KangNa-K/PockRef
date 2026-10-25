package com.pockref.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SwipeRight
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pockref.app.model.PockRefUiState
import com.pockref.app.model.SettingsTab
import com.pockref.app.ui.PockRefAction
import com.pockref.app.ui.theme.Dimens

/**
 * Visual metadata shared by the horizontal tabs, the tablet rail, and any future
 * deep-link / quick-jump all read the same icon + label, and
 * adding a4th tab is a one-line change here.
 */
internal data class SettingsTabSpec(
 val tab: SettingsTab,
 val label: String,
 val icon: ImageVector,
)

internal val settingsTabSpecs: List<SettingsTabSpec> = listOf(
 SettingsTabSpec(
 tab = SettingsTab.GENERAL,
 label = "通用",
 icon = Icons.Rounded.Tune,
 ),
 SettingsTabSpec(
 tab = SettingsTab.APPEARANCE,
 label = "外观",
 icon = Icons.Rounded.Palette,
 ),
 SettingsTabSpec(
 tab = SettingsTab.GESTURES,
 label = "手势",
 icon = Icons.Rounded.SwipeRight,
 ),
)

/**
 * Translucent segmented tabs let the popup's existing glass remain visible.
 * The selected tab inherits
 * [PockRefUiState.settingsTab] so the choice survives sheet
 * close/reopen within the same app session.
 */
@Composable
internal fun SettingsTabRow(
 selectedTab: SettingsTab,
 onTabSelected: (SettingsTab) -> Unit,
) {
 val colors = MaterialTheme.colorScheme
 val groupShape = RoundedCornerShape(16.dp)
 val itemShape = RoundedCornerShape(12.dp)
 Row(
 modifier = Modifier.fillMaxWidth()
 .padding(horizontal = 12.dp, vertical = 4.dp)
 .padding(bottom = 8.dp)
 .clip(groupShape)
 .background(colors.onSurface.copy(alpha = 0.04f))
 .border(1.dp, colors.outlineVariant.copy(alpha = 0.18f), groupShape)
 .selectableGroup()
 .testTag("settings_tabs")
 .padding(4.dp),
 horizontalArrangement = Arrangement.spacedBy(4.dp),
 ) {
 settingsTabSpecs.forEach { spec ->
 val selected = selectedTab == spec.tab
 val tint = if (selected) colors.primary else colors.onSurfaceVariant
 Row(
 modifier = Modifier.weight(1f).height(Dimens.MinTouchTarget)
 .clip(itemShape)
 .background(if (selected) colors.surface.copy(alpha = 0.64f) else Color.Transparent)
 .border(1.dp, if (selected) colors.outlineVariant.copy(alpha = 0.24f)
 else Color.Transparent, itemShape)
 .selectable(
 selected = selected,
 role = Role.Tab,
 onClick = { onTabSelected(spec.tab) },
 )
 .testTag("settings_tab_${spec.tab.name.lowercase()}"),
 horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
 verticalAlignment = Alignment.CenterVertically,
 ) {
 Icon(spec.icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
 Text(spec.label, color = tint, style = MaterialTheme.typography.labelLarge,
 fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
 maxLines = 1)
 }
 }
 }
}

/**
 * Tablet-width vertical rail on the left of the sheet. Same3 tabs
 * as [SettingsTabRow] but laid out as M3 [NavigationRailItem]s. The
 * [selected] state and the icon tint come straight from the same
 * `settingsTabSpecs` list so the two layouts never drift apart.
 */
@Composable
internal fun SettingsNavigationRail(
 selectedTab: SettingsTab,
 onTabSelected: (SettingsTab) -> Unit,
) {
 NavigationRail(modifier = Modifier.width(Dimens.SettingsNavigationWidth).fillMaxHeight()) {
 settingsTabSpecs.forEach { spec ->
 NavigationRailItem(
 selected = selectedTab == spec.tab,
 onClick = { onTabSelected(spec.tab) },
 icon = { Icon(spec.icon, contentDescription = spec.label) },
 label = { Text(spec.label) },
 colors = NavigationRailItemDefaults.colors(),
 )
 }
 }
}

/**
 * Dispatcher: renders the active tab's composable. Each tab
 * composable is responsible for its own internal scroll. The shared
 * [state] / [onAction] are forwarded unchanged.
 */
@Composable
internal fun SettingsTabContent(
 state: PockRefUiState,
 onAction: (PockRefAction) -> Unit,
 selectedTab: SettingsTab,
 onChooseDefaultSaveDirectory: () -> Unit,
) {
 when (selectedTab) {
 SettingsTab.GENERAL -> SettingsGeneralTab(
 state = state,
 onAction = onAction,
 onChooseDefaultSaveDirectory = onChooseDefaultSaveDirectory,
 )
 SettingsTab.APPEARANCE -> SettingsAppearanceTab(state = state, onAction = onAction)
 SettingsTab.GESTURES -> SettingsGesturesTab(state = state, onAction = onAction)
 }
}
