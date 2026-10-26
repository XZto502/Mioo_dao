package com.mioo.dao.utils

import android.content.Context
import coil.map.Mapper
import coil.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 离线串图片缓存：filesDir/offline_images 下的平铺目录。
 * 文件名 = 岛图原名（含日期子目录，'/' 换 '_'），因此 /thumb/x 与 /image/x 命中同一份。
 *
 * 渲染侧经 [OfflineImageFetcher] 挂进 Coil：URL 先查这里，命中直接读文件，
 * 不发网络请求；未命中回落原网络管线（在线 SWR / Coil 磁盘缓存照旧）。
 */
object OfflineImageStore {

    /** 单串离线图片数量上限——只影响图片，楼层 JSON 不受限。 */
    const val MAX_IMAGES_PER_THREAD = 600

    fun dir(context: Context): File = File(context.filesDir, "offline_images")

    /** 岛图日期子目录换 '_' 平铺；日期前缀保证跨目录不撞名。 */
    fun sanitizeFilename(islandName: String): String = islandName.replace('/', '_')

    /** 是否为本项目会加载的岛图 URL（image.nmb.best 的 /image/ 或 /thumb/）。 */
    fun isIslandImageUrl(url: String): Boolean =
        url.contains("image.nmb.best") && (islandImageName(url) != null)

    /**
     * 从展示/缩略/原图 URL 提取岛图名（如 2022-06-28/62ba647ab18dc.jpg）。
     * 与 IslandImage.filename 同语义：名字里允许 '/'（日期子目录）。
     */
    fun islandImageName(url: String): String? {
        val marker = when {
            "/image/" in url -> "/image/"
            "/thumb/" in url -> "/thumb/"
            else -> return null
        }
        val name = url.substringAfter(marker).substringBefore('?').substringBefore('#')
        if (name.isBlank() || name.startsWith('/') || name.contains("..") || ' ' in name) {
            return null
        }
        return name
    }

    /** 渲染时查本地：URL 有对应离线文件则返回，否则 null 走网络。 */
    fun resolveLocalFile(root: File, url: String): File? {
        val name = islandImageName(url) ?: return null
        val file = File(root, sanitizeFilename(name))
        return if (file.isFile && file.length() > 0) file else null
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * 下载一张楼的配图（缩略图优先，缺缩略图回落原图）。
     * 文件已存在则直接返回（更新离线时只补新图）。失败返回 null，不抛异常。
     *
     * @param imageUrl 展示用 URL（HOST/image/<name> 形态）
     * @return 文件 to 字节数
     */
    suspend fun ensureDownloaded(root: File, imageUrl: String): Pair<File, Long>? =
        withContext(Dispatchers.IO) {
            val name = islandImageName(imageUrl) ?: return@withContext null
            val file = File(root, sanitizeFilename(name))
            if (file.isFile && file.length() > 0) return@withContext file to file.length()
            try {
                root.mkdirs()
                val body = fetchBody("$HOST/thumb/$name") ?: fetchBody("$HOST/image/$name")
                    ?: return@withContext null
                val tmp = File(root, "${file.name}.part")
                body.byteStream().use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                }
                if (tmp.length() <= 0L || !tmp.renameTo(file)) {
                    tmp.delete()
                    return@withContext null
                }
                file to file.length()
            } catch (_: Exception) {
                File(root, "${file.name}.part").delete()
                null
            }
        }

    private const val HOST = "https://image.nmb.best"

    private fun fetchBody(url: String): okhttp3.ResponseBody? = try {
        client.newCall(
            okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", "Mioo_dao offline")
                .build()
        ).execute().use { response ->
            if (response.isSuccessful) response.body else null
        }
    } catch (_: Exception) {
        null
    }

    /** 删除指定文件名集合（仅文件，不动目录本身）。 */
    fun deleteFiles(root: File, names: Collection<String>) {
        for (name in names) {
            if (name.isBlank()) continue
            // 名字来自自身 sanitize 输出，仍防御一次路径逃逸
            if (name.contains("..") || name.startsWith('/')) continue
            File(root, name).delete()
        }
    }

    /** 清空整个离线图片目录（清空全部离线串时用）。 */
    fun deleteAll(root: File) {
        val files = root.listFiles() ?: return
        for (file in files) file.delete()
    }
}

/**
 * Coil 管线里的离线优先拦截：岛图 URL 若已离线落盘，把请求映射成本地 File，
 * 交给原生 FileFetcher 读盘解码——断网/飞行模式下所有加载点
 * （列表缩略图、看图器、引用弹窗）都能出图。未命中返回 null 走原网络管线。
 */
class OfflineImageMapper(private val root: File) : Mapper<String, Any> {

    override fun map(data: String, options: Options): Any? {
        if (!OfflineImageStore.isIslandImageUrl(data)) return null
        return OfflineImageStore.resolveLocalFile(root, data)
    }
}
