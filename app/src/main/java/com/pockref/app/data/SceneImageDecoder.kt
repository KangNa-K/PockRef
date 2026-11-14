package com.pockref.app.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.os.Build
import android.util.Log
import androidx.core.graphics.scale
import com.pockref.app.model.SceneImage
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * One-stop image decoder for the scene canvas.
 *
 * Replaces the previous three-path mess:
 *  - PurFileParser.decodeImageBitmap (temp-file dance for large images)
 *  - PurFileParser.decodeSampledBitmap (in-memory for small images)
 *  - PureRefApp's inline BitmapFactory.decodeByteArray (for imageBytes)
 *
 * Key design decisions:
 * 1. **BitmapRegionDecoder for offset-based images** — decodes directly from
 *    the RandomAccessFile via InputStream, no temp file. Falls back to the
 *    old copy-to-temp path only on API < 24 (where BitmapRegionDecoder
 *    doesn't support the stream format) or on decoder creation failure.
 * 2. **Hardware bitmaps for thumbnails** — ARGB_8888 thumbnails waste 4 bytes/pixel
 *    for something that's drawn once and rarely needs pixel access. HARDWARE
 *    bitmaps live in GPU memory and are nearly free to draw. Full-res stays
 *    ARGB_8888 for color-adjust / export paths.
 * 3. **Disk cache for bounded decode levels** — WebP files under app cacheDir.
 *    The 128px through 4096px ladder levels survive a scene reopen; the
 *    unbounded original level deliberately remains memory-only.
 * 4. **Mipmap-style decode ladder** — instead of two fixed decode levels
 *    (THUMBNAIL=256, FULL≤2048), decode at the size the viewport actually
 *    needs. Zoomed-out views get 128px; zoomed-in views get up to 4096px.
 *    Re-decode on zoom change (the old code already re-decoded, but at
 *    the same two fixed sizes regardless of actual display size).
 * 5. **No more temp files** — BitmapRegionDecoder + InputStream eliminates
 *    the seek→copyTo→decodeFile→delete cycle entirely.
 */
class SceneImageDecoder(
    private var cacheDir: File?,
) {

    /**
     * Set or update the cache directory. Called by PurSceneCodec.initCacheDir()
     * once the Android Context is available.
     */
    fun setCacheDir(dir: File) {
        cacheDir = dir
    }

    companion object {
        private const val TAG = "SceneImageDecoder"

        // Decode ladder: for a given display-size bucket, decode at this
        // pixel count. Ordered from smallest to largest. The decoder picks
        // the smallest bucket where the decoded image is ≥ the requested
        // display size (after inSampleSize rounding).
        val DECODE_LADDER = intArrayOf(
            128,    // far zoomed out — barely visible
            256,    // thumbnail — default "at rest" view
            512,    // medium zoom
            1024,   // close zoom
            2048,   // pixel-perfect on most phones
            4096,   // ultra-zoom / tablet
        )

        const val ORIGINAL_LEVEL = 6

        // Maximum inSampleSize. Beyond this the quality loss is unacceptable;
        // we'd rather OOM and retry at a higher level than return a 4×4 blob.
        private const val MAX_SAMPLE_SIZE = 32

        // Disk cache version. Bump when the decode ladder changes or the
        // cache format changes — old entries will be evicted naturally.
        // V_NOW: bumped from 1 → 2 because earlier versions of this code
        // accidentally cached "valid-format but all-zero-pixel" bitmaps
        // produced by BitmapRegionDecoder on Samsung devices. Those
        // WebP entries decode cleanly back to all-gray bitmaps on the
        // next open, masking the real fallback decode and showing
        // uniform gray placeholders for every image. Version 2 makes
        // the existing v1 cache entries invisible (different key) and
        // [DiskThumbnailCache.init] cleans the orphaned files on first
        // construction of the new cache.
        private const val DISK_CACHE_VERSION = 3
        private const val DISK_CACHE_DIR = "image_decode_cache"
        private const val DISK_CACHE_MAX_BYTES = 64L * 1024 * 1024 // 64MB
        private const val ORIGINAL_SAMPLE_TILE_EDGE = 256
        private const val ORIGINAL_SAMPLE_CACHE_TILES = 6
    }

    // ── File reference management ──────────────────────────────────

    // The RandomAccessFile for the currently-open .pur scene.
    // Set by [setSourceFile] after parseFromFile succeeds.
    @Volatile
    private var sourceRaf: RandomAccessFile? = null
    @Volatile
    private var sourceFile: File? = null

    // Source fingerprint for disk-cache keying. This is the .pur file's
    // length + lastModified at the time it was opened. It's not
    // cryptographic, but it's cheap and sufficient for cache invalidation
    // (if the file changes, the fingerprint changes).
    @Volatile
    private var sourceFingerprint: String = ""
    @Volatile
    private var tileSourceKey: String? = null
    @Volatile
    private var tileSourceBytes: ByteArray? = null

    /**
     * Call after successfully parsing a .pur file. Keeps the RAF open
     * for lazy decoding via imageByteOffset/imageByteLength.
     */
    fun setSourceFile(file: File) {
        // Close previous source if any
        release()
        sourceRaf = RandomAccessFile(file, "r")
        sourceFile = file
        sourceFingerprint = "${file.absolutePath}_${file.length()}_${file.lastModified()}_v$DISK_CACHE_VERSION"
    }

    /**
     * Release the source RAF. Call when closing the scene.
     */
    fun release() {
        sourceRaf?.close()
        sourceRaf = null
        sourceFile = null
        sourceFingerprint = ""
        tileSourceKey = null
        tileSourceBytes = null
        regionDecoders.clear()
        synchronized(originalSampleTiles) {
            originalSampleTiles.values.forEach { it.recycle() }
            originalSampleTiles.clear()
        }
    }

    // ── BitmapRegionDecoder pool ───────────────────────────────────

    // BitmapRegionDecoder is expensive to create (reads the entire file
    // header) but cheap to reuse. Pool them by (imageByteOffset, imageByteLength)
    // so repeated viewport-probe decodes for the same image hit the cached
    // decoder instead of re-parsing the file header each time.
    private val regionDecoders = ConcurrentHashMap<DecoderKey, BitmapRegionDecoder>()

    private data class DecoderKey(val offset: Int, val length: Int)

    private data class OriginalSampleTileKey(
        val imageId: String,
        val sourceIdentity: Int,
        val offset: Int,
        val length: Int,
        val column: Int,
        val row: Int,
    )

    private val originalSampleTiles = LinkedHashMap<OriginalSampleTileKey, Bitmap>(
        ORIGINAL_SAMPLE_CACHE_TILES,
        0.75f,
        true,
    )

    // `image` stays in the signature so re-enabling the pool only touches the body.
    @Suppress("UNUSED_PARAMETER")
    private fun getOrCreateRegionDecoder(image: SceneImage): BitmapRegionDecoder? {
        // V_NOW: disabled. BitmapRegionDecoder created from
        // RandomAccessFileRegionInputStream has multiple failure
        // modes on Samsung devices: it can return null (which the
        // ?: fallback handles), it can return a valid-sized bitmap
        // with all pixels initialized to 0 (rendered as a uniform
        // gray rectangle because 0x00000000 ARGB on the dark canvas
        // background shows as a mid-gray), and it can throw on
        // certain image variants. The seek+read+decodeByteArray
        // fallback path is reliable on every device we've tested,
        // so skip BitmapRegionDecoder entirely here and let the
        // caller's `?: decodeFromOffsetFallback(...)` chain do the
        // real work.
        return null
    }

    private fun createRegionDecoder(image: SceneImage): BitmapRegionDecoder? {
        val raf = sourceRaf ?: return null
        return try {
            val stream = RandomAccessFileRegionInputStream(
                raf, image.imageByteOffset.toLong(), image.imageByteLength.toLong()
            )
            // Always use the two-arg overload with isShareable=false.
            // The single-arg overload (API 31+) defaults isShareable=true,
            // which requires mark()/reset() on the stream — our
            // RandomAccessFileRegionInputStream doesn't support that and
            // BitmapRegionDecoder silently fails at runtime.
            @Suppress("DEPRECATION")
            BitmapRegionDecoder.newInstance(stream, false)
        } catch (e: Exception) {
            // Some image formats (rare WebP variants, truncated files) are not
            // supported by BitmapRegionDecoder. Fall back to the old decode path.
            Log.d(TAG, "BitmapRegionDecoder unsupported for ${image.name}, will use fallback", e)
            null
        }
    }

    // ── Disk cache ─────────────────────────────────────────────────

    private val diskCache: DiskThumbnailCache by lazy {
        val dir = cacheDir ?: throw IllegalStateException(
            "SceneImageDecoder.cacheDir not set. Call setCacheDir() before decoding."
        )
        DiskThumbnailCache(File(dir, DISK_CACHE_DIR), DISK_CACHE_MAX_BYTES)
    }

    /**
     * Read raw image bytes from the source file. Used by export/crop paths
     * that need the full uncompressed bytes, not a decoded Bitmap.
     */
    fun readImageBytes(offset: Int, length: Int): ByteArray? {
        val raf = sourceRaf ?: return null
        return try {
            synchronized(raf) {
                raf.seek(offset.toLong())
                val bytes = ByteArray(length)
                raf.readFully(bytes)
                bytes
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read image bytes at offset=$offset length=$length", e)
            null
        }
    }

    // ── Public decode API ──────────────────────────────────────────

    /**
     * Decode result. The [level] indicates which rung of the decode ladder
     * was used; callers can decide whether a re-decode at a higher level
     * is warranted based on the current zoom.
     */
    data class DecodeResult(
        val bitmap: Bitmap,
        val level: Int,       // from DECODE_LADDER
        val fromDiskCache: Boolean,
    )

    /**
     * Decode an image at the most appropriate level for the given display size.
     *
     * @param displayWidth  The pixel width the image will be drawn at on screen.
     * @param displayHeight The pixel height the image will be drawn at on screen.
     * @param maxLevel      Maximum decode level (index into DECODE_LADDER).
     *                      Use 0 for thumbnail-only, DECODE_LADDER.lastIndex for full.
     * @return The decoded bitmap, or null if decoding failed.
     */
    fun decode(
        image: SceneImage,
        displayWidth: Int,
        displayHeight: Int,
        maxLevel: Int = DECODE_LADDER.lastIndex,
    ): DecodeResult? {
        val targetSide = maxOf(displayWidth, displayHeight, 1)
        val level = pickDecodeLevel(targetSide, maxLevel)

        // 1. Check disk cache (bounded ladder levels only) — V_NOW: guarded
        // by `cacheDir != null` so the by-lazy `diskCache` initializer
        // doesn't throw when a SceneImageDecoder was constructed without
        // a cache dir (e.g. the fallback `SceneImageDecoder(null)` in
        // PockRefCanvas, or a secondary `PurSceneCodec` instance like
        // AutoSaveManager's that never calls `initCacheDir`). Without
        // this guard, the IllegalStateException was caught by
        // [ImageDecodePipeline.decodeAndNotify] and logged as a warning,
        // so the user saw a silent "decode failed → gray placeholder"
        // with no UI indication. Skipping the disk cache for these
        // decodes means we lose cross-session thumbnail reuse, but the
        // in-memory decode itself still works.
        if (image.imageBytes.isEmpty() && ImageDiskCachePolicy.shouldCache(level) && cacheDir != null) {
            val diskKey = diskCacheKey(image, level)
            diskCache.get(diskKey)?.let { cached ->
                return DecodeResult(cached, level, fromDiskCache = true)
            }
        }

        // 2. Decode
        val bitmap = if (image.imageBytes.isNotEmpty()) {
            decodeFromBytes(image, level, targetSide)
        } else if (image.imageByteOffset >= 0 && image.imageByteLength > 0) {
            decodeFromOffset(image, level, targetSide)
        } else {
            Log.w(TAG, "No data source for ${image.name}")
            return null
        }

        if (bitmap == null) {
            Log.w(TAG, "decode: bitmap is null for ${image.name} offset=${image.imageByteOffset} len=${image.imageByteLength} raf=${sourceRaf != null}")
            return null
        }

        // 3. Write to disk cache (bounded ladder levels only) — same guard
        if (image.imageBytes.isEmpty() && ImageDiskCachePolicy.shouldCache(level) && cacheDir != null) {
            val diskKey = diskCacheKey(image, level)
            diskCache.put(diskKey, bitmap)
        }

        return DecodeResult(bitmap, level, fromDiskCache = false)
    }

    /**
     * Fast thumbnail-only decode. Used for the initial canvas fill where
     * we want every image visible ASAP, even at low quality.
     */
    fun decodeThumbnail(image: SceneImage): DecodeResult? {
        // V_NOW: same cacheDir guard as [decode]. Skipping the disk
        // cache is acceptable here — the in-memory decode at level 1
        // (256px) is the common case for the initial canvas fill, and
        // the disk cache is purely a "second open is instant"
        // optimization.
        if (image.imageBytes.isEmpty() && cacheDir != null) {
            val diskKey = diskCacheKey(image, 1) // level 1 = 256px
            diskCache.get(diskKey)?.let { cached ->
                return DecodeResult(cached, 1, fromDiskCache = true)
            }
            // Level 0 (128px) disk cache as fallback
            val diskKey0 = diskCacheKey(image, 0)
            diskCache.get(diskKey0)?.let { cached ->
                return DecodeResult(cached, 0, fromDiskCache = true)
            }
        }
        return decode(image, 256, 256, maxLevel = 1)
    }

    /**
     * Decodes one bounded full-resolution source region for the HD tile layer
     * or canvas export. With [sourcePad] greater than zero, the decoded region
     * includes that many extra source pixels on each side while [request] still
     * describes the interior region; callers draw only the interior so adjacent
     * export tiles share edge pixels instead of showing filtering seams.
     */
    internal fun decodeTile(
        image: SceneImage,
        request: ImageTileRequest,
        sourcePad: Int = 0,
    ): Bitmap? {
        if (request.sourceWidth !in 1..ImageTilePlanner.TILE_EDGE_PX ||
            request.sourceHeight !in 1..ImageTilePlanner.TILE_EDGE_PX
        ) {
            return null
        }
        val bytes = tileSourceBytesFor(image) ?: return null

        val decoder = try {
            @Suppress("DEPRECATION")
            BitmapRegionDecoder.newInstance(bytes, 0, bytes.size, false)
        } catch (e: Exception) {
            Log.w(TAG, "Tile decoder creation failed for ${image.name}", e)
            return null
        }

        return try {
            val pad = sourcePad.coerceAtLeast(0)
            val sourceRight = request.sourceLeft + request.sourceWidth
            val sourceBottom = request.sourceTop + request.sourceHeight
            if (request.sourceLeft < 0 || request.sourceTop < 0 ||
                sourceRight > decoder.width || sourceBottom > decoder.height
            ) {
                null
            } else {
                val left = (request.sourceLeft - pad).coerceAtLeast(0)
                val top = (request.sourceTop - pad).coerceAtLeast(0)
                val right = (sourceRight + pad).coerceAtMost(decoder.width)
                val bottom = (sourceBottom + pad).coerceAtMost(decoder.height)
                decoder.decodeRegion(
                    Rect(left, top, right, bottom),
                    BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                        inMutable = false
                    },
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Tile decode failed for ${image.name}", e)
            null
        } finally {
            decoder.recycle()
        }
    }

    /** Reads one exact pixel from the original source, never from a display LOD. */
    internal fun sampleOriginalPixel(image: SceneImage, sourceX: Int, sourceY: Int): Int? {
        if (sourceX !in 0 until image.intrinsicWidth || sourceY !in 0 until image.intrinsicHeight) {
            return null
        }
        if (image.mimeType.equals("image/gif", ignoreCase = true)) return null

        val column = sourceX / ORIGINAL_SAMPLE_TILE_EDGE
        val row = sourceY / ORIGINAL_SAMPLE_TILE_EDGE
        val sourceIdentity = if (image.imageBytes.isNotEmpty()) {
            System.identityHashCode(image.imageBytes)
        } else {
            sourceFingerprint.hashCode()
        }
        val key = OriginalSampleTileKey(
            imageId = image.id,
            sourceIdentity = sourceIdentity,
            offset = image.imageByteOffset,
            length = image.imageByteLength,
            column = column,
            row = row,
        )
        synchronized(originalSampleTiles) {
            val cached = originalSampleTiles[key]
            if (cached != null && !cached.isRecycled) {
                return cached.getPixel(sourceX % ORIGINAL_SAMPLE_TILE_EDGE, sourceY % ORIGINAL_SAMPLE_TILE_EDGE)
            }
            if (cached != null) originalSampleTiles.remove(key)
        }

        val tile = decodeOriginalSampleTile(image, column, row) ?: return null
        synchronized(originalSampleTiles) {
            val existing = originalSampleTiles[key]
            if (existing != null && !existing.isRecycled) {
                tile.recycle()
                return existing.getPixel(sourceX % ORIGINAL_SAMPLE_TILE_EDGE, sourceY % ORIGINAL_SAMPLE_TILE_EDGE)
            }
            originalSampleTiles[key] = tile
            while (originalSampleTiles.size > ORIGINAL_SAMPLE_CACHE_TILES) {
                val eldest = originalSampleTiles.entries.iterator().next()
                originalSampleTiles.remove(eldest.key)
                if (!eldest.value.isRecycled) eldest.value.recycle()
            }
            return tile.getPixel(sourceX % ORIGINAL_SAMPLE_TILE_EDGE, sourceY % ORIGINAL_SAMPLE_TILE_EDGE)
        }
    }

    private fun decodeOriginalSampleTile(image: SceneImage, column: Int, row: Int): Bitmap? {
        val bytes = tileSourceBytesFor(image) ?: return null
        val left = column * ORIGINAL_SAMPLE_TILE_EDGE
        val top = row * ORIGINAL_SAMPLE_TILE_EDGE
        val decoder = try {
            @Suppress("DEPRECATION")
            BitmapRegionDecoder.newInstance(bytes, 0, bytes.size, false)
        } catch (error: Exception) {
            Log.w(TAG, "Original pixel sampler cannot decode ${image.name}", error)
            return null
        }
        return try {
            if (left >= decoder.width || top >= decoder.height ||
                decoder.width != image.intrinsicWidth || decoder.height != image.intrinsicHeight
            ) {
                null
            } else {
                decoder.decodeRegion(
                    Rect(
                        left,
                        top,
                        minOf(left + ORIGINAL_SAMPLE_TILE_EDGE, decoder.width),
                        minOf(top + ORIGINAL_SAMPLE_TILE_EDGE, decoder.height),
                    ),
                    BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                        inMutable = false
                    },
                )
            }
        } catch (error: Exception) {
            Log.w(TAG, "Original pixel tile decode failed for ${image.name}", error)
            null
        } finally {
            decoder.recycle()
        }
    }

    private fun tileSourceBytesFor(image: SceneImage): ByteArray? {
        if (image.imageBytes.isNotEmpty()) return image.imageBytes
        if (image.imageByteOffset < 0 || image.imageByteLength <= 0) return null

        val key = "${sourceFingerprint}_${image.id}_${image.imageByteOffset}_${image.imageByteLength}"
        tileSourceBytes?.takeIf { tileSourceKey == key }?.let { return it }
        return synchronized(this) {
            tileSourceBytes?.takeIf { tileSourceKey == key }
                ?: readImageBytes(image.imageByteOffset, image.imageByteLength)?.also { bytes ->
                    tileSourceKey = key
                    tileSourceBytes = bytes
                }
        }
    }

    // ── Internal decode paths ──────────────────────────────────────

    private fun decodeFromBytes(image: SceneImage, level: Int, requestedSide: Int): Bitmap? {
        val targetSide = targetSideForLevel(level, requestedSide)
        return decodeSampledBitmap(image.imageBytes, targetSide, targetSide)
    }

    private fun decodeFromOffset(image: SceneImage, level: Int, requestedSide: Int): Bitmap? {
        val targetSide = targetSideForLevel(level, requestedSide)

        // Preferred path: BitmapRegionDecoder — no temp file, direct stream decode
        val decoder = getOrCreateRegionDecoder(image)
        if (decoder != null) {
            return try {
                decodeViaRegionDecoder(decoder, targetSide)
                    // decodeRegion() can return null silently (corrupted data,
                    // unsupported format, Samsung quirks). Fall back to the
                    // reliable seek+read+decodeByteArray path instead of
                    // returning null to the caller.
                    ?: decodeFromOffsetFallback(image, targetSide)
            } catch (e: Exception) {
                Log.d(TAG, "RegionDecoder failed for ${image.name}, falling back", e)
                regionDecoders.remove(DecoderKey(image.imageByteOffset, image.imageByteLength))
                decodeFromOffsetFallback(image, targetSide)
            }
        }

        // Fallback: old seek+read+decodeByteArray path (no temp file)
        return decodeFromOffsetFallback(image, targetSide)
    }

    /**
     * Decode via BitmapRegionDecoder. This is the happy path — it reads
     * only the portions of the file it needs, with no intermediate copy.
     *
     * V_NOW (2026-06-16 user feedback "应用又闪退" + logcat
     * "Canvas: trying to draw too large(347892948bytes) bitmap"):
     * `BitmapRegionDecoder.decodeRegion(Rect, Options)` is documented to
     * honour `Options.inSampleSize` (added in API 21), but in practice
     * several Android framework combinations IGNORE it — the returned
     * bitmap is the region at 1:1 pixel density regardless of
     * `inSampleSize`. The SM-X810 (Adreno 730, Android 16) is one such
     * combination; the user-visible symptom is a disk-backed
     * `SceneImage` whose `displaySize` was set from the raw pixel
     * dimensions (e.g. 9317×9333) by [PurFileParser] line 408 (the
     * no-transform-in-.pur fallback path): the pipeline picks level 5
     * (4096) → `calculateSampleSize(9317, 9333, 4096, 4096) = 2` →
     * `decodeRegion` is asked for sample=2 but returns 1:1 → a
     * 347MB ARGB_8888 bitmap lands in [ImageDecodePipeline]'s
     * `onBitmapReady` → [PockRefCanvas] `drawSceneImage` →
     * [androidx.compose.ui.graphics.drawscope.DrawScope.drawImage]
     * → `BaseRecordingCanvas.drawBitmap` → `throwIfCannotDraw`
     * past the ~200MB single-bitmap limit on most GPUs → crash.
     *
     * Fix: after `decodeRegion`, ALWAYS re-check the returned bitmap's
     * long edge. If it exceeds [targetSide], force a precise
     * [Bitmap.createScaledBitmap] downscale. This mirrors the
     * [decodeSampledBitmap] helper used by the
     * [decodeFromOffsetFallback] path (where
     * `BitmapFactory.decodeByteArray` correctly honours
     * `inSampleSize`, so the additional createScaledBitmap there
     * is a precision step, not a safety net). After the fix, the
     * region-decode path is also safe regardless of GPU/driver.
     *
     * Recycle the intermediate `decoded` if the scale pass
     * produced a fresh bitmap (matches the recycle discipline in
     * [decodeSampledBitmap] so we don't hold two copies of a
     * 347MB allocation).
     */
    private fun decodeViaRegionDecoder(
        decoder: BitmapRegionDecoder,
        targetSide: Int,
    ): Bitmap? {
        val width = decoder.width
        val height = decoder.height
        if (width <= 0 || height <= 0) return null

        val sampleSize = calculateSampleSize(width, height, targetSide, targetSide)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = false
        }
        val decoded = decoder.decodeRegion(Rect(0, 0, width, height), opts)
            ?: return null

        val longSide = maxOf(decoded.width, decoded.height)
        return if (longSide > targetSide) {
            val scale = targetSide.toFloat() / longSide.toFloat()
            val newW = (decoded.width * scale).toInt().coerceAtLeast(1)
            val newH = (decoded.height * scale).toInt().coerceAtLeast(1)
            decoded.scale(newW, newH, filter = true).also {
                if (it !== decoded) decoded.recycle()
            }
        } else {
            decoded
        }
    }

    /**
     * Fallback for images where BitmapRegionDecoder doesn't work.
     * Uses seek+read+decodeByteArray instead of the old temp-file path.
     * This avoids the file-creation overhead while still being correct.
     */
    private fun decodeFromOffsetFallback(image: SceneImage, targetSide: Int): Bitmap? {
        val raf = sourceRaf ?: return null
        return try {
            synchronized(raf) {
                raf.seek(image.imageByteOffset.toLong())
                val bytes = ByteArray(image.imageByteLength)
                raf.readFully(bytes)
                decodeSampledBitmap(bytes, targetSide, targetSide)
            }
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "OOM in fallback decode for ${image.name}, retrying with 2x sample", e)
            System.gc()
            val raf2 = sourceRaf ?: return null
            try {
                synchronized(raf2) {
                    raf2.seek(image.imageByteOffset.toLong())
                    val bytes = ByteArray(image.imageByteLength)
                    raf2.readFully(bytes)
                    // Force higher sample size
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
                    val sample = calculateSampleSize(
                        bounds.outWidth, bounds.outHeight,
                        targetSide / 2, targetSide / 2 // halve the target → double the sample
                    )
                    val opts = BitmapFactory.Options().apply {
                        inSampleSize = sample.coerceAtMost(MAX_SAMPLE_SIZE)
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                        inMutable = false
                    }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                }
            } catch (e2: Exception) {
                Log.e(TAG, "Fallback decode also failed for ${image.name}", e2)
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fallback decode failed for ${image.name}", e)
            null
        }
    }

    // ── Decode ladder logic ────────────────────────────────────────

    private fun pickDecodeLevel(targetSide: Int, maxLevel: Int): Int {
        if (maxLevel >= ORIGINAL_LEVEL) return ORIGINAL_LEVEL
        // Pick the smallest ladder rung whose decoded size is >= targetSide
        // (after inSampleSize rounding). In practice, since inSampleSize
        // rounds down to power-of-2, a 2000px image decoded at level 1 (256)
        // with sampleSize=8 yields 250px, which is "close enough" to 256.
        // We don't need to be exact — the draw path scales to fit anyway.
        for (i in 0..maxLevel.coerceAtMost(DECODE_LADDER.lastIndex)) {
            if (DECODE_LADDER[i] >= targetSide) return i
        }
        return maxLevel.coerceAtMost(DECODE_LADDER.lastIndex)
    }

    private fun targetSideForLevel(level: Int, requestedSide: Int): Int =
        if (level == ORIGINAL_LEVEL) {
            requestedSide
        } else {
            DECODE_LADDER[level.coerceIn(DECODE_LADDER.indices)]
        }

    // ── Disk cache key ─────────────────────────────────────────────

    private fun diskCacheKey(image: SceneImage, level: Int): String {
        // Use source fingerprint + image offset + level as the key.
        // This means: same file, same image, same decode level → cache hit.
        // Different file (different fingerprint) → cache miss → re-decode.
        return "${sourceFingerprint}_${image.imageByteOffset}_${image.imageByteLength}_${image.id}_$level"
    }

    // ── Shared helpers ─────────────────────────────────────────────

    /**
     * Decode a JPEG/PNG/WebP byte array into a bitmap whose long edge
     * is at most [reqWidth]/[reqHeight] (using [reqWidth] / [reqHeight]
     * as the canvas-side target).
     *
     * V_NOW (2026-06-16 user feedback "应用又闪退" — round 2):
     * [BitmapFactory.decodeByteArray] with `Options.inSampleSize` is
     * documented to honour the sample, but on the SM-X810 (Adreno
     * 730, Android 16) the framework actually returns a 1:1 bitmap
     * (e.g. 9317×9333) for sample=2 — same symptom as the
     * [BitmapRegionDecoder.decodeRegion] inSampleSize bug, but on
     * the BitmapFactory path. The earlier fix in e4b2b31 (added
     * createScaledBitmap after [decodeViaRegionDecoder]) didn't
     * catch this because [getOrCreateRegionDecoder] is a stub that
     * always returns null on this project (deliberate, see its
     * KDoc about Samsung decode failures), so production decode
     * never reached decodeViaRegionDecoder — it always went
     * through [decodeFromOffsetFallback] → [decodeSampledBitmap].
     *
     * Fix: after [BitmapFactory.decodeByteArray] returns, ALWAYS
     * check the bitmap's long edge. If it exceeds the requested
     * max, force a precise [Bitmap.createScaledBitmap] downscale.
     * This mirrors the e4b2b31 fix in [decodeViaRegionDecoder] and
     * guarantees the returned bitmap fits the LOD budget regardless
     * of whether inSampleSize is honoured on the target device.
     * Intermediate bitmap is recycled if the scale pass produced
     * a fresh bitmap.
     */
    private fun decodeSampledBitmap(bytes: ByteArray, reqWidth: Int, reqHeight: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val targetLongEdge = maxOf(reqWidth, reqHeight)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return decodeWithImageDecoder(bytes, targetLongEdge)
        }
        val sample = calculateSampleSize(bounds.outWidth, bounds.outHeight, reqWidth, reqHeight)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = false
        }
        val decoded = try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        } catch (oom: OutOfMemoryError) {
            // V_NOW (e4b2b31 didn't touch this path because the
            // first OOM retry with sample*2 was already here; the
            // new force-scale fix below catches the case where
            // inSampleSize is silently ignored, which the OOM
            // retry would NOT catch — OOM only fires on a real
            // allocation failure, not on a 1:1 decode that
            // succeeds because the heap happens to have the room).
            Log.w(TAG, "OOM decoding, retrying with 2x sample")
            opts.inSampleSize = sample * 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        } ?: return decodeWithImageDecoder(bytes, targetLongEdge)

        val longSide = maxOf(decoded.width, decoded.height)
        return if (longSide > targetLongEdge) {
            val scale = targetLongEdge.toFloat() / longSide.toFloat()
            val newW = (decoded.width * scale).toInt().coerceAtLeast(1)
            val newH = (decoded.height * scale).toInt().coerceAtLeast(1)
            decoded.scale(newW, newH, filter = true).also {
                if (it !== decoded) decoded.recycle()
            }
        } else {
            decoded
        }
    }

    /**
     * BitmapFactory and ImageDecoder share most codecs, but Android vendor
     * builds can route them through different allocation paths. In particular,
     * Xiaomi Android 16 devices can return null from BitmapFactory for an
     * otherwise readable image and log `... image decoder ... unimplemented`.
     */
    private fun decodeWithImageDecoder(bytes: ByteArray, targetLongEdge: Int): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        return try {
            val source = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
                val sourceLongEdge = maxOf(info.size.width, info.size.height)
                if (sourceLongEdge > targetLongEdge) {
                    val scale = targetLongEdge.toDouble() / sourceLongEdge.toDouble()
                    decoder.setTargetSize(
                        (info.size.width * scale).toInt().coerceAtLeast(1),
                        (info.size.height * scale).toInt().coerceAtLeast(1),
                    )
                }
            }
        } catch (error: Throwable) {
            Log.w(TAG, "ImageDecoder fallback failed", error)
            null
        }
    }

    private fun calculateSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtMost(MAX_SAMPLE_SIZE)
    }

    // ── Stream wrapper (reuses the one from PurFileParser) ─────────

    private class RandomAccessFileRegionInputStream(
        private val raf: RandomAccessFile,
        private val startOffset: Long,
        private var remaining: Long,
    ) : InputStream() {
        private var position = 0L

        override fun read(): Int {
            val buffer = ByteArray(1)
            return if (read(buffer, 0, 1) == 1) buffer[0].toInt() and 0xFF else -1
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (remaining <= 0L) return -1
            val toRead = minOf(len.toLong(), remaining).toInt()
            return synchronized(raf) {
                raf.seek(startOffset + position)
                val read = raf.read(b, off, toRead)
                if (read > 0) {
                    position += read
                    remaining -= read
                }
                read
            }
        }
    }
}

// ── Disk LOD cache ─────────────────────────────────────────────────

/**
 * Simple file-based LRU cache for bounded decoded image levels.
 *
 * Layout: cacheDir/<hex-hash-of-key>.webp
 *
 * Uses WebP lossy for thumbnails — typically 2-5KB per 256px image,
 * vs 30-50KB for the equivalent PNG. Decoding WebP is fast on all
 * Android 5.0+ devices.
 *
 * Thread-safe. Eviction is lazy (on put when total size exceeds max).
 */
private class DiskThumbnailCache(
    private val cacheDir: File,
    private val maxBytes: Long,
) {
    private val indexFile = File(cacheDir, "index")
    // Map: key → (fileName, sizeBytes, lastAccessTime)
    private val index = LinkedHashMap<String, CacheEntry>(16, 0.75f, true)
    private var totalSize = 0L

    private data class CacheEntry(val file: File, val size: Long, var lastAccess: Long)

    init {
        cacheDir.mkdirs()
        // V_NOW: one-time wipe of the old (v1) cache directory. The
        // earlier code path could have written WebP files whose
        // content decodes back to an all-zero-pixel bitmap — a
        // "valid" but useless entry that would shadow the real
        // fallback decode on the next open and show uniform gray
        // rectangles for every image. We use a marker file so the
        // wipe runs exactly once per device, not on every app
        // launch. Subsequent runs are no-ops.
        val marker = File(cacheDir, ".v2_migrated")
        if (!marker.exists()) {
            cacheDir.listFiles()?.forEach { it.delete() }
            marker.createNewFile()
        }
        loadIndex()
    }

    @Synchronized
    fun get(key: String): Bitmap? {
        val entry = index[key] ?: return null
        if (!entry.file.exists()) {
            index.remove(key)
            totalSize -= entry.size
            return null
        }
        entry.lastAccess = System.currentTimeMillis()
        return BitmapFactory.decodeFile(entry.file.absolutePath)
    }

    @Synchronized
    fun put(key: String, bitmap: Bitmap) {
        val fileName = thumbnailCacheFileName(key)
        val file = File(cacheDir, fileName)
        val size: Long
        try {
            file.outputStream().use { out ->
                val compressed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 80, out)
                } else {
                    @Suppress("DEPRECATION")
                    bitmap.compress(Bitmap.CompressFormat.WEBP, 80, out)
                }
                check(compressed) { "Thumbnail compression failed" }
            }
            size = file.length()
        } catch (e: Exception) {
            file.delete()
            return
        }

        // Remove old entry if overwriting
        index[key]?.let { old ->
            if (old.file.exists() && old.file != file) old.file.delete()
            totalSize -= old.size
        }

        index[key] = CacheEntry(file, size, System.currentTimeMillis())
        totalSize += size
        while (totalSize > maxBytes && index.isNotEmpty()) {
            val oldest = index.entries.first()
            oldest.value.file.delete()
            totalSize -= oldest.value.size
            index.remove(oldest.key)
        }
        saveIndex()
    }

    private fun loadIndex() {
        if (!indexFile.exists()) return
        try {
            indexFile.readLines().forEach { line ->
                val parts = line.split('\t')
                if (parts.size >= 3) {
                    val key = parts[0]
                    val file = File(cacheDir, parts[1])
                    val size = parts[2].toLongOrNull() ?: 0L
                    if (file.exists()) {
                        index[key] = CacheEntry(file, size, System.currentTimeMillis())
                        totalSize += size
                    }
                }
            }
        } catch (_: Exception) {
            // Corrupt index — start fresh
            index.clear()
            totalSize = 0
        }
    }

    private fun saveIndex() {
        try {
            indexFile.writeText(index.entries.joinToString("\n") { (key, entry) ->
                "$key\t${entry.file.name}\t${entry.size}"
            })
        } catch (_: Exception) {
            // Best effort — cache still works in-memory for this session
        }
    }
}

internal fun thumbnailCacheFileName(key: String): String =
    MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) } + ".webp"
