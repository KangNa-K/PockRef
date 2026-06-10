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
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.pockref.app.ui.theme.Dimens
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
import androidx.compose.ui.draw.alpha
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

// V_NOW (L2 empty-canvas revamp 2026-06-10): the new
// recent-files grid that replaces the old
// RecentFileSection lists. Renders as a LazyVerticalGrid
// with column count derived from the platform
// WindowWidthSizeClass: Compact -> 2 cols, Medium -> 3
// cols, Expanded -> 5 cols. Each tile is a 1:1 square
// (Modifier.aspectRatio(1f)) thumbnail with a single-line
// filename caption underneath. Swipe-to-dismiss is a
// reveal pattern: as the user drags EndToStart, two 48dp
// circular action buttons (star + delete) fade in
// underneath. The box always springs back after release
// (confirmValueChange = { false }) so the dismiss is
// GESTURE-ONLY — the actual removal is dispatched by
// tapping the revealed button. Avoids accidental
// thumb-flick dismissal the user reported with the old
// Snackbar-undo approach.
@Composable
fun RecentFileGrid(
    entries: List<RecentSceneEntry>,
    sortMode: RecentSortMode,
    showPinnedOnly: Boolean,
    onOpen: (String) -> Unit,
    onRemove: (String) -> Unit,
    onTogglePin: (String) -> Unit,
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
            RecentFileTile(
                entry = entry,
                onOpen = { onOpen(entry.uri) },
                onRemove = { onRemove(entry.uri) },
                onTogglePin = { onTogglePin(entry.uri) },
                onRename = { newLabel -> onRename(entry.uri, newLabel) },
            )
        }
    }
}

@Composable
private fun RecentFileTile(
    entry: RecentSceneEntry,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    onTogglePin: () -> Unit,
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
    // V_NOW (L2 polish v4 2026-06-10): swipe-to-dismiss
    // replaced by long-press context menu. The previous
    // SwipeToDismissBox reveal pattern was unintuitive (the
    // 48dp circular buttons clipped against the narrow
    // thumbnail width on a 3-column tablet grid, and the
    // confirmValueChange dance between Settled and EndToStart
    // confused the gesture's interaction with the surrounding
    // LazyVerticalGrid). Long-press on the thumbnail now
    // opens an M3 DropdownMenu anchored to the tile, with
    // 置顶/取消置顶 and 删除 as 2 DropdownMenuItem entries.
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .combinedClickable(
                        onClick = onOpen,
                        onLongClick = { showMenu = true },
                    ),
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
                if (entry.pinned) {
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.VerticalAlignTop,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
            // V_NOW (L2 polish v7 2026-06-10): M3 contextual
            // floating toolbar replaces the v5/v6 bottom-
            // corner dual circles. The previous build used
            // two 48dp saturated (primary/tertiary/error)
            // circles anchored at bottom-left and bottom-
            // right with Arrangement.SpaceBetween, which
            // had two M3-debt problems:
            //   1. Raw primary/tertiary/error solid colors
            //      clash with M3's container-role system.
            //      M3 reserves those for Filled/FAB primary
            //      surfaces and uses secondaryContainer /
            //      tertiaryContainer / errorContainer for
            //      tonal icon buttons. Bold saturated colors
            //      on a 48dp circle is the MD2 pattern.
            //   2. SpaceBetween corner anchoring on a
            //      5-column tablet grid squeezed both 48dp
            //      buttons into a ~110dp tile, and the
            //      right-hand button clipped to a pill on
            //      the narrowest thumbnails (visible in the
            //      v6 device screenshot on the 5-col SM-X810
            //      landscape).
            //
            // New layout: a single M3 floating toolbar at
            // the bottom-centre of the tile, sized to
            // content. The toolbar is a Surface with
            // surfaceContainerHigh + tonalElevation 3dp (M3
            // standard for elevated floating controls;
            // matches Dimens.ToolbarElevation used by the
            // top toolbar / auto-layout panel), shaped as
            // a 20dp pill. Two 40dp FilledTonalIconButton
            // entries (M3's standard icon-only action
            // component):
            //   - Pin: secondaryContainer by default, swaps
            //     to tertiaryContainer when entry.pinned is
            //     true (preserves v6's "pinned vs unpinned"
            //     visual feedback AND matches the
            //     persistent top-start pinned badge)
            //   - Delete: errorContainer / onErrorContainer
            //     (M3 canonical destructive-action color
            //     pair — pale red surface with dark red
            //     icon, not the bold solid red that v5/v6
            //     used)
            // The scrim is reduced from 35% to 28% because
            // the floating toolbar's surface provides its
            // own contrast boundary and the image should
            // still be visible enough for the user to
            // identify the file.
            val overlayAlpha by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (showMenu) 1f else 0f,
                label = "recent-tile-overlay",
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        MaterialTheme.colorScheme.scrim.copy(alpha = 0.28f * overlayAlpha),
                    ),
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
                    .alpha(overlayAlpha),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = Dimens.ToolbarElevation,
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    FilledTonalIconButton(
                        onClick = {
                            onTogglePin()
                            showMenu = false
                        },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (entry.pinned)
                                MaterialTheme.colorScheme.tertiaryContainer
                            else
                                MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = if (entry.pinned)
                                MaterialTheme.colorScheme.onTertiaryContainer
                            else
                                MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    ) {
                        Icon(
                            imageVector =
                                if (entry.pinned) Icons.Filled.PushPin
                                else Icons.Outlined.PushPin,
                            contentDescription = if (entry.pinned) "取消置顶" else "置顶",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    FilledTonalIconButton(
                        onClick = {
                            onRemove()
                            showMenu = false
                        },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "删除",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = entry.label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onOpen,
                    onLongClick = { showRenameDialog = true },
                ),
        )
    }
    if (showRenameDialog) {
        var newName by remember { mutableStateOf(entry.label) }
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
                    enabled = newName.isNotBlank() && newName != entry.label,
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
