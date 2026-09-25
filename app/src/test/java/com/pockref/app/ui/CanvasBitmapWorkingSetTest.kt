package com.pockref.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasBitmapWorkingSetTest {
    @Test
    fun evictsLeastRecentlyUsedUnprotectedEntry() {
        val set = CanvasBitmapWorkingSet(maximumEntryCount = 10, maximumCostBytes = 8)
        val cache = linkedMapOf(
            "old" to bitmap(),
            "visible" to bitmap(),
        )
        set.update(cache, setOf("visible"))
        cache["new"] = bitmap()

        assertEquals(setOf("old"), set.update(cache, setOf("visible")))
    }

    @Test
    fun protectedEntriesAreNeverEvicted() {
        val set = CanvasBitmapWorkingSet(maximumEntryCount = 1, maximumCostBytes = 4)
        val cache = mapOf("a" to bitmap(2, 2), "b" to bitmap(2, 2))

        assertTrue(set.update(cache, cache.keys).isEmpty())
    }

    private fun bitmap(width: Int = 1, height: Int = 1) =
        CanvasBitmapWorkingSet.BitmapInfo(level = 1, width = width, height = height)
}
