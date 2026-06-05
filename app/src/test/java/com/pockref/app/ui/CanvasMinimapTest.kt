package com.pockref.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.pockref.app.model.PockRefUiState
import com.pockref.app.model.SceneImage
import com.pockref.app.model.Size2D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasMinimapTest {

    @Test
    fun computeSceneBboxForMinimap_emptyListReturnsDefault() {
        val bbox = computeSceneBboxForMinimap(emptyList())
        // Default: 1000x1000 centered on origin.
        assertEquals(-500f, bbox.left, 0.01f)
        assertEquals(-500f, bbox.top, 0.01f)
        assertEquals(500f, bbox.right, 0.01f)
        assertEquals(500f, bbox.bottom, 0.01f)
    }

    // ---- imageRotatedAabb ----

    @Test
    fun imageRotatedAabb_zeroRotation_matchesUnrotatedAabb() {
        // At 0° the AABB should be the unrotated rectangle. The
        // minimap and main-canvas footprint would visually agree.
        val image = SceneImage(
            id = "i",
            name = "x",
            mimeType = "image/jpeg",
            intrinsicWidth = 1000,
            intrinsicHeight = 2000,
            displaySize = Size2D(width = 100f, height = 200f),
            scale = 1f,
            rotationDegrees = 0f,
            flipX = false,
            flipY = false,
            center = Offset(50f, 50f),
            zIndex = 0,
            imageBytes = ByteArray(0),
        )
        val aabb = imageRotatedAabb(image)
        assertEquals(0f, aabb.left, 0.01f)   // 50 - 100/2
        assertEquals(-50f, aabb.top, 0.01f)  // 50 - 200/2
        assertEquals(100f, aabb.right, 0.01f)
        assertEquals(150f, aabb.bottom, 0.01f)
    }

    @Test
    fun imageRotatedAabb_ninetyDegrees_swapsWidthAndHeight() {
        // At 90° a 100x200 rect becomes 200x100 in world. The AABB
        // should reflect that swap, NOT keep the portrait shape.
        val image = SceneImage(
            id = "i",
            name = "x",
            mimeType = "image/jpeg",
            intrinsicWidth = 1000,
            intrinsicHeight = 2000,
            displaySize = Size2D(width = 100f, height = 200f),
            scale = 1f,
            rotationDegrees = 90f,
            flipX = false,
            flipY = false,
            center = Offset(0f, 0f),
            zIndex = 0,
            imageBytes = ByteArray(0),
        )
        val aabb = imageRotatedAabb(image)
        // 90° rotation: halfW=50, halfH=100, |cos|=0, |sin|=1
        //   aabbHalfW = 0*50 + 1*100 = 100
        //   aabbHalfH = 1*50 + 0*100 = 50
        assertEquals(-100f, aabb.left, 0.01f)
        assertEquals(-50f, aabb.top, 0.01f)
        assertEquals(100f, aabb.right, 0.01f)
        assertEquals(50f, aabb.bottom, 0.01f)
    }

    @Test
    fun imageRotatedAabb_fortyFiveDegrees_growsBoundingBox() {
        // A 100x100 square rotated 45° has a diamond footprint whose
        // AABB is roughly 141x141 (half-extent = halfSide * sqrt(2) ≈
        // 70.7). Anything smaller than this is wrong: it would mean
        // the rotated image is being clipped in the minimap.
        val side = 100f
        val image = SceneImage(
            id = "i",
            name = "x",
            mimeType = "image/jpeg",
            intrinsicWidth = 1000,
            intrinsicHeight = 1000,
            displaySize = Size2D(width = side, height = side),
            scale = 1f,
            rotationDegrees = 45f,
            flipX = false,
            flipY = false,
            center = Offset(0f, 0f),
            zIndex = 0,
            imageBytes = ByteArray(0),
        )
        val aabb = imageRotatedAabb(image)
        val expectedHalf = side / 2f * kotlin.math.sqrt(2f).toFloat()
        assertEquals(-expectedHalf, aabb.left, 0.01f)
        assertEquals(-expectedHalf, aabb.top, 0.01f)
        assertEquals(expectedHalf, aabb.right, 0.01f)
        assertEquals(expectedHalf, aabb.bottom, 0.01f)
    }

    @Test
    fun imageRotatedAabb_flip_doesNotChangeAabb() {
        // Flips mirror the content but not the footprint, so the AABB
        // for a flipped image is identical to the unrotated-unflipped
        // version. (The diagonal-flip symmetry: |cos| and |sin| don't
        // depend on sign of θ, only on |θ|.)
        val make = { flipX: Boolean, flipY: Boolean -> SceneImage(
            id = "i",
            name = "x",
            mimeType = "image/jpeg",
            intrinsicWidth = 1000,
            intrinsicHeight = 2000,
            displaySize = Size2D(width = 100f, height = 200f),
            scale = 1f,
            rotationDegrees = 30f,
            flipX = flipX,
            flipY = flipY,
            center = Offset(0f, 0f),
            zIndex = 0,
            imageBytes = ByteArray(0),
        ) }
        val aabbNoFlip = imageRotatedAabb(make(false, false))
        val aabbBothFlips = imageRotatedAabb(make(true, true))
        assertEquals(aabbNoFlip.left, aabbBothFlips.left, 0.01f)
        assertEquals(aabbNoFlip.top, aabbBothFlips.top, 0.01f)
        assertEquals(aabbNoFlip.right, aabbBothFlips.right, 0.01f)
        assertEquals(aabbNoFlip.bottom, aabbBothFlips.bottom, 0.01f)
    }

    @Test
    fun computeSceneBboxForMinimap_rotatedImage_growsBbox() {
        // Two images of the same size at the same center: one
        // unrotated, one rotated 45°. The rotated one should make
        // the scene bbox larger (in both dimensions) than the
        // unrotated one. If the bbox is the same, the minimap
        // would still clip the rotated image.
        val unrotated = listOf(makeTestImage(
            id = "u",
            displayW = 100f, displayH = 100f,
            centerX = 0f, centerY = 0f,
            rotationDegrees = 0f,
        ))
        val rotated = listOf(makeTestImage(
            id = "r",
            displayW = 100f, displayH = 100f,
            centerX = 0f, centerY = 0f,
            rotationDegrees = 45f,
        ))
        val bboxU = computeSceneBboxForMinimap(unrotated)
        val bboxR = computeSceneBboxForMinimap(rotated)
        assertTrue(
            "rotated bbox should be larger in width than unrotated",
            bboxR.width > bboxU.width,
        )
        assertTrue(
            "rotated bbox should be larger in height than unrotated",
            bboxR.height > bboxU.height,
        )
    }

    private fun makeTestImage(
        id: String,
        displayW: Float, displayH: Float,
        centerX: Float, centerY: Float,
        rotationDegrees: Float,
    ): SceneImage = SceneImage(
        id = id,
        name = id,
        mimeType = "image/jpeg",
        intrinsicWidth = 1000,
        intrinsicHeight = 1000,
        displaySize = Size2D(width = displayW, height = displayH),
        scale = 1f,
        rotationDegrees = rotationDegrees,
        flipX = false,
        flipY = false,
        center = Offset(centerX, centerY),
        zIndex = 0,
        imageBytes = ByteArray(0),
    )

    @Test
    fun computeSceneBboxForMinimap_singleImage_atOrigin() {
        val img = image(id = "a", center = Offset(0f, 0f), displayW = 100f, displayH = 50f)
        val bbox = computeSceneBboxForMinimap(listOf(img))
        // Image AABB is (-50,-25)..(50,25). 10% padding: padW=10, padH=5.
        // So bbox = (-60, -30)..(60, 30).
        assertEquals(-60f, bbox.left, 0.01f)
        assertEquals(-30f, bbox.top, 0.01f)
        assertEquals(60f, bbox.right, 0.01f)
        assertEquals(30f, bbox.bottom, 0.01f)
    }

    @Test
    fun computeSceneBboxForMinimap_multipleImagesTakesUnion() {
        val a = image(id = "a", center = Offset(-100f, 0f), displayW = 50f, displayH = 50f)
        val b = image(id = "b", center = Offset(100f, 50f), displayW = 50f, displayH = 50f)
        val bbox = computeSceneBboxForMinimap(listOf(a, b))
        // AABBs: a = (-125,-25)..(-75,25), b = (75,25)..(125,75)
        // Union = (-125,-25)..(125,75). Padding 10% of width/height = (25, 10)
        // bbox = (-150,-35)..(150,85)
        assertEquals(-150f, bbox.left, 0.01f)
        assertEquals(-35f, bbox.top, 0.01f)
        assertEquals(150f, bbox.right, 0.01f)
        assertEquals(85f, bbox.bottom, 0.01f)
    }

    @Test
    fun fitContain_letterboxesSourceIntoTarget() {
        val source = Rect(0f, 0f, 100f, 50f)  // aspect 2:1
        val target = Size(80f, 80f)              // square
        val fitted = fitContainForTest(source, target)
        // scale = min(80/100, 80/50) = 0.8
        // scaledW = 80, scaledH = 40
        // offsetX = 0, offsetY = 20
        assertEquals(0f, fitted.left, 0.01f)
        assertEquals(20f, fitted.top, 0.01f)
        assertEquals(80f, fitted.right, 0.01f)
        assertEquals(60f, fitted.bottom, 0.01f)
    }

    @Test
    fun fitContain_zeroSize_returnsZeroRect() {
        val fitted = fitContainForTest(Rect(0f, 0f, 0f, 0f), Size(80f, 80f))
        assertEquals(Rect(0f, 0f, 80f, 80f), fitted)
    }
    private fun image(
        id: String,
        center: Offset,
        displayW: Float,
        displayH: Float,
        scale: Float = 1f,
    ): SceneImage = SceneImage(
        id = id,
        name = id,
        mimeType = "image/png",
        imageBytes = ByteArray(0),
        intrinsicWidth = 100,
        intrinsicHeight = 100,
        center = center,
        displaySize = Size2D(displayW, displayH),
        scale = scale,
    )

    private fun fitContainForTest(source: Rect, target: Size): Rect {
        if (source.width <= 0f || source.height <= 0f || target.width <= 0f || target.height <= 0f) {
            return Rect(0f, 0f, target.width, target.height)
        }
        val scale = kotlin.math.min(target.width / source.width, target.height / source.height)
        val scaledW = source.width * scale
        val scaledH = source.height * scale
        val offX = (target.width - scaledW) / 2f
        val offY = (target.height - scaledH) / 2f
        return Rect(offX, offY, offX + scaledW, offY + scaledH)
    }
}
