package com.mioo.dao.utils

import android.app.Activity
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaScannerConnection
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.mioo.dao.ui.components.PostData
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object PostShareCardExporter {
    fun threadUrl(threadId: String) = "https://www.nmbxd1.com/t/$threadId"

    fun qrCode(threadId: String): ImageBitmap {
        val matrix = QRCodeWriter().encode(threadUrl(threadId), BarcodeFormat.QR_CODE, 276, 276,
            mapOf(EncodeHintType.MARGIN to 0))
        val pixels = IntArray(matrix.width * matrix.height) { i ->
            if (matrix[i % matrix.width, i / matrix.width]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
        return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    /** Resolve software bitmaps before measuring either the preview or the export. */
    suspend fun loadImages(context: Context, posts: List<PostData>): Map<String, ImageBitmap> =
        withContext(Dispatchers.IO) {
            posts.mapNotNull { post ->
                val url = post.imageUrl?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val result = context.imageLoader.execute(ImageRequest.Builder(context).data(url)
                    .allowHardware(false).size(1080, 1800).build())
                check(result is SuccessResult) { "No.${post.id} 的附图加载失败，请重试" }
                val drawable = result.drawable
                val sourceWidth = drawable.intrinsicWidth.coerceAtLeast(1)
                val sourceHeight = drawable.intrinsicHeight.coerceAtLeast(1)
                val scale = minOf(1f, 1080f / sourceWidth, 4000f / sourceHeight)
                post.id to drawable.toBitmap((sourceWidth * scale).toInt().coerceAtLeast(1),
                    (sourceHeight * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888).asImageBitmap()
            }.toMap()
        }

    /** A separate attached composition is measured with unlimited height, never the preview viewport. */
    suspend fun render(
        context: Context, parent: CompositionContext, content: @Composable () -> Unit
    ): Bitmap = withContext(Dispatchers.Main.immediate) {
        val activity = context.activity() ?: error("无法创建分享画布")
        val host = activity.window.decorView as ViewGroup
        val view = ComposeView(context).apply {
            setParentCompositionContext(parent)
            alpha = 0f
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(3f, 1f)) { content() }
            }
        }
        try {
            host.addView(view, ViewGroup.LayoutParams(1080, ViewGroup.LayoutParams.WRAP_CONTENT))
            view.createComposition()
            // Let inline text and composition settle, then measure independently of host constraints.
            withFrameNanos { }
            view.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            val fullHeight = view.measuredHeight.coerceAtLeast(1)
            view.layout(0, 0, 1080, fullHeight)
            val outputHeight = fullHeight.coerceAtMost(12000)
            val bitmap = Bitmap.createBitmap(1080, outputHeight, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(bitmap)
                // Preserve all floors even for extremely long posts: fit instead of cropping at 12000px.
                if (fullHeight > outputHeight) {
                    val scale = outputHeight.toFloat() / fullHeight
                    canvas.translate((1080f - 1080f * scale) / 2f, 0f)
                    canvas.scale(scale, scale)
                }
                view.draw(canvas)
                bitmap
            } catch (error: Throwable) {
                bitmap.recycle()
                throw error
            }
        } finally {
            view.disposeComposition()
            host.removeView(view)
        }
    }

    suspend fun saveToAlbum(context: Context, bitmap: Bitmap): Uri = withContext(Dispatchers.IO) {
        val name = "MiooDao_${System.currentTimeMillis()}.png"
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MiooDao")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("无法创建相册图片")
            try {
                resolver.openOutputStream(uri)?.use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                    ?: error("无法写入相册")
                check(resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null) > 0)
                uri
            } catch (error: Throwable) {
                resolver.delete(uri, null, null)
                throw error
            }
        } else {
            @Suppress("DEPRECATION")
            val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "MiooDao")
            check(directory.exists() || directory.mkdirs())
            val file = File(directory, name)
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("image/png"), null)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
    }

    /** Share a published, persistent local photo, so recipients can reopen it after returning. */
    fun shareImage(context: Context, uri: Uri) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, "分享卡片", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        // Some QQ/WeChat versions forward to another activity before reading the stream.
        context.packageManager.queryIntentActivities(send, PackageManager.MATCH_DEFAULT_ONLY).forEach {
            context.grantUriPermission(it.activityInfo.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "分享卡片图").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = send.clipData
        })
    }

    fun shareLink(context: Context, threadId: String, title: String) {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, "$title\n${threadUrl(threadId)}")
        }, "分享串链接"))
    }
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
