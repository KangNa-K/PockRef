package com.pockref.benchmark

import android.content.Intent
import android.net.Uri
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 大 .pur 文件加载性能基准。
 *
 * 把测试文件 push 到设备 /data/local/tmp 目录,用 VIEW Intent 启动 App 加载它,
 * 测量从 start 到画布出现 + 图片渲染完成的耗时与帧率。
 *
 * 运行前需准备:
 *   adb push /path/to/big.pur /data/local/tmp/benchmark_big.pur
 *   adb shell chmod 644 /data/local/tmp/benchmark_big.pur
 *
 * 运行:
 *   ./gradlew :benchmark:connectedBenchmarkAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=com.pockref.benchmark.LargeFileLoadBenchmark
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMetricApi::class)
class LargeFileLoadBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun loadLargePurFile() {
        val testFile = File("/data/local/tmp/$TEST_FILE_NAME")
        assertTrue(
            "测试文件不存在: ${testFile.absolutePath}。请先 adb push 大 .pur 文件到该路径",
            testFile.exists(),
        )

        rule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(
                androidx.benchmark.macro.StartupTimingMetric(),
                androidx.benchmark.macro.FrameTimingMetric(),
                MemoryUsageMetric(MemoryUsageMetric.Mode.Max),
            ),
            compilationMode = CompilationMode.None(),
            startupMode = StartupMode.COLD,
            iterations = 5,
        ) {
            pressHome()
            // 用 VIEW Intent 直接打开 .pur 文件,跳过手动 picker 流程
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.fromFile(testFile), "application/octet-stream")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivityAndWait(intent)
            check(device.wait(Until.gone(By.textContains("无最近文件")), 60_000)) {
                "场景未在 60s 内离开启动空状态，可能加载失败或 OOM"
            }
            // 再等 2s 让首屏缩略图解码完成
            Thread.sleep(2_000)
        }
    }

    companion object {
        const val TEST_FILE_NAME = "benchmark_big.pur"
        private const val TARGET_PACKAGE = "com.pockref.app"
    }
}
