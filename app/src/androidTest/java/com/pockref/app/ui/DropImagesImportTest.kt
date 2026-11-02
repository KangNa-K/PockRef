package com.pockref.app.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import android.os.SystemClock
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pockref.app.model.PockRefUiState
import com.pockref.app.model.SceneImage
import com.pockref.app.model.Size2D
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DropImagesImportTest {
    @Test fun repeatedImportsOnLargeCanvasPreserveViewAndOriginals() {
        verifyImport(existingCount = 300, rounds = 3)
    }

    @Test fun firstDropOnBlankCanvasPreservesReleasePoint() {
        verifyImport(existingCount = 0, rounds = 1)
    }

    private fun verifyImport(existingCount: Int, rounds: Int) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val application = instrumentation.targetContext.applicationContext as Application
        val files = (0..4).map { index ->
            File(application.cacheDir, "drop-regression-$index.png").also { file ->
                val bitmap = Bitmap.createBitmap(80 + index * 20, 60 + index * 30, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.BLUE)
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
        val originals = (0 until existingCount).map { index ->
            SceneImage(id = "old$index", name = "old$index", mimeType = "image/png",
                intrinsicWidth = 100, intrinsicHeight = 100,
                displaySize = Size2D(100f, 100f), center = Offset(index * 120f, index * 10000f))
        }
        val store = ViewModelStore()
        lateinit var vm: PockRefViewModel
        val pan = Offset(100f, -40f)
        val zoom = 0.25f
        instrumentation.runOnMainSync {
            vm = PockRefViewModel(application)
            store.put("drop-regression", vm)
            vm._uiState.value = PockRefUiState(blankCanvasOpen = existingCount == 0,
                images = originals, canvasScale = zoom, canvasOffset = pan,
                canvasViewportSize = Size2D(1200f, 800f), canvasOriginInWindow = Offset(20f, 60f))
        }
        try {
            repeat(rounds) { round ->
                val before = vm.uiState.value.images
                val releasePoint = Offset(320f + round * 80f, 260f + round * 40f)
                instrumentation.runOnMainSync {
                    vm.onAction(PockRefAction.DropImages(files.map { FileProvider.getUriForFile(application, "com.pockref.app.fileprovider", it) }, positionInWindow = releasePoint))
                }
                val deadline = SystemClock.uptimeMillis() + 15000
                while ((vm.uiState.value.isBusy || vm.uiState.value.images.size == before.size) && SystemClock.uptimeMillis() < deadline) {
                    SystemClock.sleep(25)
                }
                val after = vm.uiState.value
                assertFalse("Import did not finish", after.isBusy)
                assertEquals(before.size + files.size, after.images.size)
                assertEquals(before, after.images.take(before.size))
                assertEquals(pan, after.canvasOffset)
                assertEquals(zoom, after.canvasScale, 0f)
                val imported = after.images.drop(before.size)
                assertEquals(imported.map { it.id }.toSet(), after.selectedImageIds)
                val expected = layoutDroppedImages(imported.map { it.copy(scale = 1f, center = Offset.Zero) }, after.copy(images = before), releasePoint)
                imported.zip(expected).forEach { (actual, expectedImage) ->
                    assertEquals(expectedImage.center.x, actual.center.x, 0.01f)
                    assertEquals(expectedImage.center.y, actual.center.y, 0.01f)
                    assertTrue(actual.imageBytes.isNotEmpty())
                }
                val targetHeight = imported.first().displaySize.height * imported.first().scale
                if (before.isNotEmpty()) {
                    val existingHeight = before.first().displaySize.height * before.first().scale
                    assertEquals(existingHeight, targetHeight, 0.01f)
                }
                imported.forEach { image ->
                    assertEquals(targetHeight, image.displaySize.height * image.scale, 0.01f)
                    before.forEach { old ->
                        val a = imageRotatedFootprint(image)
                        val b = imageRotatedFootprint(old)
                        val overlapsX = kotlin.math.abs(image.center.x - old.center.x) < (a.width + b.width) / 2f - 0.01f
                        val overlapsY = kotlin.math.abs(image.center.y - old.center.y) < (a.height + b.height) / 2f - 0.01f
                        assertFalse("Imported image obscures existing image", overlapsX && overlapsY)
                    }
                }
                assertTrue(after.canUndo)
                instrumentation.runOnMainSync { vm.onAction(PockRefAction.Undo) }
                assertEquals(before, vm.uiState.value.images)
                instrumentation.runOnMainSync { vm.onAction(PockRefAction.Redo) }
                assertEquals(after.images, vm.uiState.value.images)
            }
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            files.forEach { it.delete() }
        }
    }
}
