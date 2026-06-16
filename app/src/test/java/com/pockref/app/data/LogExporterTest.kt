package com.pockref.app.data

import com.pockref.app.ui.UiError
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM unit tests for [LogExporter]. We inject a fake
 * `LogSource` interface so the test does not depend on
 * `Runtime.exec("logcat ...")` (which would require a real
 * Android device).
 *
 * The tests lock the assembly contract:
 *  - All 6 section headers are present and ordered.
 *  - Empty `recentErrors` → "No recent errors" literal.
 *  - Null `lastCrash` → "No recent crash" literal.
 *  - LogSource that throws → section shows "dump failed" + still
 *    completes the rest of the assembly.
 *  - Header contains the app version + SDK int (developer-relevant
 *    breadcrumbs).
 *  - Recent errors appear newest-first.
 */
class LogExporterTest {

    /** A LogSource that returns fixed text — used for happy-path tests. */
    private val happyLog: LogExporter.LogSource = object : LogExporter.LogSource {
        override fun dump(pid: Int, maxLines: Int): String =
            "06-16 13:39:08.123 12345 12345 D AutoSave: tick\n"
    }

    /** A LogSource that throws — used to verify graceful degradation. */
    private val failingLog: LogExporter.LogSource = object : LogExporter.LogSource {
        override fun dump(pid: Int, maxLines: Int): String =
            throw java.io.IOException("logcat unavailable")
    }

    @Test
    fun build_allSectionsPresent_inOrder() = kotlinx.coroutines.runBlocking {
        val text = LogExporter.build(
            appLabel = "PockRef",
            versionName = "1.3.0",
            versionCode = 9,
            packageName = "com.pockref.app",
            sdkInt = 34,
            recentErrors = emptyList(),
            lastCrash = null,
            logSource = happyLog,
            currentPid = 12345,
            logcatLineCap = 500,
        )

        // Section ordering.
        val h = text.indexOf("=== PockRef Run Log ===")
        val d = text.indexOf("=== DEVICE INFO ===")
        val p = text.indexOf("=== PREFERENCES ===")
        val e = text.indexOf("=== UI ERRORS (last 50, newest first) ===")
        val c = text.indexOf("=== LAST CRASH ===")
        val l = text.indexOf("=== LOGCAT (PID 12345, last 500 lines, threadtime) ===")

        assertTrue("HEADER section missing", h >= 0)
        assertTrue("DEVICE section missing", d > h)
        assertTrue("PREFERENCES section missing", p > d)
        assertTrue("UI ERRORS section missing", e > p)
        assertTrue("LAST CRASH section missing", c > e)
        assertTrue("LOGCAT section missing", l > c)
    }

    @Test
    fun build_emptyRecentErrors_writesNoRecentErrorsLiteral() = kotlinx.coroutines.runBlocking {
        val text = LogExporter.build(
            appLabel = "PockRef",
            versionName = "1.3.0", versionCode = 9,
            packageName = "com.pockref.app", sdkInt = 34,
            recentErrors = emptyList(),
            lastCrash = null,
            logSource = happyLog, currentPid = 1, logcatLineCap = 500,
        )
        assertTrue(text.contains("No recent errors"))
    }

    @Test
    fun build_nullLastCrash_writesNoRecentCrashLiteral() = kotlinx.coroutines.runBlocking {
        val text = LogExporter.build(
            appLabel = "PockRef",
            versionName = "1.3.0", versionCode = 9,
            packageName = "com.pockref.app", sdkInt = 34,
            recentErrors = emptyList(),
            lastCrash = null,
            logSource = happyLog, currentPid = 1, logcatLineCap = 500,
        )
        assertTrue(text.contains("No recent crash"))
    }

    @Test
    fun build_logSourceThrows_sectionShowsDumpFailed_butRestCompletes() = kotlinx.coroutines.runBlocking {
        val text = LogExporter.build(
            appLabel = "PockRef",
            versionName = "1.3.0", versionCode = 9,
            packageName = "com.pockref.app", sdkInt = 34,
            recentErrors = listOf(
                UiError("decode timeout", RuntimeException("5s")),
            ),
            lastCrash = "fake crash",
            logSource = failingLog, currentPid = 1, logcatLineCap = 500,
        )
        assertTrue(text.contains("dump failed"))
        assertTrue(text.contains("decode timeout")) // errors still emitted
        assertTrue(text.contains("fake crash"))      // crash still emitted
    }

    @Test
    fun build_headerContainsVersionNameAndSdkInt() = kotlinx.coroutines.runBlocking {
        val text = LogExporter.build(
            appLabel = "PockRef",
            versionName = "1.3.0", versionCode = 9,
            packageName = "com.pockref.app", sdkInt = 34,
            recentErrors = emptyList(),
            lastCrash = null,
            logSource = happyLog, currentPid = 1, logcatLineCap = 500,
        )
        assertTrue(text.contains("1.3.0"))
        assertTrue(text.contains("SDK"))
        assertTrue(text.contains("34"))
    }

    @Test
    fun build_recentErrorsAppear_newestFirst() = kotlinx.coroutines.runBlocking {
        val text = LogExporter.build(
            appLabel = "PockRef",
            versionName = "1.3.0", versionCode = 9,
            packageName = "com.pockref.app", sdkInt = 34,
            recentErrors = listOf(
                UiError("first error", null),
                UiError("second error", null),
            ),
            lastCrash = null,
            logSource = happyLog, currentPid = 1, logcatLineCap = 500,
        )
        val firstIdx = text.indexOf("first error")
        val secondIdx = text.indexOf("second error")
        assertTrue("both errors should be present", firstIdx > 0 && secondIdx > 0)
        assertTrue(
            "second error should appear BEFORE first error (newest-first)",
            secondIdx < firstIdx,
        )
    }
}
