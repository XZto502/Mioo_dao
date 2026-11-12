package com.mioo.dao.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflinePagePolicyTest {
    @Test fun conservativeRefreshReusesOnlyPagesBeforeOldLastPage() {
        assertTrue(shouldUseCachedOfflinePage(true, page = 2, previousReplyCount = 65, pageSize = 20))
        assertFalse(shouldUseCachedOfflinePage(true, page = 4, previousReplyCount = 65, pageSize = 20))
        assertFalse(shouldUseCachedOfflinePage(true, page = 5, previousReplyCount = 65, pageSize = 20))
    }

    @Test fun exactPageBoundaryStillRefreshesOldLastPage() {
        assertTrue(shouldUseCachedOfflinePage(true, 2, 60, 20))
        assertFalse(shouldUseCachedOfflinePage(true, 3, 60, 20))
    }

    @Test fun manualDownloadsAndThreadsWithoutPreviousRepliesFetchPages() {
        assertFalse(shouldUseCachedOfflinePage(false, 2, 100, 20))
        assertFalse(shouldUseCachedOfflinePage(true, 2, 0, 20))
    }
}
