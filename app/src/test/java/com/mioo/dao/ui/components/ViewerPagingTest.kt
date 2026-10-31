package com.mioo.dao.ui.components

import org.junit.Assert.*
import org.junit.Test

class ViewerPagingTest {
    @Test fun imageFreePagesStillContinueSearching() {
        assertTrue(shouldLoadViewerPage(4, 5, true, false, false, 2, 1))
        assertTrue(shouldLoadViewerPage(4, 5, true, false, false, 3, 2))
    }
    @Test fun requestBlockedDuringLoadResumesWhenLoadFinishes() {
        assertFalse(shouldLoadViewerPage(0, 1, true, true, false, 1, null))
        assertTrue(shouldLoadViewerPage(0, 1, true, false, false, 1, null))
    }
    @Test fun samePageErrorsAndEndNeverCauseRequestLoops() {
        assertFalse(shouldLoadViewerPage(4, 5, true, false, false, 2, 2))
        assertFalse(shouldLoadViewerPage(4, 5, true, false, true, 2, 1))
        assertFalse(shouldLoadViewerPage(4, 5, false, false, false, 2, 1))
        assertFalse(shouldLoadViewerPage(0, 5, true, false, false, 2, 1))
        assertFalse(shouldLoadViewerPage(0, 0, true, false, false, 2, 1))
    }
}
