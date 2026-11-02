package com.mioo.dao.data.repository

import android.content.Context
import java.io.File

/** IDs and filenames from imported backups cannot escape this private directory. */
internal fun actionHistoryImageFile(context: Context, id: String, extension: String): File {
    val directory = File(context.filesDir, "action_history_images").apply { mkdirs() }
    val safeId = java.security.MessageDigest.getInstance("SHA-256").digest(id.toByteArray())
        .joinToString("") { "%02x".format(it) }
    val safeExtension = extension.lowercase().takeIf { it in setOf("jpg", "jpeg", "png", "webp", "gif") } ?: "jpg"
    return File(directory, "$safeId.$safeExtension")
}

internal fun deleteActionHistoryImage(context: Context, path: String?) {
    if (path == null) return
    val file = File(path)
    if (file.canonicalFile.parentFile == File(context.filesDir, "action_history_images").canonicalFile) file.delete()
}
