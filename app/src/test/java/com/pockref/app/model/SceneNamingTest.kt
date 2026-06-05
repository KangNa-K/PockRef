package com.pockref.app.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SceneNamingTest {

    @Test
    fun nextUntitled_isZeroIndexedAndHumanReadable() {
        // 1-based — "Untitled 1" feels more natural than
        // "Untitled 0" for the first scene. The product spec is
        // "Untitled {N} (递增)" with a 1-indexed counter.
        assertEquals("Untitled 1", SceneNaming.nextUntitled(1))
        assertEquals("Untitled 2", SceneNaming.nextUntitled(2))
        assertEquals("Untitled 99", SceneNaming.nextUntitled(99))
    }

    @Test
    fun fromPurFileName_stripsExtension() {
        assertEquals("v2", SceneNaming.fromPurFileName("v2.pur"))
        assertEquals("v2", SceneNaming.fromPurFileName("/some/dir/v2.pur"))
        assertEquals("My Reference", SceneNaming.fromPurFileName("My Reference.PUR"))
    }

    @Test
    fun fromPurFileName_keepsNameWithoutExtension() {
        // No recognized suffix — return the file's basename
        // untouched. We don't want to invent a name for a
        // mis-extensioned file.
        assertEquals("notes", SceneNaming.fromPurFileName("notes"))
        assertEquals("notes", SceneNaming.fromPurFileName("/dir/notes"))
    }

    @Test
    fun fromPurFileName_stripsPathSlashes() {
        // The whole point of the path-stripping is so the display
        // name doesn't show the user's directory layout.
        assertEquals("v2", SceneNaming.fromPurFileName("/very/long/path/v2.pur"))
    }

    @Test
    fun sanitizeForStorage_stripsTrailingDirtyMarker() {
        // Construct via concat so the test breaks if DIRTY_MARKER
        // changes shape (length, leading space, etc.) — that's
        // part of the visible contract.
        assertEquals("v2", SceneNaming.sanitizeForStorage("v2" + SceneNaming.DIRTY_MARKER))
        assertEquals("v2", SceneNaming.sanitizeForStorage("v2 •"))
    }

    @Test
    fun sanitizeForStorage_isIdempotent() {
        val clean = "v2"
        assertEquals(clean, SceneNaming.sanitizeForStorage(SceneNaming.sanitizeForStorage(clean)))
    }

    @Test
    fun sanitizeForStorage_leavesNameWithEmbeddedMarkerAlone() {
        // The marker is only the suffix. A name that just happens
        // to contain the bullet character in the middle (rare
        // but legal) should not be modified.
        assertEquals("a • b", SceneNaming.sanitizeForStorage("a • b"))
    }

    @Test
    fun displayName_appendsMarkerWhenDirty() {
        assertEquals("v2", SceneNaming.displayName("v2", isDirty = false))
        assertEquals("v2 •", SceneNaming.displayName("v2", isDirty = true))
    }

    @Test
    fun displayName_storedNameIsNeverTheMarker() {
        // Round-trip: stored name + dirty rendering → unwrap to
        // the original. The UI uses this to know the stored
        // value is always clean.
        val original = "Untitled 3"
        val displayed = SceneNaming.displayName(original, isDirty = true)
        assertEquals(original, SceneNaming.sanitizeForStorage(displayed))
    }
}
