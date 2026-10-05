package com.pockref.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pockref.app.model.PockRefUiState
import com.pockref.app.ui.PockRefAction
import com.pockref.app.ui.theme.Dimens

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
    val configuration = LocalConfiguration.current
    val tablet = configuration.screenWidthDp >= 600
    val rail = configuration.screenWidthDp >= 840
    val height = configuration.screenHeightDp.dp *
        if (tablet) Dimens.SettingsWindowHeightFraction else 0.90f
    val content: @Composable () -> Unit = {
        Column(Modifier.fillMaxSize()) {
            SettingsSheetHeader(onClose = onDismiss)
            if (!rail) {
                SettingsTabRow(
                    selectedTab = state.settingsTab,
                    onTabSelected = { onAction(PockRefAction.SetSettingsTab(it)) },
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            // Only the content list scrolls; the header retains its own height.
            Row(Modifier.weight(1f).fillMaxWidth()) {
                if (rail) {
                    SettingsNavigationRail(
                        selectedTab = state.settingsTab,
                        onTabSelected = { onAction(PockRefAction.SetSettingsTab(it)) },
                    )
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                    Box(Modifier.widthIn(max = Dimens.SettingsContentMaxWidth).fillMaxSize()) {
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
    }
    if (tablet) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Spacer(
                    Modifier.matchParentSize().clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
                )
                Surface(
                    modifier = Modifier.padding(horizontal = 24.dp)
                        .widthIn(max = if (rail) Dimens.SettingsWindowMaxWidth else 600.dp)
                        .fillMaxWidth()
                        .height(maxHeight * Dimens.SettingsWindowHeightFraction)
                        .testTag("settings_panel"),
                    shape = RoundedCornerShape(Dimens.DialogCornerRadius),
                    color = MaterialTheme.colorScheme.surface,
                ) { content() }
            }
        }
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            modifier = Modifier.height(height).testTag("settings_panel"),
        ) { content() }
    }
}

@Composable
internal fun SettingsSheetHeader(onClose: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth()
            .padding(start = Dimens.SettingsContentInset, end = 12.dp, top = 8.dp, bottom = 8.dp)
            .testTag("settings_header"),
    ) {
        Text("设置", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = onClose) {
            Icon(Icons.Rounded.Close, contentDescription = "关闭设置")
        }
    }
}
