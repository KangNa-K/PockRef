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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AutoSaveContentHashTest {

    private val codec = PurSceneCodec()

    @Test
    fun identicalScene_hasStableHash() {
        val scene = sceneWithImage()

        assertEquals(hash(scene), hash(scene.copy()))
    }

    @Test
    fun everyPersistedSceneField_changesHash() {
        val scene = sceneWithImage()

        assertHashChanges(scene, scene.copy(name = "Renamed"))
        assertHashChanges(scene, scene.copy(backgroundColor = Color.Black))
        assertHashChanges(scene, scene.copy(defaultSpacing = Size2D(16f, 12f)))
        assertHashChanges(scene, scene.copy(defaultAlignment = ImageAlignment.TOP_LEFT))
        assertHashChanges(
            scene,
            scene.copy(markupStrokes = listOf(CanvasMarkupStroke(points = listOf(Offset.Zero, Offset(4f, 5f))))),
        )
        assertHashChanges(
            scene,
            scene.copy(markupTexts = listOf(CanvasMarkupText(text = "note", position = Offset(3f, 7f)))),
        )
    }

    @Test
    fun persistedImageFields_changeHash() {
        val scene = sceneWithImage()
        val image = scene.images.single()

        assertHashChanges(scene, scene.copy(images = listOf(image.copy(name = "renamed.png"))))
        assertHashChanges(scene, scene.copy(images = listOf(image.copy(mimeType = "image/jpeg"))))
        assertHashChanges(scene, scene.copy(images = listOf(image.copy(imageBytes = byteArrayOf(9, 8, 7)))))
        assertHashChanges(scene, scene.copy(images = listOf(image.copy(displaySize = Size2D(300f, 150f)))))
        assertHashChanges(scene, scene.copy(images = listOf(image.copy(locked = true))))
        assertHashChanges(scene, scene.copy(images = listOf(image.copy(cropRect = CropRect(0.1f, 0.2f, 0.8f, 0.9f)))))
        assertHashChanges(scene, scene.copy(images = listOf(image.copy(posterizeLevels = 2))))
    }

    private fun assertHashChanges(before: PockRefScene, after: PockRefScene) {
        assertNotEquals(hash(before), hash(after))
    }

    private fun hash(scene: PockRefScene): String {
        return AutoSaveHash.compute(codec, scene)
    }

    private fun sceneWithImage() = PockRefScene(
        version = 2,
        name = "Scene",
        backgroundColor = Color.White,
        images = listOf(
            SceneImage(
                id = "image-1",
                name = "image.png",
                mimeType = "image/png",
                imageBytes = byteArrayOf(1, 2, 3),
                intrinsicWidth = 100,
                intrinsicHeight = 50,
                center = Offset(10f, 20f),
                displaySize = Size2D(200f, 100f),
            ),
        ),
    )
}
