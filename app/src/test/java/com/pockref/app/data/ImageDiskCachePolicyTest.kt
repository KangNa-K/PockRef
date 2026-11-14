package com.pockref.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageDiskCachePolicyTest {

    @Test
    fun cachesEveryBoundedDecodeLevelButNeverOriginal() {
        SceneImageDecoder.DECODE_LADDER.indices.forEach { level ->
            assertTrue(ImageDiskCachePolicy.shouldCache(level))
        }

        assertFalse(ImageDiskCachePolicy.shouldCache(SceneImageDecoder.ORIGINAL_LEVEL))
    }

    @Test
    fun cacheFilenamesDoNotCollideForJavaHashCollisions() {
        assertEquals("Aa".hashCode(), "BB".hashCode())
        assertNotEquals(thumbnailCacheFileName("Aa"), thumbnailCacheFileName("BB"))
        assertEquals(thumbnailCacheFileName("Aa"), thumbnailCacheFileName("Aa"))
    }
}
