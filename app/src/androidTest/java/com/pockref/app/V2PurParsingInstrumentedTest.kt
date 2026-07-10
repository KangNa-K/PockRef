package com.pockref.app

import androidx.compose.ui.graphics.toArgb
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pockref.app.data.PurFileParser
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class V2PurParsingInstrumentedTest {

    @Test
    @Ignore("Requires host fixture at /data/local/tmp/test_v2.pur or /Volumes/KNJ/画画/6/插画6.pur. " +
        "Re-enable after pushing the file via `adb push test_v2.pur /data/local/tmp/`, " +
        "or rewrite to self-build a V2 .pur via PurSceneCodec in this test's TemporaryFolder.")
    fun parsesV2MetadataAndFlipFlags() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cacheFile = File(context.cacheDir, "test_v2.pur")
        if (!cacheFile.exists() || cacheFile.length() == 0L) {
            val hostSources = listOf(
                "/data/local/tmp/test_v2.pur",
                "/Volumes/KNJ/画画/6/插画6.pur",
            )
            val sourcePath = hostSources.firstOrNull {
                runCatching { File(it).exists() && File(it).length() > 0L }.getOrDefault(false)
            } ?: return
            if (sourcePath.startsWith("/data/local/tmp/")) {
                runCatching {
                    Runtime.getRuntime().exec(
                        arrayOf("cp", sourcePath, cacheFile.absolutePath)
                    ).waitFor()
                }
                if (!cacheFile.exists() || cacheFile.length() == 0L) {
                    File(sourcePath).inputStream().use { input ->
                        cacheFile.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            } else {
                File(sourcePath).inputStream().use { input ->
                    cacheFile.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
        require(cacheFile.exists() && cacheFile.length() > 0L) {
            "Cache file not prepared: ${cacheFile.absolutePath}"
        }

        val parser = PurFileParser()
        val scene = parser.parseFromFile(cacheFile)

        android.util.Log.i("V2Test", "sceneName='${scene.name}'")
        android.util.Log.i("V2Test", "backgroundColor=0x${scene.backgroundColor.toArgb().toUInt().toString(16)}")
        android.util.Log.i("V2Test", "imageCount=${scene.images.size}")
        android.util.Log.i("V2Test", "noteTextCount=${scene.markupTexts.count { it.id.startsWith("pur-v2-note-") }}")
        android.util.Log.i("V2Test", "drawingImageCount=${scene.images.count { it.id.startsWith("pur-v2-drawing-") }}")

        // Behaviour assertions (previously missing — this test only logged).
        assertTrue("Expected the parsed scene to have a name", scene.name.isNotBlank())
        assertTrue("Expected at least one image in a sample V2 .pur", scene.images.isNotEmpty())
        scene.images.forEach { image ->
            assertTrue("Image ${image.name} should have non-zero width", image.intrinsicWidth > 0)
            assertTrue("Image ${image.name} should have non-zero height", image.intrinsicHeight > 0)
            assertTrue("Image ${image.name} display width should be > 0", image.displaySize.width > 0f)
            assertTrue("Image ${image.name} display height should be > 0", image.displaySize.height > 0f)
        }
    }
}
