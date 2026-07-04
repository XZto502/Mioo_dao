package com.mioo.dao.ui.components

import android.content.Context
import android.net.Uri
import java.io.File

fun Uri.toFile(context: Context): File? {
    return try {
        val inputStream = context.contentResolver.openInputStream(this) ?: return null
        val tempFile = File(context.cacheDir, "temp_upload_${System.currentTimeMillis()}.jpg")
        tempFile.outputStream().use { outputStream ->
            inputStream.use { it.copyTo(outputStream) }
        }
        tempFile
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

val KAOMOJI_LIST = listOf(
    "|∀ﾟ)", "(´ﾟДﾟ`)", "(;´Д`)", "(｀･ω･)", "(=ﾟωﾟ)=",
    "| ω・´)", "((*ﾟДﾟ)ゞ", "(つд⊂)", "(ﾟ∀ﾟ )", "(╬ﾟдﾟ)",
    "(*´∀`)", "(*ﾟ∇ﾟ)", "(*ﾟーﾟ)", "(　ﾟ 3ﾟ)", "( ´ー`)",
    "(・_ゝ・)", "( -д-)", "(*ﾟ∀ﾟ*)", "( ﾟ∀ﾟ)", "(っ*)",
    "(*_　_)", "(*￣∇￣)", "( ´∀`)", "⊂彡☆))д´)", "⊂彡☆))д`)"
)
