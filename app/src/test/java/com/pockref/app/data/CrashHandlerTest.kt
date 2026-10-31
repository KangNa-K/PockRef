package com.pockref.app.data

import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Pure-JVM unit tests for [CrashHandler]. We use a real
 * `TemporaryFolder`-style setup (Files.createTempDirectory) so
 * the file-IO code runs through the same code path as on-device.
 * Robolectric is NOT required because [CrashHandler] uses
 * `Context.filesDir` only as a path; for tests we drive it via
 * a `filesDir` parameter directly (the package-private
 * `writeCrashSync` / `readLastCrashFrom` / `clearLastCrashFrom`).
 *
 * The tests lock three behaviors:
 *  1. After a simulated uncaught exception, [readLastCrashFrom]
 *     returns a string containing the exception class name and
 *     at least one stack frame line.
 *  2. A second crash overwrites the first (only the latest crash
 *     is retained — there is no history).
 *  3. [clearLastCrashFrom] empties the file such that the next
 *     [readLastCrashFrom] returns null.
 * Plus three defensive cases (missing file, empty file, garbage
 * file) that lock the contract that read never throws.
 */
class CrashHandlerTest {

    private lateinit var crashDir: File

    @Before
    fun setUp() {
        crashDir = Files.createTempDirectory("pockref-crash-test").toFile()
    }

    @After
    fun tearDown() {
        crashDir.deleteRecursively()
    }

    /**
     * Drive [CrashHandler.writeCrashSync] directly (the same private
     * function the installed handler invokes). For test isolation we
     * do not call [CrashHandler.install] in these tests — we exercise
     * the persistence layer directly.
     */
    private fun writeCrash(t: Throwable, threadName: String = "main") {
        CrashHandler.writeCrashSync(crashDir, t, Thread(threadName))
    }

    @Test
    fun writeCrash_thenRead_returnsNonNullContainingClassNameAndFrame() {
        val ex = RuntimeException("simulated camera failure")
        writeCrash(ex)

        val text = CrashHandler.readLastCrashFrom(crashDir)
        assertNotNull("readLastCrashFrom should be non-null after a crash", text)
        assertTrue(
            "text should contain exception class name; was:\n$text",
            text!!.contains("RuntimeException"),
        )
        assertTrue(
            "text should contain at least one stack frame line; was:\n$text",
            text.contains("at "),
        )
        assertTrue(
            "text should contain the exception message; was:\n$text",
            text.contains("simulated camera failure"),
        )
    }

    @Test
    fun secondCrash_overwritesFirst() {
        writeCrash(RuntimeException("first"))
        writeCrash(IllegalStateException("second"))

        val text = CrashHandler.readLastCrashFrom(crashDir)!!
        assertTrue(
            "second crash's class name should be present; was:\n$text",
            text.contains("IllegalStateException"),
        )
        assertTrue(
            "second crash's message should be present; was:\n$text",
            text.contains("second"),
        )
        assertTrue(
            "first crash should NOT be present in file; was:\n$text",
            !text.contains("first"),
        )
    }

    @Test
    fun clearLastCrash_emptiesFileAndReadReturnsNull() {
        writeCrash(RuntimeException("boom"))
        assertNotNull(CrashHandler.readLastCrashFrom(crashDir))

        CrashHandler.clearLastCrashFrom(crashDir)

        assertNull(
            "After clearLastCrash, readLastCrashFrom should return null",
            CrashHandler.readLastCrashFrom(crashDir),
        )
    }

    @Test
    fun readLastCrash_missingFile_returnsNull() {
        assertNull(CrashHandler.readLastCrashFrom(crashDir))
    }

    @Test
    fun readLastCrash_emptyFile_returnsNull() {
        File(crashDir, CrashHandler.CRASH_FILE_NAME).writeText("")
        assertNull(CrashHandler.readLastCrashFrom(crashDir))
    }

    @Test
    fun readLastCrash_garbageFile_doesNotThrow() {
        // V_NOW: a corrupted file (e.g. truncated mid-write by an OS
        // crash) must NOT propagate an exception up the log-export
        // pipeline. We do NOT assert it returns null — partial
        // stacktrace content is still actionable for the user; only a
        // total read failure should null it out.
        File(crashDir, CrashHandler.CRASH_FILE_NAME).writeText("\u0000\u0001BINARY")
        // Must not throw:
        val text = CrashHandler.readLastCrashFrom(crashDir)
        // And the read should be observable (either null OR the partial
        // content), not silently lost.
        assertTrue(text == null || text.isNotEmpty())
    }
}
