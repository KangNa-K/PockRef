@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.pockref.app.ui

import android.net.Uri
import com.pockref.app.data.PurSceneCodec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Star
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pockref.app.model.RecentSceneEntry
import com.pockref.app.model.RecentSortMode
import com.pockref.app.model.pickCoverTarget
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
    onSwipePin: (String) -> Unit,
    onSwipeDelete: (String) -> Unit,
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
                onSwipePin = { onSwipePin(entry.uri) },
                onSwipeDelete = { onSwipeDelete(entry.uri) },
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
    onSwipePin: () -> Unit,
    onSwipeDelete: () -> Unit,
    onRename: (String) -> Unit,
) {
    // V_NOW (2026-06-11, swipe v5 — M3 swipe-to-delete):
    //   - Only right-to-left swipe supported.
    //   - The cover image slides left with the finger.
    //   - When the image slides past the commit
    //     threshold, the entire 1:1 tile area is
    //     replaced by a full-size M3 error-colored
    //     delete button.
    //   - Click the delete button → execute deletion.
    //   - Click anywhere else → cancel, slide back.
    //   - M3 motion: emphasizedDecelerate for settle,
    //     emphasizedAccelerate for snap-back.
    val density = LocalDensity.current
    val commitThresholdPx = with(density) { 56.dp.toPx() }
    val maxDragPx = with(density) { 96.dp.toPx() }
    val emphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    val emphasizedAccelerate = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

    var dragOffset by remember { mutableFloatStateOf(0f) }
    var deleteRevealed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Auto-dismiss delete reveal after 3 seconds
    LaunchedEffect(deleteRevealed) {
        if (deleteRevealed) {
            kotlinx.coroutines.delay(3000L)
            deleteRevealed = false
            scope.launch {
                animate(
                    initialValue = dragOffset,
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = 250,
                        easing = emphasizedAccelerate,
                    ),
                ) { value, _ ->
                    dragOffset = value
                }
            }
        }
    }
    val context = LocalContext.current
    val draggableState = rememberDraggableState { delta ->
        // Allow both directions: negative = left-swipe (delete),
        // positive = right-swipe (pin)
        dragOffset = (dragOffset + delta).coerceIn(-maxDragPx, maxDragPx)
    }

    var bitmap by remember(entry.uri, entry.coverImageId) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(entry.uri, entry.coverImageId) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val tempFile = java.io.File.createTempFile("pockref_cover_", ".pur", context.cacheDir)
                try {
                    context.contentResolver.openInputStream(Uri.parse(entry.uri))?.use { input ->
                        tempFile.outputStream().use { out -> input.copyTo(out) }
                    }
                    val codec = PurSceneCodec()
                    val scene = codec.readFromFile(tempFile)
                    val targetImage = pickCoverTarget(
                        images = scene.images,
                        coverImageId = entry.coverImageId,
                    )
                    if (targetImage != null) {
                        codec.imageDecoder.setSourceFile(tempFile)
                        codec.imageDecoder.decodeThumbnail(targetImage)?.bitmap?.asImageBitmap()
                    } else {
                        null
                    }
                } finally {
                    tempFile.delete()
                }
            }.getOrNull()
        }
    }

    var showRenameDialog by remember { mutableStateOf(false) }

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
                Modifier.background(Color.Black.copy(alpha = 0.15f))
            } else {
                Modifier
            },
        )
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
            // 1. Delete button — matches the sheet's
            //    SheetActionButton style (icon + label,
            //    error tint) but scaled up for the tile
            //    context. M3 error color scheme.
            if (deleteRevealed) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.errorContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .size(24.dp)
                                .clickable { onSwipeDelete() },
                        )
                    }
                }
            }

            // 2. Foreground (thumbnail) — slides left
            //    on swipe, hidden when deleteRevealed.
            if (!deleteRevealed) {
                Box(
                    modifier = tileModifier
                        .then(
                            if (!multiSelectMode) {
                                Modifier.draggable(
                                    state = draggableState,
                                    orientation = Orientation.Horizontal,
                                    onDragStopped = {
                                        if (dragOffset > commitThresholdPx) {
                                            // Right swipe past threshold → auto-pin
                                            onSwipePin()
                                        } else if (dragOffset < -commitThresholdPx) {
                                            // Left swipe past threshold → reveal delete
                                            deleteRevealed = true
                                        }
                                        val target = if (deleteRevealed) {
                                            -maxDragPx
                                        } else {
                                            0f
                                        }
                                        scope.launch {
                                            animate(
                                                initialValue = dragOffset,
                                                targetValue = target,
                                                animationSpec = tween(
                                                    durationMillis = 300,
                                                    easing = emphasizedDecelerate,
                                                ),
                                            ) { value, _ ->
                                                dragOffset = value
                                            }
                                        }
                                    },
                                )
                            } else {
                                Modifier
                            },
                        )
                        .combinedClickable(
                            onClick = onTap,
                            onLongClick = if (!multiSelectMode) onLongPress else null,
                        )
                        .graphicsLayer {
                            translationX = dragOffset
                        },
                ) {
                    val bmp = bitmap
                    if (bmp != null) {
                        androidx.compose.foundation.Image(
                            bitmap = bmp,
                            contentDescription = entry.label,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    if (multiSelectMode) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(24.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color.White.copy(alpha = 0.85f)),
                            contentAlignment = Alignment.Center,
                        ) {
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
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
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
                    if (entry.coverImageId != null && !multiSelectMode) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.tertiaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Star,
                                contentDescription = "已设为封面",
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
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
                    onClick = {
                        if (deleteRevealed) {
                            // Click on label area while delete
                            // is revealed → cancel
                            deleteRevealed = false
                            dragOffset = 0f
                        } else {
                            onTap()
                        }
                    },
                    onLongClick = if (multiSelectMode) {
                        { /* no-op in multi-select */ }
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
