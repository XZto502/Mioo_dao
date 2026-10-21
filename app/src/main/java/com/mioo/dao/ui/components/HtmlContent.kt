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
import androidx.compose.ui.graphics.luminance
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
    if (indexOf('&') < 0) return this
    val once = decodeHtmlEntitiesOnce()
    return if (once != this && once.indexOf('&') >= 0) once.decodeHtmlEntitiesOnce() else once
}

private fun String.decodeHtmlEntitiesOnce(): String {
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
        val named = when {
            startsWith("&amp;", i) -> 5 to '&'
            startsWith("&gt;", i) -> 4 to '>'
            startsWith("&lt;", i) -> 4 to '<'
            startsWith("&quot;", i) -> 6 to '"'
            startsWith("&apos;", i) -> 6 to '\''
            startsWith("&nbsp;", i) -> 6 to ' '
            startsWith("&middot;", i) -> 8 to '·'
            startsWith("&bull;", i) -> 6 to '•'
            else -> null
        }
        if (named != null) {
            i += named.first
            out.append(named.second)
            continue
        }
        if (startsWith("&#", i)) {
            val semi = indexOf(';', i + 2)
            if (semi > i && semi - i <= 12) {
                val body = substring(i + 2, semi)
                val code = when {
                    body.startsWith("x") || body.startsWith("X") -> body.drop(1).toIntOrNull(16)
                    else -> body.toIntOrNull()
                }
                if (code != null && code in 1..0x10FFFF) {
                    i = semi + 1
                    if (code == 160) {
                        out.append(' ')
                    } else if (code <= 0xFFFF) {
                        out.append(code.toChar())
                    } else {
                        out.append(String(Character.toChars(code)))
                    }
                    continue
                }
            }
        }
        out.append(c)
        i++
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
    defaultGreenColor: Color = Color(0xFF789922),
    darkTheme: Boolean = false
): AnnotatedString {
    return buildAnnotatedString {
        val cleaned = html.replace(BR_TAG_REGEX, "\n").decodeHtmlEntities()
        val matcher = HTML_PATTERN.matcher(cleaned)

        var lastIndex = 0
        while (matcher.find()) {
            appendRichText(
                text = cleaned.substring(lastIndex, matcher.start()),
                baseColor = null,
                quoteLinkColor = quoteLinkColor
            )

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
                }.getOrDefault(defaultGreenColor).forNight(darkTheme)
                appendRichText(fontText, color, quoteLinkColor)
            } else if (spanColor != null && spanText != null) {
                val color = when (spanColor.trim().lowercase()) {
                    "green" -> Color(0xFF789922)
                    "red" -> Color(0xFFE53935)
                    "blue" -> Color(0xFF1E88E5)
                    else -> runCatching {
                        Color(android.graphics.Color.parseColor(spanColor.trim()))
                    }.getOrDefault(defaultGreenColor)
                }.forNight(darkTheme)
                appendRichText(spanText, color, quoteLinkColor)
            } else if (linkUrl != null && linkText != null) {
                appendLink(linkText.stripRemainingHtmlTags(), linkUrl)
            } else if (fullQuote != null && postId != null) {
                appendQuote(fullQuote, postId, quoteLinkColor)
            }

            lastIndex = matcher.end()
        }

        if (lastIndex < cleaned.length) {
            appendRichText(cleaned.substring(lastIndex), null, quoteLinkColor)
        }
    }
}

private val INNER_PATTERN = Pattern.compile(
    "(?:<a\\s+href=\"([^\"]+)\"[^>]*>(.*?)</a>)|(>>(?:No\\.)?(\\d+))",
    Pattern.CASE_INSENSITIVE or Pattern.DOTALL
)

/** 过暗的 font 色在深色背景上提亮。#789922 一类绿字亮度够，保持原色。 */
private fun Color.forNight(darkTheme: Boolean): Color {
    if (!darkTheme) return this
    if (relativeLuminance() >= 0.22f) return this
    val mix = 0.58f
    return copy(
        red = red + (1f - red) * mix,
        green = green + (1f - green) * mix,
        blue = blue + (1f - blue) * mix
    )
}

private fun Color.relativeLuminance(): Float {
    fun channel(c: Float): Float {
        return if (c <= 0.03928f) c / 12.92f
        else Math.pow(((c + 0.055) / 1.055).toDouble(), 2.4).toFloat()
    }
    return 0.2126f * channel(red) + 0.7152f * channel(green) + 0.0722f * channel(blue)
}

private fun AnnotatedString.Builder.appendRichText(
    text: String,
    baseColor: Color?,
    quoteLinkColor: Color
) {
    val matcher = INNER_PATTERN.matcher(text)
    var last = 0
    while (matcher.find()) {
        appendPlain(text.substring(last, matcher.start()), baseColor)
        val linkUrl = matcher.group(1)
        val linkText = matcher.group(2)
        val fullQuote = matcher.group(3)
        val postId = matcher.group(4)
        if (linkUrl != null && linkText != null) {
            appendLink(linkText.stripRemainingHtmlTags(), linkUrl)
        } else if (fullQuote != null && postId != null) {
            appendQuote(fullQuote, postId, quoteLinkColor)
        }
        last = matcher.end()
    }
    appendPlain(text.substring(last), baseColor)
}

private fun AnnotatedString.Builder.appendPlain(raw: String, color: Color?) {
    val plain = raw.stripRemainingHtmlTags()
    if (plain.isEmpty()) return
    val start = length
    append(plain)
    if (color != null) addStyle(SpanStyle(color = color), start, length)
}

private fun AnnotatedString.Builder.appendLink(label: String, url: String) {
    if (label.isEmpty()) return
    val start = length
    append(label)
    addStyle(
        SpanStyle(
            color = Color(0xFF1E88E5),
            fontWeight = FontWeight.Bold,
            textDecoration = TextDecoration.Underline
        ),
        start,
        length
    )
    addStringAnnotation(tag = "LINK_CLICK", annotation = url, start = start, end = length)
}

private fun AnnotatedString.Builder.appendQuote(
    label: String,
    postId: String,
    quoteLinkColor: Color
) {
    val start = length
    append(label)
    addStyle(
        SpanStyle(
            color = quoteLinkColor,
            fontWeight = FontWeight.Bold,
            textDecoration = TextDecoration.Underline
        ),
        start,
        length
    )
    addStringAnnotation(tag = "QUOTE_CLICK", annotation = postId, start = start, end = length)
}

/** Cache key includes night mode so lifted font colors do not stick across theme changes. */
private data class HtmlCacheKey(val html: String, val quoteColorArgb: Int, val darkTheme: Boolean)

/**
 * Shared HTML → AnnotatedString cache. Safe for background pre-warm and UI lookup.
 * Synchronized put/get — prewarm runs on Default while composition reads on main.
 */
object HtmlParseCache {
    private val cache = android.util.LruCache<HtmlCacheKey, AnnotatedString>(600)
    private val lock = Any()

    fun getOrParse(html: String, quoteLinkColor: Color, darkTheme: Boolean = false): AnnotatedString {
        val colorArgb = quoteLinkColor.toArgb()
        val key = HtmlCacheKey(html, colorArgb, darkTheme)
        synchronized(lock) {
            cache.get(key)?.let { return it }
        }
        val parsed = parseHtmlToAnnotatedString(html, quoteLinkColor, darkTheme = darkTheme)
        synchronized(lock) {
            cache.put(key, parsed)
        }
        return parsed
    }

    /**
     * Pre-parse HTML blobs on a background thread so first scroll frames hit the cache.
     * Call from [Dispatchers.Default] / [Dispatchers.IO].
     * Skips blanks and already-cached keys; no-op when list empty.
     */
    fun prewarm(htmlList: Collection<String>, quoteLinkColor: Color, darkTheme: Boolean = false) {
        if (htmlList.isEmpty()) return
        val colorArgb = quoteLinkColor.toArgb()
        for (html in htmlList) {
            if (html.isBlank()) continue
            val key = HtmlCacheKey(html, colorArgb, darkTheme)
            val hit = synchronized(lock) { cache.get(key) != null }
            if (hit) continue
            val parsed = parseHtmlToAnnotatedString(html, quoteLinkColor, darkTheme = darkTheme)
            synchronized(lock) {
                cache.put(key, parsed)
            }
        }
    }
}

/** Stable no-op — avoids allocating a new lambda every list-row composition. */
private val NoOpOnTextLayout: (TextLayoutResult) -> Unit = {}

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
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val annotatedString = remember(html, quoteColorArgb, darkTheme) {
        HtmlParseCache.getOrParse(html, quoteLinkColor, darkTheme)
    }
    val context = LocalContext.current
    // Only allocate layout state when gestures need hit-testing (thread detail / reply).
    var layoutResult by remember(enableGestures) {
        mutableStateOf<TextLayoutResult?>(null)
    }

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
        // List mode: stable no-op (no layoutResult write → no per-measure recomposition).
        onTextLayout = if (enableGestures) {
            { layoutResult = it }
        } else {
            NoOpOnTextLayout
        }
    )
}
