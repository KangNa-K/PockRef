package com.pockref.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pockref.app.data.AppPreferences
import com.pockref.app.model.BackgroundPreset
import com.pockref.app.model.PracticeConfig
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
            frostedGlassEnabled = true
            defaultBackgroundPreset = BackgroundPreset.DefaultPreset
            singleFingerCanvasPanEnabled = false
            defaultSavePathEnabled = false
            defaultSaveDirectoryUri = null
            lastPracticeConfig = null
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

    @Test
    fun defaultSavePathSwitchPersistsAcrossInstances() {
        // Finish the legacy migration before testing an explicit user choice.
        AppPreferences(context).defaultSavePathEnabled
        AppPreferences(context).defaultSavePathEnabled = true

        assertEquals(true, AppPreferences(context).defaultSavePathEnabled)
    }

    @Test
    fun practiceResetTimerPreferencePersistsAcrossInstances() {
        val config = PracticeConfig(
            practiceDurationMs = 300_000L,
            restDurationMs = 0L,
            resetTimerOnImageChange = false,
        )
        AppPreferences(context).lastPracticeConfig = config

        assertEquals(config, AppPreferences(context).lastPracticeConfig)
    }

    @Test
    fun defaultSaveDirectoryPersistsAcrossInstances() {
        val prefs1 = AppPreferences(context)
        prefs1.defaultSaveDirectoryUri = "content://example/tree/primary%3APictures"
        prefs1.defaultSaveDirectoryName = "Pictures"

        val prefs2 = AppPreferences(context)
        assertEquals(
            "content://example/tree/primary%3APictures",
            prefs2.defaultSaveDirectoryUri,
        )
        assertEquals("Pictures", prefs2.defaultSaveDirectoryName)
    }

    @Test
    fun frostedGlassPreferencePersistsAcrossInstances() {
        val initial = AppPreferences(context).frostedGlassEnabled
        try {
            AppPreferences(context).frostedGlassEnabled = false
            assertEquals(false, AppPreferences(context).frostedGlassEnabled)
            AppPreferences(context).frostedGlassEnabled = true
            assertEquals(true, AppPreferences(context).frostedGlassEnabled)
        } finally {
            AppPreferences(context).frostedGlassEnabled = initial
        }
    }

    @Test
    fun frostedGlassDefaultsToEnabledWhenUnset() {
        context.getSharedPreferences("pockref_settings", android.content.Context.MODE_PRIVATE)
            .edit().remove("frosted_glass_enabled").commit()
        assertEquals(true, AppPreferences(context).frostedGlassEnabled)
        assertEquals(true, com.pockref.app.model.PockRefUiState().frostedGlassEnabled)
    }

    @Test
    fun frostedGlassActionUpdatesStateAndRestoresAfterViewModelRecreation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val application = context.applicationContext as android.app.Application
            val store = androidx.lifecycle.ViewModelStore()
            try {
                AppPreferences(context).frostedGlassEnabled = true
                val first = com.pockref.app.ui.PockRefViewModel(application)
                store.put("first", first)
                first.onAction(com.pockref.app.ui.PockRefAction.SetFrostedGlassEnabled(false))
                assertEquals(false, first.uiState.value.frostedGlassEnabled)
                assertEquals(false, AppPreferences(context).frostedGlassEnabled)
                val restored = com.pockref.app.ui.PockRefViewModel(application)
                store.put("restored", restored)
                assertEquals(false, restored.uiState.value.frostedGlassEnabled)
                restored.onAction(com.pockref.app.ui.PockRefAction.SetFrostedGlassEnabled(true))
                assertEquals(true, restored.uiState.value.frostedGlassEnabled)
                assertEquals(true, AppPreferences(context).frostedGlassEnabled)
            } finally {
                store.clear()
                AppPreferences(context).frostedGlassEnabled = true
            }
        }
    }

}
