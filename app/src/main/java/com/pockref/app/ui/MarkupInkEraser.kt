package com.pockref.app.ui

import androidx.compose.ui.geometry.Offset
import com.pockref.app.model.CanvasMarkupStroke
import java.util.UUID
import kotlin.math.abs
import kotlin.math.sqrt

/** Clip the centerline against a swept capsule; resulting fragments remain shared editable ink. */
internal fun eraseMarkupInk(stroke: CanvasMarkupStroke, from: Offset, to: Offset, radius: Float): List<CanvasMarkupStroke> {
    if (stroke.locked || stroke.kind !in setOf("pen", "highlighter") ||
        !hitMarkupStrokeAlongSegment(stroke, from, to, radius)) return listOf(stroke)
    val points = markupPolylines(stroke).firstOrNull() ?: return listOf(stroke)
    val r = (radius + stroke.lineWidth/2).toDouble()
    if (stroke.points.size == 1) return emptyList()
    val fragments = mutableListOf<MutableList<Offset>>()
    var active: MutableList<Offset>? = null
    fun dot(a: Offset, b: Offset) = a.x.toDouble()*b.x + a.y.toDouble()*b.y
    for ((a,b) in points.zipWithNext()) {
        val direction = b-a
        if (direction.getDistance() < 0.00001f) continue
        val cuts = mutableListOf(0.0, 1.0)
        fun add(t: Double) { if (t.isFinite() && t > 0 && t < 1) cuts += t }
        for (center in listOf(from,to)) {
            val offset = a-center
            val qa = dot(direction,direction)
            val qb = 2*dot(offset,direction)
            val qc = dot(offset,offset)-r*r
            val discriminant = qb*qb-4*qa*qc
            if (discriminant >= 0) {
                add((-qb-sqrt(discriminant))/(2*qa)); add((-qb+sqrt(discriminant))/(2*qa))
            }
        }
        val sweep = to-from
        val lengthSquared = dot(sweep,sweep)
        if (lengthSquared > 0.000001) {
            val offset = a-from
            val projectedDelta = dot(direction,sweep)
            if (abs(projectedDelta) > 0.000001) {
                add(-dot(offset,sweep)/projectedDelta)
                add((lengthSquared-dot(offset,sweep))/projectedDelta)
            }
            val crossStart = offset.x.toDouble()*sweep.y-offset.y.toDouble()*sweep.x
            val crossDelta = direction.x.toDouble()*sweep.y-direction.y.toDouble()*sweep.x
            if (abs(crossDelta) > 0.000001) {
                val threshold = r*sqrt(lengthSquared)
                add((threshold-crossStart)/crossDelta); add((-threshold-crossStart)/crossDelta)
            }
        }
        val sorted = cuts.sorted().distinct()
        for ((start,end) in sorted.zipWithNext()) {
            if (end-start < 0.0000001) continue
            val middle = a+direction*((start+end)/2).toFloat()
            if (markupSegmentDistance(middle,middle,from,to) <= r) {
                active = null
            } else {
                val first = a+direction*start.toFloat()
                val last = a+direction*end.toFloat()
                val path = active?.takeIf { (it.last()-first).getDistance() < 0.001f } ?:
                    mutableListOf(first).also { fragments += it; active = it }
                path += last
            }
        }
    }
    return fragments.filter { it.size >= 2 }.mapIndexed { index, fragment ->
        stroke.copy(id = if (index == 0) stroke.id else UUID.randomUUID().toString(), points = fragment, rotation = 0f)
    }
}
