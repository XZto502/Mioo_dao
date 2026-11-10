package com.mioo.dao.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.mioo.dao.data.local.SettingsDataStore
import com.mioo.dao.data.local.CustomKaomoji
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

private val kaomojiSaveScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

// 内容指纹 + 同内容序号：重复颜文字也能独立排序，内置列表增补不会挪错用户顺序。
private val kaomojiById: Map<String, KaomojiItem> = buildMap {
    val occurrences = mutableMapOf<String, Int>()
    KAOMOJI_ITEMS.forEach { item ->
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(item.raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val occurrence = occurrences.getOrDefault(digest, 0)
        occurrences[digest] = occurrence + 1
        put("$digest:$occurrence", item)
    }
}

/** 长按拖动自定义顺序，点击仍插入完整颜文字。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KaomojiQuickPanel(
    onInsert: (String) -> Unit,
    modifier: Modifier = Modifier,
    heightDp: Int = 220,
    onEditingChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current.applicationContext
    val settings = remember(context) { SettingsDataStore(context) }
    val customItems by settings.customKaomojiFlow.collectAsState(initial = null)
    var editing by remember { mutableStateOf<CustomKaomoji?>(null) }
    // 只保存用户排序，不跨面板打开恢复滚动位置。
    val grid = remember { LazyGridState() }
    val haptic = LocalHapticFeedback.current
    // Wait for the saved order before composing cells, avoiding an animated initial reorder.
    var order by remember { mutableStateOf<List<String>>(emptyList()) }
    var savedOrder by remember { mutableStateOf<List<String>>(emptyList()) }
    var moved by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var grabOffset by remember { mutableStateOf(Offset.Zero) }
    val edge = with(LocalDensity.current) { 36.dp.toPx() }
    val maxScroll = with(LocalDensity.current) { 8.dp.toPx() }

    LaunchedEffect(settings) {
        val saved = settings.kaomojiOrderFlow.first()
        savedOrder = saved
        // 应用排序后显式定位首项，避免稳定 key 将原首项锚定到其新位置。
        grid.scrollToItem(0)
        ready = true
    }
    LaunchedEffect(customItems, ready) {
        val items = customItems
        if (ready && items != null && draggingId == null) {
            val keys = items.map { "custom:${it.id}" } + kaomojiById.keys
            val newCustomKeys = keys.filter { it.startsWith("custom:") && it !in savedOrder }
            order = restoreKaomojiOrder(newCustomKeys + savedOrder, keys)
        }
    }

    fun moveUnderPointer() {
        val id = draggingId ?: return
        val target = grid.layoutInfo.visibleItemsInfo.firstOrNull {
            it.key != id && it.key in order && pointer.x >= it.offset.x && pointer.x < it.offset.x + it.size.width &&
                pointer.y >= it.offset.y && pointer.y < it.offset.y + it.size.height
        } ?: return
        order = moveKaomoji(order, id, target.key as String)
    }

    fun finishDrag() {
        val id = draggingId ?: return
        draggingId = null
        if (id.startsWith("custom:") && !moved) {
            customItems?.firstOrNull { "custom:${it.id}" == id }?.let { onEditingChange(true); editing = it }
        }
        val saved = order.toList()
        savedOrder = saved
        // 使用应用级 DataStore；写入不依赖面板是否继续挂载。
        kaomojiSaveScope.launch { settings.saveKaomojiOrder(saved) }
    }

    val moveCurrent by rememberUpdatedState(newValue = { moveUnderPointer() })
    val finishCurrent by rememberUpdatedState(newValue = { finishDrag() })
    val readyCurrent by rememberUpdatedState(ready)

    // 边缘自动滚动，支持从第一屏拖到列表末尾；速度与帧率无关。
    LaunchedEffect(draggingId) {
        if (draggingId == null) return@LaunchedEffect
        var previousFrame = withFrameNanos { it }
        while (true) {
            val frame = withFrameNanos { it }
            val elapsed = ((frame - previousFrame) / 16_666_667f).coerceAtMost(3f)
            previousFrame = frame
            val info = grid.layoutInfo
            val speed = when {
                pointer.y < info.viewportStartOffset + edge ->
                    -((info.viewportStartOffset + edge - pointer.y) / edge).coerceIn(0f, 1f)
                pointer.y > info.viewportEndOffset - edge ->
                    ((pointer.y - info.viewportEndOffset + edge) / edge).coerceIn(0f, 1f)
                else -> 0f
            }
            if (speed != 0f) {
                grid.scrollBy(speed * maxScroll * elapsed)
                moveCurrent()
            }
        }
    }

    LazyVerticalGrid(
        state = grid,
        columns = GridCells.Fixed(KAOMOJI_PER_ROW),
        modifier = modifier.fillMaxWidth().height(heightDp.dp)
            .pointerInput(grid) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { position ->
                        if (readyCurrent) {
                            grid.layoutInfo.visibleItemsInfo.firstOrNull {
                                position.x >= it.offset.x && position.x < it.offset.x + it.size.width &&
                                    position.y >= it.offset.y && position.y < it.offset.y + it.size.height
                            }?.takeIf { it.key in order }?.let {
                                draggingId = it.key as String
                                moved = false
                                pointer = position
                                grabOffset = position - Offset(it.offset.x.toFloat(), it.offset.y.toFloat())
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        }
                    },
                    onDrag = { change, delta ->
                        if (draggingId != null) {
                            moved = true
                            change.consume()
                            pointer += delta
                            moveCurrent()
                        }
                    },
                    onDragEnd = { finishCurrent() },
                    onDragCancel = { moved = true; finishCurrent() }
                )
            },
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalArrangement = Arrangement.Top
    ) {
        item(key = "custom_add") {
            Box(Modifier.heightIn(min = 48.dp).padding(4.dp)
                .clickable { onEditingChange(true); editing = CustomKaomoji(java.util.UUID.randomUUID().toString(), "") }, contentAlignment = Alignment.Center) {
                Text("+", color = MaterialTheme.colorScheme.primary)
            }
        }
        items(order, key = { it }, contentType = { "kaomoji" }) { id ->
            val item = customItems?.firstOrNull { "custom:${it.id}" == id }
            if (id.startsWith("custom:")) {
            if (item != null) Box(Modifier.zIndex(if (draggingId == id) 1f else 0f)
                .then(if (draggingId == id) Modifier else Modifier.animateItemPlacement())
                .graphicsLayer {
                    if (draggingId == id) {
                        grid.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }?.let {
                            translationX = pointer.x - grabOffset.x - it.offset.x
                            translationY = pointer.y - grabOffset.y - it.offset.y
                        }
                        scaleX = 1.06f; scaleY = 1.06f
                    }
                }.background(
                    if (draggingId == id) MaterialTheme.colorScheme.secondaryContainer
                    else androidx.compose.ui.graphics.Color.Transparent,
                    RoundedCornerShape(10.dp)
                ).heightIn(min = 48.dp).padding(4.dp)
                .clickable(enabled = draggingId == null, interactionSource = remember { MutableInteractionSource() }, indication = null) { onInsert(item.text) }, contentAlignment = Alignment.Center) {
                Text(item.alias.trim().ifEmpty { item.text }, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            } else {
            val builtIn = kaomojiById.getValue(id)
            val dragging = draggingId == id
            Box(
                modifier = Modifier
                    .zIndex(if (dragging) 1f else 0f)
                    .then(if (dragging) Modifier else Modifier.animateItemPlacement())
                    .graphicsLayer {
                        if (dragging) {
                            val info = grid.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }
                            if (info != null) {
                                translationX = pointer.x - grabOffset.x - info.offset.x
                                translationY = pointer.y - grabOffset.y - info.offset.y
                            }
                            scaleX = 1.06f
                            scaleY = 1.06f
                        }
                    }
                    .background(
                        if (dragging) MaterialTheme.colorScheme.secondaryContainer
                        else androidx.compose.ui.graphics.Color.Transparent,
                        RoundedCornerShape(10.dp)
                    )
                    .clickable(
                        enabled = ready && draggingId == null,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onInsert(builtIn.raw) }
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 2.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = builtIn.preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            }
        }
    }
    editing?.let { item ->
        CustomKaomojiDialog(item, onDismiss = { editing = null; onEditingChange(false) }, onSave = { saved ->
            kaomojiSaveScope.launch { settings.updateCustomKaomoji(saved) }; editing = null; onEditingChange(false)
        }, onDelete = { deleted ->
            kaomojiSaveScope.launch { settings.updateCustomKaomoji(deleted, delete = true) }; editing = null; onEditingChange(false)
        })
    }
}

@Composable
fun CustomKaomojiDialog(item: CustomKaomoji, onDismiss: () -> Unit, onSave: (CustomKaomoji) -> Unit, onDelete: (CustomKaomoji) -> Unit) {
    var text by remember(item.id) { mutableStateOf(item.text) }
    var alias by remember(item.id) { mutableStateOf(item.alias) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (item.text.isBlank()) "添加自定义颜文字" else "编辑自定义颜文字") },
        text = {
            androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("颜文字内容") })
                OutlinedTextField(value = alias, onValueChange = { alias = it }, label = { Text("别名（选填）") },
                    supportingText = { Text("设置后将在面板中直接显示别名，不填则显示颜文字本身") })
            }
        },
        dismissButton = { androidx.compose.foundation.layout.Row {
            if (item.text.isNotBlank()) TextButton(onClick = { onDelete(item) }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = onDismiss) { Text("取消") }
        } },
        confirmButton = { TextButton(onClick = { onSave(item.copy(text = text, alias = alias)) }, enabled = text.isNotBlank()) { Text("保存") } })
}
