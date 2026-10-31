package com.pockref.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import com.pockref.app.ui.theme.Dimens
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.pockref.app.model.CanvasMarkupTool
import com.pockref.app.model.PockRefUiState

private enum class MarkupPalettePanel { STYLE, SHAPES }

private val extraTools = listOf(
    CanvasMarkupTool.LINE to "直线", CanvasMarkupTool.ARROW to "箭头",
    CanvasMarkupTool.RECTANGLE to "矩形", CanvasMarkupTool.ELLIPSE to "椭圆",
    CanvasMarkupTool.SELECT to "选择", CanvasMarkupTool.TEXT to "添加文本",
)

private class MarkupPanelPositionProvider(private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
        layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset = IntOffset(
        (anchorBounds.center.x - popupContentSize.width / 2)
            .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
        (anchorBounds.top - popupContentSize.height - gap)
            .coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)),
    )
}

@Composable
internal fun MarkupToolPalette(
    state: PockRefUiState,
    onAction: (PockRefAction) -> Unit,
    modifier: Modifier = Modifier,
    onColorRequested: () -> Unit = {},
) {
    var panel by rememberSaveable { mutableStateOf<MarkupPalettePanel?>(null) }
    fun toggle(next: MarkupPalettePanel) { panel = if (panel == next) null else next }

    val panelGap = with(LocalDensity.current) { 8.dp.roundToPx() }
    val panelPosition = remember(panelGap) { MarkupPanelPositionProvider(panelGap) }
    Box(modifier.widthIn(max = 496.dp), contentAlignment = Alignment.Center) {
        if (panel != null) {
            Popup(popupPositionProvider = panelPosition, onDismissRequest = { panel = null },
                properties = PopupProperties(focusable = true)) {
                val panelWidth = if (panel == MarkupPalettePanel.SHAPES) 368.dp else 254.dp
                GlassSurface(modifier = Modifier.width(panelWidth).testTag("markup-options-popup"),
                    shape = RoundedCornerShape(Dimens.ChromeCornerRadius), role = GlassRole.Popup) {
                    Box(Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
                        if (panel == MarkupPalettePanel.STYLE) {
                            MarkupStyleOptions(state, onAction)
                        } else {
                            Row(Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                extraTools.forEach { (tool, label) ->
                                    PaletteChoice(label, state.markupTool == tool, {
                                        onAction(PockRefAction.SetMarkupTool(tool))
                                        if (tool == CanvasMarkupTool.TEXT) onAction(PockRefAction.RequestMarkupText)
                                        panel = null
                                    }) { Icon(toolIcon(tool), null, Modifier.size(26.dp)) }
                                }
                            }
                        }
                    }
                }
            }
        }
        GlassSurface(modifier = Modifier.testTag("markup-tool-dock"),
            shape = RoundedCornerShape(Dimens.ChromeCornerRadius)) {
            Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Column(Modifier.testTag("markup-history-controls")) {
                        PaletteIcon("撤销", Icons.AutoMirrored.Rounded.Undo, state.canUndo) { onAction(PockRefAction.Undo) }
                        PaletteIcon("重做", Icons.AutoMirrored.Rounded.Redo, state.canRedo) { onAction(PockRefAction.Redo) }
                    }
                    listOf(CanvasMarkupTool.PEN to "画笔", CanvasMarkupTool.HIGHLIGHTER to "荧光笔",
                        CanvasMarkupTool.ERASER to "对象橡皮", CanvasMarkupTool.INK_ERASER to "局部擦墨迹",
                        CanvasMarkupTool.LASSO to "套索").forEach { (tool, label) ->
                        PaletteChoice(label, state.markupTool == tool, {
                            if (state.markupTool == tool && (tool == CanvasMarkupTool.PEN || tool == CanvasMarkupTool.HIGHLIGHTER)) {
                                toggle(MarkupPalettePanel.STYLE)
                            } else {
                                onAction(PockRefAction.SetMarkupTool(tool))
                                panel = null
                            }
                        }, tall = true) {
                            PenToolIllustration(tool, state.markupColor, state.markupTool == tool)
                        }
                    }
                    PaletteChoice("颜色", false,
                        { panel = null; onColorRequested() }) {
                        Canvas(Modifier.size(34.dp)) {
                            drawCircle(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green,
                                Color.Cyan, Color.Blue, Color.Magenta, Color.Red)))
                            drawCircle(Color.White, radius = size.minDimension * 0.36f)
                            drawCircle(state.markupColor, radius = size.minDimension * 0.29f)
                        }
                    }
                    val extraSelected = extraTools.any { it.first == state.markupTool }
                    PaletteChoice("形状和更多工具", panel == MarkupPalettePanel.SHAPES || extraSelected,
                        { toggle(MarkupPalettePanel.SHAPES) }) {
                        Icon(Icons.Rounded.Add,
                            null, Modifier.size(28.dp))
                    }
                }
            }
        }
    }
}

@Composable
internal fun MarkupSelectionActions(
    state: PockRefUiState,
    onAction: (PockRefAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.markupSelection.isEmpty()) return
    GlassSurface(modifier = modifier.testTag("markup-selection-actions"),
        shape = RoundedCornerShape(Dimens.ChromeCornerRadius)) {
        Column(Modifier.padding(4.dp).semantics {
            contentDescription = "已选 ${state.markupSelection.size} 项"
        }, horizontalAlignment = Alignment.CenterHorizontally) {
            PaletteIcon("删除", Icons.Rounded.DeleteOutline) { onAction(PockRefAction.DeleteSelectedMarkup) }
            PaletteIcon("复用", Icons.Rounded.ContentCopy) { onAction(PockRefAction.DuplicateSelectedMarkup) }
        }
    }
}

@Composable
private fun MarkupStyleOptions(state: PockRefUiState, onAction: (PockRefAction) -> Unit) {
    val ink = MaterialTheme.colorScheme.onSurface
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(2f, 4f, 8f, 16f).forEach { width ->
            PaletteChoice("线宽 ${width.toInt()}", state.markupLineWidth == width,
                { onAction(PockRefAction.SetMarkupWidth(width)) }) {
                Canvas(Modifier.size(30.dp)) {
                    drawLine(ink, Offset(size.width * 0.22f, size.height * 0.22f),
                        Offset(size.width * 0.78f, size.height * 0.78f), width.dp.toPx(), StrokeCap.Round)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaletteChoice(label: String, selected: Boolean, onClick: () -> Unit,
    tall: Boolean = false, content: @Composable () -> Unit) {
    TooltipBox(positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } }, state = rememberTooltipState()) {
        Box(Modifier.size(52.dp, if (tall) 80.dp else 52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f) else Color.Transparent)
            .selectable(selected, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaletteIcon(label: String, icon: ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    TooltipBox(positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } }, state = rememberTooltipState()) {
        IconButton(onClick, enabled = enabled) { Icon(icon, label, Modifier.size(24.dp)) }
    }
}

private fun toolIcon(tool: CanvasMarkupTool): ImageVector = when (tool) {
    CanvasMarkupTool.LINE -> Icons.Rounded.HorizontalRule
    CanvasMarkupTool.ARROW -> Icons.Rounded.NorthEast
    CanvasMarkupTool.RECTANGLE -> Icons.Rounded.CropSquare
    CanvasMarkupTool.ELLIPSE -> Icons.Rounded.PanoramaFishEye
    CanvasMarkupTool.TEXT -> Icons.Rounded.Title
    CanvasMarkupTool.SELECT -> Icons.Rounded.NearMe
    else -> Icons.Rounded.Add
}

/** Vector pen silhouettes stay crisp at tablet density and need no bitmap assets. */
@Composable
private fun PenToolIllustration(tool: CanvasMarkupTool, color: Color, selected: Boolean) {
    val outline = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f)
    Canvas(Modifier.size(44.dp, if (selected) 78.dp else 70.dp)) {
        scale(size.width / 44f, size.height / 84f, pivot = Offset.Zero) {
            val body = Brush.horizontalGradient(listOf(Color(0xFFD1D2D4), Color.White, Color(0xFFE3E4E6)), 8f, 36f)
            fun polygon(points: List<Offset>): Path = Path().apply {
                moveTo(points[0].x, points[0].y)
                points.drop(1).forEach { lineTo(it.x, it.y) }; close()
            }
            when (tool) {
                CanvasMarkupTool.PEN -> {
                    val nib = polygon(listOf(Offset(22f, 5f), Offset(10f, 57f), Offset(34f, 57f)))
                    drawPath(nib, body)
                    drawPath(nib, outline, style = Stroke(0.7f))
                    drawPath(polygon(listOf(Offset(22f, 5f), Offset(19f, 17f), Offset(25f, 17f))), color)
                    drawRect(body, Offset(10f, 57f), Size(24f, 35f))
                    drawRect(color, Offset(10f, 60f), Size(24f, 4f))
                }
                CanvasMarkupTool.HIGHLIGHTER -> {
                    drawPath(polygon(listOf(Offset(13f, 12f), Offset(30f, 5f), Offset(30f, 26f), Offset(13f, 26f))), color)
                    drawPath(polygon(listOf(Offset(13f, 25f), Offset(30f, 25f), Offset(32f, 44f),
                        Offset(36f, 54f), Offset(36f, 88f), Offset(8f, 88f), Offset(8f, 54f), Offset(12f, 44f))), body)
                    drawRect(color, Offset(8f, 60f), Size(28f, 7f))
                }
                CanvasMarkupTool.ERASER, CanvasMarkupTool.INK_ERASER -> {
                    drawRoundRect(body, Offset(9f, 8f), Size(26f, 86f), CornerRadius(7f))
                    drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFFD87986), Color(0xFFFFB4BC), Color(0xFFD87986)), 9f, 35f),
                        Offset(9f, 8f), Size(26f, 25f), CornerRadius(7f))
                    drawRect(body, Offset(9f, 30f), Size(26f, 12f))
                    drawLine(outline, Offset(9f, 42f), Offset(35f, 42f), 1f)
                    if (tool == CanvasMarkupTool.INK_ERASER) {
                        drawLine(Color(0xFF61656A), Offset(16f, 56f), Offset(28f, 68f), 2f, StrokeCap.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f)))
                    }
                }
                else -> {
                    val loop = Path().apply {
                        moveTo(29f, 51f); cubicTo(6f, 54f, 8f, 15f, 23f, 14f)
                        cubicTo(40f, 13f, 39f, 49f, 23f, 53f)
                        cubicTo(11f, 56f, 17f, 70f, 29f, 63f)
                    }
                    drawPath(loop, outline, style = Stroke(2f, cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f))))
                    drawRoundRect(body, Offset(18f, 69f), Size(10f, 24f), CornerRadius(3f))
                }
            }
        }
    }
}
