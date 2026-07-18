package com.mioo.dao.utils

/**
 * Multi-pattern substring matcher for block keywords.
 *
 * - 0 keywords → never matches
 * - 1–[LINEAR_THRESHOLD) keywords → sequential `in` on pre-lowercased text (cheapest)
 * - more keywords → Aho–Corasick automaton (one pass over text, O(n + z))
 *
 * Patterns are matched case-insensitively: build with any casing; call [containsMatch]
 * with **already-lowercased** haystack for best performance (or pass raw and set
 * [textIsLowercase] = false).
 */
class KeywordMatcher private constructor(
    private val mode: Mode
) {
    private sealed class Mode {
        data object Empty : Mode()
        data class Linear(val patterns: List<String>) : Mode()
        data class Automaton(val root: AcNode) : Mode()
    }

    /** True if any keyword is a substring of [text]. */
    fun containsMatch(text: String, textIsLowercase: Boolean = true): Boolean {
        if (mode is Mode.Empty) return false
        if (text.isEmpty()) return false
        val hay = if (textIsLowercase) text else text.lowercase()
        return when (val m = mode) {
            Mode.Empty -> false
            is Mode.Linear -> m.patterns.any { it in hay }
            is Mode.Automaton -> m.root.matches(hay)
        }
    }

    val isEmpty: Boolean get() = mode is Mode.Empty

    companion object {
        /** Below this count, linear scan wins (no fail-link build / cache pressure). */
        const val LINEAR_THRESHOLD: Int = 4

        val EMPTY: KeywordMatcher = KeywordMatcher(Mode.Empty)

        fun build(keywords: Collection<String>): KeywordMatcher {
            val patterns = ArrayList<String>(keywords.size)
            val seen = HashSet<String>(keywords.size)
            for (raw in keywords) {
                val p = raw.trim().lowercase()
                if (p.isEmpty()) continue
                if (seen.add(p)) patterns.add(p)
            }
            if (patterns.isEmpty()) return EMPTY
            if (patterns.size < LINEAR_THRESHOLD) {
                return KeywordMatcher(Mode.Linear(patterns))
            }
            return KeywordMatcher(Mode.Automaton(buildAutomaton(patterns)))
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
            // BFS fail links (classic Aho–Corasick)
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
                    // Output link: if fail is end, this path also matches
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
