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

    // Text has no background or decorative border, on either the canvas or export.
    fun drawText(canvas: Canvas, text: CanvasMarkupText) {
        if (text.text.isEmpty()) return
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = text.color.toArgb()
            alpha = (alpha * text.opacity.coerceIn(0f,1f)).toInt()
            textSize = text.fontSize
        }
        val width = (text.boxSize?.width ?: text.text.lines().maxOf { paint.measureText(it) })
            .coerceIn(1f, 100_000f).toInt().coerceAtLeast(1)
        val layout = StaticLayout.Builder.obtain(text.text,0,text.text.length,paint,width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).build()
        val save = canvas.save()
        try {
            canvas.translate(text.position.x,text.position.y)
            canvas.rotate(Math.toDegrees(text.rotation.toDouble()).toFloat())
            canvas.translate(-width/2f, -(text.boxSize?.height ?: layout.height.toFloat())/2f)
            layout.draw(canvas)
        } finally { canvas.restoreToCount(save) }
    }
}
