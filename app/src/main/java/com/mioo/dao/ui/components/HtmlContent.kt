package com.mioo.dao.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.Text
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import com.mioo.dao.ui.theme.DaoTheme
import java.util.regex.Pattern

fun String.decodeHtmlEntities(): String {
    return this
        .replace("&gt;", ">")
        .replace("&lt;", "<")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#039;", "'")
        .replace("&nbsp;", " ")
        .replace("&#62;", ">")
        .replace("&#60;", "<")
        .replace("&middot;", "·")
        .replace("&bull;", "•")
}

/**
 * Strips ALL remaining HTML tags that were not matched by the main regex.
 * This prevents any raw <xxx> tags from leaking into the displayed text.
 */
fun String.stripRemainingHtmlTags(): String {
    return this.replace(Regex("<[^>]+>"), "")
}

/**
 * Parsed X-Island HTML comments into a styled Compose AnnotatedString.
 * Handles:
 * - `<br>` and `<br />` for line breaks
 * - `<font color="#xxxxxx">...</font>` for colored texts
 * - `<span style="color: xxx">...</span>` for styled span texts
 * - `<a href="xxx" ...>...</a>` for links (with extra attributes like target, rel)
 * - `>>No.xxxx` and `>>xxxx` for quote links
 */
fun parseHtmlToAnnotatedString(
    html: String,
    quoteLinkColor: Color,
    defaultGreenColor: Color = Color(0xFF789922)
): AnnotatedString {
    return buildAnnotatedString {
        // Clean line breaks and decode HTML entities before parsing
        val cleaned = html.replace(Regex("(?i)<br\\s*/?>"), "\n").decodeHtmlEntities()

        // Regex notes:
        // - <font color="xxx">...</font> : groups 1,2
        // - <span style="...color: xxx...">...</span> : groups 3,4
        // - <a href="xxx" ...>...</a> : groups 5,6  (allows extra attrs after href)
        // - >>No.xxxx or >>xxxx : groups 7,8
        val pattern = Pattern.compile(
            "(?:<font\\s+color=\"([^\"]+)\"[^>]*>(.*?)</font>)|" +
            "(?:<span\\s+style=\"[^\"]*color:\\s*([^;\"]+)[^\"]*\"[^>]*>(.*?)</span>)|" +
            "(?:<a\\s+href=\"([^\"]+)\"[^>]*>(.*?)</a>)|" +
            "(>>(?:No\\.)?(\\d+))",
            Pattern.CASE_INSENSITIVE or Pattern.DOTALL
        )
        val matcher = pattern.matcher(cleaned)

        var lastIndex = 0
        while (matcher.find()) {
            // Append normal text before the matched pattern (strip any leftover tags)
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
                // Strip nested tags inside font content
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

        // Append remaining text (strip any leftover tags)
        if (lastIndex < cleaned.length) {
            append(cleaned.substring(lastIndex).stripRemainingHtmlTags())
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
    onLongClick: (() -> Unit)? = null
) {
    val quoteLinkColor = MaterialTheme.colorScheme.primary
    val annotatedString = remember(html, quoteLinkColor) {
        parseHtmlToAnnotatedString(html, quoteLinkColor)
    }
    val context = LocalContext.current
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    val finalLinkClick = onLinkClick ?: { url ->
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        }
    }

    Text(
        text = annotatedString,
        modifier = modifier.pointerInput(annotatedString, onTextClick, onLongClick) {
            detectTapGestures(
                onTap = { pos ->
                    layoutResult?.let { layout ->
                        val offset = layout.getOffsetForPosition(pos)
                        val quoteAnnotation = annotatedString.getStringAnnotations(tag = "QUOTE_CLICK", start = offset, end = offset).firstOrNull()
                        val linkAnnotation = annotatedString.getStringAnnotations(tag = "LINK_CLICK", start = offset, end = offset).firstOrNull()

                        if (quoteAnnotation != null) {
                            onQuoteClick(quoteAnnotation.item)
                        } else if (linkAnnotation != null) {
                            finalLinkClick(linkAnnotation.item)
                        } else {
                            onTextClick?.invoke()
                        }
                    }
                },
                onLongPress = {
                    onLongClick?.invoke()
                }
            )
        },
        style = style.copy(color = MaterialTheme.colorScheme.onSurface),
        maxLines = maxLines,
        overflow = overflow,
        onTextLayout = { layoutResult = it }
    )
}
