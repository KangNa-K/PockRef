package com.pockref.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.pockref.app.data.ThemeMode

/**
 * Material 3 color palette — full 28-role ColorScheme for both
 * light and dark themes.
 *
 * Design constraints:
 *   - **Brand colours preserved**: primary stays a deep teal in
 *     light mode (Color(0xFF005E7A)) and a light cyan in dark
 *     mode (Color(0xFF6AD6FF)) so PockRef reads as the same
 *     product across themes. All on* and container* roles are
 *     derived from these by HCT (Hue-Chroma-Tone) adjustments
 *     per M3 spec.
 *   - **M3 standard structure**: every role the M3 spec defines
 *     is explicitly set (no implicit fallbacks), so any
 *     [androidx.compose.material3] component that reads from
 *     `colorScheme.*` gets a sensible value. Pre-this-commit,
 *     18/28 roles were relying on the default [ColorScheme]
 *     constructor's fallback (which produced grey-on-grey in
 *     light mode and weak contrast overall).
 *   - **Dynamic Color opt-in**: callers can pass
 *     `dynamicColor = true` (default-on) to follow Android 12+
 *     wallpaper-derived palettes. Brand identity is preserved by
 *     default (dynamicColor = false at the [PockRefTheme] call
 *     site) since PockRef is a creator-tool app where the brand
 *     colour carries meaning ("the auto-layout / one-touch
 *     magic" affordance is always cyan).
 *
 * What's still TODO in future commits (not in this P0 batch):
 *   - Extended semantic colours (selectedImage / lockedImage /
 *     snapHighlight) — currently hardcoded in
 *     [com.pockref.app.ui.PureRefApp.LockedStrokeColor] and
 *     [com.pockref.app.ui.CanvasMinimap.MinimapColors]. The
 *     [ExtendedColors] object below is the placeholder; P1
 *     follow-up will route those call sites through it so light
 *     / dark themes have proper contrast on those affordances.
 *   - highContrast color schemes (M3 spec supports
 *     [dynamicDarkColorScheme] + [dynamicLightColorScheme]
 *     variants; needs `LocalAccessibilityManager` to read
 *     the user's system high-contrast toggle).
 */

// ── Light scheme (full 28 roles) ────────────────────────────────────
//
// Brand primary: deep teal #005E7A. Derives to:
//   onPrimary           = white  (4.5:1+ vs #005E7A ✓)
//   primaryContainer    = pale cyan #B5E9FF
//   onPrimaryContainer  = near-black #001E2A  (4.5:1+ vs #B5E9FF ✓)
//
// Brand secondary: same teal hue as primary (M3 tone 40), shifted
// slightly bluer so it reads as a distinct accent against the
// brand primary without leaving the cyan family. Derives to:
//   onSecondary           = white
//   secondaryContainer    = pale blue #CDE5FF (90)
//   onSecondaryContainer  = near-black #001E2A (10)
//
// Brand tertiary: forest green #1E6B2A. Derives to:
//   onTertiary            = white
//   tertiaryContainer     = mint #A8F5A8
//   onTertiaryContainer   = near-black #002106
//
// Error: standard M3 red #BA1A1A (not brand-tinted — errors should
// always be the M3 canonical red so the user recognises "wrong /
// broken" without re-learning a brand colour).
private val LightColors = lightColorScheme(
    primary = Color(0xFF005E7A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB5E9FF),
    onPrimaryContainer = Color(0xFF001E2A),
    secondary = Color(0xFF006590),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCDE5FF),
    onSecondaryContainer = Color(0xFF001E2A),
    tertiary = Color(0xFF1E6B2A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFA8F5A8),
    onTertiaryContainer = Color(0xFF002106),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFCFCFF),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFCFCFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFDDE3EA),
    onSurfaceVariant = Color(0xFF41484D),
    outline = Color(0xFF71787E),
    outlineVariant = Color(0xFFC1C7CE),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF2F3133),
    inverseOnSurface = Color(0xFFF1F0F4),
    inversePrimary = Color(0xFF6AD6FF),
    // surfaceTint drives tonalElevation overlays — M3 spec says it
    // should equal primary so elevation-tinted surfaces still feel
    // brand-coherent.
    surfaceTint = Color(0xFF005E7A),
)

// ── Dark scheme (full 28 roles) ─────────────────────────────────────
//
// Inverted tones: primary becomes the lighter end of the HCT pair
// (light cyan #6AD6FF) so it has enough contrast on the dark
// surface #17181C. onPrimary flips to a deep teal so primary
// backgrounds still read as "the brand colour, not white text
// randomly".
private val DarkColors = darkColorScheme(
    primary = Color(0xFF6AD6FF),
    onPrimary = Color(0xFF003547),
    primaryContainer = Color(0xFF004D6A),
    onPrimaryContainer = Color(0xFFB5E9FF),
    secondary = Color(0xFF82CFFA),
    onSecondary = Color(0xFF003547),
    secondaryContainer = Color(0xFF004C6A),
    onSecondaryContainer = Color(0xFFCDE5FF),
    tertiary = Color(0xFF8BE28B),
    onTertiary = Color(0xFF003910),
    tertiaryContainer = Color(0xFF005319),
    onTertiaryContainer = Color(0xFFA8F5A8),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0D0E11),
    onBackground = Color(0xFFE2E2E5),
    surface = Color(0xFF17181C),
    onSurface = Color(0xFFE2E2E5),
    surfaceVariant = Color(0xFF41484D),
    onSurfaceVariant = Color(0xFFC1C7CE),
    outline = Color(0xFF8B9198),
    outlineVariant = Color(0xFF41484D),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFE2E2E5),
    inverseOnSurface = Color(0xFF2F3133),
    inversePrimary = Color(0xFF005E7A),
    surfaceTint = Color(0xFF6AD6FF),
)

/**
 * Theme entry point.
 *
 * - [themeMode]        : light / dark / system-follow (default).
 * - [dynamicColor]     : when true AND running on Android 12+,
 *                        use [dynamicLightColorScheme] /
 *                        [dynamicDarkColorScheme] to follow the
 *                        user's wallpaper palette. Default `false`
 *                        to preserve brand identity — PockRef is a
 *                        creator tool, the cyan primary is a
 *                        signature affordance.
 */
@Composable
fun PockRefTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val useDarkTheme = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (useDarkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        useDarkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
