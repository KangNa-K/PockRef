@file:Suppress("DEPRECATION")

package com.pockref.app.ui

import android.graphics.Movie
import com.pockref.app.model.SceneImage

internal data class GifPlaybackSource(
    val movie: Movie,
    val timeline: GifFrameTimeline,
)

internal data class GifPlaybackState(
    val frameIndex: Int = 0,
    val isPlaying: Boolean = true,
)

internal data class GifFrameTimeline(
    val frameStartMs: IntArray,
    val frameDurationsMs: IntArray,
    val totalDurationMs: Int,
) {
    val frameCount: Int get() = frameDurationsMs.size
    val lastFrameIndex: Int get() = (frameCount - 1).coerceAtLeast(0)

    fun timeForFrame(index: Int): Int =
        frameStartMs[index.coerceIn(0, lastFrameIndex)]

    fun durationForFrame(index: Int): Int =
        frameDurationsMs[index.coerceIn(0, lastFrameIndex)]
}

internal fun decodeGifPlaybackSource(bytes: ByteArray): GifPlaybackSource? {
    val timeline = parseGifFrameTimeline(bytes) ?: return null
    val movie = Movie.decodeByteArray(bytes, 0, bytes.size) ?: return null
    return GifPlaybackSource(movie = movie, timeline = timeline)
}

internal fun parseGifFrameTimeline(bytes: ByteArray): GifFrameTimeline? {
    if (bytes.size < 14) return null
    val header = String(bytes, 0, 6, Charsets.US_ASCII)
    if (header != "GIF87a" && header != "GIF89a") return null

    var pos = 13
    val logicalScreenPacked = bytes.u8(10)
    if ((logicalScreenPacked and 0x80) != 0) {
        pos += colorTableByteSize(logicalScreenPacked)
    }

    val durations = ArrayList<Int>()
    var pendingDelayMs = DEFAULT_GIF_FRAME_DELAY_MS

    while (pos < bytes.size) {
        when (bytes.u8(pos)) {
            0x2C -> {
                if (pos + 10 > bytes.size) return null
                val imagePacked = bytes.u8(pos + 9)
                pos += 10
                if ((imagePacked and 0x80) != 0) {
                    pos += colorTableByteSize(imagePacked)
                }
                if (pos >= bytes.size) return null
                pos++ // LZW minimum code size.
                pos = skipGifSubBlocks(bytes, pos)
                durations += pendingDelayMs
                pendingDelayMs = DEFAULT_GIF_FRAME_DELAY_MS
            }
            0x21 -> {
                if (pos + 2 > bytes.size) return null
                val label = bytes.u8(pos + 1)
                if (label == 0xF9 && pos + 8 <= bytes.size && bytes.u8(pos + 2) == 4) {
                    val delayHundredths = bytes.u16Le(pos + 4)
                    pendingDelayMs = gifDelayToMs(delayHundredths)
                    pos += 8
                } else {
                    pos += 2
                    pos = skipGifSubBlocks(bytes, pos)
                }
            }
            0x3B -> break
            else -> return null
        }
    }

    if (durations.size <= 1) return null
    val starts = IntArray(durations.size)
    var total = 0
    durations.forEachIndexed { index, delay ->
        starts[index] = total
        total += delay
    }
    if (total <= 0) return null
    return GifFrameTimeline(
        frameStartMs = starts,
        frameDurationsMs = durations.toIntArray(),
        totalDurationMs = total,
    )
}

internal fun isGifMimeType(mimeType: String): Boolean =
    mimeType.equals("image/gif", ignoreCase = true)

internal fun selectedGifPlaybackImageIdFor(
    images: List<SceneImage>,
    selectedImageIds: Set<String>,
    selectedImageId: String?,
): String? {
    val selectedId = when {
        selectedImageIds.size == 1 -> selectedImageIds.first()
        selectedImageIds.isEmpty() -> selectedImageId
        else -> null
    } ?: return null
    return images.firstOrNull { image ->
        image.id == selectedId &&
            isGifMimeType(image.mimeType) &&
            image.cropRect == null
    }?.id
}

private const val DEFAULT_GIF_FRAME_DELAY_MS = 100
private const val MIN_GIF_FRAME_DELAY_MS = 20

private fun gifDelayToMs(delayHundredths: Int): Int {
    if (delayHundredths <= 0) return DEFAULT_GIF_FRAME_DELAY_MS
    return (delayHundredths * 10).coerceAtLeast(MIN_GIF_FRAME_DELAY_MS)
}

private fun colorTableByteSize(packed: Int): Int =
    3 * (1 shl ((packed and 0x07) + 1))

private fun skipGifSubBlocks(bytes: ByteArray, start: Int): Int {
    var pos = start
    while (pos < bytes.size) {
        val blockSize = bytes.u8(pos)
        pos++
        if (blockSize == 0) return pos
        pos += blockSize
        if (pos > bytes.size) return bytes.size
    }
    return pos
}

private fun ByteArray.u8(index: Int): Int = this[index].toInt() and 0xFF

private fun ByteArray.u16Le(index: Int): Int =
    u8(index) or (u8(index + 1) shl 8)
