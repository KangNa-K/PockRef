package com.pockref.app.perf

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.pockref.app.data.PurSceneCodec
import com.pockref.app.model.ColorAdjust
import com.pockref.app.model.PockRefScene
import com.pockref.app.model.RecentSceneEntry
import com.pockref.app.model.SceneImage
import com.pockref.app.model.Size2D
import com.pockref.app.model.encodeRecentScenes
import com.pockref.app.model.decodeRecentScenes
import com.pockref.app.ui.SnapEngine
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.cos
import kotlin.math.sin

/**
 * JVM 微基准测试套件 —— PockRef 性能测试
 *
 * 设计目标:
 *  - 覆盖拖拽热路径 (SnapEngine)、序列化 (PurSceneCodec)、列表重建 (images.map)、
 *    最近场景编解码 (RecentScenesCodec) 等纯 Kotlin 计算密集路径。
 *  - 不依赖 Android 真机 (BitmapFactory 等),所以可在 `testDebugUnitTest` 下运行。
 *  - 每个用例 warmup 后再计时,输出 mean / min / max / p95 / p99,既给阈值断言
 *    防严重回归,也把详细统计打到 stdout 供报告采集。
 *
 * 注意:JVM 微基准只测"纯算法"开销,真正的渲染/解码/IO 性能需要跑 Macrobenchmark
 * 模块 (见 `/benchmark`) 在真机上测。
 */
class PerformanceBenchmarkTest {

    /** 单次迭代计时的统计结果。 */
    private data class Stats(
        val name: String,
        val n: Int,
        val meanUs: Double,
        val minUs: Double,
        val maxUs: Double,
        val p95Us: Double,
        val p99Us: Double,
    ) {
        fun format(): String {
            fun Double.fmt() = "%.1f".format(this)
            return "[$name] n=$n mean=${meanUs.fmt()}μs min=${minUs.fmt()}μs max=${maxUs.fmt()}μs p95=${p95Us.fmt()}μs p99=${p99Us.fmt()}μs"
        }
    }

    /**
     * 跑 [block] 若干轮,前 [warmup] 轮丢弃,[measure] 轮计时,返回统计。
     * [block] 内部应是被测逻辑;返回值仅用于防止 JIT 死码消除。
     */
    private fun bench(
        name: String,
        warmup: Int = 5,
        measure: Int = 50,
        block: () -> Any?,
    ): Stats {
        // warmup
        var sink: Any? = null
        repeat(warmup) { sink = block() }
        val samples = DoubleArray(measure) {
            val t0 = System.nanoTime()
            sink = block()
            (System.nanoTime() - t0) / 1_000.0 // → μs
        }
        // 防 JIT 消除
        assertTrue("bench sink must not be null", sink != null || measure == 0)
        samples.sort()
        val mean = samples.average()
        val min = samples.first()
        val max = samples.last()
        val p95 = samples[(measure * 0.95).toInt().coerceIn(0, measure - 1)]
        val p99 = samples[(measure * 0.99).toInt().coerceIn(0, measure - 1)]
        return Stats(name, measure, mean, min, max, p95, p99).also {
            println("PERFBENCH ${it.format()}")
        }
    }

    // ────────────────────────────────────────────────────────────────────
    //  Test-data builders
    // ────────────────────────────────────────────────────────────────────

    private fun makeImage(
        id: String,
        cx: Float,
        cy: Float,
        w: Float = 120f,
        h: Float = 90f,
        rotation: Float = 0f,
        scale: Float = 1f,
        bytes: ByteArray = ByteArray(8) { (id.hashCode() and 0xFF).toByte() },
    ): SceneImage = SceneImage(
        id = id,
        name = id,
        mimeType = "image/png",
        imageBytes = bytes,
        intrinsicWidth = w.toInt(),
        intrinsicHeight = h.toInt(),
        center = Offset(cx, cy),
        displaySize = Size2D(w, h),
        rotationDegrees = rotation,
        scale = scale,
        zIndex = id.removePrefix("img-").toIntOrNull() ?: 0,
    )

    /** 生成 N 张铺在网格上的图片,加一点旋转使 snap AABB 真正含三角运算。 */
    private fun makeGrid(n: Int, step: Float = 140f, rot: Float = 12f): List<SceneImage> {
        val cols = kotlin.math.sqrt(n.toFloat()).toInt().coerceAtLeast(1)
        return List(n) { i ->
            val r = i / cols
            val c = i % cols
            makeImage("img-$i", cx = c * step, cy = r * step, rotation = if (i % 3 == 0) rot else 0f)
        }
    }

    /** 模拟 [com.pockref.app.ui.PureRefViewModelTransform.commitImageDrag] 的全量 map 重建。 */
    private fun rebuildImages(
        images: List<SceneImage>,
        movedIds: Set<String>,
        dx: Float,
        dy: Float,
    ): List<SceneImage> = images.map { img ->
        if (img.id in movedIds) {
            img.copy(
                center = Offset(img.center.x + dx, img.center.y + dy),
                scale = img.scale,
                rotationDegrees = img.rotationDegrees,
            )
        } else {
            img
        }
    }

    // ────────────────────────────────────────────────────────────────────
    //  SnapEngine — 拖拽热路径 (每帧调用)
    // ────────────────────────────────────────────────────────────────────

    @Test
    fun snap_measureClosestTargetDistance_50images_1drag() {
        val images = makeGrid(50)
        val dragIds = setOf("img-0")
        val s = bench("snap.measureClosest_50_1drag", measure = 80) {
            SnapEngine.measureClosestTargetDistance(
                deltaWorld = SnapEngine.SnapDelta(3f, 3f),
                dragIds = dragIds,
                allImages = images,
            )
        }
        // 阈值:单帧 proximity 探测应远小于 16ms (一帧预算)
        assertTrue("p95 ${s.p95Us}μs 超过 2000μs", s.p95Us < 2000.0)
    }

    @Test
    fun snap_measureClosestTargetDistance_200images_1drag() {
        val images = makeGrid(200)
        val dragIds = setOf("img-0")
        val s = bench("snap.measureClosest_200_1drag", measure = 60) {
            SnapEngine.measureClosestTargetDistance(
                deltaWorld = SnapEngine.SnapDelta(3f, 3f),
                dragIds = dragIds,
                allImages = images,
            )
        }
        assertTrue("p95 ${s.p95Us}μs 超过 8000μs", s.p95Us < 8000.0)
    }

    @Test
    fun snap_measureClosestTargetDistance_500images_1drag() {
        val images = makeGrid(500)
        val dragIds = setOf("img-0")
        val s = bench("snap.measureClosest_500_1drag", measure = 40) {
            SnapEngine.measureClosestTargetDistance(
                deltaWorld = SnapEngine.SnapDelta(3f, 3f),
                dragIds = dragIds,
                allImages = images,
            )
        }
        // 500 图压力场景:期望仍在单帧预算内,但允许稍宽
        assertTrue("p95 ${s.p95Us}μs 超过 20000μs", s.p95Us < 20000.0)
    }

    @Test
    fun snap_measureClosestTargetDistance_500images_10drag() {
        val images = makeGrid(500)
        val dragIds = (0 until 10).map { "img-$it" }.toSet()
        val s = bench("snap.measureClosest_500_10drag", measure = 40) {
            SnapEngine.measureClosestTargetDistance(
                deltaWorld = SnapEngine.SnapDelta(3f, 3f),
                dragIds = dragIds,
                allImages = images,
            )
        }
        // 多选拖动:dragIds=10,subImageMinDistance 内层 O(dragIds) → 总 O(N×dragIds×8)
        assertTrue("p95 ${s.p95Us}μs 超过 40000μs", s.p95Us < 40000.0)
    }

    @Test
    fun snap_compute_50images_engaged() {
        val images = makeGrid(50)
        val dragIds = setOf("img-0")
        // 把 drag 图挪到接近 img-1 的位置以触发 snap
        val moved = images.map {
            if (it.id == "img-0") it.copy(center = Offset(it.center.x + 138f, it.center.y))
            else it
        }
        val s = bench("snap.compute_50_engaged", measure = 80) {
            SnapEngine.compute(
                deltaWorld = SnapEngine.SnapDelta(2f, 0f),
                dragIds = dragIds,
                allImages = moved,
                thresholdWorld = 15f,
            )
        }
        assertTrue("p95 ${s.p95Us}μs 超过 3000μs", s.p95Us < 3000.0)
    }

    @Test
    fun snap_compute_200images_engaged() {
        val images = makeGrid(200)
        val dragIds = setOf("img-0")
        val moved = images.map {
            if (it.id == "img-0") it.copy(center = Offset(it.center.x + 138f, it.center.y))
            else it
        }
        val s = bench("snap.compute_200_engaged", measure = 50) {
            SnapEngine.compute(
                deltaWorld = SnapEngine.SnapDelta(2f, 0f),
                dragIds = dragIds,
                allImages = moved,
                thresholdWorld = 15f,
            )
        }
        assertTrue("p95 ${s.p95Us}μs 超过 12000μs", s.p95Us < 12000.0)
    }

    // ────────────────────────────────────────────────────────────────────
    //  images.map 重建 — 模拟 commitImageDrag / batchAlign 全量列表 copy
    // ────────────────────────────────────────────────────────────────────

    @Test
    fun imagesMap_rebuild_200_10moved() {
        val images = makeGrid(200)
        val moved = (0 until 10).map { "img-$it" }.toSet()
        val s = bench("imagesMap.rebuild_200_10moved", measure = 80) {
            rebuildImages(images, moved, dx = 1.5f, dy = 0.8f)
        }
        assertTrue("p95 ${s.p95Us}μs 超过 1500μs", s.p95Us < 1500.0)
    }

    @Test
    fun imagesMap_rebuild_500_10moved() {
        val images = makeGrid(500)
        val moved = (0 until 10).map { "img-$it" }.toSet()
        val s = bench("imagesMap.rebuild_500_10moved", measure = 60) {
            rebuildImages(images, moved, dx = 1.5f, dy = 0.8f)
        }
        assertTrue("p95 ${s.p95Us}μs 超过 3000μs", s.p95Us < 3000.0)
    }

    @Test
    fun imagesMap_rebuild_500_allMoved() {
        val images = makeGrid(500)
        val moved = images.map { it.id }.toSet()
        val s = bench("imagesMap.rebuild_500_allMoved", measure = 40) {
            rebuildImages(images, moved, dx = 1.5f, dy = 0.8f)
        }
        // 全量 copy 是最坏情况
        assertTrue("p95 ${s.p95Us}μs 超过 8000μs", s.p95Us < 8000.0)
    }

    // ────────────────────────────────────────────────────────────────────
    //  PurSceneCodec — V2 (PRFA) 格式 round-trip
    //  走自定义格式避开 BitmapFactory (JVM stub),专注序列化开销
    // ────────────────────────────────────────────────────────────────────

    private fun makeScene(n: Int): PockRefScene = PockRefScene(
        name = "bench-scene-$n",
        backgroundColor = Color(0xFF102030),
        images = makeGrid(n),
    )

    @Test
    fun purSceneCodec_v2_write_10images() {
        val scene = makeScene(10)
        val codec = PurSceneCodec()
        val s = bench("purCodec.v2_write_10", measure = 80) {
            val o = ByteArrayOutputStream()
            codec.write(scene, o)
            o.toByteArray()
        }
        assertTrue("p95 ${s.p95Us}μs 超过 1500μs", s.p95Us < 1500.0)
    }

    @Test
    fun purSceneCodec_v2_roundtrip_50images() {
        // 注意:V2 read 路径在 JVM 下受临时文件 IO + resolveImageBytes 影响,
        // 完整 round-trip 留给 instrumented 测试。这里只测 write 序列化开销
        // (write 是 AutoSave / Save As 的热路径)。
        val scene = makeScene(50)
        val codec = PurSceneCodec()
        val s = bench("purCodec.v2_write_50_roundtripMark", measure = 40) {
            val o = ByteArrayOutputStream()
            codec.write(scene, o)
            o.toByteArray()
        }
        assertTrue("p95 ${s.p95Us}μs 超过 25000μs", s.p95Us < 25000.0)
    }

    @Test
    fun purSceneCodec_v2_writeonly_50images() {
        val scene = makeScene(50)
        val codec = PurSceneCodec()
        val s = bench("purCodec.v2_write_50", measure = 60) {
            val o = ByteArrayOutputStream()
            codec.write(scene, o)
            o.toByteArray()
        }
        assertTrue("p95 ${s.p95Us}μs 超过 10000μs", s.p95Us < 10000.0)
    }

    // ────────────────────────────────────────────────────────────────────
    //  RecentScenesCodec — 启动页 recent 列表编解码
    // ────────────────────────────────────────────────────────────────────

    private fun makeRecentEntries(n: Int): List<RecentSceneEntry> = List(n) { i ->
        RecentSceneEntry(
            uri = "content://pockref/scene/$i",
            label = "Scene $i",
            source = "Saved",
            timestamp = 1_700_000_000_000L + i,
            filePath = "/sdcard/PockRef/scene$i.pur",
            fileSize = (i * 4096).toLong(),
            pinned = i == 0,
            coverImageId = if (i % 2 == 0) "cover-$i" else null,
        )
    }

    @Test
    fun recentScenesCodec_roundtrip_20entries() {
        val entries = makeRecentEntries(20)
        val s = bench("recentCodec.roundtrip_20", measure = 80) {
            val encoded = encodeRecentScenes(entries)
            decodeRecentScenes(encoded)
        }
        assertTrue("p95 ${s.p95Us}μs 超过 2000μs", s.p95Us < 2000.0)
    }

    @Test
    fun recentScenesCodec_encode_50entries() {
        val entries = makeRecentEntries(50)
        val s = bench("recentCodec.encode_50", measure = 80) {
            encodeRecentScenes(entries)
        }
        assertTrue("p95 ${s.p95Us}μs 超过 1500μs", s.p95Us < 1500.0)
    }

    // ────────────────────────────────────────────────────────────────────
    //  旋转 AABB 计算 — 模拟 PureRefApp.imageWorldAabb 的三角运算密集调用
    //  (fitToScreen 4×N、batchAlign、绘制循环每帧都会触发)
    // ────────────────────────────────────────────────────────────────────

    private fun rotatedAabb(cx: Float, cy: Float, w: Float, h: Float, rotDeg: Float, scale: Float): FloatArray {
        val rad = Math.toRadians(rotDeg.toDouble())
        val cosR = cos(rad).toFloat()
        val sinR = sin(rad).toFloat()
        val hw = w * scale / 2f
        val hh = h * scale / 2f
        val cxCorners = floatArrayOf(-hw, -hh, hw, -hh, hw, hh, -hw, hh)
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var i = 0
        while (i < cxCorners.size) {
            val lx = cxCorners[i]
            val ly = cxCorners[i + 1]
            val wx = cx + lx * cosR - ly * sinR
            val wy = cy + lx * sinR + ly * cosR
            if (wx < minX) minX = wx
            if (wy < minY) minY = wy
            if (wx > maxX) maxX = wx
            if (wy > maxY) maxY = wy
            i += 2
        }
        return floatArrayOf(minX, minY, maxX, maxY)
    }

    @Test
    fun rotatedAabb_compute_500images() {
        val images = makeGrid(500, rot = 23f)
        val s = bench("rotatedAabb.compute_500", measure = 60) {
            images.forEach { img ->
                rotatedAabb(
                    img.center.x, img.center.y,
                    img.displaySize.width, img.displaySize.height,
                    img.rotationDegrees, img.scale,
                )
            }
        }
        // fitToScreen 之类的路径每帧 4×N 次三角运算,500 图下应仍在毫秒级
        assertTrue("p95 ${s.p95Us}μs 超过 8000μs", s.p95Us < 8000.0)
    }

    @Test
    fun rotatedAabb_compute_500images_cached() {
        // 对照组:同样的 N,但旋转为 0 → 走快路径(实际代码没有 0 旋转快路径,
        // 这里只是把数据打出来对比,看缓存 0-旋转结果能省多少)
        val images = makeGrid(500, rot = 0f)
        val s = bench("rotatedAabb.compute_500_noRot", measure = 60) {
            images.forEach { img ->
                rotatedAabb(
                    img.center.x, img.center.y,
                    img.displaySize.width, img.displaySize.height,
                    img.rotationDegrees, img.scale,
                )
            }
        }
        assertTrue("p95 ${s.p95Us}μs 超过 8000μs", s.p95Us < 8000.0)
    }
}
