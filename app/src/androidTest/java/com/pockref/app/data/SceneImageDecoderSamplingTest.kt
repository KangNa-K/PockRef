package com.pockref.app.data

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pockref.app.model.SceneImage
import com.pockref.app.model.Size2D
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SceneImageDecoderSamplingTest {

    @Test
    fun samplesExactArgbFromOriginalEncodedPixels() {
        val source = Bitmap.createBitmap(4, 3, Bitmap.Config.ARGB_8888)
        val expected = 0x80408020.toInt()
        source.setPixel(3, 2, expected)
        val bytes = ByteArrayOutputStream().use { output ->
            source.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }
        source.recycle()
        val image = SceneImage(
            name = "color-sample.png",
            mimeType = "image/png",
            imageBytes = bytes,
            intrinsicWidth = 4,
            intrinsicHeight = 3,
            center = Offset.Zero,
            displaySize = Size2D(4f, 3f),
        )

        assertEquals(expected, SceneImageDecoder(null).sampleOriginalPixel(image, 3, 2))
    }
}
