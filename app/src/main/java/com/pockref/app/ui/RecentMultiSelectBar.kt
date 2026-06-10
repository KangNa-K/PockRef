@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.pockref.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Deselect
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pockref.app.model.PockRefUiState
import com.pockref.app.ui.theme.Dimens

/**
 * V_NOW (2026-06-10, "启动页多选" → sheet 统一): the
 * start-page multi-select chrome, refactored to a
 * SINGLE M3 bottom sheet (replacing the previous
 * dual top-bar + bottom-bar layout). Reuses the
 * existing [BottomSheetPanel] widget for the chrome
 * shell — 28dp top corners, 32×4dp drag handle,
 * 560dp max width, 0dp tonalElevation, hairline
 * outlineVariant border, 220ms slide-in animation.
 * The visual contract is now byte-identical to
 * the canvas's [com.pockref.app.ui.PureRefApp.SelectionBottomSheet]
 * and the crop toolbar's [BottomSheetPanel] usage
 * — every sheet in the app shares the same token
 * vocabulary, so the user's "sheet 风格统一" goal
 * is fully achieved.
 *
 * The sheet has two sections:
 *   1. **Header** (56dp) — left: "已选 N 项"
 *      [titleMedium] text (M3 ListItem header convention),
 *      right: 48dp [IconButton] with [Icons.Rounded.Close],
 *      `contentDescription = "取消多选"` for TalkBack.
 *   2. **Actions row** (80dp) — 4 bulk action buttons
 *      (全选 / 置顶 / 取消置顶 / 删除), unchanged from
 *      the v1.0 dual-bar implementation.
 *
 * Between the two sections is a 1dp
 * [HorizontalDivider] tinted `outlineVariant @ 40%`
 * — the M3 ListItem "header / content" separator
 * pattern. The divider is INVISIBLE against most
 * backgrounds and serves as a subtle visual chunk
 * between the count info and the action affordances.
 *
 * Visibility:
 *   - visible = [PockRefUiState.recentMultiSelectMode]
 *     AND [PockRefUiState.recentScenes] is not empty.
 *   - Empty recentScenes (impossible to multi-select
 *     in that case) hides the sheet entirely — the
 *     [EmptyCanvasState] "选择项目" surface would be
 *     the only UI and the sheet would be a visual
 *     orphan.
 *
 * Dismissal:
 *   - Swipe-down on the sheet's drag handle (the
 *     [BottomSheetPanel] default behaviour,
 *     [swipeToDismiss] = true) dispatches
 *     [PockRefAction.ExitRecentMultiSelect] via the
 *     sheet's [onDismiss] callback.
 *   - The sheet's own back-press handler is
 *     DISABLED ([dismissOnBackPress] = false) because
 *     [com.pockref.app.ui.PureRefApp]'s [BackHandler]
 *     chain already routes back to the multi-select
 *     exit branch (with higher-priority overrides
 *     for the bulk-delete confirm dialog). Letting
 *     the sheet register a second back handler would
 *     double-fire on the same press.
 *   - The header's [Close] icon button is a third
 *     dismissal affordance, exposed for users who
 *     don't notice the swipe gesture.
 *
 * Smart action bar:
 *   - The actions row carries the same
 *     "pin-unpin mixed" smart disable logic as the
 *     v1.0 dual-bar implementation. The [PockRefAction]
 *     dispatching is unchanged (复用
 *     [PockRefAction.BulkSetRecentPin] etc.), so
 *     the bulk pin / delete flow goes through the
 *     same `persistRecentScenes` prefs path the
 *     canvas-side edit-scene code uses.
 */
@Composable
fun RecentMultiSelectSheet(
    state: PockRefUiState,
    onAction: (PockRefAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val validScenes = state.recentScenes
    val validSelection = state.recentSelection.intersect(
        validScenes.map { it.uri }.toSet(),
    )
    val visible = state.recentMultiSelectMode && validScenes.isNotEmpty()
    BottomSheetPanel(
        visible = visible,
        // V_NOW: onDismiss routes to the multi-select
        // exit action — handles BOTH swipe-down on
        // the drag handle (BottomSheetPanel default)
        // AND the header's close button (the
        // IconButton onClick below). Both end the
        // multi-select mode, which flips the
        // visibility check above, which triggers
        // the sheet's exit animation.
        onDismiss = { onAction(PockRefAction.ExitRecentMultiSelect) },
        modifier = modifier,
        sheetMaxWidth = Dimens.SheetMaxWidth,
        showDragHandle = true,
        swipeToDismiss = true,
        // V_NOW: back-press is owned by
        // [com.pockref.app.ui.PureRefApp.BackHandler]
        // which has the higher-priority bulk-delete
        // confirm dialog branch. Disabling the
        // sheet's own back handler prevents
        // double-firing on the same press.
        dismissOnBackPress = false,
    ) {
        // ── Header: "已选 N 项" + ✕ ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val title = if (validSelection.isEmpty()) {
                "选择项目"
            } else {
                "已选 ${validSelection.size} 项"
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Medium,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            IconButton(
                onClick = { onAction(PockRefAction.ExitRecentMultiSelect) },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "取消多选",
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        // ── Divider: header / actions separator ──
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        )
        // ── Actions row: 4 bulk actions ──
        val allSelected = validScenes.isNotEmpty() &&
            validScenes.all { it.uri in validSelection }
        val anyUnpinned = validScenes.any { it.uri in validSelection && !it.pinned }
        val anyPinned = validScenes.any { it.uri in validSelection && it.pinned }
        val isAllPinned = validScenes.any { it.uri in validSelection } &&
            validScenes.filter { it.uri in validSelection }.all { it.pinned }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            // 1. 全选 / 清空 (toggle)
            val selectAllIcon = if (allSelected) {
                Icons.Rounded.Deselect
            } else {
                Icons.Rounded.DoneAll
            }
            val selectAllLabel = if (allSelected) "清空" else "全选"
            BottomActionButton(
                icon = selectAllIcon,
                label = selectAllLabel,
                onClick = {
                    if (allSelected) {
                        onAction(PockRefAction.ClearRecentSelection)
                    } else {
                        onAction(PockRefAction.SelectAllRecentScenes)
                    }
                },
            )
            // 2. 置顶 — disabled when ALL selected are already pinned
            BottomActionButton(
                icon = Icons.Rounded.PushPin,
                label = "置顶",
                onClick = { onAction(PockRefAction.BulkSetRecentPin(true)) },
                enabled = validSelection.isNotEmpty() && !isAllPinned,
                tintIcon = if (anyUnpinned) {
                    MaterialTheme.colorScheme.primary
                } else {
                    LocalContentColor.current
                },
            )
            // 3. 取消置顶 — disabled when ALL selected are already unpinned
            BottomActionButton(
                icon = Icons.Rounded.PushPin,
                label = "取消置顶",
                onClick = { onAction(PockRefAction.BulkSetRecentPin(false)) },
                enabled = validSelection.isNotEmpty() && anyPinned,
            )
            // 4. 删除
            BottomActionButton(
                icon = Icons.Rounded.Delete,
                label = "删除",
                onClick = { onAction(PockRefAction.RequestBulkDeleteConfirm) },
                enabled = validSelection.isNotEmpty(),
                tintIcon = if (validSelection.isNotEmpty()) {
                    MaterialTheme.colorScheme.error
                } else {
                    LocalContentColor.current
                },
            )
        }
    }
}



/**
 * V_NOW (2026-06-10): single button in the
 * [RecentMultiSelectSheet]'s actions row. M3
 * small-bottom-app-bar convention is "icon above
 * text label" — a vertical [Column] of [Icon] +
 * [Text] inside a clickable surface. The surface
 * is 56dp wide × 64dp tall (a comfortable thumb
 * target on a tablet — smaller than 48dp would
 * be too dense with 4 actions, larger would push
 * the bar above 80dp). When [enabled] is false
 * the entire button (icon + label) is rendered
 * at 38% alpha per M3 spec for disabled controls.
 */
@Composable
private fun BottomActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tintIcon: Color = LocalContentColor.current,
) {
    val alpha = if (enabled) 1f else 0.38f
    Column(
        modifier = Modifier
            .width(56.dp)
            .height(64.dp)
            .background(Color.Transparent)
            .padding(vertical = 4.dp)
            .clickable(enabled = enabled, onClick = onClick),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tintIcon.copy(alpha = alpha),
            modifier = Modifier
                .size(24.dp)
                .background(Color.Transparent),
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            maxLines = 1,
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
    AlertDialog(
        onDismissRequest = { onAction(PockRefAction.DismissBulkDeleteConfirm) },
        title = { Text("删除最近文件") },
        text = {
            Text(
                text = if (count <= 0) {
                    "没有选中任何文件。"
                } else {
                    "确定要从最近列表中删除这 $count 个文件吗?此操作不可撤销。"
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
