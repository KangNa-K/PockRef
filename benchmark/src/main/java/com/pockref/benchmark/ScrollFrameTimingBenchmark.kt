package com.pockref.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 画布滚动 / 拖拽帧率基准。
 *
 * 先加载场景,然后在画布上做一系列 fling + swipe 手势,
 * 用 [FrameTimingMetric] 采集 frameDurationCpuMs / frameOverrunMs,
 * 判断是否掉帧 (P90 / P95 / P99 应 < 16ms)。
 *
 * 运行:
 *   ./gradlew :benchmark:connectedBenchmarkAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=com.pockref.benchmark.ScrollFrameTimingBenchmark
 */
@RunWith(AndroidJUnit4::class)
class ScrollFrameTimingBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun flingCanvas() {
        rule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(androidx.benchmark.macro.FrameTimingMetric()),
            compilationMode = CompilationMode.None(),
            startupMode = StartupMode.WARM,
            iterations = 5,
        ) {
            pressHome()
            startActivityAndWait()
            device.wait(Until.hasObject(By.textContains("PockRef")), 5_000)

            val canvas = device.findObject(By.descContains("canvas"))
                ?: device.findObject(By.pkg(TARGET_PACKAGE))
            requireNotNull(canvas) { "无法定位画布元素" }

            // 8 次 fling,每次间 300ms,触发视口解码 + 缓存驱逐
            val dirs = listOf(Direction.LEFT, Direction.RIGHT, Direction.UP, Direction.DOWN)
            repeat(2) {
                dirs.forEach { d ->
                    canvas.fling(d)
                    Thread.sleep(300)
                }
            }
        }
    }

    @Test
    fun swipeDragImage() {
        rule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(androidx.benchmark.macro.FrameTimingMetric()),
            compilationMode = CompilationMode.None(),
            startupMode = StartupMode.WARM,
            iterations = 5,
        ) {
            pressHome()
            startActivityAndWait()
            device.wait(Until.hasObject(By.textContains("PockRef")), 5_000)

            // 用 device.swipe 模拟图片拖拽 (触发 SnapEngine 每帧热路径)
            val cx = device.displayWidth / 2
            val cy = device.displayHeight / 2
            // 8 个方向的短拖拽,每个 8 步 (步多 → 触发更多 frame)
            val dirs = listOf(
                intArrayOf(cx - 200, cy),
                intArrayOf(cx + 200, cy),
                intArrayOf(cx, cy - 200),
                intArrayOf(cx, cy + 200),
                intArrayOf(cx - 150, cy - 150),
                intArrayOf(cx + 150, cy + 150),
                intArrayOf(cx - 150, cy + 150),
                intArrayOf(cx + 150, cy - 150),
            )
            repeat(3) {
                dirs.forEach { end ->
                    device.swipe(cx, cy, end[0], end[1], 16)
                    Thread.sleep(100)
                }
            }
        }
    }

    companion object {
        private const val TARGET_PACKAGE = "com.pockref.app"
    }
}
