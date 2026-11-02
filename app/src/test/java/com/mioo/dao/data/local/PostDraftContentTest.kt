package com.mioo.dao.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostDraftContentTest {
    @Test fun saveAndRestoreKeepAllThreeFieldsIncludingFormatting() {
        val original = PostDraftContent(" 标题 ", " 自定义名称 ", "第一段\n\n第二段 🐱")
        val stored = original.toDraft("4", "综合版")
        assertEquals(DraftEntity.TYPE_POST, stored.type)
        assertEquals("4", stored.targetId)
        assertEquals("综合版", stored.targetName)
        assertEquals(original, PostDraftContent.from(stored))
    }

    @Test fun nameOnlyAndTitleOnlyDraftsAreNotDeletedAsEmpty() {
        assertTrue(PostDraftContent("", "名称", "").hasContent)
        assertTrue(PostDraftContent("标题", "", "").hasContent)
        assertTrue(PostDraftContent("", "", "正文").hasContent)
        assertFalse(PostDraftContent(" ", "\n", "\t").hasContent)
    }

    @Test fun legacyBodyOnlyDraftCanStillBeSelected() {
        val legacy = DraftEntity(type = DraftEntity.TYPE_POST, targetId = "new_thread", content = "旧正文")
        assertEquals(PostDraftContent("", "", "旧正文"), PostDraftContent.from(legacy))
    }
}
