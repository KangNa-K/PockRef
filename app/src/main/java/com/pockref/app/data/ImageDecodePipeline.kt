package com.pockref.app.data

import android.graphics.Bitmap
import android.util.Log
import com.pockref.app.model.SceneImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Orchestrates image decoding for the canvas.
 *
 * This replaces the old PureRefApp.kt LaunchedEffect-based pipeline with
 * a structured, testable class. The old pipeline had these problems:
 *
 * 1. **Two-phase awaitAll blocks the entire pipeline** — thumbnail decode
 *    for image N+1 waits for full decode of image N. On a 500-image scene,
 *    the last thumbnail doesn't appear until ~250 full decodes complete.
 *
 * 2. **Fixed decode levels (256px / ≤2048px) ignore actual zoom** — at
 *    0.1× zoom you decode 2048px and draw it at 50px; at 3× zoom you
 *    decode 2048px and draw it at 6144px (truncated).
 *
 * 3. **No disk cache** — every scene open re-decodes every image from
 *    scratch, even if you opened the same file 5 minutes ago.
 *
 * 4. **Duplicated decode logic** — the main effect and viewportProbe
 *    effect each have their own inline decode function, sharing only the
 *    loadingImages set. Race conditions between the two produce duplicate
 *    work and wasted memory.
 *
 * This class solves all four:
 * - **Streaming decode**: thumbnails decode independently and call back
 *   immediately; full-res decodes proceed in the background.
 * - **Zoom-aware decode levels**: the decode ladder (128→256→512→...→4096)
 *   picks the right size based on actual display pixels.
 * - **Disk cache**: thumbnails are persisted as WebP; second open is instant.
 * - **Single decode path**: one [decode] method, one [Semaphore], one
 *   loading set. No duplication.
 *
 * Usage from PureRefApp.kt:
 * ```
 * val imagePipeline = remember { ImageDecodePipeline(SceneImageDecoder(cacheDir)) }
 * // On scene load:
 * imagePipeline.onSceneChanged(state.images, state.canvasOffset, ...)
 * // On pan/zoom:
 * imagePipeline.onViewportChanged(state.canvasOffset, state.canvasScale, ...)
 * ```
 */
class ImageDecodePipeline(
    private val decoder: SceneImageDecoder,
    maxConcurrentDecodes: Int = 6, // Up from 4 — BitmapRegionDecoder is lighter than temp-file decode
) {
    companion object {
        private const val TAG = "ImageDecodePipeline"

        // Viewport margin multiplier. Images within this factor of the
        // viewport edge are decoded proactively (reduces gray pop-in on
        // small pans).
        private const val PREFETCH_MARGIN = 0.25f

        // Delay before decoding images that are off-viewport. Shorter than
        // the old 500ms because the disk cache makes most of these free.
        private const val OFF_VIEWPORT_DELAY_MS = 200L
    }

    private val decodePermits = Semaphore(maxConcurrentDecodes)

    // Currently-loading image IDs. Shared across all decode calls so
    // parallel triggers (scene change + viewport change) don't duplicate work.
    private val loadingImages = mutableSetOf<String>()

    // ── Callbacks for Compose integration ──────────────────────────

    /**
     * Called when a bitmap is ready to display. The caller should update
     * the Compose bitmap cache (mutableStateMapOf) so the draw loop picks
     * it up on the next frame.
     *
     * Parameters: (imageId, decodeLevel, bitmap)
     * - decodeLevel: index into SceneImageDecoder.DECODE_LADDER
     */
    var onBitmapReady: ((imageId: String, level: Int, bitmap: Bitmap) -> Unit)? = null

    /**
     * Called when the pipeline finishes all work for the current batch.
     * Useful for clearing busy indicators.
     */
    var onBatchComplete: (() -> Unit)? = null

    // ── Public API ─────────────────────────────────────────────────

    /**
     * Called when the scene's image list changes (load, import, delete, reorder).
     * Decodes all in-viewport images (thumbnail first, then full) and
     * queues off-viewport images for delayed decode.
     *
     * @param images Current scene images.
     * @param viewportRect World-space viewport rectangle.
     * @param canvasScale Current zoom level.
     * @param cachedImageIds Set of image IDs that already have a bitmap
     *   in the Compose cache (skip re-decoding these).
     * @param displaySizeForImage Function that returns the display size
     *   (in pixels) for a given image at the current zoom. This drives
     *   the decode ladder selection.
     */
    suspend fun onSceneChanged(
        images: List<SceneImage>,
        viewportRect: android.graphics.RectF,
        canvasScale: Float,
        cachedImageIds: Set<String>,
        displaySizeForImage: (SceneImage) -> Pair<Int, Int>,
    ) {
        val toLoad = images.filter { it.id !in cachedImageIds && it.id !in loadingImages }
        if (toLoad.isEmpty()) return

        val (inView, offView) = toLoad.partition { image ->
            rectsOverlap(imageWorldAabb(image), viewportRect)
        }

        // Prefetch ring: images just outside the viewport that are likely
        // to pan into view soon. Decode at thumbnail level only.
        val prefetchRect = expandedRect(viewportRect, PREFETCH_MARGIN)
        val prefetch = offView.filter { image ->
            rectsOverlap(imageWorldAabb(image), prefetchRect)
        }
        val farOffView = offView.filter { it !in prefetch }

        coroutineScope {
            // Phase 1: In-viewport thumbnails (fast, many hit disk cache)
            val thumbJobs = inView.map { image ->
                async(Dispatchers.Default) {
                    decodeAndNotify(image, isThumbnail = true)
                }
            }
            thumbJobs.awaitAll()

            // Phase 2: In-viewport full-res + prefetch thumbnails (parallel)
            val fullJobs = inView.map { image ->
                async(Dispatchers.Default) {
                    val (dw, dh) = displaySizeForImage(image)
                    decodeAndNotify(image, isThumbnail = false, displayWidth = dw, displayHeight = dh)
                }
            }
            val prefetchJobs = prefetch.map { image ->
                async(Dispatchers.Default) {
                    decodeAndNotify(image, isThumbnail = true)
                }
            }
            (fullJobs + prefetchJobs).awaitAll()

            // Phase 3: Far off-viewport (delayed, thumbnail only)
            val farJobs = farOffView.map { image ->
                async(Dispatchers.Default) {
                    kotlinx.coroutines.delay(OFF_VIEWPORT_DELAY_MS)
                    decodeAndNotify(image, isThumbnail = true)
                }
            }
            farJobs.awaitAll()
        }

        onBatchComplete?.invoke()
    }

    /**
     * Called when the user pans or zooms. Decodes any newly-visible images
     * that aren't cached yet, and re-decodes visible images at a higher
     * level if the zoom has increased enough to warrant it.
     */
    suspend fun onViewportChanged(
        images: List<SceneImage>,
        viewportRect: android.graphics.RectF,
        canvasScale: Float,
        cachedImageIds: Set<String>,
        cachedLevels: Map<String, Int>, // imageId → currently-cached decode level
        displaySizeForImage: (SceneImage) -> Pair<Int, Int>,
    ) {
        val toDecode = images.filter { image ->
            val aabb = imageWorldAabb(image)
            if (!rectsOverlap(aabb, viewportRect)) return@filter false
            if (image.id in loadingImages) return@filter false
            if (image.id !in cachedImageIds) return@filter true
            // Already cached — check if zoom increased enough to warrant
            // a re-decode at a higher level
            val (dw, dh) = displaySizeForImage(image)
            val targetSide = maxOf(dw, dh)
            val currentLevel = cachedLevels[image.id] ?: 0
            val desiredLevel = pickLevelForDisplaySize(targetSide)
            desiredLevel > currentLevel
        }
        if (toDecode.isEmpty()) return

        coroutineScope {
            val jobs = toDecode.map { image ->
                async(Dispatchers.Default) {
                    val (dw, dh) = displaySizeForImage(image)
                    decodeAndNotify(image, isThumbnail = false, displayWidth = dw, displayHeight = dh)
                }
            }
            jobs.awaitAll()
        }
    }

    // ── Internal ───────────────────────────────────────────────────

    private suspend fun decodeAndNotify(
        image: SceneImage,
        isThumbnail: Boolean,
        displayWidth: Int = 256,
        displayHeight: Int = 256,
    ) {
        synchronized(loadingImages) {
            if (image.id in loadingImages) return
            loadingImages.add(image.id)
        }
        try {
            val result = decodePermits.withPermit {
                withContext(Dispatchers.IO) {
                    if (isThumbnail) {
                        decoder.decodeThumbnail(image)
                    } else {
                        decoder.decode(image, displayWidth, displayHeight)
                    }
                }
            }
            if (result != null) {
                onBitmapReady?.invoke(image.id, result.level, result.bitmap)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Decode failed for ${image.name}", e)
        } finally {
            synchronized(loadingImages) {
                loadingImages.remove(image.id)
            }
        }
    }

    // ── Geometry helpers ───────────────────────────────────────────

    private fun imageWorldAabb(image: SceneImage): android.graphics.RectF {
        val hw = image.displaySize.width / 2f
        val hh = image.displaySize.height / 2f
        return android.graphics.RectF(
            image.center.x - hw,
            image.center.y - hh,
            image.center.x + hw,
            image.center.y + hh,
        )
    }

    private fun rectsOverlap(a: android.graphics.RectF, b: android.graphics.RectF): Boolean {
        return a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top
    }

    private fun expandedRect(rect: android.graphics.RectF, marginFraction: Float): android.graphics.RectF {
        val dx = rect.width() * marginFraction
        val dy = rect.height() * marginFraction
        return android.graphics.RectF(rect.left - dx, rect.top - dy, rect.right + dx, rect.bottom + dy)
    }

    private fun pickLevelForDisplaySize(targetSide: Int): Int {
        for (i in SceneImageDecoder.DECODE_LADDER.indices) {
            if (SceneImageDecoder.DECODE_LADDER[i] >= targetSide) return i
        }
        return SceneImageDecoder.DECODE_LADDER.lastIndex
    }
}
