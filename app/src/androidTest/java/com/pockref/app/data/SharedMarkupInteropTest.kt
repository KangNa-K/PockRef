package com.pockref.app.data

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pockref.app.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import android.database.sqlite.SQLiteDatabase
import java.nio.ByteBuffer
import java.nio.ByteOrder
import com.pockref.app.ui.eraseMarkupInk
import com.pockref.app.ui.hitMarkupStroke

@RunWith(AndroidJUnit4::class)
class SharedMarkupInteropTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun codec() = PurSceneCodec().also { it.initCacheDir(context.cacheDir) }
    private fun sample() = PockRefScene(name="Markup interoperability",
        markupStrokes=listOf(
            CanvasMarkupStroke(id="portable-pen",points=listOf(Offset(-40f,20f),Offset(15f,75f),Offset(80f,10f)),color=Color(0x801450A0),opacity=0.6f,lineWidth=3.5f),
            CanvasMarkupStroke(id="portable-rectangle",kind="rectangle",points=listOf(Offset(-120f,-50f),Offset(-30f,10f)),rotation=0.25f,zOrder=1)),
        markupTexts=listOf(CanvasMarkupText(id="portable-text",text="双端编辑",position=Offset(100f,80f),fontSize=18f,rotation=-0.5f,zOrder=2,boxSize=Size2D(160f,80f))))

    @Test fun partialInkEraseSurvivesSaveAndProducesEditableSwiftFixture() {
        val ink = CanvasMarkupStroke(id="eraser-source", points=listOf(Offset(-100f,0f), Offset(100f,0f)), lineWidth=4f)
        val fragments = eraseMarkupInk(ink,Offset(0f,-50f),Offset(0f,50f),16f)
        assertEquals(2, fragments.size)
        val scene = sample().copy(markupStrokes = fragments + sample().markupStrokes.last())
        val output = File(context.getExternalFilesDir(null), "android-partial-erase.pur")
        val writer = codec()
        try {
            output.outputStream().use { writer.writePureRefCompatible(scene,it,context.cacheDir) }
            val reopened = writer.readFromFile(output)
            assertEquals(scene.markupStrokes.map { it.id },reopened.markupStrokes.map { it.id })
            val inkParts = reopened.markupStrokes.filter { it.kind == "pen" }
            assertEquals(2,inkParts.size)
            assertFalse(inkParts.any { hitMarkupStroke(it,Offset.Zero,0f) })
        } finally { writer.release() }
    }

    @Test fun corruptMetadataWithImagesFailsInsteadOfImageOnlyRecovery() {
        val png = java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScL+XQAAAABJRU5ErkJggg==")
        val scene = sample().copy(images=listOf(SceneImage(id="safe-image", name="image.png",
            mimeType="image/png",imageBytes=png,intrinsicWidth=1,intrinsicHeight=1,
            center=Offset.Zero,displaySize=Size2D(40f,40f))))
        val file = File(context.cacheDir,"corrupt-markup-v2.pur")
        val writer = codec()
        try { file.outputStream().use { writer.writePureRefCompatible(scene,it,context.cacheDir) } } finally { writer.release() }
        val bytes = file.readBytes()
        val length = ByteBuffer.wrap(bytes,14,8).order(ByteOrder.BIG_ENDIAN).long.toInt()
        val prefix = bytes.size-length
        val database = File(context.cacheDir,"corrupt-markup.sqlite")
        database.writeBytes(bytes.copyOfRange(length,bytes.size)+bytes.copyOfRange(prefix,length))
        val db = SQLiteDatabase.openDatabase(database.path,null,SQLiteDatabase.OPEN_READWRITE)
        try { db.execSQL("UPDATE pockref_metadata SET json = '{broken'") } finally { db.close() }
        val sqlite = database.readBytes()
        assertEquals(length,sqlite.size)
        file.writeBytes(bytes.copyOfRange(0,prefix)+sqlite.copyOfRange(prefix,length)+sqlite.copyOfRange(0,prefix))
        val reader = codec()
        try {
            assertThrows(Exception::class.java) { reader.readFromFile(file) }
        } finally { reader.release(); database.delete(); file.delete() }
    }

    @Test fun corruptLegacyMetadataWithImagesFailsWhenFixtureAvailable() {
        val source = File(context.getExternalFilesDir(null),"ios-legacy-markup.pur")
        org.junit.Assume.assumeTrue("Generate Swift legacy fixture first",source.exists())
        val bytes = source.readBytes()
        val marker = "POCKREF_META_V1".toByteArray()
        val start = (0..bytes.size-marker.size).first { offset -> marker.indices.all { bytes[offset+it] == marker[it] } }
        bytes[start+marker.size+4] = '!'.code.toByte()
        val file = File(context.cacheDir,"corrupt-ios-legacy.pur").also { it.writeBytes(bytes) }
        val reader = codec()
        try { assertThrows(Exception::class.java) { reader.readFromFile(file) } } finally { reader.release(); file.delete() }
    }

    @Test fun nativeV2TenCyclesKeepEditableObjectsWithoutPreviewDuplicates() {
        var scene = sample()
        repeat(10) { iteration ->
            val file = File(context.cacheDir,"shared-markup-$iteration.pur")
            val writer = codec()
            file.outputStream().use { writer.writePureRefCompatible(scene,it,context.cacheDir) }
            scene = codec().readFromFile(file)
            assertEquals(2,scene.markupStrokes.size)
            assertEquals(1,scene.markupTexts.size)
            assertTrue("Native previews must not become reference images",scene.images.isEmpty())
            assertEquals("portable-rectangle",scene.markupStrokes[1].id)
            assertEquals(0.25f,scene.markupStrokes[1].rotation)
            assertEquals(18f,scene.markupTexts.single().fontSize)
            assertNull(scene.markupCompatibilityWarning)
            file.delete()
        }
        val writer = codec()
        File(context.getExternalFilesDir(null),"android-markup.pur").outputStream().use {
            writer.writePureRefCompatible(scene,it,context.cacheDir)
        }
    }

    @Test fun deletingAllObjectsDoesNotRestoreNativePreview() {
        val file = File(context.cacheDir,"deleted-markup.pur")
        val writer = codec()
        file.outputStream().use { writer.writePureRefCompatible(sample(),it,context.cacheDir) }
        val reader = codec()
        val scene = reader.readFromFile(file).copy(markupStrokes=emptyList(),markupTexts=emptyList())
        file.outputStream().use { reader.writePureRefCompatible(scene,it,context.cacheDir) }
        val empty = codec().readFromFile(file)
        assertTrue(empty.markupStrokes.isEmpty())
        assertTrue(empty.markupTexts.isEmpty())
        assertTrue(empty.images.isEmpty())
    }

    @Test fun readSwiftRoundtripWhenFixtureIsAvailable() {
        val file = File(context.getExternalFilesDir(null),"ios-edited-markup.pur")
        org.junit.Assume.assumeTrue("Run after Swift fixture export",file.exists())
        val scene = codec().readFromFile(file)
        assertEquals(2,scene.markupStrokes.size)
        assertEquals("iOS 已编辑",scene.markupTexts.single().text)
        assertEquals("portable-text",scene.markupTexts.single().id)
        assertTrue(scene.images.isEmpty())
        assertNull(scene.markupCompatibilityWarning)
    }

    @Test fun swiftReturnedPartialEraseKeepsGapAndFragmentIdentities() {
        val directory = context.getExternalFilesDir(null)
        val returned = File(directory,"ios-returned-partial-erase.pur")
        org.junit.Assume.assumeTrue("Run after Swift partial erase roundtrip",returned.exists())
        val reader = codec()
        try {
            val original = reader.readFromFile(File(directory,"android-partial-erase-source.pur"))
            val scene = reader.readFromFile(returned)
            val parts = scene.markupStrokes.filter { it.kind == "pen" }
            assertEquals(2,parts.size)
            assertEquals(original.markupStrokes.map { it.id }.toSet(),scene.markupStrokes.map { it.id }.toSet())
            assertTrue(parts.all { stroke -> stroke.points.all { kotlin.math.abs(it.x) >= 17.9f } })
            assertTrue(parts.any { it.points.all { p -> p.x < 0 } })
            assertTrue(parts.any { it.points.all { p -> p.x > 0 } })
            assertNull(scene.markupCompatibilityWarning)
        } finally { reader.release() }
    }

    @Test fun editIndependentSwiftV2AndLegacyFixturesWhenAvailable() {
        val directory = context.getExternalFilesDir(null)
        val source = File(directory, "ios-original-markup.pur")
        org.junit.Assume.assumeTrue("Generate Swift source fixtures first", source.exists())
        for (name in listOf("ios-original-markup.pur", "ios-legacy-markup.pur", "ios-legacy-markup-only.pur")) {
            val reader = codec()
            try {
                val scene = reader.readFromFile(File(directory, name))
                assertEquals(2, scene.markupStrokes.size)
                assertEquals(1, scene.markupTexts.size)
                assertEquals(if (name.contains("only")) 0 else 1, scene.images.size)
                assertNull(scene.markupCompatibilityWarning)
                assertEquals("ios-source-pen", scene.markupStrokes.first().id)
                val edited = scene.copy(
                    markupTexts = scene.markupTexts.map { it.copy(text = "Android 已编辑") },
                    markupStrokes = scene.markupStrokes.map { if (it.kind == "rectangle") it.copy(rotation = 0.75f) else it })
                val result = File(directory, if (name == "ios-original-markup.pur") "android-edited-ios.pur" else "edited-$name")
                result.outputStream().use { reader.writePureRefCompatible(edited, it, context.cacheDir) }
                val reopenedReader = codec()
                try {
                    val reopened = reopenedReader.readFromFile(result)
                    assertEquals("Android 已编辑", reopened.markupTexts.single().text)
                    assertEquals(scene.images.size, reopened.images.size)
                    assertEquals(0.75f, reopened.markupStrokes.single { it.kind == "rectangle" }.rotation)
                } finally { reopenedReader.release() }
            } finally { reader.release() }
        }
    }
}
