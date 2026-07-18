package com.mioo.dao.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import java.util.regex.Pattern

private val HTML_TAG_REGEX = Regex("<[^>]+>")
private val BR_TAG_REGEX = Regex("(?i)<br\\s*/?>")
private val HTML_PATTERN = Pattern.compile(
    "(?:<font\\s+color=\"([^\"]+)\"[^>]*>(.*?)</font>)|" +
        "(?:<span\\s+style=\"[^\"]*color:\\s*([^;\"]+)[^\"]*\"[^>]*>(.*?)</span>)|" +
        "(?:<a\\s+href=\"([^\"]+)\"[^>]*>(.*?)</a>)|" +
        "(>>(?:No\\.)?(\\d+))",
    Pattern.CASE_INSENSITIVE or Pattern.DOTALL
)

fun String.decodeHtmlEntities(): String {
    // Fast path: skip scan when no entities present
    val amp = indexOf('&')
    if (amp < 0) return this

    // Single-pass decode — avoid chained replace() intermediate String allocations
    val out = StringBuilder(length)
    var i = 0
    val n = length
    while (i < n) {
        val c = this[i]
        if (c != '&') {
            out.append(c)
            i++
            continue
        }
        // Named / numeric entities used by X-Island HTML
        val decoded = when {
            startsWith("&gt;", i) || startsWith("&#62;", i) -> {
                i += if (this[i + 1] == '#') 5 else 4
                '>'
            }
            startsWith("&lt;", i) || startsWith("&#60;", i) -> {
                i += if (this[i + 1] == '#') 5 else 4
                '<'
            }
            startsWith("&amp;", i) -> {
                i += 5
                '&'
            }
            startsWith("&quot;", i) -> {
                i += 6
                '"'
            }
            startsWith("&#039;", i) -> {
                i += 6
                '\''
            }
            startsWith("&nbsp;", i) -> {
                i += 6
                ' '
            }
            startsWith("&middot;", i) -> {
                i += 8
                '·'
            }
            startsWith("&bull;", i) -> {
                i += 6
                '•'
            }
            else -> {
                out.append(c)
                i++
                continue
            }
        }
        out.append(decoded)
    }
    return out.toString()
}

/**
 * Strips ALL remaining HTML tags that were not matched by the main regex.
 * This prevents any raw <xxx> tags from leaking into the displayed text.
 */
fun String.stripRemainingHtmlTags(): String {
    val lt = indexOf('<')
    if (lt < 0) return this
    // Fast path for short residual snippets without nested tags
    val gt = indexOf('>', lt)
    if (gt < 0) return this
    if (indexOf('<', gt + 1) < 0) {
        // Single tag only — avoid full regex
        return removeRange(lt, gt + 1)
    }
    return replace(HTML_TAG_REGEX, "")
}

/**
 * Parsed X-Island HTML comments into a styled Compose AnnotatedString.
 */
fun parseHtmlToAnnotatedString(
    html: String,
    quoteLinkColor: Color,
    defaultGreenColor: Color = Color(0xFF789922)
): AnnotatedString {
    return buildAnnotatedString {
        val cleaned = html.replace(BR_TAG_REGEX, "\n").decodeHtmlEntities()
        val matcher = HTML_PATTERN.matcher(cleaned)

        var lastIndex = 0
        while (matcher.find()) {
            val gap = cleaned.substring(lastIndex, matcher.start()).stripRemainingHtmlTags()
            append(gap)

            val fontColorHex = matcher.group(1)
            val fontText = matcher.group(2)
            val spanColor = matcher.group(3)
            val spanText = matcher.group(4)
            val linkUrl = matcher.group(5)
            val linkText = matcher.group(6)
            val fullQuote = matcher.group(7)
            val postId = matcher.group(8)

            if (fontColorHex != null && fontText != null) {
                val color = runCatching {
                    Color(android.graphics.Color.parseColor(fontColorHex))
                }.getOrDefault(defaultGreenColor)

                val start = length
                append(fontText.stripRemainingHtmlTags())
                addStyle(SpanStyle(color = color), start, length)
            } else if (spanColor != null && spanText != null) {
                val color = when (spanColor.trim().lowercase()) {
                    "green" -> Color(0xFF789922)
                    "red" -> Color(0xFFE53935)
                    "blue" -> Color(0xFF1E88E5)
                    else -> runCatching {
                        Color(android.graphics.Color.parseColor(spanColor.trim()))
                    }.getOrDefault(defaultGreenColor)
                }

                val start = length
                append(spanText.stripRemainingHtmlTags())
                addStyle(SpanStyle(color = color), start, length)
            } else if (linkUrl != null && linkText != null) {
                val start = length
                append(linkText.stripRemainingHtmlTags())
                addStyle(
                    SpanStyle(
                        color = Color(0xFF1E88E5),
                        fontWeight = FontWeight.Bold,
                        textDecoration = TextDecoration.Underline
                    ),
                    start,
                    length
                )
                addStringAnnotation(
                    tag = "LINK_CLICK",
                    annotation = linkUrl,
                    start = start,
                    end = length
                )
            } else if (fullQuote != null && postId != null) {
                val start = length
                append(fullQuote)
                addStyle(
                    SpanStyle(
                        color = quoteLinkColor,
                        fontWeight = FontWeight.Bold,
                        textDecoration = TextDecoration.Underline
                    ),
                    start,
                    length
                )
                addStringAnnotation(
                    tag = "QUOTE_CLICK",
                    annotation = postId,
                    start = start,
                    end = length
                )
            }

            lastIndex = matcher.end()
        }

        if (lastIndex < cleaned.length) {
            append(cleaned.substring(lastIndex).stripRemainingHtmlTags())
        }
    }
}

/** Cache key: html + packed ARGB of quote color. */
private data class HtmlCacheKey(val html: String, val quoteColorArgb: Int)

/**
 * Shared HTML → AnnotatedString cache. Safe for background pre-warm and UI lookup.
 */
object HtmlParseCache {
    private val cache = android.util.LruCache<HtmlCacheKey, AnnotatedString>(500)

    fun getOrParse(html: String, quoteLinkColor: Color): AnnotatedString {
        val key = HtmlCacheKey(html, quoteLinkColor.toArgb())
        cache.get(key)?.let { return it }
        val parsed = parseHtmlToAnnotatedString(html, quoteLinkColor)
        cache.put(key, parsed)
        return parsed
    }

    /**
     * Pre-parse HTML blobs on a background thread so first scroll frames hit the cache.
     * Call from [Dispatchers.Default] / [Dispatchers.IO].
     */
    fun prewarm(htmlList: Collection<String>, quoteLinkColor: Color) {
        if (htmlList.isEmpty()) return
        val colorArgb = quoteLinkColor.toArgb()
        for (html in htmlList) {
            if (html.isBlank()) continue
            val key = HtmlCacheKey(html, colorArgb)
            if (cache.get(key) == null) {
                cache.put(key, parseHtmlToAnnotatedString(html, quoteLinkColor))
            }
        }
    }
}

@Composable
fun HtmlContent(
    html: String,
    onQuoteClick: (postId: String) -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    onTextClick: (() -> Unit)? = null,
    onLinkClick: ((url: String) -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    /**
     * When false, skip pointerInput gesture setup (parent Card already handles click/long-press).
     * Use for dense forum/timeline lists to cut cold-start scroll work.
     */
    enableGestures: Boolean = true
) {
    val quoteLinkColor = MaterialTheme.colorScheme.primary
    val quoteColorArgb = quoteLinkColor.toArgb()
    val annotatedString = remember(html, quoteColorArgb) {
        HtmlParseCache.getOrParse(html, quoteLinkColor)
    }
    val context = LocalContext.current
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    val surfaceColor = MaterialTheme.colorScheme.onSurface
    val mergedStyle = remember(style, surfaceColor) { style.copy(color = surfaceColor) }

    val textModifier = if (!enableGestures) {
        modifier
    } else {
        val defaultLinkClick = remember(context) {
            { url: String ->
                runCatching {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(intent)
                }
                Unit
            }
        }
        val finalLinkClick = onLinkClick ?: defaultLinkClick
        val onQuoteClickState = rememberUpdatedState(onQuoteClick)
        val onTextClickState = rememberUpdatedState(onTextClick)
        val onLongClickState = rememberUpdatedState(onLongClick)
        val finalLinkClickState = rememberUpdatedState(finalLinkClick)

        modifier.pointerInput(annotatedString) {
            detectTapGestures(
                onTap = { pos ->
                    layoutResult?.let { layout ->
                        val offset = layout.getOffsetForPosition(pos)
                        val quoteAnnotation = annotatedString.getStringAnnotations(
                            tag = "QUOTE_CLICK", start = offset, end = offset
                        ).firstOrNull()
                        val linkAnnotation = annotatedString.getStringAnnotations(
                            tag = "LINK_CLICK", start = offset, end = offset
                        ).firstOrNull()

                        when {
                            quoteAnnotation != null -> onQuoteClickState.value(quoteAnnotation.item)
                            linkAnnotation != null -> finalLinkClickState.value(linkAnnotation.item)
                            else -> onTextClickState.value?.invoke()
                        }
                    }
                },
                onLongPress = {
                    onLongClickState.value?.invoke()
                }
            )
        }
    }

    Text(
        text = annotatedString,
        modifier = textModifier,
        style = mergedStyle,
        maxLines = maxLines,
        overflow = overflow,
        onTextLayout = if (enableGestures) {
            { layoutResult = it }
        } else {
            {}
        }
    )
}
