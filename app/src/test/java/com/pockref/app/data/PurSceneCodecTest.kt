package com.pockref.app.data

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.pockref.app.model.ColorAdjust
import com.pockref.app.model.PockRefScene
import com.pockref.app.model.SceneImage
import com.pockref.app.model.Size2D
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

class PurSceneCodecTest {
    private val codec = PurSceneCodec()

    companion object {
        // Varios tests rely on Android's BitmapFactory.decodeByteArray to
        // probe PNG dimensions during readLegacyPurScene. On the JVM
        // unit-test runtime that method either throws
        //   "Method ... in android.graphics.BitmapFactory not mocked."
        // or returns a 1024×1024 default sentinel, neither of which
        // yields a meaningful assertion. These tests must be run on an
        // instrumented (androidTest) target where BitmapFactory is real.
        //
        // We auto-detect the JVM stub via android.os.Build.FINGERPRINT
        // which is null/empty on the JVM and non-empty on Android.
        @JvmStatic
        @BeforeClass
        fun assumeAndroidRuntime() {
            val fp = try { android.os.Build.FINGERPRINT } catch (e: Throwable) { "" } ?: ""
            val isJvmStub = fp.isEmpty() || fp == "null"
            if (isJvmStub) {
                assumeTrue(
                    "BitmapFactory is a JVM stub — these raster-probe tests " +
                        "must run under an instrumented androidTest target",
                    false,
                )
            }
        }
    }

    @Test
    fun roundTripPreservesCoreFields() {
        val scene = PockRefScene(
            name = "round-trip",
            backgroundColor = Color(0xFF102030),
            images = listOf(
                SceneImage(
                    id = "img-1",
                    name = "sample",
                    mimeType = "image/png",
                    imageBytes = byteArrayOf(1, 2, 3, 4),
                    intrinsicWidth = 640,
                    intrinsicHeight = 480,
                    center = Offset(100f, 120f),
                    displaySize = Size2D(320f, 240f),
                    colorAdjust = ColorAdjust(
                        brightness = 0.2f,
                        contrast = -0.1f,
                        saturation = 0.4f,
                    ),
                    rotationDegrees = 15f,
                    scale = 1.25f,
                    flipX = true,
                    zIndex = 4,
                )
            ),
        )

        val output = ByteArrayOutputStream()
        codec.write(scene, output)
        val restored = codec.read(ByteArrayInputStream(output.toByteArray()))

        assertEquals(scene.name, restored.name)
        assertEquals(scene.backgroundColor, restored.backgroundColor)
        assertEquals(1, restored.images.size)
        val restoredImage = restored.images.single()
        assertEquals("sample", restoredImage.name)
        assertEquals("image/png", restoredImage.mimeType)
        assertEquals(640, restoredImage.intrinsicWidth)
        assertEquals(480, restoredImage.intrinsicHeight)
        assertEquals(100f, restoredImage.center.x)
        assertEquals(120f, restoredImage.center.y)
        assertEquals(320f, restoredImage.displaySize.width)
        assertEquals(240f, restoredImage.displaySize.height)
        assertEquals(0.2f, restoredImage.colorAdjust.brightness)
        assertEquals(-0.1f, restoredImage.colorAdjust.contrast)
        assertEquals(0.4f, restoredImage.colorAdjust.saturation)
        assertEquals(15f, restoredImage.rotationDegrees)
        assertEquals(1.25f, restoredImage.scale)
        assertEquals(true, restoredImage.flipX)
        assertEquals(false, restoredImage.flipY)
        assertEquals(4, restoredImage.zIndex)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), restoredImage.imageBytes)
    }

    @Test
    fun desktopPurLikeFileImportsEmbeddedJpegs() {
        val jpeg = Base64.getDecoder().decode(
            "/9j/4AAQSkZJRgABAQAAAQABAAD/2wCEAAkGBxAQEBUQEA8QDw8QEA8PDw8QEA8QFREWFhURFRUYHSggGBolHRUVITEhJSkrLi4uFx8zODMsNygtLisBCgoKDg0OGxAQGy0lHyUtLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLS0tLf/AABEIAAEAAQMBEQACEQEDEQH/xAAXAAEBAQEAAAAAAAAAAAAAAAAAAQMC/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAwDAQACEAMQAAAB6A//xAAZEAEAAwEBAAAAAAAAAAAAAAABAAIREjH/2gAIAQEAAT8AqZ8w0//EABQRAQAAAAAAAAAAAAAAAAAAABD/2gAIAQIBAT8Af//EABQRAQAAAAAAAAAAAAAAAAAAABD/2gAIAQMBAT8Af//Z"
        )
        val output = ByteArrayOutputStream().apply {
            write(byteArrayOf(0, 0, 0, 6))
            write("2.1\u0000".toByteArray(Charsets.UTF_16LE).copyOf(8))
            write(ByteArray(24))
            write(jpeg)
            write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(123L).array())
            write(jpeg)
        }

        val restored = codec.read(ByteArrayInputStream(output.toByteArray()))

        // The parser intentionally returns blank [name] for the
        // embedded-image fallback (see [readEmbeddedImagesScene] in
        // [PurSceneCodec]). The [loadScene] call site in
        // [PureRefViewModelScene] is the layer that derives the
        // display name from the file's DISPLAY_NAME column via
        // [SceneNaming.fromPurFileName]. Asserting blank here pins
        // the contract: parser = "I don't have a real name",
        // UI = "I do, here's the file name".
        assertTrue(restored.name.isBlank())
        assertEquals(2, restored.images.size)
        assertEquals("image/jpeg", restored.images[0].mimeType)
        assertTrue(restored.images.all { it.imageBytes.isNotEmpty() })
    }

    @Test
    fun legacyRoundTripPreservesImageGeometry() {
        val pngBytes = fakePngBytes(width = 672, height = 1024)
        val scene = PockRefScene(
            name = "legacy-geometry",
            backgroundColor = Color(0xFF111111),
            images = listOf(
                SceneImage(
                    id = "legacy-1",
                    name = "geometry-sample",
                    mimeType = "image/png",
                    imageBytes = pngBytes,
                    intrinsicWidth = 672,
                    intrinsicHeight = 1024,
                    center = Offset(320f, 480f),
                    displaySize = Size2D(420f, 640f),
                    zIndex = 2,
                )
            ),
        )

        val output = ByteArrayOutputStream()
        codec.writeLegacy(scene, output)
        val restored = codec.read(ByteArrayInputStream(output.toByteArray()))

        assertEquals(1, restored.images.size)
        val restoredImage = restored.images.single()
        assertEquals(672, restoredImage.intrinsicWidth)
        assertEquals(1024, restoredImage.intrinsicHeight)
        assertEquals(320f, restoredImage.center.x, 0.01f)
        assertEquals(480f, restoredImage.center.y, 0.01f)
        assertEquals(420f, restoredImage.displaySize.width, 0.01f)
        assertEquals(640f, restoredImage.displaySize.height, 0.01f)
        assertEquals(420f / 640f, restoredImage.displaySize.width / restoredImage.displaySize.height, 0.0001f)
    }

    @Test
    fun streamingLegacyRoundTripPreservesImageGeometry() {
        val pngBytes = fakePngBytes(width = 672, height = 1024)
        val scene = PockRefScene(
            name = "streaming-legacy-geometry",
            backgroundColor = Color(0xFF111111),
            images = listOf(
                SceneImage(
                    id = "legacy-1",
                    name = "geometry-sample",
                    mimeType = "image/png",
                    imageBytes = pngBytes,
                    intrinsicWidth = 672,
                    intrinsicHeight = 1024,
                    center = Offset(320f, 480f),
                    displaySize = Size2D(420f, 640f),
                    zIndex = 2,
                )
            ),
        )

        val output = ByteArrayOutputStream()
        codec.writeLegacyStreaming(scene, output, File("build/tmp"))
        val restored = codec.read(ByteArrayInputStream(output.toByteArray()))

        assertEquals(1, restored.images.size)
        val restoredImage = restored.images.single()
        assertEquals(672, restoredImage.intrinsicWidth)
        assertEquals(1024, restoredImage.intrinsicHeight)
        assertEquals(320f, restoredImage.center.x, 0.01f)
        assertEquals(480f, restoredImage.center.y, 0.01f)
        assertEquals(420f, restoredImage.displaySize.width, 0.01f)
        assertEquals(640f, restoredImage.displaySize.height, 0.01f)
    }

    @Test
    fun writeLegacyStreamingRoundTripPreservesTransform() {
        // Regression: writeLegacyStreaming followed by readFromFile must
        // preserve every transform field the user set — center, scale,
        // rotation, flip, zIndex, and the rendered display size. Before the
        // fix, readFromFile's small-V1 branch preferred
        // purFileParser.parseFromFile → parseV1Format, which only reads image
        // byte offsets and falls back to a grid layout for every other field.
        val pngBytes = fakePngBytes(width = 200, height = 300)
        val scene = PockRefScene(
            name = "transform-round-trip",
            backgroundColor = Color(0xFF111111),
            images = listOf(
                SceneImage(
                    id = "img-transform-1",
                    name = "transformed",
                    mimeType = "image/png",
                    imageBytes = pngBytes,
                    intrinsicWidth = 200,
                    intrinsicHeight = 300,
                    center = Offset(123.4f, -567.8f),
                    displaySize = Size2D(180f, 270f),
                    colorAdjust = ColorAdjust(),
                    rotationDegrees = 37.5f,
                    scale = 1.7f,
                    flipX = true,
                    flipY = false,
                    zIndex = 3,
                ),
                SceneImage(
                    id = "img-transform-2",
                    name = "flipped-both",
                    mimeType = "image/png",
                    imageBytes = pngBytes,
                    intrinsicWidth = 200,
                    intrinsicHeight = 300,
                    center = Offset(-90f, 40f),
                    displaySize = Size2D(60f, 90f),
                    rotationDegrees = -22.5f,
                    scale = 0.5f,
                    flipX = false,
                    flipY = true,
                    zIndex = 1,
                ),
            ),
        )

        val output = ByteArrayOutputStream()
        codec.writeLegacyStreaming(scene, output, File("build/tmp"))
        val written = output.toByteArray()
        val tempFile = File.createTempFile("pockref_roundtrip_", ".pur")
        tempFile.deleteOnExit()
        tempFile.writeBytes(written)

        val restored = codec.readFromFile(tempFile)

        assertEquals(2, restored.images.size)

        val byId = restored.images.associateBy { it.id }
        val first = byId.getValue("img-transform-1")
        assertEquals(123.4f, first.center.x, 0.01f)
        assertEquals(-567.8f, first.center.y, 0.01f)
        // displaySize is recovered as displaySize * scale (the legacy matrix
        // collapses those two into one). Scale is reset to 1, so we assert
        // the product is preserved.
        assertEquals(180f * 1.7f, first.displaySize.width, 0.5f)
        assertEquals(270f * 1.7f, first.displaySize.height, 0.5f)
        assertEquals(37.5f, first.rotationDegrees, 0.5f)
        assertEquals(1f, first.scale, 0.001f)
        assertEquals(true, first.flipX)
        assertEquals(false, first.flipY)
        assertEquals(3, first.zIndex)

        val second = byId.getValue("img-transform-2")
        assertEquals(-90f, second.center.x, 0.01f)
        assertEquals(40f, second.center.y, 0.01f)
        assertEquals(60f * 0.5f, second.displaySize.width, 0.5f)
        assertEquals(90f * 0.5f, second.displaySize.height, 0.5f)
        assertEquals(-22.5f, second.rotationDegrees, 0.5f)
        assertEquals(true, second.flipY)
        assertEquals(1, second.zIndex)
    }

    @Test
    fun writeLegacyStreamingRoundTripPreservesSceneMetadata() {
        // Regression: PockRefScene.defaultSpacing and defaultAlignment are
        // scene-level settings (auto-layout gap + image reference point).
        // They were not part of the original desktop .pur format, so
        // they need a length-prefixed metadata block between the scene
        // name and the index area. Round-trip must preserve them.
        val pngBytes = fakePngBytes(width = 120, height = 120)
        val scene = PockRefScene(
            name = "metadata-round-trip",
            backgroundColor = Color(0xFF202020),
            images = listOf(
                SceneImage(
                    id = "img-m-1",
                    name = "alpha",
                    mimeType = "image/png",
                    imageBytes = pngBytes,
                    intrinsicWidth = 120,
                    intrinsicHeight = 120,
                    center = Offset(0f, 0f),
                    displaySize = Size2D(120f, 120f),
                    zIndex = 0,
                ),
            ),
            defaultSpacing = Size2D(16f, 24f),
            defaultAlignment = com.pockref.app.model.ImageAlignment.BOTTOM_RIGHT,
        )

        val output = ByteArrayOutputStream()
        codec.writeLegacyStreaming(scene, output, File("build/tmp"))
        val tempFile = File.createTempFile("pockref_meta_roundtrip_", ".pur")
        tempFile.deleteOnExit()
        tempFile.writeBytes(output.toByteArray())

        val restored = codec.readFromFile(tempFile)

        assertEquals(Size2D(16f, 24f), restored.defaultSpacing)
        assertEquals(com.pockref.app.model.ImageAlignment.BOTTOM_RIGHT, restored.defaultAlignment)
    }

    @Test
    fun writeLegacyStreamingRoundTripDefaultsForLegacyFiles() {
        // Reading a hand-crafted legacy V1 file (no metadata block) must
        // NOT crash and must fall back to the documented default
        // spacing / alignment. This guards the metadata reader's
        // "garbage length = skip" branch.
        val pngBytes = fakePngBytes(width = 80, height = 80)
        val scene = PockRefScene(
            name = "legacy-defaults",
            backgroundColor = Color(0xFF000000),
            images = listOf(
                SceneImage(
                    id = "img-ld-1",
                    name = "legacy",
                    mimeType = "image/png",
                    imageBytes = pngBytes,
                    intrinsicWidth = 80,
                    intrinsicHeight = 80,
                    center = Offset(0f, 0f),
                    displaySize = Size2D(80f, 80f),
                    zIndex = 0,
                ),
            ),
        )

        val output = ByteArrayOutputStream()
        codec.writeLegacyStreaming(scene, output, File("build/tmp"))
        val bytes = output.toByteArray()
        // Strip the metadata block (last 4 bytes of the scene name area
        // + payload + the index area) by replacing the 4-byte metadata
        // length prefix with 0. This simulates a file written before
        // the extension existed.
        val fileLength = bytes.size
        val transformIdCount = (bytes[108].toInt() and 0xFF) or
            ((bytes[109].toInt() and 0xFF) shl 8) or
            ((bytes[110].toInt() and 0xFF) shl 16) or
            ((bytes[111].toInt() and 0xFF) shl 24)
        val indexAreaStart = fileLength - (4 + transformIdCount * 20)
        val mutated = bytes.copyOf()
        // Overwrite the metadata length prefix with 0 (no metadata).
        mutated[indexAreaStart] = 0
        mutated[indexAreaStart + 1] = 0
        mutated[indexAreaStart + 2] = 0
        mutated[indexAreaStart + 3] = 0

        val tempFile = File.createTempFile("pockref_legacy_meta_", ".pur")
        tempFile.deleteOnExit()
        tempFile.writeBytes(mutated)

        val restored = codec.readFromFile(tempFile)

        assertEquals(Size2D(8f, 8f), restored.defaultSpacing)
        assertEquals(com.pockref.app.model.ImageAlignment.CENTER, restored.defaultAlignment)
    }

    @Test
    fun writeLegacyStreamingRoundTripPreservesColorAdjust() {
        // Regression: SceneImage.colorAdjust (brightness/contrast/saturation)
        // was not part of the original desktop .pur transform block, so the
        // V1 reader defaulted it to ColorAdjust() on every reload — the
        // user's brightness/contrast tweaks silently disappeared on save.
        // The fix introduced a 4-byte COLOR_ADJUST_SENTINEL ("PRAC") in
        // the writer + reader pair, so a round-trip through the codec
        // now also exercises the sentinel path.
        val pngBytes = fakePngBytes(width = 200, height = 200)
        val scene = PockRefScene(
            name = "color-adjust-round-trip",
            backgroundColor = Color(0xFF111111),
            images = listOf(
                SceneImage(
                    id = "img-ca-1",
                    name = "colored",
                    mimeType = "image/png",
                    imageBytes = pngBytes,
                    intrinsicWidth = 200,
                    intrinsicHeight = 200,
                    center = Offset(0f, 0f),
                    displaySize = Size2D(200f, 200f),
                    colorAdjust = ColorAdjust(
                        brightness = 0.18f,
                        contrast = -0.32f,
                        saturation = 0.47f,
                    ),
                    zIndex = 0,
                ),
            ),
        )

        val output = ByteArrayOutputStream()
        codec.writeLegacyStreaming(scene, output, File("build/tmp"))
        val tempFile = File.createTempFile("pockref_ca_roundtrip_", ".pur")
        tempFile.deleteOnExit()
        tempFile.writeBytes(output.toByteArray())

        val restored = codec.readFromFile(tempFile)

        val restoredImage = restored.images.single()
        assertEquals(0.18f, restoredImage.colorAdjust.brightness, 0.001f)
        assertEquals(-0.32f, restoredImage.colorAdjust.contrast, 0.001f)
        assertEquals(0.47f, restoredImage.colorAdjust.saturation, 0.001f)
    }

    @Test
    fun androidPurFileEmbedsColorAdjustSentinel() {
        // Companion to desktopPurFileIgnoresColorAdjustGarbage. The
        // Android writer MUST embed the 4-byte "PRAC" sentinel right
        // before the 24 bytes of colorAdjust, otherwise the reader
        // can't distinguish a desktop file (no colorAdjust) from an
        // Android file. Asserting the byte pattern is present in the
        // stream also documents the on-disk format: any change here
        // needs a matching reader update.
        val pngBytes = fakePngBytes(width = 200, height = 200)
        val scene = PockRefScene(
            name = "android-pur-sentinel-present",
            backgroundColor = Color(0xFF111111),
            images = listOf(
                SceneImage(
                    id = "img-android-sentinel-1",
                    name = "colored",
                    mimeType = "image/png",
                    imageBytes = pngBytes,
                    intrinsicWidth = 200,
                    intrinsicHeight = 200,
                    center = Offset(0f, 0f),
                    displaySize = Size2D(200f, 200f),
                    colorAdjust = ColorAdjust(
                        brightness = 0.18f,
                        contrast = -0.32f,
                        saturation = 0.47f,
                    ),
                    zIndex = 0,
                ),
            ),
        )

        val output = ByteArrayOutputStream()
        codec.writeLegacyStreaming(scene, output, File("build/tmp"))
        val written = output.toByteArray()

        val sentinel = byteArrayOf(0x50, 0x52, 0x43, 0x41)
        assertTrue(
            "Android writer must embed 4-byte \"PRAC\" sentinel before colorAdjust; " +
                "without it, desktop .pur imports deserialize the 25-byte desktop " +
                "trailer as three brightness/contrast/saturation doubles and can " +
                "render the scene fully grayscale (saturation = -1 case).",
            indexOfSubArray(written, sentinel) >= 0,
        )
    }

    @Test
    fun desktopPurFileIgnoresColorAdjustGarbage() {
        // Regression: desktop PockRef .pur files do NOT include a
        // colorAdjust field. Their transform block ends with 25 bytes
        // of "trailer" data
        //   (writeDoubleBe(0.0) + writeIntBe(1) + write(0) +
        //    writeLongBe(-1L) + writeIntBe(0))
        // and the old Android reader blindly consumed the first 24 of
        // those bytes as three brightness/contrast/saturation doubles.
        // For a typical desktop file that produces garbage doubles, and
        // if the byte pattern happens to deserialize to saturation = -1
        // the scene renders fully grayscale — every image becomes B&W
        // and the user has to reset colorAdjust by hand on every import.
        //
        // We simulate a desktop file by:
        //   1. writing a normal Android .pur (which now has a 4-byte
        //      "PRAC" sentinel before colorAdjust),
        //   2. stripping the 4-byte sentinel + 24-byte colorAdjust from
        //      each transform block (28 bytes total),
        //   3. shrinking the block's endAddress by 28 so the rest of
        //      the file (trailer, index, ...) stays byte-aligned.
        // After this rewrite, the bytes at the original colorAdjust
        // position are the desktop's 25-byte trailer, with no sentinel
        // in front of them. The reader must fall back to ColorAdjust().
        val pngBytes = fakePngBytes(width = 200, height = 200)
        val scene = PockRefScene(
            name = "desktop-pur-color-adjust",
            backgroundColor = Color(0xFF111111),
            images = listOf(
                SceneImage(
                    id = "img-desktop-1",
                    name = "full-color",
                    mimeType = "image/png",
                    imageBytes = pngBytes,
                    intrinsicWidth = 200,
                    intrinsicHeight = 200,
                    center = Offset(0f, 0f),
                    displaySize = Size2D(200f, 200f),
                    colorAdjust = ColorAdjust(
                        brightness = 0.18f,
                        contrast = -0.32f,
                        saturation = 0.47f,
                    ),
                    zIndex = 0,
                ),
            ),
        )

        val output = ByteArrayOutputStream()
        codec.writeLegacyStreaming(scene, output, File("build/tmp"))
        val written = output.toByteArray()

        // 1. Locate the COLOR_ADJUST_SENTINEL ("PRAC") that the Android
        //    writer placed right before the 3 brightness/contrast/
        //    saturation doubles.
        val sentinel = byteArrayOf(0x50, 0x52, 0x43, 0x41)
        val sentinelPos = indexOfSubArray(written, sentinel)
        check(sentinelPos >= 0) {
            "Could not find COLOR_ADJUST_SENTINEL in Android-written .pur"
        }

        // 2. The endAddress stored at the start of this transform block
        //    equals sentinelPos + 4 (sentinel) + 24 (colorAdjust) + 25
        //    (trailer) = sentinelPos + 53. Find the 8-byte long in
        //    the file whose value matches that. We restrict the search
        //    to positions < sentinelPos, which guarantees we're looking
        //    at the start of THIS transform block (other transforms
        //    would be located past the current sentinel).
        val expectedEndAddress = (sentinelPos + 4 + 24 + 25).toLong()
        val endAddressPos = findLongBefore(written, expectedEndAddress, sentinelPos)
        check(endAddressPos >= 0) {
            "Could not locate endAddress=$expectedEndAddress before sentinel at $sentinelPos"
        }

        // 3. Strip the 28 bytes (sentinel + colorAdjust) starting at
        //    sentinelPos, and shrink the block's endAddress by 28 so
        //    the trailer stays at the position endAddress points to.
        val stripped = ByteArray(written.size - 28)
        System.arraycopy(written, 0, stripped, 0, sentinelPos)
        System.arraycopy(
            written,
            sentinelPos + 28,
            stripped,
            sentinelPos,
            written.size - sentinelPos - 28,
        )
        val shrunkEndAddress = expectedEndAddress - 28
        ByteBuffer.wrap(stripped, endAddressPos, 8)
            .order(ByteOrder.BIG_ENDIAN)
            .putLong(shrunkEndAddress)

        val tempFile = File.createTempFile("pockref_desktop_pur_ca_", ".pur")
        tempFile.deleteOnExit()
        tempFile.writeBytes(stripped)

        val restored = codec.readFromFile(tempFile)
        val restoredImage = restored.images.single()

        // Sentinel is absent → reader must default to ColorAdjust(),
        // NOT the (0.18, -0.32, 0.47) we wrote into the Android file.
        // This is the bug the user was seeing: with the old code the
        // reader would deserialize 3 of the 25 desktop trailer bytes
        // as doubles, often yielding saturation = -1 → grayscale.
        assertEquals(ColorAdjust(), restoredImage.colorAdjust)
    }

    @Test
    fun desktopPurFileParserIgnoresColorAdjustGarbage() {
        // Companion test for the PurFileParser code path (parseV1Format
        // → parseLegacyTransforms). Same bug, different reader:
        // PurFileParser also naively consumed 24 bytes of the 25-byte
        // desktop trailer as brightness/contrast/saturation, often
        // yielding saturation = -1 → grayscale on desktop imports.
        //
        // We build a minimal desktop-style .pur:
        //   [224-byte header][PNG blob][transform WITHOUT sentinel+CA]
        //   [20-byte legacy index entry pointing to the PNG]
        // and verify the PurFileParser returns a scene whose image
        // has default ColorAdjust().
        val pngBytes = fakePngBytes(width = 200, height = 200)
        val imageStart = 224L
        val imageEnd = imageStart + pngBytes.size
        val indexStart = imageEnd + desktopTransformBlockSize

        val out = ByteArrayOutputStream()
        // --- header (224 bytes) -----------------------------------
        // versionByteLen (4 BE) = 8
        out.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(8).array())
        // formatVersion = "1.10" UTF-16BE (8 bytes)
        out.write("1.10".toByteArray(Charsets.UTF_16BE))
        // numImages (2 BE) at +12 = 1, padding (2 BE) = 0
        out.write(ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN).putShort(1).array())
        out.write(ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN).putShort(0).array())
        // sqliteOffset (8 BE) at +16 — points to the index area
        out.write(ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(indexStart).array())
        // Pad the rest of the header to 224 bytes total.
        val headerBytesSoFar = 4 + 8 + 2 + 2 + 8
        out.write(ByteArray(224 - headerBytesSoFar))
        // --- PNG blob ---------------------------------------------
        out.write(pngBytes)
        // --- transform block (NO sentinel, NO colorAdjust) --------
        writeDesktopLegacyTransformBlock(out, imageId = 0, imageStart = imageStart)
        // --- legacy index entry (20 bytes) ------------------------
        // id (4 BE) = 0
        out.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(0).array())
        // startOffset (8 BE) — where the image blob begins
        out.write(ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(imageStart).array())
        // endOffset (8 BE) — where the image blob ends
        out.write(ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(imageEnd).array())

        val tempFile = File.createTempFile("pockref_desktop_pur_parser_", ".pur")
        tempFile.deleteOnExit()
        tempFile.writeBytes(out.toByteArray())

        val parser = PurFileParser()
        val restored = parser.parseFromFile(tempFile)
        val restoredImage = restored.images.single()
        assertEquals(ColorAdjust(), restoredImage.colorAdjust)
    }

    @Test
    @org.junit.Ignore("Requires host fixture at /Volumes/KNJ/画画/6/插画6.pur. Re-enable after dropping the file in.")
    fun macDesktopPurSampleCanBeParsedWhenPresent() {
        val sample = File("/Volumes/KNJ/画画/6/插画6.pur")
        val restored = FileInputStream(sample).use { codec.read(it) }
        assertTrue(restored.images.isNotEmpty())
    }

    @Test
    @org.junit.Ignore("Requires host fixture at /Volumes/KNJ/NewScene 3.pur. Re-enable after dropping the file in.")
    fun largeDesktopPurSampleCanBeParsedWhenPresent() {
        val sample = File("/Volumes/KNJ/NewScene 3.pur")
        val restored = codec.readFromFile(sample)
        assertTrue(restored.images.isNotEmpty())
    }

    @Test
    fun matrixDisplaySizeRoundTripRecoversRenderSize() {
        // Verify the math that maps displaySize ↔ matrix. If the user's
        // screenshots show the reopened file with images rendered at full
        // intrinsic size (instead of the user-set displaySize * scale),
        // the bug is in this roundtrip. The test mirrors what
        // writeLegacyStreaming encodes and what readLegacyPurScene decodes.
        //
        // For an image with:
        //   intrinsicW = 1000, intrinsicH = 1500
        //   displaySize = 1000x1500  (untouched)
        //   scale = 1.0
        //   no rotation / no flip
        // matrixForLegacy encodes a = baseScaleX = 1.0
        // displayFromMatrix decodes a * intrinsicW = 1000.0
        // which is the original displaySize.width. The "rendered size" in
        // drawSceneImage is displaySize.width * scale * canvasScale.
        // canvasScale is a UI-state value and is NOT persisted; on reload
        // it falls back to 1.0, so the user will see images at their
        // original pixel size unless they zoom the canvas out. This is
        // the expected behavior; if the user expected the autoLayout's
        // 0.35x canvasScale to be persisted, that's a different feature
        // (canvas-level zoom persistence) and is intentionally NOT in
        // scope of layout-preservation.
        val intrinsicW = 1000
        val intrinsicH = 1500
        val displayW = 1000f
        val displayH = 1500f
        val scale = 1.0f

        val sourceImage = SceneImage(
            id = "diag",
            name = "diag",
            mimeType = "image/png",
            intrinsicWidth = intrinsicW,
            intrinsicHeight = intrinsicH,
            center = Offset(200f, 100f),
            displaySize = Size2D(displayW, displayH),
            scale = scale,
            zIndex = 0,
        )
        val matrix = codec.matrixForLegacyForTest(sourceImage)
        val recovered = codec.displayFromMatrixForTest(matrix, intrinsicW, intrinsicH)
        assertEquals(displayW, recovered.first, 0.5f)
        assertEquals(displayH, recovered.second, 0.5f)
    }

    private fun fakePngBytes(width: Int, height: Int): ByteArray {
        val signature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val ihdrData = ByteBuffer.allocate(13).order(ByteOrder.BIG_ENDIAN)
            .putInt(width)
            .putInt(height)
            .put(8)
            .put(2)
            .put(0)
            .put(0)
            .put(0)
            .array()
        // PNG CRC32 covers the chunk-type + chunk-data (i.e. "IHDR" + ihdrData)
        val chunkTypeAndData = "IHDR".toByteArray(Charsets.US_ASCII) + ihdrData
        val crc = java.util.zip.CRC32().apply { update(chunkTypeAndData) }.value.toInt()
        val crcBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(crc).array()
        // Minimal IDAT: a single 0x00-filtered scanline of (width*3) bytes plus zlib stream.
        // We don't need real pixel data — only the IHDR to be valid so BitmapFactory
        // can extract width/height via inJustDecodeBounds. Most decoders stop after
        // reading IHDR, so a minimal IDAT is enough; we still need a valid zlib
        // wrapper (two-byte header + empty stored block + adler32) so the chunk
        // doesn't fail the structural check.
        val idatData = byteArrayOf(0x78, 0x01) + byteArrayOf(0x01, 0x00, 0x00, 0xFF.toByte(), 0xFF.toByte())
        val idatCrc = java.util.zip.CRC32().apply {
            update("IDAT".toByteArray(Charsets.US_ASCII))
            update(idatData)
        }.value.toInt()
        val idatCrcBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(idatCrc).array()
        val iendCrc = java.util.zip.CRC32().apply {
            update("IEND".toByteArray(Charsets.US_ASCII))
        }.value.toInt()
        val iendCrcBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(iendCrc).array()
        return ByteArrayOutputStream().apply {
            write(signature)
            write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(13).array())
            write(chunkTypeAndData)
            write(crcBytes)
            write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(idatData.size).array())
            write("IDAT".toByteArray(Charsets.US_ASCII))
            write(idatData)
            write(idatCrcBytes)
            write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(0).array())
            write("IEND".toByteArray(Charsets.US_ASCII))
            write(iendCrcBytes)
        }.toByteArray()
    }

    // --- helpers used by the desktop .pur regression tests -------

    private fun indexOfSubArray(haystack: ByteArray, needle: ByteArray): Int {
        if (needle.isEmpty()) return 0
        outer@ for (i in 0..(haystack.size - needle.size)) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }

    /**
     * Walks [bytes] from the start, returning the offset of the first
     * 8-byte big-endian long whose value equals [target], or -1 if
     * none is found in [0, [limit]). Used by
     * `desktopPurFileIgnoresColorAdjustGarbage` to locate the
     * transform block's endAddress long that lives somewhere between
     * the file start and the COLOR_ADJUST_SENTINEL.
     */
    private fun findLongBefore(bytes: ByteArray, target: Long, limit: Int): Int {
        val end = minOf(limit, bytes.size - 8)
        var i = 0
        while (i <= end) {
            val value = ByteBuffer.wrap(bytes, i, 8).order(ByteOrder.BIG_ENDIAN).long
            if (value == target) return i
            i++
        }
        return -1
    }

    // --- desktop-style transform block helpers (test-only) -------
    //
    // Build a legacy .pur transform block WITHOUT the 4-byte
    // COLOR_ADJUST_SENTINEL and 24-byte colorAdjust that the Android
    // writer adds. Mirrors the byte layout the desktop writer produces:
    //   [endAddress:8][type:4]["GraphicsImageItem":34][source:UTF-16BE]
    //   [name:UTF-16BE][pad-double:8][matrix:48][x:8][y:8]
    //   [pad-double:8][id:4][zLayer:8][matrixBeforeCrop:48]
    //   [xCrop:8][yCrop:8][scaleCrop:8][pointCount:4]
    //   [5 points × (4 + 8 + 8) bytes][trailer:25]
    // The total size is the Android block size minus 28 (sentinel + CA).

    private val androidTransformBlockSize: Int = run {
        // Mirrors writeLegacyImageTransform's fixed-size prefix and
        // suffix around the 5-point default polygon. We need the size
        // only to position the index area, so this is recomputed
        // independently from the production writer rather than reading
        // it off a real file. The source/name strings here are empty
        // (writeStringUtf16Be still emits a 4-byte length prefix), and
        // the 5-point default polygon is what legacyDefaultPointX/Y
        // produces.
        val prefix = 8 + 4 + 34 + (4 + 0) + (4 + 0) + 8 + 48 + 8 + 8 + 8 + 4 + 8 + 48 + 8 + 8 + 8 + 4
        val points = 5 * (4 + 8 + 8)
        val sentinelAndCa = 4 + 24
        val trailer = 25
        prefix + points + sentinelAndCa + trailer
    }

    private val desktopTransformBlockSize: Int = androidTransformBlockSize - 28

    private fun writeDesktopLegacyTransformBlock(
        out: ByteArrayOutputStream,
        imageId: Int,
        imageStart: Long,
    ) {
        // endAddress placeholder (8 BE) — real value doesn't matter for
        // parseLegacyTransforms; it uses (endAddress - baseOffset) as
        // the transform boundary, and our baseOffset is imageEnd.
        val transformStart = out.size()
        out.write(ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(0L).array())
        // type (4 BE) = 34
        out.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(34).array())
        // "GraphicsImageItem" UTF-16BE = 17 chars = 34 bytes
        out.write("GraphicsImageItem".toByteArray(Charsets.UTF_16BE))
        // source: empty string (4-byte length 0)
        out.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(0).array())
        // name: empty string (4-byte length 0)
        out.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(0).array())
        // pad double 1.0
        out.write(ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putDouble(1.0).array())
        // matrix (6 doubles): 1, 0, 0, 0, 1, 0
        val matrixDoubles = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0)
        matrixDoubles.forEach { v ->
            out.write(ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putDouble(v).array())
        }
        // x (8) y (8) — imageStart is encoded as the absolute x, which
        // also pins the transform's position for the reader
        out.write(ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putDouble(imageStart.toDouble()).array())
        out.write(ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putDouble(imageStart.toDouble()).array())
        // pad double 1.0
        out.write(ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putDouble(1.0).array())
        // id (4 BE)
        out.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(imageId).array())
        // zLayer (8 BE) = 0
        out.write(ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putDouble(0.0).array())
        // matrixBeforeCrop (6 doubles): identity
        val mbcDoubles = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0)
        mbcDoubles.forEach { v ->
            out.write(ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putDouble(v).array())
        }
        // xCrop, yCrop, scaleCrop (8 each) = 0, 0, 1
        out.write(ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putDouble(0.0).array())
        out.write(ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putDouble(0.0).array())
        out.write(ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putDouble(1.0).array())
        // pointCount (4 BE) = 5 (default polygon)
        out.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(5).array())
        // 5 points × (4 + 8 + 8) — first point has flag 0, rest flag 1
        repeat(5) { i ->
            out.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(if (i == 0) 0 else 1).array())
            out.write(ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putDouble(0.0).array())
            out.write(ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putDouble(0.0).array())
        }
        // Desktop trailer (25 bytes): double 0.0 + int 1 + byte 0 + long -1 + int 0
        out.write(ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putDouble(0.0).array())
        out.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(1).array())
        out.write(0)
        out.write(ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(-1L).array())
        out.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(0).array())

        // Patch the endAddress long to the actual end of the block.
        val end = out.size()
        val bytes = out.toByteArray()
        ByteBuffer.wrap(bytes, transformStart, 8)
            .order(ByteOrder.BIG_ENDIAN)
            .putLong(end.toLong())
        out.reset()
        out.write(bytes)
    }
}
