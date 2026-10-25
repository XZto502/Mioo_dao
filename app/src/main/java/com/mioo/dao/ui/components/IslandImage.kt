package com.mioo.dao.ui.components

/**
 * X岛接口只返回图片文件名。地址固定拼到 image.nmb.best。
 */
object IslandImage {
    const val HOST = "https://image.nmb.best"

    /** 原图。已经是 http(s) 的岛图会改到固定域名的 /image/。 */
    fun url(filename: String?): String? {
        if (filename.isNullOrBlank()) return null
        if (filename.startsWith("//")) return "https:${filename.trim()}"
        if (filename.startsWith("http://") || filename.startsWith("https://")) return fullSize(filename)
        return "$HOST/image/${filename.trimStart('/')}"
    }

    /** 点击看大图。非岛图原样返回。 */
    fun fullSize(url: String): String {
        val name = filename(url) ?: return url
        return "$HOST/image/$name"
    }

    /**
     * 列表先请求缩略图。失败时调用方再加载 [fullSize]。
     * 非岛图原样返回。
     */
    fun listUrl(url: String): String {
        val name = filename(url) ?: return url
        return "$HOST/thumb/$name"
    }

    private fun filename(url: String): String? {
        val marker = when {
            "/image/" in url -> "/image/"
            "/thumb/" in url -> "/thumb/"
            else -> return null
        }
        val name = url.substringAfter(marker).substringBefore('?').substringBefore('#')
        // 岛图带日期子目录（如 2022-06-28/62ba647ab18dc.jpg），名字里允许 '/'；
        // 只拒绝会导致转换丢路径或越界的形态。
        if (name.isBlank() || name.startsWith('/') || ' ' in name || ".." in name) return null
        return name
    }
}
