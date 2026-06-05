package com.pockref.app.data

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.pockref.app.model.PockRefScene
import com.pockref.app.model.SceneImage
import com.pockref.app.model.Size2D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class HostSaveRoundTripTest {
    @Test
    @Ignore("Requires host test fixtures at ../测试文件. Re-enable after populating that folder, " +
        "or rewrite to use TemporaryFolder + synthetic PNG fixtures.")
    fun savesSceneFromLocalTestFolderImages() {
        val inputDir = File("../测试文件")
        val files = inputDir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() == "png" }
            ?.sortedBy { it.name }
            .orEmpty()

        require(files.isNotEmpty()) { "No image files found in $inputDir" }

        val images = files.mapIndexedNotNull { index, file ->
            val bytes = file.readBytes()
            val (width, height) = probePngSize(bytes) ?: return@mapIndexedNotNull null
            val column = index % 4
            val row = index / 4
            val displayWidth = width.toFloat().coerceAtMost(720f)
            val displayHeight = height.toFloat() * (displayWidth / width.toFloat())

            SceneImage(
                name = file.name,
                mimeType = "image/png",
                imageBytes = bytes,
                intrinsicWidth = width,
                intrinsicHeight = height,
                center = Offset(200f + column * 420f, 200f + row * 320f),
                displaySize = Size2D(displayWidth, displayHeight),
                zIndex = index,
            )
        }

        val scene = PockRefScene(
            name = "host-test-folder-roundtrip",
            backgroundColor = Color(0xFF111111),
            images = images,
        )

        val output = File("build/tmp/host-test-folder-roundtrip.pur")
        output.parentFile?.mkdirs()
        FileOutputStream(output).use { PurSceneCodec().writeLegacy(scene, it) }

        val restored = FileInputStream(output).use { PurSceneCodec().read(it) }
        assertEquals(images.size, restored.images.size)

        images.zip(restored.images).forEach { (expected, actual) ->
            assertEquals(expected.name, actual.name)
            assertEquals(expected.intrinsicWidth, actual.intrinsicWidth)
            assertEquals(expected.intrinsicHeight, actual.intrinsicHeight)
            assertEquals(expected.center.x, actual.center.x, 0.01f)
            assertEquals(expected.center.y, actual.center.y, 0.01f)
            assertEquals(expected.displaySize.width, actual.displaySize.width, 0.02f)
            assertEquals(expected.displaySize.height, actual.displaySize.height, 0.02f)
            assertEquals(
                expected.displaySize.width / expected.displaySize.height,
                actual.displaySize.width / actual.displaySize.height,
                0.0001f,
            )
            assertTrue(actual.imageBytes.isNotEmpty())
        }
    }

    private fun probePngSize(bytes: ByteArray): Pair<Int, Int>? {
        if (bytes.size < 24) return null
        if (!bytes.copyOfRange(0, 8).contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))) {
            return null
        }
        val width = ByteBuffer.wrap(bytes, 16, 4).order(ByteOrder.BIG_ENDIAN).int
        val height = ByteBuffer.wrap(bytes, 20, 4).order(ByteOrder.BIG_ENDIAN).int
        return if (width > 0 && height > 0) width to height else null
    }
}
