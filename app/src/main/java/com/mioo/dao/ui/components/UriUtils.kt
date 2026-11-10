package com.mioo.dao.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun compressImageUnder2MB(context: Context, sourceUri: Uri): File = withContext(Dispatchers.IO) {
    val maxBytes = 2 * 1024 * 1024L
    val targetBytes = 1950 * 1024L
    val source = File.createTempFile("temp_upload_", ".${sourceUri.imageExtension(context)}", context.cacheDir)
    var keepSource = false
    try {
        val input = context.contentResolver.openInputStream(sourceUri) ?: throw IOException("无法打开图片")
        input.use { stream -> source.outputStream().use { stream.copyTo(it) } }
        if (source.length() == 0L) throw IOException("图片文件为空")
        if (source.length() <= maxBytes) {
            keepSource = true
            return@withContext source
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.path, bounds)
        val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
        if (longEdge <= 0) throw IOException("无法读取图片尺寸")
        var sample = 1
        while (longEdge / sample >= 5120) sample *= 2
        var bitmap = BitmapFactory.decodeFile(source.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw IOException("无法解码图片")
        try {
            val rotation = runCatching {
                when (ExifInterface(source.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            }.getOrDefault(0f)
            if (rotation != 0f) {
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height,
                    Matrix().apply { postRotate(rotation) }, true)
                if (rotated !== bitmap) bitmap.recycle()
                bitmap = rotated
            }
            fun scale(factor: Float) {
                val scaled = Bitmap.createScaledBitmap(bitmap, (bitmap.width * factor).toInt().coerceAtLeast(1),
                    (bitmap.height * factor).toInt().coerceAtLeast(1), true)
                if (scaled !== bitmap) bitmap.recycle()
                bitmap = scaled
            }
            val sampledEdge = maxOf(bitmap.width, bitmap.height)
            if (sampledEdge > 2560) scale(2560f / sampledEdge)

            val bytes = ByteArrayOutputStream()
            fun encode(quality: Int): Boolean {
                bytes.reset()
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, bytes)) throw IOException("图片编码失败")
                return bytes.size() <= targetBytes
            }
            if (!listOf(92, 85, 78, 70, 62).any(::encode)) {
                do {
                    scale(0.82f)
                } while (!encode(76))
            }
            val output = File.createTempFile("temp_upload_", ".jpg", context.cacheDir)
            try {
                output.outputStream().use { bytes.writeTo(it) }
                output
            } catch (e: IOException) {
                output.delete()
                throw e
            }
        } finally {
            bitmap.recycle()
        }
    } finally {
        if (!keepSource) source.delete()
    }
}

fun Uri.toFile(context: Context): File? {
    return try {
        val inputStream = context.contentResolver.openInputStream(this) ?: return null
        val ext = imageExtension(context)
        val tempFile = File(context.cacheDir, "temp_upload_${System.currentTimeMillis()}.$ext")
        tempFile.outputStream().use { outputStream ->
            inputStream.use { it.copyTo(outputStream) }
        }
        tempFile
    } catch (_: Exception) {
        null
    }
}

private fun Uri.imageExtension(context: Context): String {
    val mime = context.contentResolver.getType(this)?.lowercase()
    val fromMime = when (mime) {
        "image/png" -> "png"
        "image/gif" -> "gif"
        "image/webp" -> "webp"
        "image/jpeg", "image/jpg" -> "jpg"
        else -> null
    }
    if (fromMime != null) return fromMime
    val fromName = lastPathSegment
        ?.substringAfterLast('.', "")
        ?.lowercase()
        ?.substringBefore('?')
    return when (fromName) {
        "png", "gif", "webp", "jpg" -> fromName
        "jpeg" -> "jpg"
        else -> "jpg"
    }
}

/** 颜文字快捷面板每行列数 */
const val KAOMOJI_PER_ROW = 4

/**
 * 岛内常用颜文字（经典列表 + 扩展）。
 * 多行颜文字用 \n 保留换行，点选后整段插入。
 */
val KAOMOJI_LIST = listOf(
    // --- 经典岛内 ---
    "|∀ﾟ)",
    "(´ﾟДﾟ`)",
    "(;´Д`)",
    "(｀･ω･)",
    "(=ﾟωﾟ)=",
    "| ω・´)",
    "|-` )",
    "|д` )",
    "|ー` )",
    "|∀` )",
    "(つд⊂)",
    "(ﾟДﾟ≡ﾟДﾟ)",
    "(＾o＾)ﾉ",
    "(|||ﾟДﾟ)",
    "( ﾟ∀ﾟ)",
    "( ´∀`)",
    "(*´∀`)",
    "(*ﾟ∇ﾟ)",
    "(*ﾟーﾟ)",
    "(　ﾟ 3ﾟ)",
    "( ´ー`)",
    "( ・_ゝ・)",
    "( ´_ゝ`)",
    "(*´д`)",
    "(・ー・)",
    "(・∀・)",
    "(ゝ∀･)",
    "(〃∀〃)",
    "(*ﾟ∀ﾟ*)",
    "( ﾟ∀。)",
    "( `д´)",
    "(`ε´ )",
    "(`ヮ´ )",
    "σ`∀´)",
    "ﾟ∀ﾟ)σ",
    "ﾟ ∀ﾟ)ノ",
    "(╬ﾟдﾟ)",
    "(|||ﾟдﾟ)",
    "( ﾟдﾟ)",
    "Σ( ﾟдﾟ)",
    "( ;ﾟдﾟ)",
    "( ;´д`)",
    "(　д ) ﾟ ﾟ",
    "( ☉д⊙)",
    "(((　ﾟдﾟ)))",
    "( ` ・´)",
    "( ´д`)",
    "( -д-)",
    "(>д<)",
    "･ﾟ( ﾉд`ﾟ)",
    "( TдT)",
    "(￣∇￣)",
    "(￣3￣)",
    "(￣ｰ￣)",
    "(￣ . ￣)",
    "(￣皿￣)",
    "(￣艸￣)",
    "(￣︿￣)",
    "(￣︶￣)",
    "ヾ(´ωﾟ｀)",
    "(*´ω`*)",
    "(・ω・)",
    "( ´・ω)",
    "(｀・ω)",
    "(´・ω・`)",
    "(`・ω・´)",
    "( `_っ´)",
    "( `ー´)",
    "( ´_っ`)",
    "( ´ρ`)",
    "( ﾟωﾟ)",
    "(oﾟωﾟo)",
    "(　^ω^)",
    "(｡◕∀◕｡)",
    "/( ◕‿‿◕ )\\",
    "ヾ(´ε`ヾ)",
    "(ノﾟ∀ﾟ)ノ",
    "(σﾟдﾟ)σ",
    "(σﾟ∀ﾟ)σ",
    "|дﾟ )",
    "┃電柱┃",
    "ﾟ(つд`ﾟ)",
    "ﾟÅﾟ )　",
    "⊂彡☆))д`)",
    "⊂彡☆))д´)",
    "⊂彡☆))∀`)",
    "(´∀((☆ミつ",
    // --- 扩展 ---
    "(　ˇωˇ)",
    "(ﾉ)`ω´(ヾ)",
    "ᕕ( ᐛ )ᕗ",
    "( ˇωˇ)",
    "( ｣ﾟДﾟ)｣",
    "＜( ›´ω`‹ )",
    "(;´ヮ`)7",
    "(`ゥ´ )",
    "(`ᝫ´ )",
    "( ᑭ`д´)ᓀ",
    "))д´)ᑫ",
    "σ( ᑒ )",
    "･ﾟ( ﾉヮ´ )",
    "( ´_ゝ`)旦",
    "(<ゝω・) ☆",
    "(`ε´ ",
    "(つ*⊂)",
    "=͟͟͞͞( 'ヮ' 三 'ヮ' =͟͟͞͞)",
    "↙(`ヮ´ )↗ 开摆！",
    "(っ˘Д˘)ノ",
    "<(ﾉ#)`д´)σ",
    "₍₍(ง`ᝫ´ )ว⁾",
    "( `ᵂ´)",
    "( *・ω・)✄╰ひ╯",
    "U•ェ•*U",
    "⊂( ﾟωﾟ)つ",
    "( ﾟ∀。)7",
    "･ﾟ( ﾟ∀。) ﾟ。",
    "( `д´)σ",
    "( ﾟᯅ 。)",
    "( ;`д´; )",
    "m9( `д´)",
    "( ﾟπ。)",
    "ᕕ( ﾟ∀。)ᕗ",
    "ฅ(^ω^ฅ)",
    "(|||^ヮ^)",
    "(|||ˇヮˇ)",
    "(　↺ω↺)",
    " `ー´) `д´) `д´)",
    "接☆龙☆大☆成☆功",
    "ᑭ`д´)ᓀ",
    "∑ᑭ(`ヮ´ )ᑫ",
    "乚 (^ω^ ﾐэ)Э好钩我咬",
    "乚(`ヮ´  ﾐэ)Э",
    "( ﾟ∀。ﾐэ)Э三三三三　",
    "乚(ˇωˇ ﾐэ)Э三三三三　",
    "乚( へ ﾟ∀ﾟ)べ摔低低",
    "(ベ ˇωˇ)べ 摔低低",
    "(`ヮ´ )σ`∀´) ﾟ∀ﾟ)σ",
    "( ﾟ∀。)∀。)∀。)",
    // --- 从鄂尔多斯开始，按简称顺序保留完整图形 ---
    """
Σ( ﾟдﾟ)´ﾟДﾟ)　ﾟдﾟ)))
""".trimIndent(),
    """
( ﾟ∀。)∀。)∀。)
""".trimIndent(),
    """
(　ˇωˇ )◕∀◕｡)^ω^)
""".trimIndent(),
    """
　σ　σ
σ(　´ρ`)σ[F5]
　σ　σ
""".trimIndent(),
    """
\ ︵
ᐕ)⁾⁾
""".trimIndent(),
    """
╭◜◝ ͡ ◜◝ J J
(　　　　 `д´) 　“咩！”
╰◟д ◞ ͜ ◟д◞
""".trimIndent(),
    """
     /)　/)
c(　╹^╹)
""".trimIndent(),
    """
　　       　∧,,　
　　　　ヾ ｀. ､`フ
　　　(,｀'´ヽ､､ﾂﾞ
　 (ヽｖ'　　　`''ﾞつ
　　,ゝ　 ⌒`ｙ'''´
　 （ (´＾ヽこつ
　　 ) )
　　(ノ
""".trimIndent(),
    """
（\_/）
(・_・)
 / 　>
""".trimIndent(),
    """
　　　　_∧＿∧_ 　　　　
            ((∀｀/ 　) 　　
　       /⌒　　 ／ 　　
         /(__ノ＼_ノ 　　
          (_ノ ||| 举高高~~
　∧＿∧　∧＿∧
 (( ・∀・ ))・∀・) )
 `＼　　 ∧ 　　ノ
　/　｜/　　｜
（＿ノ＿)_ノL＿)
""".trimIndent(),
    """
举糕糕~
　　☆☆☆☆☆☆☆☆
 　╭┻┻┻┻┻┻┻┻╮
 　┃╱╲╱╲╱╲╱╲┃
 ╭┻━━━━━━━━┻╮
 ┃╱╲╱╲╱╲╱╲╱╲┃
 ┗━━━━━━━━━━┛
 　　　∧＿∧　∧＿∧
　　(( ・∀・ ))・∀・) )
 　　`＼　　 ∧ 　　ノ
　　　/　　｜/　　｜
 　　（＿ノ＿)_ノL＿)
""".trimIndent(),
    """
. 　　　　   　.★ * ★.. 　
 　.*★　*.　*..*　　　★ 　
 　★　　　　　　 ★ 　　
     ‘*. *'　 ʜᴀᴘᴘʏ 肥肥 ᴅᴀʏ 　　　
         ‘★.　　 ★’ 　　　　 　　
               ‘*..★
┊┊┊┊☆☆☆☆☆☆☆☆┊┊┊┊
┊┊┊╭┻┻┻┻┻┻┻┻╮┊┊┊
┊┊┊┃╱╲╱╲╱╲╱╲┃┊┊┊
┊┊╭┻━━━━━━━━┻╮┊┊
┊┊┃╱╲╱╲╱╲╱╲╱╲┃┊┊
╱▔┗━━━━━━━━━━┛▔╲
""".trimIndent(),
    """
              _∧＿∧_
　　　 ((∀｀/ 　)
　　　 /⌒　　 ／
　 /(__ノ＼_ノ(((ヽ
　(_ノ　 　￣Ｙ＼
|　(＼　(\　 /)　 ｜ )举高高！
ヽ　ヽ` ( ﾟ∀ﾟ ) _ノ /
　＼ |　⌒Ｙ⌒　/ /
　 ｜ヽ · ｜ · ﾉ ／/
　 ＼トー仝ーイ
　　 ｜ ミ土彡/
　　　). \ °　 /
　　　( 　\. y 　\
""".trimIndent(),
    """
吁~~~~　　rnm，退钱！
 　　　/　　　/
(　ﾟ 3ﾟ) `ー´) `д´) `д´)
""".trimIndent(),
    """
　　　　　　＿＿＿
　　　　　／＞　　フ
　　　　　|  　_　 _ l 我是一只催更的
　 　　　／` ミ＿xノ 喵喵酱
　　 　 /　　　 　 | gkdgkd
　　　 /　 ヽ　　 ﾉ
　 　 │　　|　|　|
　／￣|　　 |　|　|
　| (￣ヽ＿_ヽ_)__)
　＼二つ
""".trimIndent(),
    """
　∧＿∧
（｡･ω･｡)つ━☆・*。
 ⊂　　 ノ 　　　・゜+.
　しーＪ　　　°。+ *´¨)
　　　 　　.· ´¸.·*´¨) ¸.·*¨)
　　　　　　　 　(¸.·´ (¸.·’*
""".trimIndent(),
    "\u3000", // 全角空格，点选后插入原字符
)

/** 预计算预览文案，避免面板每次重组时扫多行字符串 */
data class KaomojiItem(val raw: String, val preview: String)

// 从指定条目起，按选择面板截图的阅读顺序命名；简称不影响插入原文。
private val KAOMOJI_SHORT_NAMES = listOf(
    "齐齐哈尔", "呼伦悲尔", "鄂尔多斯", "智利",
    "阴山山脉", "F5欧拉", "UK酱", "白羊",
    "兔兔", "neko", "给你", "举高高",
    "举糕糕", "Happy肥肥Day", "举高高2", "大嘘",
    "催更喵gkd", "巴拉巴拉", "全角空格"
)

private val KAOMOJI_SHORT_NAME_START = KAOMOJI_LIST.indexOf("(`ヮ´ )σ`∀´) ﾟ∀ﾟ)σ")

val KAOMOJI_ITEMS: List<KaomojiItem> = KAOMOJI_LIST.mapIndexed { index, raw ->
    val shortName = if (KAOMOJI_SHORT_NAME_START >= 0) {
        KAOMOJI_SHORT_NAMES.getOrNull(index - KAOMOJI_SHORT_NAME_START)
    } else {
        null
    }
    val preview = shortName ?: if (raw.contains('\n')) {
        raw.lineSequence()
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?.take(10)
            ?.let { "$it…" }
            ?: "多行"
    } else {
        raw
    }
    KaomojiItem(raw = raw, preview = preview)
}
