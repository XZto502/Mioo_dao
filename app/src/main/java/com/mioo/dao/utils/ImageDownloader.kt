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
        val appContext = context.applicationContext
        Toast.makeText(appContext, "正在开始保存图片...", Toast.LENGTH_SHORT).show()

        scope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(imageUrl).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        toast(appContext, "图片下载失败")
                        return@launch
                    }
                    val body = response.body
                    if (body == null) {
                        toast(appContext, "图片下载失败")
                        return@launch
                    }
                    val bytes = readCapped(body.byteStream(), MAX_BYTES)
                    if (bytes == null) {
                        toast(appContext, "图片太大，无法保存")
                        return@launch
                    }
                    val sniffed = sniffImage(bytes)
                    val saved = if (sniffed != null) {
                        val (ext, mime) = sniffed
                        saveBytes(appContext, bytes, "mioo_image_${System.currentTimeMillis()}.$ext", mime)
                    } else {
                        val bitmap = decodeBounded(bytes)
                        if (bitmap == null) {
                            toast(appContext, "图片转换失败")
                            return@launch
                        }
                        try {
                            saveBitmap(appContext, bitmap, "mioo_image_${System.currentTimeMillis()}.jpg")
                        } finally {
                            bitmap.recycle()
                        }
                    }
                    toast(appContext, if (saved != null) "图片已成功保存到相册" else "保存失败")
                }
            } catch (_: OutOfMemoryError) {
                toast(appContext, "图片太大，无法保存")
            } catch (_: Exception) {
                toast(appContext, "保存失败")
            }
        }
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
}
