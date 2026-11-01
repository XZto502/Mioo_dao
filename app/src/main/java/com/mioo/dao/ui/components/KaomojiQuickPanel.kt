package com.mioo.dao.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
    heightDp: Int = 220
) {
    val context = LocalContext.current.applicationContext
    val settings = remember(context) { SettingsDataStore(context) }
    val grid = rememberLazyGridState()
    val haptic = LocalHapticFeedback.current
    var order by remember { mutableStateOf(kaomojiById.keys.toList()) }
    var ready by remember { mutableStateOf(false) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var grabOffset by remember { mutableStateOf(Offset.Zero) }
    val edge = with(LocalDensity.current) { 36.dp.toPx() }
    val maxScroll = with(LocalDensity.current) { 8.dp.toPx() }

    LaunchedEffect(settings) {
        val saved = settings.kaomojiOrderFlow.first()
        order = restoreKaomojiOrder(saved, kaomojiById.keys.toList())
        ready = true
    }

    fun moveUnderPointer() {
        val id = draggingId ?: return
        val target = grid.layoutInfo.visibleItemsInfo.firstOrNull {
            it.key != id && pointer.x >= it.offset.x && pointer.x < it.offset.x + it.size.width &&
                pointer.y >= it.offset.y && pointer.y < it.offset.y + it.size.height
        } ?: return
        order = moveKaomoji(order, id, target.key as String)
    }

    fun finishDrag() {
        if (draggingId == null) return
        draggingId = null
        val saved = order.toList()
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
                            }?.let {
                                draggingId = it.key as String
                                pointer = position
                                grabOffset = position - Offset(it.offset.x.toFloat(), it.offset.y.toFloat())
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        }
                    },
                    onDrag = { change, delta ->
                        if (draggingId != null) {
                            change.consume()
                            pointer += delta
                            moveCurrent()
                        }
                    },
                    onDragEnd = { finishCurrent() },
                    onDragCancel = { finishCurrent() }
                )
            },
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalArrangement = Arrangement.Top
    ) {
        items(order, key = { it }, contentType = { "kaomoji" }) { id ->
            val item = kaomojiById.getValue(id)
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
                    ) { onInsert(item.raw) }
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 2.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item.preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
