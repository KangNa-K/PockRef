package com.pockref.app.data

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.pockref.app.model.CanvasMarkupStroke
import com.pockref.app.model.CanvasMarkupText
import com.pockref.app.model.PockRefScene
import com.pockref.app.model.SceneImage
import com.pockref.app.model.Size2D
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PurSceneCodecCustomFormatTest {

    @Test
    fun customFormat_roundTripsOriginalResolutionFlag() {
        val codec = PurSceneCodec()
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
        val output = ByteArrayOutputStream()

        codec.write(scene, output)

        assertTrue(
            codec.read(ByteArrayInputStream(output.toByteArray()))
                .images
                .single()
                .showsOriginalResolution,
        )
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
    fun customV2Format_defaultsOriginalResolutionToFalse() {
        val bytes = ByteArrayOutputStream().apply {
            write("PRFA".toByteArray())
            writeIntLeForTest(2)
            writeStringForTest("v2")
            writeLongLeForTest(Color.White.value.toLong())
            writeIntLeForTest(1)
            writeStringForTest("image")
            writeStringForTest("image")
            writeStringForTest("image/png")
            writeIntLeForTest(3)
            write(byteArrayOf(1, 2, 3))
            writeIntLeForTest(10)
            writeIntLeForTest(10)
            writeFloatLeForTest(0f)
            writeFloatLeForTest(0f)
            writeFloatLeForTest(10f)
            writeFloatLeForTest(10f)
            writeFloatLeForTest(0f)
            writeFloatLeForTest(0f)
            writeFloatLeForTest(0f)
            writeFloatLeForTest(0f)
            writeFloatLeForTest(1f)
            write(0)
            write(0)
            writeIntLeForTest(0)
            write(0)
            write(0)
        }.toByteArray()

        val restored = PurSceneCodec().read(ByteArrayInputStream(bytes))

        assertTrue(!restored.images.single().showsOriginalResolution)
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
    fun desktopCompatibleWriteBakesMarkupIntoImageLayerWithoutPrivateExtension() {
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

    private fun ByteArrayOutputStream.writeStringForTest(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeIntLeForTest(bytes.size)
        write(bytes)
    }

    private fun ByteArrayOutputStream.writeIntLeForTest(value: Int) {
        write(
            ByteBuffer.allocate(Int.SIZE_BYTES)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(value)
                .array(),
        )
    }

    private fun ByteArrayOutputStream.writeLongLeForTest(value: Long) {
        write(
            ByteBuffer.allocate(Long.SIZE_BYTES)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putLong(value)
                .array(),
        )
    }

    private fun ByteArrayOutputStream.writeFloatLeForTest(value: Float) {
        writeIntLeForTest(value.toBits())
    }
}
