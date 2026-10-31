package com.pockref.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewModelScope
import com.pockref.app.model.AngleSnapGuide
import com.pockref.app.model.CanvasMarkupStroke
import com.pockref.app.model.CanvasMarkupText
import com.pockref.app.model.ColorAdjust
import com.pockref.app.model.SelectionFlash
import com.pockref.app.model.PockRefUiState
import com.pockref.app.model.SceneImage
import com.pockref.app.model.SceneGroup
import com.pockref.app.model.Size2D
import com.pockref.app.model.SpeedSample
import com.pockref.app.model.groupContaining
import com.pockref.app.model.isImageLocked
import com.pockref.app.model.selectedGroups
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Metadata-only snapshot of a scene at a point in time. The image list is
 * stored separately in [HistoryEntry.Snapshot] so we don't pin a 1000-image
 * list every time a tiny property change is recorded.
 *
 * The undo/redo stacks no longer store [SceneSnapshot] directly — they store
 * [HistoryEntry] which can be either a [HistoryEntry.Snapshot] (used as a
 * checkpoint base for diffs) or a [HistoryEntry.Diff] (an incremental change).
 */
internal data class SceneSnapshot(
    val sceneName: String,
    val backgroundColor: Color,
    val groups: List<SceneGroup>,
)

/**
 * One incremental change in the event log. Each variant carries enough
 * information to be reversed without consulting a separate checkpoint: the
 * reverse of a [UpdateImage] swaps every `old*`/`new*` field, the reverse
 * of [AddImage] is a [RemoveImage] of the same image, and so on.
 *
 * Field-level [UpdateImage] entries are used for the common case
 * (rotate / scale / move a single image) so the per-entry footprint stays
 * tiny. [ReplaceImages] is the fallback used when a precise per-field diff
 * isn't worth computing (e.g. multi-image reorders or bulk import follow-ups);
 * it still carries [oldImages] so it remains reversible on its own.
 */
internal sealed interface SceneDiff {
    /**
     * Whole image list was replaced. We keep BOTH [oldImages] and [newImages]
     * so reversing this entry is a self-contained swap — no need to consult
     * the current state to recover the old list.
     */
    data class ReplaceImages(
        val oldImages: List<SceneImage>,
        val newImages: List<SceneImage>,
    ) : SceneDiff

    data class ReplaceGroups(
        val oldGroups: List<SceneGroup>,
        val newGroups: List<SceneGroup>,
    ) : SceneDiff

    /**
     * Property-level change of a single image. Only fields that actually
     * changed are populated on each side; `null` means "leave this field
     * alone" so the same data class works for arbitrary subsets of fields.
     * [oldX] / [newX] mirror each other: the reverse swaps them.
     */
    data class UpdateImage(
        val id: String,
        val oldCenter: Offset? = null,
        val oldDisplaySize: Size2D? = null,
        val oldRotation: Float? = null,
        val oldScale: Float? = null,
        val oldFlipX: Boolean? = null,
        val oldFlipY: Boolean? = null,
        val oldColorAdjust: ColorAdjust? = null,
        val newCenter: Offset? = null,
        val newDisplaySize: Size2D? = null,
        val newRotation: Float? = null,
        val newScale: Float? = null,
        val newFlipX: Boolean? = null,
        val newFlipY: Boolean? = null,
        val newColorAdjust: ColorAdjust? = null,
    ) : SceneDiff

    /** A new image appeared in the scene. */
    data class AddImage(val image: SceneImage) : SceneDiff

    /**
     * An image was removed. We keep the full [SceneImage] (not just the id)
     * so the reverse — re-adding it with the same field values — is possible
     * without going back to a snapshot.
     */
    data class RemoveImage(val image: SceneImage) : SceneDiff

    /**
     * Scene-level metadata changed. Both old and new values are stored for
     * the fields that actually changed (`null` on a side means "this field
     * was not part of the change"). The reverse swaps them.
     */
    data class SetMeta(
        val oldName: String? = null,
        val newName: String? = null,
        val oldBackgroundColor: Color? = null,
        val newBackgroundColor: Color? = null,
    ) : SceneDiff

    data class ReplaceMarkup(
        val oldStrokes: List<CanvasMarkupStroke>,
        val newStrokes: List<CanvasMarkupStroke>,
        val oldTexts: List<CanvasMarkupText>,
        val newTexts: List<CanvasMarkupText>,
    ) : SceneDiff

    /**
     * Selection state changed (e.g. deselecting all images by
     * tapping blank canvas). Carries both old and new selection
     * so the reverse is self-contained.
     */
    data class ChangeSelection(
        val oldSelectedImageId: String?,
        val oldSelectedImageIds: Set<String>,
        val oldMultiSelectMode: Boolean,
        val newSelectedImageId: String?,
        val newSelectedImageIds: Set<String>,
        val newMultiSelectMode: Boolean,
    ) : SceneDiff
}

/**
 * One entry in the undo / redo stacks. A [Snapshot] is a full checkpoint
 * (rare, used as a base for the diffs in between); a [Diff] is a small
 * incremental change that can be applied forward or reversed cheaply.
 */
internal sealed interface HistoryEntry {
    data class Snapshot(
        val sceneName: String,
        val backgroundColor: Color,
        val images: List<SceneImage>,
        val groups: List<SceneGroup>,
        val markupStrokes: List<CanvasMarkupStroke>,
        val markupTexts: List<CanvasMarkupText>,
        val markupSourceMetadata: String? = null,
        val markupCompatibilityWarning: String? = null,
    ) : HistoryEntry

    data class Diff(val diff: SceneDiff) : HistoryEntry
}

internal const val RECENT_SCENES_KEY = "recent_scenes_v1"

/**
 * Every Nth entry we emit a [HistoryEntry.Snapshot] instead of a [HistoryEntry.Diff]
 * so a long undo chain doesn't have to replay from the dawn of time. Diffs in
 * between are applied incrementally against the current state.
 */
internal const val SCENE_SNAPSHOT_INTERVAL = 5

/**
 * Snap trigger distance in screen pixels. The dragged group's edge becomes
 * "active" for snap once it is within this many pixels (converted to world
 * units via current canvasScale) of any other image's edge.
 *
 * Picked at 15 to match the user spec and to feel "tight" — a value much
 * larger than that and snap starts feeling like the canvas is fighting
 * the user; a value much smaller and snap becomes hard to find. The
 * engine measures distance as the absolute delta between two edge
 * positions on the SAME axis (no diagonal cases).
 */
private const val SNAP_THRESHOLD_PX = 15f

/**
 * How long (ms) the post-commit snap highlight is visible. Fades out over
 * this window. The renderer animates an alpha from 0.6 → 0 over this
 * duration.
 */
internal const val SNAP_HIGHLIGHT_DURATION_MS = 350L

// ---------------------------------------------------------------------------
// Intent-detection state machine thresholds (V4).
//
// Snap used to be "trigger the moment the cursor is within range". In dense
// scenes with many small, irregular gaps, that fires constantly while the
// user is just passing through. The V4 state machine gates snap on
// INTENT: the user must slow down, dwell for a moment, AND be within
// range. Symmetrically, a fast subsequent move cancels the snap
// immediately rather than waiting for the cursor to drift past 15px again.
//
// All values are in *screen* pixels / ms for the speed and time gates.
// They're not scaled with canvasScale — the intent reading is a
// perceptual, finger-on-glass thing, not a world-space thing.
// ---------------------------------------------------------------------------

/**
 * Time (ms) the cursor must stay within the trigger range AND below
 * the engage speed threshold before snap fires. 200ms is the user
 * spec; it matches Figma's "settle" feel and is long enough that a
 * fast pass-through doesn't trigger, short enough that an explicit
 * pause feels responsive.
 */
private const val SNAP_ENGAGE_DELAY_MS = 200L

/**
 * Maximum cursor speed (screen px/s) during the TRACKING window for
 * snap to engage. Below this, the user is clearly "settling" on a
 * target. 50 px/s is slow on a tablet (~20cm/sec fingertip) — slow
 * enough to feel intentional.
 */
private const val SNAP_ENGAGE_MAX_SPEED_PX_PER_S = 50f

/**
 * Cursor speed (screen px/s) at which we conclude the user has
 * "resumed moving" and cancel the active snap. Higher than the engage
 * threshold (hysteresis): once locked on, the user must really MOVE
 * to break out — small trembles don't disengage.
 */
private const val SNAP_DISENGAGE_SPEED_PX_PER_S = 100f

/**
 * Minimum move distance (screen px) for the disengage-speed check to
 * count. Prevents the disengage from firing on a single high-spike
 * sample at the end of a stationary cursor.
 */
private const val SNAP_DISENGAGE_MIN_MOVE_PX = 5f

/**
 * Distance (screen px) past the trigger range the user must move
 * before we cancel snap (主动移出). Larger than the trigger range so
 * tiny "graze" movements don't disengage.
 */
private const val SNAP_CANCEL_DISTANCE_PX = 30f

/**
 * Width (ms) of the sliding window used to compute the smoothed drag
 * speed. 100ms is short enough to feel responsive to a sudden burst,
 * long enough to swallow single-frame jitter from the touch sampler.
 */
private const val SNAP_SPEED_WINDOW_MS = 100L

// ---------------------------------------------------------------------------
// Rotation angle-snap (0° / 45° / 90° / 135° / 180° / 225° / 270° / 315°
// lock, with 200ms pause + 10° continue).
//
// The transform layer runs rotation through the same gesture path as
// scale + pan (TransformSelectedImage). When the user brings the
// candidate rotation within [ROTATION_SNAP_ACTIVATION_THRESHOLD_DEG]
// of any 8-angle, the engine starts a pause timer. After
// [ROTATION_SNAP_DEBOUNCE_MS] of continuous in-zone rotation, the
// rotation locks to the nearest 8-angle and an AngleSnapGuide is
// published so the renderer can draw a small "snapped" indicator. The
// snap is held until the user rotates past
// [ROTATION_SNAP_CONTINUE_THRESHOLD_DEG] of the active snap target
// — at which point the lock is released and the image follows the
// gesture freely again (re-entering a zone starts the 200ms pause
// timer fresh).
//
// Distinct from V5 edge-snap: that engine runs on the drag path
// (updateImageDrag) and gates on cursor proximity + speed. Rotation
// snap is continuous, deterministic (pure-function
// `computeRotationSnapDecision`), and per-frame — the only stateful
// piece is the pending 200ms debounce coroutine, which is cancelled
// when the gesture leaves the zone or another snap fires.
// ---------------------------------------------------------------------------

/**
 * Re-export the spec constants from [TransformHelpers] so the rest of
 * this file (and the `internal` visibility scope) can reference them
 * without going through the helpers. Values are defined in
 * [TransformHelpers] so the pure decision function
 * [computeRotationSnapDecision] can use them as default arguments.
 *
 * (The constants previously lived in this file as `private const val
 * ROTATION_SNAP_DEBOUNCE_MS = 150L` and were used directly by the
 * state machine. The 4-angle, no-pause snap is gone in V2 of the
 * engine; the V2 state machine lives in [computeRotationSnapDecision]
 * and the coroutine variant in [scheduleRotationSnapEngage].)
 */
internal const val ROTATION_SNAP_DEBOUNCE_MS_LOCAL = ROTATION_SNAP_DEBOUNCE_MS

internal fun PockRefUiState.normalizeSelection(): PockRefUiState {
    val validIds = images.map { it.id }.toSet()
    val ownedIds = mutableSetOf<String>()
    val normalizedGroups = groups.mapNotNull { group ->
        val members = group.imageIds.filter { it in validIds && ownedIds.add(it) }.distinct()
        group.copy(
            imageIds = members,
            backgroundOpacity = group.backgroundOpacity.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f,
        ).takeIf { members.isNotEmpty() }
    }
    val normalizedEditingGroupId = editingGroupId?.takeIf { id ->
        normalizedGroups.any { it.id == id && !it.positionLocked }
    }
    val validSelectedGroupIds = selectedGroupIds.filterTo(linkedSetOf()) { id ->
        normalizedGroups.any { it.id == id }
    }
    val keptSelection = selectedImageIds.filterTo(linkedSetOf()) { it in validIds }
    val primary = when {
        selectedImageId in validIds -> selectedImageId
        keptSelection.isNotEmpty() -> keptSelection.last()
        else -> null
    }
    var normalizedIds = when {
        primary == null -> emptySet()
        keptSelection.isEmpty() -> setOf(primary)
        else -> keptSelection
    }
    if (normalizedEditingGroupId != null) {
        val members = normalizedGroups.first { it.id == normalizedEditingGroupId }.imageIds.toSet()
        normalizedIds = normalizedIds.intersect(members)
    } else {
        val explicitGroups = normalizedGroups.filter { it.id in validSelectedGroupIds }
        if (explicitGroups.isNotEmpty()) {
            explicitGroups.forEach { group -> normalizedIds = normalizedIds + group.imageIds }
        } else {
            normalizedGroups.forEach { group ->
                if (group.imageIds.any { it in normalizedIds }) normalizedIds = normalizedIds + group.imageIds
            }
        }
    }
    val normalizedSelectedGroupIds = if (normalizedEditingGroupId != null) {
        emptySet()
    } else if (validSelectedGroupIds.isNotEmpty()) {
        validSelectedGroupIds
    } else {
        normalizedGroups.filter { group ->
            group.imageIds.isNotEmpty() && group.imageIds.all { it in normalizedIds }
        }.mapTo(linkedSetOf()) { it.id }
    }
    val normalizedPrimary = when {
        primary != null && primary in normalizedIds -> primary
        else -> normalizedIds.lastOrNull()
    }
    return copy(
        groups = normalizedGroups,
        selectedGroupIds = normalizedSelectedGroupIds,
        editingGroupId = normalizedEditingGroupId,
        selectedImageId = normalizedPrimary,
        selectedImageIds = normalizedIds,
    ).clearPosterizeIfSelectionChanged(normalizedIds)
}

internal fun PockRefUiState.syncHistoryFlags(undoStackSize: Int, redoStackSize: Int): PockRefUiState =
    copy(canUndo = undoStackSize > 0, canRedo = redoStackSize > 0)

/** Capture the full current scene as a snapshot entry. */
internal fun PockRefUiState.toHistoryEntry(): HistoryEntry.Snapshot =
    HistoryEntry.Snapshot(
        sceneName = sceneName,
        backgroundColor = backgroundColor,
        images = images,
        groups = groups,
        markupStrokes = markupStrokes,
        markupTexts = markupTexts,
        markupSourceMetadata = markupSourceMetadata,
        markupCompatibilityWarning = markupCompatibilityWarning,
    )

/**
 * Build a history entry for the transition [oldState] -> [newState].
 *
 * Strategy:
 * 1. Try to produce a small set of [SceneDiff] entries (one per change).
 * 2. If the resulting diff is "small enough" (at most a couple of image
 *    additions/removals plus a handful of property updates), wrap it in a
 *    [HistoryEntry.Diff] (or several — currently collapsed to one Diff
 *    entry per call for stack simplicity).
 * 3. Otherwise, fall back to a single [SceneDiff.ReplaceImages] so the
 *    reverse still has the full new image list to swap with the current
 *    one. (Task spec allows this simplification.)
 *
 * Every [SCENE_SNAPSHOT_INTERVAL] entries, we promote the entry to a
 * [HistoryEntry.Snapshot] regardless of size — that's our checkpoint. The
 * snapshot captures the **before** state, so undoing back to it (which
 * just applies the snapshot) actually restores the state at the time the
 * checkpoint was recorded.
 */
internal fun buildHistoryEntry(
    oldState: PockRefUiState,
    newState: PockRefUiState,
    stackSize: Int,
): HistoryEntry {
    // Always take a checkpoint at the configured interval so undo chains
    // don't need to replay dozens of diffs to recover the oldest state.
    val needsCheckpoint = (stackSize % SCENE_SNAPSHOT_INTERVAL) == 0
    if (needsCheckpoint) return oldState.toHistoryEntry()
    val markupChanged = oldState.markupStrokes != newState.markupStrokes ||
        oldState.markupTexts != newState.markupTexts
    if (markupChanged) {
        val onlyMarkupChanged = oldState.sceneName == newState.sceneName &&
            oldState.backgroundColor == newState.backgroundColor &&
            oldState.images == newState.images &&
            oldState.groups == newState.groups
        if (onlyMarkupChanged) {
            return HistoryEntry.Diff(
                SceneDiff.ReplaceMarkup(
                    oldStrokes = oldState.markupStrokes,
                    newStrokes = newState.markupStrokes,
                    oldTexts = oldState.markupTexts,
                    newTexts = newState.markupTexts,
                ),
            )
        }
        return oldState.toHistoryEntry()
    }
    if (oldState.groups != newState.groups) {
        if (oldState.images == newState.images &&
            oldState.sceneName == newState.sceneName &&
            oldState.backgroundColor == newState.backgroundColor) {
            return HistoryEntry.Diff(
                SceneDiff.ReplaceGroups(oldGroups = oldState.groups, newGroups = newState.groups),
            )
        }
        return oldState.toHistoryEntry()
    }
    val diff = computeSceneDiff(oldState, newState)
    return HistoryEntry.Diff(diff)
}

/**
 * Compute a single [SceneDiff] describing the change from [oldState] to
 * [newState]. This is intentionally lossy — we collapse multi-image edits
 * into one [SceneDiff.ReplaceImages] when the field-level diff would be
 * larger than the replacement. Image-byte payloads are NOT part of the diff
 * since the [SceneImage] instances already carry their bytes; only metadata
 * and transform fields are compared.
 */
internal fun computeSceneDiff(oldState: PockRefUiState, newState: PockRefUiState): SceneDiff {
    val oldById = oldState.images.associateBy { it.id }
    val newById = newState.images.associateBy { it.id }

    val added = newState.images.filter { it.id !in oldById }
    val removed = oldState.images.filter { it.id !in newById }
    val common = oldState.images.filter { it.id in newById }

    val churn = added.size + removed.size
    if (churn > 2 || common.size != oldState.images.size - removed.size) {
        // Fallback: record both image lists so the entry stays reversible
        // on its own (no external state lookup needed at undo time).
        return SceneDiff.ReplaceImages(
            oldImages = oldState.images,
            newImages = newState.images,
        )
    }

    // Metadata changes are recorded separately so the field-level UpdateImage
    // entry stays focused on image transforms.
    if (added.isEmpty() && removed.isEmpty() && common.size == oldState.images.size) {
        // Try to find a single-image field update.
        val updatedImages = common.mapNotNull { oldImg ->
            val newImg = newById[oldImg.id]!!
            val update = buildImageUpdate(oldImg, newImg) ?: return@mapNotNull null
            oldImg to update
        }
        if (updatedImages.size == 1) {
            val meta = buildMetaUpdate(oldState, newState)
            if (meta != null) return meta // shouldn't normally happen together, but cover it
            return updatedImages[0].second
        }
        // If the only change is one we didn't track field-by-field (e.g.
        // zIndex in bringToFront / sendToBack), still fall back to a
        // ReplaceImages so the entry is reversible — the caller will skip
        // pushing it as a no-op anyway.
        if (updatedImages.isEmpty() && oldState.images == newState.images) {
            return SceneDiff.ReplaceImages(
                oldImages = oldState.images,
                newImages = newState.images,
            )
        }
    }

    // Multi-image change (or a single-image change we can't represent
    // field-by-field). Encode as ReplaceImages — the reverse simply swaps
    // image lists; metadata changes (if any) are captured by old/new
    // snapshots taken at apply time.
    return SceneDiff.ReplaceImages(
        oldImages = oldState.images,
        newImages = newState.images,
    )
}

/**
 * Build a [SceneDiff.UpdateImage] for a single image if any tracked field
 * changed. Returns `null` when nothing material changed.
 */
private fun buildImageUpdate(oldImg: SceneImage, newImg: SceneImage): SceneDiff.UpdateImage? {
    // V_NOW: pixel-level fields (imageBytes, intrinsicWidth/Height,
    // imageByteOffset/Length, mimeType, name, locked, zIndex) AND the
    // non-destructive cropRect and posterizeLevels are NOT tracked by UpdateImage. If any
    // of them changed (e.g. after a crop operation), the field-level
    // diff would lose those changes on undo — the user would see a
    // stretched image because displaySize reverts but the cropRect
    // stays at the cropped values, so drawSceneImage renders the
    // square crop into the original 16:9 box. Return null so
    // computeSceneDiff falls through to ReplaceImages, which
    // preserves the full SceneImage on both sides.
    if (oldImg.intrinsicWidth != newImg.intrinsicWidth ||
        oldImg.intrinsicHeight != newImg.intrinsicHeight ||
        oldImg.imageByteOffset != newImg.imageByteOffset ||
        oldImg.imageByteLength != newImg.imageByteLength ||
        oldImg.mimeType != newImg.mimeType ||
        oldImg.locked != newImg.locked ||
        oldImg.zIndex != newImg.zIndex ||
        oldImg.cropRect != newImg.cropRect ||
        oldImg.posterizeLevels != newImg.posterizeLevels
    ) {
        return null
    }
    // imageBytes is a ByteArray — compare by content to avoid O(n²)
    // structural equality on every diff, but only when the sizes differ
    // (same size + same intrinsic dims → bytes are almost certainly the
    // same; different size → definitely changed).
    if (oldImg.imageBytes.size != newImg.imageBytes.size &&
        oldImg.imageBytes.isNotEmpty() && newImg.imageBytes.isNotEmpty()
    ) {
        return null
    }

    val oldCenter = if (oldImg.center != newImg.center) oldImg.center else null
    val oldSize = if (oldImg.displaySize != newImg.displaySize) oldImg.displaySize else null
    val oldRot = if (oldImg.rotationDegrees != newImg.rotationDegrees) oldImg.rotationDegrees else null
    val oldScale = if (oldImg.scale != newImg.scale) oldImg.scale else null
    val oldFlipX = if (oldImg.flipX != newImg.flipX) oldImg.flipX else null
    val oldFlipY = if (oldImg.flipY != newImg.flipY) oldImg.flipY else null
    val oldColor = if (oldImg.colorAdjust != newImg.colorAdjust) oldImg.colorAdjust else null

    val newCenter = if (oldCenter != null) newImg.center else null
    val newSize = if (oldSize != null) newImg.displaySize else null
    val newRot = if (oldRot != null) newImg.rotationDegrees else null
    val newScale = if (oldScale != null) newImg.scale else null
    val newFlipX = if (oldFlipX != null) newImg.flipX else null
    val newFlipY = if (oldFlipY != null) newImg.flipY else null
    val newColor = if (oldColor != null) newImg.colorAdjust else null

    if (oldCenter == null && oldSize == null && oldRot == null && oldScale == null &&
        oldFlipX == null && oldFlipY == null && oldColor == null
    ) {
        return null
    }
    return SceneDiff.UpdateImage(
        id = oldImg.id,
        oldCenter = oldCenter,
        oldDisplaySize = oldSize,
        oldRotation = oldRot,
        oldScale = oldScale,
        oldFlipX = oldFlipX,
        oldFlipY = oldFlipY,
        oldColorAdjust = oldColor,
        newCenter = newCenter,
        newDisplaySize = newSize,
        newRotation = newRot,
        newScale = newScale,
        newFlipX = newFlipX,
        newFlipY = newFlipY,
        newColorAdjust = newColor,
    )
}

private fun buildMetaUpdate(oldState: PockRefUiState, newState: PockRefUiState): SceneDiff.SetMeta? {
    val oldName = if (oldState.sceneName != newState.sceneName) oldState.sceneName else null
    val newName = if (oldState.sceneName != newState.sceneName) newState.sceneName else null
    val oldBg = if (oldState.backgroundColor != newState.backgroundColor) oldState.backgroundColor else null
    val newBg = if (oldState.backgroundColor != newState.backgroundColor) newState.backgroundColor else null
    if (newName == null && newBg == null) return null
    return SceneDiff.SetMeta(
        oldName = oldName,
        newName = newName,
        oldBackgroundColor = oldBg,
        newBackgroundColor = newBg,
    )
}

/**
 * Compute the inverse of a [HistoryEntry.Diff] given the state the diff was
 * applied against. Used by [undo] / [redo] to restore a previous state.
 *
 * For [HistoryEntry.Snapshot] entries there's nothing to invert — the
 * snapshot itself IS the target state — so [undo] / [redo] apply it
 * directly. The helper is therefore diff-only.
 */
internal fun invertHistoryEntry(
    entry: HistoryEntry,
    currentState: PockRefUiState,
): HistoryEntry {
    return when (entry) {
        is HistoryEntry.Snapshot -> entry
        is HistoryEntry.Diff -> HistoryEntry.Diff(invertDiff(entry.diff, currentState))
    }
}

/**
 * Compute the inverse of a single [SceneDiff] given the state the diff was
 * applied against. For self-contained diffs ([UpdateImage], [AddImage],
 * [RemoveImage]) the input [appliedState] is not consulted; the diff
 * already carries everything needed to reverse itself. The argument is
 * kept in the signature for symmetry / future use.
 */
@Suppress("UNUSED_PARAMETER")
internal fun invertDiff(diff: SceneDiff, appliedState: PockRefUiState): SceneDiff {
    return when (diff) {
        is SceneDiff.ReplaceImages -> SceneDiff.ReplaceImages(
            oldImages = diff.newImages,
            newImages = diff.oldImages,
        )
        is SceneDiff.ReplaceGroups -> SceneDiff.ReplaceGroups(
            oldGroups = diff.newGroups,
            newGroups = diff.oldGroups,
        )
        is SceneDiff.UpdateImage -> diff.swap()
        is SceneDiff.AddImage -> SceneDiff.RemoveImage(image = diff.image)
        is SceneDiff.RemoveImage -> SceneDiff.AddImage(image = diff.image)
        is SceneDiff.SetMeta -> SceneDiff.SetMeta(
            oldName = diff.newName,
            newName = diff.oldName,
            oldBackgroundColor = diff.newBackgroundColor,
            newBackgroundColor = diff.oldBackgroundColor,
        )
        is SceneDiff.ReplaceMarkup -> SceneDiff.ReplaceMarkup(
            oldStrokes = diff.newStrokes,
            newStrokes = diff.oldStrokes,
            oldTexts = diff.newTexts,
            newTexts = diff.oldTexts,
        )
        is SceneDiff.ChangeSelection -> SceneDiff.ChangeSelection(
            oldSelectedImageId = diff.newSelectedImageId,
            oldSelectedImageIds = diff.newSelectedImageIds,
            oldMultiSelectMode = diff.newMultiSelectMode,
            newSelectedImageId = diff.oldSelectedImageId,
            newSelectedImageIds = diff.oldSelectedImageIds,
            newMultiSelectMode = diff.oldMultiSelectMode,
        )
    }
}

/** Swap the `old*` and `new*` field bundles of an [UpdateImage]. */
private fun SceneDiff.UpdateImage.swap(): SceneDiff.UpdateImage = SceneDiff.UpdateImage(
    id = id,
    oldCenter = newCenter,
    oldDisplaySize = newDisplaySize,
    oldRotation = newRotation,
    oldScale = newScale,
    oldFlipX = newFlipX,
    oldFlipY = newFlipY,
    oldColorAdjust = newColorAdjust,
    newCenter = oldCenter,
    newDisplaySize = oldDisplaySize,
    newRotation = oldRotation,
    newScale = oldScale,
    newFlipX = oldFlipX,
    newFlipY = oldFlipY,
    newColorAdjust = oldColorAdjust,
)

internal fun canvasPointToWorld(
    point: Offset,
    viewportSize: Size,
    canvasOffset: Offset,
    canvasScale: Float,
): Offset = (point - Offset(viewportSize.width / 2f, viewportSize.height / 2f) - canvasOffset) / canvasScale

class PockRefViewModelTransform(private val mainVm: PockRefViewModel) {
    /**
     * Pending 200ms debounce coroutine for rotation-snap engagement.
     * Non-null means a coroutine is armed and waiting to fire the
     * snap. Cancelled and re-armed on every
     * [transformSelectedImage] call that needs it, and explicitly
     * cleared by [beginImageDrag] / [commitImageDrag] /
     * [cancelImageDrag] so a stale debounce never fires mid-drag.
     *
     * Lives on the transform controller (not on the main VM) because
     * it's a rotation-gesture-internal concern: no other consumer
     * needs to know whether a debounce is pending.
     */
    private var rotationSnapEngageJob: Job? = null

    /**
     * Clear all selection state and exit multi-select mode.
     * Tapping blank canvas triggers this — the user's intent is
     * "I'm done with this selection", which includes leaving
     * multi-select mode so the next tap goes back to single-select.
     */
    internal fun deselect() {
        val before = mainVm._uiState.value
        // Only do work if there was an actual selection to lose.
        if (before.selectedImageIds.isEmpty() && before.selectedImageId == null && before.editingGroupId == null) return
        // V_NOW (2026-06-08 "双指撤销后无法继续撤销" 修复):
        // selection is a TRANSIENT user gesture, not a
        // durable scene change. The previous design
        // pushed a `ChangeSelection` diff onto the
        // undo stack so the user could "undo" the
        // deselect (re-selecting the image). The
        // asymmetry with [selectImage] — which does
        // NOT push to undo because frequent taps
        // would flood the 50-entry cap — caused the
        // bug: "deselect → undo" is the ONLY way to
        // reverse a selection change, but that undo
        // CONSUMES one undo slot. After the user
        // undoes the deselect, the undo stack is now
        // one entry shorter, and a confused user
        // (who read the deselect+undo pair as a
        // net-zero "I just undid my select" gesture)
        // reports that they "can't keep undoing".
        //
        // Fix: don't record deselect on the undo
        // stack either. Selection is a transient
        // gesture; the user can re-select the same
        // (or a different) image by tapping it. The
        // undo button is reserved for DURABLE scene
        // changes — import, drag, rotate, crop,
        // delete, color-adjust, etc. — that the
        // user genuinely wants to revert. This
        // mirrors the existing `selectImage`
        // policy and removes the asymmetry that
        // caused the bug.
        //
        // Side effect: the user can no longer
        // "three-finger-tap redo a deselect" to
        // resurrect a lost selection. This is
        // acceptable because:
        //   - the 50-entry undo cap means a long
        //     session could lose the diff anyway
        //   - the 2-finger-tap gesture (the natural
        //     inverse of "tap to select") is the
        //     much more common way to "undo a
        //     selection" — it's a free gesture, no
        //     stack entry needed
        //   - "intentionally re-selecting" was a
        //     discoverability trap anyway; the user
        //     just taps the image
        // We also drop the [redoStack.clear()] for
        // the same reason: selection is transient,
        // a pending redo target that would resurface
        // a previous selection would be confusing
        // (the user moved on).
        mainVm._uiState.update { state ->
            state.copy(
                selectedImageId = null,
                selectedImageIds = emptySet(),
                selectedGroupIds = emptySet(),
                multiSelectMode = false,
                editingGroupId = null,
                bottomRightPanelExpanded = false,
            ).normalizeSelection().syncHistoryFlags(mainVm.undoStackSize, mainVm.redoStackSize)
        }
    }

    internal fun selectImage(imageId: String?) {
        // V_NOW (2026-06-08 "global image lock" feature):
        // when the app-wide global lock is on, refuse
        // to select ANY image — the user's intent in
        // tapping an image under a global lock is
        // ambiguous (it might be a misclick, or the
        // user forgot the lock was on), and the prior
        // behavior ("tapping still selects, just
        // shows the locked panel") was reported by
        // the user as "无法被选中" — they expected the
        // tap to do nothing while the lock is on. This
        // matches the existing per-image locked
        // picture: a locked image also refuses to
        // enter the drag state on down (see the
        // [activeDragImageId] guard), so refusing to
        // select under a global lock is the natural
        // generalization.
        //
        // The user gets visual feedback via a 200ms
        // [SelectionFlash] on the tapped image (already
        // used by the two-finger-down SelectImage
        // path at L6343), so they understand the
        // canvas is reacting to the tap — the canvas
        // just isn't selecting.
        //
        // We also pass `imageId = null` through
        // unchanged — deselecting from the
        // right-bottom-panel collapse action is a
        // legitimate UX path that must keep working
        // (the empty-tap-on-canvas [PockRefAction.Deselect]
        // path still fires even under a global lock, so
        // the user can clear a stale selection that
        // pre-dated the lock toggle).
        if (imageId != null && mainVm._uiState.value.globalImageLock) {
            mainVm._uiState.update { it.copy(selectionFlash = SelectionFlash(imageId = imageId, atMs = System.currentTimeMillis())) }
            return
        }
        mainVm._uiState.update { state ->
            if (imageId == null) {
                return@update state.copy(
                    selectedImageId = null,
                    selectedImageIds = emptySet(),
                    selectedGroupIds = emptySet(),
                    editingGroupId = null,
                    topToolbarExpanded = true,
                    // V_NOW: same as [deselect] — deselecting
                    // a selected image collapses the
                    // right-bottom panel.
                    bottomRightPanelExpanded = false,
                ).normalizeSelection()
            }
            val editingGroup = state.editingGroupId?.let { id -> state.groups.firstOrNull { it.id == id } }
            if (state.editingGroupId != null && editingGroup?.contains(imageId) != true) {
                return@update state
            }
            val group = if (state.editingGroupId == null) state.groupContaining(imageId) else null
            if (group != null) {
                val groupIds = group.imageIds.toSet()
                val nextSelection = if (state.multiSelectMode) {
                    if (state.selectedImageIds.containsAll(groupIds)) state.selectedImageIds - groupIds
                    else state.selectedImageIds + groupIds
                } else {
                    groupIds
                }
                return@update state.copy(
                    selectedImageId = imageId.takeIf { it in nextSelection } ?: nextSelection.lastOrNull(),
                    selectedImageIds = nextSelection,
                    selectedGroupIds = state.groups.filter { candidate ->
                        candidate.imageIds.any { it in nextSelection }
                    }.mapTo(linkedSetOf()) { it.id },
                    multiSelectMode = nextSelection.size > 1,
                    boxSelectMode = false,
                    topToolbarExpanded = nextSelection.isEmpty(),
                    bottomRightPanelExpanded = false,
                ).normalizeSelection()
            }
            if (!state.multiSelectMode) {
                return@update state.copy(
                    selectedImageId = imageId,
                    selectedImageIds = setOf(imageId),
                    selectedGroupIds = emptySet(),
                    editingGroupId = state.editingGroupId,
                    topToolbarExpanded = false,
                    // V_NOW: selecting an image causes the
                    // SelectionFloatingToolbar to appear
                    // (the user's "new focus area" per the
                    // spec). Collapse the right-bottom panel
                    // so the selection toolbar has clean
                    // visual room without competing with the
                    // bottom panel's 4 list items.
                    bottomRightPanelExpanded = false,
                    // V_NOW: re-show the selection toolbar
                    // if it was auto-hidden by the 5s
                    // inactivity timer. Tapping an image is
                    // the explicit user action to bring it
                    // back.
                ).normalizeSelection()
            }
            val updated = state.selectedImageIds.toMutableSet().apply {
                if (!add(imageId)) remove(imageId)
            }
            state.copy(
                selectedImageId = when {
                    updated.isEmpty() -> null
                    imageId in updated -> imageId
                    state.selectedImageId in updated -> state.selectedImageId
                    else -> updated.lastOrNull()
                },
                selectedImageIds = updated,
                selectedGroupIds = state.groups.filter { candidate ->
                    candidate.imageIds.isNotEmpty() && candidate.imageIds.all { it in updated }
                }.mapTo(linkedSetOf()) { it.id },
                topToolbarExpanded = updated.isEmpty(),
                // V_NOW: re-show the selection toolbar on
                // multi-select tap (same as single-select).
                selectionToolbarHidden = false,
            )
                .normalizeSelection()
        }
    }

    internal fun moveImage(imageId: String, delta: Offset) {
        // V_NOW: lock guard. `selectedIdsFor(imageId)` returns the
        // full multi-selection which may include locked images. A
        // locked image must not move even when selected (same
        // invariant as the drag / scale / rotate / flip paths).
        // Filter locked out before applying the delta so a mixed
        // selection translates only the unlocked members.
        // V_NOW (2026-06-08 "global image lock" feature):
        // use [PockRefUiState.isImageLocked] so the global
        // lock toggle ALSO gates this path — when
        // [globalImageLock] is on, no image moves even
        // if the per-image [SceneImage.locked] is off.
        val stateBefore = mainVm._uiState.value
        val targets = selectedIdsFor(imageId).filterTo(linkedSetOf()) { id ->
            !stateBefore.isImageLocked(id)
        }
        if (targets.isEmpty()) return
        mainVm.updateSceneForGesture {
            copy(images = images.map { image ->
                if (image.id in targets) image.copy(center = image.center + delta / canvasScale) else image
            })
        }
    }

    internal fun bringImageToFront(imageId: String) {
        if (imageId.isBlank()) return
        val state = mainVm._uiState.value
        if (state.images.none { it.id == imageId }) return
        val targetIds = if (state.editingGroupId == null) {
            state.groupContaining(imageId)?.imageIds?.toSet() ?: setOf(imageId)
        } else setOf(imageId)
        val currentMax = state.images.maxOf { it.zIndex }
        if (targetIds.all { id -> state.images.firstOrNull { it.id == id }?.zIndex == currentMax }) {
            // Already at the top; still record a gesture entry so undo/redo
            // work consistently even when the zIndex bump is a no-op.
            return
        }
        mainVm.updateSceneForGesture {
            val reordered = images.sortedBy { it.zIndex }.filterNot { it.id in targetIds } +
                images.filter { it.id in targetIds }.sortedBy { it.zIndex }
            copy(images = reordered.mapIndexed { index, img -> img.copy(zIndex = index) })
        }
    }

    internal fun cycleImageUnderPoint(point: Offset, viewportSize: Size, showSheet: Boolean = true) {
        val state = mainVm._uiState.value
        val viewportW = viewportSize.width
        val viewportH = viewportSize.height
        if (viewportW <= 0f || viewportH <= 0f) return

        fun hitTest(state: PockRefUiState, p: Offset): List<String> {
            return state.images.sortedByDescending { it.zIndex }.mapNotNull { image ->
                if (state.editingGroupId != null &&
                    state.groupContaining(image.id)?.id != state.editingGroupId
                ) return@mapNotNull null
                val worldPoint = canvasPointToWorld(p, viewportSize, state.canvasOffset, state.canvasScale)
                val localPoint = worldPoint - image.center
                val matrix = android.graphics.Matrix().apply {
                    postRotate(-image.rotationDegrees)
                    postScale(if (image.flipX) -1f else 1f, if (image.flipY) -1f else 1f)
                }
                val pts = floatArrayOf(localPoint.x, localPoint.y)
                matrix.mapPoints(pts)
                val halfWidth = (image.displaySize.width * image.scale) / 2f
                val halfHeight = (image.displaySize.height * image.scale) / 2f
                if (pts[0] in -halfWidth..halfWidth && pts[1] in -halfHeight..halfHeight) image.id else null
            }
        }

        val stacked = hitTest(state, point)
        if (stacked.isEmpty()) return
        val currentTop = state.selectedImageId
        val nextIndex = if (currentTop == null) {
            0
        } else {
            val idx = stacked.indexOf(currentTop)
            if (idx < 0) 0 else (idx + 1) % stacked.size
        }
        val nextId = stacked[nextIndex]
        selectImage(nextId)
        bringImageToFront(nextId)
        // V_NOW: in single-select mode, every image tap
        // should pop the SelectionBottomSheet. In
        // multi-select mode, tapping toggles selection
        // without popping the sheet — the sheet only
        // appears on long-press of an already-selected
        // image (handled in the pointer input gesture
        // handler). Bump the show-tick only when NOT in
        // multi-select / box-select mode AND [showSheet]
        // is true. The gesture handler passes
        // [showSheet = false] on the first tap of a
        // potential double-tap, then schedules a delayed
        // [TickSelectionSheet] — if the second tap comes
        // within the double-tap window, the scheduled
        // show is cancelled and the focus/unfocus action
        // fires instead, preventing the sheet from
        // flashing open then closed.
        if (showSheet && !state.multiSelectMode && !state.boxSelectMode) {
            mainVm._uiState.update {
                it.copy(
                    selectionSheetShowTick = it.selectionSheetShowTick + 1,
                    // V_NOW: reset dismiss-tick so the
                    // [SelectionBottomSheet]'s
                    // [LaunchedEffect(selectionSheetDismissTick)]
                    // doesn't re-fire with a stale
                    // non-zero value when the
                    // [key(showTick)] block is
                    // re-created on the next tap.
                    selectionSheetDismissTick = 0,
                )
            }
        }
    }

    internal fun beginImageDrag(imageId: String) {
        // V_NOW: lock guard. The down handler in PockRefApp.kt is
        // supposed to skip BeginImageDrag for locked images, but the
        // VM is the authoritative state machine — defence in depth.
        // If a locked image's id is somehow passed in, refuse here.
        // V_NOW (2026-06-08 "global image lock" feature):
        // use [PockRefUiState.isImageLocked] so the global
        // lock toggle also gates drag-start. When
        // [globalImageLock] is on, no image begins a drag
        // regardless of its per-image lock state.
        val stateBefore = mainVm._uiState.value
        if (stateBefore.isImageLocked(imageId)) return
        mainVm.beginSceneGesture()
        // V_NOW (user bug report): 在单选 / 多选 / 框选模式下,锁定的
        // 图片即使被选中了也不能被移动。`selectedIdsFor` 返回的集合
        // 可能包含**其它**已锁定的图片(例如用户先用框选把一张锁定
        // 图片和一张未锁定图片一起圈进选择,然后手指落在未锁定图片
        // 上开始拖)。如果不过滤,这一组 ids 会全部进入
        // `transientDragImageIds`,updateImageDrag 把 dragDelta
        // 累加到 `transientDragDelta`,commitImageDrag 提交时把这
        // 个 delta 同步加到每张图(包括锁定的)上 — 锁定图片被静默
        // 移动了。
        //
        // 修法:在赋值给 `transientDragImageIds` 前把锁定的剔掉。
        // 渲染层用 `transientDragImageIds` 决定哪几张图渲染时叠
        // `+transientDragDelta`,锁定图片不在这集合里 → 视觉上
        // 不动;commit 时 `dragIds` 也只包含非锁定的 → 实际位
        // 置不变。锁定图片的 zIndex / 边框 / 选中高亮照常显示,
        // 用户依然可以选中(用于解锁或检查),只是不能动。
        // V_NOW (2026-06-08 "global image lock" feature):
        // use [PockRefUiState.isImageLocked] so the global
        // lock toggle ALSO gates which images can be
        // entered into the drag state. When
        // [globalImageLock] is on, no image is draggable.
        val targets = selectedIdsFor(imageId).filterTo(linkedSetOf()) { id ->
            !stateBefore.isImageLocked(id)
        }
        // Drag and rotation are mutually exclusive: cancel any pending
        // rotation-snap debounce so it doesn't fire mid-drag and
        // surprise the user with a sudden rotation lock.
        rotationSnapEngageJob?.cancel()
        rotationSnapEngageJob = null
        mainVm._uiState.update {
            it.copy(
                transientDragImageIds = targets,
                transientDragDelta = Offset.Zero,
                // Clear any leftover snap state from a previous gesture so
                // the new gesture starts from a clean visual baseline.
                snapGuide = null,
                snapHighlight = null,
                snapInRangeSinceMs = null,
                snapEngagedTargetId = null,
                snapRecentSpeeds = emptyList(),
                snapLastSampleTimeMs = null,
                snapGuideAlpha = 1f,
                // Clear any stale angle-snap guide from a prior rotation
                // gesture (could happen if the user lifted mid-rotation).
                angleSnapGuide = null,
            )
        }
    }

    internal fun updateImageDrag(delta: Offset) {
        val current = mainVm._uiState.value
        if (current.transientDragImageIds.isEmpty()) return

        // If the user has disabled snap, accumulate the drag delta
        // verbatim and never run the engine. We still want to clear
        // any leftover snapGuide from a prior drag (e.g. user toggled
        // snap off mid-frame) so the canvas doesn't keep drawing
        // ghost guide lines. Also clear intent-detection state so a
        // subsequent re-enable starts clean.
        if (!current.edgeSnapEnabled) {
            mainVm._uiState.update { state ->
                val scale = state.canvasScale.coerceAtLeast(0.0001f)
                val maxStep = 200f
                val dx = delta.x.coerceIn(-maxStep, maxStep)
                val dy = delta.y.coerceIn(-maxStep, maxStep)
                state.copy(
                    transientDragDelta = state.transientDragDelta + Offset(dx, dy) / scale,
                    snapGuide = null,
                    snapInRangeSinceMs = null,
                    snapEngagedTargetId = null,
                    snapRecentSpeeds = emptyList(),
                    snapGuideAlpha = 1f,
                )
            }
            return
        }

        mainVm._uiState.update { state ->
            val scale = state.canvasScale.coerceAtLeast(0.0001f)
            // delta 是屏幕坐标增量，转换为世界坐标增量。
            // 限制单次最大步长，避免 motion event batched 后 delta 过大。
            val maxStep = 200f
            val dx = delta.x.coerceIn(-maxStep, maxStep)
            val dy = delta.y.coerceIn(-maxStep, maxStep)
            val candidate = state.transientDragDelta + Offset(dx, dy) / scale

            val now = System.currentTimeMillis()
            val triggerWorld = SNAP_THRESHOLD_PX / scale
            val cancelWorld = SNAP_CANCEL_DISTANCE_PX / scale

            // ---- 1. Probe proximity to the nearest target (world units) ----
            val proximity = SnapEngine.measureClosestTargetDistance(
                deltaWorld = SnapEngine.SnapDelta(candidate.x, candidate.y),
                dragIds = state.transientDragImageIds,
                allImages = state.images,
            )

            // ---- 2. Update sliding-window speed samples (screen px/s) ----
            val prevSampleMs = state.snapLastSampleTimeMs
            val dtMs = if (prevSampleMs == null) 16L else (now - prevSampleMs).coerceIn(1L, 100L)
            val instantaneousSpeedPxPerSec = if (delta == Offset.Zero) 0f else
                kotlin.math.hypot(delta.x, delta.y) / (dtMs.toFloat() / 1000f)
            val prunedSpeeds = state.snapRecentSpeeds.filter { now - it.timestampMs <= SNAP_SPEED_WINDOW_MS }
            val newSamples = prunedSpeeds + SpeedSample(now, instantaneousSpeedPxPerSec)
            val avgSpeed = if (newSamples.isEmpty()) 0f else newSamples.sumOf { it.pxPerSec.toDouble() }.toFloat() / newSamples.size

            // ---- 3. State machine: in-range timer, engage target, alpha ----
            // in-range timer
            val newInRangeSince: Long? = when {
                proximity == null || proximity.distanceWorld > cancelWorld -> null
                state.snapEngagedTargetId != null -> state.snapInRangeSinceMs
                proximity.distanceWorld <= triggerWorld -> state.snapInRangeSinceMs ?: now
                else -> state.snapInRangeSinceMs
            }

            // engage target: which image the snap engine should lock on
            val isInCancel = proximity != null && proximity.distanceWorld > cancelWorld
            val fastMove = avgSpeed >= SNAP_DISENGAGE_SPEED_PX_PER_S &&
                (delta.x * delta.x + delta.y * delta.y) > (SNAP_DISENGAGE_MIN_MOVE_PX * SNAP_DISENGAGE_MIN_MOVE_PX)
            val shouldDisengage = state.snapEngagedTargetId != null && (isInCancel || fastMove)

            val newEngagedTarget: String? = when {
                shouldDisengage -> null
                proximity != null && proximity.distanceWorld <= triggerWorld -> proximity.targetImageId
                else -> state.snapEngagedTargetId
            }

            // is currently engaged (engine should run)
            val dwellSatisfied = newInRangeSince != null && now - newInRangeSince >= SNAP_ENGAGE_DELAY_MS
            val slowEnough = avgSpeed < SNAP_ENGAGE_MAX_SPEED_PX_PER_S
            val isEngaged = newEngagedTarget != null && dwellSatisfied && slowEnough

            // guide alpha: 0..1 fade-in over the TRACKING window
            val guideAlpha: Float = when {
                isEngaged -> 1f
                newInRangeSince == null -> 1f  // no active engagement, alpha is moot
                else -> ((now - newInRangeSince).toFloat() / SNAP_ENGAGE_DELAY_MS.toFloat())
                    .coerceIn(0f, 1f)
            }

            // ---- 4. Run the engine only when ENGAGED ----
            if (isEngaged) {
                val snapResult = SnapEngine.compute(
                    deltaWorld = SnapEngine.SnapDelta(candidate.x, candidate.y),
                    dragIds = state.transientDragImageIds,
                    allImages = state.images,
                    thresholdWorld = triggerWorld,
                )
                state.copy(
                    transientDragDelta = Offset(
                        snapResult.adjustedDeltaWorld.dx,
                        snapResult.adjustedDeltaWorld.dy,
                    ),
                    snapGuide = snapResult.guide,
                    snapInRangeSinceMs = newInRangeSince,
                    snapEngagedTargetId = newEngagedTarget,
                    snapRecentSpeeds = newSamples,
                    snapLastSampleTimeMs = now,
                    snapGuideAlpha = guideAlpha,
                )
            } else {
                // No snap this frame. Accumulate raw drag delta and
                // clear the guide. Intent-detection state is updated
                // either way so the user still sees the alpha ramp
                // (when in TRACKING) and the next engage decision is
                // current.
                state.copy(
                    transientDragDelta = candidate,
                    snapGuide = null,
                    snapInRangeSinceMs = newInRangeSince,
                    snapEngagedTargetId = newEngagedTarget,
                    snapRecentSpeeds = newSamples,
                    snapLastSampleTimeMs = now,
                    snapGuideAlpha = guideAlpha,
                )
            }
        }
    }

    internal fun commitImageDrag() {
        val dragIds = mainVm._uiState.value.transientDragImageIds
        val dragDelta = mainVm._uiState.value.transientDragDelta
        val lastGuide = mainVm._uiState.value.snapGuide
        if (dragIds.isEmpty()) {
            // Clear any pending rotation-snap debounce so a stale
            // timer doesn't fire after the drag ends.
            rotationSnapEngageJob?.cancel()
            rotationSnapEngageJob = null
            mainVm.endSceneGesture()
            return
        }
        if (dragDelta == Offset.Zero) {
            cancelImageDrag()
            return
        }
        // V_NOW: 防御性 lock 过滤。理论上 `transientDragImageIds`
        // 在 `beginImageDrag` 里就已经剔过锁定的图,正常路径下
        // 这里不会再有锁定图片。但是中间件路径(例如
        // Lock 状态在拖动中从外部被改动 — 未来的批处理脚本、
        // 撤销栈应用 [applyDiffForward]、跨设备协同等)可能
        // 让一张图在拖动过程中突然变成 locked。如果不再次过滤,
        // commit 时 delta 就会落到一张不该动的图上。
        // V_NOW (2026-06-08 "global image lock" feature):
        // use [PockRefUiState.isImageLocked] so the global
        // lock toggle (which may have flipped mid-drag)
        // also blocks the commit. The two checks are
        // intentionally redundant — the begin-time
        // filter already does the per-image lock check,
        // the commit-time filter is the
        // defence-in-depth fallback.
        val stateNow = mainVm._uiState.value
        val movableIds = dragIds.filter { id ->
            !stateNow.isImageLocked(id)
        }
        if (movableIds.isEmpty()) {
            // 全部被锁住 / 已经空了 — 当成取消处理,清 transient。
            cancelImageDrag()
            return
        }
        // dragDelta 是世界单位累加，直接加到 image.center。
        // 同步清零 transient 状态：否则下一帧 drawSceneImage 会把 dragDelta 叠加到
        // 已含 dragDelta 的 image.center 上（image.center + dragOffset），导致 2x 跳位。
        mainVm.updateSceneForGesture {
            copy(
                images = images.map { image ->
                    if (image.id in movableIds) image.copy(center = image.center + dragDelta) else image
                },
                transientDragImageIds = emptySet(),
                transientDragDelta = Offset.Zero,
                snapGuide = null,
                // Reset the V4 intent-detection state machine so the
                // NEXT drag starts from IDLE (no stale "in range since"
                // timer or engaged target from this drag).
                snapInRangeSinceMs = null,
                snapEngagedTargetId = null,
                snapRecentSpeeds = emptyList(),
                snapLastSampleTimeMs = null,
                snapGuideAlpha = 1f,
                // Drag is unrelated to rotation, but clearing here keeps
                // the invariant "angleSnapGuide is null unless a
                // transform-gesture is actively firing rotation snap".
                angleSnapGuide = null,
            )
        }
        mainVm.endSceneGesture()
        // If the final resting position was produced by snap, fire a brief
        // highlight overlay so the user gets visual confirmation that
        // snap fired. We arm it with the start time so the renderer can
        // animate an alpha fade-out over [SNAP_HIGHLIGHT_DURATION_MS].
        if (lastGuide != null) {
            mainVm.setSnapHighlight(
                com.pockref.app.model.SnapHighlight(
                    guide = lastGuide,
                    startTimeMs = System.currentTimeMillis(),
                )
            )
        }
    }

    internal fun cancelImageDrag() {
        // Clear any pending rotation-snap debounce so a stale
        // timer doesn't fire after the drag is cancelled.
        rotationSnapEngageJob?.cancel()
        rotationSnapEngageJob = null
        mainVm._uiState.update {
            it.copy(
                transientDragImageIds = emptySet(),
                transientDragDelta = Offset.Zero,
                snapGuide = null,
                snapInRangeSinceMs = null,
                snapEngagedTargetId = null,
                snapRecentSpeeds = emptyList(),
                snapLastSampleTimeMs = null,
                snapGuideAlpha = 1f,
                angleSnapGuide = null,
            )
        }
        mainVm.endSceneGesture()
    }

    @Suppress("UNUSED_PARAMETER")
    internal fun zoomCanvas(scaleChange: Float, focus: Offset) {
        // [focus] parameter is intentionally ignored. The no-selection
        // pinch / rotate gestures all dispatch [ZoomCanvas] expecting
        // the zoom to anchor on the **canvas geometric center** —
        // that is the only behaviour the user spec'd ("固定为画布可视
        // 区域的几何中心,而非双指中点或单指触点或画布右下角").
        //
        // The correct formula to keep the viewport's world-space
        // centre (= the world point sitting at the screen centre
        // (vpW/2, vpH/2)) fixed is [newOffset = canvasOffset * scaleRatio].
        // The previous formula [newOffset = anchor - (anchor - canvasOffset)
        // * scaleRatio] was the textbook "zoom around point" form, but
        // it implicitly assumed [anchor] and [canvasOffset] share a
        // coordinate system where the origin is the **screen's** top-left
        // — which is true for [anchor] (it is the screen centre in raw
        // pixels) but **false** for [canvasOffset], which is the world
        // origin's offset from the **screen centre** (see line 4545,
        // `imageLeftScreenX = halfW + canvasOffset.x + worldX * scale`).
        // That coordinate-system mismatch produced an extra
        // [anchor * (1 - scaleRatio)] term that drifted the world-space
        // viewport centre by ~0.09 * viewportWidth on a 10% zoom in —
        // which is what the user observed as '画布朝右下角放大' on
        // the minimap (the blue viewport indicator slid toward the
        // bottom-right of the scene bbox instead of staying put while
        // shrinking). The pure-multiply form has no such term.
        mainVm._uiState.update { state ->
            val newScale = (state.canvasScale * scaleChange).coerceIn(MIN_CANVAS_SCALE, MAX_CANVAS_SCALE)
            val scaleRatio = newScale / state.canvasScale
            val newOffset = state.canvasOffset * scaleRatio
            state.copy(canvasScale = newScale, canvasOffset = newOffset, fittedImageId = null)
        }
    }

    /**
     * Set canvas zoom to an absolute scale value, anchoring
     * on the canvas visual center. Used by the minimap
     * long-press zoom slider.
     */
    internal fun setCanvasZoom(scale: Float) {
        mainVm._uiState.update { state ->
            val newScale = scale.coerceIn(MIN_CANVAS_SCALE, MAX_CANVAS_SCALE)
            val scaleRatio = newScale / state.canvasScale
            val newOffset = state.canvasOffset * scaleRatio
            state.copy(canvasScale = newScale, canvasOffset = newOffset, fittedImageId = null)
        }
    }

    internal fun transformSelectedImage(scaleChange: Float, rotationChange: Float, pan: Offset, worldPivot: Offset? = null) {
        val stateBefore = mainVm._uiState.value
        val allSelectedIds = stateBefore.selectedImageIds.ifEmpty {
            stateBefore.selectedImageId?.let(::setOf).orEmpty()
        }
        // V_NOW: a locked image must NOT be scaled, rotated, or
        // panned. Filter it out of the transform target set. If
        // every selected image is locked, this call is a no-op
        // (the gesture handler still has `transformMode` = SCALE /
        // ROTATE so the rest of the app's "transform in progress"
        // state stays consistent, but the scene itself is not
        // mutated). For mixed selections, the locked images keep
        // their pose while the rest transform normally — the locked
        // ones are not part of the pivot, the scale factor, or
        // the per-image transform loop below.
        // V_NOW (2026-06-08 "global image lock" feature):
        // use [PockRefUiState.isImageLocked] so the global
        // lock toggle ALSO blocks the transform. When
        // [globalImageLock] is on, no selected image can
        // be scaled / rotated / panned regardless of its
        // per-image lock state — the call becomes a
        // no-op for the scene state machine.
        val selectedIds = allSelectedIds.filter { id ->
            !stateBefore.isImageLocked(id)
        }
        if (selectedIds.isEmpty()) return
        val rotationSnapActive = stateBefore.rotationSnapEnabled
        val previousGuide = stateBefore.angleSnapGuide
        // The rotation-snap reference image must also be a
        // transformable one — otherwise the snap guide would fire
        // for a pose change the locked image isn't actually
        // undergoing. Prefer [selectedImageId] if it's unlocked,
        // else fall through to the first unlocked member of
        // [selectedImageIds].
        val firstImageId = stateBefore.selectedImageId
            ?.takeIf { id -> id in selectedIds }
            ?: stateBefore.selectedImageIds.firstOrNull { id -> id in selectedIds }
        val firstImage = if (firstImageId != null) {
            stateBefore.images.firstOrNull { it.id == firstImageId }
        } else null
        val firstRotation = firstImage?.rotationDegrees ?: 0f
        val now = System.currentTimeMillis()

        // Run the pure state-machine transition. The result tells us
        // (a) what `effectiveDelta` to apply to the image's
        // `rotationDegrees` for THIS frame, (b) what the new
        // `angleSnapGuide` should be, and (c) whether we still need a
        // pending coroutine to fire the 200ms debounce (or if the
        // state machine decided to fire it synchronously on this
        // frame, in which case no coroutine is needed).
        val decision = computeRotationSnapDecision(
            firstRotation = firstRotation,
            rotationChange = rotationChange,
            rotationSnapActive = rotationSnapActive,
            previousGuide = previousGuide,
            now = now,
        )

        // Coroutine bookkeeping. We keep a single `Job?` reference so
        // we never have more than one pending debounce fire at a time.
        // The coroutine is only armed when:
        // - The state machine says we're in the zone
        // - We're NOT actively snapped (so a coroutine for the NEXT
        //   fire isn't needed — we're already locked)
        // - The state machine did NOT fire synchronously on this frame
        // - The user-supplied rotationChange was non-zero (otherwise
        //   we'd just be re-arming the same coroutine every frame the
        //   user holds still, which is wasteful — the coroutine
        //   already started counting from the original entry)
        val needsEngageJob = decision.isInZone &&
            !decision.isActivelySnapped &&
            !decision.shouldEngageNow &&
            decision.newGuide != null &&
            decision.newGuide.activeSnapAngle == null &&
            rotationChange != 0f

        if (!needsEngageJob) {
            rotationSnapEngageJob?.cancel()
            rotationSnapEngageJob = null
        } else if (rotationSnapEngageJob == null) {
            val enteredMs = decision.newGuide?.angleEnteredSnapZoneMs ?: now
            val remaining = (enteredMs + ROTATION_SNAP_DEBOUNCE_MS) - now
            if (remaining > 0L) {
                scheduleRotationSnapEngage(
                    imageId = firstImageId,
                    remainingMs = remaining,
                )
            }
        }

        // Apply the state-machine decision. `effectiveDelta` is what
        // actually mutates the image's `rotationDegrees` for this
        // frame; the center math in `transformImageAroundPivot` uses
        // it (so when the snap fires on the same frame, the image
        // visually jumps to the snap target rather than overshooting
        // and rubber-banding back).
        val effectiveDelta = decision.effectiveDelta
        val newGuide = decision.newGuide
        mainVm.updateSceneForGesture {
            val selected = images.filter { it.id in selectedIds }
            if (selected.isEmpty()) return@updateSceneForGesture this

            // V_NOW (user spec "双指缩放选中图片"): the
            // scale pivot. When [worldPivot] is provided
            // (the two-finger pinch path passes the centroid
            // in world coordinates), we use it as the
            // scale anchor instead of the AABB-centre
            // default. Multi-select still pivots the
            // whole group at its AABB centre and then
            // adds a scene-space pan offset to translate
            // the post-scale AABB centre onto
            // [worldPivot] — that way all members of the
            // group scale around the same world point.
            //
            // AABB FALLBACK: if the centroid is wildly
            // outside the AABB (e.g. the user pinched
            // far from the selection in canvas
            // background), we treat it as "anchored at
            // the centroid" but quietly use the AABB
            // centre instead — otherwise the pan
            // correction would translate the group by
            // a huge amount and visibly "snap" the
            // selection to the finger location. The
            // threshold is 1.5× the AABB half-diagonal,
            // which gives the user some slack (pinch
            // just outside the AABB still works) but
            // caps the worst-case drift.
            val aabb = if (selected.size == 1) {
                val s = selected.first()
                val hW = s.displaySize.width * s.scale / 2f
                val hH = s.displaySize.height * s.scale / 2f
                AabbBox(s.center, hW, hH)
            } else {
                var left = Float.POSITIVE_INFINITY
                var right = Float.NEGATIVE_INFINITY
                var top = Float.POSITIVE_INFINITY
                var bottom = Float.NEGATIVE_INFINITY
                for (img in selected) {
                    val hW = img.displaySize.width * img.scale / 2f
                    val hH = img.displaySize.height * img.scale / 2f
                    val l = img.center.x - hW; val r = img.center.x + hW
                    val t = img.center.y - hH; val b = img.center.y + hH
                    if (l < left) left = l
                    if (r > right) right = r
                    if (t < top) top = t
                    if (b > bottom) bottom = b
                }
                AabbBox(
                    center = Offset((left + right) / 2f, (top + bottom) / 2f),
                    halfW = (right - left) / 2f,
                    halfH = (bottom - top) / 2f,
                )
            }
            val effectiveScale = computeGroupScaleFactor(selected, scaleChange)
            val (scalePivot, extraPanInScene) = if (worldPivot == null) {
                // Legacy path — toolbar [ScaleUp] /
                // [ScaleDown] buttons don't pass a
                // focal point, so the AABB centre is
                // the only sensible anchor.
                Pair(aabb.center, Offset.Zero)
            } else {
                val pivotDelta = worldPivot - aabb.center
                val aabbDiag = kotlin.math.hypot(aabb.halfW, aabb.halfH)
                val worldTooFar = pivotDelta.getDistance() > aabbDiag * 1.5f
                if (worldTooFar) {
                    // Centroid is far outside the AABB
                    // — fall back to AABB centre to
                    // avoid a huge "snap-to-finger"
                    // translation when the user
                    // releases. The user's pinch was
                    // still honored at the rate of
                    // [scaleChange] around the AABB
                    // centre.
                    Pair(aabb.center, Offset.Zero)
                } else {
                    // Pivot at worldPivot. For
                    // multi-select, scale around the
                    // AABB centre then pan so the
                    // post-scale AABB centre lands on
                    // [worldPivot] (the centroid the
                    // user's fingers were on).
                    //
                    // The pivot "moves" from aabb.center
                    // to worldPivot under scale [s];
                    // the displacement is
                    // (worldPivot - aabb.center) in
                    // world units. The compensation
                    // that keeps [worldPivot] visually
                    // fixed is:
                    //   pan = (aabb.center - worldPivot) * (1 - 1/s)
                    //       = (aabb.center - worldPivot) * (s-1) / s
                    // For single-image we just use
                    // [worldPivot] as the pivot
                    // directly (no pan correction
                    // needed — [transformImageAroundPivot]
                    // already keeps the pivot fixed).
                    if (selected.size == 1) {
                        Pair(worldPivot, Offset.Zero)
                    } else {
                        val centerPan = (aabb.center - worldPivot) * ((effectiveScale - 1f) / effectiveScale)
                        Pair(aabb.center, centerPan)
                    }
                }
            }
            val panInScene = pan / canvasScale + extraPanInScene

            copy(
                images = images.map { image ->
                    if (image.id !in selectedIds) image
                    else transformImageAroundPivot(image, scalePivot, effectiveScale, effectiveDelta, panInScene)
                },
                angleSnapGuide = newGuide,
            )
        }
    }

    /**
     * V_NOW (user spec "双指缩放选中图片"): local helper
     * struct that pairs an AABB's centre with its half-
     * extent on each axis. Used by the transform pivot
     * selection logic above to decide whether the
     * two-finger centroid is close enough to the AABB
     * to be used as a pivot, or far enough that we
     * should silently fall back to the AABB centre.
     */
    private data class AabbBox(
        val center: Offset,
        val halfW: Float,
        val halfH: Float,
    )

    /**
     * Arm a coroutine on the main VM's `viewModelScope` that fires
     * after [remainingMs] and re-evaluates the rotation-snap state
     * machine. If the gesture is still in the snap zone at fire
     * time, the snap engages: the image's `rotationDegrees` is set
     * to the canonical 8-angle, the `angleSnapGuide` is updated to
     * the ENGAGED shape, and the gesture is locked until the user
     * rotates past the continue threshold.
     *
     * The coroutine uses a fresh `now` value (re-evaluated at fire
     * time) and re-reads the current state from
     * `mainVm._uiState.value`, so it correctly handles the case
     * where the user kept moving (or moved out of the zone) between
     * the schedule time and the fire time — if the state machine
     * says "no engage" at fire time, the coroutine is a no-op.
     *
     * Cancelled by any subsequent [transformSelectedImage] call that
     * arms a different decision state, and by
     * [beginImageDrag] / [commitImageDrag] / [cancelImageDrag] (drag
     * and rotation are mutually exclusive gestures — clearing the
     * job here prevents a stale debounce from firing mid-drag).
     */
    private fun scheduleRotationSnapEngage(imageId: String?, remainingMs: Long) {
        rotationSnapEngageJob?.cancel()
        if (imageId == null) return
        val targetImageId = imageId
        rotationSnapEngageJob = mainVm.viewModelScope.launch {
            delay(remainingMs)
            val stateNow = mainVm._uiState.value
            val currentImg = stateNow.images.firstOrNull { it.id == targetImageId }
                ?: return@launch
            val fireNow = System.currentTimeMillis()
            val decision = computeRotationSnapDecision(
                firstRotation = currentImg.rotationDegrees,
                rotationChange = 0f,
                rotationSnapActive = stateNow.rotationSnapEnabled,
                previousGuide = stateNow.angleSnapGuide,
                now = fireNow,
            )
            if (decision.shouldEngageNow && decision.engageTargetAngle != null) {
                val target = decision.engageTargetAngle
                mainVm._uiState.update { s ->
                    s.copy(
                        images = s.images.map { img ->
                            if (img.id == targetImageId) img.copy(rotationDegrees = target) else img
                        },
                        angleSnapGuide = decision.newGuide ?: s.angleSnapGuide,
                    )
                }
            }
        }
    }

    internal fun scaleSelected(factor: Float) {
        val stateBefore = mainVm._uiState.value
        val selectedIds = currentSelectedIds().filterTo(mutableSetOf()) { id -> !stateBefore.isImageLocked(id) }
        if (selectedIds.isEmpty()) return
        mainVm.mutateScene {
            val selected = images.filter { it.id in selectedIds }
            if (selected.isEmpty()) return@mutateScene this

            val pivot = calculateTransformPivot(selected)
            val effectiveFactor = computeGroupScaleFactor(selected, factor)

            copy(images = images.map { image ->
                if (image.id !in selectedIds) image
                else scaleImageAroundPivot(image, pivot, effectiveFactor)
            })
        }
    }

    internal fun rotateSelected(delta: Float) {
        val stateBefore = mainVm._uiState.value
        val allSelectedIds = stateBefore.selectedImageIds.ifEmpty {
            stateBefore.selectedImageId?.let(::setOf).orEmpty()
        }
        // V_NOW: lock guard. Locked images ignore discrete rotate
        // (the ±90° toolbar buttons) the same way they ignore
        // continuous pinch / rotate (see [transformSelectedImage]
        // and [flipSelected] for the matching filter).
        val selectedIds = allSelectedIds.filter { id ->
            !stateBefore.isImageLocked(id)
        }
        if (selectedIds.isEmpty()) return
        val rotationSnapActive = stateBefore.rotationSnapEnabled
        mainVm.mutateScene {
            val selected = images.filter { it.id in selectedIds }
            if (selected.isEmpty()) return@mutateScene this

            val pivot = calculateTransformPivot(selected)

            // Discrete rotate (-90° / +90° buttons) — snap the FINAL
            // angle to the nearest cardinal. For exactly-90° deltas this
            // is a no-op (the result is already on a snap), but the
            // check is cheap and keeps behavior consistent with the
            // continuous-rotation path. e.g. a discrete "rotate 87°"
            // would land on 90° if within threshold.
            val firstRotation = selected.first().rotationDegrees
            val candidate = firstRotation + delta
            val snapped = if (rotationSnapActive) detectAngleSnap(candidate) else null
            val effectiveDelta = if (snapped != null) snapped - firstRotation else delta

            copy(images = images.map { image ->
                if (image.id !in selectedIds) image
                else rotateImageAroundPivot(image, pivot, effectiveDelta)
            })
        }
    }

    internal fun flipSelected(horizontal: Boolean) {
        val stateBefore = mainVm._uiState.value
        val allSelectedIds = stateBefore.selectedImageIds.ifEmpty {
            stateBefore.selectedImageId?.let(::setOf).orEmpty()
        }
        // V_NOW: locked images ignore flip too. For a mixed
        // selection, only the unlocked members actually flip;
        // for an all-locked selection, the call is a no-op
        // (same shape as the transformSelectedImage filter).
        val transformableIds = allSelectedIds.filter { id ->
            !stateBefore.isImageLocked(id)
        }
        if (transformableIds.isEmpty()) return
        mainVm.mutateScene {
            copy(images = images.map { image ->
                if (image.id !in transformableIds) image
                else if (horizontal) image.copy(flipX = !image.flipX)
                else image.copy(flipY = !image.flipY)
            })
        }
    }

    internal fun bringToFront() {
        reorderSelectedImages(bringToFront = true)
    }

    internal fun sendToBack() {
        reorderSelectedImages(bringToFront = false)
    }

    private fun reorderSelectedImages(bringToFront: Boolean) {
        val stateBefore = mainVm._uiState.value
        val selectedIds = currentSelectedIds()
        if (selectedIds.isEmpty()) return
        val movableIds = selectedIds.filterTo(mutableSetOf()) { id -> !stateBefore.isImageLocked(id) }
        if (movableIds.isEmpty()) return
        val ordered = stateBefore.images.sortedBy { it.zIndex }
        val scopeIds = stateBefore.editingGroupId?.let { groupId ->
            stateBefore.groups.firstOrNull { it.id == groupId }?.imageIds?.toSet()
        } ?: ordered.mapTo(mutableSetOf()) { it.id }
        val scoped = ordered.filter { it.id in scopeIds }
        val selected = scoped.filter { it.id in movableIds }
        if (selected.isEmpty()) return
        val remaining = scoped.filterNot { it.id in movableIds }
        val reorderedScope = if (bringToFront) remaining + selected else selected + remaining
        val replacement = reorderedScope.iterator()
        val reordered = ordered.map { image ->
            if (image.id in scopeIds) replacement.next() else image
        }.mapIndexed { index, image -> image.copy(zIndex = index) }
        mainVm.mutateScene {
            val zById = reordered.associate { it.id to it.zIndex }
            copy(
                images = reordered,
                groups = groups.map { group ->
                    group.copy(imageIds = group.imageIds.sortedBy { zById[it] ?: Int.MAX_VALUE })
                },
            )
        }
    }

    internal fun deleteSelected() {
        val selectedIds = currentSelectedIds()
        if (selectedIds.isEmpty()) return
        mainVm.mutateScene {
            val remaining = images.filterNot { it.id in selectedIds }
            copy(
                images = remaining,
                selectedImageId = remaining.lastOrNull()?.id,
                selectedImageIds = remaining.lastOrNull()?.id?.let(::setOf).orEmpty(),
                transientDragImageIds = emptySet(),
                transientDragDelta = Offset.Zero,
            )
        }
    }

    /**
     * Clone every selected image with a fresh id and a small
     * diagonal offset, then add the clones to the scene above the
     * originals. The new images inherit selection so the user can
     * immediately move / rotate them. Lock state is NOT carried
     * over (the user explicitly duplicated — they almost certainly
     * want to work with the copies, not have them be inert).
     */
    internal fun duplicateSelected() {
        // V_NOW: lock guard. A locked image can be duplicated —
        // duplicating creates a NEW image and does not touch the
        // original's pose, so the original's lock state is
        // preserved (and the clone itself is born unlocked, see
        // [locked = false] below). Refuse only the (rare) case
        // where a user explicitly tries to duplicate by
        // double-tap-drag or other future gesture against a locked
        // image — the down handler skips drag there, so this
        // server-side guard is defence in depth, not a primary
        // lock enforcement.
        val stateBefore = mainVm._uiState.value
        val selectedIds = stateBefore.selectedImageIds.ifEmpty {
            stateBefore.selectedImageId?.let(::setOf).orEmpty()
        }
        if (selectedIds.isEmpty()) return
        val originals = stateBefore.images.filter { it.id in selectedIds }.sortedBy { it.zIndex }
        mainVm.mutateScene {
            val maxZ = (images.maxOfOrNull { it.zIndex } ?: 0) + 1
            val newImages = originals.mapIndexed { i, original ->
                val clone = original.copy(
                    id = java.util.UUID.randomUUID().toString(),
                    center = original.center + Offset(20f + i * 6f, 20f + i * 6f),
                    zIndex = maxZ + i,
                    // The clone is born UNLOCKED — the lock applies
                    // to a specific image id (i.e. the original); a
                    // fresh clone starts as a normal mutable image.
                    locked = false,
                )
                clone
            }
            val idMap = originals.zip(newImages).associate { (original, copy) -> original.id to copy.id }
            val editingGroupId = stateBefore.editingGroupId
            val updatedGroups = if (editingGroupId != null) {
                groups.map { group ->
                    if (group.id == editingGroupId) group.copy(imageIds = group.imageIds + newImages.map { it.id })
                    else group
                }
            } else {
                val copiedGroups = stateBefore.selectedGroups
                    .filter { group -> selectedIds.containsAll(group.imageIds) }
                    .map { group ->
                        group.copy(
                            id = java.util.UUID.randomUUID().toString(),
                            name = "${group.name} 副本",
                            imageIds = group.imageIds.mapNotNull(idMap::get),
                        )
                    }
                groups + copiedGroups
            }
            val imagesWithCopies = images + newImages
            val orderedImages = if (editingGroupId != null) {
                val originalGroupIds = groups.firstOrNull { it.id == editingGroupId }?.imageIds.orEmpty().toSet()
                val copyIds = newImages.mapTo(mutableSetOf()) { it.id }
                val existing = imagesWithCopies.filterNot { it.id in copyIds }.sortedBy { it.zIndex }
                val insertionIndex = (existing.indexOfLast { it.id in originalGroupIds } + 1)
                    .coerceIn(0, existing.size)
                existing.toMutableList().apply { addAll(insertionIndex, newImages) }
                    .mapIndexed { index, image -> image.copy(zIndex = index) }
            } else {
                imagesWithCopies
            }
            copy(
                images = orderedImages,
                groups = updatedGroups,
                selectedGroupIds = if (editingGroupId == null) {
                    updatedGroups.filter { group -> newImages.map { it.id }.toSet().containsAll(group.imageIds) }
                        .mapTo(linkedSetOf()) { it.id }
                } else {
                    emptySet()
                },
                selectedImageIds = newImages.map { it.id }.toSet(),
                selectedImageId = newImages.last().id,
            )
        }
        // Also copy the first selected image to the system
        // clipboard so the user can paste it into other apps.
        // The MediaStore insert below needs scoped storage (API 29+);
        // older devices would need WRITE_EXTERNAL_STORAGE, which we
        // don't request, so they keep the in-app duplicate only.
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) return
        val firstOriginal = originals.firstOrNull() ?: return
        mainVm.viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val bytes = mainVm.resolveImageBytes(firstOriginal)
                    val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        ?: return@withContext
                    // Apply non-destructive crop if present.
                    val crop = firstOriginal.cropRect
                    val croppedBitmap = if (crop != null) {
                        val cx = (crop.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
                        val cy = (crop.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
                        val cw = ((crop.right - crop.left) * bitmap.width).toInt().coerceIn(1, bitmap.width - cx)
                        val ch = ((crop.bottom - crop.top) * bitmap.height).toInt().coerceIn(1, bitmap.height - cy)
                        android.graphics.Bitmap.createBitmap(bitmap, cx, cy, cw, ch)
                            .also { if (it !== bitmap) bitmap.recycle() }
                    } else {
                        bitmap
                    }
                    val app = mainVm.getApplication<android.app.Application>()
                    // Insert into MediaStore to get a content:// URI
                    // that other apps can read from the clipboard.
                    val values = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, firstOriginal.name)
                        put(android.provider.MediaStore.Images.Media.MIME_TYPE, firstOriginal.mimeType.ifEmpty { "image/png" })
                    }
                    val collection = android.provider.MediaStore.Images.Media.getContentUri(
                        android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY
                    )
                    val contentUri = app.contentResolver.insert(collection, values)
                    if (contentUri != null) {
                        app.contentResolver.openOutputStream(contentUri)?.use { out ->
                            val mime = firstOriginal.mimeType.ifEmpty { "image/png" }
                            val compressFormat = when {
                                mime.equals("image/jpeg", true) || mime.equals("image/jpg", true) ->
                                    android.graphics.Bitmap.CompressFormat.JPEG
                                mime.equals("image/webp", true) ->
                                    if (android.os.Build.VERSION.SDK_INT >= 30)
                                        android.graphics.Bitmap.CompressFormat.WEBP_LOSSY
                                    else @Suppress("DEPRECATION") android.graphics.Bitmap.CompressFormat.WEBP
                                else -> android.graphics.Bitmap.CompressFormat.PNG
                            }
                            croppedBitmap.compress(compressFormat, 95, out)
                        }
                        val clipboard = app.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as? android.content.ClipboardManager
                        clipboard?.setPrimaryClip(
                            android.content.ClipData.newUri(
                                app.contentResolver,
                                "Image",
                                contentUri,
                            )
                        )
                    }
                    croppedBitmap.recycle()
                }
            } catch (_: Exception) {
                // Non-critical: clipboard copy failure should not
                // affect the in-app duplicate operation.
            }
        }
    }

    /**
     * Flip the [locked] flag on every selected image. Locked images
     * ignore drag / pinch / rotate / lasso input but still render
     * and remain tappable so the user can unlock them. Used by the
     * long-press context menu's lock button.
     */
    internal fun toggleLockSelected() {
        val selectedIds = currentSelectedIds()
        if (selectedIds.isEmpty()) return
        val state = mainVm._uiState.value
        // V_NOW (2026-06-08 "global image lock" feature):
        // refuse to toggle per-image lock when the
        // global lock is on. The semantic is "global
        // wins" — the user must first turn the global
        // lock off before they can change any individual
        // image's lock state. This keeps the
        // "everything's locked" promise consistent.
        if (state.globalImageLock) return
        val selectedGroups = state.selectedGroups
        val selectedGroupIds = selectedGroups.mapTo(mutableSetOf()) { it.id }
        val selectedGroupMembers = state.groups.filter { it.id in selectedGroupIds }
            .flatMapTo(mutableSetOf()) { it.imageIds }
        val shouldUnlockGroup = selectedGroups.any { it.positionLocked }
        mainVm.mutateScene {
            copy(
                groups = groups.map { group ->
                    if (group.id in selectedGroupIds) group.copy(positionLocked = !shouldUnlockGroup) else group
                },
                images = images.map { image ->
                    if (image.id !in selectedIds || image.id in selectedGroupMembers) image
                    else image.copy(locked = !image.locked)
                },
            )
        }
    }

    internal fun toggleOriginalDisplaySelected() {
        val selectedIds = currentSelectedIds()
        if (selectedIds.isEmpty()) return
        val selected = mainVm._uiState.value.images.filter { it.id in selectedIds }
        if (selected.isEmpty()) return
        val shouldEnable = !selected.all { it.showsOriginalResolution }
        mainVm.mutateScene {
            copy(images = images.map { image ->
                if (image.id !in selectedIds) image
                else image.copy(showsOriginalResolution = shouldEnable)
            })
        }
    }

    internal fun updateColorAdjust(brightness: Float, contrast: Float, saturation: Float) {
        val selectedIds = currentSelectedIds()
        if (selectedIds.isEmpty()) return
        mainVm.mutateScene {
            copy(images = images.map { image ->
                if (image.id !in selectedIds) image
                else image.copy(
                    colorAdjust = ColorAdjust(
                        brightness = brightness,
                        contrast = contrast,
                        saturation = saturation,
                    )
                )
            })
        }
    }

    fun undo() {
        val entry = mainVm.undoStack.removeLastOrNull() ?: return
        val currentState = mainVm._uiState.value
        // The state we are about to leave behind is the redo target.
        mainVm.redoStack.addLast(currentState.toHistoryEntry())
        if (mainVm.redoStack.size > 50) mainVm.redoStack.removeFirst()
        when (entry) {
            is HistoryEntry.Snapshot -> applyHistoryEntry(entry)
            is HistoryEntry.Diff -> applyDiffForward(invertDiff(entry.diff, currentState))
        }
    }

    fun redo() {
        val entry = mainVm.redoStack.removeLastOrNull() ?: return
        val currentState = mainVm._uiState.value
        mainVm.undoStack.addLast(currentState.toHistoryEntry())
        if (mainVm.undoStack.size > 50) mainVm.undoStack.removeFirst()
        when (entry) {
            is HistoryEntry.Snapshot -> applyHistoryEntry(entry)
            is HistoryEntry.Diff -> applyDiffForward(entry.diff)
        }
    }

    /**
     * Apply a [HistoryEntry] to the current state. The entry is expected to
     * already be in "forward" form relative to the current state — [undo]
     * and [redo] compute the inverse before calling here.
     */
    internal fun applyHistoryEntry(entry: HistoryEntry) {
        when (entry) {
            is HistoryEntry.Snapshot -> {
                mainVm._uiState.update { state ->
                    state.copy(
                        sceneName = entry.sceneName,
                        backgroundColor = entry.backgroundColor,
                        images = entry.images,
                        groups = entry.groups,
                        markupStrokes = entry.markupStrokes,
                        markupTexts = entry.markupTexts,
                        markupSourceMetadata = entry.markupSourceMetadata,
                        markupCompatibilityWarning = entry.markupCompatibilityWarning,
                        markupModeActive = false,
                        draftMarkupStroke = null,
                        markupSessionBaseStrokes = null,
                        markupSessionBaseTexts = null,
                        pendingMarkupTextPosition = null,
                        pendingMarkupTextValue = "",
                        pendingMarkupTextFontSize = 80f,
                        editingMarkupTextId = null,
                        transientDragImageIds = emptySet(),
                        transientDragDelta = Offset.Zero,
                    ).normalizeSelection().clearPosterizePreview()
                        .syncHistoryFlags(mainVm.undoStackSize, mainVm.redoStackSize)
                }
            }
            is HistoryEntry.Diff -> applyDiffForward(entry.diff)
        }
    }

    /**
     * Apply a [SceneDiff] in the forward direction (old -> new). The diff
     * is constructed so the "old" side matches the current state for the
     * fields it sets, and the "new" side carries the target value.
     */
    private fun applyDiffForward(diff: SceneDiff) {
        mainVm._uiState.update { state ->
            when (diff) {
                is SceneDiff.ReplaceImages -> state.copy(
                    images = diff.newImages,
                    transientDragImageIds = emptySet(),
                    transientDragDelta = Offset.Zero,
                )
                is SceneDiff.ReplaceGroups -> state.copy(groups = diff.newGroups)
                is SceneDiff.UpdateImage -> state.copy(
                    images = state.images.map { img ->
                        if (img.id != diff.id) img
                        else img.copy(
                            center = diff.newCenter ?: img.center,
                            displaySize = diff.newDisplaySize ?: img.displaySize,
                            rotationDegrees = diff.newRotation ?: img.rotationDegrees,
                            scale = diff.newScale ?: img.scale,
                            flipX = diff.newFlipX ?: img.flipX,
                            flipY = diff.newFlipY ?: img.flipY,
                            colorAdjust = diff.newColorAdjust ?: img.colorAdjust,
                        )
                    },
                )
                is SceneDiff.AddImage -> state.copy(
                    images = state.images + diff.image,
                )
                is SceneDiff.RemoveImage -> state.copy(
                    images = state.images.filterNot { it.id == diff.image.id },
                )
                is SceneDiff.SetMeta -> state.copy(
                    sceneName = diff.newName ?: state.sceneName,
                    backgroundColor = diff.newBackgroundColor ?: state.backgroundColor,
                )
                is SceneDiff.ReplaceMarkup -> state.copy(
                    markupStrokes = diff.newStrokes,
                    markupTexts = diff.newTexts,
                    markupModeActive = false,
                    draftMarkupStroke = null,
                    markupSessionBaseStrokes = null,
                    markupSessionBaseTexts = null,
                    pendingMarkupTextPosition = null,
                    pendingMarkupTextValue = "",
                    pendingMarkupTextFontSize = 80f,
                    editingMarkupTextId = null,
                )
                is SceneDiff.ChangeSelection -> state.copy(
                    selectedImageId = diff.newSelectedImageId,
                    selectedImageIds = diff.newSelectedImageIds,
                    multiSelectMode = diff.newMultiSelectMode,
                )
            }.normalizeSelection().clearPosterizePreview()
                .syncHistoryFlags(mainVm.undoStackSize, mainVm.redoStackSize)
        }
    }

    private fun currentSelectedIds(): Set<String> {
        val state = mainVm._uiState.value
        return state.selectedImageIds.ifEmpty {
            state.selectedImageId?.let(::setOf).orEmpty()
        }
    }

    private fun selectedIdsFor(fallbackImageId: String): Set<String> {
        val selected = currentSelectedIds()
        return if (fallbackImageId in selected) selected else setOf(fallbackImageId)
    }

    fun resetTransform() {
        val stateBefore = mainVm._uiState.value
        val allSelectedIds = stateBefore.selectedImageIds.ifEmpty {
            stateBefore.selectedImageId?.let(::setOf).orEmpty()
        }
        // V_NOW: lock guard. Resetting a transform reverts rotation
        // / scale / flip / display-size / position — every pose
        // field — which is the broadest possible mutation. A locked
        // image must keep its current pose until the user explicitly
        // unlocks, so reset is a no-op for it.
        val selectedIds = allSelectedIds.filter { id ->
            !stateBefore.isImageLocked(id)
        }
        if (selectedIds.isEmpty()) return
        mainVm.mutateScene {
            copy(
                images = images.map { img ->
                    if (img.id !in selectedIds) img else img.copy(
                        center = img.initialCenter,
                        displaySize = img.initialDisplaySize,
                        rotationDegrees = 0f,
                        scale = 1f,
                        flipX = false,
                        flipY = false,
                        // V_NOW (2026-06-11): clear the non-destructive
                        // crop when resetting transform. The
                        // initialDisplaySize was set at import time
                        // (before any crop), so keeping a cropRect
                        // would cause drawSceneImage to render the
                        // cropped region into the original-aspect
                        // displaySize box, producing a stretched image.
                        cropRect = null,
                    )
                },
            )
        }
    }

    internal fun fitImageToScreen(imageId: String) {
        val currentState = mainVm._uiState.value
        val image = currentState.images.find { it.id == imageId } ?: return
        android.util.Log.d(
            "ExportCanvas",
            "fitImageToScreen imageId=$imageId " +
                "currentFittedId=${currentState.fittedImageId} " +
                "willUnfocus=${currentState.fittedImageId == imageId}",
        )
        // V_NOW (2026-06-10 "全局锁定/冻结模式
        // 双击图片聚焦" fix): reject fit-to-screen
        // ONLY for per-image locked images, NOT for
        // the app-wide [globalImageLock] view-mode.
        // Rationale: fit-to-screen is a pure
        // viewport transform (mutates
        // [canvasScale] / [canvasOffset] /
        // [fittedImageId]; never touches the
        // image's own [SceneImage.locked] / scale
        // / rotation / center). The previous
        // [isImageLocked] gate (which OR's in
        // [globalImageLock]) made double-tap
        // focus silently no-op in global lock
        // mode — the user could see images
        // already fitted in the viewport but
        // couldn't unfocus, and couldn't focus a
        // new image. Per-image lock stays in
        // effect: a doubly-tapped locked image
        // still gets the "exit focus" path below
        // (clear fittedImageId if something else
        // is fitted), then return without
        // focusing it.
        if (image.locked) {
            if (currentState.fittedImageId != null &&
                currentState.fittedImageId != imageId
            ) {
                val previouslyFitted = currentState.fittedImageId
                mainVm._uiState.update { state ->
                    state.copy(
                        canvasScale = state.preFitCanvasScale,
                        canvasOffset = state.preFitCanvasOffset,
                        fittedImageId = null,
                    )
                }
                android.util.Log.d(
                    "ExportCanvas",
                    "fitImageToScreen UNFOCUS_VIA_LOCKED_TAP " +
                        "requestedId=$imageId " +
                        "previouslyFitted=$previouslyFitted",
                )
            }
            return
        }
        if (currentState.fittedImageId == imageId) {
            mainVm._uiState.update { state ->
                state.copy(
                    canvasScale = state.preFitCanvasScale,
                    canvasOffset = state.preFitCanvasOffset,
                    fittedImageId = null,
                )
            }
            return
        }
        val viewport = currentState.canvasViewportSize
        if (viewport.width <= 0f || viewport.height <= 0f) return
        // V_NOW: use rotated AABB footprint instead of simple
        // width/height swap. The old logic only handled 90°
        // multiples (`rotationDegrees % 180f != 0f`), which
        // broke for arbitrary angles (e.g. 45°) where the
        // rotated image's AABB is larger than the simple swap.
        // imageRotatedFootprint computes the correct axis-
        // aligned bounding box for any rotation angle.
        val footprint = imageRotatedFootprint(image)
        val effectiveW = footprint.width
        val effectiveH = footprint.height
        val targetScale = minOf(
            viewport.width / effectiveW,
            viewport.height / effectiveH,
        ).coerceIn(0.15f, 8f)
        val savedScale = currentState.canvasScale
        val savedOffset = currentState.canvasOffset
        mainVm._uiState.update { state ->
            state.copy(
                canvasScale = targetScale,
                canvasOffset = Offset(
                    -image.center.x * targetScale,
                    -image.center.y * targetScale,
                ),
                fittedImageId = imageId,
                preFitCanvasScale = savedScale,
                preFitCanvasOffset = savedOffset,
            )
        }
    }

    fun convertToGrayscale() {
        val selectedIds = currentSelectedIds()
        if (selectedIds.isEmpty()) return
        mainVm.mutateScene {
            copy(
                images = images.map { img ->
                    if (img.id !in selectedIds) img
                    else {
                        val currentSat = img.colorAdjust.saturation
                        val newSat = if (currentSat <= -0.9f) 0f else -1f
                        img.copy(
                            colorAdjust = img.colorAdjust.copy(saturation = newSat),
                            // Posterize is a grayscale-only operation. Switching back to
                            // color also clears the persisted posterize setting.
                            posterizeLevels = if (newSat <= -0.9f) img.posterizeLevels else null,
                        )
                    }
                },
            )
        }

        val after = mainVm._uiState.value
        val allSelectedGrayscale = selectedIds.all { id ->
            after.images.firstOrNull { it.id == id }
                ?.colorAdjust?.saturation?.let { it <= -0.9f } == true
        }
        if (allSelectedGrayscale && selectedIds == after.selectedImageIds) {
            mainVm.openPosterizeControlsForSelection(selectedIds)
        } else {
            mainVm.clearPosterizePreview()
        }
    }

}
