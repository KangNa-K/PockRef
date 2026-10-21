package com.pockref.app.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class PurJpegMetadataTest {
    private fun jpeg(largeMetadata: Boolean = true): ByteArray = ByteArrayOutputStream().apply {
        val out = DataOutputStream(this)
        out.writeShort(0xFFD8)
        if (largeMetadata) {
            repeat(4) {
                out.writeShort(0xFFE1)
                out.writeShort(60002)
                val payload = ByteArray(60000)
                // A nested JPEG thumbnail must not become the source size.
                jpeg(false).copyInto(payload)
                out.write(payload)
            }
        }
        out.writeShort(0xFFC2)
        out.writeShort(11)
        out.writeByte(8)
        out.writeShort(if (largeMetadata) 5600 else 160)
        out.writeShort(if (largeMetadata) 3680 else 100)
        out.write(byteArrayOf(1, 1, 0x11, 0))
        out.writeShort(0xFFD9)
    }.toByteArray()

    @Test fun skipsLargeMetadataAndNestedThumbnail() {
        assertEquals(3680 to 5600, PurFileParser().probeJpegSize(ByteArrayInputStream(jpeg())))
    }

    @Test fun truncatedMetadataDoesNotReturnThumbnailDimensions() {
        assertNull(PurFileParser().probeJpegSize(ByteArrayInputStream(jpeg().copyOf(65536))))
    }

    @Test fun truncatedFrameAndInvalidSegmentAreRejected() {
        val parser = PurFileParser()
        assertNull(parser.probeJpegSize(ByteArrayInputStream(byteArrayOf(-1, -40, -1, -31, 0, 1))))
        assertNull(parser.probeJpegSize(ByteArrayInputStream(jpeg(false).copyOf(8))))
    }

    @Test fun lazyPurParserKeepsDimensionsBeyond64KiB() {
        val image = jpeg()
        val indexOffset = 224 + image.size
        val bytes = ByteBuffer.allocate(indexOffset + 20).apply {
            putInt(8)
            put("1.10".toByteArray(Charsets.UTF_16BE))
            putShort(1)
            putShort(1)
            putLong(indexOffset.toLong())
            position(224)
            put(image)
            putInt(0)
            putLong(224)
            putLong(indexOffset.toLong())
        }.array()
        val file = File.createTempFile("pockref-jpeg-metadata-", ".pur")
        try {
            file.writeBytes(bytes)
            val restored = PurFileParser().parseFromFile(file).images.single()
            assertEquals(3680, restored.intrinsicWidth)
            assertEquals(5600, restored.intrinsicHeight)
            val request = ImageDecodePolicy.requestFor(restored, 0.2f, false)
            assertTrue(request.displayHeight > 256)
        } finally {
            file.delete()
        }
    }

    @Test fun suppliedSceneRetainsAllSixFullResolutionDimensions() {
        val path = System.getenv("POCKREF_REGRESSION_PUR")
        assumeTrue(path != null)
        val scene = PurFileParser().parseFromFile(File(requireNotNull(path)))
        assertEquals(6, scene.images.size)
        scene.images.forEach {
            assertEquals(it.name, 3680, it.intrinsicWidth)
            assertEquals(it.name, 5600, it.intrinsicHeight)
        }
    }
}
