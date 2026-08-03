package com.pockref.app.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SwipeRight
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.pockref.app.model.PockRefUiState
import com.pockref.app.model.SettingsTab
import com.pockref.app.ui.PockRefAction

/**
 * Visual metadata for the3 settings tabs. Centralised so the
 * TabRow (phones), the NavigationRail (tablets), and any future
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
 * Phone-width tab bar across the top of the sheet.3 fixed tabs
 * (M3 [TabRow]); selected tab inherits
 * [PockRefUiState.settingsTab] so the choice survives sheet
 * close/reopen within the same app session.
 */
@Composable
internal fun SettingsTabRow(
 selectedTab: SettingsTab,
 onTabSelected: (SettingsTab) -> Unit,
) {
 TabRow(
 selectedTabIndex = settingsTabSpecs.indexOfFirst { it.tab == selectedTab }
 .coerceAtLeast(0),
 ) {
 settingsTabSpecs.forEach { spec ->
 Tab(
 selected = selectedTab == spec.tab,
 onClick = { onTabSelected(spec.tab) },
 text = { Text(spec.label) },
 icon = { Icon(spec.icon, contentDescription = spec.label) },
 )
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
 NavigationRail {
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
