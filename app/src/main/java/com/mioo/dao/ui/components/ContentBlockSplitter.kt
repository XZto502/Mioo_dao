package com.mioo.dao.ui.components

/** Replace only resolved references, in source order; unresolved links remain clickable. */
object ContentBlockSplitter {
    private val reference = Regex(
        """(?:<(?:font|a)\b[^>]*>)*[ \t]*(?:&gt;&gt;|>>)(?:No\.)?(\d+)(?!\d)[ \t]*(?:</(?:font|a)>)*|(?<![A-Za-z0-9])(?<bare>\d{8})(?![A-Za-z0-9])""",
        RegexOption.IGNORE_CASE)
    private val edgeBreaks = Regex("""^(?:\s|<br\s*/?>)+|(?:\s|<br\s*/?>)+$""", RegexOption.IGNORE_CASE)
    private val spoilers = Regex(
        """\[h\][\s\S]*?\[/h\]|<span\b(?=[^>]*\bclass\s*=\s*["'][^"']*\bh(?:-hidden-text)?\b[^"']*["'])[^>]*>[\s\S]*?</span\s*>""",
        RegexOption.IGNORE_CASE
    )
    private val excluded = Regex(
        """<a\b[^>]*>[\s\S]*?</a\s*>|<[^>]*>|https?://[^\s<>"']+""",
        RegexOption.IGNORE_CASE
    )

    /** Regex matches and queried offsets both arrive in source order. */
    private class RangeCursor(ranges: Sequence<IntRange>) {
        private val iterator = ranges.iterator()
        private var current: IntRange? = null

        fun contains(offset: Int): Boolean {
            while (current == null || current!!.last < offset) {
                if (!iterator.hasNext()) return false
                current = iterator.next()
            }
            return offset in current!!
        }
    }

    private fun references(html: String): Sequence<Pair<IntRange, String>> = sequence {
        val ignored = RangeCursor(excluded.findAll(html).map { it.range })
        val hidden = RangeCursor(spoilers.findAll(html).map { it.range })
        for (match in reference.findAll(html)) {
            val bare = match.groups["bare"]
            if (hidden.contains(match.range.first)) continue
            if (bare != null && ignored.contains(bare.range.first)) continue
            yield(match.range to (bare?.value ?: match.groupValues[1]))
        }
    }

    fun quoteIds(html: String): List<String> = references(html).map { it.second }.toList()

    fun split(rawHtml: String, quotes: List<PostData>): List<PostContentBlock> {
        val html = normalizePostHtmlSource(rawHtml)
        if (quotes.isEmpty()) return listOf(PostContentBlock.Text(html))
        val resolved = quotes.associateBy { it.id }
        val blocks = mutableListOf<PostContentBlock>()
        var cursor = 0
        fun addText(text: String) {
            val trimmed = text.replace(edgeBreaks, "")
            if (trimmed.isNotBlank()) blocks.add(PostContentBlock.Text(trimmed))
        }
        for ((range, id) in references(html)) {
            val quote = resolved[id] ?: continue
            addText(html.substring(cursor, range.first))
            blocks.add(PostContentBlock.Quote(quote))
            cursor = range.last + 1
        }
        if (cursor == 0) return listOf(PostContentBlock.Text(html))
        addText(html.substring(cursor))
        return blocks
    }
}
