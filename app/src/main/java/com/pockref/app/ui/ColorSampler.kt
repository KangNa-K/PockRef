package com.pockref.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.pockref.app.model.ColorSample
import com.pockref.app.model.SceneImage
import com.pockref.app.ui.theme.Dimens

internal data class ColorSamplerLens(
    val image: SceneImage,
    val point: Offset,
    val geometry: ColorSamplerMath.PreviewGeometry,
    val showFullImage: Boolean,
)

internal fun colorSamplerLensCenter(point: Offset, viewport: Size, diameter: Float, gap: Float, margin: Float): Offset {
    val left = margin
    val top = margin
    val right = (viewport.width - diameter - margin).coerceAtLeast(left)
    val bottom = (viewport.height - diameter - margin).coerceAtLeast(top)
    val x = (point.x - diameter / 2f).coerceIn(left, right)
    val above = point.y - diameter - gap
    val y = (if (above >= top) above else point.y + gap).coerceIn(top, bottom)
    return Offset(x + diameter / 2f, y + diameter / 2f)
}

@Composable
internal fun BoxScope.ColorSamplerOverlay(
    sample: ColorSample?,
    lens: ColorSamplerLens?,
    bitmap: ImageBitmap?,
    showPanel: Boolean = true,
    panelBounds: Rect? = null,
    onClose: () -> Unit,
) {
    Canvas(Modifier.fillMaxSize().testTag("color-sampler-marker")) {
        if (sample != null && lens == null) {
            val point = sample.screenPosition
            drawCircle(Color.Black.copy(alpha = 0.72f), radius = 17.dp.toPx(), center = point)
            drawCircle(Color.White, radius = 13.dp.toPx(), center = point, style = Stroke(2.dp.toPx()))
            drawCircle(Color(sample.argb), radius = 7.dp.toPx(), center = point)
            drawCircle(Color.Black, radius = 7.dp.toPx(), center = point, style = Stroke(1.dp.toPx()))
        }
        if (lens != null && bitmap != null) {
            val diameter = 128.dp.toPx().coerceAtMost(size.width).coerceAtMost(size.height)
            val center = colorSamplerLensCenter(lens.point, size, diameter, 24.dp.toPx(), 8.dp.toPx())
            val outer = diameter / 2f - 1.dp.toPx()
            val inner = (outer - 12.dp.toPx()).coerceAtLeast(0f)
            val ringColor = sample?.takeIf { it.imageId == lens.image.id }?.let { Color(it.argb).copy(alpha = 1f) }
                ?: Color.DarkGray
            drawCircle(ringColor, outer, center)
            val clip = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, inner)) }
            clipPath(clip) {
                drawCircle(Color.DarkGray, inner, center)
                translate(center.x, center.y) {
                    scale(2.5f, 2.5f, pivot = Offset.Zero) {
                        translate(-lens.point.x, -lens.point.y) {
                            val g = lens.geometry
                            translate(g.center.x - g.width / 2f, g.center.y - g.height / 2f) {
                                rotate(lens.image.rotationDegrees, pivot = Offset(g.width / 2f, g.height / 2f)) {
                                    scale(
                                        if (lens.image.flipX) -1f else 1f,
                                        if (lens.image.flipY) -1f else 1f,
                                        pivot = Offset(g.width / 2f, g.height / 2f),
                                    ) {
                                        val crop = lens.image.cropRect.takeUnless { lens.showFullImage }
                                        val left = ((crop?.left ?: 0f) * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
                                        val top = ((crop?.top ?: 0f) * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
                                        val right = ((crop?.right ?: 1f) * bitmap.width).toInt().coerceIn(left + 1, bitmap.width)
                                        val bottom = ((crop?.bottom ?: 1f) * bitmap.height).toInt().coerceIn(top + 1, bitmap.height)
                                        drawImage(
                                            bitmap,
                                            srcOffset = IntOffset(left, top),
                                            srcSize = IntSize(right - left, bottom - top),
                                            dstSize = IntSize(g.width.toInt().coerceAtLeast(1), g.height.toInt().coerceAtLeast(1)),
                                            colorFilter = buildColorFilter(lens.image.colorAdjust),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            drawCircle(Color.White, inner, center, style = Stroke(1.dp.toPx()))
            val arm = 7.dp.toPx()
            listOf(3.dp.toPx() to Color.Black.copy(alpha = 0.6f), 1.dp.toPx() to Color.White).forEach { (width, color) ->
                drawLine(color, center - Offset(arm, 0f), center + Offset(arm, 0f), width)
                drawLine(color, center - Offset(0f, arm), center + Offset(0f, arm), width)
            }
        }
    }

    if (showPanel && sample != null) {
        val density = LocalDensity.current
        val panelModifier = if (panelBounds != null) {
            Modifier
                .offset { IntOffset(panelBounds.left.toInt(), panelBounds.top.toInt()) }
                .width(with(density) { panelBounds.width.toDp() })
        } else {
            Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    start = Dimens.EdgeInset,
                    end = Dimens.EdgeInset,
                    bottom = Dimens.CornerInset,
                )
        }
        ColorSamplerPanel(
            sample = sample,
            modifier = panelModifier,
            onClose = onClose,
        )
    }
}

/** Compact capsule used beside the selection chrome. */
@Composable
internal fun ColorSamplerPanel(
    sample: ColorSample,
    modifier: Modifier = Modifier,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    GlassSurface(
        modifier = modifier
            .widthIn(max = 360.dp)
            .height(Dimens.BottomPanelChromeSize)
            .testTag("color-sampler-panel"),
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = Dimens.ChromeSurfaceAlpha),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(
            Dimens.ChromeBorderWidth,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .background(Color(sample.argb), CircleShape)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                        shape = CircleShape,
                    )
                    .testTag("color-sampler-swatch"),
            )
            Spacer(Modifier.width(8.dp))
            Column(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                Text(
                    text = sample.rgbHex,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                )
                Text(
                    text = "H ${sample.hue.toInt()}°  S ${(sample.saturation * 100f).toInt()}%  " +
                        "V ${(sample.value * 100f).toInt()}%",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(2.dp))
            IconButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("色号", sample.rgbHex))
                    onClose()
                },
                modifier = Modifier
                    .size(Dimens.MinTouchTarget)
                    .testTag("color-sampler-copy"),
            ) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = "复制色号")
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(Dimens.MinTouchTarget)
                    .testTag("color-sampler-close"),
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "关闭取色")
            }
        }
    }
}
