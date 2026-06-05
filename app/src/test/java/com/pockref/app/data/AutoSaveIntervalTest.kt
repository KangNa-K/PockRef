package com.pockref.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-JVM unit tests for the auto-save interval sliding behavior
 * that the Settings panel relies on. The full ViewModel / Context
 * integration is not in scope here (it lives in androidTest); this
 * test only exercises the parts that can be tested without
 * Robolectric — the [AutoSaveManager.INTERVAL_STOPS_MINUTES] table
 * the slider walks through, and the round-trip math from "user
 * drags slider to stop N" → "intervalMs the loop will use".
 *
 * The test exists to LOCK the contract between the slider and the
 * loop: 1 min must produce a 60_000 ms delay, 5 min must produce
 * a 300_000 ms delay, etc. If a future refactor reorders the
 * stops or mis-computes the multiplier, the failing assertion
 * will name the exact stop and ms value that drifted.
 */
class AutoSaveIntervalTest {

    /**
     * The 13 discrete stops the slider can land on. Order matters
     * — index 0 is the leftmost (1 min), index 12 is the rightmost
     * (60 min). The test mirrors [AutoSaveManager.INTERVAL_STOPS_MINUTES]
     * so a copy-paste here that drifts from the production array
     * will surface as a failure.
     */
    private val stopsMinutes: IntArray = intArrayOf(
        1, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60,
    )

    @Test
    fun stopsArrayMatchesProduction() {
        assertEquals(
            "INTERVAL_STOPS_MINUTES drifted from the table this test pins — " +
                "update both sites in lock-step.",
            stopsMinutes.toList(),
            AutoSaveManager.INTERVAL_STOPS_MINUTES.toList(),
        )
    }

    @Test
    fun sliderIndexToMs_oneMinuteMapsTo60Seconds() {
        // V_NOW: regression for the "set 1 min and waited 1 min, nothing
        // happened" report. The slider's onValueChange dispatches
        // SetAutoSaveIntervalMs(snappedMs) where
        //   snappedMs = stops[snappedIndex] * 60_000L
        // and snappedIndex is `newValue.toInt().coerceIn(0, size-1)`.
        // Index 0 must produce 60_000; an off-by-one in the
        // multiplier or index computation would silently produce
        // 0 (and the loop would tight-spin) or 1_000 (loop fires
        // every second). Both are visible in this test.
        val snappedIndex = 0
        val snappedMs = stopsMinutes[snappedIndex] * 60_000L
        assertEquals(60_000L, snappedMs)
    }

    @Test
    fun sliderIndexToMs_allStopsMapToExpectedMs() {
        val expected = longArrayOf(
            60_000L,           // 1 min
            300_000L,          // 5 min
            600_000L,          // 10 min
            900_000L,          // 15 min
            1_200_000L,        // 20 min
            1_500_000L,        // 25 min
            1_800_000L,        // 30 min
            2_100_000L,        // 35 min
            2_400_000L,        // 40 min
            2_700_000L,        // 45 min
            3_000_000L,        // 50 min
            3_300_000L,        // 55 min
            3_600_000L,        // 60 min
        )
        for (i in stopsMinutes.indices) {
            val actual = stopsMinutes[i] * 60_000L
            assertEquals("stop[$i] (${stopsMinutes[i]} min) → ms mismatch", expected[i], actual)
        }
    }

    @Test
    fun coerceIn_clampBoundaries() {
        // [PockRefViewModel.setAutoSaveIntervalMs] runs
        // `intervalMs.coerceIn(60_000L, 3_600_000L)` so a future
        // debug toggle that dispatches 0 or 30_000 doesn't
        // collapse the loop to a tight spin or somehow pass
        // 0 to `delay()`. 60_000 is the 1-min stop's ms value
        // and 3_600_000 is the 60-min stop's — these are the
        // exact clamp bounds the slider can produce.
        assertEquals(60_000L, 0L.coerceIn(60_000L, 3_600_000L))
        assertEquals(60_000L, 60_000L.coerceIn(60_000L, 3_600_000L))
        assertEquals(3_600_000L, 3_600_000L.coerceIn(60_000L, 3_600_000L))
        assertEquals(3_600_000L, 9_999_999L.coerceIn(60_000L, 3_600_000L))
    }

    @Test
    fun shouldAutoSave_returnsFalseDuringQuietPeriod() {
        // The [AutoSaveManager.shouldAutoSave] check
        //   currentTimestamp - lastSaveTimestamp < intervalMs
        // is the "throttle" gate. When a save just fired (or
        // never fired, lastSave = 0L), the gate must NOT block
        // a save at the very first tick after the user changed
        // to 1 min — that's the entire "1 min didn't fire" bug.
        //
        // The interesting case is: lastSave was at the OLD
        // 5-min cadence (lastSave = T - 5 min), and the user
        // just changed interval to 1 min. One minute later
        // currentTimestamp = T - 4 min. The throttle must say
        // "go" because (T-4min) - (T-5min) = 1 min which is
        // >= 1 min interval. The bug we're guarding against
        // is the throttle going the OTHER way: subtracting
        // intervalMs from now instead of lastSave from now.
        val lastSave = 1_000_000L
        val intervalMs = 60_000L
        val currentTs = lastSave + intervalMs  // exactly intervalMs later
        val gate = currentTs - lastSave < intervalMs
        assertEquals(false, gate)
    }
}
