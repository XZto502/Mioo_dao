package com.mioo.dao.ui.components

import org.junit.Assert.*
import org.junit.Test

class ReplyCountUpdateTest {
    private fun item(id: Int) = ThreadListItem(id.toLong(), "$id", "cookie", 3,
        PostData(id = "$id", userId = "cookie", createdAt = "time", content = "body $id"),
        "body $id", false)

    @Test fun enrichmentOnlyChangesMatchingRowsAndRetainsContentInstances() {
        val original = (1..1000).map(::item)
        val updated = original.withReplyCounts(mapOf("501" to 9))
        assertEquals(original.map { it.id }, updated.map { it.id })
        original.indices.forEach { index ->
            if (index == 500) {
                assertEquals(9, updated[index].replyCount)
                assertSame(original[index].postData, updated[index].postData)
                assertEquals(original[index].copy(replyCount = 9), updated[index])
            } else assertSame(original[index], updated[index])
        }
        assertEquals(3, original[500].replyCount)
    }

    @Test fun unchangedUnknownAndInvalidCountsDoNotRebuildList() {
        val original = listOf(item(1), item(2))
        assertSame(original, original.withReplyCounts(emptyMap()))
        assertSame(original, original.withReplyCounts(mapOf("1" to 3, "2" to -1, "99" to 10)))
    }

    @Test fun zeroAndNewerCountsCanReplacePreviouslyAppliedValues() {
        val original = listOf(item(1), item(2))
        val updated = original.withReplyCounts(mapOf("1" to 8, "2" to 7))
            .withReplyCounts(mapOf("1" to 0, "2" to 12))
        assertEquals(listOf(0, 12), updated.map { it.replyCount })
    }
}
