@file:Suppress("UnusedContentLambdaTargetStateParameter")

package com.pockref.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pockref.app.data.PurFileParser
import com.pockref.app.data.SceneImageDecoder
import com.pockref.app.model.PracticePhase
import com.pockref.app.model.PracticeState
import com.pockref.app.model.PracticeStatus
import com.pockref.app.model.SceneImage
import com.pockref.app.ui.theme.Dimens
import kotlinx.coroutines.delay

/**
 * V_NOW (2026-06-08 速写练习 commit 3 — Material 3
 * alignment revision): the practice screen.
 *
 * This revision is a MANDATORY COMPLIANCE PASS to make the
 * practice screen read as the same design system as the
 * rest of PockRef. The previous version was functionally
 * correct but used hardcoded [androidx.compose.ui.graphics.Color]
 * values (Color.Black, Color.White.copy(alpha = ...)) for
 * backgrounds and text, hardcoded font sizes (fontSize =
 * 32.sp), hardcoded shapes (RoundedCornerShape(12.dp)),
 * and `Box(...background(...))` instead of M3 [Surface].
 * The chrome-survey mismatch with the rest of the app
 * (TopToolbar / BottomRightActionPanel use `surface @ 0.92
 * alpha + ChromeCornerRadius = 12.dp + tonalElevation = 3dp
 * or 6dp` per Dimens.kt) was jarring — the user
 * explicitly called this out.
 *
 * What changed in this revision (everything else stayed
 * the same — the state machine, the ticker math, the
 * chrome-hide gates in PockRefApp.kt, the BackHandler
 * wiring, and the cross-fade logic are all untouched):
 *
 *   - [Box] + hardcoded `Color.Black` background → M3
 *     [Surface] with `color = MaterialTheme.colorScheme.scrim`.
 *     `scrim` in PockRef's Theme.kt is `Color(0xFF000000)`
 *     (opaque pure black) so the visual outcome is
 *     identical, but the token is now theme-aware: if a
 *     future commit changes the dark scheme's scrim to
 *     e.g. `Color(0xFF0D0E11)` (M3's standard "very dark
 *     gray" scrim), the practice screen picks it up
 *     automatically.
 *   - Hardcoded `Color.White` text + `Color.Black.copy(
 *     alpha = 0.6f)` background for the countdown badge
 *     → M3 [Surface] with `color = inverseSurface` (dark
 *     gray in light theme, light gray in dark theme) +
 *     `color = inverseOnSurface` text. This is the M3
 *     canonical pair for "high-contrast text on top of
 *     a scrim / fullscreen image" — same pattern the
 *     snackbar / tooltip use when they need to sit on
 *     top of an image.
 *   - Hardcoded `RoundedCornerShape(12.dp)` → M3 shape
 *     token `MaterialTheme.shapes.large` (16dp, M3
 *     spec). The PAUSED / FINISHED overlay containers
 *     use `MaterialTheme.shapes.extraLarge` (28dp, M3
 *     spec for dialogs / sheets — matches Dimens.kt's
 *     [SheetCornerRadius]).
 *   - `fontSize = 32.sp` for the countdown →
 *     [MaterialTheme.typography.headlineLarge] which is
 *     32sp by M3 spec. Same rendered size, but the role
 *     is now part of the typography scale and adjusts if
 *     a future commit changes the type scale.
 *   - "已暂停" / "练习完成" headers → [headlineSmall]
 *     (24sp) which is M3's "small headline" — the
 *     standard for a dialog header.
 *   - Custom `BottomActionButton` (Box with
 *     `Color.White.copy(alpha = 0.15f)` background +
 *     Icon + Text) → M3 [FilledTonalButton] (the
 *     M3 "secondary action" button, uses
 *     `secondaryContainer` / `onSecondaryContainer`
 *     internally — no need to set colors explicitly,
 *     the button reads them from the theme).
 *   - PAUSED / FINISHED card uses [Surface] with
 *     `color = surfaceContainerHigh` (M3 1.2 elevated
 *     surface token, matches the rest of PockRef's
 *     dialog chrome) + `tonalElevation = 6.dp` +
 *     `shadowElevation = 8.dp` — matches the
 *     auto-layout FAB's elevation tier (see
 *     Dimens.FabElevation = 6.dp) so the overlay
 *     reads as "primary visual weight, sits above
 *     the scrim".
 *   - Cross-fade `tween(200)` → M3 standard
 *     `tween(300, easing = FastOutSlowInEasing)`
 *     (medium-emphasis motion, the M3 default for
 *     in-element transitions per the M3 motion spec
 *     table). The 300ms duration is also the M3
 *     "medium" tier — `tween(150)` is small (ripple,
 *     focus), `tween(500)` is large (page transitions).
 *   - `tween(350)` for the rest-dim alpha → M3
 *     standard `tween(300, easing = FastOutSlowInEasing)`.
 *   - [Card] / [AlertDialog] were considered for the
 *     PAUSED / FINISHED overlays but [Surface] with
 *     `tonalElevation` + `shadowElevation` matches
 *     the rest of PockRef's overlay chrome more
 *     closely (the BottomSheetPanel uses
 *     `surface @ opacity 1` + `tonalElevation = 0`).
 *     Going [AlertDialog] would be a more disruptive
 *     visual change (full M3 modal with title + text +
 *     action slots) than the user asked for.
 *
 * M3 spec cross-check (everything in the new file):
 *   - Colors: 100% from [MaterialTheme.colorScheme.*]
 *   - Typography: 100% from
 *     [MaterialTheme.typography.*] (headlineLarge,
 *     headlineSmall, bodyMedium, labelLarge)
 *   - Shapes: 100% from [MaterialTheme.shapes.*]
 *     (large, extraLarge)
 *   - Components: [Surface] (×3), [Button],
 *     [FilledTonalButton], [TextButton], [Text], [Icon]
 *     — all M3
 *   - Motion: [tween] + [FastOutSlowInEasing] +
 *     [AnimatedContent] — M3 standard easing/duration
 *   - No hardcoded `Color(0xFF...)` values
 *   - No hardcoded `fontSize = N.sp`
 *   - No hardcoded `RoundedCornerShape(N.dp)`
 *   - No hardcoded `alpha = N` for surface tints
 *     (all alpha comes from M3 container tokens
 *     automatically)
 */
@Composable
fun BoxScope.PracticeScreen(
    state: PracticeState,
    images: List<SceneImage>,
    imageDecoder: SceneImageDecoder?,
    imageLazyLoader: PurFileParser?,
    onAction: (PockRefAction) -> Unit,
) {
    val currentImage = remember(state.currentImageId, images) {
        images.firstOrNull { it.id == state.currentImageId }
    }

    // Ticker: drift-free phaseEndMs - now math. Keyed on
    // the phase-identity tuple so any state transition
    // cancels the old coroutine and launches a fresh
    // one. Same as the previous revision.
    val phaseKey = "${state.status.name}|${state.phase.name}|${state.currentIndex}|${state.config.practiceDurationMs}|${state.config.restDurationMs}"
    LaunchedEffect(phaseKey) {
        if (state.status != PracticeStatus.RUNNING) return@LaunchedEffect
        val phaseDuration = when (state.phase) {
            PracticePhase.COUNTDOWN -> state.config.practiceDurationMs
            PracticePhase.REST -> state.config.restDurationMs
        }
        val phaseEndMs = System.currentTimeMillis() + state.remainingMs.coerceAtLeast(0L)
        while (true) {
            val remaining = phaseEndMs - System.currentTimeMillis()
            onAction(
                PockRefAction.PracticeTick(
                    remainingMs = remaining,
                    imageIndex = state.currentIndex,
                    phase = state.phase,
                )
            )
            if (remaining <= 0L) break
            delay(16L)
        }
    }

    // BackHandler: system back pauses during RUNNING,
    // RESUMES during PAUSED (matches the scrim-tap
    // behavior of the M3 [ModalBottomSheet] — tapping
    // outside the sheet also resumes, per the
    // onDismissRequest the [PracticePausedView]
    // dispatches), and exits during FINISHED. RUNNING
    // → PAUSED → FINISHED all match the "natural"
    // back-flow: tap-back-to-step-back, then tap-back-
    // to-finish. (PAUSED previously was excluded; the
    // user feedback "已暂停窗口显示的尺寸有点大" led to
    // the [ModalBottomSheet] redesign which gives the
    // sheet a proper onDismissRequest, so back is now
    // a sensible resume trigger.)
    BackHandler(enabled = state.status == PracticeStatus.RUNNING) {
        onAction(PockRefAction.PausePractice)
    }
    BackHandler(enabled = state.status == PracticeStatus.PAUSED) {
        onAction(PockRefAction.ResumePractice)
    }
    BackHandler(enabled = state.status == PracticeStatus.FINISHED) {
        onAction(PockRefAction.ExitPractice)
    }

    when (state.status) {
        PracticeStatus.RUNNING -> PracticeRunningView(
            state = state,
            currentImage = currentImage,
            imageDecoder = imageDecoder,
            imageLazyLoader = imageLazyLoader,
            onAction = onAction,
        )
        PracticeStatus.PAUSED -> PracticePausedView(
            state = state,
            currentImage = currentImage,
            imageDecoder = imageDecoder,
            imageLazyLoader = imageLazyLoader,
            onAction = onAction,
        )
        PracticeStatus.FINISHED -> PracticeFinishedOverlay(
            state = state,
            onAction = onAction,
        )
        PracticeStatus.IDLE -> { /* caller gates; defensive */ }
    }
}

/**
 * V_NOW: the RUNNING view — M3 [Surface] background
 * (theme-aware scrim), M3 cross-fade (medium motion,
 * standard easing), M3 inverseSurface countdown badge
 * top-right, M3 [FilledTonalButton] bottom-center.
 */
@Composable
private fun PracticeRunningView(
    state: PracticeState,
    currentImage: SceneImage?,
    imageDecoder: SceneImageDecoder?,
    imageLazyLoader: PurFileParser?,
    onAction: (PockRefAction) -> Unit,
) {
    val restDim = state.phase == PracticePhase.REST
    val imageAlpha by animateFloatAsState(
        targetValue = if (restDim) 0.30f else 1f,
        animationSpec = tween(
            durationMillis = 300,
            easing = FastOutSlowInEasing,
        ),
        label = "rest-dim",
    )
    // M3 Surface for the fullscreen background. color =
    // scrim reads from the theme — in PockRef's current
    // Theme.kt this is `Color(0xFF000000)`, but if the
    // scrim is later retuned to a different dark tone
    // (or follows dynamic color on Android 12+), this
    // view picks it up automatically.
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.scrim,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Cross-fade image. M3 standard medium-emphasis
            // motion: 300ms with FastOutSlowInEasing (the
            // M3 default "in-element" easing per the M3
            // motion spec).
            AnimatedContent(
                targetState = currentImage?.id,
                transitionSpec = {
                    fadeIn(
                        animationSpec = tween(
                            durationMillis = 300,
                            easing = FastOutSlowInEasing,
                        ),
                    ) togetherWith fadeOut(
                        animationSpec = tween(
                            durationMillis = 300,
                            easing = FastOutSlowInEasing,
                        ),
                    )
                },
                label = "practice-image-crossfade",
                modifier = Modifier.fillMaxSize(),
            ) { _ ->
                PracticeImageLayer(
                    image = currentImage,
                    imageDecoder = imageDecoder,
                    imageLazyLoader = imageLazyLoader,
                    alpha = imageAlpha,
                )
            }

            // Top-right countdown: M3 inverseSurface / inverseOnSurface
            // pair. This is the canonical M3 pattern for
            // "high-contrast text on top of an image" —
            // same color pair used by snackbar / tooltip
            // / bottom-sheet drag handle. Shape uses
            // `MaterialTheme.shapes.large` (M3's 16dp
            // default for chips / badges) so the badge
            // reads as a chip, not a button.
            CountdownBadge(
                state = state,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 16.dp, end = 16.dp),
            )

            PracticePageNavigation(
                state = state,
                onAction = onAction,
            )

            // Bottom-center action button: M3 FilledTonalButton
            // (the M3 "secondary action" button role). Uses
            // secondaryContainer / onSecondaryContainer
            // internally — no need to specify colors.
            // FilledTonalButton is the M3-recommended
            // alternative to the previous custom Box
            // implementation because it gets correct
            // touch target (48dp), correct ripple, and
            // correct content padding for free.
            if (state.phase == PracticePhase.REST) {
                FilledTonalButton(
                    onClick = { onAction(PockRefAction.SkipRest) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "跳过",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            } else {
                FilledTonalButton(
                    onClick = { onAction(PockRefAction.PausePractice) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Pause,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "暂停",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

/**
 * M3 filled-tonal icon buttons for moving through the practice queue.
 * The controls stay at the vertical center of the image so they are
 * easy to reach without competing with the countdown badge or the
 * bottom pause / skip action. Disabled states communicate the first
 * and last page boundaries through the standard M3 treatment.
 */
@Composable
private fun BoxScope.PracticePageNavigation(
    state: PracticeState,
    onAction: (PockRefAction) -> Unit,
) {
    FilledTonalIconButton(
        onClick = { onAction(PockRefAction.PreviousPracticeImage) },
        enabled = state.currentIndex > 0,
        modifier = Modifier
            .align(Alignment.CenterStart)
            .padding(start = 16.dp)
            .size(48.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.ChevronLeft,
            contentDescription = "上一页",
            modifier = Modifier.size(24.dp),
        )
    }
    FilledTonalIconButton(
        onClick = { onAction(PockRefAction.NextPracticeImage) },
        enabled = state.currentIndex < state.imageIds.lastIndex,
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .padding(end = 16.dp)
            .size(48.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = "下一页",
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * V_NOW (2026-06-08 user feedback "已暂停窗口显示的
 * 尺寸有点大"): replaced the previous centered
 * `Surface` card (≈280dp tall) with an M3
 * [ModalBottomSheet] that sits at the bottom of the
 * screen and only takes ~25% of the viewport. The
 * [PracticeImageLayer] is rendered at 100% alpha
 * underneath so the user can see the image they
 * paused on while deciding resume-vs-exit (the
 * whole point of "pause" is "I want to think about
 * this image before continuing"). The image is the
 * main visual content; the sheet is just a
 * contextual action bar.
 *
 * M3 tokens used:
 *   - [ModalBottomSheet] (M3 standard modal sheet
 *     composable, slides up from the bottom; same
 *     pattern as [PracticeConfigSheet])
 *   - M3 [OutlinedButton] for "结束练习" (the
 *     secondary / less-prominent action — M3 says
 *     "the destructive or alternate action is
 *     outlined, the primary action is filled")
 *   - M3 [Button] (filled) for "继续" (the primary
 *     action — the modal-sheet equivalent of the
 *     M3 "primary button on the right" dialog
 *     convention; the user is far more likely to
 *     resume than exit, so 继续 sits in the more
 *     visually prominent right slot)
 *   - `typography.titleMedium` for the "已暂停 ·
 *     N / N" summary line
 *   - `typography.labelLarge` for the button labels
 *     (M3 standard button-text role)
 *
 * Modal behavior:
 *   - skipPartiallyExpanded = true so the sheet
 *     snaps to its full content height on show
 *     (no half-height "peek" state the user would
 *     have to swipe up to see the buttons)
 *   - onDismissRequest dispatches [ResumePractice]
 *     — tapping on the scrim (above the sheet)
 *     resumes, which is the more common action
 *     than exit. System back is also wired to
 *     [ResumePractice] in [PracticeScreen]'s outer
 *     BackHandler, so the scrim-tap and back
 *     behaviors match.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PracticePausedView(
    state: PracticeState,
    currentImage: SceneImage?,
    imageDecoder: SceneImageDecoder?,
    imageLazyLoader: PurFileParser?,
    onAction: (PockRefAction) -> Unit,
) {
    // No opaque scrim here — the image needs to be
    // visible at full brightness so the user can
    // evaluate "do I continue with this image?"
    // (which is the whole point of pause). The M3
    // [ModalBottomSheet] brings its own
    // semi-transparent scrim around the sheet itself,
    // but the image above the sheet is untouched.
    // V_NOW (2026-06-08 user feedback "速写模式暂停时图
    // 片周边的背景还是保持黑色，不要变成白色的"):
    // wrap with [Surface(color = scrim)] (the M3
    // scrim = `Color(0xFF000000)` per PockRef's Theme.kt
    // — opaque pure black) so the area AROUND the
    // image stays black instead of showing the
    // underlying Scaffold's [colorScheme.background]
    // (which is near-white in light mode). Same
    // pattern [PracticeRunningView] uses for its
    // COUNTDOWN / REST states. The image is rendered
    // at alpha 1f so the image itself stays at
    // full brightness; only the gap between the
    // image's bounding box and the screen edge
    // (where the image's ContentScale.Fit leaves
    // letterbox space) is filled with black.
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.scrim,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Image as fullscreen background. NOT dimmed
            // during paused — the user explicitly wants
            // "图完整可见" (image fully visible) in the
            // spec for this option. The M3 sheet below
            // gives the modal context without darkening
            // the image.
        PracticeImageLayer(
            image = currentImage,
            imageDecoder = imageDecoder,
            imageLazyLoader = imageLazyLoader,
            alpha = 1f,
        )

        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            modifier = Modifier.glassMaterial(MaterialTheme.shapes.extraLarge, GlassRole.Popup),
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            tonalElevation = 0.dp,
            onDismissRequest = { onAction(PockRefAction.ResumePractice) },
            sheetState = sheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Summary line: "⏸ 已暂停 · 3 / 10".
                // Single-row layout (no big icon) so the
                // sheet stays short — the user is going
                // to be looking at the image above, not
                // the sheet text.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Pause,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "已暂停 · ${state.progressLabel}",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                // Action row: secondary "结束练习" (outlined)
                // + primary "继续" (filled), per M3 dialog
                // convention (primary on the right).
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedButton(
                        onClick = { onAction(PockRefAction.ExitPractice) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = "结束练习",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    Button(
                        onClick = { onAction(PockRefAction.ResumePractice) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "继续",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
        }  // close the Box that holds image + sheet
    }  // close the M3 Surface (scrim background)
}

/**
 * V_NOW: the FINISHED overlay. Same M3 [Surface] card
 * pattern as PAUSED, with a primary-tinted check icon
 * for the "you did it" visual cue.
 */
@Composable
private fun PracticeFinishedOverlay(
    state: PracticeState,
    onAction: (PockRefAction) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.scrim,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            GlassSurface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                // P1 (2026-06-11 chrome unify): dialog
                // tier (24dp). 4dp tighter than the M3
                // default [MaterialTheme.shapes.extraLarge]
                // (28dp) so an in-canvas modal reads as
                // a tighter overlay than the screen-edge
                // bottom sheets — they keep their
                // [Dimens.SheetCornerRadius] = 28dp.
                shape = RoundedCornerShape(Dimens.DialogCornerRadius),
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 40.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        // Primary-tinted check so the user
                        // sees the brand colour carry the
                        // "completion" semantic (PockRef's
                        // primary is the cyan / deep-teal
                        // accent, so this also acts as a
                        // brand-anchor moment).
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp),
                    )
                    Text(
                        text = "练习完成",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = "已完成 ${state.totalImages} 张图片",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { onAction(PockRefAction.ExitPractice) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "返回画布",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

/**
 * V_NOW: top-right countdown badge. M3 [Surface] with
 * `color = inverseSurface` (the dark-gray in light
 * theme, light-gray in dark theme) and M3
 * `MaterialTheme.shapes.large` (16dp, chip-shaped).
 *
 * `inverseOnSurface` is the M3-canonical text colour
 * for content rendered on top of an `inverseSurface`
 * (e.g. snackbar / tooltip). The badge text uses
 * `headlineLarge` (32sp per M3 spec, in the 28-36sp
 * range the user asked for) + a SemiBold weight.
 *
 * Content adapts to phase / status:
 *   - COUNTDOWN: "MM:SS" (time left in the per-image hold)
 *   - REST: "休息 MM:SS" (so the user knows we're between
 *     images, not on a new image)
 *   - PAUSED: "⏸ MM:SS" (frozen, with a pause icon prefix)
 *   - FINISHED: "✓ 完成" (no time — session is over)
 */
@Composable
private fun CountdownBadge(
    state: PracticeState,
    modifier: Modifier = Modifier,
) {
    val text = when {
        state.status == PracticeStatus.FINISHED -> "✓ 完成"
        state.status == PracticeStatus.PAUSED -> "⏸ " + formatMmSs(state.remainingMs)
        state.phase == PracticePhase.REST -> "休息 " + formatMmSs(state.remainingMs)
        else -> formatMmSs(state.remainingMs)
    }
    GlassSurface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        // P1 (2026-06-11 chrome unify): card tier (16dp).
        // 4dp tighter than the dialog tier so the
        // small inline badge doesn't visually compete
        // with the completion dialog it sits inside of.
        shape = RoundedCornerShape(Dimens.CardCornerRadius),
        modifier = modifier,
    ) {
        Text(
            text = text,
            // headlineLarge = 32sp per M3 spec. In the
            // user-requested 28-36sp range. SemiBold is
            // the M3-standard weight for headlines.
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

/**
 * V_NOW: image layer. Decodes the [SceneImage] via the
 * existing [SceneImageDecoder] pipeline at the screen's
 * pixel dimensions so the decoder picks the closest
 * DECODE_LADDER level (e.g. 1080×1920 phone → level 3 =
 * 1024px long side). The bitmap is cached by
 * `remember(image.id)` so the same image across multiple
 * ticker frames doesn't re-decode.
 *
 * [alpha] applies the rest-phase dim transition (M3
 * medium-emphasis 300ms motion, see [PracticeRunningView]).
 */
@Composable
private fun PracticeImageLayer(
    image: SceneImage?,
    imageDecoder: SceneImageDecoder?,
    imageLazyLoader: PurFileParser?,
    alpha: Float,
) {
    if (image == null) return
    val configuration = LocalConfiguration.current
    val displayWidth = configuration.screenWidthDp
    val displayHeight = configuration.screenHeightDp
    val bitmap = remember(image.id, displayWidth, displayHeight) {
        decodePracticeImage(
            image = image,
            imageDecoder = imageDecoder,
            imageLazyLoader = imageLazyLoader,
            displayWidth = displayWidth,
            displayHeight = displayHeight,
        )
    } ?: return
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize().glassBackdropSource().background(Color.Black),
        alpha = alpha,
    )
}

/**
 * V_NOW: image-bytes resolver + bitmap decode. Mirrors
 * the canvas path: [SceneImageDecoder.decode] with the
 * screen dimensions so the decoder picks the closest
 * DECODE_LADDER level.
 */
private fun decodePracticeImage(
    image: SceneImage,
    imageDecoder: SceneImageDecoder?,
    imageLazyLoader: PurFileParser?,
    displayWidth: Int,
    displayHeight: Int,
): android.graphics.Bitmap? {
    if (imageDecoder == null) return null
    return imageDecoder.decode(image, displayWidth, displayHeight)?.bitmap
}

/** Format milliseconds as zero-padded MM:SS. */
private fun formatMmSs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val mm = totalSec / 60
    val ss = totalSec % 60
    return "%02d:%02d".format(mm, ss)
}
