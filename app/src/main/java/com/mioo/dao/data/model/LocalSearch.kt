package com.mioo.dao.data.model

import androidx.compose.runtime.Immutable

enum class LocalSearchSource(val label: String, val priority: Int) {
    BOOKMARK("收藏", 0),
    HISTORY("历史", 1),
    CACHE("缓存", 2)
}

@Immutable
data class LocalSearchHit(
    val threadId: String,
    val title: String?,
    val snippet: String,
    val userId: String,
    val source: LocalSearchSource,
    val timestamp: Long,
    val newReplyCount: Int = 0
)

@Immutable
data class LocalSearchResult(
    val query: String = "",
    /** When query looks like a thread number, offer one-tap open. */
    val directThreadId: String? = null,
    val hits: List<LocalSearchHit> = emptyList()
) {
    val isEmpty: Boolean
        get() = directThreadId == null && hits.isEmpty()
}

/**
 * External search helpers. X-island has no public search API, so full-site
 * discovery goes through search engines with [site:] operators.
 */
object XdWebSearch {
    private const val BASE = "https://www.nmbxd1.com"

    /** Domains / path prefixes to restrict engine results to X-island content. */
    private val SITE_SCOPES = listOf(
        "nmbxd.com",
        "nmbxd1.com",
        "www.nmbxd.com",
        "www.nmbxd1.com"
    )

    enum class Engine(val label: String) {
        GOOGLE("Google"),
        BING("Bing"),
        DUCKDUCKGO("DuckDuckGo")
    }

    /**
     * Build a search-engine URL:
     * `site:nmbxd.com OR site:nmbxd1.com ... "keyword"`
     * Prefer thread URLs when possible via optional `/t/` boost is left to the engine ranking.
     */
    fun siteSearchUrl(keyword: String, engine: Engine = Engine.GOOGLE): String {
        val q = keyword.trim()
        require(q.isNotEmpty()) { "keyword is blank" }

        val siteClause = SITE_SCOPES
            .distinctBy { it.removePrefix("www.") }
            .joinToString(" OR ") { "site:$it" }

        // Quote multi-word queries so engines treat them as a phrase more often
        val keywordClause = if (q.contains(' ')) "\"$q\"" else q
        val fullQuery = "($siteClause) $keywordClause"

        val encoded = java.net.URLEncoder.encode(fullQuery, Charsets.UTF_8.name())
        return when (engine) {
            Engine.GOOGLE -> "https://www.google.com/search?q=$encoded"
            Engine.BING -> "https://www.bing.com/search?q=$encoded"
            Engine.DUCKDUCKGO -> "https://duckduckgo.com/?q=$encoded"
        }
    }

    /** @deprecated Prefer [siteSearchUrl]. Kept for call-site migration. */
    fun searchUrl(keyword: String): String = siteSearchUrl(keyword, Engine.GOOGLE)

    fun threadUrl(threadId: String): String = "$BASE/t/$threadId"

    fun threadUrlAlt(threadId: String): String = "https://www.nmbxd.com/t/$threadId"
}
