package com.mioo.dao.ui.components

import androidx.compose.ui.graphics.Color
import com.mioo.dao.utils.DiceRulePreset

/**
 * Lightweight bridge allowing ViewModels and Application startup to prewarm
 * [HtmlParseCache] on [kotlinx.coroutines.Dispatchers.Default] using the currently active
 * theme palette before emitting new list items to Compose.
 */
internal object HtmlPrewarmBridge {
    private data class ActivePalette(
        val quoteLinkColor: Color,
        val darkTheme: Boolean
    )

    @Volatile
    private var activePalette: ActivePalette? = null

    @Volatile
    private var activeThreadDicePreset: DiceRulePreset? = DiceRulePreset.NEUTRAL_DEFAULT

    fun updateTheme(quoteLinkColor: Color, darkTheme: Boolean) {
        val current = activePalette
        if (current != null &&
            current.quoteLinkColor == quoteLinkColor &&
            current.darkTheme == darkTheme
        ) {
            return
        }
        activePalette = ActivePalette(quoteLinkColor = quoteLinkColor, darkTheme = darkTheme)
    }

    fun updateThreadDicePreset(preset: DiceRulePreset?) {
        activeThreadDicePreset = preset
    }

    fun prewarmForumPreview(items: List<ThreadListItem>, limit: Int = 10) {
        val palette = activePalette ?: return
        if (items.isEmpty() || limit <= 0) return
        val count = minOf(limit, items.size)
        val bodies = ArrayList<String>(count)
        for (i in 0 until count) {
            bodies.add(items[i].postData.content)
        }
        HtmlParseCache.prewarm(
            htmlList = bodies,
            quoteLinkColor = palette.quoteLinkColor,
            darkTheme = palette.darkTheme,
            dicePreset = null,
            compactBlankLines = true
        )
    }

    fun prewarmThreadWindow(
        mainContent: String?,
        items: List<ReplyDisplayItem>,
        anchorIndex: Int = 0,
        dicePreset: DiceRulePreset? = activeThreadDicePreset,
        windowSize: Int = 10
    ) {
        val palette = activePalette ?: return
        val bodies = ArrayList<String>(windowSize + 1)
        if (!mainContent.isNullOrBlank() && anchorIndex <= 2) {
            bodies.add(mainContent)
        }
        if (items.isNotEmpty() && windowSize > 0) {
            val start = (anchorIndex - 1).coerceIn(0, items.lastIndex)
            val end = (start + windowSize).coerceAtMost(items.size)
            for (i in start until end) {
                bodies.add(items[i].postData.content)
            }
        }
        if (bodies.isEmpty()) return
        HtmlParseCache.prewarm(
            htmlList = bodies,
            quoteLinkColor = palette.quoteLinkColor,
            darkTheme = palette.darkTheme,
            dicePreset = dicePreset,
            compactBlankLines = false
        )
    }

    /**
     * Initializes HTML regexes, entity decoder, and AnnotatedString builder on a background
     * thread during startup so the first real list frame never pays cold class/regex setup.
     */
    fun warmParserRuntime() {
        val palette = activePalette ?: ActivePalette(Color(0xFF6750A4), false)
        HtmlParseCache.prewarm(
            htmlList = listOf("<font color=\"#789922\">&gt;&gt;No.12345678</font><br/>热身(ﾟ∀。)[h]黑幕[/h]"),
            quoteLinkColor = palette.quoteLinkColor,
            darkTheme = palette.darkTheme,
            dicePreset = null,
            compactBlankLines = true
        )
    }
}
