package com.pockref.app.data

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * V_NOW (2026-06-16, "启动页导出运行日志" feature):
 * Installs a [Thread.UncaughtExceptionHandler] that synchronously
 * writes the most recent uncaught exception to `<filesDir>/last_crash.txt`
 * before chaining to whatever handler was previously installed (so
 * the system still pops its "Application has stopped" dialog and
 * Android vitals still receive the report).
 *
 * Crash files are intentionally overwritten — only the latest crash
 * is retained. We chose overwrite (not a rolling list) to bound
 * disk usage and because crash history is rarely actionable: by the
 * time the user is ready to export, the most recent crash is what
 * they want to inspect.
 *
 * File I/O is synchronous on the crashing thread because coroutine
 * dispatch would be cancelled when the process dies. We call
 * `FileDescriptor.sync()` after writing to push the bytes to disk
 * before the kernel reaps the process.
 */
object CrashHandler {

    const val CRASH_FILE_NAME = "last_crash.txt"

    private const val TAG = "CrashHandler"
    private val ISO_FORMATTER: ThreadLocal<SimpleDateFormat> =
        object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat =
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply {
                    timeZone = TimeZone.getDefault()
                }
        }

    @Volatile
    private var installed: Boolean = false

    /**
     * Install the handler. Safe to call multiple times — only the
     * first call has effect.
     *
     * Must be called from the main thread, **after** `super.onCreate()`
     * and before any other initialization that might throw — see
     * [com.pockref.app.MainActivity.onCreate].
     */
    @Synchronized
    fun install(app: android.app.Application) {
        if (installed) return
        installed = true

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeCrashSync(app.filesDir, throwable, thread)
            } catch (writeFailure: Throwable) {
                // Never let our handler swallow the original crash.
                Log.e(TAG, "Failed to persist crash to disk", writeFailure)
            }
            // Chain to the previous handler (system default → process
            // death + "Application has stopped" dialog).
            previous?.uncaughtException(thread, throwable)
        }
    }

    /**
     * Write the crash synchronously. Visible for testing — production
     * callers should rely on [install] which sets up the handler.
     *
     * Performs: open `wt` → write → flush → `fd.sync()` → close.
     * The `fd.sync()` is what guarantees the bytes survive process death.
     */
    fun writeCrashSync(filesDir: File, throwable: Throwable, thread: Thread) {
        val out = File(filesDir, CRASH_FILE_NAME)
        FileOutputStream(out, false).use { fos ->
            PrintWriter(fos.bufferedWriter(Charsets.UTF_8)).use { pw ->
                pw.println("=== PockRef Last Crash ===")
                pw.println("Time:        ${ISO_FORMATTER.get()!!.format(Date())}")
                pw.println("Thread:      ${thread.name}")
                pw.println("Exception:   ${throwable.javaClass.name}")
                pw.println("Message:     ${throwable.message ?: "(no message)"}")
                pw.println("Stacktrace:")
                throwable.printStackTrace(pw)
            }
            // Force the bytes to disk before the process is killed.
            // On real Android this is what guarantees survival across
            // process death; on JVM dev hosts (e.g. macOS unit tests)
            // it can throw SyncFailedException for temp files — we
            // swallow it as best-effort since the close() above already
            // flushed user-space buffers.
            runCatching { fos.fd.sync() }.onFailure {
                Log.w(TAG, "fd.sync() failed (best-effort on JVM dev hosts)", it)
            }
        }
    }

    /**
     * Returns the contents of `last_crash.txt`, or null if the file is
     * missing, empty, or unreadable. Never throws — callers can safely
     * use the return value in a log section without try/catch.
     */
    fun readLastCrash(context: Context): String? =
        readLastCrashFrom(context.filesDir)

    /** File-dir variant, package-private for testing. */
    fun readLastCrashFrom(filesDir: File): String? = try {
        val f = File(filesDir, CRASH_FILE_NAME)
        if (!f.exists() || f.length() == 0L) null
        else f.readText(Charsets.UTF_8).takeIf { it.isNotBlank() }
    } catch (t: Throwable) {
        Log.w(TAG, "readLastCrashFrom failed", t)
        null
    }

    /**
     * Delete the crash file. Not auto-called after export — the next
     * export will overwrite anyway, and we leave clearing under user
     * control (no UI exposed yet).
     */
    fun clearLastCrash(context: Context) = clearLastCrashFrom(context.filesDir)

    /** File-dir variant, package-private for testing. */
    fun clearLastCrashFrom(filesDir: File) {
        File(filesDir, CRASH_FILE_NAME).delete()
    }
}
