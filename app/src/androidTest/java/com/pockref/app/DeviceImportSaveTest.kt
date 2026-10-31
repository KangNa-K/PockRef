package com.pockref.app

import android.os.Environment
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DeviceImportSaveTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private lateinit var device: UiDevice

    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    @Test
    fun importImagesAndSavePurFileToDownloads() {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val inputFiles = TEST_FILE_NAMES.map { File(downloadDir, it) }
        inputFiles.forEach { file ->
            assumeTrue(
                "Test fixture missing on device: ${file.absolutePath}. " +
                "Skipping this test; push fixture files to emulator/device if desired.",
                file.exists()
            )
        }
        val outputFile = File(downloadDir, "device-import-save-test.pur")
        if (outputFile.exists()) outputFile.delete()

        composeRule.onAllNodesWithContentDescription("Import")[0].performClick()
        waitForPicker()
        openDownloadsInPicker()
        inputFiles.forEach { file ->
            tapDocument(file.name)
        }
        confirmPickerAction(primaryLabels = listOf("Select", "Done", "Open"))

        composeRule.waitUntil(timeoutMillis = 15_000) {
            runCatching {
                composeRule.onNodeWithText("${inputFiles.size} image(s)", useUnmergedTree = true)
                    .assertTextEquals("${inputFiles.size} image(s)")
            }.isSuccess
        }

        composeRule.onAllNodesWithContentDescription("Save scene")[0].performClick()
        waitForPicker()
        typeFileName("device-import-save-test.pur")
        confirmPickerAction(primaryLabels = listOf("Save"))

        device.waitForIdle(3_000)
        assertTrue("Saved file was not created: $outputFile", outputFile.exists() && outputFile.length() > 0L)
    }

    private fun waitForPicker() {
        assertTrue(
            "Document picker did not appear",
            device.wait(Until.hasObject(By.pkg("com.google.android.documentsui")), 10_000)
        )
    }

    private fun openDownloadsInPicker() {
        val downloads = device.wait(Until.findObject(By.textContains("Downloads")), 5_000)
            ?: device.wait(Until.findObject(By.descContains("Show roots")), 3_000)
        if (downloads != null && downloads.text?.contains("Downloads") == true) {
            downloads.click()
            return
        }

        device.findObject(By.descContains("Show roots"))?.click()
        val downloadsEntry = device.wait(Until.findObject(By.textContains("Downloads")), 5_000)
        downloadsEntry?.click()
    }

    private fun tapDocument(fileName: String) {
        val doc = device.wait(Until.findObject(By.text(fileName)), 8_000)
        requireNotNull(doc) { "Could not find document $fileName in picker" }
        doc.click()
    }

    private fun confirmPickerAction(primaryLabels: List<String>) {
        val button = primaryLabels.firstNotNullOfOrNull { label ->
            device.wait(Until.findObject(By.text(label)), 3_000)
        } ?: device.wait(Until.findObject(By.clazz("android.widget.Button")), 3_000)
        requireNotNull(button) { "Could not find picker confirmation button" }
        button.click()
    }

    private fun typeFileName(fileName: String) {
        val input = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5_000)
        requireNotNull(input) { "Could not find save file name input" }
        input.text = fileName
    }

    companion object {
        private val TEST_FILE_NAMES = listOf(
            "image_task_01KSJG5BFW3CPP4G3D18S2ZE1E_0.png",
            "PixPin_2026-05-25_11-56-26.png",
        )
    }
}
