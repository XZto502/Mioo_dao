package com.mioo.dao.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object ImageDownloader {

    fun downloadImage(
        context: Context,
        imageUrl: String,
        enableWatermark: Boolean,
        scope: CoroutineScope
    ) {
        Toast.makeText(context, "正在开始保存图片...", Toast.LENGTH_SHORT).show()
        
        scope.launch(Dispatchers.IO) {
            try {
                // 1. Fetch image from Coil (utilizes cache if available)
                val loader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .allowHardware(false) // Must be false to extract Bitmap
                    .build()
                
                val result = loader.execute(request)
                if (result !is SuccessResult) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "图片下载失败", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                
                val drawable = result.drawable
                val loadedBitmap = (drawable as? BitmapDrawable)?.bitmap
                if (loadedBitmap == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "图片转换失败", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                
                // Copy bitmap so it's mutable if we need to apply watermark
                var finalBitmap = loadedBitmap.copy(Bitmap.Config.ARGB_8888, true)
                
                // 2. Add Watermark if enabled
                if (enableWatermark) {
                    finalBitmap = addWatermarkToBitmap(finalBitmap)
                }
                
                // 3. Save to Gallery
                val filename = "mioo_image_${System.currentTimeMillis()}.jpg"
                val savedUri = saveBitmapToGallery(context, finalBitmap, filename)
                
                withContext(Dispatchers.Main) {
                    if (savedUri != null) {
                        Toast.makeText(context, "图片已成功保存到相册", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "发生错误: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun addWatermarkToBitmap(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val result = Bitmap.createBitmap(width, height, src.config)
        val canvas = Canvas(result)
        canvas.drawBitmap(src, 0f, 0f, null)
        
        val paint = Paint().apply {
            color = Color.WHITE
            alpha = 180 // Semi-transparent
            textSize = (width * 0.035f).coerceIn(16f, 48f) // Scale text size dynamically based on image width
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            // Add a subtle shadow/outline for readability on white backgrounds
            setShadowLayer(3f, 1f, 1f, Color.BLACK)
        }
        
        val watermarkText = "喵岛 App"
        
        // Position watermark at the bottom-right corner with margin
        val xMargin = width * 0.04f
        val yMargin = height * 0.04f
        val textWidth = paint.measureText(watermarkText)
        
        val x = width - textWidth - xMargin
        val y = height - yMargin
        
        canvas.drawText(watermarkText, x, y, paint)
        return result
    }

    private fun saveBitmapToGallery(context: Context, bitmap: Bitmap, filename: String): Uri? {
        var outputStream: OutputStream? = null
        var uri: Uri? = null
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MiooDao")
                }
                
                uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    outputStream = resolver.openOutputStream(uri)
                }
            } else {
                val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString()
                val fileDir = File(imagesDir, "MiooDao")
                if (!fileDir.exists()) {
                    fileDir.mkdirs()
                }
                val imageFile = File(fileDir, filename)
                uri = Uri.fromFile(imageFile)
                outputStream = FileOutputStream(imageFile)
            }
            
            if (outputStream != null) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                outputStream.flush()
                
                // Add to scanner if on older Android
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && uri != null) {
                    val mediaScanIntent = android.content.Intent(android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                    mediaScanIntent.data = uri
                    context.sendBroadcast(mediaScanIntent)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            uri = null
        } finally {
            try {
                outputStream?.close()
            } catch (e: Exception) {
                // ignore
            }
        }
        
        return uri
    }
}
