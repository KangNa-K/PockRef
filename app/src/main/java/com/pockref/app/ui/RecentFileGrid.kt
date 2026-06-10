@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.pockref.app.ui

import android.net.Uri
import com.pockref.app.data.PurSceneCodec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

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
    // V_NOW (2026-06-10, v1.1 swipe-to-pin, v2):
    // the v1 implementation used M3
    // [SwipeToDismissBox] but the user reported
    // the gesture was "很难触发" (hard to trigger)
    // on real-device first test. Three root causes
    // were identified:
    //   1. 30% positional threshold was too high
    //      — on a 5-column landscape tablet
    //      (560dp / tile) the threshold is 168dp,
    //      a very long swipe.
    //   2. [SwipeToDismissBox]'s internal
    //      pointerInput competed with the
    //      thumbnail's [combinedClickable] (which
    //      has a long-press timer for "enter
    //      multi-select"). Compose's cooperative
    //      gesture model let the long-press timer
    //      win in some races, cancelling the
    //      horizontal drag.
    //   3. The drag's default slop-detection
    //      cooperated with [LazyVerticalGrid]'s
    //      vertical scroll, so a near-horizontal
    //      drag (e.g. 70° / 20°) could be claimed
    //      as a vertical scroll instead.
    //
    // The v2 implementation below replaces
    // [SwipeToDismissBox] with a hand-rolled
    // [awaitEachGesture] loop that:
    //   - Uses a FIXED 56dp commit threshold
    //     (M3 [MinTouchTarget] 48dp + 8dp buffer)
    //     regardless of tile width — same
    //     "swipe distance" feel on phone, compact
    //     tablet, and 5-col landscape.
    //   - Consumes the pointer events aggressively
    //     on the FIRST horizontal move past
    //     [viewConfiguration.touchSlop], so neither
    //     the [LazyVerticalGrid]'s vertical scroll
    //     nor the [combinedClickable]'s long-press
    //     timer can claim the gesture after the
    //     drag has been identified as horizontal.
    //   - Drives the foreground translation
    //     directly via [graphicsLayer] + an
    //     [Animatable] (no `SwipeToDismissBoxState`
    //     to fight with) — the spring-back is a
    //     single [Animatable.animateTo] call.
    //
    // The background reveal (pin icon + "置顶"
    // label on primaryContainer) is rendered as
    // a SIBLING of the moving foreground inside
    // an outer [Box], so the background stays
    // pinned at its position while the foreground
    // slides over it. This is the same z-order
    // trick [SwipeToDismissBox] uses internally,
    // just unrolled so we control every detail.
    val density = LocalDensity.current
    val commitThresholdPx = with(density) { 56.dp.toPx() }
    val maxDragPx = with(density) { 96.dp.toPx() }
    // V_NOW (v1.1 swipe-to-pin v3): [mutableFloatStateOf]
    // for [dragOffset] — the v2 implementation used
    // an [Animatable] and called
    // [Animatable.snapTo] inside a [scope.launch] on
    // every pointer-move event. With a 60Hz pointer
    // stream that meant dozens of coroutines all
    // racing to update the same [Animatable]; the
    // coroutine scheduler could deliver the updates
    // out of order, leaving [dragOffset.value]
    // lagging behind the actual finger position by
    // 1-2 frames. The visible symptom was a "stutter"
    // feel during the drag, and on some Compose
    // versions the [Animatable] would refuse to
    // commit (the gesture detector saw the finger
    // move past the threshold but the visual offset
    // hadn't caught up, so the release-vs-commit
    // decision looked like "no drag happened"). A
    // plain [mutableFloatStateOf] updates SYNCHRONOUSLY
    // on every assignment — no coroutine queueing,
    // no scheduler delays, the visual offset always
    // matches the finger position in the same frame.
    // The [animate] call (separate from
    // [mutableFloatStateOf]) is used ONLY for the
    // spring-back on release, when we explicitly
    // want a 220ms eased animation.
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var bitmap by remember(entry.uri, entry.coverImageId) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(entry.uri, entry.coverImageId) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                // V_NOW (2026-06-10 user spec "启动页封面的显示规则"):
                // .pur is a custom binary format — BitmapFactory can't
                // decode it. We must parse via PurSceneCodec and use
                // SceneImageDecoder to extract the thumbnail.
                val tempFile = java.io.File.createTempFile("pockref_cover_", ".pur", context.cacheDir)
                try {
                    context.contentResolver.openInputStream(Uri.parse(entry.uri))?.use { input ->
                        tempFile.outputStream().use { out -> input.copyTo(out) }
                    }
                    val codec = PurSceneCodec()
                    val scene = codec.readFromFile(tempFile)
                    // V_NOW (2026-06-11): delegate the
                    // cover-vs-default three-way branch to
                    // [com.pockref.app.model.pickCoverTarget]
                    // so the picker is unit-testable on the
                    // JVM. The semantics are identical to the
                    // prior inline if/else — see
                    // [pickCoverTarget] for the full branch
                    // description.
                    val targetImage = pickCoverTarget(
                        images = scene.images,
                        coverImageId = entry.coverImageId,
                    )
                    if (targetImage != null) {
                        // readFromFile already calls setSourceFile for
                        // desktop .pur; for custom format (imageBytes
                        // inline) no sourceFile is needed. Re-set here
                        // to ensure the RAF is open for offset-based
                        // desktop images.
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
            // V_NOW (2026-06-10, user spec "浅色主题的
            // 选定框和勾选框改成和深色主题一样"):
            // selected tiles get a dark translucent
            // overlay so the cyan border + checkbox
            // are equally legible on light and dark
            // thumbnails. Without this, on a pale
            // reference-art thumbnail the dark
            // "primary" cyan border (#005E7A) blends
            // into a light gray image and the
            // selection reads as "very faint outline
            // you have to squint at". The dark
            // scrim (15% black) is heavy enough to
            // read as "this is highlighted" on
            // any thumbnail background but light
            // enough to keep the underlying image
            // visible — the same role Google Photos
            // uses for its select-mode overlay.
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
        // V_NOW (v1.1 swipe-to-pin v2): the
        // outer [Box] is the gesture root — both
        // the pin-reveal background AND the moving
        // foreground (thumbnail) live inside it.
        // The outer Box itself does NOT move; only
        // the inner foreground does, via
        // [graphicsLayer] translationX driven by
        // the [Animatable] `dragOffset` above.
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
            // 1. Background reveal layers — rendered FIRST
            //    so the foreground paints over them.
            //    Only visible when |dragOffset| > 0.5f.
            //    Left-to-right (dragOffset > 0) = pin.
            //    Right-to-left (dragOffset < 0) = delete.
            if (dragOffset > 0.5f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Row(
                        modifier = Modifier.padding(start = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PushPin,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp),
                        )
                        Text(
                            text = "置顶",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                        )
                    }
                }
            } else if (dragOffset < -0.5f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.errorContainer),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Row(
                        modifier = Modifier.padding(end = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "删除",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                        )
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
            // 2. Foreground (the actual thumbnail) —
            //    receives gestures ([combinedClickable]
            //    for tap + long-press, [pointerInput]
            //    for horizontal drag) and moves right
            //    via [graphicsLayer] translationX.
            //    The [graphicsLayer] block is LAST
            //    in the modifier chain so it applies
            //    to everything (clip / background /
            //    border / clickable / pointerInput).
            Box(
                modifier = tileModifier
                    .pointerInput(entry.uri, multiSelectMode) {
                        // V_NOW (v1.1 swipe-to-pin v3): the
                        // foreground thumbnail carries
                        // a SINGLE [pointerInput] that
                        // handles ALL three idle-mode
                        // gestures (tap / long-press /
                        // swipe) — no [combinedClickable]
                        // and no [detectTapGestures] —
                        // because every Compose gesture
                        // detector we tried to chain
                        // competed with the others under
                        // [LazyVerticalGrid]'s vertical
                        // scroll pointerInput, and the
                        // race outcome was that the
                        // horizontal drag lost ~80% of
                        // the time. By handling every
                        // gesture class in one
                        // [awaitEachGesture] loop there
                        // is NO gesture arbitration —
                        // the loop itself decides whether
                        // a down-then-release-without-
                        // movement is a tap, a
                        // down-then-hold is a long-press,
                        // or a down-then-horizontal-move
                        // is a swipe.
                        //
                        // Multi-select mode uses a
                        // SEPARATE branch (the early
                        // return) that reuses Compose's
                        // built-in [detectTapGestures] for
                        // tap-to-toggle-selection. In
                        // multi-select we don't need
                        // long-press or swipe at all (the
                        // bottom action bar owns those
                        // actions), so a simple
                        // [detectTapGestures] is enough
                        // and avoids re-implementing tap
                        // here.
                        if (multiSelectMode) {
                            detectTapGestures(
                                onTap = { onTap() },
                                // onLongPress is intentionally
                                // omitted in multi-select
                                // — long-press is a no-op
                                // (the user is already in
                                // multi-select, pressing
                                // longer doesn't change
                                // anything).
                            )
                            return@pointerInput
                        }
                        // Idle mode: tap + long-press +
                        // horizontal swipe, all in one
                        // [awaitEachGesture] loop.
                        val touchSlop = viewConfiguration.touchSlop
                        val longPressTimeoutMs = viewConfiguration.longPressTimeoutMillis
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var isHorizontalDrag = false
                            var didLongPress = false
                            var didDispatch = false
                            // Long-press timer — launched
                            // on every gesture, cancelled
                            // the moment the gesture is
                            // identified as a swipe or
                            // released before timeout.
                            val longPressJob = scope.launch {
                                delay(longPressTimeoutMs)
                                if (!isHorizontalDrag) {
                                    didLongPress = true
                                    didDispatch = true
                                    onLongPress()
                                }
                            }
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes
                                        .firstOrNull { it.id == down.id }
                                        ?: break
                                    if (!change.pressed) {
                                        // Pointer up — commit
                                        // decision time.
                                        if (isHorizontalDrag) {
                                            if (dragOffset > commitThresholdPx) {
                                                onSwipePin()
                                            } else if (dragOffset < -commitThresholdPx) {
                                                onSwipeDelete()
                                            }
                                            scope.launch {
                                                animate(
                                                    initialValue = dragOffset,
                                                    targetValue = 0f,
                                                    animationSpec = tween(
                                                        durationMillis = 220,
                                                        easing = FastOutSlowInEasing,
                                                    ),
                                                ) { value, _ ->
                                                    dragOffset = value
                                                }
                                            }
                                        } else if (!didDispatch) {
                                            // No movement, no long-press
                                            // fired — must be a tap.
                                            onTap()
                                        }
                                        break
                                    }
                                    if (!isHorizontalDrag) {
                                        val dx = change.position.x - down.position.x
                                        val dy = change.position.y - down.position.y
                                        if (abs(dx) > touchSlop &&
                                            abs(dx) > abs(dy)
                                        ) {
                                            isHorizontalDrag = true
                                            // Cancel the long-press
                                            // timer — the user is
                                            // swiping, not holding.
                                            longPressJob.cancel()
                                            // Consume the down
                                            // event so the parent
                                            // [LazyVerticalGrid]
                                            // doesn't see it as
                                            // the start of a
                                            // vertical scroll.
                                            down.consume()
                                        } else if (abs(dx) > touchSlop ||
                                            abs(dy) > touchSlop
                                        ) {
                                            // Movement but NOT
                                            // horizontal — the
                                            // user is dragging
                                            // diagonally. Cancel
                                            // the long-press
                                            // (it's not a hold),
                                            // but DON'T claim the
                                            // gesture (let the
                                            // grid scroll).
                                            longPressJob.cancel()
                                        }
                                    }
                                    if (isHorizontalDrag) {
                                        // We own the gesture —
                                        // consume the change so
                                        // no other detector
                                        // (the grid's scroll,
                                        // the long-press timer
                                        // cancellation handler)
                                        // can react.
                                        change.consume()
                                        val newOffset = (
                                            dragOffset + change.positionChange().x
                                            ).coerceIn(-maxDragPx, maxDragPx)
                                        dragOffset = newOffset
                                    }
                                }
                            } finally {
                                longPressJob.cancel()
                            }
                        }
                    }
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
                // V_NOW (2026-06-10, user spec "浅色主题的
                // 选定框和勾选框改成和深色主题一样"):
                // the checkbox now sits on a 28dp
                // semi-opaque white circle (the same
                // "always-legible" backing used in
                // Google Photos select mode). The
                // backing is drawn with [Color.White]
                // at 85% alpha so it adapts to both
                // light and dark themes — the user
                // sees a soft "pin" in the top-end
                // corner that's readable on any
                // thumbnail, including a black
                // reference image (where an unbacked
                // white checkbox would vanish). The
                // [Icon] sits centered inside the
                // 28dp backing (24dp icon), giving
                // 2dp breathing room.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(28.dp)
                        .clip(CircleShape)
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
                        modifier = Modifier.size(24.dp),
                    )
                }
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
            // V_NOW (2026-06-11, user bug report "重新打开
            // 文件后封面图没有'已设为封面'的标识"):
            // cover-image badge on TopEnd. Mirrors the
            // single-select sheet's "设为封面" button
            // (PureRefApp.kt — same [Icons.Rounded.Star]
            // icon), so the visual language is consistent
            // across the start page and the in-canvas
            // sheet. M3 [tertiaryContainer] /
            // [onTertiaryContainer] — distinct from the
            // brand teal [primary] (multi-select
            // checkbox) and the soft lavender
            // [secondaryContainer] (pinned badge) so the
            // three corner indicators don't fight for
            // visual weight.
            //
            // 24dp circle / 16dp icon matches the
            // pinned-badge geometry for symmetric corner
            // pins. M3 role: "tertiary state, persistent
            // indicator" (same role as the chip family
            // and progress meters).
            //
            // Mutex with the TopEnd multi-select
            // checkbox — the checkbox only appears in
            // [multiSelectMode], and the star badge only
            // appears in idle mode, so the two never
            // overlap on the same tile. The thumbnail's
            // actual cover content is already driven by
            // [entry.coverImageId] in the [LaunchedEffect]
            // above; this badge is purely the "I am the
            // cover" affordance the user noticed was
            // missing.
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
