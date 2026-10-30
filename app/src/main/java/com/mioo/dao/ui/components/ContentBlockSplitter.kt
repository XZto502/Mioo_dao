package com.mioo.dao.ui.components

/** Replace only resolved references, in source order; unresolved links remain clickable. */
object ContentBlockSplitter {
    private val reference = Regex(
        """(?:<(?:font|a)\b[^>]*>)*[ \t]*(?:&gt;&gt;|>>)(?:No\.)?(\d+)(?!\d)[ \t]*(?:</(?:font|a)>)*""",
        RegexOption.IGNORE_CASE)
    private val edgeBreaks = Regex("""^(?:\s|<br\s*/?>)+|(?:\s|<br\s*/?>)+$""", RegexOption.IGNORE_CASE)

    fun split(html: String, quotes: List<PostData>): List<PostContentBlock> {
        if (quotes.isEmpty()) return listOf(PostContentBlock.Text(html))
        val resolved = quotes.associateBy { it.id }
        val blocks = mutableListOf<PostContentBlock>()
        var cursor = 0
        fun addText(text: String) {
            val trimmed = text.replace(edgeBreaks, "")
            if (trimmed.isNotBlank()) blocks.add(PostContentBlock.Text(trimmed))
        }
        for (match in reference.findAll(html)) {
            val quote = resolved[match.groupValues[1]] ?: continue
            addText(html.substring(cursor, match.range.first))
            blocks.add(PostContentBlock.Quote(quote))
            cursor = match.range.last + 1
        }
        if (cursor == 0) return listOf(PostContentBlock.Text(html))
        addText(html.substring(cursor))
        return blocks
    }
}
