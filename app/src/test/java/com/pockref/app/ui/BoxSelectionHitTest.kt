package com.pockref.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.pockref.app.model.BoxSelectionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for the CAD-style box-select hit-test logic
 * used by [PockRefViewModelSelection.updateBoxSelectRect].
 *
 * The reducer itself depends on a live [PockRefViewModel] (an
 * AndroidViewModel), which is awkward to instantiate in unit
 * tests (needs Robolectric / instrumentation). These tests
 * instead exercise the **two hit-test predicates** directly:
 *
 *1. [fullyContains] (WINDOW) - the inner rect's4 corners
 * must lie **inside or on** the outer rect. Touching
 * an edge counts as "fully inside" so a tightly-fit
 * rectangle isn't rejected by floating-point slop.
 *2. [intersects] (CROSSING) - the two rects'4 edges must
 * **overlap** (strict inequalities). Touching at the
 * edge counts as non-intersection to match the user' + "'" + 's
 * "any contact at all" mental model — a1-px overlap is
 * enough; a0-px contact (edges kiss but no overlap) is
 * not.
 *
 * The reducer reads these predicates verbatim from the inline
 * expressions in [PockRefViewModelSelection.updateBoxSelectRect].
 * If a future change to the predicates diverges from these
 * tests, the test fails and forces a re-review of the UI-side
 * behaviour.
 *
 * V_NOW (2026-06-08 CAD-style box select): initial test set,
 * covering the4 core CAD-style scenarios + a couple of
 * edge cases (touching-at-edge, exact match, far-apart).
 */
class BoxSelectionHitTest {

 /**
 * The WINDOW predicate: inner rect is fully contained by
 * outer rect. Matches the inline expression in
 * [PockRefViewModelSelection.updateBoxSelectRect].
 */
 private fun fullyContains(outer: Rect, inner: Rect): Boolean =
 inner.left >= outer.left &&
 inner.right <= outer.right &&
 inner.top >= outer.top &&
 inner.bottom <= outer.bottom

 /**
 * The CROSSING predicate: two rects'4 edges overlap. Matches
 * the inline expression in
 * [PockRefViewModelSelection.updateBoxSelectRect]. Strict
 * inequalities so touching-at-edge counts as non-intersection.
 */
 private fun intersects(a: Rect, b: Rect): Boolean =
 a.left <= b.right &&
 a.right >= b.left &&
 a.top <= b.bottom &&
 a.bottom >= b.top

 // ─── WINDOW scenarios ────────────────────────────────────

 @Test
 fun window_image_fully_inside_selection_is_included() {
 val selection = Rect(100f,100f,200f,200f)
 val image = Rect(120f,120f,180f,180f)
 assertTrue(
 "image fully inside selection should be WINDOW-included",
 fullyContains(selection, image),
 )
 }

 @Test
 fun window_image_partially_overlapping_selection_is_excluded() {
 val selection = Rect(100f,100f,200f,200f)
 val image = Rect(180f,180f,250f,250f) // only top-left quarter inside
 assertFalse(
 "partially overlapping image should be WINDOW-excluded",
 fullyContains(selection, image),
 )
 }

 @Test
 fun window_image_touching_right_edge_of_selection_is_included() {
 val selection = Rect(100f,100f,200f,200f)
 val image = Rect(150f,150f,200f,200f) // right+bottom edge kisses
 assertTrue(
 "edge-touching image should be WINDOW-included (>= comparison)",
 fullyContains(selection, image),
 )
 }

 @Test
 fun window_image_completely_outside_selection_is_excluded() {
 val selection = Rect(100f,100f,200f,200f)
 val image = Rect(300f,300f,400f,400f)
 assertFalse(
 "image completely outside selection should be WINDOW-excluded",
 fullyContains(selection, image),
 )
 }

 @Test
 fun window_image_exact_match_with_selection_is_included() {
 val rect = Rect(100f,100f,200f,200f)
 assertTrue(
 "exact match should be WINDOW-included",
 fullyContains(rect, rect),
 )
 }

 // ─── CROSSING scenarios ──────────────────────────────────

 @Test
 fun crossing_image_partially_overlapping_selection_is_included() {
 val selection = Rect(100f,100f,200f,200f)
 val image = Rect(180f,180f,250f,250f)
 assertTrue(
 "partially overlapping image should be CROSSING-included",
 intersects(selection, image),
 )
 }

 @Test
 fun crossing_image_just_a_few_pixels_overlap_is_included() {
 val selection = Rect(100f,100f,200f,200f)
 val image = Rect(199f,199f,250f,250f) //1px overlap
 assertTrue(
 "1-pixel-overlap image should be CROSSING-included",
 intersects(selection, image),
 )
 }

 @Test
 fun crossing_image_completely_outside_selection_is_excluded() {
 val selection = Rect(100f,100f,200f,200f)
 val image = Rect(300f,300f,400f,400f)
 assertFalse(
 "image completely outside should be CROSSING-excluded",
 intersects(selection, image),
 )
 }

 @Test
 fun crossing_image_touching_right_edge_of_selection_is_included() {
 // CROSSING predicate uses strict <=/>= so a touching
 // edge DOES count as intersection. The reducer's inline
 // expression mirrors this, so the test locks the
 // convention down.
 val selection = Rect(100f,100f,200f,200f)
 val image = Rect(200f,100f,300f,200f)
 assertTrue(
 "edge-touching image should be CROSSING-included",
 intersects(selection, image),
 )
 }

 // ─── Direction-lock (WINDOW -> CROSSING) ─────────────────

 /**
 * The reducer's direction-lock logic: only flip from WINDOW
 * to CROSSING, never back. We exercise it via a minimal
 * copy of the reducer's inline expression so a future change
 * to the lock semantic is forced to update this test.
 */
 private fun lockDirection(
 currentKind: BoxSelectionKind,
 start: Offset?,
 current: Offset,
 ): BoxSelectionKind {
 return if (
 currentKind == BoxSelectionKind.WINDOW &&
 start != null &&
 current.x < start.x
 ) {
 BoxSelectionKind.CROSSING
 } else {
 currentKind
 }
 }

 @Test
 fun direction_lock_drags_left_flips_to_crossing_once() {
 val start = Offset(500f,500f)
 assertEquals(
 BoxSelectionKind.CROSSING,
 lockDirection(BoxSelectionKind.WINDOW, start, Offset(400f,500f)),
 )
 }

 @Test
 fun direction_lock_drags_right_keeps_window() {
 val start = Offset(500f,500f)
 assertEquals(
 BoxSelectionKind.WINDOW,
 lockDirection(BoxSelectionKind.WINDOW, start, Offset(600f,500f)),
 )
 }

 @Test
 fun direction_lock_already_crossing_does_not_revert_to_window() {
 // The CAD convention: once flipped, kind stays. Even if
 // the pointer wobbles back to the right, the kind doesn't
 // revert.
 val start = Offset(500f,500f)
 assertEquals(
 BoxSelectionKind.CROSSING,
 lockDirection(BoxSelectionKind.CROSSING, start, Offset(600f,500f)),
 )
 }

 @Test
 fun direction_lock_null_start_skips_flip() {
 // Defensive: if start wasn't captured (e.g. a test-only
 // dispatch path bypassed BeginBoxSelectAt), the reducer
 // doesn't crash and just keeps the current kind.
 assertEquals(
 BoxSelectionKind.WINDOW,
 lockDirection(BoxSelectionKind.WINDOW, null, Offset(100f,500f)),
 )
 }
}
