package com.pockref.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PureRefNoteTextTest {
    @Test
    fun nativeFontSizeUsesPixelsAndConvertsPoints() {
        assertEquals(20f, pureRefNoteFontSize("<p style='font-size:20px'>Text</p>"), 0.001f)
        assertEquals(32f, pureRefNoteFontSize("<p style='font-size:24pt'>Text</p>"), 0.001f)
        assertEquals(16f, pureRefNoteFontSize("Text"), 0.001f)
        assertEquals(16f, pureRefNoteFontSize("<p style='font-size:0px'>Text</p>"), 0.001f)
    }

    @Test
    fun qtContentFontOverridesDefaultBodyFont() {
        assertEquals(40f, pureRefNoteFontSize("<body style='font-size:9pt'><p><span style='font-size:30pt'>Text</span></p></body>"), 0.001f)
    }

    @Test
    fun pureRefNotePlainTextStripsQtCssAndHtml() {
        val raw = """
            <html><head><style>
            p, li { white-space: pre-wrap; }
            li.unchecked::marker { content: "\2610"; }
            li.checked::marker { content: "\2612"; }
            </style></head><body><p>第一行</p><p>第二行</p></body></html>
        """.trimIndent()

        val text = pureRefNotePlainText(raw)

        assertEquals("第一行\n第二行", text)
        assertFalse(text.contains("white-space"))
        assertFalse(text.contains("unchecked"))
    }
}
