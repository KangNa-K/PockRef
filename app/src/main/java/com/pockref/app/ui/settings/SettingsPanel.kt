package com.pockref.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pockref.app.model.PockRefUiState
import com.pockref.app.ui.CanvasSidePopup
import com.pockref.app.ui.PockRefAction
import com.pockref.app.ui.theme.Dimens

@Composable
internal fun BoxScope.SettingsPanel(
    open: Boolean,
    onDismiss: () -> Unit,
    state: PockRefUiState,
    onAction: (PockRefAction) -> Unit,
    onChooseDefaultSaveDirectory: () -> Unit,
    panelWidth: Dp = 360.dp,
    topInset: Dp = with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() } + Dimens.CornerInset,
    bottomInset: Dp = Dimens.CornerInset,
) {
    BackHandler(enabled = open, onBack = onDismiss)
    CanvasSidePopup(
        visible = open,
        panelWidth = panelWidth,
        title = "设置窗口",
        modifier = Modifier.testTag("settings_panel"),
        topInset = topInset,
        bottomInset = bottomInset,
    ) {
        Column(Modifier.fillMaxSize()) {
            SettingsSheetHeader()
            SettingsTabRow(
                selectedTab = state.settingsTab,
                onTabSelected = { onAction(PockRefAction.SetSettingsTab(it)) },
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
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

@Composable
internal fun SettingsSheetHeader(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().height(56.dp)
            .padding(start = 16.dp, end = 4.dp).testTag("settings_header"),
    ) {
        Icon(Icons.Rounded.Settings, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text("设置", style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 10.dp).weight(1f))
    }
}
