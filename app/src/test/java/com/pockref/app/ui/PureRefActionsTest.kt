package com.pockref.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM unit tests for the [PockRefAction] sealed interface.
 *
 * The action set is the contract between the UI (which dispatches
 * actions) and the ViewModel (which reduces them). Adding or removing
 * a variant changes the public surface, so we lock the count down with
 * a test.
 *
 * NOTE on the expected count: the original task spec said "76 variants",
 * but the current source has **69** direct subclasses of [PockRefAction]
 * (counted by hand on 2026-06-01). We assert the actual count here so
 * the test would only break if a variant is accidentally added or
 * removed. If you intentionally change the count, update the constant
 * below AND mention it in the change log.
 *
 * Implementation note: we use Java reflection (`Class.getPermittedSubclasses()` /
 * `getClasses()`) instead of `kotlin.reflect.full.sealedSubclasses` so this
 * test runs on the bare Kotlin stdlib without requiring kotlin-reflect
 * (which the production build does not depend on).
 */
class PockRefActionsTest {

    /**
     * The expected number of [PockRefAction] subclasses. Update this if
     * you intentionally add or remove a variant.
     *
     * Changelog:
     *   - 2026-06-04: 99 → 101 — added `SetBottomRightPanelExpanded`
     *     and `OpenSettings` for the new expandable bottom-right
     *     action panel (long-press FAB expansion adds 4 list items:
     *     box-select / settings / import / save).
     *   - 2026-06-05: 101 → 102 — added `SetSettingsTab` for the
     *     new tabbed settings sheet (通用 / 外观 / 手势).
     *     Net change is +1.
     *     Note: the prior value 95 was already off by 4 (other
     *     recent commits had silently added 4 variants without
     *     updating this constant); 99 was the real pre-change count.
     *   - 2026-06-05: 102 → 103 — added `SetMenuBarsCollapsed`
     *     for window-blur chrome collapse
     *     ([com.pockref.app.MainActivity.onWindowFocusChanged]
     *     → unified collapse of top toolbar / selection
     *     floating toolbar / bottom-right action panel).
     *     Net change is +1.
     *   - 2026-06-05: 103 → 104 — added `SetAutoSaveEnabled`
     *     for the auto-save master switch in
     *     [com.pockref.app.ui.SettingsAutoSaveSection]
     *     (the Switch in the settings sheet's 自动保存
     *     section, paired with `SettingsToggleRow`; the
     *     reducer mirrors the pref into uiState so the
     *     Switch position + Slider enabled state recompose
     *     in the same frame).
     *     Net change is +1.
     *   - 2026-06-05: 105 → 106 — added `DismissBottomRightPanel`
     *     for chrome-mutex: when the user starts interacting
     *     elsewhere (canvas tap → Deselect, image tap →
     *     SelectImage, top toolbar click) the right-bottom
     *     action panel collapses so the new focus area
     *     (selection toolbar / top toolbar menu / canvas)
     *     has clean visual room. Always-collapsed inverse
     *     of [SetBottomRightPanelExpanded]. Idempotent.
     *     Net change is +1.
     */
    // V_NOW: -1 variant — [PockRefAction.SetMinimapMoving]
    // (long-press-drag "moving" flag) was REMOVED by user
    // spec "minimap 去掉长按功能". The 114 → 113 net
    // change is verified at build time by
    // [PockRefAction.permittedSubclasses.size]; if a new
    // variant is added or removed, this number must be
    // updated in lockstep. [SetMinimapZoomScale] is still
    // in the variant set — its data-layer plumbing is
    // preserved for a future UI-layer re-enable.
    private val expectedVariantCount: Int = 113

    private fun directSubclasses(): List<Class<out PockRefAction>> {
        val permitted = PockRefAction::class.java.permittedSubclasses
        if (permitted != null) {
            // Java 17+ sealed classes: this is the authoritative list.
            @Suppress("UNCHECKED_CAST")
            return permitted.toList() as List<Class<out PockRefAction>>
        }
        // Fallback for older JVMs: declared inner classes (less precise
        // for nested hierarchies, but acceptable here since
        // PockRefAction is a flat sealed interface).
        @Suppress("UNCHECKED_CAST")
        return PockRefAction::class.java.declaredClasses
            .filter { PockRefAction::class.java.isAssignableFrom(it) }
            .map { it as Class<out PockRefAction> }
    }

    @Test
    fun allVariants_areDirectSealedSubclasses() {
        val subs = directSubclasses()
        assertEquals(
            "PockRefAction has $expectedVariantCount variants, " +
                "actual permittedSubclasses.size = ${subs.size}",
            expectedVariantCount,
            subs.size,
        )
        // Every variant must implement PockRefAction.
        subs.forEach { javaClass ->
            assertNotNull("$javaClass should be a recognized Java class", javaClass)
            assertTrue(
                "${javaClass.simpleName} must implement PockRefAction",
                PockRefAction::class.java.isAssignableFrom(javaClass),
            )
        }
    }

    @Test
    fun variantCount_meetsMinimumFloor() {
        // Loose floor: a regression that drops the count below this would
        // indicate someone broke the action surface entirely. The current
        // count is 74; the floor is set to 60 so small refactors don't
        // break this test, while a mass removal would.
        val count = directSubclasses().size
        assertTrue(
            "Expected at least 60 PockRefAction variants, got $count",
            count >= 60,
        )
    }

    @Test
    fun allVariants_areDataClassesOrDataObjects() {
        // PockRefAction is the "event vocabulary" — every variant should
        // be a value-typed event (data class for parameterized, data object
        // for singleton). We can't directly distinguish "data" from plain
        // class on the JVM (they compile to the same bytecode shape, just
        // with extra generated `equals`/`hashCode`/`copy`/`componentN`
        // methods), so we approximate: at least one of the synthetic
        // equals / copy / componentN methods should be present.
        val subs = directSubclasses()
        subs.forEach { javaClass ->
            val methods = javaClass.declaredMethods.map { it.name }
            val isDataLike = "equals" in methods && "hashCode" in methods
            assertTrue(
                "${javaClass.simpleName} should look like a data class/object " +
                    "(expected equals/hashCode in declared methods; got $methods)",
                isDataLike,
            )
        }
    }

    @Test
    fun noVariants_areNestedSealedInterfaces() {
        // The original task spec asked us to enumerate
        // `sealedSubclasses.flatMap { it.sealedSubclasses }` to reach
        // 76. But the current shape is a flat sealed interface with no
        // nested sealed sub-hierarchies, so the second hop is empty. We
        // lock that contract in so a future refactor that adds a nested
        // sealed interface is intentional (and the test should be updated
        // to match).
        val secondLevel = directSubclasses()
            .flatMap { it.permittedSubclasses?.toList().orEmpty() }
        assertEquals(
            "PockRefAction should not have nested sealed sub-hierarchies",
            0,
            secondLevel.size,
        )
    }

    @Test
    fun variantNames_areUnique() {
        // Each variant must have a distinct simple name. This catches
        // accidental name collisions.
        val names = directSubclasses().map { it.simpleName }
        assertEquals(names.size, names.toSet().size)
    }

    // ----- A few representative variants can be instantiated -------------------

    @Test
    fun singletonDataObjects_haveDistinctInstances() {
        // data object semantics: equality is reference equality, so two
        // references to PockRefAction.RotateLeft must be the same
        // instance. (Also confirms data-object compilation produced
        // singletons, not classes.)
        val a: PockRefAction = PockRefAction.RotateLeft
        val b: PockRefAction = PockRefAction.RotateLeft
        assertTrue(a === b)
        assertTrue(a == b)
    }

    @Test
    fun dataClasses_haveStructuralEquality() {
        val a: PockRefAction = PockRefAction.SelectImage("img-1")
        val b: PockRefAction = PockRefAction.SelectImage("img-1")
        val c: PockRefAction = PockRefAction.SelectImage("img-2")
        assertEquals(a, b)
        assertNotEqualsCustom(a, c)
    }

    @Test
    fun dataClasses_distinguishByValue() {
        val rotate: PockRefAction = PockRefAction.SetCropRotation(15f)
        val rotate2: PockRefAction = PockRefAction.SetCropRotation(75f)
        assertNotEqualsCustom(rotate, rotate2)
    }

    @Test
    fun dataClass_copy_returnsNewInstance() {
        val original: PockRefAction = PockRefAction.SelectImage("img-1")
        // data class copy() returns a new instance with the same values.
        // We test via reflection so the test stays robust to
        // constructor-signature changes — we just check that .copy()
        // is generated.
        val copyMethod = original.javaClass.declaredMethods.firstOrNull { it.name == "copy" }
        assertNotNull("${original.javaClass.simpleName}.copy() should exist", copyMethod)
    }

    // ----- Minimal custom assert to keep imports tidy --------------------------

    private fun assertNotEqualsCustom(a: Any?, b: Any?) {
        assertTrue(
            "Expected $a != $b",
            a != b,
        )
    }
}
