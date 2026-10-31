package com.mioo.dao.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mioo.dao.utils.DiceRulePreset

enum class ShareCardTheme(val label: String) {
    PAPER("暖白纸张"), NIGHT("深邃暗夜"), APP("跟随主题")
}

internal val shareQuoteRegex = Regex("(?:&gt;&gt;|>>)(?:No\\.)?(\\d+)", RegexOption.IGNORE_CASE)

internal fun shareContextIds(post: PostData, mainPost: PostData?, include: Boolean): List<String> {
    if (!include) return emptyList()
    val references = shareQuoteRegex.findAll(post.content).map { it.groupValues[1] }.distinct().toList()
    return references.ifEmpty { listOfNotNull(mainPost?.id?.takeUnless { it == post.id }) }
}

internal fun trimShareQuote(html: String): String = html.trim().replace(
    Regex("""^(?:\s|<br\s*/?>|&nbsp;)+|(?:\s|<br\s*/?>|&nbsp;)+$""", RegexOption.IGNORE_CASE), "").trim()

@Composable
fun PostShareCardContent(
    selectedPosts: List<PostData>, threadId: String, boardName: String, title: String,
    cardTheme: ShareCardTheme, hideCookie: Boolean, includeContext: Boolean,
    showBranding: Boolean,
    contextPosts: Map<String, PostData>, mainPost: PostData?, qrBitmap: ImageBitmap?,
    dicePreset: DiceRulePreset? = null,
    modifier: Modifier = Modifier
) {
    val appColors = MaterialTheme.colorScheme
    val palette = when (cardTheme) {
        ShareCardTheme.PAPER -> lightColorScheme(surface = Color(0xFFFAF9F6),
            background = Color(0xFFE6E9EF), onSurface = Color(0xFF22262C))
        ShareCardTheme.NIGHT -> darkColorScheme(surface = Color(0xFF181A20),
            background = Color(0xFF0D0F14), onSurface = Color(0xFFE3E6EA))
        ShareCardTheme.APP -> appColors
    }
    MaterialTheme(colorScheme = palette) {
        CompositionLocalProvider(LocalDicePreset provides dicePreset, LocalDiceRuleMenu provides {}) {
            // At export density 3, 64px margins/padding and a 56px corner radius.
            Column(modifier.background(palette.background).padding((64f / 3f).dp)) {
                Surface(shape = RoundedCornerShape((56f / 3f).dp), color = palette.surface) {
                    Column(Modifier.fillMaxWidth().padding((64f / 3f).dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            ShareTag(boardName, 11f)
                            Spacer(Modifier.width(8.dp))
                            Text(title.takeUnless { it == "无标题" }.orEmpty(),
                                fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Spacer(Modifier.width(8.dp))
                            Text("No.$threadId", style = shareMeta(11.5f),
                                color = palette.onSurfaceVariant.copy(alpha = 0.55f), maxLines = 1, softWrap = false)
                        }
                        Spacer(Modifier.height(12.dp))
                        ShareDivider()
                        Spacer(Modifier.height(14.dp))
                        val dark = palette.surface.luminance() < 0.5f
                        val selectedIds = selectedPosts.map { it.id }.toSet()
                        selectedPosts.forEachIndexed { index, post ->
                            if (index > 0) {
                                Spacer(Modifier.height(14.dp)); ShareDivider(); Spacer(Modifier.height(14.dp))
                            }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.height(22.dp)
                                    .background(if (dark) Color(0xFF23262F) else Color(0xFFEFF1F5), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                                    Text(if (hideCookie) "匿名岛民" else post.userId,
                                        style = shareMeta(11.5f).copy(fontWeight = FontWeight.Medium),
                                        maxLines = 1, softWrap = false)
                                }
                                if (post.isPo) {
                                    Spacer(Modifier.width(4.dp))
                                    Box(Modifier.height(22.dp).background(palette.secondaryContainer, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 5.dp), contentAlignment = Alignment.Center) {
                                        Text("PO", fontSize = 10.5.sp, fontWeight = FontWeight.Bold,
                                            color = palette.onSecondaryContainer, maxLines = 1)
                                    }
                                }
                                Spacer(Modifier.width(6.dp))
                                Text(formatReadingTime(post.createdAt), style = shareMeta(11.5f),
                                    color = palette.onSurfaceVariant.copy(alpha = 0.55f), maxLines = 1,
                                    overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.weight(1f))
                                Text("#${post.id}", style = shareMeta(11.5f), maxLines = 1, softWrap = false,
                                    color = palette.onSurfaceVariant.copy(alpha = 0.55f))
                            }
                            Spacer(Modifier.height(10.dp))
                            val contextIds = shareContextIds(post, mainPost, includeContext)
                            val resolved = contextIds.mapNotNull { contextPosts[it] }
                            contextIds.forEach { id ->
                                if (id in selectedIds) {
                                    Text("↳ 回复 #$id", style = shareMeta(11f), color = palette.onSurfaceVariant)
                                    Spacer(Modifier.height(8.dp))
                                } else {
                                    contextPosts[id]?.let { upstream ->
                                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)
                                            .background(palette.onSurface.copy(alpha = 0.04f), RoundedCornerShape(8.dp))
                                            .padding(10.dp)) {
                                            Box(Modifier.width(3.dp).fillMaxHeight()
                                                .background(palette.primary.copy(alpha = 0.45f), RoundedCornerShape(2.dp)))
                                            Spacer(Modifier.width(8.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text("No.$id", style = shareMeta(11f), color = palette.onSurfaceVariant)
                                                HtmlContent(trimShareQuote(upstream.content), {}, style = TextStyle(fontSize = 13.sp,
                                                    lineHeight = 19.sp, color = palette.onSurface.copy(alpha = 0.75f)),
                                                    maxLines = 4, overflow = TextOverflow.Ellipsis, enableGestures = false)
                                            }
                                        }
                                        Spacer(Modifier.height(10.dp))
                                    }
                                }
                            }
                            // Replace resolved labels only; missing reference bodies remain visible.
                            val blocks = if (includeContext) ContentBlockSplitter.split(post.content, resolved) else
                                listOf(PostContentBlock.Text(post.content))
                            blocks.filterIsInstance<PostContentBlock.Text>().forEach { block ->
                                HtmlContent(block.html, {}, style = TextStyle(fontSize = 15.sp, lineHeight = 23.5.sp,
                                    letterSpacing = 0.25.sp, fontWeight = FontWeight.Normal, color = palette.onSurface),
                                    enableGestures = false, modifier = Modifier.fillMaxWidth())
                            }

                        }
                        if (showBranding) {
                            Spacer(Modifier.height(16.dp)); ShareDivider(); Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("喵岛 MiooDao", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                        color = palette.onSurface.copy(alpha = 0.85f))
                                    Text("X岛揭示板 · 扫码直达原串", fontSize = 11.sp,
                                        color = palette.onSurfaceVariant.copy(alpha = 0.6f))
                                }
                                if (qrBitmap != null) Image(qrBitmap, "原串二维码",
                                    modifier = Modifier.size(54.dp).background(Color.White, RoundedCornerShape(10.dp)).padding(4.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun ShareTag(text: String, size: Float) {
    val primary = MaterialTheme.colorScheme.primary
    Box(Modifier.height(22.dp).background(primary.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
        .padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
        Text(text, fontSize = size.sp, fontWeight = FontWeight.Bold, maxLines = 1,
            overflow = TextOverflow.Ellipsis, color = primary)
    }
}

@Composable private fun ShareDivider() = Divider(thickness = 0.5.dp,
    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

private fun shareMeta(size: Float) = TextStyle(fontSize = size.sp, lineHeight = 16.sp,
    fontFeatureSettings = "tnum", fontWeight = FontWeight.Normal)
