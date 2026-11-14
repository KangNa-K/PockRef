package com.pockref.app.data

import com.pockref.app.model.PockRefScene
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AutoSaveRecoveryTest {

    private lateinit var testDir: File
    private val codec = PurSceneCodec()

    @Before
    fun setUp() {
        testDir = Files.createTempDirectory("pockref-auto-restore").toFile()
    }

    @After
    fun tearDown() {
        testDir.deleteRecursively()
    }

    @Test
    fun newestCorruptBackup_fallsBackToOlderValidBackup() {
        val olderValid = backup("older", timestamp = 1_000L).also { file ->
            file.outputStream().use { codec.write(PockRefScene(name = "older-valid"), it) }
        }
        val newestCorrupt = backup("newest", timestamp = 2_000L).also {
            it.writeBytes(byteArrayOf(1, 2, 3, 4))
        }

        val restored = loadLatestValidBackup(
            candidates = listOf(entry(newestCorrupt), entry(olderValid)),
            reader = codec::readFromFile,
        )

        assertEquals("older-valid", restored?.scene?.name)
        assertEquals(olderValid, restored?.entry?.file)
    }

    @Test
    fun allCorruptBackups_returnsNull() {
        val first = backup("first", 2_000L).also { it.writeText("bad") }
        val second = backup("second", 1_000L).also { it.writeText("also bad") }

        val restored = loadLatestValidBackup(
            candidates = listOf(entry(first), entry(second)),
            reader = codec::readFromFile,
        )

        assertNull(restored)
    }

    @Test
    fun backupNames_distinguishUnicodeNamesAndDestinationUris() {
        assertNotEquals(backupFileName("画画", null), backupFileName("参考", null))
        assertNotEquals(backupFileName("same", "content://one"), backupFileName("same", "content://two"))
        assertEquals("画画", backupDisplayName(backupFileName("画画", null)))
        assertEquals("old name", backupDisplayName(backupFileName("old name", "content://one")))
        assertEquals("legacy", backupDisplayName("legacy.auto_backup.pur"))
    }

    @Test
    fun skipRequiresSameContentDestinationAndExistingBackup() {
        assertTrue(shouldSkipAutoSave("hash", "hash", "content://one", "content://one", true))
        assertFalse(shouldSkipAutoSave("hash", "hash", "content://two", "content://one", true))
        assertFalse(shouldSkipAutoSave("hash", "hash", "content://one", "content://one", false))
        assertFalse(shouldSkipAutoSave("hash", "hash", "content://one", null, true))
        assertTrue(shouldSkipAutoSave("hash", "hash", null, null, true))
    }

    private fun backup(name: String, timestamp: Long): File =
        File(testDir, "$name.auto_backup.pur").also { it.setLastModified(timestamp) }

    private fun entry(file: File) = AutoSaveManager.BackupEntry(
        file = file,
        name = file.name.removeSuffix(".auto_backup.pur"),
        timestamp = file.lastModified(),
        size = file.length(),
    )
}
