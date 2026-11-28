package com.mioo.dao.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchIndexTest {

    @Test
    fun blankQueryReturnsEmptySet() {
        assertEquals(emptySet<String>(), matchSettingSections(""))
        assertEquals(emptySet<String>(), matchSettingSections("   "))
        assertEquals(emptySet<String>(), matchSettingSections("\t\n"))
    }

    @Test
    fun noMatchQueryReturnsEmptySet() {
        assertEquals(emptySet<String>(), matchSettingSections("完全不存在的设置项xyz123"))
    }

    @Test
    fun allExpectedSectionIdsAreRegisteredAndUnique() {
        val ids = SETTINGS_SECTIONS.map { it.id }
        val expected = setOf(
            "cookies",
            "appearance",
            "reading",
            "subscription",
            "blocks",
            "network",
            "backup",
            "gestures",
            "offline",
            "kaomoji"
        )
        assertEquals(expected.size, ids.size)
        assertEquals(expected, ids.toSet())
    }

    @Test
    fun networkAliasesMatch() {
        listOf("卡", "连不上", "诊断", "兼容", "网络", "DNS").forEach { query ->
            assertTrue("Expected network to match '$query'", SettingsSectionIds.NETWORK in matchSettingSections(query))
        }
    }

    @Test
    fun subscriptionAliasesMatch() {
        listOf("通知", "提醒", "免打扰", "间隔", "后台", "UUID").forEach { query ->
            assertTrue(
                "Expected subscription to match '$query'",
                SettingsSectionIds.SUBSCRIPTION in matchSettingSections(query)
            )
        }
    }

    @Test
    fun cookiesAliasesMatch() {
        listOf("饼干", "确认", "鉴权", "UserHash", "草稿", "草稿箱").forEach { query ->
            assertTrue("Expected cookies to match '$query'", SettingsSectionIds.COOKIES in matchSettingSections(query))
        }
    }

    @Test
    fun blocksAliasesMatch() {
        listOf("屏蔽", "拉黑", "关键词", "关键字").forEach { query ->
            assertTrue("Expected blocks to match '$query'", SettingsSectionIds.BLOCKS in matchSettingSections(query))
        }
    }

    @Test
    fun gesturesAliasesMatch() {
        listOf("预测返回", "手势", "防误触").forEach { query ->
            assertTrue("Expected gestures to match '$query'", SettingsSectionIds.GESTURES in matchSettingSections(query))
        }
    }

    @Test
    fun appearanceAliasesMatch() {
        listOf("主题", "头图", "模糊", "AMOLED").forEach { query ->
            assertTrue(
                "Expected appearance to match '$query'",
                SettingsSectionIds.APPEARANCE in matchSettingSections(query)
            )
        }
    }

    @Test
    fun readingAliasesMatch() {
        listOf("阅读", "排版", "字号", "字体", "行距", "引用", "骰点").forEach { query ->
            assertTrue("Expected reading to match '$query'", SettingsSectionIds.READING in matchSettingSections(query))
        }
    }

    @Test
    fun backupAliasesMatch() {
        listOf("备份", "恢复", "导入", "导出", "换机", "JSON").forEach { query ->
            assertTrue("Expected backup to match '$query'", SettingsSectionIds.BACKUP in matchSettingSections(query))
        }
    }

    @Test
    fun offlineAliasesMatch() {
        listOf("离线", "缓存", "清理", "WiFi").forEach { query ->
            assertTrue("Expected offline to match '$query'", SettingsSectionIds.OFFLINE in matchSettingSections(query))
        }
    }

    @Test
    fun kaomojiAliasesMatch() {
        listOf("颜文字", "快捷输入", "创作者", "涂鸦").forEach { query ->
            assertTrue("Expected kaomoji to match '$query'", SettingsSectionIds.KAOMOJI in matchSettingSections(query))
        }
    }

    @Test
    fun singleAsciiLetterDoesNotSpamMatches() {
        listOf("a", "e", "o", "c", "s").forEach { single ->
            assertEquals("Expected empty match for '$single'", emptySet<String>(), matchSettingSections(single))
        }
        assertEquals(setOf(SettingsSectionIds.APPEARANCE), matchSettingSections("am"))
        assertEquals(setOf(SettingsSectionIds.COOKIES), matchSettingSections("co"))
        assertEquals(setOf(SettingsSectionIds.OFFLINE), matchSettingSections("wi"))
        assertEquals(setOf(SettingsSectionIds.READING), matchSettingSections("字号"))
    }

    @Test
    fun trimmedAndCaseInsensitiveQueriesMatch() {
        assertTrue(SettingsSectionIds.COOKIES in matchSettingSections("  饼干  "))
        assertTrue(SettingsSectionIds.NETWORK in matchSettingSections("  NETWORK  "))
    }
}
