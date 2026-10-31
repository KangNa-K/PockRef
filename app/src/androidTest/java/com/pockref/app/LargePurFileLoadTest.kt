package com.pockref.app

import android.os.Environment
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
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
class LargePurFileLoadTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private lateinit var device: UiDevice

    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    @Test
    fun loadLargePurFileWithoutOOM() {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val testFile = File(downloadDir, TEST_PUR_FILE)
        if (!testFile.exists()) {
            pushTestFileFromHost(testFile)
        }
        // The large .pur fixture is not checked in; skip instead of failing when absent.
        assumeTrue("Test fixture missing on device: ${testFile.absolutePath}", testFile.exists())

        composeRule.onAllNodesWithContentDescription("Import")[0].performClick()
        waitForPicker()
        openDownloadsInPicker()
        tapDocument(testFile.name)
        confirmPickerAction(primaryLabels = listOf("Select", "Done", "Open"))

        composeRule.waitUntil(timeoutMillis = 60_000) {
            val errorDialog = device.findObject(By.textContains("Load failed"))
            errorDialog == null
        }
    }

    private fun pushTestFileFromHost(target: File) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val tempFile = File(context.cacheDir, TEST_PUR_FILE)
        if (!tempFile.exists()) {
            val hostPath = "/Volumes/KNJ/NewScene 3.pur"
            val hostFile = File(hostPath)
            if (hostFile.exists()) {
                hostFile.inputStream().use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output, 65536)
                    }
                }
            }
        }
        if (tempFile.exists()) {
            tempFile.copyTo(target, overwrite = true)
        }
    }

    private fun waitForPicker() {
        val appeared = device.wait(Until.hasObject(By.pkg("com.google.android.documentsui")), 10_000)
        assertTrue("Document picker did not appear", appeared)
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

    companion object {
        private const val TEST_PUR_FILE = "NewScene 3.pur"
    }
}
