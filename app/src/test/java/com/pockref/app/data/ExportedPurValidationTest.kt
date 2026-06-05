package com.pockref.app.data

import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import java.io.File
import java.io.FileInputStream

class ExportedPurValidationTest {
    @Test
    @Ignore("Requires the system property -Dpockref.exportedPurPath=/path/to/exported.pur at test time. " +
        "Re-enable by passing that property to gradlew test, or rewrite to use a fixture under " +
        "src/test/resources/.")
    fun validatesExportedPurFromPath() {
        val path = System.getProperty("pockref.exportedPurPath").orEmpty()
        val exported = File(path)
        require(exported.isFile) { "Exported .pur not found: $path" }

        val scene = FileInputStream(exported).use { PurSceneCodec().read(it) }
        assertTrue("Expected imported scene to contain images", scene.images.isNotEmpty())
        scene.images.forEach { image ->
            assertTrue("Image ${image.name} should have bytes", image.imageBytes.isNotEmpty())
            assertTrue("Image ${image.name} width should be > 0", image.intrinsicWidth > 0)
            assertTrue("Image ${image.name} height should be > 0", image.intrinsicHeight > 0)
            assertTrue("Image ${image.name} display width should be > 0", image.displaySize.width > 0f)
            assertTrue("Image ${image.name} display height should be > 0", image.displaySize.height > 0f)
        }
    }
}
