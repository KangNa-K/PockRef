package com.pockref.app.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pockref.app.ui.theme.Dimens

/** Shared fixed-size, non-modal window for canvas chrome. */
@Composable
internal fun BoxScope.CanvasChromePopup(
    visible: Boolean,
    toolbarWidth: Dp,
    title: String,
    alignment: Alignment = Alignment.BottomStart,
    endInset: Dp = Dimens.CornerInset,
    modifier: Modifier = Modifier,
    topInset: Dp? = null,
    bottomInset: Dp = Dimens.CornerInset,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val panelWidth = minOf(toolbarWidth,
            (maxWidth - Dimens.CornerInset - endInset).coerceAtLeast(0.dp))
        val panelHeight = if (topInset != null) {
            (maxHeight - topInset - bottomInset).coerceAtLeast(0.dp)
        } else {
            minOf(520.dp, maxHeight * 0.68f,
                (maxHeight - bottomInset - 8.dp).coerceAtLeast(0.dp))
        }
        val origin = TransformOrigin(if (alignment == Alignment.BottomEnd) 1f else 0f, 1f)
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(alignment).padding(
                start = Dimens.CornerInset, end = endInset,
                top = topInset ?: 0.dp, bottom = bottomInset),
            enter = fadeIn(tween(140)) + scaleIn(initialScale = 0.94f,
                transformOrigin = origin, animationSpec = tween(180)),
            exit = fadeOut(tween(100)) + scaleOut(targetScale = 0.97f,
                transformOrigin = origin, animationSpec = tween(120)),
        ) {
            GlassSurface(
                modifier = modifier.width(panelWidth).height(panelHeight)
                    .semantics { paneTitle = title },
                role = GlassRole.Popup,
                shape = RoundedCornerShape(Dimens.ChromeCornerRadius),
                color = MaterialTheme.colorScheme.surface.copy(alpha = Dimens.ChromeSurfaceAlpha),
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = Dimens.ToolbarElevation,
                shadowElevation = 0.dp,
                content = content,
            )
        }
    }
}

/** Settings and layers share one right-hand popup footprint. */
@Composable
internal fun BoxScope.CanvasSidePopup(
    visible: Boolean,
    title: String,
    modifier: Modifier = Modifier,
    panelWidth: Dp = 360.dp,
    topInset: Dp = with(LocalDensity.current) {
        WindowInsets.statusBars.getTop(this).toDp()
    } + Dimens.CornerInset,
    bottomInset: Dp = Dimens.CornerInset,
    content: @Composable () -> Unit,
) {
    CanvasChromePopup(
        visible = visible,
        toolbarWidth = panelWidth,
        title = title,
        alignment = Alignment.BottomEnd,
        endInset = Dimens.CornerInset + Dimens.BottomPanelChromeSize + 8.dp,
        modifier = modifier,
        topInset = topInset,
        bottomInset = bottomInset,
        content = content,
    )
}
