package com.mioo.dao.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DoodleSavePolicyTest {
    @Test
    fun autoSaveRunsWhenEnabledAndCanvasHasUnsavedContent() {
        assertTrue(
            shouldAutoSaveDoodleToGallery(
                autoSaveEnabled = true,
                hasVisualContent = true,
                canvasRevision = 1,
                lastSavedRevision = -1
            )
        )
    }

    @Test
    fun autoSaveSkipsWhenDisabled() {
        assertFalse(
            shouldAutoSaveDoodleToGallery(
                autoSaveEnabled = false,
                hasVisualContent = true,
                canvasRevision = 1,
                lastSavedRevision = -1
            )
        )
    }

    @Test
    fun autoSaveSkipsWhenCanvasHasNoContent() {
        assertFalse(
            shouldAutoSaveDoodleToGallery(
                autoSaveEnabled = true,
                hasVisualContent = false,
                canvasRevision = 0,
                lastSavedRevision = -1
            )
        )
    }

    @Test
    fun autoSaveSkipsWhenCurrentRevisionAlreadySavedManually() {
        assertFalse(
            shouldAutoSaveDoodleToGallery(
                autoSaveEnabled = true,
                hasVisualContent = true,
                canvasRevision = 2,
                lastSavedRevision = 2
            )
        )
    }

    @Test
    fun autoSaveRunsAgainAfterNewEditsFollowingManualSave() {
        assertTrue(
            shouldAutoSaveDoodleToGallery(
                autoSaveEnabled = true,
                hasVisualContent = true,
                canvasRevision = 3,
                lastSavedRevision = 2
            )
        )
    }
}
