package com.pockref.app.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.graphics.toArgb
import com.pockref.app.model.CanvasMarkupStroke
import com.pockref.app.model.CanvasMarkupText
import com.pockref.app.model.Size2D

/** The caller supplies the world-to-target transform for screen or export. */
internal object SharedMarkupRenderer {
    fun draw(canvas: Canvas, strokes: List<CanvasMarkupStroke>, texts: List<CanvasMarkupText>) {
        (strokes.map { it.zOrder to { drawStroke(canvas,it) } } + texts.map { it.zOrder to { drawText(canvas,it) } })
            .sortedBy { it.first }.forEach { it.second() }
    }

    fun drawStroke(canvas: Canvas, stroke: CanvasMarkupStroke) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = stroke.color.toArgb()
            alpha = (alpha * stroke.opacity.coerceIn(0f,1f)).toInt()
            style = Paint.Style.STROKE
            strokeWidth = stroke.lineWidth
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        if (stroke.points.size == 1 && stroke.isRenderable) {
            paint.style = Paint.Style.FILL
            canvas.drawCircle(stroke.points.single().x, stroke.points.single().y, stroke.lineWidth / 2, paint)
            return
        }
        val path = Path()
        markupPolylines(stroke).forEach { points ->
            points.firstOrNull()?.let { path.moveTo(it.x,it.y) }
            points.drop(1).forEach { path.lineTo(it.x,it.y) }
        }
        canvas.drawPath(path,paint)
    }

    private data class TextLayout(
        val layout: StaticLayout,
        val width: Int,
        val height: Float,
        val padding: Float,
    )

    private fun textWidth(width: Float): Int = width.coerceIn(1f, 100_000f).toInt().coerceAtLeast(1)

    /** Use the same font measurement, wrapping and padding for drawing and group containment. */
    private fun createTextLayout(text: CanvasMarkupText): TextLayout {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = text.color.toArgb()
            alpha = (alpha * text.opacity.coerceIn(0f,1f)).toInt()
            textSize = text.fontSize
        }
        val padding = text.textPadding.coerceAtLeast(0f)
        val width = textWidth(text.boxSize?.width ?: (text.text.lines().maxOf { paint.measureText(it) } + padding * 2))
        val contentWidth = (width - padding * 2).toInt().coerceAtLeast(1)
        val layout = StaticLayout.Builder.obtain(text.text,0,text.text.length,paint,contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).build()
        return TextLayout(layout, width, text.boxSize?.height ?: (layout.height + padding * 2), padding)
    }

    fun textSize(text: CanvasMarkupText): Size2D {
        if (text.text.isEmpty()) return Size2D(0f, 0f)
        text.boxSize?.let { return Size2D(textWidth(it.width).toFloat(), it.height) }
        val measured = createTextLayout(text)
        return Size2D(measured.width.toFloat(), measured.height)
    }

    // Text has no background or decorative border, on either the canvas or export.
    fun drawText(canvas: Canvas, text: CanvasMarkupText) {
        if (text.text.isEmpty()) return
        val measured = createTextLayout(text)
        val save = canvas.save()
        try {
            canvas.translate(text.position.x,text.position.y)
            canvas.rotate(Math.toDegrees(text.rotation.toDouble()).toFloat())
            canvas.translate(-measured.width/2f, -measured.height/2f)
            canvas.translate(measured.padding, measured.padding)
            measured.layout.draw(canvas)
        } finally { canvas.restoreToCount(save) }
    }
}
