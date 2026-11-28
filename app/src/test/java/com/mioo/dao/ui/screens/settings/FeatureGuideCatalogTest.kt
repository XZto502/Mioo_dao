package com.mioo.dao.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureGuideCatalogTest {

    @Test
    fun catalogHasSixOrderedSectionsWithUniqueIdsAndValidCopy() {
        val sections = FeatureGuideCatalog.sections
        assertEquals(6, sections.size)
        assertEquals(
            listOf("whats_new", "reading", "posting", "collections", "images", "personalization"),
            sections.map { it.id }
        )

        val allItems = sections.flatMap { it.items }
        assertEquals(allItems.size, allItems.map { it.id }.toSet().size)
        assertTrue(allItems.all { it.title.isNotBlank() && it.summary.isNotBlank() && it.triggerBadge.isNotBlank() })
    }

    @Test
    fun catalogCoversExpectedActionsAndSettingsSections() {
        val actions = FeatureGuideCatalog.sections.flatMap { it.items }.map { it.action }
        val settingsIds = actions.filterIsInstance<GuideAction.OpenSettings>().map { it.sectionId }.toSet()

        assertEquals(
            setOf("subscription", "blocks", "kaomoji", "cookies", "offline", "gestures", "appearance", "backup"),
            settingsIds
        )
        assertTrue(actions.contains(GuideAction.OpenNetworkDiagnostics))
        assertTrue(actions.contains(GuideAction.OpenDrafts))
        assertTrue(actions.contains(GuideAction.OpenSearch))
        assertTrue(actions.contains(GuideAction.None))
    }

    @Test
    fun blankQueryReturnsFullCatalog() {
        assertEquals(FeatureGuideCatalog.sections, filterGuideSections(""))
        assertEquals(FeatureGuideCatalog.sections, filterGuideSections("   "))
    }

    @Test
    fun filtersByTitleSummaryBadgeAndKeywords() {
        val byTitle = filterGuideSections("颜文字").flatMap { it.items }
        assertTrue(byTitle.any { it.id == "post_kaomoji_reorder" })

        val bySummary = filterGuideSections("全屏编辑").flatMap { it.items }
        assertEquals(listOf("post_creator_mode"), bySummary.map { it.id })

        val byBadge = filterGuideSections("横滑").flatMap { it.items }
        assertTrue(byBadge.isNotEmpty())
        assertTrue(byBadge.any { it.triggerBadge == "横滑" })

        val byKeywordIgnoreCase = filterGuideSections("AMOLED").flatMap { it.items }
        assertEquals(listOf("pref_appearance"), byKeywordIgnoreCase.map { it.id })

        val byVersion = filterGuideSections("1.1.8")
        assertEquals(listOf("whats_new"), byVersion.map { it.id })
        assertEquals(3, byVersion.first().items.size)
    }

    @Test
    fun multiTokenQueryNarrowsAndDropsEmptySections() {
        val matched = filterGuideSections("长按 屏蔽")
        assertEquals(listOf("reading"), matched.map { it.id })
        assertEquals(listOf("read_block_card"), matched.first().items.map { it.id })
    }

    @Test
    fun unmatchedQueryReturnsEmptyList() {
        assertTrue(filterGuideSections("不存在的功能XYZ").isEmpty())
    }
}
