package com.pockref.app.update

import android.content.SharedPreferences
import java.io.File
import java.lang.reflect.Proxy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppUpdateControllerTest {
    private class Backend : UpdateBackend {
        val manifest = AppUpdateManifest(36, "test", "test", "hash", 10, "", null)
        var checks = 0
        var downloads = 0
        var installs = 0
        var permissions = 0
        var allowed = true
        var cached: File? = null
        var gate: CompletableDeferred<Unit>? = null
        var failCheck = false
        var failPermission = false
        var onOpenPermission: (() -> Unit)? = null
        override suspend fun check(): UpdateCheckResult {
            checks++
            if (failCheck) error("offline")
            return UpdateCheckResult.Available(manifest)
        }
        override suspend fun cached(manifest: AppUpdateManifest) = cached
        override suspend fun download(manifest: AppUpdateManifest, progress: (Int) -> Unit): File {
            downloads++
            gate?.await()
            return File("verified.apk").also { cached = it }
        }
        override fun newerThanInstalled(code: Int) = true
        override fun canInstall() = allowed
        override fun openPermission() {
            permissions++
            if (failPermission) error("settings unavailable")
            onOpenPermission?.invoke()
        }
        override fun install(apk: File): UpdateInstallResult {
            installs++
            return UpdateInstallResult.Started
        }
    }

    private fun preferences(): SharedPreferences {
        val values = mutableMapOf<String, Any?>()
        val editor = Proxy.newProxyInstance(
            SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java),
        ) { proxy, method, args ->
            when {
                method.name.startsWith("put") -> { values[args!![0] as String] = args[1]; proxy }
                method.name == "remove" -> { values.remove(args!![0]); proxy }
                method.name == "commit" -> true
                else -> null
            }
        }
        return Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader, arrayOf(SharedPreferences::class.java),
        ) { _, method, args ->
            when (method.name) {
                "edit" -> editor
                "contains" -> values.containsKey(args!![0])
                else -> values[args!![0]] ?: args[1]
            }
        } as SharedPreferences
    }

    @Test fun foregroundChecksDeduplicateAndManualBypassesThrottle() = runTest {
        val backend = Backend()
        val controller = AppUpdateController(backend, preferences(), this) {}
        controller.onForeground()
        controller.onForeground()
        advanceUntilIdle()
        controller.onForeground()
        advanceUntilIdle()
        assertEquals(1, backend.checks)
        controller.check()
        advanceUntilIdle()
        assertEquals(2, backend.checks)
    }

    @Test fun automaticFailureIsSilentAndManualFailureVisible() = runTest {
        val backend = Backend().apply { failCheck = true }
        val controller = AppUpdateController(backend, preferences(), this) {}
        controller.onForeground()
        advanceUntilIdle()
        assertNull(controller.state.value.message)
        controller.check()
        advanceUntilIdle()
        assertNotNull(controller.state.value.message)
    }

    @Test fun automaticFailureRetriesOnNextForeground() = runTest {
        val backend = Backend().apply { failCheck = true }
        val controller = AppUpdateController(backend, preferences(), this) {}
        controller.onForeground()
        advanceUntilIdle()
        backend.failCheck = false
        controller.onBackground()
        controller.onForeground()
        advanceUntilIdle()
        assertEquals(2, backend.checks)
        assertNotNull(controller.state.value.manifest)
    }

    @Test fun permissionSettingsFailureDoesNotLeavePendingAuthorization() = runTest {
        val backend = Backend().apply { allowed = false; failPermission = true }
        val controller = AppUpdateController(backend, preferences(), this) {}
        controller.onForeground()
        advanceUntilIdle()
        controller.downloadAndInstall()
        advanceUntilIdle()
        assertFalse(controller.state.value.awaitingPermission)
        assertTrue(controller.state.value.message!!.contains("settings unavailable"))
        backend.allowed = true
        controller.onBackground()
        controller.onForeground()
        advanceUntilIdle()
        assertEquals(0, backend.installs)
    }

    @Test fun immediatePermissionReturnResumesAfterPreparationFinishes() = runTest {
        val backend = Backend().apply { allowed = false }
        val controller = AppUpdateController(backend, preferences(), this) {}
        backend.onOpenPermission = {
            controller.onBackground()
            backend.allowed = true
            controller.onForeground()
        }
        controller.onForeground()
        advanceUntilIdle()
        controller.downloadAndInstall()
        advanceUntilIdle()
        assertEquals(1, backend.installs)
        assertFalse(controller.state.value.awaitingPermission)
        assertFalse(controller.state.value.preparingInstall)
    }

    @Test fun backgroundDownloadNeverInstallsAutomaticallyOnResume() = runTest {
        val backend = Backend()
        val controller = AppUpdateController(backend, preferences(), this) {}
        controller.onForeground()
        advanceUntilIdle()
        controller.downloadAndInstall()
        controller.downloadAndInstall()
        controller.onBackground()
        advanceUntilIdle()
        assertEquals(1, backend.downloads)
        assertEquals(0, backend.installs)
        controller.onForeground()
        advanceUntilIdle()
        assertEquals(0, backend.installs)
        controller.installReady()
        advanceUntilIdle()
        assertEquals(1, backend.installs)
    }

    @Test fun failedCanvasCheckpointBlocksInstaller() = runTest {
        val backend = Backend()
        val controller = AppUpdateController(backend, preferences(), this) { error("disk full") }
        controller.onForeground()
        advanceUntilIdle()
        controller.downloadAndInstall()
        advanceUntilIdle()
        assertEquals(0, backend.installs)
        assertTrue(controller.state.value.message!!.contains("disk full"))
        assertFalse(controller.state.value.preparingInstall)
    }

    @Test fun permissionReturnReusesVerifiedApk() = runTest {
        val backend = Backend().apply { allowed = false }
        var checkpoints = 0
        val controller = AppUpdateController(backend, preferences(), this) { checkpoints++ }
        controller.onForeground()
        advanceUntilIdle()
        controller.downloadAndInstall()
        advanceUntilIdle()
        assertEquals(1, backend.permissions)
        assertEquals(0, checkpoints)
        controller.onBackground()
        backend.allowed = true
        controller.onForeground()
        advanceUntilIdle()
        assertEquals(1, checkpoints)
        assertEquals(1, backend.downloads)
        assertEquals(1, backend.installs)
    }

    @Test fun ignoredReleaseStillAppearsOnManualCheck() = runTest {
        val backend = Backend()
        val controller = AppUpdateController(backend, preferences(), this) {}
        controller.onForeground()
        advanceUntilIdle()
        controller.ignoreCurrentVersion()
        controller.check()
        advanceUntilIdle()
        assertFalse(controller.state.value.noticeVisible)
        assertNotNull(controller.state.value.manifest)
    }

    @Test fun restartRestoresCachedDownloadWithoutNetwork() = runTest {
        val backend = Backend()
        val prefs = preferences()
        val first = AppUpdateController(backend, prefs, this) {}
        first.check()
        advanceUntilIdle()
        first.downloadAndInstall()
        advanceUntilIdle()
        val second = AppUpdateController(backend, prefs, this) {}
        advanceUntilIdle()
        assertNotNull(second.state.value.readyApk)
        assertEquals(1, backend.downloads)
        assertEquals(1, backend.checks)
        assertEquals(0, backend.installs)
    }

    @Test fun cancelledDownloadCanBeRetried() = runTest {
        val backend = Backend().apply { gate = CompletableDeferred() }
        val controller = AppUpdateController(backend, preferences(), this) {}
        controller.check()
        advanceUntilIdle()
        controller.downloadAndInstall()
        advanceUntilIdle()
        controller.cancelDownload()
        advanceUntilIdle()
        assertFalse(controller.state.value.downloading)
        assertNull(controller.state.value.readyApk)
        backend.gate = null
        controller.downloadAndInstall()
        advanceUntilIdle()
        assertEquals(2, backend.downloads)
        assertNotNull(controller.state.value.readyApk)
    }
}
