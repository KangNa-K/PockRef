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
import androidx.compose.material.icons.rounded.VerticalAlignTop
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
                onRename = { newLabel -> onRename(entry.uri, newLabel) },
            )
        }
    }
}

@Composable
private fun RecentFileTile(
    entry: RecentSceneEntry,
    onOpen: () -> Unit,
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
    // V_NOW (2026-06-10 user spec "启动页去掉长按封面显示置顶
    // 和删除按钮，以及去除长按封面触发其他功能的相关代码"):
    // the long-press overlay (置顶/删除 M3 floating toolbar
    // introduced in v5/v6/v7) is removed. The thumbnail is
    // now a single-action tile — tap to open. Pin / Delete /
    // Rename all live in the dedicated "edit scene" path on
    // the canvas, not in the start-page peek.
    //
    // What survives from the v5/v6/v7 era:
    //   - the persistent top-start "pinned" badge
    //     ([Icons.Rounded.VerticalAlignTop] in a 20dp
    //     tertiary circle) — this is a read-only indicator,
    //     not a control, so it stays. Tapping the badge does
    //     nothing (no clickable Modifier).
    //   - the label below the thumbnail is still long-press-
    //     able to open the rename dialog (long-press on
    //     TEXT, not on the thumbnail image — the gesture
    //     doesn't conflict with the thumbnail's plain
    //     `clickable` anymore).
    var showRenameDialog by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onOpen),
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
