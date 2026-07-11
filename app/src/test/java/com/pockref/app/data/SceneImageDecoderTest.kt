package com.pockref.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SceneImageDecoderTest {

    @Test
    fun originalLevel_preservesRequestedSize() {
        val method = SceneImageDecoder::class.java.getDeclaredMethod(
            "targetSideForLevel",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
        ).apply {
            isAccessible = true
        }

        val targetSide = method.invoke(
            SceneImageDecoder(null),
            SceneImageDecoder.ORIGINAL_LEVEL,
            14073,
        ) as Int

        assertEquals(14073, targetSide)
    }
}
