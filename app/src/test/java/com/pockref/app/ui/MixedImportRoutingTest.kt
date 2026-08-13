package com.pockref.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MixedImportRoutingTest {
    @Test
    fun purExtensionWinsOverIncorrectOemMime() {
        assertTrue(shouldOpenSceneFromImport("创作版.pur", "application/x-zip-compressed"))
        assertTrue(shouldOpenSceneFromImport("创作版.PUR", "image/*"))
    }

    @Test
    fun genericSceneMimeOpensScene() {
        assertTrue(shouldOpenSceneFromImport("download", "application/octet-stream"))
        assertTrue(shouldOpenSceneFromImport("download", "application/x-pockref-scene"))
    }

    @Test
    fun imageMimeStaysOnImageImport() {
        assertFalse(shouldOpenSceneFromImport("reference.png", "image/png"))
        assertFalse(shouldOpenSceneFromImport(null, "image/jpeg"))
    }

    @Test
    fun unknownNonImageMimeOpensAsScene() {
        assertTrue(shouldOpenSceneFromImport("download", "application/x-unknown"))
        assertTrue(shouldOpenSceneFromImport(null, null))
    }
}
