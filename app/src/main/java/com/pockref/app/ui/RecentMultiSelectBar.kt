package com.pockref.app.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Deselect
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.pockref.app.model.PockRefUiState
import com.pockref.app.ui.theme.Dimens

@Composable
internal fun RecentMultiSelectToolbar(
    state: PockRefUiState,
    onAction: (PockRefAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scenes = state.recentScenes
    if (!state.recentMultiSelectMode || scenes.isEmpty()) return
    val selected = state.recentSelection.intersect(scenes.map { it.uri }.toSet())
    val allSelected = scenes.all { it.uri in selected }
    val anyUnpinned = scenes.any { it.uri in selected && !it.pinned }
    val anyPinned = scenes.any { it.uri in selected && it.pinned }
    val shape = RoundedCornerShape(Dimens.ChromeCornerRadius)

    Row(
        modifier = modifier
            .testTag("recent-selection-toolbar")
            .clip(shape)
            .glassMaterial(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .height(Dimens.BottomPanelChromeSize)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "已选 ${selected.size}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, end = 4.dp),
            maxLines = 1,
        )
        SheetActionButton(
            icon = if (allSelected) Icons.Rounded.Deselect else Icons.Rounded.DoneAll,
            label = if (allSelected) "清空" else "全选",
            showLabel = false,
            modifier = Modifier.size(Dimens.MinTouchTarget),
            onClick = { onAction(if (allSelected) PockRefAction.ClearRecentSelection else PockRefAction.SelectAllRecentScenes) },
        )
        SheetActionButton(
            icon = Icons.Rounded.PushPin, label = "置顶", showLabel = false,
            enabled = anyUnpinned, modifier = Modifier.size(Dimens.MinTouchTarget),
            onClick = { onAction(PockRefAction.BulkSetRecentPin(true)) },
        )
        SheetActionButton(
            icon = Icons.Outlined.PushPin, label = "取消置顶", showLabel = false,
            enabled = anyPinned, modifier = Modifier.size(Dimens.MinTouchTarget),
            onClick = { onAction(PockRefAction.BulkSetRecentPin(false)) },
        )
        SheetActionButton(
            icon = Icons.Rounded.Delete, label = "删除", showLabel = false,
            tint = MaterialTheme.colorScheme.error, enabled = selected.isNotEmpty(),
            modifier = Modifier.size(Dimens.MinTouchTarget),
            onClick = { onAction(PockRefAction.RequestBulkDeleteConfirm) },
        )
        SheetActionButton(
            icon = Icons.Rounded.Close, label = "取消多选", showLabel = false,
            modifier = Modifier.size(Dimens.MinTouchTarget),
            onClick = { onAction(PockRefAction.ExitRecentMultiSelect) },
        )
    }
}

/**
 * V_NOW (2026-06-10): the bulk-delete confirmation
 * dialog. Rendered at the app root whenever
 * [PockRefUiState.bulkDeleteConfirmOpen] is true
 * (the flag is set by the bottom bar's "删除" button
 * and cleared by the dialog's confirm / cancel /
 * `onDismissRequest` callbacks).
 *
 * The dialog follows the M3 [AlertDialog] visual
 * contract (rounded 28dp corners, tonal-elevation
 * surface, 24dp padding, title + text + confirm +
 * dismiss button row). The title shows the count
 * of files about to be deleted, computed from the
 * intersection of [PockRefUiState.recentSelection]
 * and [PockRefUiState.recentScenes] (a stale URI
 * in the selection would render the count
 * inaccurate).
 *
 * Confirm button: red [MaterialTheme.colorScheme.error]
 * text per M3 convention for destructive actions.
 * The dispatch path is:
 *   tap confirm → [PockRefAction.BulkDeleteRecentScenes]
 *   (the actual deletion) — the dialog's
 *   `onDismissRequest` fires first (closing the
 *   dialog) but [PockRefAction.DismissBulkDeleteConfirm]
 *   is a no-op when called after the deletion has
 *   already cleared the flag, so the order
 *   doesn't matter.
 */
@Composable
fun BulkDeleteConfirmDialog(
    state: PockRefUiState,
    onAction: (PockRefAction) -> Unit,
) {
    if (!state.bulkDeleteConfirmOpen) return
    val validScenes = state.recentScenes
    val validSelection = state.recentSelection.intersect(
        validScenes.map { it.uri }.toSet(),
    )
    val count = validSelection.size
    GlassAlertDialog(
        onDismissRequest = { onAction(PockRefAction.DismissBulkDeleteConfirm) },
        title = { Text("删除最近文件") },
        text = {
            Text(
                text = if (count <= 0) {
                    "没有选中任何文件。"
                } else {
                    "确定要删除这 $count 个文件吗?\n\n" +
                        "这些文件将从最近列表移除,并永久删除本地文件,无法恢复。"
                },
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onAction(
                        PockRefAction.BulkDeleteRecentScenes(validSelection.toList()),
                    )
                },
                enabled = count > 0,
            ) {
                Text(
                    text = "删除",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(PockRefAction.DismissBulkDeleteConfirm) }) {
                Text("取消")
            }
        },
    )
}
