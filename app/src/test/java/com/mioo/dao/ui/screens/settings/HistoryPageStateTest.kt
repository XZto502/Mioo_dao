package com.mioo.dao.ui.screens.settings

import com.mioo.dao.data.local.HistoryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryPageStateTest {
    private fun item(id: String, type: HistoryType = HistoryType.BROWSE, threadId: String? = id) = HistoryItem(
        id = id,
        type = type,
        threadId = threadId,
        forumName = "综合版1",
        cookieHash = "abc",
        timestamp = 100L,
        title = "标题 $id",
        content = "内容 $id"
    )

    @Test
    fun removeHistoryItemDropsOnlyTargetAndPreservesOrder() {
        val initial = HistoryPageState(
            loaded = true,
            items = listOf(item("1"), item("2"), item("3")),
            isRefreshing = false
        )
        val updated = removeHistoryItem(initial, "2")
        assertTrue(updated.loaded)
        assertFalse(updated.isRefreshing)
        assertEquals(listOf("1", "3"), updated.items.map { it.id })
    }

    @Test
    fun clearHistoryPageEmptiesItemsAndMarksLoaded() {
        val initial = HistoryPageState(
            loaded = false,
            items = listOf(item("1"), item("2")),
            isRefreshing = true
        )
        val cleared = clearHistoryPage(initial)
        assertTrue(cleared.loaded)
        assertFalse(cleared.isRefreshing)
        assertTrue(cleared.items.isEmpty())
    }

    @Test
    fun patchResolvedThreadIdUpdatesMatchingEntryInPlace() {
        val initial = HistoryPageState(
            loaded = true,
            items = listOf(
                item("a", HistoryType.NEW_THREAD, threadId = null),
                item("b", HistoryType.NEW_THREAD, threadId = "200")
            )
        )
        val patched = patchResolvedThreadId(initial, "a", "999")
        assertEquals(listOf("999", "200"), patched.items.map { it.threadId })
        assertEquals(listOf("a", "b"), patched.items.map { it.id })
    }
}
