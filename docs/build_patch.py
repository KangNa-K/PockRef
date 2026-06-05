#!/usr/bin/env python3
"""Build docs/M3_P0_FIXES.patch as a git-apply-ready unified diff.

The worktree's gitdir is missing, so we can't `git diff` directly.
This script reconstructs the "before" content of each modified
file from the original code I read earlier in the session, then
combines them with the current "after" content (read from disk)
into a proper unified diff that `git apply` can consume.

Strategy: for each of the 3 modified files, build a synthetic
"before" file (with my 7 PureRefApp edits undone) in a temp
location, then run `difflib.unified_diff` against the current
"after" file. The line numbers come out correct because the
synthetic before is built from the actual current after.
"""

import difflib
import shutil
import subprocess
import tempfile
from pathlib import Path

WORKTREE = Path("/Volumes/KNJ/项目/Android/pureref-merge-context-menu")
PATCH_FILE = WORKTREE / "docs/M3_P0_FIXES.patch"

REL = {
    "dimens": "app/src/main/java/com/pockref/app/ui/theme/Dimens.kt",
    "theme":  "app/src/main/java/com/pockref/app/ui/theme/Theme.kt",
    "app":    "app/src/main/java/com/pockref/app/ui/PureRefApp.kt",
}


# ── 1. Dimens.kt — full file rewrite (72 lines → 99 lines) ─────────

# BEFORE: the ORIGINAL full Dimens.kt content (read from session earlier)
B_DIMENS = '''\
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
     * The vertical (and square) size of every primary chrome
     * surface — the top toolbar AND the bottom-right auto-layout
     * FAB. Per the user's bottom-right FAB spec: "主按钮尺寸：
     * 右下角折叠状态的主按钮，其宽度和高度均等于顶部导航栏
     * （顶栏）的实际渲染高度，保持正方形或与顶栏高度一致的
     * 等比尺寸". Pinning both elements to this single token
     * means the FAB's edge stays flush with the toolbar's edge
     * no matter which one gets resized in the future — they
     * cannot drift apart. Also used by the panel's 4 expandable
     * ListItems so the panel reads as 5 identical squares stacked
     * vertically.
     */
    val TopToolbarHeight = 44.dp

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
'''


# ── 2. Theme.kt — full file rewrite (40 lines → ~190 lines) ─────────

B_THEME = '''\
package com.pockref.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.pockref.app.data.ThemeMode

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6AD6FF),
    secondary = Color(0xFFFFB866),
    tertiary = Color(0xFF8BE28B),
    surface = Color(0xFF17181C),
    background = Color(0xFF0D0E11),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF005E7A),
    secondary = Color(0xFF9C4C00),
    tertiary = Color(0xFF1E6B2A),
)

@Composable
fun PockRefTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val useDarkTheme = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (useDarkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content,
    )
}
'''


# ── 3. PureRefApp.kt — 7 separate edits ─────────────────────────────

# 3.1. Import block
B_APP_IMPORTS = '''\
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
'''


# 3.2. minimap default position
B_APP_MINIMAP_POS = '''\
    val defaultPosition = Offset(
        x = cornerInsetPx,
        y = parentHeight - with(density) { Dimens.TopToolbarHeight.toPx() } - cornerInsetPx,
    )
'''


# 3.3. minimap collapsed touch target
B_APP_MINIMAP_TOUCH = '''\
        val touchWidthDp = if (isHorizontalEdge) Dimens.TopToolbarHeight else 32.dp
        val touchHeightDp = if (isHorizontalEdge) 32.dp else Dimens.TopToolbarHeight
'''


# 3.4. ToolbarActionItem IconButton
B_APP_TOOLBAR_ITEM = '''\
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(36.dp),
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = resolvedTint,
        ),
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
        )
    }
'''


# 3.5. RecentFileItem IconButtons
B_APP_RECENT_ICONS = '''\
        IconButton(
            onClick = onOpen,
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                Icons.Rounded.FileOpen,
                contentDescription = "打开",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = "移除",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
'''


# 3.6. FAB — the big rewrite
B_APP_FAB = '''\
            // Always-visible FAB — the panel's anchor.
            // Gesture contract:
            //   - **Tap** = always run auto-layout (the FAB's
            //     primary action, same in both states).
            //   - **Long-press** = toggle the panel's
            //     expanded state.
            //
            // Size: pinned to [Dimens.TopToolbarHeight] so the
            // FAB edge sits flush with the top toolbar's bottom
            // edge (and the panel's 4 ListItems match the FAB
            // so the column reads as 5 identical squares).
            Box(
                modifier = Modifier
                    .size(Dimens.TopToolbarHeight)
                    .combinedClickable(
                        onClick = {
                            // V_NOW: chrome-mutex — both FAB
                            // tap (run auto-layout) and long-
                            // press (toggle the panel) close
                            // the top toolbar's 3-dots menu.
                            onMenuDismiss()
                            onAction(PockRefAction.ExecuteAutoLayout)
                        },
                        onLongClick = {
                            onMenuDismiss()
                            onAction(
                                PockRefAction.SetBottomRightPanelExpanded(
                                    !state.bottomRightPanelExpanded,
                                ),
                            )
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.AutoFixHigh,
                    contentDescription = "Auto-layout",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
'''


# 3.7. BottomPanelListItem Box
B_APP_LIST_ITEM = '''\
    Box(
        modifier = Modifier
            .size(Dimens.TopToolbarHeight)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp),
            tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
'''


# ─── After versions (read from current files) ───────────────────────

A_DIMENS = (WORKTREE / REL["dimens"]).read_text(encoding="utf-8")
A_THEME = (WORKTREE / REL["theme"]).read_text(encoding="utf-8")


def extract_fab_after() -> str:
    content = (WORKTREE / REL["app"]).read_text(encoding="utf-8")
    start_marker = "            // Always-visible FAB — the panel's anchor.\n"
    fab_start = content.index(start_marker)
    icon_close = "                tint = MaterialTheme.colorScheme.onPrimaryContainer,\n"
    icon_close_offset = content.index(icon_close, fab_start) + len(icon_close)
    box_close_marker = "        }\n"
    box_close_offset = content.index(box_close_marker, icon_close_offset) + len(box_close_marker)
    return content[fab_start:box_close_offset]


A_APP_FAB = extract_fab_after()


# ─── Reverse-engineer the pre-edit PureRefApp.kt ───────────────────

def reconstruct_app_before() -> str:
    """Read current (post-edit) PureRefApp.kt and undo my 7 changes.

    The BEFORE snippets above define the original content. We replace
    each post-edit block with the corresponding pre-edit block. The
    result is a synthetic 8960-line file that represents the original
    pre-edit state — enough for `diff` to compute correct line numbers.
    """
    current = (WORKTREE / REL["app"]).read_text(encoding="utf-8")

    # Read each AFTER version from the current file
    A_APP_IMPORTS = '''\
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
'''
    A_APP_MINIMAP_POS = '''\
    val defaultPosition = Offset(
        x = cornerInsetPx,
        y = parentHeight - with(density) { Dimens.BottomPanelChromeSize.toPx() } - cornerInsetPx,
    )
'''
    A_APP_MINIMAP_TOUCH = '''\
        val touchWidthDp = if (isHorizontalEdge) Dimens.BottomPanelChromeSize else 32.dp
        val touchHeightDp = if (isHorizontalEdge) 32.dp else Dimens.BottomPanelChromeSize
'''
    A_APP_TOOLBAR_ITEM = '''\
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(36.dp)
            .minimumInteractiveComponentSize(),
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = resolvedTint,
        ),
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
        )
    }
'''
    A_APP_RECENT_ICONS = '''\
        IconButton(
            onClick = onOpen,
            modifier = Modifier
                .size(28.dp)
                .minimumInteractiveComponentSize(),
        ) {
            Icon(
                Icons.Rounded.FileOpen,
                contentDescription = "打开",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .size(28.dp)
                .minimumInteractiveComponentSize(),
        ) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = "移除",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
'''
    A_APP_LIST_ITEM = '''\
    Box(
        modifier = Modifier
            .size(Dimens.BottomPanelChromeSize)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp),
            tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
'''

    # Replace each AFTER with the corresponding BEFORE
    pairs = [
        (A_APP_IMPORTS,        B_APP_IMPORTS),
        (A_APP_MINIMAP_POS,    B_APP_MINIMAP_POS),
        (A_APP_MINIMAP_TOUCH,  B_APP_MINIMAP_TOUCH),
        (A_APP_TOOLBAR_ITEM,   B_APP_TOOLBAR_ITEM),
        (A_APP_RECENT_ICONS,   B_APP_RECENT_ICONS),
        (A_APP_FAB,            B_APP_FAB),
        (A_APP_LIST_ITEM,      B_APP_LIST_ITEM),
    ]
    before = current
    for after_text, before_text in pairs:
        if after_text not in before:
            raise RuntimeError(f"Could not find AFTER block in current file:\n{after_text[:200]}...")
        before = before.replace(after_text, before_text, 1)
    return before


# ─── Diff helpers ───────────────────────────────────────────────────

def unified_diff_with_line_numbers(rel_path: str, before: str, after: str, n: int = 3) -> str:
    """Generate a proper unified diff with correct `@@ -X,Y +A,B @@` headers.

    `before` and `after` should be the COMPLETE file content (or
    near-complete) so that the line numbers come out right. Use
    `n=3` for 3 lines of context.
    """
    b = before.splitlines(keepends=True)
    a = after.splitlines(keepends=True)
    if b and not b[-1].endswith("\n"):
        b[-1] += "\n"
    if a and not a[-1].endswith("\n"):
        a[-1] += "\n"

    diff = difflib.unified_diff(
        b, a,
        fromfile=f"a/{rel_path}",
        tofile=f"b/{rel_path}",
        lineterm="\n",
        n=n,
    )
    return "".join(diff)


def main():
    sections = []

    # 1. Dimens.kt — use the section-level diff since Dimens.kt is small
    sections.append(unified_diff_with_line_numbers(REL["dimens"], B_DIMENS, A_DIMENS, n=3))

    # 2. Theme.kt — full file diff
    sections.append(unified_diff_with_line_numbers(REL["theme"], B_THEME, A_THEME, n=3))

    # 3. PureRefApp.kt — full file diff (before reconstructed, after = current)
    print("Reconstructing pre-edit PureRefApp.kt...")
    app_before = reconstruct_app_before()
    app_after = (WORKTREE / REL["app"]).read_text(encoding="utf-8")
    print(f"  Before: {len(app_before.splitlines())} lines")
    print(f"  After:  {len(app_after.splitlines())} lines")
    sections.append(unified_diff_with_line_numbers(REL["app"], app_before, app_after, n=3))

    # Write patch
    patch = "".join(sections)
    PATCH_FILE.write_text(patch, encoding="utf-8")
    print(f"\nWrote {PATCH_FILE} ({len(patch):,} bytes)")
    print(f"  Files patched: 3")
    print()
    print("Verify with:")
    print(f"  $ cd <worktree>")
    print(f"  $ git apply --check docs/M3_P0_FIXES.patch")
    print(f"  $ git apply docs/M3_P0_FIXES.patch")


if __name__ == "__main__":
    main()
