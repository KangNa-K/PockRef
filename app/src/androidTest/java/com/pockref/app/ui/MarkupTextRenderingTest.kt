package com.pockref.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pockref.app.model.CanvasMarkupText
import com.pockref.app.model.Size2D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MarkupTextRenderingTest {
    @Test
    fun centeredImportedBoxAnchorsGlyphsAtItsTopLeftAfterRotation() {
        val bitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888)
        try {
            val note = CanvasMarkupText(text = "Test", position = Offset(160f, 120f),
                fontSize = 20f, boxSize = Size2D(160f, 80f), rotation = (Math.PI / 2).toFloat())
            SharedMarkupRenderer.drawText(Canvas(bitmap), note)
            val painted = mutableListOf<Pair<Int, Int>>()
            for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                if (AndroidColor.alpha(bitmap.getPixel(x, y)) > 0) painted += x to y
            }
            assertTrue(painted.isNotEmpty())
            assertTrue("Rotated text must start at the rotated box corner, not at the item center",
                painted.all { (x, y) -> x in 170..200 && y in 40..95 })
        } finally { bitmap.recycle() }
    }

    private val text = CanvasMarkupText(
        text = "文本\nText",
        position = Offset(160f, 120f),
        fontSize = 32f,
        color = Color.Red,
        boxSize = Size2D(240f, 160f),
    )

    @Test
    fun exportedTextLeavesBackgroundAndWhitespaceTransparent() {
        val bitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888)
        try {
            SharedMarkupRenderer.draw(Canvas(bitmap), emptyList(), listOf(text))
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val ink = pixels.filter { AndroidColor.alpha(it) > 0 }
            assertTrue("Text must still be visible", ink.isNotEmpty())
            assertTrue("Only red glyphs may be painted, not a white background",
                ink.all { AndroidColor.red(it) == 255 && AndroidColor.green(it) == 0 && AndroidColor.blue(it) == 0 })
            assertEquals(AndroidColor.TRANSPARENT, bitmap.getPixel(45, 45))
            assertEquals(AndroidColor.TRANSPARENT, bitmap.getPixel(200, 120))
        } finally { bitmap.recycle() }
    }

    @Test
    fun rotatedTranslucentTextPreservesUnderlyingCanvas() {
        val bitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(AndroidColor.BLUE)
            SharedMarkupRenderer.drawText(Canvas(bitmap), text.copy(rotation = 0.3f, opacity = 0.5f))
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            assertTrue("Text must be composited over the canvas", pixels.any { it != AndroidColor.BLUE })
            assertTrue("No white fill may be composited over the blue canvas",
                pixels.all { AndroidColor.green(it) == 0 })
            assertEquals(AndroidColor.BLUE, bitmap.getPixel(160, 160))
        } finally { bitmap.recycle() }
    }
}
