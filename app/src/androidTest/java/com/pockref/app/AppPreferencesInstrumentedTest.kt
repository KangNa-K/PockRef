package com.pockref.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pockref.app.data.AppPreferences
import com.pockref.app.model.BackgroundPreset
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppPreferencesInstrumentedTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun cleanup() {
        AppPreferences(context).apply {
            defaultBackgroundPreset = BackgroundPreset.DefaultPreset
            singleFingerCanvasPanEnabled = false
        }
    }

    @Test
    fun defaultPresetPersistsAcrossInstances() {
        val prefs1 = AppPreferences(context)
        prefs1.defaultBackgroundPreset = BackgroundPreset.Sky

        val prefs2 = AppPreferences(context)
        assertEquals(BackgroundPreset.Sky, prefs2.defaultBackgroundPreset)
    }

    @Test
    fun customPresetIsNeverPersisted() {
        val prefs1 = AppPreferences(context)
        prefs1.defaultBackgroundPreset = BackgroundPreset.Mint

        val prefs2 = AppPreferences(context)
        prefs2.defaultBackgroundPreset = BackgroundPreset.Custom
        assertEquals(BackgroundPreset.Mint, prefs2.defaultBackgroundPreset)

        val prefs3 = AppPreferences(context)
        assertEquals(BackgroundPreset.Mint, prefs3.defaultBackgroundPreset)
    }

    @Test
    fun unsetReturnsFactoryDefault() {
        AppPreferences(context).defaultBackgroundPreset = BackgroundPreset.DefaultPreset
        val fresh = AppPreferences(context)
        assertEquals(BackgroundPreset.DefaultPreset, fresh.defaultBackgroundPreset)
    }

    @Test
    fun singleFingerCanvasPanPersistsAcrossInstances() {
        AppPreferences(context).singleFingerCanvasPanEnabled = true

        assertEquals(true, AppPreferences(context).singleFingerCanvasPanEnabled)
    }
}
