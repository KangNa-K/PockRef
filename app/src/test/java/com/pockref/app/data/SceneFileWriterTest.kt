package com.pockref.app.data

import androidx.compose.ui.geometry.Offset
import com.pockref.app.model.PockRefScene
import com.pockref.app.model.SceneImage
import com.pockref.app.model.Size2D
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.Base64

class SceneFileWriterTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun encodingFailure_neverOpensOrTruncatesOriginal() = runBlocking {
        val original = temporaryFolder.newFile().apply { writeText("original scene") }
        var opened = false
        val result = runCatching {
            writer().write(
                encode = {
                    it.write(byteArrayOf(1, 2, 3))
                    throw IOException("Image source is unavailable")
                },
                openDestination = {
                    opened = true
                    original.outputStream()
                },
            )
        }

        assertTrue(result.exceptionOrNull() is IOException)
        assertFalse(opened)
        assertEquals("original scene", original.readText())
    }

    @Test
    fun pureRefEncodingFailure_preservesOriginalEvenWhenBackupEncodingSucceeds() = runBlocking {
        val original = temporaryFolder.newFile().apply { writeText("original scene") }
        val codec = PurSceneCodec()
        val scene = PockRefScene(images = listOf(SceneImage(
            name = "undecodable image",
            mimeType = "image/png",
            imageBytes = byteArrayOf(1, 2, 3),
            intrinsicWidth = 1, intrinsicHeight = 1,
            center = Offset.Zero, displaySize = Size2D(1f, 1f),
        )))
        // Custom backups/hash accept bytes that the compatible writer cannot decode.
        codec.write(scene, OutputStream.nullOutputStream())
        val result = runCatching {
            writer().write(
                encode = { codec.writePureRefCompatible(scene, it, temporaryFolder.root) },
                openDestination = { original.outputStream() },
            )
        }

        assertTrue(result.isFailure)
        assertEquals("original scene", original.readText())
    }

    @Test
    fun emptyEncoding_doesNotOpenDestination() = runBlocking {
        var opened = false
        val result = runCatching {
            writer().write({}, { opened = true; OutputStream.nullOutputStream() })
        }
        assertTrue(result.isFailure)
        assertFalse(opened)
    }

    @Test
    fun encodedScene_isCompleteBeforeOriginalIsOpened_andRoundTrips() = runBlocking {
        val original = temporaryFolder.newFile().apply { writeText("original scene") }
        val codec = PurSceneCodec()
        val png = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a5WQAAAAASUVORK5CYII=",
        )
        val scene = PockRefScene(name = "saved", images = listOf(SceneImage(
            name = "image.png", mimeType = "image/png", imageBytes = png,
            intrinsicWidth = 1, intrinsicHeight = 1,
            center = Offset.Zero, displaySize = Size2D(1f, 1f),
        )))
        val expected = ByteArrayOutputStream().also {
            codec.write(scene, it)
        }.toByteArray()
        var encoded = false
        writer().write(
            encode = {
                codec.write(scene, it)
                encoded = true
            },
            openDestination = {
                assertTrue(encoded)
                assertEquals("original scene", original.readText())
                original.outputStream()
            },
        )
        assertArrayEquals(expected, original.readBytes())
        val reader = PurSceneCodec()
        try {
            val restored = original.inputStream().use(reader::read)
            assertEquals(1, restored.images.size)
            val image = restored.images.single()
            val bytes = image.imageBytes.takeIf { it.isNotEmpty() }
                ?: reader.imageLazyLoader.readImageBytes(image.imageByteOffset, image.imageByteLength)
            assertArrayEquals(png, bytes)
        } finally {
            reader.release()
        }
    }

    @Test
    fun cancellationDuringEncoding_doesNotOpenDestination() = runBlocking {
        var opened = false
        val job = launch {
            val owner = coroutineContext[Job]!!
            writer().write(
                encode = { it.write(1); owner.cancel() },
                openDestination = { opened = true; OutputStream.nullOutputStream() },
            )
        }
        job.join()
        assertTrue(job.isCancelled)
        assertFalse(opened)
    }

    @Test
    fun cancellationAfterTruncation_finishesCopy() = runBlocking {
        val original = temporaryFolder.newFile().apply { writeText("original scene") }
        val bytes = ByteArray(600_000) { (it % 251).toByte() }
        val job = launch {
            val owner = coroutineContext[Job]!!
            writer().write(
                encode = { it.write(bytes) },
                openDestination = {
                    original.outputStream().also { owner.cancel(CancellationException("Host destroyed")) }
                },
            )
        }
        job.join()
        assertTrue(job.isCancelled)
        assertArrayEquals(bytes, original.readBytes())
    }

    @Test
    fun failedDestination_canBeRetried() = runBlocking {
        val writer = writer()
        val result = runCatching {
            writer.write({ it.write(1) }, { throw IOException("Provider unavailable") })
        }
        assertTrue(result.exceptionOrNull() is IOException)
        val original = temporaryFolder.newFile()
        writer.write({ it.write(2) }, { original.outputStream() })
        assertArrayEquals(byteArrayOf(2), original.readBytes())
        assertTrue(temporaryFolder.root.resolve("scene_writes").listFiles()!!.isEmpty())
    }

    private fun writer() = SceneFileWriter(temporaryFolder.root.resolve("scene_writes"))
}
