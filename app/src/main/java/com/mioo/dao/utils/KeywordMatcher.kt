package com.mioo.dao.utils

/**
 * Multi-pattern substring matcher for block keywords.
 *
 * Ordinary keywords stay literal contains / Aho–Corasick matches. A keyword is
 * compiled as a regex only when it is wrapped as `/pattern/` or prefixed `regex:`.
 * An illegal pattern falls back to a case-insensitive contains of that body, so a
 * stray `[` or `(` cannot throw [java.util.regex.PatternSyntaxException].
 *
 * - 0 keywords → never matches
 * - 1–[LINEAR_THRESHOLD) literal keywords → sequential `in` on pre-lowercased text
 * - more literal keywords → Aho–Corasick automaton (one pass over text, O(n + z))
 */
class KeywordMatcher private constructor(
    private val mode: Mode,
    private val regexes: List<Regex>
) {
    private sealed class Mode {
        data object Empty : Mode()
        data class Linear(val patterns: List<String>) : Mode()
        data class Automaton(val root: AcNode) : Mode()
    }

    /** True if any keyword is a substring of [text], or any opt-in regex matches. */
    fun containsMatch(text: String, textIsLowercase: Boolean = true): Boolean {
        if (text.isEmpty()) return false
        if (regexes.isNotEmpty()) {
            for (regex in regexes) {
                val hit = runCatching { regex.containsMatchIn(text) }.getOrDefault(false)
                if (hit) return true
            }
        }
        if (mode is Mode.Empty) return false
        val hay = if (textIsLowercase) text else text.lowercase()
        return when (val m = mode) {
            Mode.Empty -> false
            is Mode.Linear -> m.patterns.any { it in hay }
            is Mode.Automaton -> m.root.matches(hay)
        }
    }

    val isEmpty: Boolean get() = mode is Mode.Empty && regexes.isEmpty()

    companion object {
        /** Below this count, linear scan wins (no fail-link build / cache pressure). */
        const val LINEAR_THRESHOLD: Int = 4

        val EMPTY: KeywordMatcher = KeywordMatcher(Mode.Empty, emptyList())

        fun build(keywords: Collection<String>): KeywordMatcher {
            val literals = ArrayList<String>(keywords.size)
            val regexes = ArrayList<Regex>()
            val seen = HashSet<String>(keywords.size)
            for (raw in keywords) {
                val trimmed = raw.trim()
                if (trimmed.isEmpty()) continue
                val spec = regexBody(trimmed)
                if (spec != null) {
                    val compiled = runCatching {
                        Regex(spec, RegexOption.IGNORE_CASE)
                    }.getOrNull()
                    if (compiled != null) {
                        regexes.add(compiled)
                    } else if (spec.isNotEmpty()) {
                        val literal = spec.lowercase()
                        if (seen.add(literal)) literals.add(literal)
                    }
                    continue
                }
                val pattern = trimmed.lowercase()
                if (seen.add(pattern)) literals.add(pattern)
            }
            if (literals.isEmpty() && regexes.isEmpty()) return EMPTY
            val mode = when {
                literals.isEmpty() -> Mode.Empty
                literals.size < LINEAR_THRESHOLD -> Mode.Linear(literals)
                else -> Mode.Automaton(buildAutomaton(literals))
            }
            return KeywordMatcher(mode, regexes)
        }

        /** `/pattern/` or `regex:pattern`. Plain text, including `[`, stays literal. */
        private fun regexBody(raw: String): String? {
            if (raw.length >= 2 && raw.first() == '/' && raw.last() == '/') {
                val body = raw.substring(1, raw.length - 1)
                return body.ifEmpty { null }
            }
            if (raw.length > 6 && raw.startsWith("regex:", ignoreCase = true)) {
                val body = raw.substring(6).trim()
                return body.ifEmpty { null }
            }
            return null
        }

        private fun buildAutomaton(patterns: List<String>): AcNode {
            val root = AcNode()
            for (pattern in patterns) {
                var node = root
                for (ch in pattern) {
                    node = node.children.getOrPut(ch) { AcNode() }
                }
                node.isEnd = true
            }
            val queue = ArrayDeque<AcNode>()
            for (child in root.children.values) {
                child.fail = root
                queue.add(child)
            }
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                for ((ch, child) in current.children) {
                    queue.add(child)
                    var f = current.fail
                    while (f != null && ch !in f.children) {
                        f = f.fail
                    }
                    child.fail = f?.children?.get(ch) ?: root
                    if (child.fail === child) child.fail = root
                    if (child.fail?.isEnd == true) {
                        child.isEnd = true
                    }
                }
            }
            return root
        }
    }

    private class AcNode {
        val children: HashMap<Char, AcNode> = HashMap(4)
        var fail: AcNode? = null
        var isEnd: Boolean = false

        fun matches(text: String): Boolean {
            var node = this
            for (ch in text) {
                while (node.fail != null && ch !in node.children) {
                    node = node.fail!!
                }
                node = node.children[ch] ?: this
                if (node.isEnd) return true
            }
            return false
        }
    }
}
