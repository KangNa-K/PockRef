package com.pockref.app.data

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class OriginalImageImportTest {

    @Test
    fun preservesOriginalBytesDimensionsAndMimeType() {
        val sourceBytes = byteArrayOf(1, 3, 5, 7, 9)

        val decoded = decodeOriginalImage(
            imageBytes = sourceBytes,
            declaredMimeType = "image/jpeg",
            boundsReader = { ImageBounds(6000, 4000, "image/jpeg") },
        )

        assertSame(sourceBytes, decoded!!.bytes)
        assertEquals(6000, decoded.width)
        assertEquals(4000, decoded.height)
        assertEquals("image/jpeg", decoded.mimeType)
    }

    @Test
    fun usesDetectedMimeWhenProviderMimeIsGeneric() {
        val sourceBytes = byteArrayOf(2, 4, 6, 8)

        val decoded = decodeOriginalImage(
            imageBytes = sourceBytes,
            declaredMimeType = "application/octet-stream",
            boundsReader = { ImageBounds(3200, 1800, "image/webp") },
        )

        assertSame(sourceBytes, decoded!!.bytes)
        assertEquals("image/webp", decoded.mimeType)
    }

    @Test
    fun rejectsBytesWithoutValidImageBounds() {
        val decoded = decodeOriginalImage(
            imageBytes = byteArrayOf(0, 1, 2),
            declaredMimeType = "image/png",
            boundsReader = { ImageBounds(0, -1, null) },
        )

        assertNull(decoded)
    }

    @Test
    fun sceneImageFromOriginalImport_keepsOriginalPixelsForDragAndDrop() {
        val sourceBytes = byteArrayOf(9, 7, 5, 3)
        val imported = OriginalImageImport(
            bytes = sourceBytes,
            width = 6000,
            height = 4000,
            mimeType = "image/jpeg",
        )

        val image = sceneImageFromOriginalImport(
            name = "reference.jpg",
            imported = imported,
            center = Offset(100f, 200f),
            displayWidth = 480f,
            zIndex = 3,
        )

        assertSame(sourceBytes, image.imageBytes)
        assertEquals(6000, image.intrinsicWidth)
        assertEquals(4000, image.intrinsicHeight)
        assertEquals(480f, image.displaySize.width, 0f)
        assertEquals(320f, image.displaySize.height, 0f)
    }
}
