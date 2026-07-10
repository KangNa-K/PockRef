package com.pockref.app.ui

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GifPlaybackTest {
    @Test
    fun parseGifFrameTimeline_readsFrameDelaysAndStarts() {
        val bytes = byteArrayOf(
            0x47, 0x49, 0x46, 0x38, 0x39, 0x61, // GIF89a
            0x01, 0x00, 0x01, 0x00, // 1x1
            0x00, 0x00, 0x00, // no global color table
            0x21, 0xF9.toByte(), 0x04, 0x00, 0x05, 0x00, 0x00, 0x00, // 50ms
            0x2C, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
            0x02, 0x00,
            0x21, 0xF9.toByte(), 0x04, 0x00, 0x0C, 0x00, 0x00, 0x00, // 120ms
            0x2C, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
            0x02, 0x00,
            0x3B,
        )

        val timeline = parseGifFrameTimeline(bytes)

        requireNotNull(timeline)
        assertEquals(2, timeline.frameCount)
        assertEquals(170, timeline.totalDurationMs)
        assertArrayEquals(intArrayOf(0, 50), timeline.frameStartMs)
        assertArrayEquals(intArrayOf(50, 120), timeline.frameDurationsMs)
    }

    @Test
    fun parseGifFrameTimeline_rejectsNonGifBytes() {
        assertNull(parseGifFrameTimeline(byteArrayOf(0x00, 0x01, 0x02)))
    }
}
