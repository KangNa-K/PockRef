package com.pockref.app.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Design tokens for chrome / floating UI elements.
 *
 * Single source of truth for insets, corner radius, surface
 * alpha, and tonal elevation. Tweaks here propagate to every
 * floating surface (top toolbar, bottom-left minimap, bottom-
 * right auto-layout FAB) so the layout grid stays consistent
 * without grep'ing magic numbers across three files.
 *
 * Conventions used by every chrome element:
 *   - EdgeInset: 16dp from the nearest window edge (top,
 *     bottom, leading, trailing all the same)
 *   - ChromeCornerRadius: 12dp — Material 3 medium shape,
 *     default, matches the top toolbar's pre-existing value
 *   - ChromeSurfaceAlpha: 0.92f — frosted-glass tint on top
 *     of the canvas
 *   - ChromeBorderWidth: 1dp — sub-1dp tonalElevation panels
 *     (minimap) get a hairline border so they still read as
 *     a discrete surface on light backgrounds
 *   - Visual hierarchy: FAB > Toolbar > Minimap, in 6dp / 3dp /
 *     1dp tiers so the most-tappable element pops the most
 */
object Dimens {
    /** All floating elements get exactly this from the nearest edge. */
    val EdgeInset = 16.dp

    /**
     * Larger inset (24dp, 3× the 8dp grid) reserved for the three
     * primary corner chrome elements: the top-left top toolbar, the
     * bottom-left minimap, and the bottom-right action panel. These
     * three share this value so their corner-anchored edges line up
     * (top toolbar left = minimap left; minimap bottom = action
     * panel bottom). The other bottom element — the bottom-center
     * selection-status pill — stays on [EdgeInset] because it isn't
     * a corner element and isn't in the "three buttons" group.
     */
    val CornerInset = 24.dp

    /**
     * The vertical (and square) size of the top toolbar's chrome
     * Surface. Distinct from [BottomPanelChromeSize] (which is 48dp)
     * because the top toolbar is a "view + interact" row with
     * multiple items, while the bottom-right panel is a stack of
     * single-purpose cells. Per the user's bottom-right FAB spec:
     * "主按钮尺寸：右下角折叠状态的主按钮，其宽度和高度均等于
     * 顶部导航栏（顶栏）的实际渲染高度" — the top toolbar keeps
     * its 44dp height for visual density; the bottom-right panel
     * is bumped to 48dp to satisfy the M3 minimum touch target.
     */
    val TopToolbarHeight = 44.dp

    /**
     * Square size of every bottom-right panel cell — the auto-
     * layout FAB plus the 4 expandable ListItems. Pinned to M3's
     * minimum touch target (48dp, [MinTouchTarget]) so every tap
     * target is finger-friendly, while still reading as "5 identical
     * squares stacked vertically" (just slightly larger than the
     * pre-P0-fix 44dp).
     *
     * Also used by the collapsed minimap's bar height + default
     * position offset so the bar's bottom edge stays flush with
     * the bottom-right panel's top edge regardless of which one
     * gets resized in the future.
     */
    val BottomPanelChromeSize = 48.dp

    /**
     * M3 minimum interactive component size. Mirrors
     * [androidx.compose.material3.minimumInteractiveComponentSize]'s
     * default of 48dp (WCAG 2.5.5 Level AAA target size). Applied
     * via that Modifier on IconButton sites where the visual icon
     * is smaller than 48dp (e.g. 28dp / 36dp IconButton in the
     * recent-files list and top toolbar) so the hit area stays at
     * 48dp without inflating the visible icon size.
     */
    val MinTouchTarget = 48.dp

    /** Corner radius for every chrome surface (toolbar, minimap, FAB). M3 medium shape. */
    val ChromeCornerRadius = 12.dp

    /** Surface alpha for "frosted glass" floating chrome. */
    const val ChromeSurfaceAlpha = 0.92f

    /** Hairline border for low-elevation panels. */
    val ChromeBorderWidth = 1.dp

    // Visual hierarchy via Material 3 tonalElevation. FAB is the
    // most tappable thing on screen, so it gets the most elevation.
    val FabElevation = 6.dp
    val ToolbarElevation = 3.dp
    val MinimapElevation = 1.dp
}
