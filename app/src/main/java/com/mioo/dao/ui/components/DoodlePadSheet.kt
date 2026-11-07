package com.mioo.dao.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as BitmapCanvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AutoFixNormal
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.mioo.dao.ui.theme.isReducedMotionEnabled
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val inkColors = listOf(
    Color(0xFF1E2022) to "炭黑",
    Color(0xFFE53935) to "红色",
    Color(0xFFFFB300) to "黄色",
    Color(0xFF43A047) to "绿色",
    Color(0xFF1E88E5) to "蓝色",
    Color.White to "白色"
)

private class DoodlePath(
    val color: Color,
    val width: Float,
    val isEraser: Boolean,
    val start: Offset
) {
    val path = Path().apply { moveTo(start.x, start.y) }
    private var previous = start
    var sampleCount by mutableIntStateOf(0)
        private set

    fun append(point: Offset) {
        if (point == previous) return
        val middle = (previous + point) / 2f
        path.quadTo(previous.x, previous.y, middle.x, middle.y)
        previous = point
        sampleCount++
    }

    fun finish() {
        if (sampleCount > 0) path.lineTo(previous.x, previous.y)
    }
}

private fun fitBoard(board: IntSize, width: Float, height: Float): RectF {
    val scale = minOf(width / board.width, height / board.height)
    val halfWidth = board.width * scale / 2
    val halfHeight = board.height * scale / 2
    return RectF(width / 2 - halfWidth, height / 2 - halfHeight, width / 2 + halfWidth, height / 2 + halfHeight)
}

private class DoodleRenderer {
    private val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val erase = PorterDuffXfermode(PorterDuff.Mode.CLEAR)

    fun draw(
        canvas: BitmapCanvas,
        board: IntSize,
        area: RectF,
        base: Bitmap?,
        paths: List<DoodlePath>,
        active: DoodlePath? = null
    ) {
        canvas.save()
        canvas.clipRect(area)
        if (base != null) canvas.drawBitmap(base, null, area, imagePaint)
        if (paths.isNotEmpty() || active != null) {
            // Only the ink is erased. The original image and paper live below this layer.
            canvas.saveLayer(area, null)
            canvas.translate(area.left, area.top)
            canvas.scale(area.width() / board.width, area.height() / board.height)
            for (stroke in paths) drawStroke(canvas, stroke)
            active?.let { drawStroke(canvas, it) }
            canvas.restore()
        }
        canvas.restore()
    }

    private fun drawStroke(canvas: BitmapCanvas, stroke: DoodlePath) {
        ink.color = stroke.color.toArgb()
        ink.xfermode = if (stroke.isEraser) erase else null
        ink.strokeWidth = stroke.width
        val moved = stroke.sampleCount > 0
        ink.style = if (moved) Paint.Style.STROKE else Paint.Style.FILL
        if (moved) canvas.drawPath(stroke.path, ink)
        else canvas.drawCircle(stroke.start.x, stroke.start.y, stroke.width / 2, ink)
    }
}

private suspend fun exportDoodle(
    context: Context,
    board: IntSize,
    base: Bitmap?,
    paths: List<DoodlePath>
): Uri = withContext(Dispatchers.IO) {
    val scale = minOf(1f, 2560f / maxOf(board.width, board.height))
    val width = (board.width * scale).roundToInt().coerceAtLeast(1)
    val height = (board.height * scale).roundToInt().coerceAtLeast(1)
    val file = File.createTempFile("doodle_", ".jpg", context.cacheDir)
    try {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(android.graphics.Color.WHITE)
            DoodleRenderer().draw(BitmapCanvas(bitmap), board, RectF(0f, 0f, width.toFloat(), height.toFloat()), base, paths)
            file.outputStream().use {
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 96, it)) throw IOException("无法保存涂鸦")
            }
        } finally {
            bitmap.recycle()
        }
        Uri.fromFile(compressImageUnder2MB(context, Uri.fromFile(file)))
    } finally {
        file.delete()
    }
}

@Composable
internal fun DoodlePadSheet(
    baseImageUri: Uri?,
    onDismiss: () -> Unit,
    onExport: (Uri) -> Unit
) {
    val context = LocalContext.current
    val source = remember { baseImageUri }
    val scope = rememberCoroutineScope()
    val dismiss by rememberUpdatedState(onDismiss)
    val export by rememberUpdatedState(onExport)
    val reduced = isReducedMotionEnabled()
    val progress = remember { Animatable(0f) }
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    val page = if (dark) Color(0xFF0E1015) else Color(0xFFF0F1F4)
    val paper = if (dark) Color(0xFF16181E) else Color.White
    val paths = remember { mutableStateListOf<DoodlePath>() }
    val redoPaths = remember { mutableStateListOf<DoodlePath>() }
    val renderer = remember { DoodleRenderer() }
    var base by remember { mutableStateOf<Bitmap?>(null) }
    var board by remember { mutableStateOf(IntSize.Zero) }
    var loading by remember { mutableStateOf(source != null) }
    var exporting by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var loadFailed by remember { mutableStateOf(false) }
    var exportFailed by remember { mutableStateOf(false) }
    var color by remember { mutableStateOf(inkColors.first().first) }
    var brushWidth by remember { mutableFloatStateOf(12f) }
    var eraser by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf<DoodlePath?>(null) }
    val busy = exporting || closing
    val canEdit = !busy && !loading && !loadFailed && board != IntSize.Zero

    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(if (reduced) 0 else 240, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(source) {
        if (source == null) return@LaunchedEffect
        try {
            val result = context.imageLoader.execute(
                ImageRequest.Builder(context).data(source).size(2560).allowHardware(false).build()
            ) as? SuccessResult ?: throw IOException("无法读取图片")
            val bitmap = withContext(Dispatchers.IO) { result.drawable.toBitmap() }
            base = bitmap
            board = IntSize(bitmap.width, bitmap.height)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            loadFailed = true
        } finally {
            loading = false
        }
    }

    fun close() {
        if (closing || exporting) return
        closing = true
        scope.launch {
            progress.animateTo(0f, tween(if (reduced) 0 else 180, easing = FastOutSlowInEasing))
            dismiss()
        }
    }

    BackHandler { close() }
    Column(
        Modifier.fillMaxSize().zIndex(110f)
            .graphicsLayer { alpha = progress.value; translationY = (1f - progress.value) * 18.dp.toPx() }
            .background(page)
            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false) } }
    ) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = ::close, enabled = !busy, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Close, "关闭")
            }
            Text("涂鸦", Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface)
            IconButton(onClick = { redoPaths.add(paths.removeAt(paths.lastIndex)) }, enabled = canEdit && paths.isNotEmpty()) {
                Icon(Icons.AutoMirrored.Outlined.Undo, "撤销")
            }
            IconButton(onClick = { paths.add(redoPaths.removeAt(redoPaths.lastIndex)) }, enabled = canEdit && redoPaths.isNotEmpty()) {
                Icon(Icons.AutoMirrored.Outlined.Redo, "重做")
            }
            IconButton(onClick = { paths.clear(); redoPaths.clear() }, enabled = canEdit && paths.isNotEmpty()) {
                Icon(Icons.Outlined.DeleteOutline, "清空")
            }
            Button(
                onClick = {
                    exporting = true
                    exportFailed = false
                    scope.launch {
                        try {
                            val uri = exportDoodle(context, board, base, paths.toList())
                            export(uri)
                            exporting = false
                            close()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            exportFailed = true
                        } finally {
                            exporting = false
                        }
                    }
                },
                enabled = canEdit && active == null,
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 14.dp)
            ) {
                Text(if (exporting) "保存中" else "完成")
            }
        }

        Surface(
            Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp, vertical = 8.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color.White
        ) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(
                    Modifier.fillMaxSize()
                        .onSizeChanged {
                            if (source == null && it.width > 0 && it.height > 0 &&
                                paths.isEmpty() && redoPaths.isEmpty() && active == null && !busy
                            ) board = it
                        }
                        .pointerInput(canEdit, board, color, brushWidth, eraser) {
                            if (!canEdit) return@pointerInput
                            val area = fitBoard(board, size.width.toFloat(), size.height.toFloat())
                            val scale = area.width() / board.width
                            fun point(position: Offset) =
                                Offset((position.x - area.left) / scale, (position.y - area.top) / scale)

                            awaitEachGesture {
                                val down = awaitFirstDown()
                                if (!area.contains(down.position.x, down.position.y)) return@awaitEachGesture
                                down.consume()
                                val stroke = DoodlePath(color, brushWidth.dp.toPx() / scale, eraser, point(down.position))
                                active = stroke
                                try {
                                    while (true) {
                                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                        if (change.isConsumed) break
                                        stroke.append(point(change.position))
                                        change.consume()
                                        if (!change.pressed) {
                                            stroke.finish()
                                            paths.add(stroke)
                                            redoPaths.clear()
                                            break
                                        }
                                    }
                                } finally {
                                    active = null
                                }
                            }
                        }
                ) {
                    if (board != IntSize.Zero) {
                        renderer.draw(drawContext.canvas.nativeCanvas, board, fitBoard(board, size.width, size.height), base, paths, active)
                    }
                }
                if (loading) CircularProgressIndicator()
                if (loadFailed) Text("无法打开底图", color = Color(0xFF2B2F36))
            }
        }
        if (exportFailed) Text("保存失败，请重试", Modifier.padding(horizontal = 16.dp), color = scheme.error)
        Surface(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
            shape = RoundedCornerShape(20.dp),
            color = paper
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for ((swatch, label) in inkColors) {
                            val selected = color == swatch && !eraser
                            val scale by animateFloatAsState(if (selected) 0.76f else 1f, label = "ink swatch")
                            Box(
                                Modifier.size(26.dp).clip(CircleShape)
                                    .border(if (selected) 2.dp else 0.5.dp, if (selected) scheme.primary else scheme.outlineVariant, CircleShape)
                                    .clickable(enabled = canEdit, onClickLabel = label) { color = swatch; eraser = false },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(Modifier.size(22.dp).graphicsLayer { scaleX = scale; scaleY = scale }
                                    .background(swatch, CircleShape))
                            }
                        }
                    }
                    Slider(brushWidth, { brushWidth = it }, Modifier.weight(1f).padding(start = 12.dp),
                        enabled = canEdit, valueRange = 4f..42f)
                }
                Row(Modifier.fillMaxWidth().height(36.dp).background(
                    if (dark) Color(0xFF22252D) else Color(0xFFF2F4F8), CircleShape
                )) {
                    for (useEraser in listOf(false, true)) {
                        val selected = eraser == useEraser
                        Row(
                            Modifier.weight(1f).fillMaxHeight().clip(CircleShape)
                                .background(if (selected) scheme.secondaryContainer else Color.Transparent)
                                .clickable(enabled = canEdit) { eraser = useEraser },
                            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val tint = if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant
                            Icon(if (useEraser) Icons.Outlined.AutoFixNormal else Icons.Outlined.Brush, null, Modifier.size(16.dp), tint)
                            Text(if (useEraser) "橡皮擦" else "画笔", color = tint, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}
