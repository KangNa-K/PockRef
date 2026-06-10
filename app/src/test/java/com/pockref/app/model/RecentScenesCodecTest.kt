package com.pockref.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure JVM unit tests for [encodeRecentScenes] / [decodeRecentScenes].
 *
 * These two pure functions are the encode / decode half of the
 * recent-scenes prefs blob stored under [com.pockref.app.ui.PureRefViewModel]
 * `RECENT_SCENES_KEY` (constant
 * `com.pockref.app.ui.RECENT_SCENES_KEY = "recent_scenes_v1"`).
 * They were extracted from inline serialization in `loadRecentScenes`
 * (and 7 other call-sites in [PockRefViewModel]) on 2026-06-11 to
 * unblock JVM unit-testing of the round-trip.
 *
 * Background: the user reported a bug — "set an image as the
 * cover, then clear the cover, the start-page thumbnail doesn't
 * restore to the default canvas thumbnail". This test pins down
 * the encode / decode contract so a future regression in either
 * direction shows up as a test failure rather than a "the tile
 * is stuck on the wrong image" bug report.
 *
 * The bug is currently suspected to be on the Compose side
 * (the [androidx.compose.runtime.LaunchedEffect] that re-runs
 * when [RecentSceneEntry.coverImageId] changes — see
 * `RecentFileGrid.kt` near `LaunchedEffect(entry.uri, entry.coverImageId)`).
 * The viewmodel-side test here proves the prefs round-trip is
 * correct; the Compose-side test would need a `createComposeRule()`
 * (instrumentation test, not a JVM unit test).
 */
class RecentScenesCodecTest {

    private fun sampleEntry(
        uri: String = "content://pockref/scene/1",
        coverImageId: String? = null,
        pinned: Boolean = false,
        label: String = "Sample Scene",
    ) = RecentSceneEntry(
        uri = uri,
        label = label,
        source = "Saved",
        timestamp = 1_700_000_000_000L,
        filePath = "/sdcard/PockRef/scene1.pur",
        fileSize = 4_096L,
        pinned = pinned,
        coverImageId = coverImageId,
    )

    // ── 8-field round-trip ────────────────────────────────────────────

    @Test
    fun `round-trip preserves coverImageId when set`() {
        val original = listOf(sampleEntry(coverImageId = "img-42"))
        val blob = encodeRecentScenes(original)
        val decoded = decodeRecentScenes(blob)
        assertEquals(1, decoded.size)
        assertEquals("img-42", decoded[0].coverImageId)
    }

    @Test
    fun `round-trip preserves coverImageId null as empty-string-then-null`() {
        // The "clear cover" path: entry.coverImageId is null on the
        // input side, the prefs writer serializes it as "" (via
        // orEmpty()), and the prefs reader converts "" back to
        // null (via ifEmpty { null }). This is the round-trip the
        // user-reported bug depends on.
        val original = listOf(sampleEntry(coverImageId = null))
        val blob = encodeRecentScenes(original)
        // The 8th field MUST be the empty string, not the literal
        // "null" or "null" or any sentinel — the reader side
        // uses ifEmpty { null } to detect the cleared state.
        val fields = blob.split("|")
        assertEquals(8, fields.size)
        assertEquals("", fields[7])
        val decoded = decodeRecentScenes(blob)
        assertNull(
            "decoded coverImageId should be null after round-trip, " +
                "but was '${decoded[0].coverImageId}'",
            decoded[0].coverImageId,
        )
    }

    @Test
    fun `round-trip preserves all 8 fields`() {
        val original = listOf(
            RecentSceneEntry(
                uri = "content://pockref/scene/9",
                label = "Pin | cover | scene",   // contains "|" to test escaping
                source = "Imported",
                timestamp = 1_700_000_001_234L,
                filePath = "/sdcard/foo|bar.pur", // also contains "|"
                fileSize = 12_345L,
                pinned = true,
                coverImageId = "img-7",
            ),
        )
        val decoded = decodeRecentScenes(encodeRecentScenes(original))
        assertEquals(1, decoded.size)
        val e = decoded[0]
        assertEquals("content://pockref/scene/9", e.uri)
        assertEquals("Pin / cover / scene", e.label)  // "|" → "/"
        assertEquals("Imported", e.source)
        assertEquals(1_700_000_001_234L, e.timestamp)
        assertEquals("/sdcard/foo/bar.pur", e.filePath)
        assertEquals(12_345L, e.fileSize)
        assertEquals(true, e.pinned)
        assertEquals("img-7", e.coverImageId)
    }

    @Test
    fun `decode tolerates pre-coverImageId 7-field rows`() {
        // Existing users (pre-2026-06-10 install) have prefs blobs
        // written with the old 7-field format. The reader must
        // accept them and default coverImageId to null.
        val legacyBlob = "content://pockref/legacy|My Scene|Saved|1700000000000|/x/y|0|0"
        val decoded = decodeRecentScenes(legacyBlob)
        assertEquals(1, decoded.size)
        val e = decoded[0]
        assertEquals("content://pockref/legacy", e.uri)
        assertEquals("My Scene", e.label)
        assertEquals("Saved", e.source)
        assertEquals(1_700_000_000_000L, e.timestamp)
        assertEquals(false, e.pinned)
        assertNull(e.coverImageId)
    }

    @Test
    fun `decode returns empty list for null blob`() {
        assertEquals(emptyList<RecentSceneEntry>(), decodeRecentScenes(null))
    }

    @Test
    fun `decode returns empty list for empty blob`() {
        assertEquals(emptyList<RecentSceneEntry>(), decodeRecentScenes(""))
    }

    @Test
    fun `decode skips rows with fewer than 3 fields`() {
        // A malformed row (less than uri/label/source) must be
        // silently dropped — same behavior as the previous
        // inline loadRecentScenes implementation.
        val blob = """
            |content://pockref/ok|ok|ok|0||0|0|img-1
            |bad-row
            |content://pockref/also-ok|ok|ok
        """.trimMargin()
        val decoded = decodeRecentScenes(blob)
        assertEquals(2, decoded.size)
        assertEquals("content://pockref/ok", decoded[0].uri)
        assertEquals("img-1", decoded[0].coverImageId)
        assertEquals("content://pockref/also-ok", decoded[1].uri)
        assertNull(decoded[1].coverImageId)
    }

    @Test
    fun `multiple entries round-trip in declared order`() {
        val original = listOf(
            sampleEntry(uri = "u1", coverImageId = "img-1"),
            sampleEntry(uri = "u2", coverImageId = null),
            sampleEntry(uri = "u3", coverImageId = "img-3", pinned = true),
        )
        val decoded = decodeRecentScenes(encodeRecentScenes(original))
        assertEquals(listOf("u1", "u2", "u3"), decoded.map { it.uri })
        assertEquals(listOf("img-1", null, "img-3"), decoded.map { it.coverImageId })
        assertEquals(listOf(false, false, true), decoded.map { it.pinned })
    }

    @Test
    fun `pin-clear then set-cover round-trip is null in middle`() {
        // Simulates the user's exact bug-report flow at the VM
        // layer:
        //   1. set cover = "img-1"      → prefs has "img-1"
        //   2. clear cover (set null)   → prefs has ""
        //   3. set cover = "img-2"      → prefs has "img-2"
        val blob1 = encodeRecentScenes(listOf(sampleEntry(coverImageId = "img-1")))
        val blob2 = encodeRecentScenes(listOf(sampleEntry(coverImageId = null)))
        val blob3 = encodeRecentScenes(listOf(sampleEntry(coverImageId = "img-2")))
        val d1 = decodeRecentScenes(blob1).single()
        val d2 = decodeRecentScenes(blob2).single()
        val d3 = decodeRecentScenes(blob3).single()
        assertNotNull(d1.coverImageId)
        assertEquals("img-1", d1.coverImageId)
        assertNull(d2.coverImageId)
        assertNotNull(d3.coverImageId)
        assertEquals("img-2", d3.coverImageId)
    }
}
