package com.pockref.app.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import com.pockref.app.model.PockRefScene
import com.pockref.app.model.SceneImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest

class AutoSaveManager(context: Context, private val codec: PurSceneCodec) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("pockref_autosave", Context.MODE_PRIVATE)

    private val autoSaveDir = File(appContext.filesDir, "auto_save").also { it.mkdirs() }

    // V_NOW: backed by [AppPreferences.autoSaveIntervalMs] so the
    // value is user-configurable via the new SettingsPanel slider.
    // Reading on every access (rather than caching + invalidating
    // on a callback) keeps the implementation simple — the
    // auto-save coroutine reads intervalMs once per cycle (~5 min
    // default), so a single SharedPreferences read per cycle is
    // negligible, and we get "always fresh" semantics for free:
    // the next cycle after the user moves the slider picks up the
    // new value without needing a coroutine restart or a
    // listener.
    val intervalMs: Long
        get() = appPrefs.autoSaveIntervalMs

    /**
     * V_NOW: master switch read on every cycle. The
     * coroutine early-returns from [shouldAutoSave]
     * when this is `false`, so the loop still ticks at
     * the configured interval (no coroutine restart,
     * the `delay()` continues to schedule the next
     * wakeup) but no file I/O happens. Reading directly
     * from the pref (same pattern as [intervalMs]) keeps
     * the "user toggles the switch → next cycle honors
     * it" semantics without any cross-coroutine
     * signalling.
     */
    val autoSaveEnabled: Boolean
        get() = appPrefs.autoSaveEnabled

    private val appPrefs = AppPreferences(appContext)

    val lastSaveTimestamp: Long
        get() = prefs.getLong(KEY_LAST_SAVE_TS, 0L)

    fun shouldAutoSave(currentTimestamp: Long): Boolean {
        // V_NOW: master switch. When the user has
        // disabled auto-save via Settings → 通用 →
        // 自动保存, the next `delay(intervalMs)` wakeup
        // sees this false and returns without touching
        // the disk. The check is the FIRST thing in the
        // function (before the `intervalMs` short-circuit)
        // so an empty canvas with the switch off is still
        // cheap (one boolean read + return) — we never
        // want the auto-save loop to do work the user
        // has explicitly turned off, even no-op work.
        if (!autoSaveEnabled) {
            Log.d(TAG, "shouldAutoSave: disabled by master switch")
            return false
        }
        val elapsed = currentTimestamp - lastSaveTimestamp
        if (elapsed < intervalMs) {
            Log.d(TAG, "shouldAutoSave: interval not reached (elapsed=${elapsed}ms < interval=${intervalMs}ms)")
            return false
        }
        Log.d(TAG, "shouldAutoSave: all checks passed (elapsed=${elapsed}ms >= interval=${intervalMs}ms)")
        return true
        // V_NOW (follow-up after "1 min never fires" bug):
        // the previous version of this function also
        // gated on a `hasContentChanged: Boolean` flag
        // passed in by the ViewModel's loop. The flag was
        // derived from a separate in-memory
        // [lastAutoSaveSnapshotHash] field that the
        // ViewModel reset on every `notifyContentChanged()`
        // call. The bug: rotation-snap engagement
        // (PureRefViewModelTransform.kt:1126),
        // [applyHistoryEntry] (line 1360), and
        // [applyDiff] (line 1379) all mutate
        // [uiState.images] directly via
        // [uiState.update] WITHOUT calling
        // [notifyContentChanged], so the in-memory hash
        // never reset and `hasContentChanged` was
        // permanently `false` after the first save. The
        // result: every subsequent `delay(intervalMs)`
        // tick saw `shouldAutoSave` return `false` and
        // the auto-save loop never wrote a backup again,
        // regardless of what the user did.
        //
        // The fix removes the redundant outer check —
        // [autoSave] itself computes a SHA-256 hash of
        // the scene and compares to the persisted
        // [KEY_LAST_CONTENT_HASH]; if the content hasn't
        // changed, [autoSave] returns `false` after one
        // cheap hash compare and never touches the disk.
        // Moving the hash comparison to the single
        // authoritative place (the durable
        // [KEY_LAST_CONTENT_HASH] in the auto-save
        // [SharedPreferences], not a transient in-memory
        // [lastAutoSaveSnapshotHash]) means the "did the
        // scene change" decision is correct REGARDLESS
        // of which [uiState.update] site mutated
        // [uiState.images] — the only requirement is that
        // the next tick reads the live [uiState.value],
        // which the loop already does.
    }

    suspend fun autoSave(
        scene: PockRefScene,
        currentSceneUri: Uri?,
        contentResolver: android.content.ContentResolver,
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val sceneHash = computeSceneHash(scene)
            val lastHash = prefs.getString(KEY_LAST_CONTENT_HASH, null)
            Log.d(TAG, "autoSave: sceneHash=$sceneHash, lastHash=$lastHash, uri=$currentSceneUri")
            if (lastHash == sceneHash) {
                Log.d(TAG, "autoSave: hash unchanged, skipping")
                return@withContext false
            }

            val backupFile = writeBackup(scene)
            Log.d(TAG, "autoSave: backup written to ${backupFile.absolutePath}")

            if (currentSceneUri != null) {
                try {
                    contentResolver.openOutputStream(currentSceneUri, "wt")?.use { output ->
                        backupFile.inputStream().use { input ->
                            input.copyTo(output, 65536)
                        }
                    }
                    Log.d(TAG, "autoSave: wrote to original URI $currentSceneUri")
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (ioError: Throwable) {
                    Log.w(TAG, "Failed to auto-save to original URI, backup preserved", ioError)
                }
            } else {
                Log.d(TAG, "autoSave: no currentSceneUri, backup-only save")
            }

            prefs.edit()
                .putLong(KEY_LAST_SAVE_TS, System.currentTimeMillis())
                .putString(KEY_LAST_CONTENT_HASH, sceneHash)
                .apply()

            cleanupOldBackups()

            true
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Auto-save failed", e)
            false
        }
    }

    private fun writeBackup(scene: PockRefScene): File {
        val sanitizedName = scene.name.replace(Regex("[^a-zA-Z0-9_.-]"), "_")
        val backupFileName = "${sanitizedName}.auto_backup.pur"
        val backupFile = File(autoSaveDir, backupFileName)

        FileOutputStream(backupFile).use { output ->
            // V_NOW (2026-06-11): use the custom format (write)
            // instead of legacy format (writeLegacyStreaming) so
            // that cropRect and locked are persisted. See
            // PureRefViewModelScene.writeSceneToFile for the same
            // change and rationale.
            codec.write(scene, output)
        }

        return backupFile
    }

    private fun cleanupOldBackups() {
        val sevenDaysMs = 7L * 24 * 60 * 60 * 1000
        val cutoff = System.currentTimeMillis() - sevenDaysMs
        autoSaveDir.listFiles()?.filter { file ->
            file.name.endsWith(".auto_backup.pur") && file.lastModified() < cutoff
        }?.forEach { file ->
            if (file.delete()) {
            }
        }
    }

    private fun computeSceneHash(scene: PockRefScene): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buffer = ByteBuffer.allocate(4)
        md.update(scene.name.toByteArray(Charsets.UTF_8))
        md.update(scene.backgroundColor.value.toString().toByteArray(Charsets.UTF_8))
        scene.images.forEach { image ->
            md.update(image.id.toByteArray(Charsets.UTF_8))
            buffer.clear(); buffer.putInt(image.center.x.toBits()); md.update(buffer.array().copyOf())
            buffer.clear(); buffer.putInt(image.center.y.toBits()); md.update(buffer.array().copyOf())
            buffer.clear(); buffer.putInt(image.scale.toBits()); md.update(buffer.array().copyOf())
            buffer.clear(); buffer.putInt(image.rotationDegrees.toBits()); md.update(buffer.array().copyOf())
            md.update(image.flipX.toString().toByteArray())
            md.update(image.flipY.toString().toByteArray())
            md.update(image.zIndex.toString().toByteArray())
            buffer.clear(); buffer.putInt(image.colorAdjust.brightness.toBits()); md.update(buffer.array().copyOf())
            buffer.clear(); buffer.putInt(image.colorAdjust.contrast.toBits()); md.update(buffer.array().copyOf())
            buffer.clear(); buffer.putInt(image.colorAdjust.saturation.toBits()); md.update(buffer.array().copyOf())
        }
        return md.digest().take(8).joinToString("") { "%02x".format(it) }
    }

    fun clearAutoSaveState() {
        prefs.edit()
            .remove(KEY_LAST_SAVE_TS)
            .remove(KEY_LAST_CONTENT_HASH)
            .apply()
    }

    fun listBackups(): List<BackupEntry> {
        return autoSaveDir.listFiles()
            ?.filter { it.name.endsWith(".auto_backup.pur") }
            ?.map { file ->
                BackupEntry(
                    file = file,
                    name = file.name.removeSuffix(".auto_backup.pur"),
                    timestamp = file.lastModified(),
                    size = file.length(),
                )
            }
            ?.sortedByDescending { it.timestamp }
            ?: emptyList()
    }

    data class BackupEntry(
        val file: File,
        val name: String,
        val timestamp: Long,
        val size: Long,
    )

    companion object {
        private const val TAG = "AutoSaveManager"
        private const val KEY_LAST_SAVE_TS = "last_auto_save_timestamp"
        private const val KEY_LAST_CONTENT_HASH = "last_content_hash"
        const val DEFAULT_INTERVAL_MS = 5L * 60 * 1000

        /**
         * V_NOW: discrete stops (in MINUTES) the user
         * can pick for the auto-save interval. 13
         * values, irregular spacing at the low end
         * (1 + 5 — the 1-minute stop exists for users
         * who want belt-and-suspenders paranoid
         * backup, the 5-minute stop matches the
         * project's historical default; everything
         * else is 5-minute increments up to the 60-min
         * cap).
         *
         * Order matters. Both the settings sheet's
         * [androidx.compose.material3.Slider] (which
         * uses an `index → value` indirection:
         * `valueRange = 0f..(size-1).toFloat()` +
         * `steps = size-2`) and the reducer
         * ([PockRefViewModel.setAutoSaveIntervalMs]
         * which snaps to the nearest stop) read this
         * array — keep it as the single source of truth
         * so a future "add 90 min / 120 min stops" edit
         * is one line.
         *
         * Not `const val` because [IntArray] is a
         * reference type (Kotlin only allows `const val`
         * for primitives + [String]). The `val` is
         * enough — the array is allocated once at class
         * load and never mutated.
         */
        val INTERVAL_STOPS_MINUTES: IntArray = intArrayOf(
            1, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60,
        )
    }
}
