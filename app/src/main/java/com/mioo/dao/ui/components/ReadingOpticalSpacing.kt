package com.mioo.dao.ui.components

// HTML anchors and quote annotations bypass this function. Protect unlinked URLs and
// parenthesized faces too; never change a destination, identifier, or face internally.
private val opticalBoundary = Regex("""(?<=[\p{IsHan}])(?=[A-Za-z0-9])|(?<=[A-Za-z0-9])(?=[\p{IsHan}])""")
private val opticalProtected = Regex(
    """(?:https?://|ftp://|www\.)[^\s<>]+|(?:>>|No\.)\d+|\([^()\n]{1,64}\)|\|[^|\n]{1,32}\)""",
    RegexOption.IGNORE_CASE)

internal fun withReadingOpticalSpacing(text: String): String = buildString(text.length) {
    var cursor = 0
    for (match in opticalProtected.findAll(text)) {
        append(text.substring(cursor, match.range.first).replace(opticalBoundary, "\u2009"))
        append(match.value)
        cursor = match.range.last + 1
    }
    append(text.substring(cursor).replace(opticalBoundary, "\u2009"))
}
