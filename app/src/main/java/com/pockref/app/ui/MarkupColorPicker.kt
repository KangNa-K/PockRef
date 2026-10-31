package com.pockref.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.core.content.edit
import com.pockref.app.ui.theme.Dimens
import kotlin.math.roundToInt

internal fun markupHex(color: Color): String = "%06X".format(color.toArgb() and 0xFFFFFF)
internal fun markupColorFromHex(text: String, alpha: Float): Color? =
    text.removePrefix("#").takeIf { it.length == 6 && it.all { c -> c in "0123456789abcdefABCDEF" } }
        ?.toLongOrNull(16)?.let { Color((0xFF000000L or it).toInt()).copy(alpha = alpha) }

internal fun markupSpectrumColor(x: Float, y: Float): Color {
    val nx = x.coerceIn(0f, 1f)
    return Color.hsv((y.coerceIn(0f, 1f) * 360f).coerceAtMost(359.999f),
        (nx * 2).coerceAtMost(1f), ((1 - nx) * 2).coerceAtMost(1f))
}

private fun spectrumPosition(color: Color): Offset {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    return Offset(if (hsv[2] < 0.999f) 1 - hsv[2] / 2 else hsv[1] / 2, hsv[0] / 360)
}

private fun gridColor(row: Int, col: Int): Color {
    if (row == 0) return Color.White.copy(red = 1 - col / 11f, green = 1 - col / 11f, blue = 1 - col / 11f)
    val hues = listOf(192f, 218f, 258f, 284f, 326f, 2f, 20f, 37f, 47f, 58f, 70f, 94f)
    return Color.hsv(hues[col], if (row <= 5) 1f else 1 - (row - 5) * 0.19f,
        if (row <= 5) 0.28f + (row - 1) * 0.18f else 1f)
}

/** Apply edits immediately: the non-modal panel stays open while drawing. */
@Composable
internal fun BoxScope.MarkupColorPicker(initialColor: Color, onColor: (Color) -> Unit, onDismiss: () -> Unit) {
    var argb by rememberSaveable { mutableIntStateOf(initialColor.toArgb()) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val color = Color(argb)
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("markup_saved_colors", android.content.Context.MODE_PRIVATE) }
    var saved by remember { mutableStateOf(prefs.getString("swatches", null)?.split(",")
        ?.mapNotNull { it.toIntOrNull() }?.take(12) ?: listOf(Color.White.toArgb(), Color(0xFFE53935).toArgb(), Color(0xFFFFD030).toArgb())) }
    fun updateColor(value: Color) {
        val next = value.toArgb()
        if (next == argb) return
        argb = next
        onColor(Color(next))
    }
    fun finish() { onDismiss() }

    BackHandler(onBack = ::finish)
    CanvasSidePopup(visible = true, title = "颜色窗口",
        modifier = Modifier.testTag("markup-color-panel")) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("颜色", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = ::finish, modifier = Modifier.size(Dimens.MinTouchTarget)) {
                    Icon(Icons.Rounded.Close, "关闭颜色面板")
                }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth().clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)).padding(3.dp)) {
                    listOf("网格", "光谱", "滑块").forEachIndexed { index, title ->
                        Box(Modifier.weight(1f).clip(CircleShape)
                            .background(if (tab == index) MaterialTheme.colorScheme.surface else Color.Transparent)
                            .selectable(tab == index, role = Role.Tab, onClick = { tab = index }).padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center) { Text(title) }
                    }
                }
                BoxWithConstraints {
                    val editor: @Composable () -> Unit = {
                        when (tab) {
                            0 -> ColorGrid(color) { updateColor(it.copy(alpha = color.alpha)) }
                            1 -> ColorSpectrum(color) { updateColor(it.copy(alpha = color.alpha)) }
                            else -> RgbColorControls(color, ::updateColor)
                        }
                    }
                    val details: @Composable () -> Unit = {
                        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                            ColorChannel("不透明度", color.alpha, "${(color.alpha * 100).roundToInt()}%",
                                listOf(color.copy(alpha = 0f), color.copy(alpha = 1f)), true,
                                onValue = { updateColor(color.copy(alpha = it)) })
                            HorizontalDivider()
                            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Top) {
                                Canvas(Modifier.size(92.dp).clip(RoundedCornerShape(22.dp)).semantics { contentDescription = "当前颜色预览" }) {
                                    checkerboard()
                                    drawRect(color)
                                    drawPath(Path().apply { moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(0f, size.height); close() }, color.copy(alpha = 1f))
                                }
                                Column(Modifier.weight(1f)) {
                                    saved.chunked(3).forEach { row ->
                                        Row {
                                            row.forEach { value ->
                                                val swatch = Color(value)
                                                Box(Modifier.size(48.dp).padding(6.dp).clip(CircleShape)
                                                    .background(swatch)
                                                    .border(if (value == argb) 3.dp else 1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                                    .clickable { updateColor(swatch) }
                                                    .semantics { contentDescription = "已存颜色 ${markupHex(swatch)}"; selected = value == argb })
                                            }
                                        }
                                    }
                                    FilledTonalIconButton(onClick = {
                                        saved = (saved.filterNot { it == argb } + argb).takeLast(12)
                                        prefs.edit { putString("swatches", saved.joinToString(",")) }
                                    }) { Icon(Icons.Rounded.Add, "保存当前颜色") }
                                }
                            }
                        }
                    }
                    if (maxWidth >= 600.dp) {
                        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                            Box(Modifier.weight(1f)) { editor() }
                            Box(Modifier.weight(1f)) { details() }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) { editor(); details() }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorGrid(color: Color, onColor: (Color) -> Unit) {
    Column(Modifier.fillMaxWidth().aspectRatio(1.2f).clip(RoundedCornerShape(24.dp))) {
        repeat(10) { row ->
            Row(Modifier.weight(1f)) {
                repeat(12) { col ->
                    val swatch = gridColor(row, col)
                    val chosen = swatch.toArgb() == color.copy(alpha = 1f).toArgb()
                    Box(Modifier.weight(1f).fillMaxHeight().background(swatch)
                        .then(if (chosen) Modifier.border(2.dp, Color.White) else Modifier)
                        .selectable(chosen, role = Role.Button, onClick = { onColor(swatch) })
                        .semantics { contentDescription = "网格颜色 ${row + 1}-${col + 1}" })
                }
            }
        }
    }
}

@Composable
private fun ColorSpectrum(color: Color, onColor: (Color) -> Unit) {
    var point by remember { mutableStateOf(spectrumPosition(color)) }
    val latestOnColor by rememberUpdatedState(onColor)
    Canvas(Modifier.fillMaxWidth().aspectRatio(1.2f).clip(RoundedCornerShape(24.dp))
        .semantics { contentDescription = "颜色光谱" }
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(); down.consume()
                fun update(p: Offset) {
                    point = Offset((p.x / size.width).coerceIn(0f, 1f), (p.y / size.height).coerceIn(0f, 1f))
                    latestOnColor(markupSpectrumColor(point.x, point.y))
                }
                update(down.position)
                do {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (change.isConsumed) break
                    update(change.position); change.consume()
                } while (change.pressed)
            }
        }) {
        drawRect(Brush.verticalGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)))
        drawRect(Brush.horizontalGradient(0f to Color.White, 0.5f to Color.Transparent, 1f to Color.Black))
        val center = Offset(point.x * size.width, point.y * size.height)
        drawCircle(color.copy(alpha = 1f), 12.dp.toPx(), center)
        drawCircle(Color.White, 12.dp.toPx(), center, style = Stroke(3.dp.toPx()))
    }
}

@Composable
private fun RgbColorControls(color: Color, onColor: (Color) -> Unit) {
    val focus = LocalFocusManager.current
    var hex by remember(color) { mutableStateOf(markupHex(color)) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        listOf("红色", "绿色", "蓝色").forEachIndexed { index, label ->
            val value = listOf(color.red, color.green, color.blue)[index]
            fun channel(v: Float) = when (index) { 0 -> color.copy(red = v); 1 -> color.copy(green = v); else -> color.copy(blue = v) }
            ColorChannel(label, value, (value * 255).roundToInt().toString(),
                listOf(channel(0f).copy(alpha = 1f), channel(1f).copy(alpha = 1f)),
                onValue = { onColor(channel(it)) })
        }
        OutlinedTextField(hex, { input ->
            hex = input.removePrefix("#").take(6).uppercase()
            markupColorFromHex(hex, color.alpha)?.let(onColor)
        }, label = { Text("sRGB Hex 颜色 #") }, singleLine = true,
            isError = markupColorFromHex(hex, color.alpha) == null,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "十六进制颜色" },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }), shape = CircleShape)
    }
}

@Composable
private fun ColorChannel(label: String, value: Float, valueLabel: String, gradient: List<Color>,
    checker: Boolean = false, onValue: (Float) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val latestOnValue by rememberUpdatedState(onValue)
            Canvas(Modifier.weight(1f).height(44.dp).clip(CircleShape)
                .semantics {
                    contentDescription = label
                    progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
                    setProgress { latestOnValue(it.coerceIn(0f, 1f)); true }
                }.pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(); down.consume()
                        val radius = size.height / 2f
                        fun update(p: Offset) { latestOnValue(((p.x - radius) / (size.width - 2 * radius).coerceAtLeast(1f)).coerceIn(0f, 1f)) }
                        update(down.position)
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.isConsumed) break
                            update(change.position); change.consume()
                        } while (change.pressed)
                    }
                }) {
                if (checker) checkerboard()
                drawRect(Brush.horizontalGradient(gradient))
                val radius = size.height / 2 - 3.dp.toPx()
                val center = Offset(size.height / 2 + value * (size.width - size.height), size.height / 2)
                drawCircle(Color.White, radius, center, style = Stroke(3.dp.toPx()))
            }
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                Text(valueLabel, Modifier.widthIn(min = 60.dp).padding(horizontal = 12.dp, vertical = 10.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

private fun DrawScope.checkerboard() {
    val cell = 10.dp.toPx()
    for (row in 0..(size.height / cell).toInt()) for (col in 0..(size.width / cell).toInt()) {
        drawRect(if ((row + col) % 2 == 0) Color(0xFFE6E4DE) else Color(0xFFF9F8F4),
            Offset(col * cell, row * cell), Size(cell, cell))
    }
}
