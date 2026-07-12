package com.mioo.dao.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.mioo.dao.ui.components.PostData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object ShareCardUtil {

    suspend fun sharePostCard(context: Context, postData: PostData) {
        val bitmap = createCardBitmap(context, postData) ?: return
        val file = saveBitmapToSharedFile(context, bitmap, postData.id) ?: return
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享回复卡片"))
    }

    private suspend fun createCardBitmap(context: Context, postData: PostData): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val width = 800
            val padding = 48
            val contentWidth = width - padding * 2

            // Colors
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF9F9FB.toInt() }
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFE4E4E7.toInt()
                style = Paint.Style.STROKE
                strokeWidth = 3f
            }
            
            // Text paints
            val idPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF18181B.toInt()
                textSize = 28f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            
            val infoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF71717A.toInt()
                textSize = 22f
                typeface = Typeface.DEFAULT
            }
            
            val contentPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF27272A.toInt()
                textSize = 28f
            }

            // Parse HTML content
            val decodedContent = android.text.Html.fromHtml(postData.content ?: "", android.text.Html.FROM_HTML_MODE_COMPACT)
            
            // Build text layout
            val textLayout = StaticLayout.Builder.obtain(decodedContent, 0, decodedContent.length, contentPaint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.25f)
                .setIncludePad(false)
                .build()

            // Handle image loading if present
            var loadedImage: Bitmap? = null
            if (!postData.imageUrl.isNullOrBlank()) {
                val loader = context.imageLoader
                val request = ImageRequest.Builder(context)
                    .data(postData.imageUrl)
                    .allowHardware(false) // needed to draw on canvas
                    .build()
                val result = loader.execute(request)
                if (result is SuccessResult) {
                    val drawable = result.drawable
                    val bmp = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                    
                    // Scale to fit contentWidth
                    val scale = contentWidth.toFloat() / bmp.width.toFloat()
                    val targetHeight = (bmp.height * scale).toInt()
                    loadedImage = Bitmap.createScaledBitmap(bmp, contentWidth, targetHeight, true)
                }
            }

            // Calculate height
            val headerHeight = 100
            val textHeight = textLayout.height
            val imageHeight = if (loadedImage != null) loadedImage.height + 32 else 0
            val totalHeight = headerHeight + textHeight + imageHeight + padding * 2

            // Create Bitmap and Canvas
            val bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // Draw Background Card
            canvas.drawRect(0f, 0f, width.toFloat(), totalHeight.toFloat(), bgPaint)
            canvas.drawRect(1.5f, 1.5f, width.toFloat() - 1.5f, totalHeight.toFloat() - 1.5f, borderPaint)

            // Draw Header: "No.ID" and "时间饼干"
            var currentY = padding.toFloat()
            val idText = "No.${postData.id}"
            canvas.drawText(idText, padding.toFloat(), currentY + 28f, idPaint)
            
            val infoText = "${postData.userName}  ${postData.createdAt}"
            canvas.drawText(infoText, padding.toFloat(), currentY + 68f, infoPaint)
            
            currentY += headerHeight

            // Draw content text
            canvas.save()
            canvas.translate(padding.toFloat(), currentY)
            textLayout.draw(canvas)
            canvas.restore()
            
            currentY += textHeight

            // Draw attached image if present
            if (loadedImage != null) {
                currentY += 32f
                canvas.drawBitmap(loadedImage, padding.toFloat(), currentY, null)
            }

            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun saveBitmapToSharedFile(context: Context, bitmap: Bitmap, id: String): File? {
        return try {
            val dir = File(context.getExternalFilesDir(null), "shares")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "share_card_$id.png")
            val out = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
            out.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
