package com.pockref.app.model

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM unit tests for the [SceneImage] data class.
 *
 * [SceneImage] carries the raw image payload ([imageBytes]) alongside the
 * renderable metadata. Two images with the same metadata but different
 * payload references or content must NOT be considered equal — the
 * equality contract underpins the bitmap cache key (see PockRefApp.kt's
 * imageBytes-driven cache), so any change to [imageBytes] must always
 * invalidate the cache.
 *
 * Note on Kotlin data class semantics: the data class `equals` for
 * `ByteArray` properties uses reference equality (because arrays in
 * Kotlin don't have value-based equals by default). So even two
 * byte arrays with identical content are NOT equal unless they are the
 * same instance. This test suite documents that behavior and verifies
 * the cache-relevant properties:
 * - Same reference + same content → equal
 * - Different reference (regardless of content) → not equal
 * - Different content (regardless of reference) → not equal
 */
class SceneImageTest {

    private fun sample(
        id: String = "img-1",
        bytes: ByteArray = byteArrayOf(1, 2, 3, 4),
        intrinsicWidth: Int = 100,
        intrinsicHeight: Int = 80,
        center: Offset = Offset(50f, 40f),
        displaySize: Size2D = Size2D(200f, 160f),
        zIndex: Int = 0,
    ) = SceneImage(
        id = id,
        name = "sample",
        mimeType = "image/png",
        imageBytes = bytes,
        intrinsicWidth = intrinsicWidth,
        intrinsicHeight = intrinsicHeight,
        center = center,
        displaySize = displaySize,
        zIndex = zIndex,
    )

    @Test
    fun equalImages_areEqual() {
        // Use the SAME byte array reference so the data class equals
        // (which falls back to == for ByteArray) is satisfied.
        val sharedBytes = byteArrayOf(9, 8, 7)
        val a = sample(bytes = sharedBytes)
        val b = sample(bytes = sharedBytes)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun differentIntrinsicWidth_notEqual() {
        val a = sample(intrinsicWidth = 100)
        val b = sample(intrinsicWidth = 200)
        assertNotEquals(a, b)
    }

    @Test
    fun differentIntrinsicHeight_notEqual() {
        val a = sample(intrinsicHeight = 80)
        val b = sample(intrinsicHeight = 60)
        assertNotEquals(a, b)
    }

    @Test
    fun differentCenter_notEqual() {
        val a = sample(center = Offset(50f, 40f))
        val b = sample(center = Offset(51f, 40f))
        assertNotEquals(a, b)
    }

    @Test
    fun differentDisplaySize_notEqual() {
        val a = sample(displaySize = Size2D(200f, 160f))
        val b = sample(displaySize = Size2D(201f, 160f))
        assertNotEquals(a, b)
    }

    @Test
    fun differentZIndex_notEqual() {
        val a = sample(zIndex = 0)
        val b = sample(zIndex = 1)
        assertNotEquals(a, b)
    }

    @Test
    fun differentId_notEqual() {
        val a = sample(id = "img-1")
        val b = sample(id = "img-2")
        assertNotEquals(a, b)
    }

    // ----- The critical bitmap-cache invariant --------------------------------

    @Test
    fun differentImageBytesReference_notEqual() {
        // Even with the same content, a different ByteArray reference
        // makes the images not equal (Kotlin data class behavior).
        val a = sample(bytes = byteArrayOf(1, 2, 3, 4))
        val b = sample(bytes = byteArrayOf(1, 2, 3, 4))
        assertNotEquals(
            "Different ByteArray references are not equal even with the " +
                "same content (Kotlin data class quirk).",
            a, b,
        )
    }

    @Test
    fun differentImageBytesContent_notEqual() {
        val a = sample(bytes = byteArrayOf(1, 2, 3, 4))
        val b = sample(bytes = byteArrayOf(1, 2, 3, 5)) // last byte differs
        assertNotEquals(a, b)
    }

    @Test
    fun differentImageBytesLength_notEqual() {
        val a = sample(bytes = byteArrayOf(1, 2, 3, 4))
        val b = sample(bytes = byteArrayOf(1, 2, 3, 4, 5))
        assertNotEquals(a, b)
    }

    @Test
    fun emptyVsNonEmptyImageBytes_notEqual() {
        val a = sample(bytes = ByteArray(0))
        val b = sample(bytes = byteArrayOf(0))
        assertNotEquals(a, b)
    }

    @Test
    fun sameBytesReference_equalsRegardlessOfContent() {
        // The bitmap cache key is the SceneImage itself (or a derivative
        // hash). If the cache uses identity (==) for the byte array,
        // then a re-decoded image with the same byte array reference
        // will hit the cache even if the content logically changed.
        // Verify that the data class only equals when the *reference*
        // is identical.
        val shared = byteArrayOf(42, 13)
        val a = sample(bytes = shared)
        val b = sample(bytes = shared)
        // Even if we mutate `shared` after construction, `a` and `b`
        // should still be equal because their byte references are the
        // same (data class equals is shallow on arrays).
        shared[0] = 99
        assertEquals(a, b)
    }

    // ----- copy() independence ------------------------------------------------

    @Test
    fun copy_returnsNewInstance() {
        val original = sample(bytes = byteArrayOf(1, 2, 3))
        val updated = original.copy(scale = 2.0f)
        assertNotSame(original, updated)
    }

    @Test
    fun copy_changesOnlyTheModifiedField() {
        val original = sample(bytes = byteArrayOf(1, 2, 3))
        val updated = original.copy(scale = 2.0f)
        // Modified field changes.
        assertEquals(2.0f, updated.scale, 0f)
        // Other fields carry over.
        assertEquals(original.id, updated.id)
        assertEquals(original.intrinsicWidth, updated.intrinsicWidth)
        assertEquals(original.center, updated.center)
        assertEquals(original.displaySize, updated.displaySize)
        // Original is not mutated.
        assertEquals(1.0f, original.scale, 0f)
    }

    @Test
    fun copy_sharesByteArrayReference() {
        // Data class copy is shallow: the byte array is shared.
        val original = sample(bytes = byteArrayOf(1, 2, 3))
        val updated = original.copy(scale = 2.0f)
        assertTrue(
            "Byte array should be shared between original and copy",
            original.imageBytes === updated.imageBytes,
        )
    }

    @Test
    fun copy_withDifferentBytes_breaksEquality() {
        val original = sample(bytes = byteArrayOf(1, 2, 3))
        val updated = original.copy(imageBytes = byteArrayOf(9, 9, 9))
        assertNotEquals(original, updated)
        // Original is unaffected by the copy mutation.
        assertEquals(1.toByte(), original.imageBytes[0])
        assertEquals(9.toByte(), updated.imageBytes[0])
    }

    @Test
    fun copy_preservesIdWhenNotChanged() {
        val original = sample(id = "fixed-id")
        val updated = original.copy(zIndex = 5)
        assertEquals("fixed-id", updated.id)
    }

    // ----- Defaults -----------------------------------------------------------

    @Test
    fun sceneImage_defaultId_isRandomUuid() {
        // Hit the data class directly so we get the production default
        // (UUID.randomUUID()), not whatever the test helper hard-codes.
        val a = SceneImage(
            name = "a",
            mimeType = "image/png",
            intrinsicWidth = 1,
            intrinsicHeight = 1,
            center = Offset.Zero,
            displaySize = Size2D(1f, 1f),
        )
        val b = SceneImage(
            name = "b",
            mimeType = "image/png",
            intrinsicWidth = 1,
            intrinsicHeight = 1,
            center = Offset.Zero,
            displaySize = Size2D(1f, 1f),
        )
        // Two independently-constructed SceneImages with the default id
        // (UUID.randomUUID()) must not collide.
        assertNotEquals(a.id, b.id)
        assertTrue("UUID should be non-empty", a.id.isNotBlank())
        // Length of a canonical UUID is 36.
        assertEquals(36, a.id.length)
    }

    @Test
    fun defaultScale_isOne() {
        assertEquals(1f, sample().scale, 0f)
    }

    @Test
    fun defaultRotation_isZero() {
        assertEquals(0f, sample().rotationDegrees, 0f)
    }

    @Test
    fun defaultFlips_areFalse() {
        val s = sample()
        assertFalse(s.flipX)
        assertFalse(s.flipY)
    }

    @Test
    fun defaultColorAdjust_isIdentity() {
        val s = sample()
        assertEquals(ColorAdjust(), s.colorAdjust)
    }

    @Test
    fun defaultImageBytes_isEmpty() {
        val s = SceneImage(
            name = "x",
            mimeType = "image/png",
            intrinsicWidth = 1,
            intrinsicHeight = 1,
            center = Offset.Zero,
            displaySize = Size2D(1f, 1f),
        )
        assertEquals(0, s.imageBytes.size)
    }

    // ----- ColorAdjust equality sanity ----------------------------------------

    @Test
    fun colorAdjust_equality() {
        assertEquals(ColorAdjust(), ColorAdjust())
        assertNotEquals(
            ColorAdjust(brightness = 0.1f),
            ColorAdjust(brightness = 0.2f),
        )
        assertEquals(
            ColorAdjust(brightness = 0.5f, contrast = 0.2f, saturation = 0.1f),
            ColorAdjust(brightness = 0.5f, contrast = 0.2f, saturation = 0.1f),
        )
    }

    @Test
    fun sceneImage_equalityRespectsColorAdjust() {
        val a = sample().copy(colorAdjust = ColorAdjust(saturation = -1f))
        val b = sample().copy(colorAdjust = ColorAdjust(saturation = 0f))
        assertNotEquals(a, b)
    }

    // ----- Initial state defaults (for resetTransform) ------------------------

    @Test
    fun initialCenter_defaultsToCenter() {
        val s = sample(center = Offset(11f, 22f))
        // Default initialCenter is computed as `center = center` at the
        // call site, so by structural equality initialCenter == center.
        assertEquals(Offset(11f, 22f), s.initialCenter)
    }

    @Test
    fun initialDisplaySize_defaultsToDisplaySize() {
        val s = sample(displaySize = Size2D(33f, 44f))
        assertEquals(Size2D(33f, 44f), s.initialDisplaySize)
    }

    @Test
    fun resetTransformCandidate_couldRoundTrip() {
        // Simulate a "user rotated then reset" by mutating the image and
        // re-applying the initial transform. After the reset, the image
        // equals the original (modulo the auto-generated id).
        val sharedBytes = byteArrayOf(42)
        val original = sample(id = "fixed", bytes = sharedBytes)
        val rotated = original.copy(rotationDegrees = 90f, scale = 1.5f)
        assertNotEquals(original, rotated)
        val reset = rotated.copy(
            center = rotated.initialCenter,
            displaySize = rotated.initialDisplaySize,
            rotationDegrees = 0f,
            scale = 1f,
            flipX = false,
            flipY = false,
        )
        assertEquals(original, reset)
        assertEquals("Reset should restore original scale", 1f, reset.scale, 0f)
    }
}
