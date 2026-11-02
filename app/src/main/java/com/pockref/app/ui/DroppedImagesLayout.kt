package com.pockref.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.pockref.app.model.PockRefUiState
import com.pockref.app.model.SceneImage
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sqrt

/** Normalize only the new batch, then find free space nearest the release point. */
internal fun layoutDroppedImages(
    images: List<SceneImage>,
    state: PockRefUiState,
    positionInWindow: Offset?,
): List<SceneImage> {
    if (images.isEmpty()) return images
    val scale = state.canvasScale.takeIf { it.isFinite() && it > 0f } ?: 1f
    val width = state.canvasViewportSize.width.takeIf { it.isFinite() && it > 0f } ?: 1200f
    val height = state.canvasViewportSize.height.takeIf { it.isFinite() && it > 0f } ?: 800f
    val localPoint = positionInWindow?.minus(state.canvasOriginInWindow)
        ?: Offset(width / 2f, height / 2f)
    val anchor = (localPoint - Offset(width / 2f, height / 2f) - state.canvasOffset) / scale
    val importedIds = images.mapTo(hashSetOf()) { it.id }
    val existing = state.images.filter { it.id !in importedIds }
    // Use image height before rotation: rotating a reference must not change
    // the size of later imports. The median ignores unusually large outliers.
    val existingHeights = existing.map { it.displaySize.height * it.scale }
        .filter { it.isFinite() && it > 0f }.sorted()
    val matchingHeight = if (existingHeights.isEmpty()) null else {
        val middle = existingHeights.size / 2
        if (existingHeights.size % 2 == 0) {
            existingHeights[middle - 1] / 2f + existingHeights[middle] / 2f
        } else existingHeights[middle]
    }
    val targetHeight = matchingHeight ?: (height * 0.24f / scale)
    val normalized = images.map { image ->
        image.copy(scale = image.scale * targetHeight / imageRotatedFootprint(image).height.coerceAtLeast(1f))
    }
    val columns = ceil(sqrt(images.size.toDouble())).toInt().coerceIn(1, 4)
    val rows = (images.size + columns - 1) / columns
    val footprints = normalized.map(::imageRotatedFootprint)
    val columnWidths = FloatArray(columns)
    val rowHeights = FloatArray(rows)
    footprints.forEachIndexed { index, size ->
        columnWidths[index % columns] = maxOf(columnWidths[index % columns], size.width)
        rowHeights[index / columns] = maxOf(rowHeights[index / columns], size.height)
    }
    val gap = 12f / scale
    val gridWidth = columnWidths.sum() + gap * (columns - 1)
    val gridHeight = rowHeights.sum() + gap * (rows - 1)
    // A populated canvas defines the size; never shrink a large batch merely
    // to fit the viewport, otherwise later imports would no longer match it.
    val factor = if (matchingHeight != null) 1f else
        min(1f, min(width * 0.65f / gridWidth, height * 0.65f / gridHeight) / scale)
    val halfWidth = gridWidth * factor / 2f
    val halfHeight = gridHeight * factor / 2f
    // Inflating obstacles by the batch half-size turns rectangle placement into
    // finding the nearest point outside their union. Rotated/locked images count too.
    val obstacles = existing.map { image ->
        val size = imageRotatedFootprint(image)
        Rect(
            image.center.x - size.width / 2f - halfWidth - gap,
            image.center.y - size.height / 2f - halfHeight - gap,
            image.center.x + size.width / 2f + halfWidth + gap,
            image.center.y + size.height / 2f + halfHeight + gap,
        )
    }
    val placement = nearestFreeDropCenter(anchor, obstacles)
    val lefts = FloatArray(columns)
    val tops = FloatArray(rows)
    for (column in 1 until columns) lefts[column] = lefts[column - 1] + columnWidths[column - 1] + gap
    for (row in 1 until rows) tops[row] = tops[row - 1] + rowHeights[row - 1] + gap
    return normalized.mapIndexed { index, image ->
        val column = index % columns
        val row = index / columns
        image.copy(
            center = placement + Offset(
                lefts[column] + columnWidths[column] / 2f - gridWidth / 2f,
                tops[row] + rowHeights[row] / 2f - gridHeight / 2f,
            ) * factor,
            scale = image.scale * factor,
        )
    }
}

private fun nearestFreeDropCenter(anchor: Offset, obstacles: List<Rect>): Offset {
    if (obstacles.none { anchor.x > it.left && anchor.x < it.right && anchor.y > it.top && anchor.y < it.bottom }) return anchor
    var best = Offset(anchor.x, obstacles.minOf { it.top })
    var bestDistance = (best - anchor).getDistanceSquared()
    val candidates = (listOf(anchor.x) + obstacles.flatMap { listOf(it.left, it.right) }).distinct()
        .sortedBy { kotlin.math.abs(it - anchor.x) }
    for (x in candidates) {
        val dx = x - anchor.x
        if (dx * dx > bestDistance) continue
        val intervals = obstacles.filter { x > it.left && x < it.right }.sortedBy { it.top }
        var lower: Float? = null
        var upper = 0f
        fun consider(top: Float, bottom: Float) {
            val y = if (anchor.y > top && anchor.y < bottom) {
                if (anchor.y - top <= bottom - anchor.y) top else bottom
            } else anchor.y
            val point = Offset(x, y)
            val distance = (point - anchor).getDistanceSquared()
            if (distance < bestDistance) { best = point; bestDistance = distance }
        }
        for (interval in intervals) {
            val start = lower
            if (start == null) { lower = interval.top; upper = interval.bottom }
            else if (interval.top < upper) upper = maxOf(upper, interval.bottom)
            else {
                if (anchor.y > start && anchor.y < upper) break
                lower = interval.top; upper = interval.bottom
            }
        }
        val start = lower
        if (start == null) consider(anchor.y, anchor.y) else consider(start, upper)
    }
    return best
}
