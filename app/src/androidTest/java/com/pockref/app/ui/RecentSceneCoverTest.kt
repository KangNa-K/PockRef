package com.pockref.app.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pockref.app.data.PurSceneCodec
import com.pockref.app.model.CropRect
import com.pockref.app.model.PockRefScene
import com.pockref.app.model.SceneImage
import com.pockref.app.model.Size2D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(AndroidJUnit4::class)
class RecentSceneCoverTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun v2CoverUsesExtractedImageSourceAndRespectsSelection() {
        val file = File.createTempFile("recent-cover-v2", ".pur", context.cacheDir)
        val codec = PurSceneCodec()
        try {
            file.outputStream().use { codec.writePureRefCompatible(scene(), it, context.cacheDir) }
            val uri = Uri.fromFile(file).toString()
            val defaultCover = loadRecentSceneCover(context, uri, null)
            assertNotNull(defaultCover)
            assertEquals(Color.BLUE, defaultCover!!.getPixel(0, 0))

            val selectedCover = loadRecentSceneCover(context, uri, "pur-v2-image-1")
            assertNotNull(selectedCover)
            assertEquals(Color.RED, selectedCover!!.getPixel(0, 0))
            assertEquals(2, selectedCover.width)
            assertEquals(4, selectedCover.height)
        } finally {
            codec.release()
            file.delete()
        }
    }

    @Test fun customFileAndEmptySceneRemainSupported() {
        val file = File.createTempFile("recent-cover-custom", ".pur", context.cacheDir)
        val codec = PurSceneCodec()
        try {
            file.outputStream().use { codec.write(scene(), it) }
            val uri = Uri.fromFile(file).toString()
            assertEquals(Color.RED, loadRecentSceneCover(context, uri, "first")!!.getPixel(0, 0))
            assertEquals(Color.BLUE, loadRecentSceneCover(context, uri, "stale-id")!!.getPixel(0, 0))
            file.outputStream().use { codec.write(PockRefScene(), it) }
            assertNull(loadRecentSceneCover(context, uri, null))
        } finally {
            codec.release()
            file.delete()
        }
    }

    private fun scene(): PockRefScene = PockRefScene(images = listOf(
        image("first", Color.RED, 1, CropRect(0f, 0f, 0.5f, 1f)),
        image("second", Color.BLUE, 2, null),
    ))

    private fun image(id: String, color: Int, z: Int, crop: CropRect?): SceneImage {
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        val bytes = ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }
        bitmap.recycle()
        return SceneImage(
            id = id, name = "$id.png", mimeType = "image/png", imageBytes = bytes,
            intrinsicWidth = 4, intrinsicHeight = 4, center = Offset.Zero,
            displaySize = Size2D(4f, 4f), zIndex = z, cropRect = crop,
        )
    }
}
