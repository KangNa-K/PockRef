package com.pockref.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.hypot

/** Owns the full press, including a stationary hold before the finger moves. */
internal class FloatingWindowMoveHandle(context: Context) : View(context) {
    var onDragStart: () -> Unit = {}
    var onDrag: (Float, Float) -> Unit = { _, _ -> }
    var onDragEnd: () -> Unit = {}
    var onTap: () -> Unit = {}
    var handleColor: Int = 0
        set(value) {
            field = value
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var active = false

    init {
        contentDescription = "拖动移动悬浮窗，轻点显示窗口操作"
        isClickable = true
        // A long hold must keep the same gesture; it is not a long-click action.
        isLongClickable = false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        paint.color = handleColor
        paint.strokeWidth = 4f * density
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(width / 2f - 18f * density, height / 2f,
            width / 2f + 18f * density, height / 2f, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                dragging = false
                active = true
                parent?.requestDisallowInterceptTouchEvent(true)
                onDragStart()
            }
            MotionEvent.ACTION_MOVE -> if (active) {
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                if (hypot(dx.toDouble(), dy.toDouble()) > slop) dragging = true
                if (dragging) onDrag(dx, dy)
            }
            MotionEvent.ACTION_UP -> if (active) {
                active = false
                onDragEnd()
                parent?.requestDisallowInterceptTouchEvent(false)
                if (!dragging) performClick()
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> finishDrag()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        onTap()
        return true
    }

    private fun finishDrag() {
        if (!active) return
        active = false
        onDragEnd()
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    override fun onDetachedFromWindow() {
        finishDrag()
        super.onDetachedFromWindow()
    }
}
