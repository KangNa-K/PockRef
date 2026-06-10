package com.pockref.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [pickCoverTarget] — the cover-image resolver
 * extracted from the [com.pockref.app.ui.RecentFileGrid]
 * `LaunchedEffect` on 2026-06-11.
 *
 * **Why this test exists**: the user reported that after
 * "设为封面" → "取消封面", the start-page tile thumbnail did
 * not restore to the default canvas thumbnail. The previous
 * inline implementation in `RecentFileGrid` made it impossible
 * to assert "given these scene images and this coverImageId,
 * the picker returns this image" without a Compose runtime.
 *
 * The most likely root cause is the `maxByOrNull` tie-breaker
 * — Kotlin's [maxByOrNull] returns the **first** element with
 * the maximum value, so for a scene where all zIndex values
 * are equal (e.g. desktop-written .pur files), the default
 * thumbnail becomes the FIRST-transform image. If the user
 * happened to set that exact image as the cover, the visual
 * result of "set cover" and "clear cover" is identical, which
 * reads as "didn't restore". [pickCoverTargetRoundTripsToSame]
 * pins this down.
 */
class PickCoverTargetTest {

    private fun img(id: String, zIndex: Int, name: String = "image_$id") =
        SceneImage(
            id = id,
            name = name,
            mimeType = "image/png",
            imageBytes = byteArrayOf(),
            intrinsicWidth = 100,
            intrinsicHeight = 100,
            center = androidx.compose.ui.geometry.Offset(0f, 0f),
            displaySize = Size2D(100f, 100f),
            zIndex = zIndex,
        )

    // ── Core three-way branch ─────────────────────────────────────

    @Test
    fun `coverImageId null returns zIndex-max image (canvas topmost)`() {
        val images = listOf(
            img("0", zIndex = 0),
            img("1", zIndex = 2),
            img("2", zIndex = 1),
        )
        val target = pickCoverTarget(images, coverImageId = null)
        assertNotNull(target)
        assertEquals("1", target!!.id)  // zIndex=2 is the max
    }

    @Test
    fun `coverImageId set with matching image returns that image`() {
        val images = listOf(
            img("0", zIndex = 0),
            img("1", zIndex = 2),
            img("2", zIndex = 1),
        )
        // User picked the zIndex=1 middle image (id="2").
        val target = pickCoverTarget(images, coverImageId = "2")
        assertNotNull(target)
        assertEquals("2", target!!.id)
    }

    @Test
    fun `coverImageId set but no match falls back to zIndex-max`() {
        val images = listOf(
            img("0", zIndex = 0),
            img("1", zIndex = 2),
        )
        // Stale cover id (image removed from scene).
        val target = pickCoverTarget(images, coverImageId = "ghost")
        assertNotNull(target)
        assertEquals("1", target!!.id)
    }

    @Test
    fun `empty images list returns null regardless of coverImageId`() {
        assertNull(pickCoverTarget(emptyList(), coverImageId = null))
        assertNull(pickCoverTarget(emptyList(), coverImageId = "any"))
    }

    // ── The user-reported scenario ────────────────────────────────

    @Test
    fun `pickCoverTarget round-trips to same when cover IS the default`() {
        // This is the exact scenario behind the user-reported
        // "clear cover doesn't restore" symptom. The user's
        // chosen cover happens to be the same image the picker
        // would return as the default — so toggling the cover
        // off produces a visually identical thumbnail.
        //
        // NOTE: this test does NOT prove the bug is "just" this.
        // It documents that the picker behaves consistently
        // regardless of which branch the cover sits in — if
        // the user observed no thumbnail change after clearing,
        // it is consistent with "their cover was the same as the
        // default". A Compose runtime test would be needed to
        // confirm the [LaunchedEffect] actually re-fires.
        val images = listOf(
            img("0", zIndex = 0, name = "first"),
            img("1", zIndex = 1, name = "middle"),
            img("2", zIndex = 2, name = "last"),  // topmost
        )
        val userPickedCover = "2"  // user picked the topmost
        val coverOn = pickCoverTarget(images, coverImageId = userPickedCover)
        val coverOff = pickCoverTarget(images, coverImageId = null)
        assertEquals(coverOn!!.id, coverOff!!.id)
        // The visual result is identical — confirming the
        // symptom the user described. The fix isn't in
        // [pickCoverTarget] itself; it would be in the
        // "set cover" UI flow (a star/badge indicator — which
        // is already implemented in a sibling fix) or in
        // changing the default-thumbnail semantics.
    }

    @Test
    fun `pickCoverTarget changes when cover is not the default`() {
        // Counter-test: when the user's cover is NOT the
        // default, clearing it should produce a DIFFERENT
        // image. If this assertion ever fails, the picker has
        // a real bug (the visual swap should be observable).
        val images = listOf(
            img("0", zIndex = 0, name = "first"),
            img("1", zIndex = 1, name = "middle"),  // user picked this
            img("2", zIndex = 2, name = "last"),    // topmost default
        )
        val coverOn = pickCoverTarget(images, coverImageId = "1")
        val coverOff = pickCoverTarget(images, coverImageId = null)
        assertEquals("1", coverOn!!.id)
        assertEquals("2", coverOff!!.id)
        assertNotEquals(coverOn.id, coverOff.id)
    }

    private fun assertNotEquals(a: Any?, b: Any?) {
        if (a == b) throw AssertionError("expected $a != $b")
    }

    // ── Tie-breaker behavior (the hidden gotcha) ──────────────────

    @Test
    fun `maxByOrNull tie returns FIRST element (documents the gotcha)`() {
        // When all zIndex are equal, [maxByOrNull] returns the
        // FIRST element with the max value (i.e. the first
        // element in iteration order). For .pur files sourced
        // from `readFromFile`, iteration order is the
        // transform-id order in the .pur header.
        val images = listOf(
            img("a", zIndex = 0),
            img("b", zIndex = 0),
            img("c", zIndex = 0),
        )
        val target = pickCoverTarget(images, coverImageId = null)
        assertNotNull(target)
        assertEquals("a", target!!.id)  // NOT "c" — first wins on tie
    }

    @Test
    fun `desktop-style pur with all-zero zIndex picks first transform`() {
        // Simulates a .pur file written by PockRef-Desktop
        // where every transform's zLayer=0. The "default
        // thumbnail" is then the FIRST transform in the file,
        // not the LAST imported image as the user might
        // expect. If the user picked a NON-first image as
        // cover, clearing it should switch to the first image.
        val images = listOf(
            img("first", zIndex = 0),
            img("second", zIndex = 0),
            img("third", zIndex = 0),
        )
        val coverOn = pickCoverTarget(images, coverImageId = "third")
        val coverOff = pickCoverTarget(images, coverImageId = null)
        assertEquals("third", coverOn!!.id)
        assertEquals("first", coverOff!!.id)
    }
}
