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

    private fun references(html: String): List<Pair<IntRange, String>> {
        val ignored = excluded.findAll(html).map { it.range }.toList()
        val hidden = spoilers.findAll(html).map { it.range }.toList()
        return reference.findAll(html).mapNotNull { match ->
            val bare = match.groups["bare"]
            if (hidden.any { match.range.first in it }) null
            else if (bare != null && ignored.any { bare.range.first in it }) null
            else match.range to (bare?.value ?: match.groupValues[1])
        }.toList()
    }

    fun quoteIds(html: String): List<String> = references(html).map { it.second }

    fun split(html: String, quotes: List<PostData>): List<PostContentBlock> {
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
