package com.mioo.dao.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mioo.dao.data.local.QuickInputItem
import com.mioo.dao.data.local.SettingsDataStore
import com.mioo.dao.data.local.defaultQuickInputs
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuickInputBar(onInsert: (QuickInputItem) -> Unit, onUndo: () -> Unit, onRedo: () -> Unit,
    canUndo: Boolean, canRedo: Boolean, enabled: Boolean = true) {
    val context = LocalContext.current.applicationContext
    val store = remember(context) { SettingsDataStore(context) }
    val items by store.quickInputsFlow.collectAsState(initial = defaultQuickInputs)
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<QuickInputItem?>(null) }
    val pillShape = RoundedCornerShape(50)
    val pillColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val scrollState = rememberLazyListState()
    Row(Modifier.fillMaxWidth().height(42.dp).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.height(30.dp).background(pillColor, pillShape), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onUndo, enabled = enabled && canUndo, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Undo, "撤销", Modifier.size(18.dp)) }
        IconButton(onClick = onRedo, enabled = enabled && canRedo, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Redo, "重做", Modifier.size(18.dp)) }
        }
        LazyRow(Modifier.weight(1f).fillMaxHeight()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val fade = 12.dp.toPx().coerceAtMost(size.width / 2f)
                val rtl = layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl
                if (fade > 0f && (if (rtl) scrollState.canScrollForward else scrollState.canScrollBackward)) {
                    drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), endX = fade),
                        size = Size(fade, size.height), blendMode = BlendMode.DstIn)
                }
                if (fade > 0f && (if (rtl) scrollState.canScrollBackward else scrollState.canScrollForward)) {
                    val edge = size.width - fade
                    drawRect(Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = edge, endX = size.width),
                        topLeft = Offset(edge, 0f), size = Size(fade, size.height), blendMode = BlendMode.DstIn)
                }
            }, state = scrollState, contentPadding = PaddingValues(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            items(items, key = { it.id }) { item ->
                Surface(shape = pillShape, color = pillColor,
                    modifier = Modifier.height(28.dp).widthIn(min = 36.dp).combinedClickable(enabled = enabled,
                        onClick = { onInsert(item) }, onLongClick = { editing = item })) {
                    Box(Modifier.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                        val label = when (val stored = item.label.ifBlank { item.content }) {
                            "......" -> "……"
                            "「」", "「 」" -> "「  」"
                            "『』", "『 』" -> "『  』"
                            "【】", "【 】" -> "【  】"
                            else -> stored
                        }
                        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface, maxLines = 1,
                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false), lineHeight = 13.sp))
                    }
                }
            }
            item {
                Surface(onClick = { editing = QuickInputItem(java.util.UUID.randomUUID().toString(), "", "") }, enabled = enabled,
                    shape = pillShape, color = pillColor, modifier = Modifier.size(36.dp, 28.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text("+") }
                }
            }
        }
    }
    editing?.let { item ->
        var content by remember(item.id) { mutableStateOf(item.content) }
        var label by remember(item.id) { mutableStateOf(item.label) }
        var paired by remember(item.id) { mutableStateOf(item.cursorOffsetFromEnd > 0) }
        AlertDialog(onDismissRequest = { editing = null }, title = { Text(if (item.content.isEmpty()) "添加快捷输入" else "编辑快捷输入") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(content, { content = it }, label = { Text("快捷输入内容") })
                OutlinedTextField(label, { label = it }, label = { Text("按钮显示名称 / 别名（选填）") }, supportingText = { Text("不填则直接显示输入内容") })
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(paired, { paired = it }); Text("成对符号（插入后光标停在正中间）") }
            } },
            dismissButton = { Row {
                if (item.content.isNotEmpty()) TextButton(onClick = { scope.launch { store.updateQuickInput(item, true) }; editing = null }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = { editing = null }) { Text("取消") }
            } }, confirmButton = { TextButton(enabled = content.isNotBlank(), onClick = {
                val closeIndex = content.indexOf("[/")
                val offset = if (!paired) 0 else if (closeIndex > 0) content.length - closeIndex else content.length / 2
                val display = label.trim().ifEmpty { if (paired && content.length == 2) "${content[0]} ${content[1]}" else content }
                scope.launch { store.updateQuickInput(item.copy(content = content, label = display, cursorOffsetFromEnd = offset)) }
                editing = null
            }) { Text("保存") } })
    }
}
