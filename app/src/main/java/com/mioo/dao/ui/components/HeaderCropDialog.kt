package com.mioo.dao.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.mioo.dao.data.local.HeaderImageSettings
import com.mioo.dao.data.local.SettingsDataStore
import kotlinx.coroutines.*
import java.io.File
import kotlin.math.*

private const val HEADER_RATIO = 3.8f

private data class HeaderCropRect(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int
)

private fun computeHeaderCropRect(
    imageWidth: Int,
    imageHeight: Int,
    frameWidth: Float,
    zoom: Float,
    offset: Offset
): HeaderCropRect? {
    if (imageWidth <= 0 || imageHeight <= 0 || frameWidth <= 0f || zoom <= 0f) return null
    val factor = max(frameWidth / imageWidth, frameWidth / HEADER_RATIO / imageHeight) * zoom
    val cropWidth = (frameWidth / factor).roundToInt().coerceIn(1, imageWidth)
    val cropHeight = (frameWidth / HEADER_RATIO / factor).roundToInt().coerceIn(1, imageHeight)
    val left = ((imageWidth - cropWidth) / 2f - offset.x / factor)
        .roundToInt()
        .coerceIn(0, imageWidth - cropWidth)
    val top = ((imageHeight - cropHeight) / 2f - offset.y / factor)
        .roundToInt()
        .coerceIn(0, imageHeight - cropHeight)
    return HeaderCropRect(left = left, top = top, width = cropWidth, height = cropHeight)
}

@Composable
fun HeaderImageSettingsContent() {
    val context = LocalContext.current
    val store = remember(context) { SettingsDataStore(context.applicationContext) }
    val headerData = rememberCustomHeaderData(thread = false)
    val settings = headerData.settings
    val scope = rememberCoroutineScope()
    var cropUri by remember { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { cropUri = it }
    fun choose() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    if (settings.path.isBlank()) {
        FilledTonalButton(onClick = { choose() }) { Text("选择图片并裁切") }
    } else {
        HeaderPreviewCard(
            path = settings.path,
            luminanceMap = headerData.luminanceMap,
            onClick = { choose() }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { choose() }) { Text("更换图片") }
            OutlinedButton(onClick = {
                scope.launch {
                    store.saveHeaderImagePath("")
                    withContext(Dispatchers.IO) { cleanOldHeaders(context.filesDir, "") }
                }
            }) { Text("清除顶图") }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("同时应用到串详情页", Modifier.weight(1f))
            Switch(settings.applyToThread, { enabled ->
                scope.launch { store.saveHeaderApplyToThread(enabled) }
            })
        }
    }
    cropUri?.let { uri ->
        HeaderCropDialog(
            uri = uri,
            onDismiss = { cropUri = null },
            onConfirm = { path ->
                store.saveHeaderImagePath(path)
                withContext(Dispatchers.IO) { cleanOldHeaders(context.filesDir, path) }
                cropUri = null
            }
        )
    }
}

@Composable
private fun HeaderPreviewCard(
    path: String,
    luminanceMap: HeaderLuminanceMap?,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    val density = LocalDensity.current
    var cardSize by remember { mutableStateOf(IntSize.Zero) }
    val style = resolveHeaderBarStyle(
        enabled = true,
        luminanceMap = luminanceMap,
        colorScheme = MaterialTheme.colorScheme,
        containerWidthPx = cardSize.width.toFloat(),
        containerHeightPx = cardSize.height.toFloat(),
        statusBarHeightPx = cardSize.height * 0.34f,
        barWidthDp = if (cardSize.width > 0) with(density) { cardSize.width.toDp().value } else 360f
    )
    val refreshColors = style.trailingActionAt(0)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp)
            .onSizeChanged { cardSize = it }
            .clip(shape)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), shape)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = File(path),
            contentDescription = "顶栏背景预览",
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Crop
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "12:30",
                    style = MaterialTheme.typography.labelSmall,
                    color = style.statusBarContentColor,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = null,
                        tint = style.statusBarContentColor,
                        modifier = Modifier.size(13.dp)
                    )
                    Icon(
                        imageVector = Icons.Default.BatteryFull,
                        contentDescription = null,
                        tint = style.statusBarContentColor,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = null,
                    tint = style.navigationContentColor,
                    modifier = Modifier.size(22.dp)
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    Text(
                        text = "综合版1",
                        style = MaterialTheme.typography.titleMedium,
                        color = style.titleContentColor,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = "X岛 · nmbxd.com",
                        style = MaterialTheme.typography.labelSmall,
                        color = style.subtitleContentColor,
                        maxLines = 1
                    )
                }
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = refreshColors.contentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

private fun cleanOldHeaders(directory: File, keep: String) {
    directory.listFiles()?.filter { it.name.startsWith("custom_header_") && it.extension == "jpg" && it.absolutePath != keep }
        ?.forEach { it.delete() }
}

@Composable
fun HeaderCropDialog(
    uri: Uri,
    onConfirm: suspend (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var error by remember(uri) { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var zoom by remember(uri) { mutableFloatStateOf(1f) }
    var offset by remember(uri) { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(uri) {
        try {
            bitmap = withContext(Dispatchers.IO) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                val options = BitmapFactory.Options().apply {
                    inSampleSize = 1
                    while (max(bounds.outWidth, bounds.outHeight) / inSampleSize > 4096) inSampleSize *= 2
                }
                val decoded = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                    ?: throw IllegalStateException("无法读取这张图片")
                val orientation = context.contentResolver.openInputStream(uri)?.use {
                    ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                } ?: ExifInterface.ORIENTATION_NORMAL
                val matrix = Matrix().apply {
                    when (orientation) {
                        2 -> setScale(-1f, 1f)
                        3 -> setRotate(180f)
                        4 -> setScale(1f, -1f)
                        5 -> { setRotate(90f); postScale(-1f, 1f) }
                        6 -> setRotate(90f)
                        7 -> { setRotate(270f); postScale(-1f, 1f) }
                        8 -> setRotate(270f)
                    }
                }
                if (matrix.isIdentity) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also { if (it !== decoded) decoded.recycle() }
            }
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) { error = e.localizedMessage ?: "图片加载失败" }
    }
    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = !saving)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
                Text("裁切顶栏背景", style = MaterialTheme.typography.titleLarge)
                Text("单指拖动构图，双指缩放", style = MaterialTheme.typography.bodySmall)
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val image = bitmap
                    if (image != null) {
                        val imageBitmap = remember(image) { image.asImageBitmap() }
                        val density = LocalDensity.current
                        val previewLuminanceMap = remember(image, canvasSize, zoom, offset) {
                            val rect = computeHeaderCropRect(
                                imageWidth = image.width,
                                imageHeight = image.height,
                                frameWidth = canvasSize.width.toFloat(),
                                zoom = zoom,
                                offset = offset
                            )
                            if (rect != null) {
                                computeCropRegionLuminanceMap(image, rect.left, rect.top, rect.width, rect.height)
                            } else {
                                null
                            }
                        }
                        val frameHeightPx = if (canvasSize.width > 0) canvasSize.width / HEADER_RATIO else 0f
                        val previewStyle = resolveHeaderBarStyle(
                            enabled = true,
                            luminanceMap = previewLuminanceMap,
                            colorScheme = MaterialTheme.colorScheme,
                            containerWidthPx = canvasSize.width.toFloat(),
                            containerHeightPx = frameHeightPx,
                            statusBarHeightPx = frameHeightPx * 0.34f,
                            barWidthDp = if (canvasSize.width > 0) with(density) { canvasSize.width.toDp().value } else 360f
                        )
                        val refreshColors = previewStyle.trailingActionAt(0)
                        val frameHeightDp = with(density) {
                            if (canvasSize.width > 0) (canvasSize.width / HEADER_RATIO).toDp() else 92.dp
                        }
                        Canvas(Modifier.fillMaxWidth().aspectRatio(1.1f).onSizeChanged { canvasSize = it }.pointerInput(image, saving) {
                            if (!saving) detectTransformGestures { centroid, pan, scale, _ ->
                                val width = size.width.toFloat()
                                val base = max(width / image.width, width / HEADER_RATIO / image.height)
                                val nextZoom = (zoom * scale).coerceIn(1f, 4f)
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val moved = centroid - center - (centroid - center - offset) * (nextZoom / zoom) + pan
                                val maxX = ((image.width * base * nextZoom - width) / 2).coerceAtLeast(0f)
                                val maxY = ((image.height * base * nextZoom - width / HEADER_RATIO) / 2).coerceAtLeast(0f)
                                offset = Offset(moved.x.coerceIn(-maxX, maxX), moved.y.coerceIn(-maxY, maxY))
                                zoom = nextZoom
                            }
                        }) {
                            val frameHeight = size.width / HEADER_RATIO
                            val base = max(size.width / image.width, frameHeight / image.height)
                            val width = image.width * base * zoom
                            val height = image.height * base * zoom
                            drawImage(
                                imageBitmap,
                                dstOffset = IntOffset(((size.width - width) / 2 + offset.x).roundToInt(), ((size.height - height) / 2 + offset.y).roundToInt()),
                                dstSize = IntSize(width.roundToInt(), height.roundToInt())
                            )
                            val top = (size.height - frameHeight) / 2
                            drawRect(
                                color = previewStyle.titleContentColor.copy(alpha = 0.85f),
                                topLeft = Offset(1f, top),
                                size = Size(size.width - 2, frameHeight),
                                style = Stroke(1.dp.toPx())
                            )
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(frameHeightDp)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "12:30",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = previewStyle.statusBarContentColor,
                                    fontWeight = FontWeight.Medium
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.Wifi, null, tint = previewStyle.statusBarContentColor, modifier = Modifier.size(13.dp))
                                    Icon(Icons.Default.BatteryFull, null, tint = previewStyle.statusBarContentColor, modifier = Modifier.size(14.dp))
                                }
                            }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Menu, null, tint = previewStyle.navigationContentColor)
                                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                    Text(
                                        text = "综合版1",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = previewStyle.titleContentColor,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "X岛 · nmbxd.com",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = previewStyle.subtitleContentColor,
                                        maxLines = 1
                                    )
                                }
                                Icon(Icons.Default.Refresh, null, tint = refreshColors.contentColor)
                            }
                        }
                    } else if (error != null) Text(error!!) else CircularProgressIndicator()
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") }
                    Button(enabled = bitmap != null && canvasSize.width > 0 && !saving, onClick = {
                        val image = bitmap ?: return@Button
                        val size = canvasSize
                        val savedZoom = zoom
                        val savedOffset = offset
                        saving = true
                        scope.launch {
                            var file: File? = null
                            try {
                                file = withContext(Dispatchers.IO) {
                                    val rect = computeHeaderCropRect(
                                        imageWidth = image.width,
                                        imageHeight = image.height,
                                        frameWidth = size.width.toFloat(),
                                        zoom = savedZoom,
                                        offset = savedOffset
                                    ) ?: throw IllegalStateException("无效裁切区域")
                                    val cropped = Bitmap.createBitmap(image, rect.left, rect.top, rect.width, rect.height)
                                    val outWidth = min(1440, rect.width)
                                    val output = Bitmap.createScaledBitmap(cropped, outWidth, (outWidth / HEADER_RATIO).roundToInt().coerceAtLeast(1), true)
                                    try {
                                        File(context.filesDir, "custom_header_${System.currentTimeMillis()}.jpg").also { result ->
                                            try { result.outputStream().use { check(output.compress(Bitmap.CompressFormat.JPEG, 92, it)) } }
                                            catch (e: Exception) { result.delete(); throw e }
                                        }
                                    } finally {
                                        if (output !== image) output.recycle()
                                        if (cropped !== output && cropped !== image) cropped.recycle()
                                    }
                                }
                                onConfirm(file!!.absolutePath)
                            } catch (e: CancellationException) { throw e
                            } catch (e: Exception) {
                                file?.delete()
                                Toast.makeText(context, "保存失败，请重试", Toast.LENGTH_SHORT).show()
                            } finally { saving = false }
                        }
                    }) { Text(if (saving) "正在保存" else "确认裁切") }
                }
            }
        }
    }
}

private data class CachedDecodedHeader(
    val path: String,
    val bitmap: ImageBitmap,
    val luminanceMap: HeaderLuminanceMap?
)

@Volatile private var cachedHeaderSettings = HeaderImageSettings()
@Volatile private var cachedHeaderDecoded: CachedDecodedHeader? = null

private fun decodeHeaderBitmapWithLuminance(path: String): CachedDecodedHeader? {
    val file = File(path)
    if (!file.isFile) return null
    val raw = BitmapFactory.decodeFile(path) ?: return null
    val luminanceMap = computeBitmapHeaderLuminanceMap(raw)
    return CachedDecodedHeader(path = path, bitmap = raw.asImageBitmap(), luminanceMap = luminanceMap).also {
        cachedHeaderDecoded = it
    }
}

@Composable
internal fun rememberCustomHeaderData(thread: Boolean = false): CustomHeaderData {
    val context = LocalContext.current
    val store = remember(context) { SettingsDataStore(context.applicationContext) }
    val settings by store.headerImageSettings.collectAsState(cachedHeaderSettings)
    SideEffect { cachedHeaderSettings = settings }
    val path = settings.path.takeIf { it.isNotBlank() && (!thread || settings.applyToThread) }
        ?: return CustomHeaderData(bitmap = null, settings = settings, luminanceMap = null)
    var decoded by remember(path) {
        mutableStateOf(
            cachedHeaderDecoded?.takeIf { it.path == path } ?: decodeHeaderBitmapWithLuminance(path)
        )
    }
    LaunchedEffect(path) {
        if (decoded == null) {
            decoded = withContext(Dispatchers.IO) { decodeHeaderBitmapWithLuminance(path) }
        }
    }
    return CustomHeaderData(
        bitmap = decoded?.bitmap,
        settings = settings,
        luminanceMap = decoded?.luminanceMap
    )
}

@Composable
fun CustomHeaderBar(
    thread: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (HeaderBarStyle) -> Unit
) {
    CustomHeaderBar(rememberCustomHeaderData(thread), modifier, content)
}

/**
 * The image shares the app bar's measured bounds, including its status-bar inset,
 * rendered cleanly without any scrim or gradient while foreground elements adapt per region.
 */
@Composable
internal fun CustomHeaderBar(
    headerData: CustomHeaderData,
    modifier: Modifier = Modifier,
    content: @Composable (HeaderBarStyle) -> Unit
) {
    val bitmap = headerData.bitmap
    val density = LocalDensity.current
    val statusBarHeightPx = WindowInsets.statusBars.getTop(density).toFloat()
    var barSize by remember { mutableStateOf(IntSize.Zero) }
    val style = resolveHeaderBarStyle(
        enabled = bitmap != null,
        luminanceMap = headerData.luminanceMap,
        colorScheme = MaterialTheme.colorScheme,
        containerWidthPx = barSize.width.toFloat(),
        containerHeightPx = barSize.height.toFloat(),
        statusBarHeightPx = statusBarHeightPx,
        barWidthDp = if (barSize.width > 0) with(density) { barSize.width.toDp().value } else 411f
    )
    val bleedPx = with(density) { 24.dp.roundToPx() }
    Box(
        modifier
            .onSizeChanged { barSize = it }
            .then(
                if (bitmap == null) Modifier.background(com.mioo.dao.ui.theme.readingTopBarColor())
                else Modifier
            )
    ) {
        if (bitmap != null) {
            Canvas(Modifier.matchParentSize()) {
                val w = size.width
                val h = size.height
                if (w > 0f && h > 0f) {
                    val iw = bitmap.width.toFloat()
                    val ih = bitmap.height.toFloat()
                    val scale = max(w / iw, h / ih)
                    val sw = (w / scale).roundToInt().coerceIn(1, bitmap.width)
                    val sh = (h / scale).roundToInt().coerceIn(1, bitmap.height)
                    val sx = ((bitmap.width - sw) / 2f).roundToInt().coerceIn(0, bitmap.width - sw)
                    val sy = ((bitmap.height - sh) / 2f).roundToInt().coerceIn(0, bitmap.height - sh)
                    val dw = w.roundToInt()
                    val dh = h.roundToInt()
                    drawImage(
                        image = bitmap,
                        srcOffset = IntOffset(sx, sy),
                        srcSize = IntSize(sw, sh),
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(dw, dh),
                        filterQuality = FilterQuality.High
                    )
                    // Clamp the right edge across the enter-slide margin so -20.dp never shows a gap.
                    val rightCol = (sx + sw - 1).coerceIn(0, bitmap.width - 1)
                    drawImage(
                        image = bitmap,
                        srcOffset = IntOffset(rightCol, sy),
                        srcSize = IntSize(1, sh),
                        dstOffset = IntOffset(dw, 0),
                        dstSize = IntSize(bleedPx, dh)
                    )
                }
            }
        }
        content(style)
    }
}

@Composable
fun HeaderActionRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) { content() }
}
