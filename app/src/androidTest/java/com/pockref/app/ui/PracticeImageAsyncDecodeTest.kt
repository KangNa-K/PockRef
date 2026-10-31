package com.pockref.app.ui

import android.graphics.Bitmap
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.pockref.app.data.SceneImageDecoder
import com.pockref.app.model.PracticeState
import com.pockref.app.model.PracticeStatus
import com.pockref.app.model.SceneImage
import com.pockref.app.model.Size2D
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class PracticeImageAsyncDecodeTest {
    @get:Rule val compose = createComposeRule()

    /** Fake decode: blocks per image id until released; ids in [failing] return null. */
    private class GatedDecoder(vararg gatedIds: String, private val failing: Set<String> = emptySet()) {
        private val gates = ConcurrentHashMap<String, CountDownLatch>().apply {
            gatedIds.forEach { put(it, CountDownLatch(1)) }
        }
        val calls: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val decodedOnMain = AtomicBoolean(false)
        val maxConcurrent = AtomicInteger(0)
        private val active = AtomicInteger(0)

        fun release(id: String) = gates.getValue(id).countDown()

        val decode: (SceneImage, Int) -> Bitmap? = { image, _ ->
            calls += image.id
            if (Looper.myLooper() == Looper.getMainLooper()) decodedOnMain.set(true)
            maxConcurrent.accumulateAndGet(active.incrementAndGet()) { a, b -> maxOf(a, b) }
            try {
                gates[image.id]?.await(10, TimeUnit.SECONDS)
                if (image.id in failing) null else Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
            } finally {
                active.decrementAndGet()
            }
        }
    }

    private fun image(id: String) = SceneImage(
        id = id,
        name = "$id.png",
        mimeType = "image/png",
        intrinsicWidth = 4,
        intrinsicHeight = 4,
        center = Offset.Zero,
        displaySize = Size2D(4f, 4f),
    )

    private var current by mutableStateOf<SceneImage?>(null)

    private fun setFrameProbe(decoder: GatedDecoder) {
        compose.setContent {
            val frame = producePracticeBitmap(
                image = current,
                decodeSide = 1024,
                decoderKey = decoder,
                decode = decoder.decode,
            )
            Text("frame:${frame?.imageId ?: "none"}")
        }
    }

    private fun waitForCall(decoder: GatedDecoder, id: String) {
        compose.waitUntil(timeoutMillis = 5_000) { id in decoder.calls }
    }

    private fun waitForFrame(id: String) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasText("frame:$id")).fetchSemanticsNodes().size == 1
        }
    }

    @Test fun decodesOffMainThreadAndKeepsPreviousFrameUntilNextIsReady() {
        val decoder = GatedDecoder("A", "B")
        setFrameProbe(decoder)
        compose.runOnIdle { current = image("A") }
        waitForCall(decoder, "A")
        compose.onNodeWithText("frame:none").assertExists()

        decoder.release("A")
        waitForFrame("A")

        compose.runOnIdle { current = image("B") }
        waitForCall(decoder, "B")
        // While B is still decoding, A stays on screen (no black flash).
        compose.onNodeWithText("frame:A").assertExists()

        decoder.release("B")
        waitForFrame("B")
        assertFalse("decode ran on the main thread", decoder.decodedOnMain.get())
    }

    @Test fun failedDecodeClearsTheStaleFrame() {
        val decoder = GatedDecoder(failing = setOf("B"))
        setFrameProbe(decoder)
        compose.runOnIdle { current = image("A") }
        waitForFrame("A")
        compose.runOnIdle { current = image("B") }
        waitForCall(decoder, "B")
        waitForFrame("none")
    }

    @Test fun nullImageReleasesTheFrame() {
        val decoder = GatedDecoder()
        setFrameProbe(decoder)
        compose.runOnIdle { current = image("A") }
        waitForFrame("A")
        compose.runOnIdle { current = null }
        waitForFrame("none")
    }

    @Test fun requestSupersededWhileWaitingIsDropped() {
        val decoder = GatedDecoder("A", "B")
        setFrameProbe(decoder)
        compose.runOnIdle { current = image("A") }
        waitForCall(decoder, "A")
        // A is mid-decode. B queues behind the lock, then C replaces B.
        compose.runOnIdle { current = image("B") }
        compose.waitForIdle()
        compose.runOnIdle { current = image("C") }
        compose.waitForIdle()

        decoder.release("A")
        waitForFrame("C")
        assertEquals(listOf("A", "C"), decoder.calls.toList())
        assertEquals(1, decoder.maxConcurrent.get())
    }

    @Test fun practiceScreenShowsDecodedImageAndKeepsItWhenPaused() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val png = ByteArrayOutputStream().use { out ->
            Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF3366CC.toInt()) }
                .compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
        val scene = image("real").copy(imageBytes = png, intrinsicWidth = 64, intrinsicHeight = 48)
        val decoder = SceneImageDecoder(File(ctx.cacheDir, "practice-async-${System.nanoTime()}").apply { mkdirs() })
        var state by mutableStateOf(
            PracticeState(status = PracticeStatus.RUNNING, imageIds = listOf(scene.id), remainingMs = 0L),
        )
        compose.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    PracticeScreen(state = state, images = listOf(scene), imageDecoder = decoder, onAction = {})
                }
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasTestTag(PRACTICE_IMAGE_TAG))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.runOnIdle { state = state.copy(status = PracticeStatus.PAUSED) }
        // The bitmap is hoisted above RUNNING / PAUSED, so it is there on the
        // first PAUSED frame instead of being decoded again.
        compose.onNodeWithTag(PRACTICE_IMAGE_TAG).assertExists()
    }
}
