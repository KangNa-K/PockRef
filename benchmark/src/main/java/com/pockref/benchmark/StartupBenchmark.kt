package com.pockref.benchmark

import android.content.Intent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 启动性能基准 —— cold / warm / hot 三种启动模式。
 *
 * 运行方式:
 *   ./gradlew :benchmark:connectedBenchmarkAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=com.pockref.benchmark.StartupBenchmark
 *
 * 指标:启动时间 (ms)、initialDisplayTime、fullyDrawnTime。
 * 真机需解锁、电量充足、关闭省电模式。
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startupCold() = benchmark(StartupMode.COLD)

    @Test
    fun startupWarm() = benchmark(StartupMode.WARM)

    @Test
    fun startupHot() = benchmark(StartupMode.HOT)

    private fun benchmark(mode: StartupMode) {
        rule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(
                androidx.benchmark.macro.StartupTimingMetric(),
                androidx.benchmark.macro.FrameTimingMetric(),
            ),
            compilationMode = CompilationMode.None(),
            startupMode = mode,
            iterations = 10,
        ) {
            pressHome()
            startActivityAndWait()
            device.wait(Until.hasObject(By.textContains("PockRef")), 5_000)
        }
    }

    companion object {
        private const val TARGET_PACKAGE = "com.pockref.app"
    }
}
