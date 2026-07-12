package com.mioo.dao.utils

import android.content.Context
import coil.imageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object StorageUtil {

    suspend fun getCacheSizeFormatted(context: Context): String = withContext(Dispatchers.IO) {
        val cacheSize = getFolderSize(context.cacheDir)
        formatSize(cacheSize)
    }

    suspend fun getDatabaseSizeFormatted(context: Context): String = withContext(Dispatchers.IO) {
        var dbSize = 0L
        val dbFile = context.getDatabasePath("mioo_dao.db")
        if (dbFile.exists()) dbSize += dbFile.length()
        val walFile = File(dbFile.absolutePath + "-wal")
        if (walFile.exists()) dbSize += walFile.length()
        val shmFile = File(dbFile.absolutePath + "-shm")
        if (shmFile.exists()) dbSize += shmFile.length()
        formatSize(dbSize)
    }

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    suspend fun clearImageCache(context: Context) = withContext(Dispatchers.IO) {
        // Clear Coil cache
        context.imageLoader.diskCache?.clear()
        context.imageLoader.memoryCache?.clear()
        
        // Clear anything in cacheDir recursively
        deleteFolderContents(context.cacheDir)
    }

    private fun getFolderSize(file: File): Long {
        var size = 0L
        if (file.isDirectory) {
            val files = file.listFiles()
            if (files != null) {
                for (child in files) {
                    size += getFolderSize(child)
                }
            }
        } else {
            size = file.length()
        }
        return size
    }

    private fun deleteFolderContents(file: File) {
        if (file.isDirectory) {
            val files = file.listFiles()
            if (files != null) {
                for (child in files) {
                    if (child.isDirectory) {
                        deleteFolderContents(child)
                    }
                    child.delete()
                }
            }
        }
    }

    private fun formatSize(size: Long): String {
        if (size <= 0) return "0.00 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format("%.2f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}
