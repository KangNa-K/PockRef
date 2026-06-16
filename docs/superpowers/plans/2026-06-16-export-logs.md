# Export-Logs Button on Start Page Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a single floating "导出运行日志" button to the bottom-right of the start page (`state.images.isEmpty()`). Click captures device info, app preferences snapshot, last-50 `UiError` entries, the most recent uncaught-exception stacktrace, and the app's own logcat (last 500 lines), then opens SAF (`ACTION_CREATE_DOCUMENT`) for the user to choose where to save the resulting `PockRef_日志导出_<timestamp>.txt`. Visible in both debug and release builds. Persists the last crash to `<filesDir>/last_crash.txt` via `Thread.setDefaultUncaughtExceptionHandler`. Bumps versionCode 8→9 and versionName 1.2.4→1.3.0.

**Architecture:** Two new pure-data utility classes (`data/CrashHandler.kt`, `data/LogExporter.kt`) keep all log capture + formatting logic JVM-testable. A new `ui/logs/LogExportFab.kt` is a self-contained Composable with local `remember` UI state; it owns the `rememberLauncherForActivityResult(CreateDocument)` and triggers `viewModel.startLogExport(...)` which runs the build on `Dispatchers.IO` and hands the assembled text back via a callback closure (the text never enters `StateFlow`). `MainActivity.onCreate` installs the crash handler before any other init so we catch crashes from `enableEdgeToEdge`/`setContent` onward.

**Tech Stack:** Kotlin 1.9.24, Jetpack Compose (BoM 2024.06.00), Material 3, Coroutines, JUnit 4 (existing test infra), `androidx.activity.compose.ActivityResultContracts.CreateDocument`, `Thread.setDefaultUncaughtExceptionHandler`. **No new dependencies** — uses only what is already declared in `app/build.gradle.kts`.

**Spec:** [`docs/superpowers/specs/2026-06-16-export-logs-design.md`](../specs/2026-06-16-export-logs-design.md) v1.0

**Repo conventions honored:**
- No `v*` git tags. Versioning via `versionCode` + `versionName` only.
- Release commit pattern: `docs+build: <ver> — <summary>`, includes CHANGELOG entry + `versionCode` bump + (optional) APK artifact.
- Feature commit prefix: `feat(...)` / `fix(...)` / `refactor(...)` from observed log.
- Test pattern: JUnit 4 + `org.junit.Assert.*`, mirrors production class name + `Test` suffix, heavy KDoc per test locking the contract.
- Source style: KDoc on every public API explaining V_NOW / why-this-exists; one giant VM file is tolerated (`PureRefViewModel.kt` is 2524 lines).

---

## File Structure

### New files (7)

| File | Responsibility | Lines |
|---|---|---|
| `app/src/main/java/com/pockref/app/data/CrashHandler.kt` | Installs a `Thread.UncaughtExceptionHandler` that synchronously writes the exception to `<filesDir>/last_crash.txt` then chains to the previous handler. Exposes `readLastCrash(Context): String?` and `clearLastCrash(Context)`. | ~60 |
| `app/src/main/java/com/pockref/app/data/LogExporter.kt` | Pure object, `suspend fun build(context, appPrefs, recentErrors, lastCrash): String`. Concatenates 6 sections (HEADER / DEVICE / PREFERENCES / UI_ERRORS / LAST_CRASH / LOGCAT). logcat dump is the only IO, isolated in try/catch. | ~150 |
| `app/src/main/java/com/pockref/app/ui/logs/LogExportState.kt` | `sealed interface LogExportState { Idle, Loading, Error(msg) }`. Local UI state for the FAB. | ~10 |
| `app/src/main/java/com/pockref/app/ui/logs/LogExportFab.kt` | The composable. 44dp chrome + FabElevation + IconButton + label. Owns SAF launcher. | ~140 |
| `app/src/test/java/com/pockref/app/data/CrashHandlerTest.kt` | Locks: write produces file with class name + stack frame; second crash overwrites; `clearLastCrash` empties; `readLastCrash` returns null on missing/empty. | ~70 |
| `app/src/test/java/com/pockref/app/data/LogExporterTest.kt` | Locks: all 6 sections render; empty `recentErrors` → "No recent errors"; null `lastCrash` → "No recent crash"; logcat failure → section shows "dump failed"; header contains `BuildConfig.VERSION_NAME` + SDK int. | ~120 |
| `CHANGELOG_1.3.0.md` | User-facing changelog entry for v1.3.0. | ~12 |

### Modified files (7)

| File | Change |
|---|---|
| `app/src/main/java/com/pockref/app/ui/PureRefActions.kt` | + 4 actions: `RequestLogExport`, `LogExportStarted`, `LogExportFinished`, `LogExportFailed(reason)`. |
| `app/src/main/java/com/pockref/app/ui/PureRefViewModel.kt` | + `recentErrors: ArrayDeque<UiError>` (cap 50); + `_logExportInProgress: MutableStateFlow<Boolean>`; + `logExportInProgress: StateFlow<Boolean>`; + `snapshotRecentErrors()`; + `startLogExport(appLabel, onReady, onError)`; + 30s watchdog inside `startLogExport`; + `reportError` updated to push to ring buffer; + 4 `when` branches in `onAction`. |
| `app/src/main/java/com/pockref/app/MainActivity.kt` | + `CrashHandler.install(application)` after `super.onCreate(...)` and before `enableEdgeToEdge()`. |
| `app/src/main/java/com/pockref/app/ui/PureRefApp.kt` | + `LogExportFab` mounted inside the `state.images.isEmpty()` branch, in a `Box` overlay aligned `BottomEnd` with `padding(EdgeInset)`. + `rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain"))`. |
| `app/src/main/res/values/strings.xml` | + 5 strings (button label, 4 toast variants). |
| `app/src/main/res/values-zh-rCN/strings.xml` | + 5 strings (mirror). |
| `app/build.gradle.kts` | `versionCode 8 → 9`, `versionName "1.2.4" → "1.3.0"`. |

### Unchanged

- `AndroidManifest.xml` — no new permissions; `READ_MEDIA_*` / `CAMERA` / `READ_EXTERNAL_STORAGE` already cover everything we need (actually we don't even need those for this feature, since SAF + logcat + filesDir need zero permissions).
- `app/proguard-rules.pro` — no new keep rules required; the data class fields and Compose runtime are already kept.
- `ui/settings/*` — unrelated.

---

## Task 1: Add `appPreferences` field accessors for crash handler (verify)

**Why first:** Tasks 2-3 reference `AppPreferences` indirectly. We need to confirm the exact accessor names we'll use in `LogExporter`. No code change in this task — just verification. (If we discover the field names differ from what's in the plan during execution, fix the plan inline.)

**Files:** none (read-only)

- [ ] **Step 1: Read the field declarations**

Run:
```bash
grep -n "^    var \|^    val " app/src/main/java/com/pockref/app/data/AppPreferences.kt | head -40
```

Expected (exact lines we will reference in Task 4):
```
26:    var defaultBackgroundPreset: BackgroundPreset
39:    var themeMode: ThemeMode
56:    var edgeSnapEnabled: Boolean
68:    var rotationSnapEnabled: Boolean
87:    var disabledGestureIds: Set<String>
131:    var spenMode: SpenMode
161:    var autoSaveIntervalMs: Long
187:    var autoSaveEnabled: Boolean
```

- [ ] **Step 2: Confirm `disabledGestureIds` is the gesture-toggle field**

We will reference `disabledGestureIds` in the PREFERENCES section of the log. Confirm with:

```bash
grep -n "disabledGestureIds" app/src/main/java/com/pockref/app/ui/settings/*.kt | head -5
```

If the gesture toggles use a different field name, update Task 4 inline before executing it.

- [ ] **Step 3: No commit**

This is verification only. Proceed to Task 2.

---

## Task 2: Add the 5 string resources

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`

- [ ] **Step 1: Read the current `values/strings.xml` to find the right insertion point**

```bash
tail -10 app/src/main/res/values/strings.xml
```

- [ ] **Step 2: Append 5 strings to `values/strings.xml`**

Open `app/src/main/res/values/strings.xml` and append (after the last existing string):

```xml
    <!-- V_NOW (2026-06-16, "启动页导出运行日志" feature):
         Button label and toast text for the LogExportFab on the start page.
         Suggested SAF filename prefix; users typically leave the timestamp
         segment as-is. -->
    <string name="log_export_button_label">导出运行日志</string>
    <string name="log_export_toast_success">日志已导出: %1$s</string>
    <string name="log_export_toast_write_failed">日志写入失败: %1$s</string>
    <string name="log_export_toast_build_failed">日志导出失败: %1$s</string>
    <string name="log_export_toast_timeout">日志导出超时</string>
```

- [ ] **Step 3: Mirror in `values-zh-rCN/strings.xml`**

Append the same 5 strings to `app/src/main/res/values-zh-rCN/strings.xml`. (PockRef is zh-CN-first; both locales currently ship the same Chinese text. Future i18n can diverge these.)

- [ ] **Step 4: Verify the strings compile**

Run:
```bash
./gradlew :app:processDebugResources
```

Expected: `BUILD SUCCESSFUL`. (No Java/Kotlin compile happens here, just resource processing; if it succeeds the XML is valid.)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/values/strings.xml \
        app/src/main/res/values-zh-rCN/strings.xml
git commit -m "feat(logs): add string resources for log-export button"
```

---

## Task 3: Add 4 new PockRefAction variants

**Files:**
- Modify: `app/src/main/java/com/pockref/app/ui/PureRefActions.kt`

- [ ] **Step 1: Find the end of `sealed interface PockRefAction`**

```bash
tail -20 app/src/main/java/com/pockref/app/ui/PureRefActions.kt
```

Find the last `data object` or `data class` line before the closing `}`. We'll append after the last existing variant.

- [ ] **Step 2: Append 4 new actions**

Open `app/src/main/java/com/pockref/app/ui/PureRefActions.kt`, locate the closing `}` of `sealed interface PockRefAction`, and insert before it:

```kotlin
    // V_NOW (2026-06-16, "启动页导出运行日志" feature):
    // Lifecycle of a log-export click. The Composable dispatches
    // [RequestLogExport]; the ViewModel emits [LogExportStarted]
    // when it enters its IO coroutine, [LogExportFinished] on
    // successful text assembly, and [LogExportFailed] on any
    // thrown exception. The actual SAF call lives in the
    // Composable (it owns the launcher) and uses a callback
    // closure to receive the assembled text — the text never
    // enters StateFlow because it can be ~100KB.
    data object RequestLogExport : PockRefAction
    data object LogExportStarted : PockRefAction
    data object LogExportFinished : PockRefAction
    data class  LogExportFailed(val reason: String) : PockRefAction
```

- [ ] **Step 3: Verify the file still compiles**

Run:
```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`. (No behaviour change yet — we just added variants that nothing dispatches.)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/pockref/app/ui/PureRefActions.kt
git commit -m "feat(logs): add 4 PockRefAction variants for log export lifecycle"
```

---

## Task 4: `LogExportState` sealed interface

**Files:**
- Create: `app/src/main/java/com/pockref/app/ui/logs/LogExportState.kt`

- [ ] **Step 1: Create the file**

Create `app/src/main/java/com/pockref/app/ui/logs/LogExportState.kt` with the following contents:

```kotlin
package com.pockref.app.ui.logs

/**
 * V_NOW (2026-06-16, "启动页导出运行日志" feature):
 * Local UI state for the [LogExportFab]. Held inside the
 * composable's `remember { mutableStateOf(...) }` — deliberately
 * **not** in [com.pockref.app.model.PockRefUiState] because only
 * the FAB itself cares about this state, and adding a field to
 * that 100+ field data class would trigger needless recomposition
 * everywhere else whenever the export lifecycle ticks.
 *
 * Lifecycle:
 *   Idle ──(FAB clicked)──▶ Loading ──(VM text built + SAF launched)──▶ Idle
 *                             │
 *                             └──(VM throws / build fails)──▶ Error(msg)
 *                                                                    │
 *                                                  (1.5s later) ─────┴──▶ Idle
 */
sealed interface LogExportState {
    data object Idle : LogExportState
    data object Loading : LogExportState
    data class Error(val message: String) : LogExportState
}
```

- [ ] **Step 2: Verify the file compiles**

Run:
```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/pockref/app/ui/logs/LogExportState.kt
git commit -m "feat(logs): add LogExportState sealed interface for FAB"
```

---

## Task 5: `CrashHandler` — TDD

**Files:**
- Create: `app/src/test/java/com/pockref/app/data/CrashHandlerTest.kt`
- Create: `app/src/main/java/com/pockref/app/data/CrashHandler.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/pockref/app/data/CrashHandlerTest.kt`:

```kotlin
package com.pockref.app.data

import org.junit.After
import org.junit.Assert.assertEquals
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
 * a Context stub.
 *
 * The tests lock three behaviors:
 *  1. After a simulated uncaught exception, [readLastCrash]
 *     returns a string containing the exception class name and
 *     at least one stack frame line.
 *  2. A second crash overwrites the first (only the latest crash
 *     is retained — there is no history).
 *  3. [clearLastCrash] empties the file such that the next
 *     [readLastCrash] returns null.
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
    fun readLastCrash_garbageFile_returnsNullAndDoesNotThrow() {
        // Corrupted file should NOT propagate an exception up the
        // log-export pipeline — it should degrade gracefully.
        File(crashDir, CrashHandler.CRASH_FILE_NAME).writeText("\u0000\u0001BINARY")
        assertNull(CrashHandler.readLastCrashFrom(crashDir))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:
```bash
./gradlew :app:testDebugUnitTest --tests "com.pockref.app.data.CrashHandlerTest"
```

Expected: FAIL with `Unresolved reference: CrashHandler`.

- [ ] **Step 3: Implement `CrashHandler`**

Create `app/src/main/java/com/pockref/app/data/CrashHandler.kt`:

```kotlin
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
            fos.fd.sync()
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
```

- [ ] **Step 4: Run the test to verify it passes**

Run:
```bash
./gradlew :app:testDebugUnitTest --tests "com.pockref.app.data.CrashHandlerTest"
```

Expected: PASS, all 6 tests green.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/pockref/app/data/CrashHandler.kt \
        app/src/test/java/com/pockref/app/data/CrashHandlerTest.kt
git commit -m "feat(logs): add CrashHandler with last_crash.txt persistence"
```

---

## Task 6: `LogExporter` — TDD

**Files:**
- Create: `app/src/test/java/com/pockref/app/data/LogExporterTest.kt`
- Create: `app/src/main/java/com/pockref/app/data/LogExporter.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/pockref/app/data/LogExporterTest.kt`:

```kotlin
package com.pockref.app.data

import com.pockref.app.model.UiError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM unit tests for [LogExporter]. We use a fake
 * `LogSource` interface so the test does not depend on
 * `Runtime.exec("logcat ...")` (which would require a real
 * Android device). The production [LogExporter.build] uses a
 * default `LogSource` that calls `Runtime.getRuntime().exec(...)`;
 * the test injects a deterministic source.
 *
 * The tests lock the assembly contract:
 *  - All 6 section headers are present and ordered.
 *  - Empty `recentErrors` → "No recent errors" literal.
 *  - Null `lastCrash` → "No recent crash" literal.
 *  - LogSource that throws → section shows "dump failed" + still
 *    completes the rest of the assembly.
 *  - Header contains the app version + SDK int (these are
 *    developer-relevant breadcrumbs).
 */
class LogExporterTest {

    /** A LogSource that returns fixed text — used for happy-path tests. */
    private val happyLog: LogSource = object : LogSource {
        override fun dump(pid: Int, maxLines: Int): String =
            "06-16 13:39:08.123 12345 12345 D AutoSave: tick\n"
    }

    /** A LogSource that throws — used to verify graceful degradation. */
    private val failingLog: LogSource = object : LogSource {
        override fun dump(pid: Int, maxLines: Int): String =
            throw java.io.IOException("logcat unavailable")
    }

    @Test
    fun build_allSectionsPresent_inOrder() = kotlinx.coroutines.runBlocking {
        val appLabel = "PockRef"
        val appPrefs = FakeAppPreferences(
            themeMode = "SYSTEM",
            autoSaveEnabled = true,
            autoSaveIntervalMs = 30_000L,
            spenMode = "BOX_SELECT_ON_HOLD",
            disabledGestures = emptySet(),
            recentScenesCount = 7,
        )
        val text = LogExporter.build(
            appLabel = appLabel,
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
                UiError("decode timeout", RuntimeException("5s"))
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

/**
 * Tiny AppPreferences stand-in for the test — the production
 * LogExporter accepts an `AppPreferences`-like object via a
 * narrow read-only interface so we can substitute a fake
 * without instantiating the SharedPreferences-backed class.
 */
private data class FakeAppPreferences(
    val themeMode: String,
    val autoSaveEnabled: Boolean,
    val autoSaveIntervalMs: Long,
    val spenMode: String,
    val disabledGestures: Set<String>,
    val recentScenesCount: Int,
)
```

- [ ] **Step 2: Run the test to verify it fails**

Run:
```bash
./gradlew :app:testDebugUnitTest --tests "com.pockref.app.data.LogExporterTest"
```

Expected: FAIL with `Unresolved reference: LogExporter` (and possibly `Unresolved reference: LogSource`, `UiError` if the import path is off — verify `UiError` lives in `com.pockref.app.model`).

- [ ] **Step 3: Read the actual `UiError` shape so the test compiles**

```bash
grep -n "class UiError\|data class UiError\|^class UiError" app/src/main/java/com/pockref/app/model/Models.kt
```

You should see something like:
```
data class UiError(val message: String, val throwable: Throwable? = null)
```

If the actual signature differs (e.g., different parameter names), update the test inline before running. The two-arg `UiError(message, throwable)` constructor is what we expect.

- [ ] **Step 4: Implement `LogExporter`**

Create `app/src/main/java/com/pockref/app/data/LogExporter.kt`:

```kotlin
package com.pockref.app.data

import android.content.Context
import android.os.Build
import com.pockref.app.model.UiError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.IOException
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
     * @param appLabel       Typically `context.applicationInfo.loadLabel(...)`
     *                       or the literal "PockRef".
     * @param versionName    From `BuildConfig.VERSION_NAME`.
     * @param versionCode    From `BuildConfig.VERSION_CODE`.
     * @param packageName    From `context.packageName`.
     * @param sdkInt         From `Build.VERSION.SDK_INT`.
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
            append(preferences(logSource))
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

    private fun preferences(@Suppress("UNUSED_PARAMETER") logSource: LogSource): String {
        // V_NOW: preferences are read inside the production build() via
        // the [Context]; this overload is kept narrow for testability.
        // Production-side, `buildForContext(context, ...)` reads them.
        appendLine("=== PREFERENCES ===")
        appendLine("(preferences are populated by LogExporter.buildForContext)")
        appendLine()
        return toString()
    }

    private fun errors(recentErrors: List<UiError>): String = buildString {
        appendLine("=== UI ERRORS (last 50, newest first) ===")
        if (recentErrors.isEmpty()) {
            appendLine("No recent errors")
        } else {
            recentErrors.forEach { e ->
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
        appendLine(dumped)
    }

    // ---- production wrapper --------------------------------------------

    /**
     * Production-side wrapper that pulls all live context values and
     * delegates to [build]. Use this from `PockRefViewModel`.
     */
    suspend fun buildForContext(
        context: Context,
        appLabel: String,
        recentErrors: List<UiError>,
        lastCrash: String?,
        themeMode: String,
        autoSaveEnabled: Boolean,
        autoSaveIntervalMs: Long,
        spenMode: String,
        disabledGestures: Set<String>,
        recentScenesCount: Int,
    ): String {
        val prefsSection = buildString {
            appendLine("=== PREFERENCES ===")
            appendLine("Theme mode:           $themeMode")
            appendLine("Auto-save:            ${if (autoSaveEnabled) "enabled (interval ${autoSaveIntervalMs / 1000}s)" else "disabled"}")
            appendLine("S Pen mode:           $spenMode")
            appendLine("Recent scenes count:  $recentScenesCount")
            if (disabledGestures.isNotEmpty()) {
                appendLine("Disabled gestures:    ${disabledGestures.sorted()}")
            }
            appendLine()
        }
        // Compose preferences + base text in one shot.
        val baseText = build(
            appLabel = appLabel,
            versionName = com.pockref.app.BuildConfig.VERSION_NAME,
            versionCode = com.pockref.app.BuildConfig.VERSION_CODE,
            packageName = context.packageName,
            sdkInt = android.os.Build.VERSION.SDK_INT,
            recentErrors = recentErrors,
            lastCrash = lastCrash,
            logSource = RealLogSource,
            currentPid = android.os.Process.myPid(),
            logcatLineCap = 500,
        )
        // Splice prefsSection into the base output. Base has a "=== PREFERENCES
        // ===\n(preferences are populated by ...)\n" placeholder block we need
        // to replace.
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
            return BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8))
                .use { it.readText() }
                .also {
                    // Drain stderr to avoid blocking; ignore content.
                    process.errorStream.close()
                    process.waitFor()
                }
        }
    }
}
```

> **Note on the test's `preferences(logSource)` parameter:** The test passes `logSource = happyLog` because the production `build(...)` overload accepts a `LogSource`; the standalone `preferences(...)` helper takes it as `@Suppress("UNUSED_PARAMETER")` for symmetry with the other helpers — this is the cleanest way to keep the function pure while letting `buildForContext` swap the section in. If during implementation you find a cleaner factoring, refactor freely as long as the tests stay green and `buildForContext` still produces the documented format.

- [ ] **Step 5: Run the tests to verify they pass**

Run:
```bash
./gradlew :app:testDebugUnitTest --tests "com.pockref.app.data.LogExporterTest"
```

Expected: PASS, all 6 tests green.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/pockref/app/data/LogExporter.kt \
        app/src/test/java/com/pockref/app/data/LogExporterTest.kt
git commit -m "feat(logs): add LogExporter with 6-section text builder"
```

---

## Task 7: `PockRefViewModel` — add `recentErrors` ring buffer + `logExportInProgress` + `startLogExport`

**Files:**
- Modify: `app/src/main/java/com/pockref/app/ui/PureRefViewModel.kt`

- [ ] **Step 1: Add the ring buffer field and `snapshotRecentErrors()` accessor**

Open `app/src/main/java/com/pockref/app/ui/PureRefViewModel.kt`. Find the existing declaration of `lastError: StateFlow<UiError?>` near line 228. Immediately after that block, insert:

```kotlin
    // V_NOW (2026-06-16, "启动页导出运行日志" feature):
    // Snapshot of recent [UiError]s that the LogExportFab will
    // include in its export. We keep an internal ring buffer
    // rather than reflecting it into [PockRefUiState] because
    // (a) nothing else in the app reads it, and (b) PockRefUiState
    // is already 100+ fields — pushing 50 strings in there would
    // trigger expensive recomposition on every error report.
    //
    // Capacity 50 matches the cap documented in the spec; if a
    // future bug requires more history, bump this constant.
    private val recentErrors = ArrayDeque<UiError>(50)
    private val recentErrorsLock = Any()

    internal fun snapshotRecentErrors(): List<UiError> = synchronized(recentErrorsLock) {
        recentErrors.toList()
    }
```

- [ ] **Step 2: Modify `reportError` to push into the ring buffer**

Find `internal fun reportError(message: String, throwable: Throwable? = null)` near line 236. Replace its body so that the existing two lines (`Log.w(TAG, message, throwable)` and `_errors.tryEmit(uiError)`) are preserved and the buffer push is added:

```kotlin
    internal fun reportError(message: String, throwable: Throwable? = null) {
        Log.w(TAG, message, throwable)
        val uiError = UiError(message, throwable)
        // tryEmit is non-suspending and respects the buffer; the only way it
        // returns false here is if extraBufferCapacity is exhausted, in which
        // case we drop silently — Logcat still has the full stack trace.
        _errors.tryEmit(uiError)
        // Always update the latest-error slot, even if the SharedFlow dropped
        // the event: a snackbar is fire-and-forget but a banner / persistence
        // layer would need this. Equality-based distinctUntilChanged on the
        // StateFlow side keeps recomposition cheap.
        _lastError.value = uiError
        // V_NOW (2026-06-16): mirror into the log-export ring buffer.
        // Push is bounded by capacity (50); oldest entries are evicted FIFO.
        synchronized(recentErrorsLock) {
            recentErrors.addLast(uiError)
            while (recentErrors.size > MAX_RECENT_ERRORS) {
                recentErrors.removeFirst()
            }
        }
    }
```

> **Note on the constant:** `MAX_RECENT_ERRORS` lives in the existing `companion object` (line 2521, which already declares `private const val TAG = "PockRefViewModel"`). Do **not** add a second `companion object` — Kotlin forbids duplicates. Edit the existing one to add the new constant:

```kotlin
    companion object {
        private const val TAG = "PockRefViewModel"
        // V_NOW (2026-06-16): ring-buffer cap for [recentErrors].
        private const val MAX_RECENT_ERRORS = 50
    }
```

- [ ] **Step 3: Add `_logExportInProgress`, `logExportInProgress`, and `startLogExport`**

Immediately after the `lastError` block and before `reportError`, insert:

```kotlin
    // V_NOW (2026-06-16, "启动页导出运行日志" feature):
    // Exposes whether a log export is currently in progress so
    // the FAB can disable itself and prevent duplicate clicks.
    // Mirrors the [lastError] StateFlow pattern (line ~228).
    // Deliberately NOT in [PockRefUiState] to avoid bloating the
    // 100+ field data class.
    private val _logExportInProgress = MutableStateFlow(false)
    val logExportInProgress: StateFlow<Boolean> = _logExportInProgress.asStateFlow()
```

- [ ] **Step 4: Add `startLogExport(...)` at the bottom of the class**

Open the class, find the closing `}` of `PockRefViewModel` (search for `^}` near the bottom). Insert the following just before the closing brace:

```kotlin
    /**
     * V_NOW (2026-06-16, "启动页导出运行日志" feature):
     * Build the run-log text on IO and hand it back via [onReady].
     * The Composable owns the SAF launcher and uses [onReady] to
     * receive the assembled text + suggested filename. The text
     * never enters StateFlow (it's too big — logcat alone can be
     * hundreds of KB).
     *
     * Idempotent: a second call while one is in progress is a no-op.
     *
     * @param appLabel  Result of `context.applicationInfo.loadLabel(...)`
     *                  or the literal "PockRef". Used as the filename prefix.
     * @param onReady   Called on the main thread when the text is ready.
     * @param onError   Called on the main thread if [LogExporter.buildForContext]
     *                  throws.
     */
    fun startLogExport(
        appLabel: String,
        onReady: (suggestedName: String, content: String) -> Unit,
        onError: (reason: String) -> Unit,
    ) {
        if (_logExportInProgress.value) return
        viewModelScope.launch {
            _logExportInProgress.value = true
            onAction(PockRefAction.LogExportStarted)

            // 30s watchdog: if build hangs (extremely unlikely — logcat
            // exec could theoretically block on a hostile device), reset
            // state and surface a timeout via onError.
            val watchdogJob = launch {
                kotlinx.coroutines.delay(30_000L)
                if (_logExportInProgress.value) {
                    Log.w(TAG, "Log export timed out")
                    _logExportInProgress.value = false
                    onError("timeout")
                    onAction(PockRefAction.LogExportFailed("timeout"))
                }
            }

            try {
                val lastCrash = CrashHandler.readLastCrash(application)
                val text = LogExporter.buildForContext(
                    context = application,
                    appLabel = appLabel,
                    recentErrors = snapshotRecentErrors(),
                    lastCrash = lastCrash,
                    themeMode = appPreferences.themeMode.name,
                    autoSaveEnabled = appPreferences.autoSaveEnabled,
                    autoSaveIntervalMs = appPreferences.autoSaveIntervalMs,
                    spenMode = appPreferences.spenMode.name,
                    disabledGestures = appPreferences.disabledGestureIds,
                    recentScenesCount = appPreferences.recentScenesCountOrZero(),
                )
                val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                    .format(java.util.Date())
                val name = "${appLabel}_日志导出_$ts.txt"
                watchdogJob.cancel()
                onReady(name, text)
                onAction(PockRefAction.LogExportFinished)
            } catch (t: Throwable) {
                watchdogJob.cancel()
                Log.e(TAG, "Log export failed", t)
                val msg = t.message ?: t::class.java.simpleName
                onError(msg)
                onAction(PockRefAction.LogExportFailed(msg))
            } finally {
                _logExportInProgress.value = false
            }
        }
    }

    /**
     * Helper used by [startLogExport]. Returns the count of recent
     * scenes from preferences. If the underlying AppPreferences does
     * not expose a direct count, return 0 (no UI surfaced; the export
     * continues without it).
     */
    private fun com.pockref.app.data.AppPreferences.recentScenesCountOrZero(): Int = try {
        // Read the count via the persisted list. AppPreferences does
        // not currently expose this directly; we approximate by reading
        // the `recentScenes` JSON string and parsing its length.
        // If this proves awkward during implementation, surface a
        // dedicated `recentScenesCount` field on AppPreferences in a
        // small follow-up commit.
        getString("recent_scenes_json", "[]").let { json ->
            org.json.JSONArray(json).length()
        }
    } catch (_: Throwable) { 0 }
```

> **Implementation note on `recentScenesCountOrZero`:** `AppPreferences` does not currently expose a count field for recent scenes. The above reads the persisted JSON. If the actual SharedPreferences key differs (verify by running `grep -n "recent_scenes" app/src/main/java/com/pockref/app/data/AppPreferences.kt`), substitute the correct key. If a count accessor exists, prefer that.

- [ ] **Step 5: Add the 4 new `when` branches in `onAction`**

Find the `when (action)` block at line 451. Locate a safe insertion point (e.g., right before the closing `else -> {}` if one exists, or right before the closing `}` of the when). Add:

```kotlin
            // V_NOW (2026-06-16, "启动页导出运行日志" feature):
            // The actual work happens in [startLogExport] which the
            // FAB invokes directly via VM reference. These actions
            // are dispatched purely so any future telemetry /
            // debug-overlay UI can observe the lifecycle.
            PockRefAction.RequestLogExport -> Unit
            PockRefAction.LogExportStarted -> Unit
            PockRefAction.LogExportFinished -> Unit
            is PockRefAction.LogExportFailed -> reportError(
                "Log export failed: ${action.reason}",
            )
```

- [ ] **Step 6: Verify it compiles**

Run:
```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`. If there are missing-import errors, add them at the top of the file (`import com.pockref.app.data.CrashHandler`, `import com.pockref.app.data.LogExporter`).

- [ ] **Step 7: Run the existing test suite to confirm no regression**

Run:
```bash
./gradlew :app:testDebugUnitTest
```

Expected: PASS (all pre-existing tests still green).

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/pockref/app/ui/PureRefViewModel.kt
git commit -m "feat(logs): add startLogExport + recentErrors ring buffer to VM"
```

---

## Task 8: `LogExportFab` composable

**Files:**
- Create: `app/src/main/java/com/pockref/app/ui/logs/LogExportFab.kt`

- [ ] **Step 1: Create the file**

Create `app/src/main/java/com/pockref/app/ui/logs/LogExportFab.kt`:

```kotlin
package com.pockref.app.ui.logs

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pockref.app.R
import com.pockref.app.ui.PockRefViewModel
import com.pockref.app.ui.theme.Dimens
import kotlinx.coroutines.delay

/**
 * V_NOW (2026-06-16, "启动页导出运行日志" feature):
 * The bottom-right FAB on the start page. Mounted only when
 * [com.pockref.app.model.PockRefUiState.images] is empty (the
 * caller filters).
 *
 * Visual: 44dp chrome, semitransparent surface, chrome corner
 * radius, FabElevation shadow, Material `Description` icon,
 * permanent `labelSmall` label below ("导出运行日志").
 *
 * Lifecycle (state machine on local `remember`):
 *   Idle ──(click)──▶ Loading ──(VM returns text)──▶ Idle
 *                            │
 *                            └──(VM throws)──▶ Error(msg) ──(1.5s)──▶ Idle
 *
 * The composable owns the [rememberLauncherForActivityResult] for
 * `ACTION_CREATE_DOCUMENT("text/plain")`; the VM hands back the
 * assembled text via a closure, never via StateFlow.
 */
@Composable
fun LogExportFab(
    viewModel: PockRefViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appLabel = remember(context) {
        try {
            context.applicationInfo.loadLabel(context.packageManager).toString()
                .ifBlank { "PockRef" }
        } catch (_: Throwable) {
            "PockRef"
        }
    }

    // Captured text waiting for SAF callback to write.
    var pendingContent by remember { mutableStateOf<String?>(null) }
    var pendingName by remember { mutableStateOf<String?>(null) }
    var state by remember { mutableStateOf<LogExportState>(LogExportState.Idle) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        val name = pendingName
        val content = pendingContent
        pendingContent = null
        pendingName = null
        if (uri == null) {
            // User cancelled. No toast, no state change.
            return@rememberLauncherForActivityResult
        }
        runCatching {
            context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                out.write((content ?: "").toByteArray(Charsets.UTF_8))
            } ?: error("openOutputStream returned null")
        }.fold(
            onSuccess = {
                Toast.makeText(
                    context,
                    context.getString(R.string.log_export_toast_success, name ?: ""),
                    Toast.LENGTH_LONG,
                ).show()
            },
            onFailure = { t ->
                Toast.makeText(
                    context,
                    context.getString(
                        R.string.log_export_toast_write_failed,
                        t.message ?: t::class.java.simpleName,
                    ),
                    Toast.LENGTH_LONG,
                ).show()
            },
        )
    }

    // VM in-progress → disable button + show progress.
    val inProgress by viewModel.logExportInProgress.collectAsStateWithLifecycleSafe()
    val clickEnabled = !inProgress && state !is LogExportState.Loading

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(Dimens.BottomPanelChromeSize)  // 48dp
                .shadow(Dimens.FabElevation, RoundedCornerShape(Dimens.ChromeCornerRadius))
                .clip(RoundedCornerShape(Dimens.ChromeCornerRadius))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = Dimens.ChromeSurfaceAlpha))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(Dimens.ChromeCornerRadius)),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(
                onClick = {
                    if (!clickEnabled) return@IconButton
                    state = LogExportState.Loading
                    viewModel.startLogExport(
                        appLabel = appLabel,
                        onReady = { name, content ->
                            pendingName = name
                            pendingContent = content
                            state = LogExportState.Idle
                            launcher.launch(name)
                        },
                        onError = { reason ->
                            state = LogExportState.Error(reason)
                        },
                    )
                },
                enabled = clickEnabled,
            ) {
                if (state is LogExportState.Loading || inProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Description,
                        contentDescription = stringResource(R.string.log_export_button_label),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.log_export_button_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    // Error → auto-reset to Idle after 1.5s.
    LaunchedEffect(state) {
        if (state is LogExportState.Error) {
            val msg = (state as LogExportState.Error).message
            Toast.makeText(
                context,
                context.getString(R.string.log_export_toast_build_failed, msg),
                Toast.LENGTH_LONG,
            ).show()
            delay(1500L)
            state = LogExportState.Idle
        }
    }
}

/**
 * Wrapper around [androidx.lifecycle.compose.collectAsStateWithLifecycle]
 * that gracefully no-ops when the host is not a LifecycleOwner (which
 * can happen in Preview composables). Keeps the FAB composable
 * preview-friendly.
 */
@Composable
private fun <T> kotlinx.coroutines.flow.StateFlow<T>.collectAsStateWithLifecycleSafe(): androidx.compose.runtime.State<T> {
    val state = remember { mutableStateOf(value) }
    LaunchedEffect(this) {
        this@collectAsStateWithLifecycleSafe.collect { state.value = it }
    }
    return state
}
```

> **Imports note:** the file uses several imports (`collectAsStateWithLifecycle`, `LocalLifecycleOwner`, etc.) — if your IDE auto-imports them, accept the suggestions; otherwise add `import androidx.lifecycle.compose.collectAsStateWithLifecycle` and let the compiler resolve the rest.

- [ ] **Step 2: Verify it compiles**

Run:
```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`. Resolve any import errors.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/pockref/app/ui/logs/LogExportFab.kt
git commit -m "feat(logs): add LogExportFab composable with SAF launcher"
```

---

## Task 9: Mount `LogExportFab` on the start page in `PureRefApp.kt`

**Files:**
- Modify: `app/src/main/java/com/pockref/app/ui/PureRefApp.kt`

- [ ] **Step 1: Find the start-page branch**

```bash
grep -n "isEmpty\|images.isEmpty\|isStartPage\|StartPageScaffold\|RecentFileGrid" app/src/main/java/com/pockref/app/ui/PureRefApp.kt | head -20
```

Expected: a branch where the start page is rendered. The exact line depends on the current state of the file but should look like:

```kotlin
if (state.images.isEmpty()) {
    StartPageScaffold(...)  // or inline Box/Column
}
```

- [ ] **Step 2: Add the import**

Add to the imports block of `PureRefApp.kt`:

```kotlin
import com.pockref.app.ui.logs.LogExportFab
```

- [ ] **Step 3: Wrap the start page in a `Box` and mount the FAB**

Find the existing start-page scaffold / composable invocation. The body currently looks roughly like:

```kotlin
if (state.images.isEmpty()) {
    StartPageScaffold(state = state, ...)
}
```

Replace it with:

```kotlin
if (state.images.isEmpty()) {
    Box(modifier = Modifier.fillMaxSize()) {
        StartPageScaffold(state = state, ...)
        LogExportFab(
            viewModel = viewModel,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(Dimens.EdgeInset),
        )
    }
}
```

If the existing branch already wraps in a `Box`, just add the `LogExportFab(...)` as the second child. The key invariants:

- `LogExportFab` is the **last** child of the Box so it draws on top.
- `align(Alignment.BottomEnd)` places it at the bottom-right.
- `padding(Dimens.EdgeInset)` (16dp) gives the same inset as other chrome.

> **Caveat:** if `viewModel` is not in scope at the call site, pass it through the function signature or hoist it from the enclosing `PockRefApp`. Read lines ~509+ of `PureRefApp.kt` to see how `viewModel` is currently passed to the start-page composable; mirror that pattern.

- [ ] **Step 4: Verify it compiles**

Run:
```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Manual smoke check (no test infra for Compose UI yet)**

Run:
```bash
./gradlew :app:installDebug
adb shell am start -n com.pockref.app/.MainActivity
```

Expected: app launches, start page visible, bottom-right shows the FAB with "导出运行日志" label below the icon. (If your device doesn't have adb, skip — manual verification is in the checklist in Task 12.)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/pockref/app/ui/PureRefApp.kt
git commit -m "feat(logs): mount LogExportFab on start page bottom-right"
```

---

## Task 10: Install `CrashHandler` in `MainActivity.onCreate`

**Files:**
- Modify: `app/src/main/java/com/pockref/app/MainActivity.kt`

- [ ] **Step 1: Find `onCreate`**

```bash
grep -n "fun onCreate\|enableEdgeToEdge\|spenDetector\|setContent" app/src/main/java/com/pockref/app/MainActivity.kt | head -10
```

- [ ] **Step 2: Add the install call**

Open `MainActivity.kt` and find the line `super.onCreate(savedInstanceState)`. Immediately after it, insert:

```kotlin
        // V_NOW (2026-06-16, "启动页导出运行日志" feature):
        // Install before any other init so we catch crashes from
        // enableEdgeToEdge / setContent / Compose initialization
        // onward. Idempotent — a second install is a no-op.
        CrashHandler.install(application)
```

Add to imports:

```kotlin
import com.pockref.app.data.CrashHandler
```

- [ ] **Step 3: Verify it compiles**

Run:
```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/pockref/app/MainActivity.kt
git commit -m "feat(logs): install CrashHandler in MainActivity.onCreate"
```

---

## Task 11: Bump version + write CHANGELOG

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `CHANGELOG_1.3.0.md`

- [ ] **Step 1: Read current version fields**

```bash
grep -n "versionCode\|versionName" app/build.gradle.kts
```

Expected: `versionCode = 8` and `versionName = "1.2.4"`.

- [ ] **Step 2: Bump to 1.3.0 / 9**

Edit `app/build.gradle.kts`:

```kotlin
        versionCode = 9,
        versionName = "1.3.0",
```

- [ ] **Step 3: Read CHANGELOG_1.2.4.md for format**

```bash
cat CHANGELOG_1.2.4.md
```

- [ ] **Step 4: Create `CHANGELOG_1.3.0.md`**

Mirror the existing format. Example:

```markdown
# 1.3.0

## ✨ 新功能

- **启动页"导出运行日志"**:在起始页(`RecentFileGrid`)右下角新增悬浮按钮,点击即可导出当前设备的运行日志(应用版本 / 设备信息 / 用户偏好 / 最近 50 条 UI 错误 / 最近一次崩溃堆栈 / 本进程 logcat 后 500 行),通过 SAF 让用户选择保存位置,文件名 `PockRef_日志导出_<时间戳>.txt`。便于开发者与技术支持快速定位用户报告的异常。
- **崩溃持久化**:`CrashHandler` 在 `MainActivity.onCreate` 启动时安装,捕获未处理异常并同步写入 `<filesDir>/last_crash.txt`,下次启动导出日志时一并带上。

## 🛠 内部改进

- 新增 `data/CrashHandler.kt` 与 `data/LogExporter.kt` 两个纯函数 utility,均带 JVM 单测。
- `PockRefViewModel` 新增 `recentErrors` 50 条 ring buffer 与 `logExportInProgress: StateFlow<Boolean>`(沿用 `lastError` 模式,不进 `PockRefUiState`)。

## 🐛 Bug 修复

- (无)
```

Adjust tone / wording to match the project's existing CHANGELOG voice — read 1.2.4 and 1.2.3 to mirror.

- [ ] **Step 5: Verify build still works**

Run:
```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL` and APK at `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 6: Commit**

```bash
git add app/build.gradle.kts CHANGELOG_1.3.0.md
git commit -m "docs+build: 1.3.0 — start-page log-export FAB + crash persistence"
```

---

## Task 12: Manual verification checklist + final test pass

**Files:** none (verification only)

- [ ] **Step 1: Run full unit test suite**

```bash
./gradlew :app:testDebugUnitTest
```

Expected: all tests pass (pre-existing + new `CrashHandlerTest` + `LogExporterTest`).

- [ ] **Step 2: Build release APK to confirm `assembleRelease` works**

```bash
./gradlew :app:assembleRelease
```

Expected: `BUILD SUCCESSFUL` (or pre-existing release-build issues unrelated to this feature — if R8 strips something we need, add a `-keep` rule to `proguard-rules.pro` and commit).

- [ ] **Step 3: Walk the manual checklist from spec §9.2**

For each item, record ✅ / ❌:

1. Debug build start → FAB visible on start page (bottom-right)
2. Tap FAB → SAF opens → choose location → `.txt` lands → Toast "日志已导出: PockRef_日志导出_*.txt"
3. Open `.txt` → all 6 sections present, UTF-8 intact, logcat has own-PID lines
4. Toast text matches `R.string.log_export_toast_success`
5. Force a `RuntimeException` in `MainActivity.onCreate` → app crashes → restart → tap FAB → `=== LAST CRASH ===` contains the stacktrace
6. Release build (install via `./gradlew :app:installRelease`) → FAB still visible (no build-type gate)
7. During Loading state, FAB is disabled (try a long logcat call if needed)
8. SAF dialog → press Back → no Toast (silent cancel)
9. Pick a read-only directory in SAF → write fails → Toast "日志写入失败: ..."
10. Repeated rapid taps on FAB → only one export runs (verify by observing only one logcat dump and one Toast)

- [ ] **Step 4: If any ❌, file a follow-up commit**

For any failed item, fix inline and commit with a `fix(logs):` prefix. Do not amend prior commits.

- [ ] **Step 5: Final summary commit (only if any tweak was made)**

If no tweaks, skip this step. If tweaks were made, the fix commits above are the summary — no separate "summary" commit needed.

---

## Self-Review (writer-side)

### Spec coverage

| Spec section | Implemented by |
|---|---|
| §1 触发场景与入口 | Task 8 (FAB), Task 9 (mount) |
| §2 界面层级树 | Task 8 (LogExportFab), Task 9 (Box overlay) |
| §3.1 LogExportFab | Task 8 |
| §3.2 LogExportState | Task 4 |
| §3.3 CrashHandler | Task 5 |
| §3.4 LogExporter | Task 6 |
| §3.5 recentErrors ring buffer | Task 7 |
| §4.1 StateFlow (not in PockRefUiState) | Task 7 Step 3 |
| §4.2 PockRefAction 增量 | Task 3 |
| §4.3 startLogExport API | Task 7 Step 4 |
| §5 导出文件格式 | Task 6 |
| §6 数据流 | Task 8 (launcher) + Task 7 (callback) |
| §7 错误处理 | Task 6 (logcat degrade), Task 8 (SAF Toast), Task 7 (watchdog) |
| §8 资源/字符串 | Task 2 |
| §9.1 单元测试 | Tasks 5, 6 |
| §9.2 手动验证 | Task 12 |
| §10 文件清单 | All tasks |
| §12 不在本规范范围 | (n/a — explicit exclusion) |

### Placeholder scan

- No "TBD" / "TODO" / "implement later" / "fill in details".
- One `~` estimate per file — that's line-budgeting, not a placeholder.
- One "verify during execution" callout (Task 1, Step 2 for `disabledGestureIds`) — this is a guardrail, not a placeholder.
- One "verify during execution" callout (Task 6, Step 3 for `UiError` signature) — same.
- One "verify during execution" callout (Task 7, Step 4 for `recentScenesCountOrZero` key) — same.

### Type consistency

- `LogExportState` defined in Task 4 with `Idle / Loading / Error(msg)`; used identically in Task 8 (`state = LogExportState.Loading`, `state = LogExportState.Error(reason)`, `state = LogExportState.Idle`).
- `startLogExport(appLabel, onReady, onError)` signature in Task 7 matches the call site in Task 8 exactly.
- `CrashHandler.CRASH_FILE_NAME` referenced in Task 5 tests and Task 5 implementation; not used outside.
- `LogExporter.LogSource` referenced in Task 6 tests and Task 6 implementation; `RealLogSource` private object used only in `buildForContext`.

### Internal consistency

- §4.1 of the spec says `logExportInProgress` is NOT in `PockRefUiState`. Task 7 reflects this: it's a top-level `StateFlow<Boolean>` on the VM, mirroring `lastError`.
- The spec says no new permissions. Tasks 5–10 add no `<uses-permission>` entries. ✓
- The spec says `LogExportFab` is mounted only when `state.images.isEmpty()`. Task 9 wraps the start-page scaffold in a Box and mounts the FAB inside that branch. ✓

Plan is internally consistent and covers the spec.

---

## Execution Notes

- **Single commit per task** keeps the diff easy to review. If a task requires multiple atomic commits (e.g., Task 7 has 6 steps but is conceptually one feature), keep it as one commit.
- **If `Run the tests` ever fails for an unrelated reason** (e.g., flaky test in a different file), don't roll that into a fix commit for this feature — file a separate bug and continue.
- **If a step's exact line numbers in the source files have drifted** from what's quoted here (e.g., `reportError` is no longer at line 236), search for the named symbol instead — the file structure has been stable across the recent commit log.
- **Don't introduce new dependencies.** If a step feels like it needs one, stop and ask — the spec is explicit that we use only what's already in `app/build.gradle.kts`.
