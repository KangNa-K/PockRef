package com.pockref.app.data

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.pockref.app.model.CanvasMarkupStroke
import com.pockref.app.model.CanvasMarkupText
import com.pockref.app.model.CropRect
import com.pockref.app.model.ImageAlignment
import com.pockref.app.model.PockRefScene
import com.pockref.app.model.SceneImage
import com.pockref.app.model.Size2D
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.Base64

class PurSceneCodecCustomFormatTest {

    @Test
    fun customFormat_roundTripsOriginalResolutionPreference() {
        val codec = PurSceneCodec()
        val file = File.createTempFile("pockref-hd-preference", ".pur")
        file.deleteOnExit()
        val scene = PockRefScene(
            version = 3,
            images = listOf(
                SceneImage(
                    id = "hd-image",
                    name = "hd",
                    mimeType = "image/png",
                    imageBytes = byteArrayOf(1, 2, 3),
                    intrinsicWidth = 10,
                    intrinsicHeight = 10,
                    center = Offset.Zero,
                    displaySize = Size2D(10f, 10f),
                    showsOriginalResolution = true,
                ),
            ),
        )

        file.outputStream().use { codec.write(scene, it) }

        assertTrue(codec.readFromFile(file).images.single().showsOriginalResolution)
    }

    @Test
    fun customFormat_roundTripsPosterizeLevel() {
        val codec = PurSceneCodec()
        val file = File.createTempFile("pockref-posterize", ".pur")
        file.deleteOnExit()
        val scene = PockRefScene(
            images = listOf(
                SceneImage(
                    id = "posterized-image",
                    name = "posterized.png",
                    mimeType = "image/png",
                    imageBytes = byteArrayOf(1, 2, 3),
                    intrinsicWidth = 10,
                    intrinsicHeight = 10,
                    center = Offset.Zero,
                    displaySize = Size2D(10f, 10f),
                    posterizeLevels = 8,
                ),
            ),
        )

        file.outputStream().use { codec.write(scene, it) }

        assertEquals(8, codec.readFromFile(file).images.single().posterizeLevels)
    }

    @Test
    fun pureRefCompatibleWrite_roundTripsPockRefImageStateWithoutTransformExtensions() {
        val codec = PurSceneCodec()
        val file = File.createTempFile("pockref-pureref-compatible", ".pur")
        file.deleteOnExit()
        val png = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScL+XQAAAABJRU5ErkJggg==",
        )
        val crop = CropRect(0.1f, 0.2f, 0.9f, 0.8f)
        val scene = PockRefScene(
            name = "desktop-compatible-state",
            images = listOf(
                SceneImage(
                    id = "front-uuid",
                    name = "front.png",
                    mimeType = "image/png",
                    imageBytes = png,
                    intrinsicWidth = 1,
                    intrinsicHeight = 1,
                    center = Offset(120f, -40f),
                    displaySize = Size2D(80f, 60f),
                    colorAdjust = com.pockref.app.model.ColorAdjust(
                        brightness = 0.1f,
                        contrast = -0.2f,
                        saturation = -1f,
                    ),
                    posterizeLevels = 2,
                    rotationDegrees = 15f,
                    scale = 1.25f,
                    flipX = true,
                    zIndex = 9,
                    locked = true,
                    cropRect = crop,
                    showsOriginalResolution = true,
                ),
                SceneImage(
                    id = "back-uuid",
                    name = "back.png",
                    mimeType = "image/png",
                    imageBytes = png,
                    intrinsicWidth = 1,
                    intrinsicHeight = 1,
                    center = Offset(-50f, 30f),
                    displaySize = Size2D(32f, 24f),
                    posterizeLevels = 16,
                    zIndex = 1,
                ),
            ),
        )

        file.outputStream().use { output ->
            codec.writePureRefCompatible(scene, output, File("build/tmp"))
        }

        val written = file.readBytes()
        assertEquals(8, ByteBuffer.wrap(written, 0, 4).order(ByteOrder.BIG_ENDIAN).int)
        assertArrayEquals("1.10".toByteArray(Charsets.UTF_16BE), written.copyOfRange(4, 12))
        assertEquals(-1, written.indexOfPattern(byteArrayOf('P'.code.toByte(), 'R'.code.toByte(), 'C'.code.toByte(), 'A'.code.toByte())))

        val restored = codec.readFromFile(file).images.associateBy { it.name }
        val front = restored.getValue("front.png")
        val back = restored.getValue("back.png")
        assertEquals(2, front.posterizeLevels)
        assertEquals(16, back.posterizeLevels)
        assertEquals(-1f, front.colorAdjust.saturation, 0.0001f)
        assertEquals(120f, front.center.x, 0.0001f)
        assertEquals(-40f, front.center.y, 0.0001f)
        assertEquals(80f, front.displaySize.width, 0.0001f)
        assertEquals(60f, front.displaySize.height, 0.0001f)
        assertEquals(15f, front.rotationDegrees, 0.0001f)
        assertEquals(1.25f, front.scale, 0.0001f)
        assertTrue(front.flipX)
        assertTrue(front.locked)
        assertTrue(front.showsOriginalResolution)
        assertEquals(crop, front.cropRect)
    }

    @Test
    fun customFormat_restoresSceneDefaults() {
        val codec = PurSceneCodec()
        val file = Files.createTempFile("pockref-scene-defaults", ".pur").toFile()
        val scene = PockRefScene(
            version = 2,
            defaultSpacing = Size2D(17f, 23f),
            defaultAlignment = ImageAlignment.BOTTOM_RIGHT,
        )

        file.outputStream().use { codec.write(scene, it) }
        val restored = codec.readFromFile(file)

        assertEquals(scene.defaultSpacing, restored.defaultSpacing)
        assertEquals(scene.defaultAlignment, restored.defaultAlignment)
        file.delete()
    }

    @Test
    fun customFormat_roundTripsCropRectAndDisplaySize() {
        val codec = PurSceneCodec()
        val file = File.createTempFile("pockref-crop-round-trip", ".pur")
        file.deleteOnExit()
        val crop = CropRect(
            left = 0.125f,
            top = 0.2f,
            right = 0.875f,
            bottom = 0.8f,
        )
        val scene = PockRefScene(
            name = "crop-round-trip",
            images = listOf(
                SceneImage(
                    id = "cropped-image",
                    name = "cropped.png",
                    mimeType = "image/png",
                    imageBytes = byteArrayOf(1, 2, 3),
                    intrinsicWidth = 1600,
                    intrinsicHeight = 1200,
                    center = Offset(240f, 180f),
                    displaySize = Size2D(720f, 432f),
                    cropRect = crop,
                ),
            ),
        )

        file.outputStream().use { output -> codec.write(scene, output) }

        val written = file.readBytes()
        assertArrayEquals(
            byteArrayOf('P'.code.toByte(), 'R'.code.toByte(), 'F'.code.toByte(), 'A'.code.toByte()),
            written.copyOfRange(0, 4),
        )

        val restoredImage = codec.readFromFile(file).images.single()
        assertEquals(crop.left, restoredImage.cropRect?.left ?: -1f, 0.0001f)
        assertEquals(crop.top, restoredImage.cropRect?.top ?: -1f, 0.0001f)
        assertEquals(crop.right, restoredImage.cropRect?.right ?: -1f, 0.0001f)
        assertEquals(crop.bottom, restoredImage.cropRect?.bottom ?: -1f, 0.0001f)
        assertEquals(720f, restoredImage.displaySize.width, 0.0001f)
        assertEquals(432f, restoredImage.displaySize.height, 0.0001f)
    }

    @Test
    fun desktopCompatibleWriteDoesNotUsePrfaHeader() {
        val codec = PurSceneCodec()
        val scene = PockRefScene(name = "desktop-compatible")
        val output = ByteArrayOutputStream()

        codec.writeDesktopCompatible(scene, output, File("build/tmp"))

        val written = output.toByteArray()
        val prfa = byteArrayOf('P'.code.toByte(), 'R'.code.toByte(), 'F'.code.toByte(), 'A'.code.toByte())
        assertTrue(written.size >= 12)
        assertTrue(!written.copyOfRange(0, 4).contentEquals(prfa))
        assertEquals(8, ByteBuffer.wrap(written, 0, 4).order(ByteOrder.BIG_ENDIAN).int)
        assertArrayEquals("1.10".toByteArray(Charsets.UTF_16BE), written.copyOfRange(4, 12))
    }

    @Test
    fun desktopCompatibleWriteRoundTripsBackgroundColor() {
        val codec = PurSceneCodec()
        val file = File.createTempFile("pockref-desktop-background", ".pur")
        file.deleteOnExit()
        val scene = PockRefScene(
            name = "desktop-background",
            backgroundColor = Color(0xFFB0D8E8),
            backgroundColorIsPersisted = true,
            images = listOf(
                SceneImage(
                    id = "background-image",
                    name = "background-image.png",
                    mimeType = "image/png",
                    imageBytes = Base64.getDecoder().decode(
                        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScL+XQAAAABJRU5ErkJggg==",
                    ),
                    intrinsicWidth = 1,
                    intrinsicHeight = 1,
                    center = Offset.Zero,
                    displaySize = Size2D(1f, 1f),
                ),
            ),
        )

        file.outputStream().use { output ->
            codec.writeDesktopCompatible(scene, output, File("build/tmp"))
        }

        val restored = codec.readFromFile(file)
        assertEquals(scene.backgroundColor, restored.backgroundColor)
        assertTrue(restored.backgroundColorIsPersisted)
    }

    @Test
    fun desktopCompatibleWriteRoundTripsOriginalResolutionPreference() {
        val codec = PurSceneCodec()
        val file = File.createTempFile("pockref-desktop-hd-preference", ".pur")
        file.deleteOnExit()
        val scene = PockRefScene(
            images = listOf(
                SceneImage(
                    id = "hd-image",
                    name = "hd",
                    mimeType = "image/png",
                    imageBytes = Base64.getDecoder().decode(
                        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScL+XQAAAABJRU5ErkJggg==",
                    ),
                    intrinsicWidth = 1,
                    intrinsicHeight = 1,
                    center = Offset.Zero,
                    displaySize = Size2D(1f, 1f),
                    showsOriginalResolution = true,
                ),
            ),
        )

        file.outputStream().use { output ->
            codec.writeDesktopCompatible(scene, output, File("build/tmp"))
        }

        assertTrue(codec.readFromFile(file).images.single().showsOriginalResolution)
    }

    @Test
    fun desktopCompatibleWriteKeepsOriginalResolutionPreferenceWithSortedImages() {
        val codec = PurSceneCodec()
        val file = File.createTempFile("pockref-desktop-hd-sorted", ".pur")
        file.deleteOnExit()
        val png = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScL+XQAAAABJRU5ErkJggg==",
        )
        val scene = PockRefScene(
            images = listOf(
                SceneImage(
                    id = "back",
                    name = "back",
                    mimeType = "image/png",
                    imageBytes = png,
                    intrinsicWidth = 1,
                    intrinsicHeight = 1,
                    center = Offset.Zero,
                    displaySize = Size2D(1f, 1f),
                    zIndex = 5,
                ),
                SceneImage(
                    id = "front",
                    name = "front",
                    mimeType = "image/png",
                    imageBytes = png,
                    intrinsicWidth = 1,
                    intrinsicHeight = 1,
                    center = Offset.Zero,
                    displaySize = Size2D(1f, 1f),
                    zIndex = 1,
                    showsOriginalResolution = true,
                ),
            ),
        )

        file.outputStream().use { output ->
            codec.writeDesktopCompatible(scene, output, File("build/tmp"))
        }

        val restored = codec.readFromFile(file).images.associateBy { it.name }
        assertTrue(restored.getValue("front").showsOriginalResolution)
        assertTrue(!restored.getValue("back").showsOriginalResolution)
    }

    @Test
    fun desktopCompatibleWritePreservesGifBytesAndMimeType() {
        val codec = PurSceneCodec()
        val file = File.createTempFile("pockref-desktop-gif", ".pur")
        file.deleteOnExit()
        val gif = Base64.getDecoder().decode(
            "R0lGODlhAQABAIAAAP///wAAACH5BAAAAAAALAAAAAABAAEAAAICRAEAOw==",
        )
        val scene = PockRefScene(
            images = listOf(
                SceneImage(
                    id = "gif",
                    name = "animated.gif",
                    mimeType = "image/gif",
                    imageBytes = gif,
                    intrinsicWidth = 1,
                    intrinsicHeight = 1,
                    center = Offset.Zero,
                    displaySize = Size2D(1f, 1f),
                ),
            ),
        )

        file.outputStream().use { output ->
            codec.writeDesktopCompatible(scene, output, File("build/tmp"))
        }

        val restored = codec.readFromFile(file).images.single()
        assertEquals("image/gif", restored.mimeType)
        assertArrayEquals(
            gif,
            codec.imageDecoder.readImageBytes(restored.imageByteOffset, restored.imageByteLength),
        )
    }

    @Test
    fun desktopCompatibleWritePreservesGifWhenExtensionContainsTrailerByte() {
        val codec = PurSceneCodec()
        val file = File.createTempFile("pockref-desktop-gif-comment", ".pur")
        file.deleteOnExit()
        val gif = Base64.getDecoder().decode(
            "R0lGODlhAQABAIAAAP///wAAACH5BAAAAAAALAAAAAABAAEAAAICRAEAOw==",
        ).toMutableList().apply {
            addAll(
                19,
                listOf(0x21, 0xFE, 0x01, 0x3B, 0x00).map { it.toByte() },
            )
        }.toByteArray()
        val scene = PockRefScene(
            images = listOf(
                SceneImage(
                    id = "gif",
                    name = "animated.gif",
                    mimeType = "image/gif",
                    imageBytes = gif,
                    intrinsicWidth = 1,
                    intrinsicHeight = 1,
                    center = Offset.Zero,
                    displaySize = Size2D(1f, 1f),
                ),
            ),
        )

        file.outputStream().use { output ->
            codec.writeDesktopCompatible(scene, output, File("build/tmp"))
        }

        val restored = codec.readFromFile(file).images.single()
        assertEquals("image/gif", restored.mimeType)
        assertArrayEquals(
            gif,
            codec.imageDecoder.readImageBytes(restored.imageByteOffset, restored.imageByteLength),
        )
    }

    @Test
    fun readFromFileKeepsPrfaImagesDiskBacked() {
        val imageBytes = byteArrayOf(1, 2, 3, 4, 5, 6)
        val codec = PurSceneCodec()
        val scene = PockRefScene(
            name = "custom-disk-backed",
            backgroundColor = Color(0xFF101820),
            images = listOf(
                SceneImage(
                    id = "img-1",
                    name = "sample",
                    mimeType = "image/png",
                    imageBytes = imageBytes,
                    intrinsicWidth = 2,
                    intrinsicHeight = 3,
                    center = Offset(10f, 20f),
                    displaySize = Size2D(30f, 40f),
                ),
            ),
        )
        val file = File.createTempFile("pockref_custom_disk_backed_", ".pur")
        file.deleteOnExit()
        val output = ByteArrayOutputStream()
        codec.write(scene, output)
        file.writeBytes(output.toByteArray())

        val restored = codec.readFromFile(file)

        val restoredImage = restored.images.single()
        assertEquals(0, restoredImage.imageBytes.size)
        assertTrue(restoredImage.imageByteOffset >= 0)
        assertEquals(imageBytes.size, restoredImage.imageByteLength)
        assertArrayEquals(
            imageBytes,
            codec.imageDecoder.readImageBytes(restoredImage.imageByteOffset, restoredImage.imageByteLength),
        )
    }

    @Test
    fun customWriteRoundTripsMarkup() {
        val codec = PurSceneCodec()
        val scene = markupScene("custom-markup")
        val file = File.createTempFile("pockref_custom_markup_", ".pur")
        file.deleteOnExit()
        file.outputStream().use { output -> codec.write(scene, output) }

        val restored = codec.readFromFile(file)

        assertMarkupRestored(restored)
    }

    @Test
    fun desktopCompatibleWriteBakesMarkupIntoImageLayerWithoutMarkupOrColorAdjustExtension() {
        val codec = PurSceneCodec()
        val scene = markupScene("desktop-markup")
        val file = File.createTempFile("pockref_desktop_markup_", ".pur")
        file.deleteOnExit()
        file.outputStream().use { output ->
            codec.writeDesktopCompatible(scene, output, File("build/tmp"))
        }

        val written = file.readBytes()
        assertEquals(-1, written.indexOfPattern(byteArrayOf('M'.code.toByte(), 'R'.code.toByte(), 'K'.code.toByte(), '1'.code.toByte())))
        assertEquals(-1, written.indexOfPattern(byteArrayOf('P'.code.toByte(), 'R'.code.toByte(), 'C'.code.toByte(), 'A'.code.toByte())))

        val restored = codec.readFromFile(file)

        assertTrue(restored.markupStrokes.isEmpty())
        assertTrue(restored.markupTexts.isEmpty())
        val overlay = restored.images.singleOrNull { it.name == "PockRef Markup" }
        requireNotNull(overlay) { "Expected desktop export to include a baked markup image layer" }
        assertEquals("image/png", overlay.mimeType)
        assertTrue(overlay.intrinsicWidth > 0)
        assertTrue(overlay.intrinsicHeight > 0)
        assertTrue(overlay.displaySize.width > 0f)
        assertTrue(overlay.displaySize.height > 0f)
    }

    private fun markupScene(name: String): PockRefScene =
        PockRefScene(
            name = name,
            markupStrokes = listOf(
                CanvasMarkupStroke(
                    id = "stroke-1",
                    color = Color(0xFFE91E63),
                    lineWidth = 6f,
                    points = listOf(Offset(1f, 2f), Offset(30f, 40f), Offset(50f, 60f)),
                ),
            ),
            markupTexts = listOf(
                CanvasMarkupText(
                    id = "text-1",
                    text = "hello\nmark",
                    position = Offset(12f, 34f),
                    color = Color(0xFF2196F3),
                    fontSize = 88f,
                ),
            ),
        )

    private fun assertMarkupRestored(scene: PockRefScene) {
        val stroke = scene.markupStrokes.single()
        assertEquals("stroke-1", stroke.id)
        assertEquals(Color(0xFFE91E63), stroke.color)
        assertEquals(6f, stroke.lineWidth, 0.001f)
        assertEquals(listOf(Offset(1f, 2f), Offset(30f, 40f), Offset(50f, 60f)), stroke.points)

        val text = scene.markupTexts.single()
        assertEquals("text-1", text.id)
        assertEquals("hello\nmark", text.text)
        assertEquals(Offset(12f, 34f), text.position)
        assertEquals(Color(0xFF2196F3), text.color)
        assertEquals(88f, text.fontSize, 0.001f)
    }

    private fun ByteArray.indexOfPattern(pattern: ByteArray): Int {
        if (pattern.isEmpty() || size < pattern.size) return -1
        outer@ for (index in 0..(size - pattern.size)) {
            for (patternIndex in pattern.indices) {
                if (this[index + patternIndex] != pattern[patternIndex]) continue@outer
            }
            return index
        }
        return -1
    }
}
