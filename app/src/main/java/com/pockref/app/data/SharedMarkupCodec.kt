package com.pockref.app.data

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.pockref.app.model.CanvasMarkupStroke
import com.pockref.app.model.CanvasMarkupText
import com.pockref.app.model.Size2D
import org.json.JSONArray
import org.json.JSONObject

/** Apple Codable geometry uses arrays: CGPoint [x,y], CGRect [[x,y],[w,h]]. */
internal object SharedMarkupCodec {
    private val supported = setOf("pen", "highlighter", "line", "arrow", "rectangle", "ellipse", "text")
    data class Decoded(
        val strokes: List<CanvasMarkupStroke>,
        val texts: List<CanvasMarkupText>,
        val warning: String? = null,
    )

    fun decode(metadata: JSONObject): Decoded? {
        require(!metadata.has("annotationDocument") || metadata.isNull("annotationDocument") ||
            metadata.optJSONObject("annotationDocument") != null) { "标记文档不是有效对象" }
        val document = metadata.optJSONObject("annotationDocument") ?: return if (
            !metadata.isNull("paperKitMarkup") || !metadata.isNull("markupLayer")
        ) Decoded(emptyList(), emptyList(), "此文件包含未转换的 iOS 原生标记，请在 iOS 转换后编辑") else null
        if (document.optInt("schemaVersion", 1) != 1) return Decoded(emptyList(), emptyList(), "暂不支持此标记文档版本，请保留原文件")
        val items = document.getJSONArray("items")
        require(items.length() <= 100_000) { "标记对象数量超出安全限制" }
        val strokes = mutableListOf<CanvasMarkupStroke>()
        val texts = mutableListOf<CanvasMarkupText>()
        val ids = hashSetOf<String>()
        var pointCount = 0
        var unsupported = false
        for (index in 0 until items.length()) {
            val item = items.getJSONObject(index)
            val id = item.getString("id")
            require(id.isNotBlank() && id.length <= 1024) { "标记对象 ID 无效" }
            require(ids.add(id)) { "标记对象 ID 重复" }
            val kind = item.getString("kind")
            if (kind !in supported) { unsupported = true; continue }
            val array = item.getJSONArray("points")
            pointCount += array.length()
            require(pointCount <= 2_000_000) { "标记采样数量超出安全限制" }
            var points = (0 until array.length()).map { point(array.getJSONArray(it)) }
            if (kind in setOf("rectangle", "ellipse")) item.optJSONArray("bounds")?.let {
                val origin = point(it.getJSONArray(0)); val size = point(it.getJSONArray(1))
                require(size.x >= 0 && size.y >= 0) { "无效形状尺寸" }
                points = listOf(origin, origin + size)
            }
            val color = color(item.getJSONObject("color"))
            val opacity = finite(item, "opacity", 1.0).coerceIn(0f, 1f)
            val rotation = finite(item, "rotation", 0.0)
            val locked = item.optBoolean("locked", false)
            val order = item.optInt("zOrder", index)
            if (kind == "text") {
                val bounds = item.optJSONArray("bounds")
                val origin = bounds?.let { point(it.getJSONArray(0)) } ?: points.firstOrNull() ?: Offset.Zero
                val size = bounds?.let { point(it.getJSONArray(1)) }
                require(size == null || (size.x >= 0 && size.y >= 0)) { "无效文本尺寸" }
                val font = finite(item, "textFontSize", (size?.y?.div(2f) ?: 24f).toDouble())
                require(font in 0.01f..10000f) { "文本字号超出安全限制" }
                require(item.optString("text", "").length <= 1_000_000) { "文本长度超出安全限制" }
                texts += CanvasMarkupText(id, item.optString("text", ""),
                    origin + (size ?: Offset.Zero) / 2f, color, font, rotation, opacity, locked, order,
                    size?.let { Size2D(it.x.coerceAtLeast(0f), it.y.coerceAtLeast(0f)) }, item.toString(),
                    finite(item, "textPadding", 0.0).coerceAtLeast(0f))
            } else {
                val width = finite(item, "lineWidth", 4.0)
                require(width in 0.01f..10000f) { "标记线宽超出安全限制" }
                strokes += CanvasMarkupStroke(id, color, width,
                    points, kind, opacity, rotation, locked, order, item.toString())
            }
        }
        val opaquePencil = !metadata.isNull("markupLayer") &&
            !document.optBoolean("nativeArchivesAreStale", false) &&
            !document.optBoolean("includesPencilKitContent", false)
        val opaquePaper = !metadata.isNull("paperKitMarkup") &&
            !document.optBoolean("nativeArchivesAreStale", false) &&
            !document.optBoolean("includesPaperKitContent", false)
        return Decoded(strokes, texts, when {
            opaquePaper || opaquePencil -> "此文件包含尚未转换的 iOS 原生标记，请先在支持的 iOS 版本导出可编辑标记"
            unsupported -> "此文件含尚不支持的标记对象，已保留原始数据，暂不可编辑"
            else -> null
        })
    }

    fun document(strokes: List<CanvasMarkupStroke>, texts: List<CanvasMarkupText>, original: JSONObject? = null): JSONObject {
        val result = original?.let { JSONObject(it.toString()) } ?: JSONObject()
        result.put("schemaVersion", 1)
        result.put("nativeArchivesAreStale", true)
        val items = (strokes.map { stroke ->
            val item = stroke.sourceJson?.let(::JSONObject) ?: JSONObject()
            common(item, stroke.id, stroke.kind, stroke.color, stroke.opacity, stroke.rotation, stroke.locked, stroke.zOrder)
            item.put("points", points(stroke.points)).put("lineWidth", stroke.lineWidth)
            if (stroke.kind == "rectangle" || stroke.kind == "ellipse") {
                val a = stroke.points.firstOrNull() ?: Offset.Zero
                val b = stroke.points.lastOrNull() ?: a
                item.put("bounds", JSONArray().put(point(Offset(minOf(a.x,b.x), minOf(a.y,b.y))))
                    .put(point(Offset(kotlin.math.abs(b.x-a.x), kotlin.math.abs(b.y-a.y)))))
            }
            item
        } + texts.map { text ->
            val item = text.sourceJson?.let(::JSONObject) ?: JSONObject()
            common(item, text.id, "text", text.color, text.opacity, text.rotation, text.locked, text.zOrder)
            val size = text.boxSize ?: Size2D((text.text.lines().maxOfOrNull { it.length } ?: 1) * text.fontSize,
                text.text.lines().size * text.fontSize * 1.2f)
            val origin = text.position - Offset(size.width, size.height) / 2f
            item.put("points", points(listOf(origin, origin + Offset(size.width, size.height))))
                .put("bounds", JSONArray().put(point(origin)).put(point(Offset(size.width, size.height))))
                .put("text", text.text).put("textFontSize", text.fontSize).put("lineWidth", 0)
                .put("textPadding", text.textPadding)
            item
        }).sortedBy { it.getInt("zOrder") }
        result.put("items", JSONArray(items))
        return result
    }

    fun metadata(strokes: List<CanvasMarkupStroke>, texts: List<CanvasMarkupText>, source: String?): JSONObject {
        val root = source?.let(::JSONObject) ?: JSONObject()
        // Unsupported archives must remain byte-for-byte semantically intact until migrated.
        if (source != null && decode(root)?.warning != null) return root
        val decoded = decode(root)
        if (decoded != null && decoded.strokes == strokes && decoded.texts == texts) return root
        root.put("annotationDocument", document(strokes, texts, root.optJSONObject("annotationDocument")))
        return root
    }

    private fun common(item: JSONObject, id: String, kind: String, color: Color, opacity: Float, rotation: Float, locked: Boolean, order: Int) {
        val argb = color.toArgb()
        item.put("id", id).put("kind", kind).put("opacity", opacity).put("rotation", rotation)
            .put("locked", locked).put("zOrder", order).put("color", JSONObject()
                .put("r", argb ushr 16 and 255).put("g", argb ushr 8 and 255).put("b", argb and 255).put("a", argb ushr 24))
    }
    private fun color(value: JSONObject): Color = Color(
        value.getInt("r").coerceIn(0,255), value.getInt("g").coerceIn(0,255),
        value.getInt("b").coerceIn(0,255), value.optInt("a",255).coerceIn(0,255))
    private fun finite(item: JSONObject, key: String, fallback: Double): Float = item.optDouble(key, fallback).toFloat().also {
        require(it.isFinite()) { "无效标记数值: $key" }
    }
    private fun point(value: JSONArray): Offset = Offset(value.getDouble(0).toFloat(), value.getDouble(1).toFloat()).also {
        require(it.x.isFinite() && it.y.isFinite() && kotlin.math.abs(it.x) <= 10_000_000f && kotlin.math.abs(it.y) <= 10_000_000f) { "无效标记坐标" }
    }
    private fun point(value: Offset) = JSONArray().put(value.x).put(value.y)
    private fun points(values: List<Offset>) = JSONArray(values.map(::point))
}
