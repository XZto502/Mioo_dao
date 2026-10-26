package com.mioo.dao.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

object ImageDownloader {

    private const val MAX_BYTES = 20 * 1024 * 1024

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    fun downloadImage(
        context: Context,
        imageUrl: String,
        scope: CoroutineScope
    ) {
        downloadImageTransformed(context, imageUrl, 0, false, false, scope)
    }

    /**
     * 保存图片（可选几何变换）：有旋转/镜像时经 [android.graphics.Matrix] 生成变换后的
     * Bitmap 再入相册，用户把倒立图/反字梗图转正后存下来的就是转正效果；
     * 无变换时保留 GIF/PNG/WEBP 原格式直存。
     */
    fun downloadImageTransformed(
        context: Context,
        imageUrl: String,
        rotationDegrees: Int,
        flipX: Boolean,
        flipY: Boolean,
        scope: CoroutineScope
    ) {
        val appContext = context.applicationContext
        val transformed = rotationDegrees % 360 != 0 || flipX || flipY
        Toast.makeText(appContext, "正在开始保存图片...", Toast.LENGTH_SHORT).show()

        scope.launch(Dispatchers.IO) {
            try {
                val bytes = fetchBytes(imageUrl) ?: run {
                    toast(appContext, "图片下载失败")
                    return@launch
                }
                if (!transformed) {
                    val sniffed = sniffImage(bytes)
                    if (sniffed != null) {
                        val (ext, mime) = sniffed
                        val saved = saveBytes(appContext, bytes, "mioo_image_${System.currentTimeMillis()}.$ext", mime)
                        toast(appContext, if (saved != null) "图片已成功保存到相册" else "保存失败")
                        return@launch
                    }
                }
                val src = decodeBounded(bytes) ?: run {
                    toast(appContext, "图片转换失败")
                    return@launch
                }
                val bitmap = transformBitmap(src, rotationDegrees, flipX, flipY)
                try {
                    val saved = saveBitmap(appContext, bitmap, "mioo_image_${System.currentTimeMillis()}.jpg")
                    toast(appContext, if (saved != null) "图片已成功保存到相册" else "保存失败")
                } finally {
                    if (bitmap !== src) bitmap.recycle()
                    src.recycle()
                }
            } catch (_: OutOfMemoryError) {
                toast(appContext, "图片太大，无法保存")
            } catch (_: Exception) {
                toast(appContext, "保存失败")
            }
        }
    }

    /**
     * 分享图片（可选几何变换）：写入应用外部专属 share 目录（file_paths 已覆盖），
     * 经 FileProvider 发 ACTION_SEND。无变换且为原格式时分享原文件。
     */
    fun shareImageTransformed(
        context: Context,
        imageUrl: String,
        rotationDegrees: Int,
        flipX: Boolean,
        flipY: Boolean,
        scope: CoroutineScope
    ) {
        val appContext = context.applicationContext
        val transformed = rotationDegrees % 360 != 0 || flipX || flipY
        Toast.makeText(appContext, "正在准备分享...", Toast.LENGTH_SHORT).show()

        scope.launch(Dispatchers.IO) {
            try {
                val bytes = fetchBytes(imageUrl) ?: run {
                    toast(appContext, "图片下载失败")
                    return@launch
                }
                val stamp = System.currentTimeMillis()
                if (!transformed) {
                    val sniffed = sniffImage(bytes)
                    if (sniffed != null) {
                        val (ext, mime) = sniffed
                        shareBytes(appContext, bytes, "mioo_share_$stamp.$ext", mime)
                        return@launch
                    }
                }
                val src = decodeBounded(bytes) ?: run {
                    toast(appContext, "图片转换失败")
                    return@launch
                }
                val bitmap = transformBitmap(src, rotationDegrees, flipX, flipY)
                try {
                    val out = ByteArrayOutputStream()
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)) {
                        toast(appContext, "图片转换失败")
                        return@launch
                    }
                    shareBytes(appContext, out.toByteArray(), "mioo_share_$stamp.jpg", "image/jpeg")
                } finally {
                    if (bitmap !== src) bitmap.recycle()
                    src.recycle()
                }
            } catch (_: OutOfMemoryError) {
                toast(appContext, "图片太大，无法分享")
            } catch (_: Exception) {
                toast(appContext, "分享失败")
            }
        }
    }

    private suspend fun fetchBytes(imageUrl: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(imageUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body ?: return@withContext null
                readCapped(body.byteStream(), MAX_BYTES)
            }
        } catch (_: Exception) {
            null
        }
    }

    /** 按 [rotationDegrees]/镜像合成新 Bitmap；无变换时原样返回 [src]（调用方按引用判等回收）。 */
    private fun transformBitmap(src: Bitmap, rotationDegrees: Int, flipX: Boolean, flipY: Boolean): Bitmap {
        if (rotationDegrees % 360 == 0 && !flipX && !flipY) return src
        val matrix = android.graphics.Matrix()
        matrix.postScale(if (flipX) -1f else 1f, if (flipY) -1f else 1f)
        if (rotationDegrees % 360 != 0) matrix.postRotate(rotationDegrees.toFloat())
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    private fun shareBytes(context: Context, bytes: ByteArray, filename: String, mime: String) {
        // QQ 等应用对第三方 FileProvider（Android/data 私有目录）的 URI 会报"文件不存在"，
        // Q+ 改走 MediaStore content URI 分享（各应用读取最稳）；分享后延迟清理，不污染相册。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MiooDao")
                // 写入期间对其他应用不可见，避免相册扫到半成品
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: run {
                toastSync(context, "分享失败")
                return
            }
            try {
                resolver.openOutputStream(uri)?.use { output ->
                    output.write(bytes)
                    output.flush()
                } ?: throw IllegalStateException("no stream")
                resolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                    null,
                    null
                )
            } catch (_: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                toastSync(context, "分享失败")
                return
            }
            startShare(context, uri, mime)
            // 给接收方 3 分钟读取窗口，之后把这条临时分享记录从相册移除
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                runCatching { context.contentResolver.delete(uri, null, null) }
            }, 3 * 60_000L)
            return
        }

        // API 26-28：无 WRITE_EXTERNAL_STORAGE，走 FileProvider（external-files-path 已声明）
        val dir = File(context.getExternalFilesDir(null), "share")
        if (!dir.exists() && !dir.mkdirs()) {
            toastSync(context, "分享失败")
            return
        }
        dir.listFiles()?.forEach { file ->
            if (file.lastModified() < System.currentTimeMillis() - 30 * 60_000L) file.delete()
        }
        val file = File(dir, filename)
        try {
            file.writeBytes(bytes)
        } catch (_: Exception) {
            toastSync(context, "分享失败")
            return
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        startShare(context, uri, mime)
    }

    private fun startShare(context: Context, uri: Uri, mime: String) {
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = mime
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        // chooser 上再挂 ClipData + 授权，确保所有目标应用都拿到读取权限
        val chooser = android.content.Intent.createChooser(send, "分享图片").apply {
            addFlags(
                android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            clipData = android.content.ClipData.newRawUri("share_image", uri)
        }
        context.startActivity(chooser)
    }

    private fun readCapped(stream: java.io.InputStream, maxBytes: Int): ByteArray? {
        stream.use { input ->
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            var total = 0
            while (true) {
                val n = input.read(chunk)
                if (n < 0) break
                total += n
                if (total > maxBytes) return null
                buffer.write(chunk, 0, n)
            }
            return buffer.toByteArray()
        }
    }

    private fun sniffImage(bytes: ByteArray): Pair<String, String>? {
        if (bytes.size >= 6 &&
            bytes[0] == 'G'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() &&
            bytes[3] == '8'.code.toByte()
        ) {
            return "gif" to "image/gif"
        }
        if (bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() &&
            bytes[1] == 'P'.code.toByte() &&
            bytes[2] == 'N'.code.toByte() &&
            bytes[3] == 'G'.code.toByte()
        ) {
            return "png" to "image/png"
        }
        if (bytes.size >= 12 &&
            bytes[0] == 'R'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() &&
            bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() &&
            bytes[9] == 'E'.code.toByte() &&
            bytes[10] == 'B'.code.toByte() &&
            bytes[11] == 'P'.code.toByte()
        ) {
            return "webp" to "image/webp"
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) {
            return "jpg" to "image/jpeg"
        }
        return null
    }

    private fun decodeBounded(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > 4096 || bounds.outHeight / sample > 4096) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    private fun saveBytes(context: Context, bytes: ByteArray, filename: String, mime: String): Uri? {
        return writeGallery(context, filename, mime) { output ->
            output.write(bytes)
        }
    }

    private fun saveBitmap(context: Context, bitmap: Bitmap, filename: String): Uri? {
        return writeGallery(context, filename, "image/jpeg") { output ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)) {
                throw IllegalStateException("compress failed")
            }
        }
    }

    private fun writeGallery(
        context: Context,
        filename: String,
        mime: String,
        write: (OutputStream) -> Unit
    ): Uri? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MiooDao")
                // 写入期间对其他应用不可见，避免相册扫到半成品
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            return try {
                resolver.openOutputStream(uri)?.use { output ->
                    write(output)
                    output.flush()
                } ?: throw IllegalStateException("no stream")
                val published = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                resolver.update(uri, published, null, null)
                uri
            } catch (_: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                null
            }
        }

        // API 26-28：无 WRITE_EXTERNAL_STORAGE 权限，公共 Pictures 直接写会 Permission denied。
        // 写应用外部专属目录（免权限）+ MediaScanner 扫描进相册。
        val fileDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            ?.let { File(it, "MiooDao") } ?: return null
        if (!fileDir.exists() && !fileDir.mkdirs()) return null
        val imageFile = File(fileDir, filename)
        return try {
            FileOutputStream(imageFile).use { output ->
                write(output)
                output.flush()
            }
            val uri = Uri.fromFile(imageFile)
            val scan = android.content.Intent(android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            scan.data = uri
            context.sendBroadcast(scan)
            uri
        } catch (_: Exception) {
            runCatching { imageFile.delete() }
            null
        }
    }

    private suspend fun toast(context: Context, message: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    /** 非协程上下文（如 shareBytes）用的主线程 Toast。 */
    private fun toastSync(context: Context, message: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
