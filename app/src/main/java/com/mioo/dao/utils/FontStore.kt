package com.mioo.dao.utils

import android.content.Context
import android.net.Uri
import android.os.Build
import java.io.File

/** 用户导入的字体文件仓库：filesDir/fonts 下的一组 ttf/otf/ttc 文件。 */
object FontStore {

    private const val DIR_NAME = "fonts"

    fun dir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { mkdirs() }

    fun list(context: Context): List<File> =
        dir(context).listFiles()
            ?.filter { it.isFile }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()

    /** 复制所选 Uri 到字体目录，重名自动加序号。成功返回文件名。 */
    fun import(context: Context, uri: Uri): String? = try {
        val resolver = context.contentResolver
        var queried: String? = null
        resolver.query(
            uri,
            arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
            null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) queried = cursor.getString(0)
        }
        // queried 在 lambda 内被赋值，Kotlin 禁止对其 smart cast，转入独立局部变量后再判空
        var name = queried
        if (name.isNullOrBlank()) {
            name = uri.lastPathSegment?.substringAfterLast('/') ?: "font.ttf"
        }
        if (!name.endsWith(".ttf", true) && !name.endsWith(".otf", true) && !name.endsWith(".ttc", true)) {
            name += ".ttf"
        }
        var target = File(dir(context), name)
        var index = 1
        val base = target.nameWithoutExtension
        val ext = target.extension
        while (target.exists()) {
            target = File(dir(context), "$base-${index++}.$ext")
        }
        val copied = resolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { input.copyTo(it) }
        } != null
        if (copied) target.name else null
    } catch (_: Exception) {
        null
    }

    fun delete(context: Context, name: String) {
        val file = File(dir(context), name)
        if (file.exists()) file.delete()
    }

    fun file(context: Context, name: String): File? =
        name.takeIf { it.isNotBlank() }
            ?.let { File(dir(context), it) }
            ?.takeIf { it.exists() }

    /**
     * 组装全局 Typeface：API 29+ 且两种都配了 → 英文字体为主、中文为回退
     * （拉丁字符命中英文字体，汉字/emoji 落到中文字体或系统）；否则用单字体。
     */
    fun buildTypeface(context: Context, chinese: String, english: String): android.graphics.Typeface? {
        val cnFile = file(context, chinese)
        val enFile = file(context, english)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && cnFile != null && enFile != null) {
                val enFamily = android.graphics.fonts.FontFamily.Builder(
                    android.graphics.fonts.Font.Builder(enFile).build()
                ).build()
                val cnFamily = android.graphics.fonts.FontFamily.Builder(
                    android.graphics.fonts.Font.Builder(cnFile).build()
                ).build()
                android.graphics.Typeface.CustomFallbackBuilder(enFamily)
                    .addCustomFallback(cnFamily)
                    .build()
            } else if (cnFile != null) {
                android.graphics.Typeface.createFromFile(cnFile)
            } else if (enFile != null) {
                android.graphics.Typeface.createFromFile(enFile)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
