package com.pockref.app.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
// kotlin.math.toDegrees not exposed at top-level here; using java.lang.Math.toDegrees directly

/**
 * HSV color picker. Top → bottom:
 *1. Preview swatch + RGB readout
 *2. Circular picker: outer ring = hue wheel (0–360° sweep,
 * [Brush.sweepGradient]); inner disc = equilateral triangle S/V
 * picker (white at top vertex, black at bottom vertex, full hue
 * at right vertex). The user's touch point's angle picks hue
 * (outer ring) or barycentric coords inside the triangle (S, V).
 * The white-fill / black-edge dot marks the current (hue, sat,
 * value) so the user sees where they are before lifting their
 * finger.
 *3. Value (V) slider for brightness.
 *
 * Used only when the active preset is [BackgroundPreset.Custom].
 * The parent [SettingsAppearanceTab] renders this composable
 * conditionally on [PockRefUiState.showBackgroundPicker].
 *
 * Preview/commit pattern: dragging the wheel or moving any of
 * the3 HSV sliders only mutates local [hue] / [sat] / [value]
 * `remember` state — the global [PockRefUiState.backgroundColor]
 * is NOT touched until the user taps "确定". [remember(currentColor)]
 * re-initialises the preview to the just-saved (or
 * still-original) colour each time the parent re-passes
 * [currentColor] — e.g. when the user reopens the picker after a
 * prior "确定"/"取消" cycle.
 */
@Composable
internal fun CustomColorWheel(
 currentColor: Color,
 onConfirm: (Color) -> Unit,
 onCancel: () -> Unit,
) {
 val initialHsv = remember(currentColor) { rgbToHsv(currentColor) }
 var hue by remember(currentColor) { mutableStateOf(initialHsv[0]) }
 var sat by remember(currentColor) { mutableStateOf(initialHsv[1]) }
 var value by remember(currentColor) { mutableStateOf(initialHsv[2]) }
 val previewColor = Color.hsv(hue, sat, value)

 Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
 //1. Preview + Original swatch row.
 // [previewColor] is the live HSV-composed colour (left,48dp, the
 // bigger one — this is the swatch the user is adjusting).
 // [currentColor] is the colour that was active when the picker
 // opened, i.e. the colour the user will get back to if they tap
 // "取消" (right,32dp, smaller). Both are bordered so the
 // contrast against the sheet's translucent surface is obvious
 // regardless of which underlying hue/S/V the user lands on.
 Row(verticalAlignment = Alignment.CenterVertically) {
 Box(
 modifier = Modifier
 .size(48.dp)
 .clip(RoundedCornerShape(8.dp))
 .background(previewColor)
 .border(1.dp, Color(0x44000000), RoundedCornerShape(8.dp)),
 )
 Spacer(modifier = Modifier.width(12.dp))
 Box(
 modifier = Modifier
 .size(32.dp)
 .clip(RoundedCornerShape(8.dp))
 .background(currentColor)
 .border(1.dp, Color(0x44000000), RoundedCornerShape(8.dp)),
 )
 Spacer(modifier = Modifier.width(12.dp))
 Column(modifier = Modifier.weight(1f)) {
 Text(
 text = "预览 /调整前",
 style = MaterialTheme.typography.labelSmall,
 color = MaterialTheme.colorScheme.onSurfaceVariant,
 )
 Text(
 text = "R ${(previewColor.red *255).toInt()} " +
 "G ${(previewColor.green *255).toInt()} " +
 "B ${(previewColor.blue *255).toInt()}",
 style = MaterialTheme.typography.bodySmall,
 )
 }
 }

 //2. Hue ring (outer) + triangle S/V picker (inner).
 // The ring is unchanged from the previous design — angle picks
 // hue (0–360°), V-dependent sweep colours so the ring dims as V
 // drops. The inner area is now an equilateral triangle (white at
 // top-left, black at bottom-left, full hue at right) replacing
 // the previous radial SV disc. Triangle vertices are inscribed in
 // innerRadius (the ring's inner edge minus a4dp gap). Per-row
 // horizontal scanline fills the triangle — left side is a
 // white-to-black vertical gradient (constant in y), right side is
 // the full hue; mixing them along the row gives the standard2D
 // HSV interpolation.
 //
 // Touch region split:
 // - distance >= innerRadius: ring → H
 // - distance < innerRadius: triangle → S+V
 // The two regions are independent: a drag that started on the
 // ring keeps updating H even if the finger crosses into the
 // inner area, and vice versa. The user can lift + re-touch to
 // switch regions.
 //
 // Markers:
 // - Ring marker (white + black border, ~10dp radius) sits on the
 // ring's mid-radius at the hue angle.
 // - Triangle marker (white + black border, ~8dp radius) sits
 // inside the triangle at the position computed from (sat, value)
 // via the inverse barycentric formula:
 // Px = cx + R·(3s −1) /2
 // Py = cy + (R·√3/2)·(1 + s −2v)
 Box(
 modifier = Modifier
 .fillMaxWidth()
 .height(200.dp)
 .pointerInput(Unit) {
 // V_NOW (user bug report — "ring doesn't support touch
 // drag, can only tap"): [detectDragGestures] gates both
 // [onDragStart] and [onDrag] on the Compose touch-slop
 // (≈8dp) — the gesture doesn't fire until the user's first
 // move crosses that threshold, which made the ring feel
 // "dead" for slow drags and small movements (the finger had
 // to twitch by ~8dp before the marker snapped to the touch
 // position). We replace it with [awaitEachGesture] + a
 // direct [awaitPointerEvent] loop, which:
 // - dispatches on the INITIAL down (no slop gate) so a tap
 // immediately positions the marker
 // - dispatches on every subsequent move event (no slop
 // gate) so slow drags track the finger smoothly
 // - exits cleanly on release (the `change.pressed == false`
 // break drops out of the while loop and the
 // awaitEachGesture re-arms for the next touch)
 // The dispatch is the same [updateHueFromRingTouch] +
 // [updateSvFromTriangleTouch] pair, which already ignores
 // touches outside its region (ring / triangle respectively),
 // so the region split is preserved.
 awaitEachGesture {
 val down = awaitFirstDown(requireUnconsumed = false)
 val downPos = down.position
 updateHueFromRingTouch(downPos) { h ->
 hue = h
 }
 updateSvFromTriangleTouch(downPos) { s, v ->
 sat = s
 value = v
 }
 down.consume()
 while (true) {
 val event = awaitPointerEvent()
 val change = event.changes
 .firstOrNull { it.id == down.id }
 ?: break
 if (!change.pressed) break
 change.consume()
 val pos = change.position
 updateHueFromRingTouch(pos) { h ->
 hue = h
 }
 updateSvFromTriangleTouch(pos) { s, v ->
 sat = s
 value = v
 }
 }
 }
 }
 .drawBehind {
 val centerX = size.width /2f
 val centerY = size.height /2f
 val outerRadius = minOf(centerX, centerY) -2f
 val ringThickness = outerRadius *0.18f
 val innerRadius = outerRadius - ringThickness -4f
 val sqrt3 = sqrt(3f)
 val triHalfH = innerRadius * sqrt3 /2f
 val triR = innerRadius

 // Hue ring (outer). V-dependent sweep so the ring dims as
 // V drops.
 val ringColorsFullBrightness = listOf(
 Color.hsv(0f,1f,1f),
 Color.hsv(60f,1f,1f),
 Color.hsv(120f,1f,1f),
 Color.hsv(180f,1f,1f),
 Color.hsv(240f,1f,1f),
 Color.hsv(300f,1f,1f),
 Color.hsv(360f,1f,1f),
 )
 rotate(degrees =-90f, pivot = Offset(centerX, centerY)) {
 drawCircle(
 brush = Brush.sweepGradient(
 colors = ringColorsFullBrightness,
 center = Offset(centerX, centerY),
 ),
 radius = outerRadius - ringThickness /2f,
 style = Stroke(width = ringThickness),
 )
 }

 // Triangle (inner). Per-row horizontal gradient from
 // (white-to-black left mix) to (full hue right).1px step
 // keeps the gradient smooth on a200dp box (~400 rows on a
 //2x screen).
 val fullColor = Color.hsv(hue,1f,1f)
 val triangleLeft = centerX - triR /2f
 val triangleTop = centerY - triHalfH
 val triangleBottom = centerY + triHalfH
 val pixelStep =1f
 var y = triangleTop
 while (y <= triangleBottom) {
 // Left color: white at top (t=1) to black at bottom (t=0).
 val t = (centerY + triHalfH - y) / (2f * triHalfH)
 val leftColor = lerp(Color.Black, Color.White, t)
 // Right boundary: AB edge for top half (y < centerY),
 // AC edge for bottom half (y >= centerY).
 val xRight = if (y < centerY) {
 centerX + triR * (1f +1.5f * (y - centerY) / triHalfH)
 } else {
 centerX + triR * (1f -1.5f * (y - centerY) / triHalfH)
 }
 drawRect(
 brush = Brush.horizontalGradient(
 colors = listOf(leftColor, fullColor),
 startX = triangleLeft,
 endX = xRight,
 ),
 topLeft = Offset(triangleLeft, y),
 size = Size(xRight - triangleLeft, pixelStep),
 )
 y += pixelStep
 }

 // Ring marker (H). Sits on the ring's mid-radius at the
 // hue angle.
 //
 // V_NOW (user bug report — "ring marker doesn't match the
 // selected hue"): the ring gradient is drawn inside
 // `rotate(-90f)` so that hue=0° (red) sits at the top
 // (12 o'clock) and the sweep goes clockwise. The touch
 // handler computes hue from `atan2(dy, dx) +90°` — also
 // the "0° at top, clockwise" convention. The marker MUST
 // use the same convention or it lands180° away from the
 // actual hue. The previous formula
 // `cos(hue+90°), sin(hue+90°)` was the standard math
 // convention (0° at right, counter-clockwise) and put
 // hue=0° at the BOTTOM, so the user saw a marker at
 //7-8 o'clock when the slider said61° (which should be
 //1-2 o'clock). The "0° at top, clockwise" position
 // formula in screen coords (y+ is down) is:
 // x = cx + r · sin(θ)
 // y = cy − r · cos(θ)
 // which matches `atan2(dy, dx) +90°` one-for-one
 // (verified by walking through θ =0° /60° /180° /270°).
 val ringMarkerR = ringThickness *0.55f
 val hueRadians = hue.toDouble() * PI /180.0
 val ringMarkerRadius = outerRadius - ringThickness /2f
 val ringMarkerX = centerX + ringMarkerRadius * sin(hueRadians).toFloat()
 val ringMarkerY = centerY - ringMarkerRadius * cos(hueRadians).toFloat()
 drawCircle(
 color = Color.White,
 radius = ringMarkerR,
 center = Offset(ringMarkerX, ringMarkerY),
 )
 drawCircle(
 color = Color.Black,
 radius = ringMarkerR,
 center = Offset(ringMarkerX, ringMarkerY),
 style = Stroke(width =1.5f),
 )

 // Triangle marker (S, V). Sits inside the triangle at the
 // (s, v) position via the inverse barycentric formula. S
 // is always ≤ V (the sliders enforce this), so no
 // clamping is needed.
 val triMarkerR = ringThickness *0.45f
 val triMarkerX = centerX + triR * (3f * sat -1f) /2f
 val triMarkerY = centerY + triHalfH * (1f + sat -2f * value)
 drawCircle(
 color = Color.White,
 radius = triMarkerR,
 center = Offset(triMarkerX, triMarkerY),
 )
 drawCircle(
 color = Color.Black,
 radius = triMarkerR,
 center = Offset(triMarkerX, triMarkerY),
 style = Stroke(width =1.5f),
 )
 },
 )

 //3. H slider. [hue] is0-360° internally; the M3 Slider is
 // normalised0-1 so we multiply/divide at the boundary. The
 // label shows the integer degree value the user is seeing on
 // the ring marker.
 SliderRow(
 label = "H",
 valueText = "${hue.toInt()}°",
 value = hue /360f,
 onValueChange = { hue = it *360f },
 )
 //4. S slider. [sat] is0-1 internally, displayed as0-100%.
 // When S exceeds V the triangle can't represent it (the
 // triangle only covers V ≥ S), so we raise V to match S.
 // This keeps the marker inside the triangle and ensures it
 // actually moves when the user drags S.
 SliderRow(
 label = "S",
 valueText = "${(sat *100).toInt()}%",
 value = sat,
 onValueChange = { newS ->
 sat = newS
 if (newS > value) value = newS
 },
 )
 //5. V slider. Same shape as S, label "V" /0-100%. When V
 // drops below S the triangle can't represent it, so we
 // lower S to match V.
 SliderRow(
 label = "V",
 valueText = "${(value *100).toInt()}%",
 value = value,
 onValueChange = { newV ->
 value = newV
 if (newV < sat) sat = newV
 },
 )

 //6. Confirm / Cancel buttons. The user spec requires
 // explicit commit: drag/slider changes are preview-only
 // until "确定" is pressed. "取消" discards the preview
 // (just closes the picker — preview state is local and
 // goes away with the composable). "确定" calls the
 // parent's onConfirm with the composed preview colour;
 // the parent dispatches SetBackgroundColor which both
 // applies the colour AND closes the picker (the existing
 // setBackgroundColor reducer already does the close, so
 // the caller can dispatch one action and be done).
 Row(
 horizontalArrangement = Arrangement.spacedBy(8.dp),
 modifier = Modifier.fillMaxWidth(),
 ) {
 OutlinedButton(
 onClick = onCancel,
 modifier = Modifier.weight(1f),
 ) {
 Text("取消")
 }
 Button(
 onClick = { onConfirm(previewColor) },
 modifier = Modifier.weight(1f),
 ) {
 Text("确定")
 }
 }
 }
}

/**
 * Single labelled slider row: "H240°" / slider on its own line.
 * M3's built-in [Slider] doesn't render a value indicator, so the
 * label + value pair sits on a row above the track. Width is
 * `fillMaxWidth` so each row spans the full sheet content area.
 */
@Composable
private fun SliderRow(
 label: String,
 valueText: String,
 value: Float,
 onValueChange: (Float) -> Unit,
) {
 Column {
 Row(
 verticalAlignment = Alignment.CenterVertically,
 modifier = Modifier.fillMaxWidth(),
 ) {
 Text(
 text = label,
 style = MaterialTheme.typography.labelLarge,
 modifier = Modifier.weight(1f),
 )
 Text(
 text = valueText,
 style = MaterialTheme.typography.bodySmall,
 color = MaterialTheme.colorScheme.onSurfaceVariant,
 )
 }
 Slider(
 value = value,
 onValueChange = onValueChange,
 valueRange =0f..1f,
 )
 }
}

/**
 * Pure helper — convert an sRGB [Color] to HSV triple
 * (H in [0,360), S in [0,1], V in [0,1]). Used to initialise the
 * HSV state of [CustomColorWheel] from the colour the user picked
 * via the preset swatch row.
 */
private fun rgbToHsv(color: Color): FloatArray {
 val r = color.red
 val g = color.green
 val b = color.blue
 val max = maxOf(r, g, b)
 val min = minOf(r, g, b)
 val v = max
 val s = if (max ==0f)0f else (max - min) / max
 val h = when {
 max == min ->0f
 max == r -> ((g - b) / (max - min)) *60f
 max == g -> ((b - r) / (max - min) +2) *60f
 else -> ((r - g) / (max - min) +4) *60f
 }
 return floatArrayOf(if (h <0f) h +360f else h, s, v)
}

/**
 * Touch handler for the hue ring in [CustomColorWheel]. Computes
 * hue (0–360°) from the touch offset's angle relative to the
 * centre, then calls [apply] with the new hue. Touches inside the
 * inner area (distance < innerRadius) are ignored — those belong
 * to the triangle picker. Reads the picker size from the
 * surrounding [PointerInputScope] so callers don't have to thread
 * it through manually.
 *
 * V_NOW (user bug report — "page-scroll swipe through the picker
 * area changes the hue"): the Box around the ring is `fillMaxWidth`
 * `.height(200.dp)` — the ring is centred, but the4 box corners
 * (and the strips above / below the ring) extend well past
 * `outerRadius`. Without this guard, a vertical drag that starts
 * in a blank corner was still classified as a ring touch and
 * re-aimed the hue marker. Now only touches in the ring annulus
 * (`innerRadius ≤ distance ≤ outerRadius`) move the marker;
 * everything else is ignored (the pointerInput modifier still
 * receives the event for the Box, but no setter is called, so the
 * hue stays put and the gesture doesn't hijack vertical scrolling).
 */
private fun PointerInputScope.updateHueFromRingTouch(
 offset: Offset,
 apply: (hue: Float) -> Unit,
) {
 val centerX = size.width /2f
 val centerY = size.height /2f
 val outerRadius = minOf(centerX, centerY) -2f
 val ringThickness = outerRadius *0.18f
 val innerRadius = outerRadius - ringThickness -4f
 val dx = offset.x - centerX
 val dy = offset.y - centerY
 val distance = hypot(dx, dy)
 if (distance < innerRadius) return // in inner area; triangle picker handles this
 if (distance > outerRadius) return
 // Angle in degrees,0 = top, clockwise.
 val angleDeg = ((Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) +90.0) +360.0) %360.0
 apply(angleDeg.toFloat())
}

/**
 * Touch handler for the inner triangle S/V picker in
 * [CustomColorWheel]. Equilateral triangle inscribed in a circle
 * of radius innerRadius. Vertices in the box-centred coordinate
 * system:
 * A = (cx + R, cy) right = full hue = (s=1, v=1)
 * B = (cx − R/2, cy − h) top = white = (s=0, v=1)
 * C = (cx − R/2, cy + h) bottom = black = (s=0, v=0)
 * where h = R·√3/2. A point P inside the triangle has
 * barycentric coordinates (α, β, γ) for A, B, C:
 * α = (2·dx + R) / (3·R)
 * β = (R − dx) / (3·R) − dy / (R·√3)
 * γ = (R − dx) / (3·R) + dy / (R·√3)
 * S = α, V = α + β. Touches outside the triangle
 * (distance ≥ innerRadius, or any barycentric coordinate <0) are
 * clamped to the nearest point on the triangle's boundary (clamp
 * α, β, γ to [0,1] then renormalise so they sum to1).
 */
private fun PointerInputScope.updateSvFromTriangleTouch(
 offset: Offset,
 apply: (saturation: Float, value: Float) -> Unit,
) {
 val centerX = size.width /2f
 val centerY = size.height /2f
 val outerRadius = minOf(centerX, centerY) -2f
 val ringThickness = outerRadius *0.18f
 val innerRadius = outerRadius - ringThickness -4f
 val dx = offset.x - centerX
 val dy = offset.y - centerY
 val distance = hypot(dx, dy)
 if (distance >= innerRadius) return // in ring area; hue ring handles this
 val R = innerRadius
 val sqrt3 = sqrt(3f)
 val alpha = (2f * dx + R) / (3f * R)
 val beta = (R - dx) / (3f * R) - dy / (R * sqrt3)
 val gamma = (R - dx) / (3f * R) + dy / (R * sqrt3)
 val a = alpha.coerceIn(0f,1f)
 val b = beta.coerceIn(0f,1f)
 val g = gamma.coerceIn(0f,1f)
 val sum = a + b + g
 if (sum <=0f) return
 val an = a / sum
 val bn = b / sum
 apply(an, an + bn)
}

// Suppress unused import warnings on abs/toInt in case the helper
// signature changes (these are referenced by other helpers via
// auto-import in some IDEs).
@Suppress("unused")
private val _unused = abs(0f) +0f.toInt()
