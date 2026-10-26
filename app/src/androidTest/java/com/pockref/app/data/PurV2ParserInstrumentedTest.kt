package com.pockref.app.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pockref.app.model.ColorAdjust
import com.pockref.app.model.CropRect
import com.pockref.app.model.PockRefScene
import com.pockref.app.model.SceneGroup
import com.pockref.app.model.SceneImage
import com.pockref.app.model.Size2D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.io.ByteArrayOutputStream
import java.util.Base64

@RunWith(AndroidJUnit4::class)
class PurV2ParserInstrumentedTest {

    @Test
    fun nativeWriterRoundTripsGroupsAndPersistsItemsGroupsRows() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val png = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScL+XQAAAABJRU5ErkJggg==",
        )
        val scene = PockRefScene(
            name = "Android V2 groups",
            backgroundColor = Color(0xFF203040),
            backgroundColorIsPersisted = true,
            groups = listOf(
                SceneGroup(
                    id = "android-group-id",
                    name = "Moodboard",
                    imageIds = listOf("first", "second"),
                    backgroundColor = Color(0xFF336699),
                    backgroundOpacity = 0.3f,
                    movesAsUnit = false,
                    positionLocked = true,
                ),
            ),
            images = listOf(
                SceneImage(
                    id = "first", name = "first.png", mimeType = "image/png", imageBytes = png,
                    intrinsicWidth = 1, intrinsicHeight = 1,
                    center = Offset(120f, -40f), displaySize = Size2D(80f, 60f),
                    colorAdjust = ColorAdjust(brightness = 0.1f, contrast = -0.2f, saturation = -1f),
                    posterizeLevels = 8, rotationDegrees = 15f, flipX = true,
                    zIndex = 1, locked = true, cropRect = CropRect(0.1f, 0.2f, 0.9f, 0.8f),
                    showsOriginalResolution = true,
                ),
                SceneImage(
                    id = "second", name = "second.png", mimeType = "image/png", imageBytes = png,
                    intrinsicWidth = 1, intrinsicHeight = 1,
                    center = Offset(-50f, 30f), displaySize = Size2D(32f, 24f), zIndex = 2,
                ),
            ),
        )
        val file = File(context.cacheDir, "native_group_writer.pur")
        val codec = PurSceneCodec().also { it.initCacheDir(context.cacheDir) }
        try {
            file.outputStream().use { codec.writePureRefCompatible(scene, it, context.cacheDir) }
            val bytes = file.readBytes()
            assertEquals("2.1", PurFileParser().parseHeader(bytes).formatVersion)

            val restoredDatabase = File(context.cacheDir, "native_group_writer.sqlite")
            val sqliteOffset = ByteBuffer.wrap(bytes, 14, 8).order(ByteOrder.BIG_ENDIAN).long.toInt()
            val prefixLength = bytes.size - sqliteOffset
            ByteArrayOutputStream().use { sqliteBytes ->
                sqliteBytes.write(bytes, sqliteOffset, prefixLength)
                sqliteBytes.write(bytes, prefixLength, sqliteOffset - prefixLength)
                restoredDatabase.writeBytes(sqliteBytes.toByteArray())
            }
            val db = SQLiteDatabase.openDatabase(restoredDatabase.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            try {
                db.rawQuery("SELECT COUNT(*) FROM items_groups", null).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(1, cursor.getInt(0))
                }
                db.rawQuery("SELECT parent FROM items WHERE id = 1", null).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(3L, cursor.getLong(0))
                }
                db.rawQuery("SELECT lock_mode FROM items_groups", null).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(0, cursor.getInt(0))
                }
                db.rawQuery("SELECT locked FROM items WHERE id = 3", null).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(1, cursor.getInt(0))
                }
                db.rawQuery("SELECT length(thumbnail) FROM metadata WHERE id = 0", null).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertTrue("V2 metadata thumbnail should be populated", cursor.getInt(0) > 0)
                }
            } finally {
                db.close()
                restoredDatabase.delete()
            }

            val restored = codec.readFromFile(file)
            assertEquals("Android V2 groups", restored.name)
            assertEquals(Color(0xFF203040), restored.backgroundColor)
            val group = restored.groups.single()
            assertEquals("android-group-id", group.id)
            assertEquals("Moodboard", group.name)
            assertEquals(setOf("pur-v2-image-1", "pur-v2-image-2"), group.imageIds.toSet())
            assertEquals(Color(0xFF336699), group.backgroundColor)
            assertEquals(0.3f, group.backgroundOpacity, 0.0001f)
            assertTrue(!group.movesAsUnit)
            assertTrue(group.positionLocked)
            val first = restored.images.first { it.name == "first.png" }
            assertEquals(8, first.posterizeLevels)
            assertEquals(-1f, first.colorAdjust.saturation, 0.0001f)
            assertEquals(CropRect(0.1f, 0.2f, 0.9f, 0.8f), first.cropRect)
            assertTrue(first.showsOriginalResolution)
        } finally {
            codec.release()
            file.delete()
        }
    }

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

    @Test
    fun nativeNoteKeepsCenteredBoxAndItemTransform() {
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "transformed_note.pur")
        buildV2PurWithNote(file, 400.0, 200.0, transformed = true)
        val codec = PurSceneCodec()
        try {
            val note = codec.readFromFile(file).markupTexts.single()
            assertEquals(Offset(120f, -80f), note.position)
            assertEquals(Size2D(800f, 400f), note.boxSize)
            assertEquals(40f, note.fontSize, 0.001f)
            assertEquals((Math.PI / 2).toFloat(), note.rotation, 0.001f)
            assertEquals(0.4f, note.opacity, 0.001f)
            assertTrue(note.locked)
            assertEquals(7, note.zOrder)
        } finally { codec.release(); file.delete() }
    }

    @Test
    fun nativeNoteIncludesNestedParentTransforms() {
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "grouped_note.pur")
        buildV2PurWithNote(file, 400.0, 200.0, transformed = true, grouped = true)
        val codec = PurSceneCodec()
        try {
            val note = codec.readFromFile(file).markupTexts.single()
            assertEquals(Offset(670f, 660f), note.position)
            assertEquals(Size2D(1600f, 800f), note.boxSize)
            assertEquals(80f, note.fontSize, 0.001f)
        } finally { codec.release(); file.delete() }
    }

    @Test
    fun autoSizedNoteWithoutParentColumnKeepsTransformedLayout() {
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "auto_sized_note.pur")
        buildV2PurWithNote(file, -1.0, -1.0, transformed = true, hasParent = false)
        val codec = PurSceneCodec()
        try {
            val note = codec.readFromFile(file).markupTexts.single()
            assertEquals(Offset(120f, -80f), note.position)
            assertEquals(40f, note.fontSize, 0.001f)
            assertTrue(note.boxSize!!.width > 1f)
            assertTrue(note.boxSize!!.height > 1f)
        } finally { codec.release(); file.delete() }
    }

    @Test
    fun fixedWidthWithAutomaticHeightKeepsWrappingAndScale() {
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "fixed_width_note.pur")
        buildV2PurWithNote(file, 40.0, -1.0, transformed = true)
        val codec = PurSceneCodec()
        try {
            val note = codec.readFromFile(file).markupTexts.single()
            assertEquals(Offset(120f, -80f), note.position)
            assertEquals(80f, note.boxSize!!.width, 0.001f)
            assertEquals(40f, note.fontSize, 0.001f)
            assertTrue("Automatic height must include wrapped lines", note.boxSize!!.height > 80f)
        } finally { codec.release(); file.delete() }
    }

    @Test
    fun desktopReferenceNotesKeepNativeSizeWhenPathIsProvided() {
        val path = InstrumentationRegistry.getArguments().getString("nativeNoteSizePath").orEmpty()
        assumeTrue("Provide nativeNoteSizePath to check the desktop reference", path.isNotBlank())
        val codec = PurSceneCodec()
        try {
            val notes = codec.readFromFile(File(path)).markupTexts
            val note = notes.single { it.id == "pur-v2-note-44" }
            val nativeScale = 34.97693646768842f
            assertEquals(82f * nativeScale, note.boxSize!!.width, 0.01f)
            assertEquals(22f * nativeScale, note.fontSize, 0.01f)
            assertEquals(Offset(-10772.896f, -10667.966f), note.position)
            assertTrue("WDAD must retain its two-line desktop layout", note.boxSize!!.height > note.fontSize * 2)
        } finally { codec.release() }
    }

    @Test
    fun automaticWidthWithFixedHeightKeepsHeightAndScale() {
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "fixed_height_note.pur")
        buildV2PurWithNote(file, -1.0, 200.0, transformed = true)
        val codec = PurSceneCodec()
        try {
            val note = codec.readFromFile(file).markupTexts.single()
            assertEquals(400f, note.boxSize!!.height, 0.001f)
            assertTrue(note.boxSize!!.width > 1f)
            assertEquals(40f, note.fontSize, 0.001f)
        } finally { codec.release(); file.delete() }
    }

    private fun buildV2PurWithNote(
        purFile: File,
        noteWidth: Double,
        noteHeight: Double,
        transformed: Boolean = false,
        grouped: Boolean = false,
        hasParent: Boolean = true,
    ) {
        val dbFile = File(purFile.parentFile, "oversized_v2_note.sqlite")
        dbFile.delete()
        purFile.delete()

        val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        try {
            db.execSQL("CREATE TABLE metadata(last_load_path TEXT)")
            val parentColumn = if (hasParent) ", parent INTEGER" else ""
            db.execSQL("CREATE TABLE items(id INTEGER PRIMARY KEY, opacity REAL, z REAL, locked INTEGER, transform TEXT$parentColumn)")
            db.execSQL("CREATE TABLE images(id INTEGER PRIMARY KEY, width INTEGER, height INTEGER, format TEXT, data BLOB)")
            db.execSQL("CREATE TABLE items_images(id INTEGER PRIMARY KEY, image INTEGER, image_transform TEXT)")
            db.execSQL("CREATE TABLE items_notes(id INTEGER PRIMARY KEY, text TEXT, fixed_size TEXT, background_color TEXT)")
            db.execSQL("CREATE TABLE items_drawings(id INTEGER PRIMARY KEY, strokes TEXT)")
            db.execSQL("INSERT INTO metadata(last_load_path) VALUES('/tmp/oversized_v2_note.pur')")
            db.insert("items", null, ContentValues().apply {
                put("id", 1L)
                put("opacity", if (transformed) 0.4 else 1.0)
                put("z", if (transformed) 7.0 else 1.0)
                put("locked", if (transformed) 1 else 0)
                if (hasParent) put("parent", if (grouped) 2L else -1L)
                if (transformed) put("transform", v2TransformPayload(0.0, 2.0, -2.0, 0.0, 120.0, -80.0))
            })
            if (grouped) {
                db.insertOrThrow("items", null, ContentValues().apply {
                    put("id", 2L); put("parent", 3L)
                    put("transform", v2TransformPayload(2.0, 0.0, 0.0, 2.0, 30.0, 40.0))
                })
                db.insertOrThrow("items", null, ContentValues().apply {
                    put("id", 3L); put("parent", -1L)
                    put("transform", v2TransformPayload(1.0, 0.0, 0.0, 1.0, 400.0, 780.0))
                })
            }
            db.insert("items_notes", null, ContentValues().apply {
                put("id", 1L)
                put("text", if (transformed) "<p style=\"font-size:20px\">Large note</p>" else "Large note")
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

    private fun v2TransformPayload(a: Double, b: Double, c: Double, d: Double, x: Double, y: Double): ByteArray =
        ByteBuffer.allocate(77).order(ByteOrder.BIG_ENDIAN).apply {
            position(5)
            listOf(a, b, 0.0, c, d, 0.0, x, y, 1.0).forEach { putDouble(it) }
        }.array()

    private fun ByteArray.toByteString(): String =
        buildString(size) {
            this@toByteString.forEach { append((it.toInt() and 0xFF).toChar()) }
        }
}
