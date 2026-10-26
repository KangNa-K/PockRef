package com.pockref.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.pockref.app.model.AlignMode
import com.pockref.app.model.SceneImage
import com.pockref.app.model.SceneGroup
import com.pockref.app.model.isImageLocked
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlinx.coroutines.flow.update

/**
 * Returns the "footprint" of an image as it actually occupies
 * the scene — i.e. the bounding box of its rotated rectangle.
 *
 * When the image is unrotated (θ=0°) this equals the original
 * `(displaySize.width * scale, displaySize.height * scale)`
 * rectangle. When the image is rotated, the bounding box is
 * larger than the unrotated rectangle: a 100×50 image rotated
 * 45° has an AABB of roughly 106×106 instead of 100×50.
 *
 * Layout / alignment / fit-to-screen / auto-layout must use
 * THIS footprint, not the unrotated one. Otherwise:
 *   - a 45°-rotated long banner would be "fitted to screen"
 *     based on its unrotated (shorter) width, then its
 *     corners would poke outside the viewport edges;
 *   - two rotated images packed into a row would visually
 *     overlap at their corners even though the layout
 *     algorithm thought they were safely separated.
 *
 * The math is the standard rotated-rectangle AABB:
 *   halfW = |cosθ|·w/2 + |sinθ|·h/2
 *   halfH = |sinθ|·w/2 + |cosθ|·h/2
 * with the absolute values handling all four quadrants of θ.
 */
internal fun imageRotatedFootprint(image: SceneImage): RotatedFootprint {
    val w = image.displaySize.width * image.scale
    val h = image.displaySize.height * image.scale
    val theta = Math.toRadians(image.rotationDegrees.toDouble())
    val absCos = kotlin.math.abs(kotlin.math.cos(theta)).toFloat()
    val absSin = kotlin.math.abs(kotlin.math.sin(theta)).toFloat()
    val aabbW = absCos * w + absSin * h
    val aabbH = absSin * w + absCos * h
    return RotatedFootprint(width = aabbW, height = aabbH)
}

/**
 * Base rotated AABB at scale=1.0 — the footprint a freshly
 * imported image would have. Used by [executeAutoLayout] to
 * back-solve the target `scale` from a desired AABB height:
 *   `scale = targetAabbH / baseFootprint.height`
 *
 * [imageRotatedFootprint] includes the current [SceneImage.scale],
 * so dividing by it would produce `scale / oldScale` instead of
 * the absolute scale the image needs. This function avoids that
 * by computing the AABB at scale=1.0.
 */
internal fun imageBaseFootprint(image: SceneImage): RotatedFootprint {
    val w = image.displaySize.width
    val h = image.displaySize.height
    val theta = Math.toRadians(image.rotationDegrees.toDouble())
    val absCos = kotlin.math.abs(kotlin.math.cos(theta)).toFloat()
    val absSin = kotlin.math.abs(kotlin.math.sin(theta)).toFloat()
    return RotatedFootprint(
        width = absCos * w + absSin * h,
        height = absSin * w + absCos * h,
    )
}

internal data class RotatedFootprint(val width: Float, val height: Float)

internal fun alignEdgeAndPackWithoutOverlap(
    selected: List<SceneImage>,
    mode: AlignMode,
    gap: Float,
): List<SceneImage> {
    if (selected.size < 2) return selected

    return when (mode) {
        AlignMode.ALIGN_LEFT -> {
            val refLeft = selected.minOf { img ->
                val fp = imageRotatedFootprint(img)
                img.center.x - fp.width / 2f
            }
            var curTop = selected.minOf { img ->
                val fp = imageRotatedFootprint(img)
                img.center.y - fp.height / 2f
            }
            selected.sortedWith(compareByTopEdge()).map { img ->
                val fp = imageRotatedFootprint(img)
                val result = img.copy(
                    center = Offset(refLeft + fp.width / 2f, curTop + fp.height / 2f),
                )
                curTop += fp.height + gap
                result
            }
        }
        AlignMode.ALIGN_RIGHT -> {
            val refRight = selected.maxOf { img ->
                val fp = imageRotatedFootprint(img)
                img.center.x + fp.width / 2f
            }
            var curTop = selected.minOf { img ->
                val fp = imageRotatedFootprint(img)
                img.center.y - fp.height / 2f
            }
            selected.sortedWith(compareByTopEdge()).map { img ->
                val fp = imageRotatedFootprint(img)
                val result = img.copy(
                    center = Offset(refRight - fp.width / 2f, curTop + fp.height / 2f),
                )
                curTop += fp.height + gap
                result
            }
        }
        AlignMode.ALIGN_TOP -> {
            val refTop = selected.minOf { img ->
                val fp = imageRotatedFootprint(img)
                img.center.y - fp.height / 2f
            }
            var curLeft = selected.minOf { img ->
                val fp = imageRotatedFootprint(img)
                img.center.x - fp.width / 2f
            }
            selected.sortedWith(compareByLeftEdge()).map { img ->
                val fp = imageRotatedFootprint(img)
                val result = img.copy(
                    center = Offset(curLeft + fp.width / 2f, refTop + fp.height / 2f),
                )
                curLeft += fp.width + gap
                result
            }
        }
        AlignMode.ALIGN_BOTTOM -> {
            val refBottom = selected.maxOf { img ->
                val fp = imageRotatedFootprint(img)
                img.center.y + fp.height / 2f
            }
            var curLeft = selected.minOf { img ->
                val fp = imageRotatedFootprint(img)
                img.center.x - fp.width / 2f
            }
            selected.sortedWith(compareByLeftEdge()).map { img ->
                val fp = imageRotatedFootprint(img)
                val result = img.copy(
                    center = Offset(curLeft + fp.width / 2f, refBottom - fp.height / 2f),
                )
                curLeft += fp.width + gap
                result
            }
        }
        else -> selected
    }
}

/**
 * Arrange group members with the same row-scan layout used by iOS when a
 * dragged image is attached to a group. The returned positions are centered
 * around the origin; the caller can translate the complete layout back to
 * the group's original center.
 *
 * Keeping this separate from [PockRefViewModelBatch.executeAutoLayout] is
 * intentional: dropping into a group must not resize the members or create a
 * second undo entry for the surrounding canvas.
 */
internal fun layoutGroupMembersForDrag(
    images: List<SceneImage>,
    maxRowWidth: Float,
    gap: Float = 16f,
): List<SceneImage> {
    if (images.isEmpty()) return emptyList()

    val safeGap = gap.coerceAtLeast(0f)
    val footprints = images.map(::imageRotatedFootprint)
    val availableWidth = maxOf(
        1f,
        maxRowWidth,
        footprints.maxOf { it.width },
    )

    val rows = mutableListOf<List<Int>>()
    var current = mutableListOf<Int>()
    var currentWidth = 0f
    footprints.forEachIndexed { index, footprint ->
        val nextWidth = if (current.isEmpty()) {
            footprint.width
        } else {
            currentWidth + safeGap + footprint.width
        }
        if (current.isNotEmpty() && nextWidth > availableWidth) {
            rows += current
            current = mutableListOf(index)
            currentWidth = footprint.width
        } else {
            current += index
            currentWidth = nextWidth
        }
    }
    if (current.isNotEmpty()) rows += current

    val rowHeights = rows.map { row -> row.maxOf { footprints[it].height } }
    val rowWidths = rows.map { row ->
        row.sumOf { footprints[it].width.toDouble() }.toFloat() +
            safeGap * (row.size - 1)
    }
    val totalHeight = rowHeights.sum() + safeGap * (rows.size - 1)
    var cursorY = -totalHeight / 2f
    val result = ArrayList<SceneImage>(images.size)

    rows.forEachIndexed { rowIndex, row ->
        val rowHeight = rowHeights[rowIndex]
        var cursorX = -rowWidths[rowIndex] / 2f
        row.forEach { imageIndex ->
            val footprint = footprints[imageIndex]
            val center = Offset(
                x = cursorX + footprint.width / 2f,
                y = cursorY + (rowHeight - footprint.height) / 2f + footprint.height / 2f,
            )
            result += images[imageIndex].copy(center = center)
            cursorX += footprint.width + safeGap
        }
        cursorY += rowHeight + safeGap
    }
    return result
}

private fun compareByTopEdge(): Comparator<SceneImage> =
    compareBy<SceneImage>(
        {
            val fp = imageRotatedFootprint(it)
            it.center.y - fp.height / 2f
        },
        { it.center.y },
        { it.center.x },
        { it.zIndex },
        { it.id },
    )

private fun compareByLeftEdge(): Comparator<SceneImage> =
    compareBy<SceneImage>(
        {
            val fp = imageRotatedFootprint(it)
            it.center.x - fp.width / 2f
        },
        { it.center.x },
        { it.center.y },
        { it.zIndex },
        { it.id },
    )

/**
 * Compute the axis-aligned bounding box (in scene coordinates)
 * of a list of images, using their rotated AABBs. Returns null
 * if the list is empty. The returned rect covers [minX, minY] to
 * [maxX, maxY] inclusive.
 */
internal fun imagesBounds(images: List<SceneImage>): android.graphics.RectF? {
    if (images.isEmpty()) return null
    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE
    var maxY = -Float.MAX_VALUE
    for (img in images) {
        val fp = imageRotatedFootprint(img)
        val halfW = fp.width / 2f
        val halfH = fp.height / 2f
        val left = img.center.x - halfW
        val right = img.center.x + halfW
        val top = img.center.y - halfH
        val bottom = img.center.y + halfH
        if (left < minX) minX = left
        if (right > maxX) maxX = right
        if (top < minY) minY = top
        if (bottom > maxY) maxY = bottom
    }
    return android.graphics.RectF(minX, minY, maxX, maxY)
}

class PockRefViewModelBatch(private val mainVm: PockRefViewModel) {

    /**
     * Lay out only the images from the current import batch. Existing canvas
     * content stays put, while the new batch is kept compact and placed next
     * to it. This is the Android equivalent of iOS `layoutImported`, and is
     * especially important when the batch is being attached to an edited
     * group: the new members must not remain stacked at the import origin.
     */
    fun executeImportedAutoLayout(importedIds: Set<String>) {
        if (importedIds.isEmpty()) return
        val state = mainVm._uiState.value
        val imported = state.images.filter { it.id in importedIds }.sortedBy { it.zIndex }
        if (imported.isEmpty()) return
        val laidOut = alignEdgeAndPackWithoutOverlap(
            selected = imported,
            mode = AlignMode.ALIGN_TOP,
            gap = state.sceneDefaultSpacing.width.coerceAtLeast(2f),
        )
        val existing = state.images.filter { it.id !in importedIds }
        val existingBounds = imagesBounds(existing)
        val importedBounds = imagesBounds(laidOut)
        val offset = if (existingBounds != null && importedBounds != null) {
            Offset(
                existingBounds.right + state.sceneDefaultSpacing.width.coerceAtLeast(8f) - importedBounds.left,
                existingBounds.centerY() - importedBounds.centerY(),
            )
        } else {
            Offset.Zero
        }
        val placed = laidOut.associate { image ->
            image.id to image.copy(center = image.center + offset)
        }
        mainVm._uiState.update { current ->
            current.copy(images = current.images.map { placed[it.id] ?: it }).normalizeSelection()
        }
    }

    fun resetView() {
        mainVm._uiState.update { it.copy(canvasOffset = Offset.Zero, canvasScale = 1f, fittedImageId = null) }
    }

    fun fitToScreen() {
        fitToScreen(mainVm._uiState.value.images)
    }

    private fun fitToScreen(targetImages: List<com.pockref.app.model.SceneImage>) {
        val state = mainVm._uiState.value
        val images = targetImages
        if (images.isEmpty()) {
            resetView()
            return
        }
        val vpW = state.canvasViewportSize.width
        val vpH = state.canvasViewportSize.height
        if (vpW <= 0f || vpH <= 0f) {
            resetView()
            return
        }
        // V5: padding bumped from 10f to 2f to match
        // [PockRefViewModelBatch.executeAutoLayout]'s
        // edgePadding. The two flows (auto-layout and
        // fit-to-screen) used to disagree: auto-layout
        // put 2f of breathing room at every canvas edge,
        // fit-to-screen put 10f. Now they're aligned —
        // hitting the auto-layout button right after
        // double-tap-empty doesn't change the visible
        // padding, which is what the user asked for
        // ("四周保留宽度要与一键排版保留的边距一致").
        val padding = 2f
        // V12: scene bbox derived from each image's
        // rotated AABB (not the unrotated rectangle).
        // A rotated image's corners stick out beyond
        // its unrotated (w, h) box by up to
        // (|cosθ|·w/2 + |sinθ|·h/2). Using the unrotated
        // box would underestimate the scene size for
        // any image with rotationDegrees ≠ 0° — the
        // fit-to-screen scale would be too aggressive
        // and rotated corners would clip past the
        // viewport edge.
        val left = images.minOf { img ->
            val fp = imageRotatedFootprint(img)
            img.center.x - fp.width / 2f
        }
        val right = images.maxOf { img ->
            val fp = imageRotatedFootprint(img)
            img.center.x + fp.width / 2f
        }
        val top = images.minOf { img ->
            val fp = imageRotatedFootprint(img)
            img.center.y - fp.height / 2f
        }
        val bottom = images.maxOf { img ->
            val fp = imageRotatedFootprint(img)
            img.center.y + fp.height / 2f
        }
        val contentW = max(right - left, 1f)
        val contentH = max(bottom - top, 1f)
        val availW = vpW - padding * 2f
        val availH = vpH - padding * 2f
        if (availW <= 0f || availH <= 0f) {
            resetView()
            return
        }
        val scaleX = availW / contentW
        val scaleY = availH / contentH
        // V5: clamp removed the upper bound (3f) and
        // added a different policy: when the content is
        // smaller than the available area (i.e. the
        // user is fitting a small image into a big
        // window), DON'T zoom up — the "fit" scale
        // would otherwise be like 5x or 10x, blowing
        // up a tiny image to fill the whole screen.
        // The user's spec was: "若画布尺寸小于窗口尺寸,
        // 则以合适比例显示并最大化利用窗口空间".
        // "Suitable ratio" = up to but not exceeding
        // 1.0x (natural zoom); "maximize window space"
        // for small content is interpreted as "use
        // the natural 1:1 zoom and center it" rather
        // than upscaling.
        //
        // V_NOW (2026-06-29, 1.3.x): the downscale floor
        // was previously [0.15f] (hard-coded literal).
        // That floor was too high once a user scattered
        // 20+ reference images around the canvas — the
        // bbox often spans 10×+ the viewport, so the
        // geometric fit scale lands around 0.03–0.06, and
        // the 0.15 cap caused double-tap-empty to crop
        // off the outer ring of references instead of
        // fitting the whole scene. Replaced with
        // [MIN_CANVAS_SCALE] (= 0.02f) so fit-to-screen
        // and pinch-zoom share the same shrink floor.
        val fitScale = min(scaleX, scaleY)
        val targetScale = if (fitScale >= 1f) 1f else fitScale.coerceAtLeast(MIN_CANVAS_SCALE)
        val centerX = (left + right) / 2f
        val centerY = (top + bottom) / 2f
        mainVm._uiState.update {
            it.copy(
                canvasScale = targetScale,
                canvasOffset = Offset(-centerX * targetScale, -centerY * targetScale),
                fittedImageId = null,
            )
        }
    }

    internal fun batchAlign(mode: AlignMode) {
        val stateBefore = mainVm._uiState.value
        val selectedIds = stateBefore.selectedImageIds.filterTo(mutableSetOf()) { id ->
            !stateBefore.isImageLocked(id)
        }
        if (selectedIds.size < 2) {
            return
        }
        val selected = stateBefore.images.filter { it.id in selectedIds }
        mainVm.mutateScene {
            val gap = 15f
            val aligned = when (mode) {
                AlignMode.ALIGN_WIDTH -> {
                    // V12: align by rotated-AABB width, not by
                    // the unrotated `displaySize.width * scale`.
                    // A 100×50 image rotated 45° has AABB width
                    // 106, not 100 — the old code would have
                    // packed it on a 100-wide cell, with its
                    // corner poking out into the neighbor's
                    // space.
                    val minWidth = selected.minOf { imageRotatedFootprint(it).width }
                    val startY = selected.minOf { img ->
                        val fp = imageRotatedFootprint(img)
                        img.center.y - fp.height / 2f
                    }
                    var curX = selected.minOf { img ->
                        val fp = imageRotatedFootprint(img)
                        img.center.x - fp.width / 2f
                    }
                    selected.map { img ->
                        val fp = imageRotatedFootprint(img)
                        // Solve for `scale` so the new AABB
                        // width equals `minWidth`. Using the
                        // width axis is symmetric with the
                        // height variant below.
                        val newScale = if (fp.width > 0f) (img.scale * minWidth / fp.width) else img.scale
                        val newFp = imageRotatedFootprint(img.copy(scale = newScale))
                        val result = img.copy(
                            scale = newScale,
                            center = Offset(curX + minWidth / 2f, startY + newFp.height / 2f),
                        )
                        curX += minWidth + gap
                        result
                    }
                }
                AlignMode.ALIGN_HEIGHT -> {
                    // V12: same as ALIGN_WIDTH but on the
                    // height axis.
                    val minHeight = selected.minOf { imageRotatedFootprint(it).height }
                    val startX = selected.minOf { img ->
                        val fp = imageRotatedFootprint(img)
                        img.center.x - fp.width / 2f
                    }
                    var curY = selected.minOf { img ->
                        val fp = imageRotatedFootprint(img)
                        img.center.y - fp.height / 2f
                    }
                    selected.map { img ->
                        val fp = imageRotatedFootprint(img)
                        val newScale = if (fp.height > 0f) (img.scale * minHeight / fp.height) else img.scale
                        val newFp = imageRotatedFootprint(img.copy(scale = newScale))
                        val result = img.copy(
                            scale = newScale,
                            center = Offset(startX + newFp.width / 2f, curY + minHeight / 2f),
                        )
                        curY += minHeight + gap
                        result
                    }
                }
                AlignMode.ALIGN_MAX -> {
                    // V12: align by max AABB-side length.
                    val maxDim = selected.maxOf {
                        val fp = imageRotatedFootprint(it)
                        maxOf(fp.width, fp.height)
                    }
                    val startX = selected.minOf { img ->
                        val fp = imageRotatedFootprint(img)
                        img.center.x - fp.width / 2f
                    }
                    var curY = selected.minOf { img ->
                        val fp = imageRotatedFootprint(img)
                        img.center.y - fp.height / 2f
                    }
                    selected.map { img ->
                        val fp = imageRotatedFootprint(img)
                        val currentMax = maxOf(fp.width, fp.height)
                        val newScale = img.scale * (maxDim / currentMax)
                        val newFp = imageRotatedFootprint(img.copy(scale = newScale))
                        val result = img.copy(
                            scale = newScale.coerceIn(0.2f, 5f),
                            center = Offset(startX + newFp.width / 2f, curY + newFp.height / 2f),
                        )
                        curY += newFp.height + gap
                        result
                    }
                }
                AlignMode.ALIGN_MIN -> {
                    // V12: align by min AABB-side length.
                    val minDim = selected.minOf {
                        val fp = imageRotatedFootprint(it)
                        maxOf(fp.width, fp.height)
                    }
                    val startX = selected.minOf { img ->
                        val fp = imageRotatedFootprint(img)
                        img.center.x - fp.width / 2f
                    }
                    var curY = selected.minOf { img ->
                        val fp = imageRotatedFootprint(img)
                        img.center.y - fp.height / 2f
                    }
                    selected.map { img ->
                        val fp = imageRotatedFootprint(img)
                        val currentMax = maxOf(fp.width, fp.height)
                        val newScale = img.scale * (minDim / currentMax)
                        val newFp = imageRotatedFootprint(img.copy(scale = newScale))
                        val result = img.copy(
                            scale = newScale.coerceIn(0.2f, 5f),
                            center = Offset(startX + newFp.width / 2f, curY + newFp.height / 2f),
                        )
                        curY += newFp.height + gap
                        result
                    }
                }
                // V12+: position-alignment modes. Each one
                // snaps the requested visual edge to the
                // reference edge, then packs along the other
                // axis in the selected images' current order.
                // Keeping the old "same edge" behavior alone
                // could stack unlike-sized images on top of
                // each other; packing keeps the action useful
                // as a layout command instead of a collision
                // command.
                //
                // Edges are read from the rotated AABB so
                // the alignment line respects the actual
                // visual edge (including corners sticking
                // out due to rotation), not the unrotated
                // rectangle.
                AlignMode.ALIGN_LEFT,
                AlignMode.ALIGN_RIGHT,
                AlignMode.ALIGN_TOP,
                AlignMode.ALIGN_BOTTOM -> alignEdgeAndPackWithoutOverlap(selected, mode, gap)
            }
            val alignedMap = aligned.associateBy { it.id }
            copy(
                images = images.map { img -> alignedMap[img.id] ?: img },
            )
        }
    }

    internal fun batchScale(scaleFactor: Float) {
        val stateBefore = mainVm._uiState.value
        val selectedIds = stateBefore.selectedImageIds.filterTo(mutableSetOf()) { id ->
            !stateBefore.isImageLocked(id)
        }
        if (selectedIds.isEmpty()) return
        val selected = stateBefore.images.filter { it.id in selectedIds }
        val cx = selected.map { it.center.x }.average().toFloat()
        val cy = selected.map { it.center.y }.average().toFloat()
        val groupCenter = Offset(cx, cy)
        mainVm.mutateScene {
            copy(
                images = images.map { img ->
                    if (img.id !in selectedIds) img else {
                        val newScale = (img.scale * scaleFactor).coerceIn(0.2f, 5f)
                        val dx = img.center.x - groupCenter.x
                        val dy = img.center.y - groupCenter.y
                        val newCenter = Offset(
                            groupCenter.x + dx * (newScale / img.scale),
                            groupCenter.y + dy * (newScale / img.scale),
                        )
                        img.copy(scale = newScale, center = newCenter)
                    }
                },
            )
        }
    }

    /**
     * V_NOW (4-corner scale handles, MULTI/BOX path):
     * apply an ABSOLUTE scale FACTOR to every
     * selected image, pivoting the group around
     * [worldCenter]. Each member keeps its RELATIVE
     * size ratio (its scale is multiplied by the
     * same factor), only the group as a whole grows
     * or shrinks. The world center is the new group
     * AABB center; the group AABB tracks the user's
     * finger.
     *
     * Distinct from the existing
     * [PockRefViewModelBatch.batchScale] (which is a
     * RELATIVE per-frame multiplier that stacks
     * frame-over-frame and would let numerical drift
     * accumulate over a long group drag). The corner
     * drag wants a fixed reference frame: the
     * down-time group AABB diagonal. The
     * [newScaleFactor] is always a one-line ratio
     * against that baseline
     * (`newDiag / initialDiag`), so the absolute form
     * has no drift.
     *
     * Lock guard: per-image + global lock filter, same
     * as [PockRefViewModelBatch.batchScale] and the
     * per-image [PockRefViewModelTransform.setSelectedImageScale]
     * path. Locked members are excluded from the
     * scale application; the group as a whole still
     * re-centers around [worldCenter], but a locked
     * member's pose is preserved.
     *
     * The [worldCenter] is supplied by the pointer
     * handler as a pre-converted world-space point
     * (the screen-space midpoint of
     * (move-handle, anchor), inverse-transformed via
     * the canvas matrix). The reducer just writes
     * the field — it does NOT recompute it from the
     * group AABB because the user may have moved
     * the move-handle past the group AABB and the
     * "where the group ends up" position is whatever
     * the user's fingers say, not a fit-to-AABB
     * centroid.
     *
     * Scale clamp matches the per-image path
     * (`[0.1, 8.0]`) so a multi-select group and a
     * single-image select feel identical at the
     * boundary — the user can pull a group all the
     * way to 10% of original, same as a single image.
     */
    internal fun setSelectedImagesScale(
        // V_NOW (2026-06-10 group corner-handle
        // anchor fix v2): the previous geometry was
        //   newCenter_i = worldCenter
        //     + (initialCenter_i - initialGroupCenter) * factor
        // which keeps the group AABB's CENTER pinned to
        // the worldCenter midpoint — but for rotated
        // members (or any member whose center is offset
        // from the group AABB center), the union of
        // per-image rotated footprints is NOT exactly
        // `newGroupCenter ± (initialAABB/2) * factor`,
        // so the rendered group AABB drifts in the
        // direction perpendicular to the drag diagonal,
        // and the diagonal anchor "walks" across the
        // canvas as the user drags. The corrected
        // geometry is "the group AABB grows around the
        // pinned anchor corner A" — expressed as a
        // PER-AABB operation: each image's rotated
        // footprint AABB is rescaled by [factor] around
        // the SAME pivot (A) that the group AABB uses,
        // so
        //   - group AABB = union(per-image AABBs) grows
        //     by exactly [factor] around A → its 4
        //     corners stay correct
        //   - per-image center is the new AABB's center
        //   - per-image scale is `initialScale * factor`
        //     (matches the new AABB's footprint)
        // [factor] is the diagonal length ratio
        // `|N - A| / |M - A|` computed in world space
        // (|M - A| is the down-time diagonal between
        // the move corner and the anchor corner).
        factor: Float,
        initialImageAabbs: Map<String, Rect>,
        initialScales: Map<String, Float>,
        // V_NOW: the per-hitIdx pivot for the
        // group AABB. `pivotX` is the X coord of the
        // anchor corner A, `pivotY` is the Y coord.
        // For hitIdx=3 (user grabbed BR), pivot =
        // (groupAabb.left, groupAabb.top) (A is TL).
        // For hitIdx=0 (user grabbed TL), pivot =
        // (groupAabb.right, groupAabb.bottom) (A is
        // BR). The formula
        //   newEdge = pivot + (initialEdge - pivot) * factor
        // applied to all 4 edges of the group AABB
        // AND to the 4 edges of every member AABB
        // keeps the rendered group AABB perfectly
        // pinned at A with the rest scaled by factor.
        pivotX: Float,
        pivotY: Float,
    ) {
        val stateBefore = mainVm._uiState.value
        val targetIds = stateBefore.selectedImageIds.ifEmpty {
            stateBefore.selectedImageId?.let(::setOf).orEmpty()
        }
        if (targetIds.isEmpty()) return
        val transformableIds = targetIds.filterTo(linkedSetOf()) { id ->
            !stateBefore.isImageLocked(id)
        }
        if (transformableIds.isEmpty()) return
        // V_NOW (2026-06-11, "小圆点缩放撤销/重做逐帧" fix):
        // changed from [mutateScene] to [updateSceneForGesture].
        // [mutateScene] pushes a diff on EVERY call, so a
        // multi-frame corner drag would fill the undo stack
        // with dozens of intermediate diffs — one per move
        // event. [updateSceneForGesture] only captures the
        // "before" state on the first frame and defers the
        // single diff push until [EndSceneGesture], so the
        // entire corner-drag gesture produces exactly one
        // undo entry (matching the image-drag behavior).
        mainVm.updateSceneForGesture {
            copy(
                images = images.map { img ->
                    if (img.id !in transformableIds) img else {
                        val initialScale = initialScales[img.id] ?: img.scale
                        val newScale = (initialScale * factor)
                            .coerceIn(0.1f, 8.0f)
                        // V_NOW (2026-06-11 "放大时对角锚点漂移" fix):
                        // when [newScale] is clamped
                        // (e.g. initialScale * factor > 8.0),
                        // the AABB must be scaled by the
                        // CLAMPED factor [newScale /
                        // initialScale], not the raw
                        // [factor]. Otherwise the center
                        // overshoots the actual rendered
                        // footprint and the diagonal
                        // anchor visibly drifts — the
                        // drift grows with |factor -
                        // effectiveFactor|, which is why
                        // it was only noticeable when
                        // zooming IN (factor > 1) and not
                        // when zooming out (factor ≈ 1,
                        // clamp rarely fires).
                        val effectiveFactor = newScale / initialScale
                        val initialAabb = initialImageAabbs[img.id] ?: Rect(
                            left = img.center.x - 1f,
                            top = img.center.y - 1f,
                            right = img.center.x + 1f,
                            bottom = img.center.y + 1f,
                        )
                        val newLeft = pivotX + (initialAabb.left - pivotX) * effectiveFactor
                        val newTop = pivotY + (initialAabb.top - pivotY) * effectiveFactor
                        val newRight = pivotX + (initialAabb.right - pivotX) * effectiveFactor
                        val newBottom = pivotY + (initialAabb.bottom - pivotY) * effectiveFactor
                        val newCenter = Offset(
                            (newLeft + newRight) / 2f,
                            (newTop + newBottom) / 2f,
                        )
                        img.copy(
                            scale = newScale,
                            center = newCenter,
                        )
                    }
                },
            )
        }
    }

    /**
     * V_NOW (2026-06-11 "旋转图片角点缩放" fix):
     * single-image corner-drag using the correct
     * rotated geometry. [newCenter] is computed by
     * [computeCornerDragResult] which inverts the
     * image's rotation transform to pin the anchor
     * corner, so this works for ANY rotation angle.
     */
    internal fun setSingleImageCornerScale(
        imageId: String,
        newScale: Float,
        newCenter: Offset,
    ) {
        mainVm.updateSceneForGesture {
            copy(
                images = images.map { img ->
                    if (img.id != imageId) img else {
                        img.copy(
                            scale = newScale.coerceIn(0.1f, 8.0f),
                            center = newCenter,
                        )
                    }
                },
            )
        }
    }

    fun batchFlipHorizontal() {
        val selectedIds = mainVm._uiState.value.selectedImageIds
        if (selectedIds.isEmpty()) return
        mainVm.mutateScene {
            copy(
                images = images.map { img ->
                    if (img.id !in selectedIds) img else img.copy(flipX = !img.flipX)
                },
            )
        }
    }

    fun executeAutoLayout() {
        val currentState = mainVm._uiState.value
        val allImages = currentState.images
        if (allImages.isEmpty()) {
            return
        }
        val vpW = currentState.canvasViewportSize.width
        val vpH = currentState.canvasViewportSize.height
        if (vpW <= 0f || vpH <= 0f) {
            return
        }

        // A single selected group (or its editor) means arrange its contents,
        // not resize the unchanged group as one giant image.
        layoutSelectedGroupMembers(currentState)?.let { laidOut ->
            if (laidOut != allImages) {
                mainVm.mutateScene { copy(images = laidOut) }
                fitToScreen()
            }
            return
        }

        // Treat each native group as one layout unit. Moving the members as
        // independent images would visually split a group during auto-layout.
        if (currentState.groups.isNotEmpty() && executeGroupAwareAutoLayout(currentState)) {
            return
        }

        // In multi-select / box-select mode with ≥2 selected images,
        // layout only the selected images within the viewport.
        // Otherwise, layout all images.
        val selectedIds = currentState.selectedImageIds
        val isSelectionMode = currentState.multiSelectMode || currentState.boxSelectMode
        val images = if (isSelectionMode && selectedIds.size >= 2) {
            allImages.filter { it.id in selectedIds }
        } else {
            allImages
        }
        if (images.isEmpty()) return
        // Grid gap + canvas edge padding, in SCENE / WORLD units.
        // Picked at 24f so the breathing room is visible at the
        // default ~1.0 canvasScale; on a 2000px wide scene that's
        // ~1.2% of the viewport, which feels right next to a
        // snap threshold of 15px screen (== 15 / canvasScale
        // world units, so at scale=1.0 the snap engages when the
        // dragged image is within 15 world-units of a target edge
        // — about 0.6× the grid gap, so an image can comfortably
        // drop between two auto-laid images without immediately
        // snapping onto the neighbor).
        // V3: the user wanted a TIGHTER grid. V2 was
        // 24f for both which felt "too much air" between
        // images. 2f is "almost touching" — the user can
        // still see the seam between two images but the
        // packing looks dense. If we go to 0f the images
        // literally share edges and the auto-layout
        // doesn't visually separate them.
        val edgePadding = 2f
        val gap = 2f
        val availW = vpW - edgePadding * 2f
        val availH = vpH - edgePadding * 2f
        val sorted = images.sortedBy { it.zIndex }
        val n = sorted.size

        if (n == 1) {
            val img = sorted[0]
            // Scale off the base rotated AABB (scale=1.0) so the
            // result is an absolute scale, not relative to the
            // image's current scale.
            val baseFp = imageBaseFootprint(img)
            val scaleX = if (baseFp.width > 0f) availW / baseFp.width else 1f
            val scaleY = if (baseFp.height > 0f) availH / baseFp.height else 1f
            val scale = min(scaleX, scaleY)
            // When in selection mode, offset the single image below
            // non-selected images to avoid overlap.
            val singleOffset = if (isSelectionMode && selectedIds.size >= 2) {
                val nonSelected = allImages.filter { it.id !in selectedIds }
                val existingBounds = imagesBounds(nonSelected)
                val newFp = imageBaseFootprint(img)
                val newHalfH = newFp.height * scale / 2f
                if (existingBounds != null) {
                    Offset(0f, existingBounds.bottom + gap + newHalfH)
                } else {
                    Offset.Zero
                }
            } else {
                Offset.Zero
            }
            mainVm.mutateScene {
                copy(
                    images = images.map {
                        if (it.id != img.id) it
                        else it.copy(center = singleOffset, scale = scale)
                    },
                )
            }
            fitToScreen()
            return
        }

        // V12: use each image's rotated AABB (not its unrotated
        // rectangle) as the layout "footprint" — same call as
        // [fitToScreen] so the two flows agree on what an image
        // visually occupies. A 100×50 image at 45° has a 106×106
        // footprint; a 100×50 image at 0° has 100×50. The
        // algorithm packs rotated images by their AABB so their
        // corners never overlap a neighbor's AABB (which would
        // mean a visible collision in screen space).
        val footprints = sorted.map { imageRotatedFootprint(it) }
        // Base footprints at scale=1.0, used to back-solve the
        // target scale from the desired AABB height. [footprints]
        // includes each image's current [SceneImage.scale]; dividing
        // the target AABB by that would yield `newScale / oldScale`,
        // not the absolute scale the image needs.
        val baseFootprints = sorted.map { imageBaseFootprint(it) }
        // Aspect ratio of the AABB, not the unrotated rect.
        val aspects = footprints.map { fp ->
            if (fp.height > 0f) fp.width / fp.height else 1f
        }
        val totalAspect = aspects.sum()

        val targetRowHeight = if (totalAspect > 0f) {
            sqrt(availH * availW / totalAspect).coerceIn(availH / n, availH * 0.8f)
        } else {
            availH / ceil(sqrt(n.toFloat()))
        }

        data class RowItem(val imageIndex: Int, val aspect: Float)
        data class Row(val items: List<RowItem>, val height: Float)

        val rows = mutableListOf<Row>()
        var idx = 0
        while (idx < n) {
            val rowItems = mutableListOf<RowItem>()
            var totalRowAspect = 0f
            while (idx < n) {
                val aspect = aspects[idx]
                val potentialTotalAspect = totalRowAspect + aspect
                val potentialWidth = potentialTotalAspect * targetRowHeight + rowItems.size * gap
                if (rowItems.isNotEmpty() && potentialWidth > availW) {
                    break
                }
                rowItems.add(RowItem(idx, aspect))
                totalRowAspect += aspect
                idx++
            }
            val rowHeight: Float = when {
                idx >= n -> targetRowHeight
                rowItems.size == 1 -> min(targetRowHeight, availW / rowItems[0].aspect)
                else -> {
                    val justified = (availW - (rowItems.size - 1) * gap) / totalRowAspect
                    justified.coerceAtMost(availH * 0.8f)
                }
            }
            rows.add(Row(rowItems, rowHeight))
        }

        val totalGridH = rows.sumOf { it.height.toDouble() }.toFloat() + (rows.size - 1) * gap
        val globalScale = if (totalGridH > availH) availH / totalGridH else 1f

        // When laying out only selected images (multi-select mode),
        // offset the grid so it doesn't overlap with non-selected
        // images. Compute the AABB of non-selected images and place
        // the grid below them with a gap.
        val offsetX: Float
        val offsetY: Float
        if (isSelectionMode && selectedIds.size >= 2) {
            val nonSelected = allImages.filter { it.id !in selectedIds }
            val existingBounds = imagesBounds(nonSelected)
            if (existingBounds != null) {
                // Place the grid below the existing images' AABB.
                val scaledGridH = totalGridH * globalScale
                offsetY = existingBounds.bottom + gap + scaledGridH / 2f
                offsetX = 0f
            } else {
                offsetX = 0f
                offsetY = -totalGridH * globalScale / 2f
            }
        } else {
            offsetX = 0f
            offsetY = -totalGridH * globalScale / 2f
        }

        val result = mutableListOf<com.pockref.app.model.SceneImage>()
        var curY = offsetY
        for (row in rows) {
            val scaledRowH = row.height * globalScale
            val scaledGap = gap * globalScale
            val rowWidth = row.items.sumOf { it.aspect.toDouble() * row.height }.toFloat() * globalScale + (row.items.size - 1) * scaledGap
            var curX = offsetX - rowWidth / 2f
            for (item in row.items) {
                val img = sorted[item.imageIndex]
                val baseFp = baseFootprints[item.imageIndex]
                // Target AABB dimensions for this cell.
                val newAabbW = item.aspect * row.height * globalScale
                val newAabbH = row.height * globalScale
                // Back-solve the scene-space `scale` from the
                // target AABB height using the BASE footprint
                // (scale=1.0).  aabbH = scale·baseHeight, so
                // scale = aabbH / baseHeight. Using [footprints]
                // (which includes the old scale) would produce
                // `newScale / oldScale` instead of the absolute
                // scale — the image would be too small if it was
                // previously scaled up, or too large if scaled
                // down.
                val scale = if (baseFp.height > 0f) newAabbH / baseFp.height else 1f
                val center = Offset(curX + newAabbW / 2f, curY + newAabbH / 2f)
                result.add(img.copy(center = center, scale = scale))
                curX += newAabbW + scaledGap
            }
            curY += scaledRowH + scaledGap
        }
        val laidOutMap = result.associateBy { it.id }
        mainVm.mutateScene {
            copy(
                images = allImages.map { img -> laidOutMap[img.id] ?: img },
            )
        }
        // Always fit all images to screen so the user can see
        // the complete layout including non-selected images.
        fitToScreen()
    }

    private fun executeGroupAwareAutoLayout(state: com.pockref.app.model.PockRefUiState): Boolean {
        val selectedIds = state.selectedImageIds
        val selectionOnly = (state.multiSelectMode || state.boxSelectMode) && selectedIds.size >= 2
        val sortedImages = state.images.sortedBy { it.zIndex }
        val selectedGroupIds = if (selectionOnly) {
            state.groups.filter { group -> group.imageIds.any(selectedIds::contains) }.mapTo(mutableSetOf()) { it.id }
        } else {
            emptySet()
        }
        val selectedUnitImageIds = if (selectionOnly) {
            selectedGroupIds.flatMap { groupId ->
                state.groups.firstOrNull { it.id == groupId }?.imageIds.orEmpty()
            }.toSet() + selectedIds
        } else {
            emptySet()
        }

        data class LayoutUnit(
            val group: SceneGroup?,
            val members: List<SceneImage>,
            val bounds: android.graphics.RectF,
            val zIndex: Int,
        )

        val units = mutableListOf<LayoutUnit>()
        val groupedIds = mutableSetOf<String>()
        state.groups.sortedBy { group ->
            group.imageIds.mapNotNull { id -> state.images.firstOrNull { it.id == id }?.zIndex }.minOrNull()
                ?: Int.MAX_VALUE
        }.forEach { group ->
            val members = sortedImages.filter { it.id in group.imageIds }
            val included = !selectionOnly || group.id in selectedGroupIds
            val movable = !group.positionLocked && members.isNotEmpty() && members.none { it.locked }
            val bounds = group.canvasBounds(members, GROUP_CANVAS_BOUNDS_PADDING)
            if (included && movable && bounds != null) {
                units += LayoutUnit(group, members, bounds, members.maxOf { it.zIndex })
                groupedIds += members.map { it.id }
            } else if (included) {
                // Do not later re-introduce these members as standalone units.
                groupedIds += members.map { it.id }
            }
        }
        sortedImages.filter { image ->
            image.id !in groupedIds &&
                (!selectionOnly || image.id in selectedUnitImageIds) &&
                !image.locked
        }.forEach { image ->
            val bounds = imagesBounds(listOf(image)) ?: return@forEach
            units += LayoutUnit(null, listOf(image), bounds, image.zIndex)
        }
        // The caller only enters this path when groups exist. If every
        // layout unit is locked, report the group-aware path as handled so
        // the legacy fallback cannot move locked group members.
        if (units.isEmpty()) return true
        units.sortBy { it.zIndex }

        val gap = 2f
        val edgePadding = 2f
        val availableWidth = (state.canvasViewportSize.width - edgePadding * 2f).coerceAtLeast(1f)
        val availableHeight = (state.canvasViewportSize.height - edgePadding * 2f).coerceAtLeast(1f)
        val columns = ceil(sqrt(units.size.toFloat())).toInt().coerceAtLeast(1)
        val rows = ceil(units.size.toFloat() / columns).toInt().coerceAtLeast(1)
        val cellWidth = ((availableWidth - gap * (columns - 1)) / columns).coerceAtLeast(1f)
        val cellHeight = ((availableHeight - gap * (rows - 1)) / rows).coerceAtLeast(1f)
        val originX = -(columns * cellWidth + (columns - 1) * gap) / 2f
        val originY = -(rows * cellHeight + (rows - 1) * gap) / 2f
        val replacements = mutableMapOf<String, SceneImage>()

        units.forEachIndexed { index, unit ->
            val col = index % columns
            val row = index / columns
            val targetCenter = Offset(
                originX + col * (cellWidth + gap) + cellWidth / 2f,
                originY + row * (cellHeight + gap) + cellHeight / 2f,
            )
            val factor = min(
                cellWidth / unit.bounds.width().coerceAtLeast(1f),
                cellHeight / unit.bounds.height().coerceAtLeast(1f),
            )
            val originalCenter = Offset(unit.bounds.centerX(), unit.bounds.centerY())
            unit.members.forEach { image ->
                val relative = image.center - originalCenter
                replacements[image.id] = image.copy(
                    center = targetCenter + relative * factor,
                    scale = (image.scale * factor).coerceIn(MIN_CANVAS_SCALE, MAX_CANVAS_SCALE),
                )
            }
        }
        mainVm.mutateScene {
            copy(images = images.map { replacements[it.id] ?: it })
        }
        fitToScreen()
        return true
    }
}

private const val GROUP_CANVAS_BOUNDS_PADDING = 12f
