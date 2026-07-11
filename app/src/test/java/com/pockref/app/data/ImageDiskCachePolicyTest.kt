package com.pockref.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageDiskCachePolicyTest {

    @Test
    fun cachesEveryBoundedDecodeLevelButNeverOriginal() {
        SceneImageDecoder.DECODE_LADDER.indices.forEach { level ->
            assertTrue(ImageDiskCachePolicy.shouldCache(level))
        }

        assertFalse(ImageDiskCachePolicy.shouldCache(SceneImageDecoder.ORIGINAL_LEVEL))
    }
}
