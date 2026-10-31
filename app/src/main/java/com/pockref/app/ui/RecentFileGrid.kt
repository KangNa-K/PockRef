@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package com.pockref.app.ui

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.pockref.app.model.RecentSceneEntry
import com.pockref.app.model.RecentSortMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * V_NOW (2026-06-11, swipe v6 — progressive reveal):
 * Token set for the progressive swipe-to-action UX on
 * the recent-files grid. Lives next to the only caller
 * to keep the change surface small; if a second screen
 * ever needs the same vocabulary, hoist to a top-level
 * theme/Dimens module.
 *
 * Distance thresholds tuned for a 1:1 grid tile in a
 * 3/4/5 column layout (Compact/Medium/Expanded):
 *   - [visualRevealStart] = 12 dp — below this, the
 *     user is just noise / jitter; the button stays
 *     invisible and the thumb does not move. This
 *     matches Android's 8 dp touch slop and adds a
 *     small safety margin.
 *   - [commitThreshold] = 64 dp — past this, the user
 *     has clearly committed to the action; the button
 *     snaps to its full size and stays there waiting
 *     for a tap (or auto-dismisses in 3 s).
 *   - [maxDrag] = 112 dp — hard cap. The thumb stops
 *     here even if the user keeps dragging; this is
 *     enough to fully expose a 48 dp button plus its
 *     right-edge breathing room.
 *
 * v6 is the only path — the v5 velocity fast-path
 * "full-tile replace" delete surface is gone
 * (2026-06-11 user request). Every swipe, regardless
 * of release velocity, goes through the progressive
 * reveal + tap-to-confirm flow.
 */
private object SwipeDimens {
    val visualRevealStart = 12.dp
    val commitThreshold = 64.dp
    val maxDrag = 112.dp

    // V_NOW (2026-06-11, swipe v6 grid-tile tuning):
    // collapsed 24→32 dp, expanded 48→40 dp. The
    // previous 48 dp at commit on a 1:1 grid cell
    // (~145 dp wide at 3-col Compact) left the
    // button visually crowded against the edge; 40
    // dp gives a tighter "edge-anchored chip" feel
    // that matches the SwipeActionButton being a
    // secondary affordance, not a primary CTA. Corner
    // radii scale accordingly so the circle/pill
    // proportion stays consistent (size / 2).
    val buttonSizeCollapsed = 32.dp
    val buttonSizeExpanded = 40.dp
    val buttonCornerCollapsed = 16.dp
    val buttonCornerExpanded = 20.dp

    val autoDismissMs = 3000L
    val settleMs = 280L
}

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
    val commitThresholdPx = with(density) { SwipeDimens.commitThreshold.toPx() }
    val visualRevealStartPx = with(density) { SwipeDimens.visualRevealStart.toPx() }
    val maxDragPx = with(density) { SwipeDimens.maxDrag.toPx() }
    val emphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    val emphasizedAccelerate = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

    var dragOffset by remember { mutableFloatStateOf(0f) }
    // V_NOW (2026-06-11, swipe v6 position fix #3):
    // the outer tile Box's measured width in pixels.
    // Set by [Modifier.onSizeChanged] on the thumbnail
    // Box; read by the SwipeActionButton's
    // [graphicsLayer] lambda to compute the half-tile
    // offset. We use [onSizeChanged] (state) rather
    // than [BoxWithConstraints.maxWidth] (val) because
    // a captured outer-lambda val inside the modifier
    // chain's [graphicsLayer] lambda is not always
    // visible from the SwipeActionButton's nested
    // receiver scope in this compose-bom 1.7+ build
    // (the receiver shadowing chain stops one level
    // deep). A state read through the snapshot system
    // sidesteps the capture ambiguity entirely.
    var tileWidthPx by remember { mutableFloatStateOf(0f) }
    // V_NOW (2026-06-11, swipe v6): locked-at-commit
    // state. When non-null, the revealed action button
    // stays at full size waiting for a tap. Null =
    // not locked, no button shown.
    var swipeLocked by remember { mutableStateOf<SwipeActionKind?>(null) }
    val scope = rememberCoroutineScope()
    // Auto-dismiss: when locked, start a 3 s timer; if
    // the user does not tap the button in that window,
    // unlock and slide the thumb back home.
    LaunchedEffect(swipeLocked) {
        val locked = swipeLocked ?: return@LaunchedEffect
        kotlinx.coroutines.delay(SwipeDimens.autoDismissMs)
        swipeLocked = null
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
        @Suppress("UNUSED_EXPRESSION") locked
    }
    // V_NOW (2026-06-11, swipe v6): confirmation gate
    // for the v6 delete path. v5 went straight to
    // [onSwipeDelete] on tile tap; v6 splits the
    // gesture (swipe to reveal, tap to intend) and
    // adds an M3 [AlertDialog] so the user has one
    // more chance to cancel a destructive action.
    // Pin is reversible and skips this gate.
    val context = LocalContext.current
    val draggableState = rememberDraggableState { delta ->
        // Allow both directions: negative = left-swipe (delete),
        // positive = right-swipe (pin)
        dragOffset = (dragOffset + delta).coerceIn(-maxDragPx, maxDragPx)
    }

    var bitmap by remember(entry.uri, entry.coverImageId) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(entry.uri, entry.coverImageId) {
        bitmap = withContext(Dispatchers.IO) {
            loadRecentSceneCover(context, entry.uri, entry.coverImageId)?.asImageBitmap()
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
        // V_NOW (2026-06-11, swipe v6 position fix #3):
        // record the tile's actual measured width
        // into [tileWidthPx] (a state in the outer
        // [RecentFileTile] body). The
        // SwipeActionButton's [graphicsLayer] lambda
        // reads this state to compute its half-tile
        // offset. We can't just capture a val from
        // [BoxWithConstraints.maxWidth] because the
        // captured outer-lambda val is not visible
        // from inside the SwipeActionButton's nested
        // receiver scope in this compose-bom 1.7+
        // build (the receiver shadowing chain stops
        // one level deep). A state read sidesteps
        // the capture ambiguity.
        .onSizeChanged { size -> tileWidthPx = size.width.toFloat() }
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
            // V_NOW (2026-06-11, swipe v6 position fix):
            // the button's modifier chain (see below)
            // anchors the button at the tile center
            // (via [fillMaxSize +
            // wrapContentSize(Center)]) and then
            // translates it horizontally with
            // [graphicsLayer] to land on the
            // revealed-strip midpoint. The tile width
            // comes from the [tileWidthPx] state set by
            // [Modifier.onSizeChanged] on the thumbnail
            // Box; see the comments at the
            // [tileWidthPx] declaration and the
            // [graphicsLayer] block below.
            // 1. Delete button — DEAD CODE in v6 (the
            //    v5 full-tile replace surface is
            //    removed; the v6 path uses
            //    [SwipeActionButton] layered on top
            //    of the thumbnail below). Gated on
            //    [false] so the composable tree
            //    still compiles but the branch never
            //    renders. Future cleanup: delete
            //    the entire block; kept here for
            //    diff readability against the v5
            //    history.
            if (false) {
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
            //    on swipe. In v6 the thumbnail is
            //    always visible (the SwipeActionButton
            //    layered on top of it handles the
            //    locked-state visual).
            Box(
                modifier = tileModifier
                    .then(
                        if (!multiSelectMode) {
                            Modifier.draggable(
                                state = draggableState,
                                orientation = Orientation.Horizontal,
                                    onDragStopped = {
                                        // V_NOW (2026-06-11, swipe v6):
                                        // distance-based routing at
                                        // release. The v5
                                        // "auto-execute on commit"
                                        // path is removed; the v6
                                        // flow locks the action
                                        // button at full size and
                                        // waits for a tap.
                                        val goingLeft = dragOffset < 0f
                                        val goingRight = dragOffset > 0f
                                        val pastCommit = kotlin.math.abs(dragOffset) >= commitThresholdPx
                                        val pastReveal = kotlin.math.abs(dragOffset) >= visualRevealStartPx
                                        when {
                                            pastCommit && goingLeft -> {
                                                swipeLocked = SwipeActionKind.Delete
                                                scope.launch {
                                                    animate(
                                                        initialValue = dragOffset,
                                                        targetValue = -commitThresholdPx,
                                                        animationSpec = tween(
                                                            durationMillis = SwipeDimens.settleMs.toInt(),
                                                            easing = emphasizedDecelerate,
                                                        ),
                                                    ) { value, _ -> dragOffset = value }
                                                }
                                            }
                                            pastCommit && goingRight -> {
                                                swipeLocked = SwipeActionKind.Pin
                                                scope.launch {
                                                    animate(
                                                        initialValue = dragOffset,
                                                        targetValue = commitThresholdPx,
                                                        animationSpec = tween(
                                                            durationMillis = SwipeDimens.settleMs.toInt(),
                                                            easing = emphasizedDecelerate,
                                                        ),
                                                    ) { value, _ -> dragOffset = value }
                                                }
                                            }
                                            pastReveal && goingLeft -> {
                                                swipeLocked = SwipeActionKind.Delete
                                                scope.launch {
                                                    animate(
                                                        initialValue = dragOffset,
                                                        targetValue = -commitThresholdPx,
                                                        animationSpec = tween(
                                                            durationMillis = SwipeDimens.settleMs.toInt(),
                                                            easing = emphasizedDecelerate,
                                                        ),
                                                    ) { value, _ -> dragOffset = value }
                                                }
                                            }
                                            pastReveal && goingRight -> {
                                                swipeLocked = SwipeActionKind.Pin
                                                scope.launch {
                                                    animate(
                                                        initialValue = dragOffset,
                                                        targetValue = commitThresholdPx,
                                                        animationSpec = tween(
                                                            durationMillis = SwipeDimens.settleMs.toInt(),
                                                            easing = emphasizedDecelerate,
                                                        ),
                                                    ) { value, _ -> dragOffset = value }
                                                }
                                            }
                                            else -> {
                                                swipeLocked = null
                                                scope.launch {
                                                    animate(
                                                        initialValue = dragOffset,
                                                        targetValue = 0f,
                                                        animationSpec = tween(
                                                            durationMillis = 250,
                                                            easing = emphasizedAccelerate,
                                                        ),
                                                    ) { value, _ -> dragOffset = value }
                                                }
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
                        .testTag("recent-file-cover:${entry.uri}")
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
                                // V_NOW (2026-06-11): the
                                // check indicator background is a
                                // rounded square (4dp radius),
                                // not a circle. The user asked
                                // for a square white plate under
                                // the checkbox glyph; a small
                                // radius keeps the corners from
                                // looking like a raw hard edge.
                                .clip(RoundedCornerShape(4.dp))
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
                                modifier = Modifier
                                    .size(16.dp)
                                    .offset(y = 1.dp),
                            )
                        }
                    }
                    // V_NOW (2026-06-11): removed the
                    // "cover image" star badge from the
                    // recent-files grid. Users found the
                    // green star in the top-right corner
                    // confusing — it looked like a favorite
                    // marker rather than a "this scene has
                    // a cover image" hint. The cover image
                    // can still be set / inspected inside
                    // the scene itself.
                    if (false && entry.coverImageId != null && !multiSelectMode) {
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
            // 3. v6 progressive action button (layered
            //    ABOVE the thumbnail). Rendered inside
            //    the outer tile Box (403) so it shares
            //    the tile's coordinate frame, and after
            //    the thumbnail so it draws on top.
            //
            //    Geometry: thumbnail is full tile width
            //    with translationX = dragOffset.
            //    Revealed strip spans
            //    [tile_w + dragOffset, tile_w] (left)
            //    or [0, dragOffset] (right); its
            //    midpoint is at
            //    tile_w + dragOffset/2 (left) or
            //    dragOffset/2 (right). We anchor the
            //    button at Alignment.Center (tile_w/2)
            //    and translate by
            //    tile_w/2 + dragOffset/2 (sign by
            //    direction) so the center lands on the
            //    strip midpoint. [tileWidthPx] is in
            //    pixels (the unit
            //    graphicsLayer.translationX operates in).
            if (kotlin.math.abs(dragOffset) >= visualRevealStartPx) {
                val revealProgressRaw = (
                    (kotlin.math.abs(dragOffset) - visualRevealStartPx) /
                        (commitThresholdPx - visualRevealStartPx)
                    ).coerceIn(0f, 1f)
                val goingLeft = dragOffset < 0f
                val lockedKind = swipeLocked
                val kind = lockedKind
                    ?: if (goingLeft) SwipeActionKind.Delete else SwipeActionKind.Pin
                val isLocked = lockedKind != null
                SwipeActionButton(
                    kind = kind,
                    progress = revealProgressRaw,
                    locked = isLocked,
                    onClick = {
                        // V_NOW (2026-06-11, swipe v6):
                        // tap-on-locked-button handler.
                        // Pin is reversible so it executes
                        // immediately and resets. Delete
                        // is destructive and shows the
                        // M3 confirmation dialog; the
                        // actual [onSwipeDelete] fires
                        // only if the user confirms.
                        when (kind) {
                            SwipeActionKind.Pin -> {
                                onSwipePin()
                                swipeLocked = null
                                scope.launch {
                                    animate(
                                        initialValue = dragOffset,
                                        targetValue = 0f,
                                        animationSpec = tween(
                                            durationMillis = 250,
                                            easing = emphasizedAccelerate,
                                        ),
                                    ) { value, _ -> dragOffset = value }
                                }
                            }
                            SwipeActionKind.Delete -> {
                                onSwipeDelete()
                                swipeLocked = null
                            }
                        }
                    },
                    modifier = Modifier
                        // V_NOW (2026-06-11, swipe v6):
                        // anchor the button by using
                        // [fillMaxSize] + [wrapContentSize]
                        // rather than [BoxScope.align]. In
                        // compose-bom 1.7+ the
                        // [BoxScope.align] overload
                        // resolution prefers the
                        // [Alignment.Horizontal]-typed
                        // 1-D variant, which would reject
                        // the 2-D [Alignment] constants
                        // like [Alignment.Center] or
                        // [Alignment.TopCenter] we want
                        // here. [wrapContentSize] (a
                        // general [Modifier] function,
                        // not a [BoxScope] extension)
                        // takes the 2-D [Alignment]
                        // directly and centers the
                        // button in the parent Box.
                        // See [androidx.compose.foundation.layout.wrapContentSize].
                        //
                        // V_NOW (2026-06-11, swipe v6
                        // position fix): the
                        // [graphicsLayer.translationX]
                        // below is in pixels measured
                        // from the [wrapContentSize]-
                        // anchored position (the tile
                        // center). To place the button
                        // center on the revealed-strip
                        // midpoint we need
                        //   translationX = tile_w/2
                        //     + dragOffset/2
                        // because the strip midpoint
                        // (left-swipe) is at
                        //   tile_w + dragOffset/2
                        // and the anchor is at tile_w/2,
                        // so the delta is tile_w/2 +
                        // dragOffset/2. The tile
                        // half-width comes from
                        // [tileWidthPx] (see below);
                        // the previous attempts that
                        // used only the button
                        // half-width (no tile term)
                        // or [Alignment.CenterEnd]
                        // (which the compose-bom 1.7+
                        // align overload rejects) ended
                        // up placing the button ~button_w
                        // away from the strip midpoint.
                        .fillMaxSize()
                        .wrapContentSize(Alignment.Center)
                        .graphicsLayer {
                            // Net effect: button center
                            // lands on the revealed-strip
                            // midpoint, which is at
                            //   tile_w + dragOffset/2 (left)
                            //   dragOffset/2            (right)
                            // measured from the outer
                            // Box's 0..tile_w origin.
                            // Anchor is at tile_w/2 (from
                            // [wrapContentSize(Center)]),
                            // so the offset from anchor to
                            // target is
                            //   target - tile_w/2
                            //   = tile_w/2 + dragOffset/2
                            // for left-swipe, and
                            //   dragOffset/2 - tile_w/2
                            // for right-swipe (negative,
                            // pulling the button to the
                            // left of the tile center).
                            // The factor is direction-
                            // independent because the
                            // [goingLeft] condition flips
                            // the sign of the whole term.
                            // V_NOW (2026-06-11, swipe v6
                            // position fix #3): the prior
                            // attempt used [size.width] here
                            // (the GraphicsLayerScope's
                            // measured size of the wrapper
                            // Box, which collapses to the
                            // button's own size after
                            // [wrapContentSize] resolves —
                            // NOT the tile width). That
                            // landed the button at
                            // button_w/2 offset from the
                            // tile center, i.e. ~button_w
                            // away from the strip midpoint.
                            //
                            // We now read [tileWidthPx] (a
                            // state in the outer
                            // [RecentFileTile] body, set
                            // by [Modifier.onSizeChanged]
                            // on the thumbnail Box). The
                            // snapshot system makes the
                            // state value visible from
                            // inside the nested
                            // [graphicsLayer] lambda
                            // without needing the
                            // outer-lambda val to be in
                            // direct lexical scope (the
                            // compose-bom 1.7+ receiver
                            // shadowing chain stops one
                            // level deep for captured
                            // outer vals).
                            val tileHalf = tileWidthPx / 2f
                            translationX = if (goingLeft) {
                                tileHalf + dragOffset / 2f
                            } else {
                                dragOffset / 2f - tileHalf
                            }
                        },
                )
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
                        if (multiSelectMode) onTap() else showRenameDialog = true
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
        GlassAlertDialog(
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
 * V_NOW (2026-06-11, swipe v6 — progressive reveal):
 * The action button that gets revealed under a swiping
 * recent-files tile. Anchored to one side of the tile
 * (start for pin, end for delete); the parent passes a
 * normalized [progress] in `0f..1f` mapped from the
 * absolute drag distance between [SwipeDimens.visualRevealStart]
 * and [SwipeDimens.commitThreshold].
 *
 * Geometry interpolates linearly for size and with an
 * ease-out curve for corner radius (the corner "rounds
 * out" faster, giving the visual feel of a chip → pill
 * → circle without overshooting).
 *
 * The [locked] flag freezes the button at its expanded
 * size, raises elevation, and reveals the trailing text
 * label ("删除" / "置顶"). Locking is what the parent
 * does in the "the user has decided" gap between
 * commit-threshold reached and 3-second auto-dismiss —
 * see the [RecentFileTile] drag-stop handler.
 *
 * M3 color usage:
 *   - Delete: [MaterialTheme.colorScheme.errorContainer]
 *     paired with [MaterialTheme.colorScheme.onErrorContainer]
 *     for icon and label. Plain [colorScheme.error] is
 *     reserved for text-grade destructive emphasis;
 *     using it as a large background is too aggressive.
 *   - Pin: [MaterialTheme.colorScheme.secondaryContainer]
 *     paired with [colorScheme.onSecondaryContainer].
 *     Matches the pinned corner badge in
 *     [RecentFileTile] (the small
 *     [Icons.Rounded.PushPin] chip in the top-left
 *     of a pinned tile) so the swipe-action button
 *     and the post-action badge read as the same
 *     affordance in two different states ("intent"
 *     vs "applied"). The user explicitly asked for
 *     this match (2026-06-11) — the gesture's
 *     "pin" affordance should be visually identical
 *     to the post-pin badge.
 */
@Composable
private fun SwipeActionButton(
    kind: SwipeActionKind,
    progress: Float,
    locked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clamped = progress.coerceIn(0f, 1f)
    // Ease-out for the corner: feels like a chip → pill
    // transition. Linear for size — size is the primary
    // "growth" signal and easing it would feel laggy.
    val easeOut = 1f - (1f - clamped) * (1f - clamped)
    val size = lerp(SwipeDimens.buttonSizeCollapsed.value, SwipeDimens.buttonSizeExpanded.value, clamped).dp
    val corner = lerp(
        SwipeDimens.buttonCornerCollapsed.value,
        SwipeDimens.buttonCornerExpanded.value,
        easeOut,
    ).dp
    // Below progress 0.5 the button is barely visible;
    // cap the dim end at 0.5 alpha so the user can still
    // see "something is forming" without it being a
    // confusing flicker.
    val alpha = if (locked) 1f else 0.5f + 0.5f * clamped

    val container = when (kind) {
        SwipeActionKind.Delete -> MaterialTheme.colorScheme.errorContainer
        SwipeActionKind.Pin -> MaterialTheme.colorScheme.secondaryContainer
    }
    val onContainer = when (kind) {
        SwipeActionKind.Delete -> MaterialTheme.colorScheme.onErrorContainer
        SwipeActionKind.Pin -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    val icon = when (kind) {
        SwipeActionKind.Delete -> Icons.Rounded.Delete
        SwipeActionKind.Pin -> Icons.Rounded.PushPin
    }
    val label = when (kind) {
        SwipeActionKind.Delete -> "删除"
        SwipeActionKind.Pin -> "置顶"
    }

    Surface(
        color = container.copy(alpha = alpha),
        contentColor = onContainer,
        shape = RoundedCornerShape(corner),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier.size(size),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .clickable(enabled = locked, onClick = onClick)
                .padding(horizontal = if (locked) 12.dp else 0.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier
                    .size(if (locked) 20.dp else 16.dp)
                    .then(
                        if (kind == SwipeActionKind.Pin) {
                            // PushPin icon's visual center is
                            // above its geometric center (the
                            // pin head is heavy, the tip is
                            // light); shift down 1 dp to
                            // optically center it in the
                            // circular button.
                            Modifier.offset(y = 1.dp)
                        } else {
                            Modifier
                        }
                    ),
            )
            // V_NOW (2026-06-11): the label fades in with
            // the lock transition rather than scaling with
            // progress, to avoid the "growing text" jitter
            // that labels trigger at small tile sizes.
            if (locked) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
        }
    }
}

private enum class SwipeActionKind { Delete, Pin }

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
