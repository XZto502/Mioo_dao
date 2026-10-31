package com.mioo.dao.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.Role
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.mioo.dao.utils.DiceRulePreset
import com.mioo.dao.utils.PostShareCardExporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun PostShareCardDialog(
    initialPostId: String, posts: List<PostData>, threadId: String, title: String,
    boardName: String, mainPost: PostData?,
    resolveContexts: suspend (List<PostData>) -> Map<String, PostData>,
    showBranding: Boolean, onBrandingChange: (Boolean) -> Unit,
    dicePreset: DiceRulePreset?, onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val parent = rememberCompositionContext()
    var selectedIds by rememberSaveable(initialPostId) { mutableStateOf(listOf(initialPostId)) }
    var theme by remember { mutableStateOf(ShareCardTheme.PAPER) }
    var hideCookie by rememberSaveable { mutableStateOf(false) }
    var includeContext by rememberSaveable { mutableStateOf(true) }
    var branding by rememberSaveable { mutableStateOf(showBranding) }
    var showSelection by rememberSaveable { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    val selectedPosts = remember(posts, selectedIds) { posts.filter { it.id in selectedIds } }
    var contextPosts by remember { mutableStateOf(posts.associateBy { it.id }) }
    var contextLoading by remember { mutableStateOf(true) }
    LaunchedEffect(selectedPosts, includeContext) {
        contextLoading = true
        try {
            contextPosts = if (includeContext) resolveContexts(selectedPosts) else emptyMap()
        } finally { contextLoading = false }
    }
    val qr = remember(threadId) { PostShareCardExporter.qrCode(threadId) }

    var preview by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var previewError by remember { mutableStateOf<String?>(null) }
    var previewLoading by remember { mutableStateOf(true) }
    LaunchedEffect(selectedPosts, theme, hideCookie, includeContext, branding, contextPosts, boardName) {
        previewLoading = true
        previewError = null
        try {
            preview = PostShareCardExporter.render(context, parent) {
                PostShareCardContent(selectedPosts, threadId, boardName, title, theme,
                    hideCookie, includeContext, branding, contextPosts, mainPost, qr,
                    dicePreset, Modifier.width(360.dp))
            }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (error: Exception) { previewError = error.message ?: "预览生成失败"
        } catch (_: OutOfMemoryError) { previewError = "图片过大，请减少勾选楼层后重试"
        } finally { previewLoading = false }
    }

    fun export(save: Boolean) {
        if (exporting || (includeContext && contextLoading) || selectedPosts.isEmpty()) return
        exporting = true
        // Capture one immutable export configuration; later UI changes cannot change the image midway.
        val exportPosts = selectedPosts
        val exportTheme = theme
        val exportHide = hideCookie
        val exportContext = includeContext
        val exportBranding = branding
        val exportContexts = contextPosts
        scope.launch {
            var bitmap: android.graphics.Bitmap? = null
            try {
                bitmap = PostShareCardExporter.render(context, parent) {
                    PostShareCardContent(exportPosts, threadId, boardName, title, exportTheme,
                        exportHide, exportContext, exportBranding, exportContexts,
                        mainPost, qr, dicePreset, Modifier.width(360.dp))
                }
                val savedUri = PostShareCardExporter.saveToAlbum(context, bitmap)
                Toast.makeText(context, "已保存至 Pictures/MiooDao", Toast.LENGTH_LONG).show()
                if (!save) PostShareCardExporter.shareImage(context, savedUri)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) {
                Toast.makeText(context, error.message ?: "生成失败，请重试", Toast.LENGTH_LONG).show()
            } catch (_: OutOfMemoryError) {
                Toast.makeText(context, "图片过大，请减少勾选楼层后重试", Toast.LENGTH_LONG).show()
            } finally {
                bitmap?.recycle()
                exporting = false
            }
        }
    }
    var pendingSaveOnly by remember { mutableStateOf(true) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) export(pendingSaveOnly) else Toast.makeText(context, "保存和分享图片需要存储权限，仍可分享文本链接", Toast.LENGTH_LONG).show()
    }
    fun requestExport(saveOnly: Boolean) {
        pendingSaveOnly = saveOnly
        if (Build.VERSION.SDK_INT < 29 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else export(saveOnly)
    }
    BackHandler { if (!exporting) onDismiss() }
    Box(Modifier.fillMaxSize().zIndex(100f).background(MaterialTheme.colorScheme.surface)
        .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
            indication = null) { }) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("生成分享卡片", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { showSelection = !showSelection }, enabled = !exporting) {
                    Text("选楼 ${selectedPosts.size}/8")
                }
                IconButton(onClick = onDismiss, enabled = !exporting) { Icon(Icons.Default.Close, "关闭") }
            }
                if (showSelection) Column(Modifier.fillMaxWidth().heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                    posts.forEach { post ->
                            val checked = post.id in selectedIds
                            val enabled = !exporting && (checked && selectedIds.size > 1 || !checked && selectedIds.size < 8)
                            fun toggle() { selectedIds = if (checked) selectedIds - post.id else selectedIds + post.id }
                            Row(Modifier.fillMaxWidth().clickable(enabled = enabled) { toggle() },
                                verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = checked, onCheckedChange = { toggle() }, enabled = enabled)
                                val summary = remember(post.content) {
                                    post.content.replace(Regex("<[^>]*>"), " ").decodeHtmlEntities().replace(Regex("\\s+"), " ").take(20)
                                }
                                Text("#${post.id} · ${if (hideCookie) "匿名岛民" else post.userId}: $summary",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                preview?.let { bitmap ->
                    Image(bitmap.asImageBitmap(), "分享卡片预览", contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat() / bitmap.height))
                }
                if (previewLoading || includeContext && contextLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                previewError?.let { Text(it, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(Modifier.fillMaxWidth().height(40.dp), shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
                    Row(Modifier.fillMaxSize().padding(3.dp)) {
                        ShareCardTheme.entries.forEach { option ->
                            Surface(Modifier.weight(1f).fillMaxHeight(), shape = CircleShape,
                                color = if (theme == option) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent) {
                                Box(Modifier.fillMaxSize().clickable(enabled = !exporting, role = Role.RadioButton) { theme = option },
                                    contentAlignment = Alignment.Center) {
                                    Text(option.label, fontSize = 12.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShareOption("隐藏饼干", hideCookie, !exporting, Modifier.weight(1f)) { hideCookie = it }
                    ShareOption("品牌标识", branding, !exporting, Modifier.weight(1f)) {
                        branding = it; onBrandingChange(it)
                    }
                    ShareOption("附带引用", includeContext, !exporting, Modifier.weight(1f)) { includeContext = it }
                }
            }
            if (exporting) LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { PostShareCardExporter.shareLink(context, threadId, title) }, enabled = !exporting) { Text("仅分享链接") }
                FilledTonalButton(onClick = { requestExport(true) }, enabled = !exporting && !previewLoading && previewError == null && (!includeContext || !contextLoading)) { Text("保存相册") }
                Button(onClick = { requestExport(false) }, enabled = !exporting && !previewLoading && previewError == null && (!includeContext || !contextLoading)) { Text("分享卡片图") }
            }
        }
    }
}

@Composable private fun ShareOption(
    label: String, checked: Boolean, enabled: Boolean, modifier: Modifier, onChange: (Boolean) -> Unit
) {
    Surface(modifier.height(36.dp), shape = RoundedCornerShape(18.dp),
        color = if (checked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)) {
        Row(Modifier.fillMaxSize().clickable(enabled = enabled, role = Role.Checkbox) { onChange(!checked) }
            .padding(horizontal = 5.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center) {
            if (checked) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(3.dp))
            }
            Text(label, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
