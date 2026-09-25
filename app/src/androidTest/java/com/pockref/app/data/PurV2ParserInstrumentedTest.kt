package com.pockref.app.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.compose.ui.geometry.Offset
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(AndroidJUnit4::class)
class PurV2ParserInstrumentedTest {

    @Test
    fun loadsReferencePureRefV2FileWhenPathIsProvided() {
        val args = InstrumentationRegistry.getArguments()
        val purPath = args.getString("purV2Path").orEmpty()
        assumeTrue("Pass -e purV2Path /path/on/device/港1.pur to run this check", purPath.isNotBlank())

        val file = File(purPath)
        assumeTrue("Reference .pur file is not readable on device: $purPath", file.canRead())

        val scene = PurSceneCodec().readFromFile(file)

        assertEquals(2, scene.version)
        assertTrue("v2 sample should load images", scene.images.size >= 143)
        assertTrue(
            "v2 source images must remain file-backed to avoid loading the whole PUR into RAM",
            scene.images.filter { it.id.startsWith("pur-v2-image-") }
                .all { it.imageBytes.isEmpty() && it.imageByteOffset >= 0 && it.imageByteLength > 0 },
        )
        assertTrue("v2 notes should be imported as editable markup text", scene.markupTexts.any { it.id.startsWith("pur-v2-note-") })
        assertTrue("v2 transforms should produce non-origin positions", scene.images.any { it.center != Offset.Zero })
        assertTrue("v2 transforms should preserve display sizes", scene.images.any { it.displaySize.width > 1f && it.displaySize.height > 1f })
    }

    @Test
    fun oversizedV2NoteIsImportedAsEditableMarkupText() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val purFile = File(context.cacheDir, "oversized_v2_note.pur")
        buildV2PurWithNote(
            purFile = purFile,
            noteWidth = 20_000.0,
            noteHeight = 20_000.0,
        )

        val scene = PurSceneCodec().readFromFile(purFile)
        val note = scene.markupTexts.singleOrNull { it.id == "pur-v2-note-1" }

        assertNotNull("oversized V2 note should not be dropped during import", note)
        note!!
        assertEquals("Large note", note.text)
        assertTrue("oversized note should keep a readable font size", note.fontSize > 0f)
    }

    private fun buildV2PurWithNote(
        purFile: File,
        noteWidth: Double,
        noteHeight: Double,
    ) {
        val dbFile = File(purFile.parentFile, "oversized_v2_note.sqlite")
        dbFile.delete()
        purFile.delete()

        val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        try {
            db.execSQL("CREATE TABLE metadata(last_load_path TEXT)")
            db.execSQL("CREATE TABLE items(id INTEGER PRIMARY KEY, opacity REAL, z REAL, locked INTEGER, transform TEXT)")
            db.execSQL("CREATE TABLE images(id INTEGER PRIMARY KEY, width INTEGER, height INTEGER, format TEXT, data BLOB)")
            db.execSQL("CREATE TABLE items_images(id INTEGER PRIMARY KEY, image INTEGER, image_transform TEXT)")
            db.execSQL("CREATE TABLE items_notes(id INTEGER PRIMARY KEY, text TEXT, fixed_size TEXT, background_color TEXT)")
            db.execSQL("CREATE TABLE items_drawings(id INTEGER PRIMARY KEY, strokes TEXT)")
            db.execSQL("INSERT INTO metadata(last_load_path) VALUES('/tmp/oversized_v2_note.pur')")
            db.insert("items", null, ContentValues().apply {
                put("id", 1L)
                put("opacity", 1.0)
                put("z", 1.0)
                put("locked", 0)
            })
            db.insert("items_notes", null, ContentValues().apply {
                put("id", 1L)
                put("text", "Large note")
                put("fixed_size", v2SizePayload(noteWidth, noteHeight).toByteString())
                put("background_color", "#FFFFFFFF")
            })
        } finally {
            db.close()
        }

        val dbBytes = dbFile.readBytes()
        val prefixSize = 224
        require(dbBytes.size > prefixSize * 2) {
            "SQLite fixture is unexpectedly small: ${dbBytes.size}"
        }

        val header = ByteArray(prefixSize)
        val versionBytes = "2.0".toByteArray(Charsets.UTF_16BE)
        ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(versionBytes.size)
            put(versionBytes)
            position(14)
            putLong(dbBytes.size.toLong())
        }

        purFile.outputStream().use { output ->
            output.write(header)
            output.write(dbBytes, prefixSize, dbBytes.size - prefixSize)
            output.write(dbBytes, 0, prefixSize)
        }
        dbFile.delete()
    }

    private fun v2SizePayload(width: Double, height: Double): ByteArray =
        ByteBuffer.allocate(21).order(ByteOrder.BIG_ENDIAN).apply {
            position(5)
            putDouble(width)
            putDouble(height)
        }.array()

    private fun ByteArray.toByteString(): String =
        buildString(size) {
            this@toByteString.forEach { append((it.toInt() and 0xFF).toChar()) }
        }
}
