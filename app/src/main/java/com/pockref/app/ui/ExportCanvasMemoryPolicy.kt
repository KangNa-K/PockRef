package com.pockref.app.ui

import kotlin.math.sqrt

/**
 * Keep the decoded source bitmap within roughly two times the final render
 * target. The final draw uses filtering, so an intermediate resized bitmap
 * is unnecessary.
 */
internal fun calculateMemorySafeSampleSize(
    width: Int,
    height: Int,
    targetWidth: Int,
    targetHeight: Int,
): Int {
    var sampleSize = 1
    val maxDecodedWidth = targetWidth.toLong() * 2L
    val maxDecodedHeight = targetHeight.toLong() * 2L
    while (
        width.toLong() / sampleSize > maxDecodedWidth ||
        height.toLong() / sampleSize > maxDecodedHeight
    ) {
        sampleSize *= 2
    }
    return sampleSize
}

/**
 * Choose the largest output edge that leaves room for Compose, the canvas
 * bitmap, and one source/tile decode. The upper bound is intentionally lower
 * than the theoretical heap limit because Android also accounts for caches
 * and native graphics allocations while this export is running.
 */
internal fun selectedCanvasSafeMaxDim(
    maxMemoryBytes: Long,
    usedHeapBytes: Long,
): Int {
    val availableHeap = (maxMemoryBytes - usedHeapBytes).coerceAtLeast(0L)
    val canvasBudget = (availableHeap * 55L / 100L).coerceIn(
        24L * 1024L * 1024L,
        256L * 1024L * 1024L,
    )
    val dimension = sqrt((canvasBudget / 4L).toDouble()).toInt()
    return dimension.coerceIn(2048, 8192)
}

/**
 * Return the largest source image that can be decoded as one Bitmap while
 * leaving the canvas and app heap headroom. Sources larger than this use
 * export tiles, which are rendered with overlap to avoid seam artifacts.
 */
internal fun selectedCanvasSourceTargetDim(
    maxMemoryBytes: Long,
    usedHeapBytes: Long,
): Int {
    val availableHeap = (maxMemoryBytes - usedHeapBytes).coerceAtLeast(0L)
    val sourceBudget = (availableHeap * 15L / 100L).coerceIn(
        16L * 1024L * 1024L,
        128L * 1024L * 1024L,
    )
    val maxDecodedEdge = sqrt((sourceBudget / 4L).toDouble()).toInt()
    return maxDecodedEdge.coerceIn(2048, 4096)
}
