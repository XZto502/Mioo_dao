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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
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

@Composable
fun HeaderImageSettingsCard() {
    val context = LocalContext.current
    val store = remember(context) { SettingsDataStore(context.applicationContext) }
    val settings by store.headerImageSettings.collectAsState(HeaderImageSettings())
    val scope = rememberCoroutineScope()
    var alpha by remember(settings.alpha) { mutableFloatStateOf(settings.alpha) }
    var cropUri by remember { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { cropUri = it }
    fun choose() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    Card(colors = CardDefaults.cardColors(containerColor = com.mioo.dao.ui.theme.readingCardColor()), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("自定义顶栏背景", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (settings.path.isNotBlank()) {
                AsyncImage(File(settings.path), "顶栏背景预览", Modifier.fillMaxWidth().aspectRatio(HEADER_RATIO).clip(MaterialTheme.shapes.small).clickable { choose() }, contentScale = ContentScale.Crop, alpha = alpha)
            }
            TextButton(onClick = { choose() }) { Text(if (settings.path.isBlank()) "选择图片并裁切" else "重新选择或裁切") }
            if (settings.path.isNotBlank()) {
                Text("图片浓度", style = MaterialTheme.typography.bodyMedium)
                Slider(value = alpha, onValueChange = { alpha = it }, valueRange = 0.2f..1f,
                    onValueChangeFinished = { scope.launch { store.saveHeaderImageAlpha(alpha) } })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("同时应用到串详情页", Modifier.weight(1f))
                    Switch(settings.applyToThread, { enabled -> scope.launch { store.saveHeaderApplyToThread(enabled) } })
                }
                TextButton(onClick = {
                    scope.launch {
                        store.saveHeaderImagePath("")
                        withContext(Dispatchers.IO) { cleanOldHeaders(context.filesDir, "") }
                    }
                }) { Text("恢复默认") }
            }
        }
    }
    cropUri?.let { uri ->
        HeaderCropDialog(uri, onDismiss = { cropUri = null }, onConfirm = { path ->
            store.saveHeaderImagePath(path)
            withContext(Dispatchers.IO) { cleanOldHeaders(context.filesDir, path) }
            cropUri = null
        })
    }
}

private fun cleanOldHeaders(directory: File, keep: String) {
    directory.listFiles()?.filter { it.name.startsWith("custom_header_") && it.extension == "jpg" && it.absolutePath != keep }
        ?.forEach { it.delete() }
}

@Composable
fun HeaderCropDialog(uri: Uri, onConfirm: suspend (String) -> Unit, onDismiss: () -> Unit) {
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
                            drawImage(imageBitmap, dstOffset = IntOffset(((size.width - width) / 2 + offset.x).roundToInt(), ((size.height - height) / 2 + offset.y).roundToInt()), dstSize = IntSize(width.roundToInt(), height.roundToInt()))
                            val top = (size.height - frameHeight) / 2
                            drawRect(Color.Black.copy(alpha = 0.6f), size = Size(size.width, top))
                            drawRect(Color.Black.copy(alpha = 0.6f), topLeft = Offset(0f, top + frameHeight), size = Size(size.width, top))
                            drawRect(Color.White, Offset(1f, top), Size(size.width - 2, frameHeight), style = Stroke(1.dp.toPx()))
                        }
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Menu, null, tint = Color.White)
                            Text("综合版1", Modifier.weight(1f).padding(start = 8.dp), color = Color.White)
                            Icon(Icons.Default.Refresh, null, tint = Color.White)
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
                                    val frameWidth = size.width.toFloat()
                                    val factor = max(frameWidth / image.width, frameWidth / HEADER_RATIO / image.height) * savedZoom
                                    val cropWidth = (frameWidth / factor).roundToInt().coerceIn(1, image.width)
                                    val cropHeight = (frameWidth / HEADER_RATIO / factor).roundToInt().coerceIn(1, image.height)
                                    val left = ((image.width - cropWidth) / 2f - savedOffset.x / factor).roundToInt().coerceIn(0, image.width - cropWidth)
                                    val top = ((image.height - cropHeight) / 2f - savedOffset.y / factor).roundToInt().coerceIn(0, image.height - cropHeight)
                                    val cropped = Bitmap.createBitmap(image, left, top, cropWidth, cropHeight)
                                    val outWidth = min(1440, cropWidth)
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

/** The image shares the app bar's measured bounds, including its status-bar inset. */
@Composable
fun CustomHeaderBar(thread: Boolean = false, content: @Composable (Boolean, Shadow) -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { SettingsDataStore(context.applicationContext) }
    val settings by store.headerImageSettings.collectAsState(HeaderImageSettings())
    val enabled = settings.path.isNotBlank() && (!thread || settings.applyToThread) && File(settings.path).isFile
    val background = MaterialTheme.colorScheme.background
    Box {
        if (enabled) {
            AsyncImage(File(settings.path), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop, alpha = settings.alpha)
            Canvas(Modifier.matchParentSize()) {
                drawRect(Brush.verticalGradient(
                    0f to background.copy(alpha = 0.2f),
                    0.5f to Color.Transparent,
                    (1f - 10.dp.toPx() / size.height).coerceIn(0.5f, 0.99f) to Color.Transparent,
                    1f to background.copy(alpha = 0.5f)
                ))
            }
        }
        content(enabled, if (enabled) Shadow(background.copy(alpha = 0.85f), blurRadius = 6f) else Shadow.None)
    }
}

@Composable
fun HeaderActionProtection(enabled: Boolean, content: @Composable () -> Unit) {
    Row(Modifier.then(if (enabled) Modifier.background(MaterialTheme.colorScheme.background.copy(alpha = 0.72f), MaterialTheme.shapes.small) else Modifier),
        verticalAlignment = Alignment.CenterVertically) { content() }
}
