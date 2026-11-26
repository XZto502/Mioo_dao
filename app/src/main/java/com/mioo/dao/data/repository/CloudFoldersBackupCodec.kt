package com.mioo.dao.data.repository

import com.mioo.dao.data.model.FeedFolder
import com.squareup.moshi.Moshi

internal data class CloudFoldersBackupData(
    val folders: List<FeedFolder>,
    val lastFeedFolder: String = ""
)

internal fun normalizeFeedFolders(folders: List<FeedFolder>): List<FeedFolder> {
    if (folders.isEmpty()) return emptyList()
    val seen = LinkedHashSet<String>(folders.size)
    val result = ArrayList<FeedFolder>(folders.size)
    for (folder in folders) {
        val uuid = folder.uuid.trim()
        if (uuid.isEmpty() || !seen.add(uuid)) continue
        result.add(FeedFolder(name = folder.name.trim(), uuid = uuid))
    }
    return result
}

internal fun mergeFeedFolders(existing: List<FeedFolder>, imported: List<FeedFolder>): List<FeedFolder> {
    val normalizedImported = normalizeFeedFolders(imported)
    val normalizedExisting = normalizeFeedFolders(existing)
    if (normalizedImported.isEmpty()) return normalizedExisting
    val importedUuids = normalizedImported.mapTo(HashSet(normalizedImported.size)) { it.uuid }
    val merged = ArrayList<FeedFolder>(normalizedImported.size + normalizedExisting.size)
    merged.addAll(normalizedImported)
    for (folder in normalizedExisting) {
        if (folder.uuid !in importedUuids) {
            merged.add(folder)
        }
    }
    return merged
}

internal fun resolveRestoredLastFeedFolder(lastFeedFolder: String, mergedFolders: List<FeedFolder>): String? {
    val trimmed = lastFeedFolder.trim()
    if (trimmed.isEmpty()) return null
    return trimmed.takeIf { uuid -> mergedFolders.any { it.uuid == uuid } }
}

internal object CloudFoldersBackupCodec {
    private val jsonAdapter = Moshi.Builder().build().adapter(Any::class.java)

    fun encodeMap(folders: List<FeedFolder>, lastFeedFolder: String): Map<String, Any> {
        val normalized = normalizeFeedFolders(folders)
        val folderMaps = normalized.map { folder ->
            linkedMapOf(
                "name" to folder.name,
                "uuid" to folder.uuid
            )
        }
        return linkedMapOf(
            "folders" to folderMaps,
            "lastFeedFolder" to lastFeedFolder.trim()
        )
    }

    fun decodeJson(raw: String): CloudFoldersBackupData? {
        val parsed = runCatching { jsonAdapter.fromJson(raw) }.getOrNull() ?: return null
        return when (parsed) {
            is Map<*, *> -> {
                val rawFolders = parsed["folders"] as? List<*> ?: emptyList<Any?>()
                val folders = parseFolderItems(rawFolders)
                val last = (parsed["lastFeedFolder"] as? String).orEmpty().trim()
                CloudFoldersBackupData(folders = folders, lastFeedFolder = last)
            }
            is List<*> -> CloudFoldersBackupData(folders = parseFolderItems(parsed), lastFeedFolder = "")
            else -> null
        }
    }

    private fun parseFolderItems(items: List<*>): List<FeedFolder> {
        val list = ArrayList<FeedFolder>(items.size)
        for (item in items) {
            val map = item as? Map<*, *> ?: continue
            val uuid = (map["uuid"] as? String).orEmpty()
            val name = (map["name"] as? String).orEmpty()
            list.add(FeedFolder(name = name, uuid = uuid))
        }
        return normalizeFeedFolders(list)
    }
}
