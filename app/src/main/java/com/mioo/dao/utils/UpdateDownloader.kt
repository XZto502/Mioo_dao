package com.mioo.dao.utils

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import com.mioo.dao.data.model.GithubRelease
import java.io.File

object UpdateDownloader {

    private var downloadId: Long = -1L
    private var receiver: BroadcastReceiver? = null
    private var pendingUrls: List<String> = emptyList()
    private var urlIndex: Int = 0
    private var releaseTag: String = ""

    fun downloadAndInstall(context: Context, release: GithubRelease) {
        val appContext = context.applicationContext
        val urls = candidateDownloadUrls(release)
        if (urls.isEmpty()) {
            openInBrowser(appContext, release.htmlUrl)
            return
        }
        pendingUrls = urls
        urlIndex = 0
        releaseTag = release.tagName
        Toast.makeText(appContext, "正在后台下载更新包，请稍候...", Toast.LENGTH_LONG).show()
        enqueueCurrent(appContext)
    }

    private fun candidateDownloadUrls(release: GithubRelease): List<String> {
        val apkUrl = release.assets
            ?.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
            ?.browserDownloadUrl
            ?.takeIf { it.isNotBlank() }
            ?: return emptyList()
        if (!apkUrl.contains("github.com") || !apkUrl.contains("/releases/download/")) {
            return listOf(apkUrl)
        }
        return listOf(
            "https://ghfast.top/$apkUrl",
            "https://ghproxy.net/$apkUrl",
            apkUrl
        )
    }

    private fun enqueueCurrent(context: Context) {
        val downloadUrl = pendingUrls.getOrNull(urlIndex) ?: return
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
            setTitle("喵岛更新 $releaseTag")
            setDescription("正在下载新版本安装包...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "mioo_dao_update.apk")
            setMimeType("application/vnd.android.package-archive")
        }

        try {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val file = File(dir, "mioo_dao_update.apk")
            if (file.exists()) file.delete()
        } catch (_: Exception) {
        }

        downloadId = downloadManager.enqueue(request)
        ensureReceiver(context)
    }

    private fun ensureReceiver(context: Context) {
        if (receiver != null) return
        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id != downloadId) return
                val manager = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = manager.query(query)
                var isSuccess = false
                var errorCode = -1
                if (cursor != null && cursor.moveToFirst()) {
                    val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val reasonIdx = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                    if (statusIdx != -1) {
                        val status = cursor.getInt(statusIdx)
                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            isSuccess = true
                        } else if (status == DownloadManager.STATUS_FAILED) {
                            errorCode = if (reasonIdx != -1) cursor.getInt(reasonIdx) else -1
                        }
                    }
                    cursor.close()
                }

                if (isSuccess) {
                    Toast.makeText(ctx, "下载完成，正在调起安装...", Toast.LENGTH_SHORT).show()
                    installApk(ctx)
                    clearReceiver(ctx)
                    return
                }

                if (urlIndex + 1 < pendingUrls.size) {
                    urlIndex += 1
                    Toast.makeText(ctx, "这条线路失败，正在换线路下载...", Toast.LENGTH_SHORT).show()
                    enqueueCurrent(ctx.applicationContext)
                    return
                }

                val errorDetail = when (errorCode) {
                    DownloadManager.ERROR_CANNOT_RESUME -> "无法恢复下载"
                    DownloadManager.ERROR_DEVICE_NOT_FOUND -> "找不到存储设备"
                    DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "文件已存在"
                    DownloadManager.ERROR_FILE_ERROR -> "存储文件错误"
                    DownloadManager.ERROR_HTTP_DATA_ERROR -> "HTTP 数据错误"
                    DownloadManager.ERROR_INSUFFICIENT_SPACE -> "存储空间不足"
                    DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "重定向次数过多"
                    DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "未处理的 HTTP 代码"
                    DownloadManager.ERROR_UNKNOWN -> "未知错误"
                    else -> "网络连接失败或连接超时 (代码: $errorCode)"
                }
                Toast.makeText(ctx, "更新包下载失败: $errorDetail", Toast.LENGTH_LONG).show()
                clearReceiver(ctx)
            }
        }

        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.applicationContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.applicationContext.registerReceiver(receiver, filter)
        }
    }

    private fun clearReceiver(context: Context) {
        try {
            receiver?.let { context.applicationContext.unregisterReceiver(it) }
        } catch (_: Exception) {
        }
        receiver = null
    }

    private fun openInBrowser(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "跳转失败，请手动下载", Toast.LENGTH_SHORT).show()
        }
    }

    private fun installApk(context: Context) {
        try {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val file = File(dir, "mioo_dao_update.apk")
            if (!file.exists()) return

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "自动安装失败，请在下载目录中手动安装: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}
