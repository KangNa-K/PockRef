package com.pockref.app.data

import android.os.Build
import com.pockref.app.ui.UiError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * V_NOW (2026-06-16, "启动页导出运行日志" feature):
 * Pure text-builder for the run-log export. Assembles 6 sections
 * into a single UTF-8 string. No IO happens except the [LogSource]
 * call (logcat dump), which is isolated in try/catch and degrades
 * gracefully on failure.
 *
 * The function is intentionally synchronous from the caller's
 * perspective (suspend, but no callbacks) and side-effect free
 * except for reading the injected [LogSource]. This makes it
 * straightforward to unit-test on the JVM with a fake LogSource.
 *
 * The production caller invokes [LogExporter.build] from
 * `viewModelScope.launch(Dispatchers.IO)`.
 *
 * Sections (in order):
 *   1. HEADER     — timestamp, app version, package, SDK int
 *   2. DEVICE     — android.os.Build.* + Locale
 *   3. PREFERENCES — provided via parameters from buildForContext
 *   4. UI ERRORS  — recentErrors (newest-first)
 *   5. LAST CRASH — contents of last_crash.txt, or "No recent crash"
 *   6. LOGCAT     — current PID, -t 500, threadtime format
 */
object LogExporter {

    private val HEADER_TS_FMT: ThreadLocal<SimpleDateFormat> =
        object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat =
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply {
                    timeZone = TimeZone.getDefault()
                }
        }

    /**
     * The core builder. Pure: no IO except the [logSource] call which
     * is wrapped in try/catch and degrades to a placeholder on failure.
     *
     * @param appLabel       Display name (typically from
     *                       `context.applicationInfo.loadLabel(...)`).
     * @param versionName    `BuildConfig.VERSION_NAME`.
     * @param versionCode    `BuildConfig.VERSION_CODE`.
     * @param packageName    `context.packageName`.
     * @param sdkInt         `Build.VERSION.SDK_INT`.
     * @param recentErrors   Snapshot from the VM's ring buffer.
     * @param lastCrash      Result of `CrashHandler.readLastCrash(context)`;
     *                       null if no recent crash.
     * @param logSource      Pluggable logcat source — production wraps
     *                       `Runtime.exec("logcat ...")`, tests inject
     *                       a fake.
     * @param currentPid     `android.os.Process.myPid()`.
     * @param logcatLineCap  Max lines to pull from logcat (-t flag value).
     */
    suspend fun build(
        appLabel: String,
        versionName: String,
        versionCode: Int,
        packageName: String,
        sdkInt: Int,
        recentErrors: List<UiError>,
        lastCrash: String?,
        logSource: LogSource,
        currentPid: Int,
        logcatLineCap: Int,
    ): String = withContext(Dispatchers.Default) {
        buildString {
            append(header(appLabel, versionName, versionCode, packageName, sdkInt))
            append(device(sdkInt))
            append(preferences())
            append(errors(recentErrors))
            append(crash(lastCrash))
            append(logcat(logSource, currentPid, logcatLineCap))
        }
    }

    // ---- sections ------------------------------------------------------

    private fun header(
        appLabel: String, versionName: String, versionCode: Int,
        packageName: String, sdkInt: Int,
    ): String = buildString {
        appendLine("=== PockRef Run Log ===")
        appendLine("Exported at:    ${HEADER_TS_FMT.get()!!.format(Date())}")
        appendLine("App label:      $appLabel")
        appendLine("App version:    $versionName (versionCode $versionCode)")
        appendLine("App package:    $packageName")
        appendLine("SDK int:        $sdkInt")
        appendLine()
    }

    private fun device(sdkInt: Int): String = buildString {
        appendLine("=== DEVICE INFO ===")
        appendLine("Manufacturer:   ${Build.MANUFACTURER}")
        appendLine("Model:          ${Build.MODEL}")
        appendLine("Brand:          ${Build.BRAND}")
        appendLine("Product:        ${Build.PRODUCT}")
        appendLine("Hardware:       ${Build.HARDWARE}")
        appendLine("Android rel:    ${Build.VERSION.RELEASE}")
        appendLine("SDK int:        $sdkInt")
        appendLine("Security patch: ${Build.VERSION.SECURITY_PATCH}")
        appendLine("Locale:         ${java.util.Locale.getDefault()}")
        appendLine()
    }

    /**
     * Stub PREFERENCES section used in the core [build] call. The
     * production wrapper [buildForContext] replaces this section with
     * a real preference snapshot via a placeholder substitution so
     * the test doesn't need to construct an AppPreferences.
     */
    private fun preferences(): String = buildString {
        appendLine("=== PREFERENCES ===")
        appendLine("(preferences are populated by LogExporter.buildForContext)")
        appendLine()
    }

    private fun errors(recentErrors: List<UiError>): String = buildString {
        appendLine("=== UI ERRORS (last 50, newest first) ===")
        if (recentErrors.isEmpty()) {
            appendLine("No recent errors")
        } else {
            // Caller passes oldest-first (FIFO ring buffer order); we
            // reverse so the most recent error appears at the top of
            // the section, matching the section header's "newest first".
            recentErrors.asReversed().forEach { e ->
                appendLine("[${HEADER_TS_FMT.get()!!.format(Date())}] ${e.message}")
                e.throwable?.let { t ->
                    appendLine("  ${t.javaClass.name}: ${t.message ?: "(no message)"}")
                    t.stackTrace.take(5).forEach { f ->
                        appendLine("    at $f")
                    }
                }
            }
        }
        appendLine()
    }

    private fun crash(lastCrash: String?): String = buildString {
        appendLine("=== LAST CRASH ===")
        if (lastCrash == null) {
            appendLine("No recent crash")
        } else {
            append(lastCrash)
            if (!lastCrash.endsWith("\n")) appendLine()
        }
        appendLine()
    }

    private fun logcat(source: LogSource, pid: Int, cap: Int): String = buildString {
        appendLine("=== LOGCAT (PID $pid, last $cap lines, threadtime) ===")
        val dumped = try {
            source.dump(pid, cap)
        } catch (t: Throwable) {
            "(logcat dump failed: ${t.javaClass.simpleName}: ${t.message})"
        }
        append(dumped)
        if (!dumped.endsWith("\n")) appendLine()
    }

    // ---- production wrapper --------------------------------------------

    /**
     * Production-side wrapper that pulls live context values (BuildConfig,
     * Build.*, etc.) and produces a real preferences snapshot. Delegates
     * to [build] for the rest of the assembly.
     *
     * @param prefsLines  Pre-formatted preference lines, e.g.
     *                    `"Theme mode:           SYSTEM"`. Caller is
     *                    responsible for formatting — this keeps
     *                    LogExporter free of an AppPreferences
     *                    dependency.
     */
    suspend fun buildForContext(
        context: android.content.Context,
        appLabel: String,
        recentErrors: List<UiError>,
        lastCrash: String?,
        prefsLines: List<String>,
    ): String {
        val prefsSection = buildString {
            appendLine("=== PREFERENCES ===")
            prefsLines.forEach { appendLine(it) }
            appendLine()
        }
        val baseText = build(
            appLabel = appLabel,
            versionName = readVersionName(context),
            versionCode = readVersionCode(context),
            packageName = context.packageName,
            sdkInt = android.os.Build.VERSION.SDK_INT,
            recentErrors = recentErrors,
            lastCrash = lastCrash,
            logSource = RealLogSource,
            currentPid = android.os.Process.myPid(),
            logcatLineCap = 500,
        )
        // Splice prefsSection into the base output. Base has a placeholder
        // block we need to replace.
        return baseText.replaceFirst(
            Regex(
                "=== PREFERENCES ===\\n\\(preferences are populated by LogExporter\\.buildForContext\\)\\n\\n",
            ),
            prefsSection,
        )
    }

    // ---- log source ---------------------------------------------------

    /**
     * Pluggable interface so the JVM unit tests can substitute a
     * deterministic source for the production `Runtime.exec("logcat ...")`.
     */
    interface LogSource {
        fun dump(pid: Int, maxLines: Int): String
    }

    /**
     * The production logcat source. Uses `Runtime.getRuntime().exec(...)`
     * because Android does not expose a public `Logcat` API. Reads
     * `-d` (dump) with `-v threadtime`, `--pid <pid>`, `-t <cap>`.
     */
    private object RealLogSource : LogSource {
        override fun dump(pid: Int, maxLines: Int): String {
            val process = Runtime.getRuntime().exec(
                arrayOf(
                    "logcat", "-d", "-v", "threadtime",
                    "--pid", pid.toString(),
                    "-t", maxLines.toString(),
                ),
            )
            val text = BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8))
                .use { it.readText() }
            // Drain stderr to avoid blocking; ignore content. waitFor
            // ensures the process is reaped so we don't leak file handles.
            process.errorStream.close()
            process.waitFor()
            return text
        }
    }

    /**
     * Read the app's display versionName via PackageManager. We avoid
     * `BuildConfig.VERSION_NAME` because this project does not enable
     * `buildFeatures.buildConfig`, so the generated class is absent.
     */
    private fun readVersionName(context: android.content.Context): String = try {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
    } catch (t: Throwable) {
        "unknown"
    }

    /**
     * Read the app's versionCode via PackageManager. Uses the
     * `longVersionCode` API on API 28+ (P+) and falls back to the
     * deprecated `versionCode` on older devices. Returns 0 on
     * failure rather than throwing — the export should never fail
     * just because we couldn't read a metadata field.
     */
    private fun readVersionCode(context: android.content.Context): Int = try {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            info.longVersionCode.toInt()
        } else {
            info.versionCode
        }
    } catch (t: Throwable) {
        0
    }
}
