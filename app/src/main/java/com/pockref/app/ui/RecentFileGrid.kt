@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.pockref.app.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pockref.app.model.RecentSceneEntry
import com.pockref.app.model.RecentSortMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// V_NOW (L2 empty-canvas revamp 2026-06-10 + start-page
// multi-select 2026-06-10): the recent-files grid that
// replaces the old RecentFileSection lists. Renders as
// a LazyVerticalGrid with column count derived from
// the platform WindowWidthSizeClass: Compact -> 3
// cols, Medium -> 4 cols, Expanded -> 5 cols. Each
// tile is a 1:1 square (Modifier.aspectRatio(1f))
// thumbnail with a single-line filename caption
// underneath. The tile is a dual-state surface:
//   1. Idle mode ([multiSelectMode] = false):
//      tap = open the scene, long-press = enter
//      multi-select mode (with this tile auto-
//      selected).
//   2. Multi-select mode ([multiSelectMode] = true):
//      tap = toggle this tile's selection, long-
//      press is unbound (the multi-select chrome
//      above already gives the user the bulk
//      action affordances; long-press would just
//      re-enter the mode they're already in).
// The dual-state clickable is implemented by
// [combinedClickable]'s nullable [onLongClick] in
// the parent — when [multiSelectMode] is true the
// lambda is a no-op, and the tap dispatches
// [PockRefAction.ToggleRecentSelection] instead
// of [PockRefAction.OpenRecentScene].
@Composable
fun RecentFileGrid(
    entries: List<RecentSceneEntry>,
    sortMode: RecentSortMode,
    showPinnedOnly: Boolean,
    multiSelectMode: Boolean,
    selectedUris: Set<String>,
    onOpen: (String) -> Unit,
    onEnterMultiSelect: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onRename: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context as? androidx.activity.ComponentActivity
    val columnCount = if (activity != null) {
        when (calculateWindowSizeClass(activity).widthSizeClass) {
            WindowWidthSizeClass.Compact -> 3
            WindowWidthSizeClass.Medium -> 4
            else -> 5
        }
    } else {
        3
    }
    val sorted = remember(entries, sortMode, showPinnedOnly) {
        val filtered = if (showPinnedOnly) entries.filter { it.pinned } else entries
        when (sortMode) {
            RecentSortMode.RECENT -> filtered.sortedWith(
                compareByDescending<RecentSceneEntry> { it.pinned }.thenByDescending { it.timestamp }
            )
            RecentSortMode.NAME_ASC -> filtered.sortedWith(
                compareByDescending<RecentSceneEntry> { it.pinned }.thenBy { it.label.lowercase() }
            )
            RecentSortMode.NAME_DESC -> filtered.sortedWith(
                compareByDescending<RecentSceneEntry> { it.pinned }.thenByDescending { it.label.lowercase() }
            )
        }
    }
    if (sorted.isEmpty()) {
        Text(
            text = "无最近文件",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = modifier.fillMaxWidth().padding(vertical = 16.dp),
        )
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(columnCount),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        items(sorted, key = { it.uri }) { entry ->
            val isSelected = entry.uri in selectedUris
            RecentFileTile(
                entry = entry,
                multiSelectMode = multiSelectMode,
                isSelected = isSelected,
                onTap = {
                    if (multiSelectMode) {
                        onToggleSelection(entry.uri)
                    } else {
                        onOpen(entry.uri)
                    }
                },
                onLongPress = {
                    if (!multiSelectMode) onEnterMultiSelect(entry.uri)
                },
                onRename = { newLabel -> onRename(entry.uri, newLabel) },
            )
        }
    }
}

@Composable
private fun RecentFileTile(
    entry: RecentSceneEntry,
    multiSelectMode: Boolean,
    isSelected: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onRename: (String) -> Unit,
) {
    val context = LocalContext.current
    var bitmap by remember(entry.uri) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(entry.uri) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(entry.uri))?.use { input ->
                    BitmapFactory.decodeStream(input)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    // V_NOW (2026-06-10, "启动页长按 = 多选"): the
    // thumbnail's click surface is now dual-purpose:
    //   - tap in idle mode -> open the scene
    //   - long-press in idle mode -> enter multi-select
    //     (handled by the caller via [onLongPress])
    //   - tap in multi-select mode -> toggle this tile's
    //     selection (handled by the caller via [onTap])
    //   - long-press in multi-select mode -> no-op (the
    //     bottom action bar already offers every action
    //     the user would need; a long-press would just
    //     re-enter the mode they're already in)
    //
    // V_NOW: rename via long-press has been moved off
    // the LABEL ([Text]) and back onto the THUMBNAIL
    // ([combinedClickable.onLongClick]) so a single
    // long-press surfaces every per-tile action (open /
    // rename on idle, multi-select on enter, selection
    // toggle on subsequent presses). The previous
    // "long-press label = rename" gesture was a v_NOW
    // 2026-06-10 hack from the moment the v5/v6/v7
    // thumbnail long-press overlay was removed —
    // reassigning the long-press to the thumbnail (and
    // routing it through the new multi-select entry
    // path) restores the natural "long-press a tile to
    // do something to it" mental model. The v_NOW
    // 2026-06-10 caption-long-press comment is now
    // obsolete: there is no caption-long-press in this
    // revision.
    var showRenameDialog by remember { mutableStateOf(false) }
    // V_NOW (2026-06-10, M3 multi-select visual state):
    // tile background + border switch based on the
    // three orthogonal states:
    //   - [multiSelectMode] (whole grid mode flag) →
    //     use [colorScheme.surfaceContainer] (M3's
    //     distinct surface tint, slightly higher than
    //     [surface]) instead of [surfaceVariant] for
    //     the base tile. This gives every tile a
    //     uniform "selection-active" backdrop so the
    //     user can see "I'm in multi-select mode" at
    //     a glance, even on tiles that AREN'T selected.
    //   - [isSelected] (this specific tile's selection
    //     bit) → 2dp primary border around the tile.
    //     The border is the M3 standard "selected item"
    //     affordance (see e.g. M3 ListItem selected
    //     state) — it reads as "highlighted" without
    //     being as visually heavy as a fill change.
    //   - [entry.pinned] (persistent pinned badge) →
    //     unchanged from the prior V_NOW; the badge
    //     sits in the top-start corner regardless of
    //     mode / selection.
    //
    // The two new visuals layer cleanly with the
    // existing pinned badge: when a pinned tile is
    // selected, the badge stays in its top-start
    // position and the 2dp border surrounds the
    // entire tile (the border does NOT overlap the
    // badge because the badge has its own
    // 4dp padding and the border is drawn on the
    // Box's outside, so the badge reads as "on top
    // of" the border).
    val tileBaseColor = if (multiSelectMode) {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val tileModifier = Modifier
        .fillMaxWidth()
        .aspectRatio(1f)
        .clip(RoundedCornerShape(8.dp))
        .background(tileBaseColor)
        .then(
            if (isSelected) {
                Modifier.border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(8.dp),
                )
            } else {
                Modifier
            },
        )
        .combinedClickable(
            onClick = onTap,
            onLongClick = onLongPress,
        )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = tileModifier) {
            val bmp = bitmap
            if (bmp != null) {
                androidx.compose.foundation.Image(
                    bitmap = bmp,
                    contentDescription = entry.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // V_NOW (2026-06-10, M3 multi-select
            // checkbox overlay): when the grid is in
            // multi-select mode, render an M3 CheckBox
            // icon in the top-end (top-right in LTR)
            // corner. Top-end is the standard M3 /
            // Android list-item "checked" affordance
            // position (e.g. Settings → Apps → list of
            // apps with checkboxes). Top-start is
            // already taken by the pinned badge, so
            // we put the checkbox on the opposite
            // side to avoid the two overlapping on
            // small thumbnails.
            //
            // Filled [Icons.Rounded.CheckBox] when
            // selected, outlined [Icons.Rounded.CheckBoxOutlineBlank]
            // when not — matches the M3 spec for
            // ListItem's leading/trailing checkbox.
            // 20dp size, [primary] tint when filled
            // (high-contrast selection), 24dp white
            // circle backing for legibility on dark
            // thumbnails. The 24dp white circle is
            // intentionally NOT [surfaceContainer] —
            // it's a static white that's always
            // readable on any thumbnail, including
            // a black one. Same pattern Google
            // Photos uses for the "select" mode
            // overlay.
            if (multiSelectMode) {
                // V_NOW (2026-06-10, user spec "勾选框正方形的
                // 框框下不要有圆形的白色图案"): the
                // 24dp white circle backing is removed.
                // The M3 [Checkbox] composable renders
                // directly on top of the thumbnail —
                // the same way the standard M3 ListItem
                // checkbox appears (no backing circle).
                // The `.align(Alignment.TopEnd).padding(4.dp)
                // .size(24.dp)` modifiers are now on the
                // [Icon] itself (the previous [Box] wrapper
                // is gone), keeping the visual position
                // identical to the prior design so the
                // pinned badge in the opposite corner
                // doesn't shift.
                //
                // V_NOW (2026-06-10, user spec "提 outline
                // 颜色改成 A"): outline [CheckBoxOutlineBlank]
                // tint bumped from [colorScheme.onSurfaceVariant]
                // (M3 "secondary" surface contrast) to
                // [colorScheme.onSurface] (M3 "primary"
                // surface contrast). The previous variant
                // tint was too low-contrast on the
                // common-case light / pale reference-art
                // thumbnails — the user spotted this on
                // first real-device test and chose to fix
                // preemptively rather than wait for a
                // "checkbox invisible" bug report. The
                // selected-state tint stays
                // [colorScheme.primary] (high-contrast
                // teal), so the two states (selected vs
                // unselected) are still visually
                // distinct on a light thumbnail: teal
                // filled-box vs near-black outline-box.
                // Both stay WCAG AA on
                // [surfaceContainer] / [surfaceVariant]
                // backgrounds (M3 spec guarantees the
                // contrast pair).
                Icon(
                    imageVector = if (isSelected) {
                        Icons.Rounded.CheckBox
                    } else {
                        Icons.Rounded.CheckBoxOutlineBlank
                    },
                    contentDescription = if (isSelected) "已选中" else "未选中",
                    tint = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(24.dp),
                )
            }
            // V_NOW (2026-06-10, user spec "把启动页置顶的
            // 文件的左上角标记换成置顶按钮同款图标 +
            // 颜色按 M3 secondaryContainer 软紫方案"):
            //   1. Icon: [Icons.Rounded.VerticalAlignTop]
            //      → [Icons.Rounded.PushPin] — the
            //      bottom action bar's 置顶 / 取消置顶
            //      buttons use [PushPin], so the badge
            //      icon now matches the action verb
            //      instead of the abstract "align top"
            //      "T" glyph.
            //   2. Colors: M3 [secondaryContainer] /
            //      [onSecondaryContainer] — soft lavender
            //      in the default M3 light/dark theme,
            //      distinct from the brand teal
            //      [primary] so the badge doesn't
            //      compete with the main "+" action
            //      button for visual weight. M3 spec
            //      role: "secondary state, persistent
            //      indicator" (the same role chips,
            //      filters, and badges use).
            //   3. Geometry: 20dp circle / 14dp icon →
            //      24dp circle / 16dp icon. The
            //      thumbtack glyph has more visual
            //      detail than the simple "T", so the
            //      badge needs the extra room to
            //      render legibly. The 24dp size also
            //      matches the top-end checkbox
            //      badge in the same corner
            //      (symmetric M3 corner pins).
            if (entry.pinned) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PushPin,
                        contentDescription = "已置顶",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        // V_NOW (2026-06-10, M3 multi-select
        // re-clickability + rename preserved): the
        // label below the thumbnail keeps a
        // [combinedClickable] for two reasons:
        //   1. Tap on the label = open (or toggle
        //      selection in multi-select mode),
        //      mirroring the thumbnail's tap
        //      behavior — the label is a "fat
        //      target" overlay on the thumbnail so
        //      a user whose finger lands on the
        //      text still gets the same action.
        //   2. Long-press on the LABEL (NOT the
        //      thumbnail) = open the rename dialog.
        //      This is the preserved v_NOW
        //      2026-06-10 gesture from the moment
        //      the v5/v6/v7 thumbnail long-press
        //      overlay was removed — the long-
        //      press-to-rename functionality was
        //      kept alive by moving it onto the
        //      label. NOW the thumbnail's long-
        //      press is claimed by the multi-select
        //      entry, so rename stays on the label
        //      where it was. (A "long-press the
        //      thumbnail, then long-press again
        //      for rename" two-gesture sequence
        //      would be worse UX, so the label-
        //      based rename is the right answer.)
        // The combinedClickable is identical in
        // shape to the thumbnail's — idle-mode
        // tap opens, multi-select-mode tap
        // toggles — so the two targets feel like
        // one extended clickable area.
        Text(
            text = displayLabel(entry.label),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onTap,
                    onLongClick = if (multiSelectMode) {
                        { /* no-op in multi-select — see kdoc above */ }
                    } else {
                        { showRenameDialog = true }
                    },
                ),
        )
    }
    if (showRenameDialog) {
        var newName by remember { mutableStateOf(displayLabel(entry.label)) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("重命名") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    label = { Text("新名称") },
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRename(newName)
                        showRenameDialog = false
                    },
                    enabled = newName.isNotBlank() && newName != displayLabel(entry.label),
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                Button(onClick = { showRenameDialog = false }) {
                    Text("取消")
                }
            },
        )
    }
}

/**
 * V_NOW (2026-06-10 user spec "启动页的文件列表里的文件名
 * 不要显示文件格式'.pur'"): strip the trailing `.pur`
 * scene-format extension from a [RecentSceneEntry.label]
 * for *display only*. The stored label is left untouched
 * — the rename dialog and the canvas-side scene-name
 * path continue to see the un-stripped label, so the
 * storage format / SharedPreferences schema does not
 * need a migration.
 *
 * Case-insensitive match because some content providers
 * (and some Linux filesystems on Samsung devices)
 * surface the extension in uppercase (`.PUR`). Any
 * further-extension ambiguity is left alone — `.tar.pur`
 * would strip the trailing `.pur` only, which is
 * acceptable for the start-page peek context.
 */
private fun displayLabel(raw: String): String =
    if (raw.length >= 4 && raw.regionMatches(raw.length - 4, ".pur", 0, 4, ignoreCase = true)) {
        raw.substring(0, raw.length - 4)
    } else {
        raw
    }
