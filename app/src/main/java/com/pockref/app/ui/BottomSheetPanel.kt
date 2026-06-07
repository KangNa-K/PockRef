package com.pockref.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pockref.app.ui.theme.Dimens

/**
 * M3 "large component bottom sheet" chrome container — NON-modal.
 *
 * Why a custom widget instead of
 * [androidx.compose.material3.ModalBottomSheet]:
 *
 * M3's [androidx.compose.material3.ModalBottomSheet] is implemented
 * as a [androidx.compose.ui.window.Dialog] — a separate Android
 * window layered ABOVE the Activity's main window via
 * WindowManager. Two side effects:
 *   1. The popup window receives ALL pointer events first. The
 *      built-in [androidx.compose.material3.ModalBottomSheet]'s
 *      scrim registers `detectTapGestures(onTap = { onDismiss() })`
 *      which consumes the down event, and `Color.Transparent`
 *      doesn't disable the pointer input — M3's Scrim composable
 *      treats `Color.Transparent` as "specified" (it's a defined
 *      [Color] constant, not [Color.Unspecified]) so the alpha
 *      animation still runs and the tap-to-dismiss still fires.
 *      The Activity's canvas underneath never sees those touches,
 *      and even drag / pinch in the scrim region is ambiguous
 *      because the Dialog window's coordinate system is separate
 *      from the Activity's.
 *   2. Window focus is captured by the Dialog window. The
 *      Activity's `onWindowFocusChanged(false)` fires, which
 *      breaks S Pen hover, accessibility services, and IME focus.
 *
 * For "tool panel over a live canvas" use cases — the
 * [SelectionBottomSheet] (constant companion to a non-zero
 * selection) and the [CropOverlay] toolbar (must coexist with
 * the draggable floating crop layer) — modal semantics are the
 * WRONG choice. The user must be able to keep dragging /
 * pinching / tapping the canvas while the panel is visible.
 *
 * This widget reproduces the M3 sheet chrome (28dp top corners,
 * 32x4dp drag handle, 560dp max width, surface solid, 0dp
 * tonalElevation, 12dp bottom padding — all driven by
 * [Dimens.SheetCornerRadius] / [Dimens.SheetMaxWidth] /
 * [Dimens.SheetHandleWidth] / [Dimens.SheetHandleHeight]) as a
 * plain [Box] child of the caller's parent layout. Touches that
 * land OUTSIDE the sheet's [Surface] (i.e. anywhere on screen
 * above the sheet or beside its 560dp centred rect) pass through
 * to the underlying canvas, because the sheet's parent
 * [androidx.compose.foundation.layout.BoxScope] `align`s it to
 * `BottomCenter` and only the Surface itself blocks hit tests.
 * Touches INSIDE the Surface are consumed by the sheet content
 * as usual.
 *
 * Dismissal:
 *   - Swipe-down on the drag handle (when [showDragHandle] = true)
 *     triggers [onDismiss]. The handle is the only drag-dismiss
 *     target — swiping on sheet CONTENT (e.g. a button row) does
 *     NOT dismiss, since those gestures are reserved for
 *     in-sheet interaction (long-press sliders, future
 *     drag-to-reorder actions, etc.).
 *   - System back press triggers [onDismiss] when
 *     [dismissOnBackPress] = true (the default).
 *   - The caller is responsible for any OTHER dismissal triggers
 *     (e.g. SelectionBottomSheet's "tap empty canvas deselects"
 *     path calls [onDismiss] explicitly because the sheet's
 *     visibility is derived from `selectedImageIds.isNotEmpty()`
 *     and an empty selection unmounts the sheet anyway).
 *
 * Visibility animation:
 *   - Uses [AnimatedVisibility] with a slide-in-from-bottom
 *     + fade-in (220ms enter), and slide-out-to-bottom + fade-out
 *     (180ms exit). On exit, the sheet stays in the composition
 *     for the duration of the animation, so the slide-out
 *     visually completes before the composable is removed.
 *   - The animation runs automatically as [visible] flips; the
 *     caller doesn't need to manage any internal animation state.
 *
 * Safe-area handling:
 *   - The sheet root [Box] applies
 *     [Modifier.windowInsetsPadding] for [WindowInsets.navigationBars]
 *     so the sheet's bottom edge sits flush against the nav bar
 *     on devices that have one (S Pen gestures, 3-button nav,
 *     gesture pill). Status-bar inset is NOT applied here because
 *     the sheet is anchored to BottomCenter and never approaches
 *     the top of the screen.
 *
 * @param visible controls sheet presence. `false` triggers the
 *   exit animation, after which the composable removes itself
 *   from composition.
 * @param onDismiss invoked when the user requests dismissal
 *   (swipe-down on handle or system back press). The caller is
 *   expected to set [visible] = `false` in response so the exit
 *   animation runs.
 * @param modifier additional [Modifier] applied to the outer
 *   [Box]. The sheet always anchors itself to [Alignment.BottomCenter]
 *   of the caller's parent; callers that need different anchoring
 *   (e.g. `Alignment.BottomStart` for a docked variant) should
 *   wrap in their own `Box` and pass `Modifier.align(...)` from
 *   the caller's [androidx.compose.foundation.layout.BoxScope].
 * @param sheetMaxWidth max width of the sheet's [Surface]. Defaults
 *   to [Dimens.SheetMaxWidth] (560dp) which matches the M3 sheet
 *   contract on tablets.
 * @param showDragHandle whether to render the 32x4dp drag handle
 *   at the top of the sheet. Defaults to `true`. Set `false` for
 *   sheets that are always-on (e.g. a docked side panel) where a
 *   dismiss affordance would be confusing.
 * @param swipeToDismiss whether swipe-down on the handle dismisses
 *   the sheet. Defaults to [showDragHandle] — i.e. swiping
 *   dismisses iff the handle is shown. Ignored when
 *   [showDragHandle] is `false`.
 * @param dismissOnBackPress whether system back press dismisses
 *   the sheet. Defaults to `true`.
 * @param content the sheet body. Receives a [ColumnScope] so the
 *   caller can stack title bar / action grid / footer rows
 *   naturally.
 */
@Composable
fun BottomSheetPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetMaxWidth: Dp = Dimens.SheetMaxWidth,
    showDragHandle: Boolean = true,
    swipeToDismiss: Boolean = showDragHandle,
    dismissOnBackPress: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    // V_NOW: use AnimatedVisibility for enter/exit. The
    // exit animation (slide-out + fade-out) runs with
    // the sheet still in composition, so the user sees
    // the sheet slide away rather than vanish instantly.
    // After the exit animation completes, the composable
    // is removed from the tree and pointer events in the
    // sheet's old region pass through to the canvas
    // underneath (because the Surface is gone).
    //
    // V_NOW: track handle drag state so we can suppress
    // swipe-to-dismiss during the exit animation. Without
    // this guard, a rapid "dismiss → re-show" sequence
    // (e.g. the user accidentally swipes while the sheet
    // is already sliding away) would re-fire onDismiss
    // and queue a second exit animation on an already-
    // dismissing sheet.
    var handleDragInProgress by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = visible,
        // V_NOW: enter = slide up from full sheet height + fade.
        // M3 standard is 250ms / EmphasizedDecelerate; we use a
        // 220ms tween to keep the slide in sync with the rest
        // of the selection chrome (TickSelectionSheet debounce
        // is 800ms, well above the animation duration).
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(durationMillis = 220),
        ) + fadeIn(animationSpec = tween(durationMillis = 220)),
        // V_NOW: exit = slide down past the bottom edge + fade.
        // Slightly faster (180ms) than the enter animation so
        // dismiss feels responsive — the user has already
        // committed to dismissing (via swipe-down or back
        // press) and is waiting for the sheet to clear.
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(durationMillis = 180),
        ) + fadeOut(animationSpec = tween(durationMillis = 180)),
        // V_NOW: label for tooling / debug.
        label = "BottomSheetPanel",
    ) {
        Box(
            modifier = modifier
                // V_NOW (bug fix): .fillMaxSize() instead of
                // .fillMaxWidth(). The previous fillMaxWidth-
                // only Box had a wrap-content height (=
                // sheet's own height), so its `contentAlignment
                // = BottomCenter` placed the sheet at the BOTTOM
                // OF THE BOX (= top of the screen, since the
                // Box itself only occupied the sheet's height
                // from the parent Box's TopStart). With
                // fillMaxSize the Box covers the WHOLE parent,
                // and BottomCenter correctly pins the sheet to
                // the bottom edge of the screen (just above the
                // navigation-bar inset).
                .fillMaxSize()
                // V_NOW: pointer events that land to the LEFT
                // and RIGHT of the centred 560dp Surface (and
                // ABOVE the sheet — i.e. the entire screen
                // minus the Surface's hit region) pass through
                // to the canvas. The Surface itself is the
                // only hit-test blocker, and it's only 560dp
                // wide × sheet-height tall.
                .windowInsetsPadding(WindowInsets.navigationBars),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = sheetMaxWidth)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(
                    topStart = Dimens.SheetCornerRadius,
                    topEnd = Dimens.SheetCornerRadius,
                ),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                // V_NOW (S-08 / Nielsen heuristic #8
                // "Aesthetic and minimalist design"): a
                // 1dp hairline border using
                // [outlineVariant] at 40% alpha. Why a
                // border instead of a shadow or elevation:
                //   - The user can set the canvas
                //     background to ANY of the 7 presets
                //     + HSV custom (see
                //     [PockRefUiState.backgroundColor]
                //     and the SettingsPanel HSV wheel).
                //     Light presets (#FFFFFF, #EEEEEE,
                //     #E8DCC4, #B0E0D0, #B0D8E8) are
                //     nearly the same value as the sheet's
                //     [colorScheme.surface] — the sheet
                //     effectively disappears, the user
                //     can't tell where the action buttons
                //     end and the canvas begins, and
                //     gestures "miss" the buttons.
                //   - tonalElevation was already 0dp per
                //     M3 sheet spec — the user explicitly
                //     wanted the sheet to feel "flat over
                //     the canvas", not floating.
                //   - A drop shadow under the sheet would
                //     conflict with the non-modal promise
                //     (the sheet should read as part of
                //     the canvas UI, not a floating
                //     surface).
                // 1dp at outlineVariant @ 40% is M3's
                // canonical "subtle divider over the same
                // value background" pattern — visible
                // against light backgrounds, near-
                // invisible against dark backgrounds
                // (where the user doesn't need the help),
                // and adds zero layout cost.
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                        .copy(alpha = 0.4f),
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        // V_NOW: 12dp bottom padding so the
                        // sheet's content has a margin from
                        // the bottom rounded edge (M3 sheet
                        // standard, mirrors the v1.1.0
                        // SelectionBottomSheet / CropOverlay
                        // toolbar layout).
                        .padding(bottom = 12.dp),
                ) {
                    if (showDragHandle) {
                        SheetDragHandle(
                            swipeToDismiss = swipeToDismiss,
                            onDragDismiss = {
                                if (!handleDragInProgress) {
                                    handleDragInProgress = true
                                    onDismiss()
                                }
                            },
                        )
                    }
                    content()
                }
            }
        }
    }

    // V_NOW: system back press dismiss. Placed AFTER the
    // AnimatedVisibility block (not inside) so the BackHandler
    // is registered even when the sheet is mid-exit-animation
    // — the user pressing back during exit shouldn't get a
    // "back does nothing" surprise. The [enabled] check on
    // the handler covers the dismiss state.
    BackHandler(
        enabled = (dismissOnBackPress && visible),
        onBack = onDismiss,
    )
}

/**
 * The 32×4dp pill handle at the top of a [BottomSheetPanel],
 * with optional swipe-down dismissal.
 *
 * The handle is its own composable (rather than inlined into
 * [BottomSheetPanel]) because:
 *   - It needs [LocalDensity] for the swipe threshold
 *     conversion (dp → px) without polluting the parent's
 *     density scope.
 *   - Future callers might want a custom handle
 *     (e.g. with a title row baked in, like the iOS sheet
 *     style with "Cancel" / "Done" in the handle row) —
 *     isolating the gesture detection here keeps that
 *     extension path clean.
 */
@Composable
private fun SheetDragHandle(
    swipeToDismiss: Boolean,
    onDragDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    // V_NOW: 6dp vertical drag threshold — anything smaller
    // is treated as a stray jitter / accidental touch on the
    // handle area, NOT a dismiss intent. M3's ModalBottomSheet
    // uses a similar threshold (the sheet's hiddenValue is
    // committed after a ~12% drag of the sheet height, which
    // lands in roughly the same ballpark on a 280dp sheet).
    val swipeThresholdPx = with(density) { 6.dp.toPx() }
    Box(
        modifier = Modifier
            .padding(vertical = 10.dp)
            .fillMaxWidth()
            // V_NOW: swipe-down gesture on the handle only.
            // detectVerticalDragGestures fires AFTER the first
            // 6dp of downward drag (above the threshold),
            // preventing accidental dismiss on a stray tap
            // that happens to land on the handle row. The
            // gesture is registered ONLY on this Box, so
            // swiping on the sheet CONTENT (button grid /
            // title bar) does NOT trigger dismiss — those
            // gestures are reserved for in-sheet interaction.
            .then(
                if (swipeToDismiss) {
                    Modifier.pointerInput(Unit) {
                        detectVerticalDragGestures { _, dragAmount ->
                            if (dragAmount > swipeThresholdPx) {
                                onDragDismiss()
                            }
                        }
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(
                    width = Dimens.SheetHandleWidth,
                    height = Dimens.SheetHandleHeight,
                )
                .clip(RoundedCornerShape(999.dp))
                .background(Color(0xFFC1C7CE)),
        )
    }
}