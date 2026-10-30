package com.mioo.dao.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.ParagraphStyle
import com.mioo.dao.ui.theme.readingBodyStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mioo.dao.utils.DiceBadgeSpec
import com.mioo.dao.utils.DiceLevel
import com.mioo.dao.utils.DiceRulePreset
import com.mioo.dao.utils.DiceRoll
import java.util.regex.Pattern

private val HTML_TAG_REGEX = Regex("<[^>]+>")
private val BR_TAG_REGEX = Regex("(?i)<br\\s*/?>")
private val PARAGRAPH_BREAKS = Regex("\\n[ \\t]*(?:\\n[ \\t]*)+")
private val KAOMOJI_PATTERN = Regex("""\([^()\n]{1,32}\)|\|[^|\n]{1,16}\)""")
private val KAOMOJI_FACE_MARKS = Regex("[ﾟ∀。´ヮゝωДдಠ益ಥ╥≧≦]")
private val KAOMOJI_PROSE = Regex("[\u3400-\u9fffA-Za-z0-9]")
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
 * [dicePreset] 非空时扫描正文骰点并生成内联胶囊。默认纯色；安科/CoC 才上金/红。
 */
fun parseHtmlToAnnotatedString(
    html: String,
    quoteLinkColor: Color,
    defaultGreenColor: Color = Color(0xFF789922),
    darkTheme: Boolean = false,
    dicePreset: DiceRulePreset? = null
): ParsedContent {
    val cleaned = html.replace(BR_TAG_REGEX, "\n").decodeHtmlEntities()
        .replace("\r\n", "\n").replace(PARAGRAPH_BREAKS, "\n\n")
    val ctx = dicePreset?.let { DiceParseCtx(preset = it) }
    val annotated = buildAnnotatedString {
        val matcher = HTML_PATTERN.matcher(cleaned)

        var lastIndex = 0
        while (matcher.find()) {
            appendRichText(
                text = cleaned.substring(lastIndex, matcher.start()),
                baseColor = null,
                quoteLinkColor = quoteLinkColor,
                ctx = ctx
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
                appendRichText(fontText, color, quoteLinkColor, ctx)
            } else if (spanColor != null && spanText != null) {
                val color = when (spanColor.trim().lowercase()) {
                    "green" -> Color(0xFF789922)
                    "red" -> Color(0xFFE53935)
                    "blue" -> Color(0xFF1E88E5)
                    else -> runCatching {
                        Color(android.graphics.Color.parseColor(spanColor.trim()))
                    }.getOrDefault(defaultGreenColor)
                }.forNight(darkTheme)
                appendRichText(spanText, color, quoteLinkColor, ctx)
            } else if (linkUrl != null && linkText != null) {
                appendLink(linkText.stripRemainingHtmlTags(), linkUrl)
            } else if (fullQuote != null && postId != null) {
                appendQuote(fullQuote, postId, quoteLinkColor)
            }

            lastIndex = matcher.end()
        }

        if (lastIndex < cleaned.length) {
            appendRichText(cleaned.substring(lastIndex), null, quoteLinkColor, ctx)
        }
    }
    return ParsedContent(annotated = annotated, diceBadges = ctx?.badges ?: emptyList())
}

/** 一次解析里的骰点判定上下文（仅 dicePreset 非空时创建）。 */
private class DiceParseCtx(
    val preset: DiceRulePreset
) {
    var nextId: Int = 0
    val badges: MutableList<DiceBadgeSpec> = mutableListOf()
}

/** 解析产物：样式化文本 + 骰点胶囊（无骰点时 badges 为空）。 */
data class ParsedContent(
    val annotated: AnnotatedString,
    val diceBadges: List<DiceBadgeSpec>
)

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
    quoteLinkColor: Color,
    ctx: DiceParseCtx? = null
) {
    val matcher = INNER_PATTERN.matcher(text)
    var last = 0
    while (matcher.find()) {
        appendPlain(text.substring(last, matcher.start()), baseColor, ctx)
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
    appendPlain(text.substring(last), baseColor, ctx)
}

// Join face punctuation as well as spaces: Unicode line breaking may split either.
internal fun protectReadingKaomoji(text: String): String {
    return KAOMOJI_PATTERN.replace(text) { match ->
        if (KAOMOJI_FACE_MARKS.containsMatchIn(match.value) && !KAOMOJI_PROSE.containsMatchIn(match.value))
            match.value.replace(' ', '\u00A0').codePoints().toArray()
                .joinToString("\u2060") { String(Character.toChars(it)) }
        else match.value
    }
}

internal fun AnnotatedString.withReadingParagraphGaps(scale: Float): AnnotatedString =
    buildAnnotatedString {
        append(this@withReadingParagraphGaps)
        Regex("\n\n").findAll(this@withReadingParagraphGaps.text).forEach { gap ->
            val start = gap.range.first + 1
            addStyle(ParagraphStyle(lineHeight = (8f * scale).sp), start, start + 1)
            addStyle(SpanStyle(fontSize = (6f * scale).sp), start, start + 1)
        }
    }

private fun AnnotatedString.Builder.appendPlain(raw: String, color: Color?, ctx: DiceParseCtx? = null) {
    val plain = protectReadingKaomoji(raw.stripRemainingHtmlTags())
    if (plain.isEmpty()) return
    if (ctx == null) {
        appendStyledPlain(plain, color)
        return
    }
    // 骰点扫描：`1[1,2]` / `1d100=57` → 内联胶囊占位 + 点击注解
    val matches = DiceRoll.findTokens(plain)
    if (matches.isEmpty()) {
        appendStyledPlain(plain, color)
        return
    }
    var last = 0
    for (m in matches) {
        if (m.start > last) appendStyledPlain(plain.substring(last, m.start), color)
        val level = DiceRoll.judge(ctx.preset, m.token)
        val id = "dice_${ctx.nextId++}"
        ctx.badges.add(DiceBadgeSpec(id = id, token = m.token, level = level))
        val start = length
        appendInlineContent(id, m.token.result.toString())
        addStringAnnotation(tag = "DICE_CLICK", annotation = "dice", start = start, end = length)
        last = m.end
    }
    if (last < plain.length) appendStyledPlain(plain.substring(last), color)
}

private fun AnnotatedString.Builder.appendStyledPlain(plain: String, color: Color?) {
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

/** Cache key includes night mode so lifted font colors do not stick across theme changes.
 *  骰点规则参与键：同一楼在不同规则下胶囊配色不同。 */
private data class HtmlCacheKey(val html: String, val quoteColorArgb: Int, val darkTheme: Boolean, val dicePreset: String)

/**
 * Shared HTML → AnnotatedString cache. Safe for background pre-warm and UI lookup.
 * Synchronized put/get — prewarm runs on Default while composition reads on main.
 */
object HtmlParseCache {
    private val cache = android.util.LruCache<HtmlCacheKey, ParsedContent>(600)
    private val lock = Any()

    fun getOrParse(
        html: String,
        quoteLinkColor: Color,
        darkTheme: Boolean = false,
        dicePreset: DiceRulePreset? = null
    ): ParsedContent {
        val colorArgb = quoteLinkColor.toArgb()
        val key = HtmlCacheKey(html, colorArgb, darkTheme, dicePreset?.name ?: "")
        synchronized(lock) {
            cache.get(key)?.let { return it }
        }
        val parsed = parseHtmlToAnnotatedString(html, quoteLinkColor, darkTheme = darkTheme, dicePreset = dicePreset)
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
    fun prewarm(
        htmlList: Collection<String>,
        quoteLinkColor: Color,
        darkTheme: Boolean = false,
        dicePreset: DiceRulePreset? = null
    ) {
        if (htmlList.isEmpty()) return
        val colorArgb = quoteLinkColor.toArgb()
        for (html in htmlList) {
            if (html.isBlank()) continue
            val key = HtmlCacheKey(html, colorArgb, darkTheme, dicePreset?.name ?: "")
            val hit = synchronized(lock) { cache.get(key) != null }
            if (hit) continue
            val parsed = parseHtmlToAnnotatedString(html, quoteLinkColor, darkTheme = darkTheme, dicePreset = dicePreset)
            synchronized(lock) {
                cache.put(key, parsed)
            }
        }
    }
}

/** Stable no-op — avoids allocating a new lambda every list-row composition. */
private val NoOpOnTextLayout: (TextLayoutResult) -> Unit = {}

/** 串级骰点高亮规则：ThreadScreen 提供，其余界面为 null（不做骰点处理）。 */
val LocalDicePreset = compositionLocalOf<DiceRulePreset?> { null }

/** 点击任意骰子胶囊时回调（ThreadScreen 弹出规则切换微菜单）。 */
val LocalDiceRuleMenu = compositionLocalOf<(() -> Unit)?> { null }

/**
 * 帖子正文渲染：HTML → AnnotatedString + 引用/链接/骰点胶囊交互。
 * 骰点胶囊仅在 [LocalDicePreset] 有值时启用；点击经 [LocalDiceRuleMenu] 上抛。
 */
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
    val dicePreset = LocalDicePreset.current
    val parsed = remember(html, quoteColorArgb, darkTheme, dicePreset) {
        HtmlParseCache.getOrParse(html, quoteLinkColor, darkTheme, dicePreset)
    }
    val paragraphScale = MaterialTheme.typography.bodyMedium.lineHeight.value / 20f
    val annotatedString = remember(parsed.annotated, paragraphScale) {
        parsed.annotated.withReadingParagraphGaps(paragraphScale)
    }
    val context = LocalContext.current
    // Only allocate layout state when gestures need hit-testing (thread detail / reply).
    var layoutResult by remember(enableGestures) {
        mutableStateOf<TextLayoutResult?>(null)
    }

    val surfaceColor = readingBodyStyle().color
    val mergedStyle = remember(style, surfaceColor) {
        if (style.color == Color.Unspecified) style.copy(color = surfaceColor) else style
    }

    // 宽度按完整文案的字符数留余量，规则和深浅色都进 key，切换后立刻重画
    val inlineContent = remember(html, dicePreset, darkTheme, parsed.diceBadges) {
        if (parsed.diceBadges.isEmpty()) {
            emptyMap()
        } else {
            parsed.diceBadges.associate { spec ->
                val displayText = spec.badgeText
                val dynamicWidthEm = (2.6f + displayText.length * 0.68f).em
                spec.id to InlineTextContent(
                    placeholder = Placeholder(
                        width = dynamicWidthEm,
                        height = 1.5.em,
                        placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                    )
                ) {
                    DiceBadge(spec)
                }
            }
        }
    }

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
        val onDiceClickState = rememberUpdatedState(LocalDiceRuleMenu.current)

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
                        val diceAnnotation = annotatedString.getStringAnnotations(
                            tag = "DICE_CLICK", start = offset, end = offset
                        ).firstOrNull()

                        when {
                            diceAnnotation != null -> onDiceClickState.value?.invoke()
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
        inlineContent = inlineContent,
        // List mode: stable no-op (no layoutResult write → no per-measure recomposition).
        onTextLayout = if (enableGestures) {
            { layoutResult = it }
        } else {
            NoOpOnTextLayout
        }
    )
}

/** 骰点胶囊：13dp 矢量骰子 + 完整 `出目 [起,止]`。整枚可点，打开本串规则菜单。 */
@Composable
private fun DiceBadge(spec: DiceBadgeSpec) {
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val onBadgeClick = LocalDiceRuleMenu.current
    val normalBg = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
    val normalFg = MaterialTheme.colorScheme.onSecondaryContainer
    val colors = diceBadgeColors(spec.level, darkTheme, normalBg, normalFg)
    Row(
        modifier = Modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(6.dp))
            .background(colors.background)
            .then(
                if (colors.border != null) {
                    Modifier.border(1.dp, colors.border, RoundedCornerShape(6.dp))
                } else {
                    Modifier
                }
            )
            .clickable { onBadgeClick?.invoke() }
            .padding(horizontal = 6.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Casino,
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            tint = colors.foreground
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = spec.badgeText,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
            style = TextStyle(
                fontSize = 12.5.sp,
                fontFeatureSettings = "tnum",
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                fontWeight = colors.weight,
                color = colors.foreground
            )
        )
    }
}

private data class DiceBadgeColors(
    val background: Color,
    val foreground: Color,
    val border: Color?,
    val weight: FontWeight
)

/** 普通档沿用现有 secondaryContainer；金/红只在用户选了安科或 CoC 且出目到极值时出现。 */
private fun diceBadgeColors(
    level: DiceLevel,
    darkTheme: Boolean,
    normalBg: Color,
    normalFg: Color
): DiceBadgeColors {
    return when (level) {
        DiceLevel.NORMAL -> DiceBadgeColors(
            background = normalBg,
            foreground = normalFg,
            border = null,
            weight = FontWeight.Normal
        )
        DiceLevel.CRITICAL_GOOD -> if (darkTheme) {
            DiceBadgeColors(Color(0xFF423000), Color(0xFFFFD54F), Color(0xFFFFC107), FontWeight.ExtraBold)
        } else {
            DiceBadgeColors(Color(0xFFFFF0C2), Color(0xFFB26A00), Color(0xFFFFC107), FontWeight.ExtraBold)
        }
        DiceLevel.CRITICAL_BAD -> if (darkTheme) {
            DiceBadgeColors(Color(0xFF4A1218), Color(0xFFFF8A80), Color(0xFFFF5252), FontWeight.ExtraBold)
        } else {
            DiceBadgeColors(Color(0xFFFFDAD6), Color(0xFFB3261E), Color(0xFFFF5252), FontWeight.ExtraBold)
        }
    }
}
