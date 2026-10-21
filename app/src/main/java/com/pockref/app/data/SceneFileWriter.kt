package com.pockref.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * Finishes serialization before opening a destination that may truncate on open.
 * Share one instance between manual saves and auto-save.
 */
internal class SceneFileWriter(private val workDir: File) {
    private val mutex = Mutex()

    suspend fun write(
        encode: (OutputStream) -> Unit,
        openDestination: () -> OutputStream?,
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(workDir.isDirectory || workDir.mkdirs()) { "Unable to create save directory" }
            val staged = File.createTempFile("pockref_save_", ".pur", workDir)
            try {
                FileOutputStream(staged).use { fileOutput ->
                    val buffered = fileOutput.buffered(BUFFER_SIZE)
                    encode(buffered)
                    buffered.flush()
                    fileOutput.fd.sync()
                }
                check(staged.length() > 0L) { "Encoded scene is empty" }
                currentCoroutineContext().ensureActive()

                // Once truncation starts, lifecycle cancellation must not interrupt
                // the copy. A provider failure/process kill still requires recovery.
                withContext(NonCancellable) {
                    staged.inputStream().buffered(BUFFER_SIZE).use { input ->
                        val destination = openDestination() ?: error("Unable to open destination")
                        destination.buffered(BUFFER_SIZE).use { output ->
                            input.copyTo(output, BUFFER_SIZE)
                        }
                    }
                }
            } finally {
                staged.delete()
            }
        }
    }

    private companion object {
        const val BUFFER_SIZE = 256 * 1024
    }
}
