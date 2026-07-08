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

    fun downloadAndInstall(context: Context, release: GithubRelease) {
        val apkAsset = release.assets?.firstOrNull { it.name.endsWith(".apk") }
        val downloadUrl = apkAsset?.browserDownloadUrl ?: release.htmlUrl

        if (downloadUrl == release.htmlUrl) {
            // No APK found, fallback to browser
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "跳转失败，请手动下载", Toast.LENGTH_SHORT).show()
            }
            return
        }

        Toast.makeText(context, "正在后台下载更新包，请稍候...", Toast.LENGTH_LONG).show()

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = Uri.parse(downloadUrl)
        val request = DownloadManager.Request(uri).apply {
            setTitle("喵岛更新 ${release.tagName}")
            setDescription("正在下载新版本安装包...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "mioo_dao_update.apk")
            setMimeType("application/vnd.android.package-archive")
        }

        // Clean up previous file if exists to prevent cache issues
        try {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val file = File(dir, "mioo_dao_update.apk")
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            // ignore
        }

        downloadId = downloadManager.enqueue(request)

        // Register broadcast receiver dynamically
        if (receiver != null) {
            try {
                context.applicationContext.unregisterReceiver(receiver)
            } catch (e: Exception) {
                // ignore
            }
        }

        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id == downloadId) {
                    installApk(ctx)
                    // Clean up receiver
                    try {
                        ctx.applicationContext.unregisterReceiver(this)
                    } catch (e: Exception) {
                        // ignore
                    }
                    receiver = null
                }
            }
        }

        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.applicationContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.applicationContext.registerReceiver(receiver, filter)
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
