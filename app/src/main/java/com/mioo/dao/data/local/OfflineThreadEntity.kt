package com.mioo.dao.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 离线串标记：downloadFullThread 落库一行为「已离线」。
 * 页面 JSON 仍在 api_cache（cacheKey = thread_<id>），这里只存摘要元数据。
 */
@Entity(tableName = "offline_threads")
data class OfflineThreadEntity(
    @PrimaryKey val threadId: String,
    /** 主楼标题（「无标题」按无标题存 null）。 */
    val title: String?,
    /** 主楼正文纯文本摘要（去 HTML/实体、压空白、截断）。 */
    val contentSummary: String,
    val poUserHash: String,
    val fid: String?,
    /** 下载时解析的板块名（离线后不再依赖板块列表）。 */
    val forumName: String,
    /** 下载时刻的回复总数。 */
    val replyCount: Int,
    /** 本串已下载的离线图片文件名（sanitize 后）JSON 数组，删除时判断共享。 */
    val imageFilesJson: String,
    /** 离线图片总字节数（下载时点值）。 */
    val imageBytes: Long,
    /** 首图（主楼优先）的本地文件名，列表封面用；无图存 null。 */
    val coverFile: String?,
    val downloadedAt: Long
)

/**
 * 离线串管理页列表项。cachedPages / jsonBytes 是查询期实时值——
 * 浏览写进的额外 api_cache 页也算进已缓存页数与体积。
 */
data class OfflineThreadSummary(
    val threadId: String,
    val title: String?,
    val contentSummary: String,
    val poUserHash: String,
    val fid: String?,
    val forumName: String,
    val replyCount: Int,
    val downloadedAt: Long,
    val imageBytes: Long,
    val coverFile: String?,
    val cachedPages: Int,
    val jsonBytes: Long
) {
    val totalBytes: Long get() = jsonBytes + imageBytes
}
