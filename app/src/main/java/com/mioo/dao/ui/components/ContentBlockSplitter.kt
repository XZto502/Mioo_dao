package com.mioo.dao.ui.components

import java.util.concurrent.ConcurrentHashMap

/**
 * Split post HTML into text + embedded quote cards.
 * Pure / cacheable — safe to call from background threads.
 */
object ContentBlockSplitter {

    private val quotePatternCache = ConcurrentHashMap<String, Regex>()

    /** LRU-ish: cap map size by clearing when large (simple, lock-free enough). */
    private val blockCache = ConcurrentHashMap<String, List<PostContentBlock>>(64)

    private fun quoteRegex(quoteId: String): Regex {
        return quotePatternCache.getOrPut(quoteId) {
            Regex(
                "(?:<font[^>]*>)?\\s*(?:&gt;&gt;|>>)(?:No\\.)?${Regex.escape(quoteId)}\\s*(?:</font>)?",
                RegexOption.IGNORE_CASE
            )
        }
    }

    fun split(html: String, quotes: List<PostData>): List<PostContentBlock> {
        if (quotes.isEmpty()) {
            return listOf(PostContentBlock.Text(html))
        }
        val cacheKey = buildString(html.length + quotes.size * 12) {
            append(html.hashCode())
            append('|')
            quotes.forEach { q ->
                append(q.id)
                append(':')
                append(q.content.hashCode())
                append(',')
            }
        }
        blockCache[cacheKey]?.let { return it }

        val blocks = ArrayList<PostContentBlock>(quotes.size * 2 + 1)
        var currentHtml = html
        for (quote in quotes) {
            val match = quoteRegex(quote.id).find(currentHtml)
            if (match != null) {
                val beforeText = currentHtml.substring(0, match.range.last + 1)
                currentHtml = currentHtml.substring(match.range.last + 1)
                if (beforeText.isNotBlank()) {
                    blocks.add(PostContentBlock.Text(beforeText))
                }
                blocks.add(PostContentBlock.Quote(quote))
            } else {
                // Quote present in cache but pattern missing in body — still show the card
                blocks.add(PostContentBlock.Quote(quote))
            }
        }
        if (currentHtml.isNotBlank()) {
            blocks.add(PostContentBlock.Text(currentHtml))
        }
        val result = blocks.toList()
        if (blockCache.size > 256) {
            blockCache.clear()
        }
        blockCache[cacheKey] = result
        return result
    }
}
