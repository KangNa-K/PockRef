package com.pockref.app.ui

import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

internal enum class GlassRole(val tintAlpha: Float, val blurDp: Float) {
    Floating(0.44f, 44f), Popup(0.58f, 50f),
}

@RequiresApi(31)
internal class GlassBackdrop {
    val source = RenderNode("PockRef backdrop").apply { clipToBounds = false }
    var origin by mutableStateOf(Offset.Zero)
    var revision by mutableIntStateOf(0)
    var consumers by mutableIntStateOf(0)
}

internal val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }
private val LocalGlassEnabled = staticCompositionLocalOf { true }

@Composable
internal fun GlassBackdropHost(
    enabled: Boolean = LocalGlassEnabled.current,
    content: @Composable () -> Unit,
) {
    val backdrop = remember(enabled) {
        if (enabled && Build.VERSION.SDK_INT >= 31) GlassBackdrop() else null
    }
    DisposableEffect(backdrop) {
        onDispose { if (Build.VERSION.SDK_INT >= 31) backdrop?.source?.discardDisplayList() }
    }
    CompositionLocalProvider(
        LocalGlassBackdrop provides backdrop,
        LocalGlassEnabled provides enabled,
        content = content,
    )
}

/** Record only scene content, never the chrome which samples it. No bitmap readbacks. */
@Composable
internal fun Modifier.glassBackdropSource(enabled: Boolean = true): Modifier {
    val backdrop = LocalGlassBackdrop.current
    if (!enabled || Build.VERSION.SDK_INT < 31 || backdrop == null) return this
    val view = LocalView.current
    return onGloballyPositioned {
        val screen = IntArray(2); view.getLocationOnScreen(screen)
        val window = IntArray(2); view.getLocationInWindow(window)
        screen[0] -= window[0]; screen[1] -= window[1]
        backdrop.origin = it.positionInWindow() + Offset(screen[0].toFloat(), screen[1].toFloat())
    }
        .drawWithContent {
            val target = drawContext.canvas
            if (!target.nativeCanvas.isHardwareAccelerated || backdrop.consumers == 0) {
                drawContent()
            } else {
                val width = size.width.toInt().coerceAtLeast(1)
                val height = size.height.toInt().coerceAtLeast(1)
                backdrop.source.setPosition(0, 0, width, height)
                val recording = backdrop.source.beginRecording(width, height)
                try {
                    drawContext.canvas = Canvas(recording)
                    drawContent()
                } finally {
                    drawContext.canvas = target
                    backdrop.source.endRecording()
                }
                target.nativeCanvas.drawRenderNode(backdrop.source)
                // Separate dialog windows do not inherit the source's invalidation.
                Snapshot.withoutReadObservation { backdrop.revision++ }
            }
        }
}

/** Magpie's neutral tint and 44/50dp blur; only the backdrop is blurred. */
@Composable
internal fun Modifier.glassMaterial(
    shape: Shape = MaterialTheme.shapes.large,
    role: GlassRole = GlassRole.Floating,
    surface: Color = MaterialTheme.colorScheme.surface,
): Modifier = composed {
    glassMaterialImpl(shape, role, surface)
}

@Composable
private fun Modifier.glassMaterialImpl(shape: Shape, role: GlassRole, surface: Color): Modifier {
    val backdrop = LocalGlassBackdrop.current
    if (Build.VERSION.SDK_INT < 31 || backdrop == null) return clip(shape).background(surface.copy(alpha = 1f))
    val tint = if (surface.luminance() > 0.5f) Color(0xFFE0E0E0) else Color(0xFF303030)
    val node = remember(backdrop) { RenderNode("PockRef frosted material") }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val radius = with(density) { role.blurDp.dp.toPx() }
    val effect = remember(radius) { RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP) }
    var position by remember { mutableStateOf(Offset.Zero) }
    DisposableEffect(backdrop, node) {
        backdrop.consumers++
        onDispose { backdrop.consumers--; node.discardDisplayList() }
    }
    val view = LocalView.current
    return clip(shape).onGloballyPositioned {
        val screen = IntArray(2); view.getLocationOnScreen(screen)
        val window = IntArray(2); view.getLocationInWindow(window)
        screen[0] -= window[0]; screen[1] -= window[1]
        position = it.positionInWindow() + Offset(screen[0].toFloat(), screen[1].toFloat())
    }
        .drawWithContent {
            backdrop.revision
            val canvas = drawContext.canvas.nativeCanvas
            if (canvas.isHardwareAccelerated && backdrop.source.hasDisplayList()) {
                val padding = (radius * 2).toInt()
                val width = size.width.toInt().coerceAtLeast(1)
                val height = size.height.toInt().coerceAtLeast(1)
                node.setPosition(-padding, -padding, width + padding, height + padding)
                val recording = node.beginRecording(width + padding * 2, height + padding * 2)
                try {
                    recording.translate(
                        backdrop.origin.x - position.x + padding,
                        backdrop.origin.y - position.y + padding,
                    )
                    recording.drawRenderNode(backdrop.source)
                } finally { node.endRecording() }
                node.setRenderEffect(effect)
                canvas.drawRenderNode(node)
                drawRect(tint.copy(alpha = role.tintAlpha))
            } else {
                drawRect(surface.copy(alpha = 1f))
            }
            drawContent()
        }
}

// Keep the Material Surface signature for call-site compatibility; glass owns its tint and edge.
@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    color: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = contentColorFor(color),
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 0.dp,
    border: BorderStroke? = null,
    onClick: (() -> Unit)? = null,
    role: GlassRole = GlassRole.Floating,
    content: @Composable () -> Unit,
) {
    val material = modifier.glassMaterial(shape, role, color).then(
        if (border != null) Modifier.border(border, shape) else Modifier,
    )
    if (onClick != null) {
        Surface(onClick = onClick, modifier = material, shape = shape,
            color = Color.Transparent, contentColor = contentColor, tonalElevation = 0.dp,
            shadowElevation = 0.dp, border = null, content = content)
    } else {
        Surface(modifier = material, shape = shape, color = Color.Transparent,
            contentColor = contentColor, tonalElevation = 0.dp, shadowElevation = 0.dp,
            border = null, content = content)
    }
}

@Suppress("UNUSED_PARAMETER")
@Composable
internal fun GlassAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = 0.dp,
    properties: DialogProperties = DialogProperties(),
) {
    AlertDialog(onDismissRequest, confirmButton, modifier.glassMaterial(shape, GlassRole.Popup),
        dismissButton, icon, title, text, shape, Color.Transparent, iconContentColor,
        titleContentColor, textContentColor, 0.dp, properties)
}


@Composable
internal fun GlassDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val material = modifier.glassMaterial(MaterialTheme.shapes.extraSmall, GlassRole.Popup)
    val colors = MaterialTheme.colorScheme
    MaterialTheme(colorScheme = colors.copy(
        surface = Color.Transparent, surfaceContainer = Color.Transparent,
        surfaceContainerHigh = Color.Transparent, surfaceTint = Color.Transparent,
    )) {
        DropdownMenu(expanded, onDismissRequest, modifier = material, content = content)
    }
}
