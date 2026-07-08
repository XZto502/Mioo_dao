package com.mioo.dao.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
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
        scope: CoroutineScope
    ) {
        Toast.makeText(context, "正在开始保存图片...", Toast.LENGTH_SHORT).show()
        
        scope.launch(Dispatchers.IO) {
            try {
                // Fetch image from Coil (utilizes cache if available)
                val loader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .build()
                
                val result = loader.execute(request)
                if (result !is SuccessResult) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "图片下载失败", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                
                val drawable = result.drawable
                val bitmap = (drawable as? BitmapDrawable)?.bitmap
                if (bitmap == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "图片转换失败", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                
                // Save to Gallery
                val filename = "mioo_image_${System.currentTimeMillis()}.jpg"
                val savedUri = saveBitmapToGallery(context, bitmap, filename)
                
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
