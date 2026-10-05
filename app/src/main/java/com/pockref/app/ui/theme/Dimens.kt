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
    val SettingsWindowMaxWidth = 760.dp
    val SettingsContentMaxWidth = 560.dp
    val SettingsNavigationWidth = 120.dp
    val SettingsContentInset = 24.dp
    const val SettingsWindowHeightFraction = 0.84f

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
     * Surface. V_NOW (2026-06-10 user spec "顶栏和全局锁定按钮
     * 高度不统一，修复一下，让顶栏的高度和全局锁定按钮高度一致"):
     * bumped from 44dp to 48dp to match
     * [BottomPanelChromeSize] (which is what the
     * [com.pockref.app.ui.PureRefApp.GlobalLockButton] uses).
     * The 4dp gap was a "visual density" optimization that
     * pre-dated the global-lock button being extracted to its
     * own chrome element; now that both elements are corner-
     * anchored top-end (lock at TopEnd, toolbar at TopStart)
     * and read as a single "top chrome row" at a glance, the
     * heights should match. 48dp = M3 minimum touch target
     * ([MinTouchTarget]).
     */
    val TopToolbarHeight = 48.dp

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

    /**
     * Sheet chrome tokens — shared by [BottomSheetPanel] and any
     * caller that wants to MATCH the M3 "large component bottom
     * sheet" visual contract without going through M3's
     * [androidx.compose.material3.ModalBottomSheet] (which is a
     * modal Dialog window and intercepts ALL outside touches via
     * its built-in scrim — fatal for use cases where the canvas
     * underneath must remain interactive while the sheet is up,
     * e.g. [SelectionBottomSheet] for "tool panel over a live
     * canvas" and the [CropOverlay] toolbar that sits beneath the
     * draggable floating crop layer).
     *
     * Values mirror the M3 spec for large component bottom sheets:
     *   - 28dp top corners — M3 sheet standard (vs the 12dp
     *     [ChromeCornerRadius] used for cards / chrome panels)
     *   - 560dp max width on tablets so the sheet doesn't
     *     stretch to full landscape screen width (~1750dp on
     *     SM-X810); centered horizontally inside the parent Box
     *   - 32×4dp pill handle at the top centre, 10dp vertical
     *     padding above the sheet content
     *   - 0dp tonalElevation — M3 sheet default (a flat solid
     *     surface, no tonal tint over the canvas image)
     */
    val SheetCornerRadius = 28.dp
    val SheetMaxWidth = 560.dp
    val SheetHandleWidth = 32.dp
    val SheetHandleHeight = 4.dp

    // ── Card / dialog / chip corner-radius tier ─────────────────────
    //
    // 5-tier corner-radius scale (P0 chrome unify, 2026-06-11):
    //
    //   PillCornerRadius (pill / 999dp)
    //     └─ fully-rounded stadium shape for status tags, the
    //        SelectionModeToggle chip row, drag handles, the
    //        "锁定" badge in the selection sheet header
    //   DialogCornerRadius (24dp)
    //     └─ fullscreen modal dialogs / completion overlays
    //        (Practice "练习完成" Surface, the 360dp optimization
    //        Card). 4dp less than [SheetCornerRadius] so an
    //        in-canvas dialog reads slightly "tighter" than a
    //        bottom sheet pinned to the screen edge.
    //   SheetCornerRadius (28dp) ← existing
    //     └─ bottom sheet top corners (SelectionBottomSheet,
    //        CropOverlay toolbar). M3 spec for large component
    //        bottom sheets.
    //   CardCornerRadius (16dp)
    //     └─ mid-size embedded cards / dialog cards (EmptyCanvas
    //        State hero card, the "优化中 / 已暂停" 360dp Card).
    //        Sits between the 12dp chrome and the 24dp dialog
    //        tiers so the visual hierarchy reads as
    //        chrome < card < sheet ≈ dialog.
    //   ChromeCornerRadius (12dp) ← existing
    //     └─ every floating chrome surface (top toolbar, minimap
    //        panel, FAB, global lock button, the long-press
    //        minimap slider). M3 medium shape.
    //   ChipCornerRadius (8dp)
    //     └─ inline chips / list-item tiles / HSV swatches /
    //        Settings title-edit field. The "smallest" surface
    //        tier; pairs naturally with M3 small shape.
    //
    // Callers must pick the tier that matches the SURFACE'S
    // VISUAL ROLE, not its size. A 30dp HSV swatch and a 64dp
    // Recent-file-grid tile both live in the [ChipCornerRadius]
    // tier because they read as "list item / picker", not
    // "chrome / dialog".
    const val PillCornerRadius = 999
    val DialogCornerRadius = 24.dp
    val CardCornerRadius = 16.dp
    val ChipCornerRadius = 8.dp
}
