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

    /** 汉字码位区段（CJK 统一表意）采样检测用。 */
    private const val HAN_START = 0x4E00
    private const val HAN_END = 0x9FFF

    /**
     * 字体是否包含汉字字形：解析 sfnt 的 cmap 表看字形映射是否覆盖汉字区段。
     * 回退链按顺序取第一个含该字形的字体，英文槽若选了含汉字的字体会连汉字一起接管。
     */
    fun containsHan(file: File): Boolean = try {
        java.io.RandomAccessFile(file, "r").use { raf ->
            var base = 0L
            val magic = ByteArray(4).also { raf.readFully(it) }
            when (String(magic, Charsets.US_ASCII)) {
                "ttcf" -> {
                    // 集合字体：offsets 数组从第 12 字节起，只检查第一个成员
                    raf.seek(12)
                    base = readU32(raf)
                    raf.seek(base)
                    raf.readFully(magic)
                }
                "OTTO", "true", "typ1" -> Unit
                else -> if (magic[0] != 0.toByte() || magic[1] != 1.toByte()) return false
            }
            raf.seek(base + 4)
            val numTables = readU16(raf)
            raf.skipBytes(6) // searchRange + entrySelector + rangeShift
            var cmapOffset = -1L
            var i = 0
            while (i < numTables && cmapOffset < 0) {
                val tableTag = ByteArray(4).also { raf.readFully(it) }
                raf.skipBytes(4) // checksum
                val offset = readU32(raf)
                raf.skipBytes(4) // length
                // 表偏移一律相对文件头（TTC 成员也是），不叠加 base
                if (String(tableTag, Charsets.US_ASCII) == "cmap") cmapOffset = offset
                i++
            }
            if (cmapOffset < 0) return false
            raf.seek(cmapOffset + 2)
            val numSubtables = readU16(raf)
            var bestOffset = -1L
            var bestScore = 0
            var j = 0
            while (j < numSubtables) {
                val platformId = readU16(raf)
                val encodingId = readU16(raf)
                val subOffset = readU32(raf)
                val score = when {
                    platformId == 3 && encodingId == 10 -> 4 // Windows UCS-4
                    platformId == 0 && encodingId >= 4 -> 3   // Unicode UCS-4
                    platformId == 3 && encodingId == 1 -> 2   // Windows BMP
                    platformId == 0 -> 1                      // Unicode BMP
                    else -> 0
                }
                if (score > bestScore) {
                    bestScore = score
                    bestOffset = cmapOffset + subOffset
                }
                j++
            }
            if (bestOffset < 0) return false
            raf.seek(bestOffset)
            when (readU16(raf)) {
                4 -> hanInRange4(raf)
                12 -> hanInRange12(raf)
                else -> false
            }
        }
    } catch (_: Exception) {
        false
    }

    private fun readU16(raf: java.io.RandomAccessFile): Int =
        raf.read() shl 8 or raf.read()

    private fun readU32(raf: java.io.RandomAccessFile): Long =
        raf.read().toLong() shl 24 or (raf.read().toLong() shl 16) or
            (raf.read().toLong() shl 8) or raf.read().toLong()

    /** cmap 子表格式 4（BMP 分段）：任一分段与汉字区段相交即含汉字。 */
    private fun hanInRange4(raf: java.io.RandomAccessFile): Boolean {
        raf.skipBytes(6) // length + language
        val segCount = readU16(raf) / 2
        raf.skipBytes(6) // searchRange + entrySelector + rangeShift
        val endCodes = IntArray(segCount) { readU16(raf) }
        raf.skipBytes(2) // reservedPad
        val startCodes = IntArray(segCount) { readU16(raf) }
        return (0 until segCount).any { k ->
            startCodes[k] != 0xFFFF && startCodes[k] <= HAN_END && endCodes[k] >= HAN_START
        }
    }

    /** cmap 子表格式 12（UCS-4 分组）：任一分组与汉字区段相交即含汉字。 */
    private fun hanInRange12(raf: java.io.RandomAccessFile): Boolean {
        raf.skipBytes(10) // reserved + length + language
        val nGroups = readU32(raf).toInt()
        var hit = false
        var g = 0
        while (g < nGroups && !hit) {
            val start = readU32(raf)
            val end = readU32(raf)
            raf.skipBytes(4) // startGlyphID
            if (start <= HAN_END && end >= HAN_START) hit = true
            g++
        }
        return hit
    }
}
