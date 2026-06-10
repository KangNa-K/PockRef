@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.pockref.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Deselect
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
 * V_NOW (2026-06-10, "启动页多选 + 底部 Bulk Action Bar
 * 置顶"): the multi-select top app bar shown on the
 * start page when [PockRefUiState.recentMultiSelectMode]
 * is true. Mirrors the M3 [CenterAlignedTopAppBar] visual
 * contract — title in the centre, navigation icon
 * (X close) on the leading edge — but built directly from
 * [Surface] + [Row] so the chrome shares the
 * [com.pockref.app.ui.theme.Dimens] tokens (corner
 * radius / surface alpha / edge inset / tonal elevation)
 * the rest of the floating UI uses.
 *
 * Visual contract:
 *   - `Surface(shape = 12dp, tonalElevation = 3dp,
 *     color = surface @ alpha 0.92)` — frosted glass,
 *     matches the top toolbar's pre-existing values.
 *   - 48dp height — M3 [MinTouchTarget], matches
 *     [Dimens.TopToolbarHeight].
 *   - Title: "已选 N 项" (or "选择项目" when the selection
 *     is empty) in [titleMedium] typography. The
 *     "N" is computed from the intersection of
 *     [PockRefUiState.recentSelection] and the
 *     currently-displayed [PockRefUiState.recentScenes]
 *     so a stale URI (one the user deleted via the
 *     bottom bar) doesn't bump the count.
 *   - Trailing X (close) icon button: dispatches
 *     [PockRefAction.ExitRecentMultiSelect]. Has a
 *     `contentDescription = "取消多选"` for TalkBack.
 *   - Status-bar inset: the bar pads itself by the
 *     status-bar height so it sits BELOW the system
 *     clock/battery, not under it. Without this
 *     padding the title's first line would be cut off
 *     on devices that draw the status bar on top of
 *     the app content (default Android 15+ behaviour).
 *
 * Positioning: this composable is designed to be
 * `align(Alignment.TopCenter)`-ed inside a [Box] that
 * also contains the [EmptyCanvasState] (sibling).
 * The bar's width is `matchParentSize()`-equivalent
 * (the [Surface] is constrained by its parent's
 * `widthIn(max = ...)` at the call site).
 */
@Composable
fun BoxScope.RecentMultiSelectTopBar(
    state: PockRefUiState,
    onAction: (PockRefAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val validSelection = state.recentSelection.intersect(
        state.recentScenes.map { it.uri }.toSet(),
    )
    val title = if (validSelection.isEmpty()) {
        "选择项目"
    } else {
        "已选 ${validSelection.size} 项"
    }
    Surface(
        modifier = modifier
            .align(Alignment.TopCenter)
            .padding(
                horizontal = Dimens.EdgeInset,
                vertical = Dimens.EdgeInset,
            )
            .windowInsetsPadding(WindowInsets.statusBars)
            .fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(Dimens.ChromeCornerRadius),
        color = MaterialTheme.colorScheme.surface.copy(alpha = Dimens.ChromeSurfaceAlpha),
        tonalElevation = Dimens.ToolbarElevation,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.TopToolbarHeight)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // V_NOW: the close icon is the bar's
            // ONLY interactive element. The bar has
            // no menu / overflow / settings / etc.
            // — the bottom action bar below owns
            // every multi-select action, and the
            // top bar's only job is "show me how
            // many I selected and let me get out".
            // 48dp IconButton (default size) with
            // 24dp icon (M3 standard) so the hit
            // area is finger-friendly.
            IconButton(
                onClick = { onAction(PockRefAction.ExitRecentMultiSelect) },
                modifier = Modifier.size(Dimens.TopToolbarHeight),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "取消多选",
                    modifier = Modifier.size(24.dp),
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
            // V_NOW: the trailing slot is a 48dp
            // "spacer" IconButton-sized Box so the
            // title stays visually centred (the
            // leading X is 48dp, so the trailing
            // needs to match). The spacer is
            // non-clickable — its only job is to
            // balance the layout. Using a sized
            // Box (not Spacer-with-modifier)
            // because [Spacer] doesn't take a
            // default min-touch-target, and a
            // 0dp spacer would still leave the
            // title off-centre by 4dp.
            Box(modifier = Modifier.size(Dimens.TopToolbarHeight))
        }
    }
}

/**
 * V_NOW (2026-06-10): the multi-select bottom action
 * bar shown on the start page when
 * [PockRefUiState.recentMultiSelectMode] is true.
 * Mirrors the M3 [BottomAppBar] visual contract —
 * 80dp tall, action icons evenly distributed, icon-
 * above-text label per M3 small-bottom-app-bar
 * guidance — but built directly from [Surface] + [Row]
 * so the chrome shares the [Dimens] tokens.
 *
 * Actions (4 in the row, evenly spaced):
 *
 *   1. 全选 / 清空 (toggle): the icon and label
 *      switch based on whether the current
 *      selection covers the whole visible list
 *      ([PockRefAction.SelectAllRecentScenes] when
 *      not all are selected, [PockRefAction.ClearRecentSelection]
 *      when they are). The M3 "select all" / "deselect
 *      all" pattern is a single button that flips
 *      between two states, not two buttons.
 *
 *   2. 置顶 (always visible): when the selection
 *      contains ANY unpinned entry, the button is
 *      enabled and the icon is
 *      [Icons.Rounded.PushPin]. When ALL selected
 *      entries are already pinned, the button is
 *      disabled (alpha 0.38) — a tap would be a
 *      no-op anyway, the disabled state makes the
 *      "nothing to do" reading explicit.
 *
 *   3. 取消置顶 (always visible): symmetric to 置顶.
 *      Enabled when the selection contains ANY
 *      pinned entry, disabled when all selected
 *      entries are unpinned. The two buttons
 *      (置顶 / 取消置顶) are always both visible so
 *      the user can flip pinned ↔ unpinned with a
 *      single tap; the disabled state on each
 *      side is the M3 affordance for "this
 *      direction has no work to do".
 *
 *   4. 删除 (always visible): opens the bulk-delete
 *      confirmation dialog by dispatching
 *      [PockRefAction.RequestBulkDeleteConfirm].
 *      The actual deletion happens after the user
 *      confirms (see [BulkDeleteConfirmDialog]).
 *      Disabled when the selection is empty.
 *
 * V_NOW (smart affordance state): the bottom bar
 * reads [PockRefUiState.recentSelection] ∩
 * [PockRefUiState.recentScenes] (not just
 * [recentSelection]) to decide which buttons are
 * enabled — an orphan URI in the selection set
 * (one that was just deleted via a separate path)
 * doesn't flip the disabled state into "nothing
 * to do".
 *
 * Navigation-bar inset: the bar pads itself by
 * the gesture-navigation / 3-button-nav bar
 * height so the action icons aren't hidden by
 * the system nav bar on devices that don't
 * edge-to-edge the app.
 */
@Composable
fun BoxScope.RecentMultiSelectBottomBar(
    state: PockRefUiState,
    onAction: (PockRefAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val validScenes = state.recentScenes
    val validSelection = state.recentSelection.intersect(
        validScenes.map { it.uri }.toSet(),
    )
    val allSelected = validScenes.isNotEmpty() &&
        validScenes.all { it.uri in validSelection }
    val anyUnpinned = validScenes.any { it.uri in validSelection && !it.pinned }
    val anyPinned = validScenes.any { it.uri in validSelection && it.pinned }
    val isAllPinned = validScenes.any { it.uri in validSelection } &&
        validScenes.filter { it.uri in validSelection }.all { it.pinned }
    Surface(
        modifier = modifier
            .align(Alignment.BottomCenter)
            .padding(
                horizontal = Dimens.EdgeInset,
                vertical = Dimens.EdgeInset,
            )
            .windowInsetsPadding(WindowInsets.navigationBars)
            .fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(Dimens.ChromeCornerRadius),
        color = MaterialTheme.colorScheme.surface.copy(alpha = Dimens.ChromeSurfaceAlpha),
        tonalElevation = Dimens.ToolbarElevation,
    ) {
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
 * [RecentMultiSelectBottomBar]'s action row. M3
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
