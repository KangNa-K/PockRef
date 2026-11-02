package com.pockref.app.data

import androidx.compose.ui.geometry.Offset
import com.pockref.app.model.PockRefScene
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SharedMarkupCodecTest {
    @Test fun nativeNotePaddingSurvivesSaveReload() {
        val text = com.pockref.app.model.CanvasMarkupText(text = "WDAD", position = Offset(120f, -80f),
            fontSize = 44f, boxSize = com.pockref.app.model.Size2D(164f, 168f), textPadding = 32f)
        val root = SharedMarkupCodec.metadata(emptyList(), listOf(text), null)
        val restored = checkNotNull(SharedMarkupCodec.decode(root)).texts.single()
        assertEquals(text.position, restored.position)
        assertEquals(text.boxSize, restored.boxSize)
        assertEquals(text.fontSize, restored.fontSize, 0.001f)
        assertEquals(32f, restored.textPadding, 0.001f)
    }

    private fun fixture(): JSONObject = JSONObject().put("annotationDocument", JSONObject(
        checkNotNull(javaClass.getResource("/markup/ios-v1.json")).readText()))

    @Test fun realSwiftFixturePreservesBoundsTextAndAlpha() {
        val value = checkNotNull(SharedMarkupCodec.decode(fixture()))
        assertNull(value.warning)
        assertEquals(listOf(Offset(-120f,-50f),Offset(-30f,10f)),value.strokes[1].points)
        assertTrue(value.strokes[1].locked)
        assertEquals(0.25f,value.strokes[1].rotation)
        assertEquals(128f/255,value.strokes[0].color.alpha,0.001f)
        assertEquals(0.6f,value.strokes[0].opacity,0.001f)
        assertEquals(18f,value.texts.single().fontSize)
        assertEquals(Offset(100f,80f),value.texts.single().position)
    }

    @Test fun tenCyclesPreserveStableIdsGeometryAndUnknownFields() {
        var root = fixture()
        root.getJSONObject("annotationDocument").getJSONArray("items").getJSONObject(0).put("futureBrush","preserve-me")
        repeat(10) {
            val decoded = checkNotNull(SharedMarkupCodec.decode(root))
            root = JSONObject().put("annotationDocument",SharedMarkupCodec.document(decoded.strokes,decoded.texts,root.getJSONObject("annotationDocument")))
        }
        val decoded = checkNotNull(SharedMarkupCodec.decode(root))
        assertEquals(listOf("ios-pen","ios-rectangle"),decoded.strokes.map { it.id })
        assertEquals(18f,decoded.texts.single().fontSize)
        assertEquals("preserve-me",JSONObject(decoded.strokes.first().sourceJson!!).getString("futureBrush"))
    }

    @Test fun editsInvalidateButRetainNativeArchivesAndDeletionStaysEmpty() {
        val root = fixture().put("markupLayer","original-archive")
        root.getJSONObject("annotationDocument").put("includesPencilKitContent",true)
        val empty = SharedMarkupCodec.metadata(emptyList(),emptyList(),root.toString())
        assertEquals("original-archive",empty.getString("markupLayer"))
        assertTrue(empty.getJSONObject("annotationDocument").getBoolean("nativeArchivesAreStale"))
        assertTrue(checkNotNull(SharedMarkupCodec.decode(empty)).strokes.isEmpty())
    }

    @Test fun unchangedDocumentDoesNotInvalidateItsNativeArchive() {
        val root = fixture().put("markupLayer","original-archive")
        root.getJSONObject("annotationDocument").put("includesPencilKitContent",true)
        val decoded = checkNotNull(SharedMarkupCodec.decode(root))
        val saved = SharedMarkupCodec.metadata(decoded.strokes,decoded.texts,root.toString())
        assertFalse(saved.getJSONObject("annotationDocument").optBoolean("nativeArchivesAreStale"))
    }

    @Test fun unmigratedPencilAndUnknownObjectsAreReadOnlyAndRetained() {
        val root = fixture().put("markupLayer","opaque")
        assertNotNull(checkNotNull(SharedMarkupCodec.decode(root)).warning)
        val unknown = JSONObject().put("id","unknown").put("kind","future-shape").put("payload","keep")
        root.getJSONObject("annotationDocument").getJSONArray("items").put(unknown)
        val saved = SharedMarkupCodec.metadata(emptyList(),emptyList(),root.toString())
        assertEquals(4,saved.getJSONObject("annotationDocument").getJSONArray("items").length())
    }

    @Test(expected = IllegalArgumentException::class) fun duplicateIdsAreRejected() {
        val root = fixture(); val items = root.getJSONObject("annotationDocument").getJSONArray("items")
        items.put(items.getJSONObject(0))
        SharedMarkupCodec.decode(root)
    }

    @Test(expected = IllegalArgumentException::class) fun hugeCoordinatesAreRejected() {
        val root = fixture()
        root.getJSONObject("annotationDocument").getJSONArray("items").getJSONObject(0)
            .put("points",JSONArray("[[1e30,0],[1,2]]"))
        SharedMarkupCodec.decode(root)
    }

    @Test fun futureSchemaIsReadOnlyRatherThanSilentlyDropped() {
        val root = fixture(); root.getJSONObject("annotationDocument").put("schemaVersion",999)
        assertNotNull(checkNotNull(SharedMarkupCodec.decode(root)).warning)
    }

    @Test(expected = IllegalArgumentException::class) fun negativeTextBoundsAreRejected() {
        val root = fixture()
        root.getJSONObject("annotationDocument").getJSONArray("items").getJSONObject(2)
            .put("bounds", JSONArray("[[0,0],[-50,40]]"))
        SharedMarkupCodec.decode(root)
    }

    @Test(expected = IllegalArgumentException::class) fun invalidDocumentTypeIsNotTreatedAsMissing() {
        SharedMarkupCodec.decode(JSONObject().put("annotationDocument", "corrupt"))
    }

    @Test(expected = IllegalArgumentException::class) fun hugeLineWidthIsRejected() {
        val root = fixture()
        root.getJSONObject("annotationDocument").getJSONArray("items").getJSONObject(0).put("lineWidth", 1e20)
        SharedMarkupCodec.decode(root)
    }

    @Test fun customV7PreservesEditableShapesAndText() {
        val decoded = checkNotNull(SharedMarkupCodec.decode(fixture()))
        val scene = PockRefScene(markupStrokes=decoded.strokes,markupTexts=decoded.texts)
        val file = File.createTempFile("markup-v7",".pur")
        try {
            val codec = PurSceneCodec()
            file.outputStream().use { codec.write(scene,it) }
            val reopened = codec.readFromFile(file)
            assertEquals(scene.markupStrokes.map { it.id },reopened.markupStrokes.map { it.id })
            assertEquals("rectangle",reopened.markupStrokes[1].kind)
            assertEquals(0.25f,reopened.markupStrokes[1].rotation)
            assertEquals(scene.markupTexts.single().text,reopened.markupTexts.single().text)
            assertEquals(18f,reopened.markupTexts.single().fontSize)
        } finally { file.delete() }
    }
}
