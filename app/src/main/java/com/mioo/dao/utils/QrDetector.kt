package com.mioo.dao.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 看图器的静默二维码检测：图片加载后后台拉取并只尝试 QR 格式，
 * 无码/失败一律返回 null（不打扰）。命中结果交给 [openResult] 处理。
 */
object QrDetector {

    private const val MAX_BYTES = 20 * 1024 * 1024

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /** 下载（降采样 ≤[maxDim]）并解码二维码文本；无码或任何失败返回 null。 */
    suspend fun detect(context: Context, imageUrl: String, maxDim: Int = 1280): String? =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(imageUrl).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val body = response.body ?: return@withContext null
                    val bytes = readCapped(body.byteStream(), MAX_BYTES) ?: return@withContext null
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
                    var sample = 1
                    while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) {
                        sample *= 2
                    }
                    val opts = BitmapFactory.Options().apply {
                        inSampleSize = sample
                        inPreferredConfig = Bitmap.Config.RGB_565
                    }
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                        ?: return@withContext null
                    try {
                        val pixels = IntArray(bitmap.width * bitmap.height)
                        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                        val source = com.google.zxing.RGBLuminanceSource(
                            bitmap.width,
                            bitmap.height,
                            pixels
                        )
                        val hints = mapOf(DecodeHintType.TRY_HARDER to true)
                        QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)), hints).text
                    } finally {
                        bitmap.recycle()
                    }
                }
            } catch (_: Exception) {
                null
            }
        }

    /** 命中后处理：URL 直接打开，其他内容复制到剪贴板。 */
    fun openResult(context: Context, text: String) {
        val trimmed = text.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(trimmed))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        } else {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                as? android.content.ClipboardManager
            clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("qr", trimmed))
            Toast.makeText(context, "二维码内容已复制", Toast.LENGTH_SHORT).show()
        }
    }

    private fun readCapped(stream: java.io.InputStream, maxBytes: Int): ByteArray? {
        stream.use { input ->
            val buffer = java.io.ByteArrayOutputStream()
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
}
