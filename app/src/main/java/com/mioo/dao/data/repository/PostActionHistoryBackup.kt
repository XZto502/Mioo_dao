package com.mioo.dao.data.repository

import android.content.Context
import com.mioo.dao.data.local.HistoryType
import com.mioo.dao.data.local.PostActionHistoryEntity
import org.json.JSONObject

internal fun encodePostAction(record: PostActionHistoryEntity): JSONObject = JSONObject().apply {
    PostActionBackupCodec.encode(record).forEach { (key, value) -> put(key, value ?: JSONObject.NULL) }
}

internal fun decodePostAction(json: JSONObject, context: Context, expectedType: HistoryType): PostActionHistoryEntity? =
    PostActionBackupCodec.decode(json.toString(), expectedType) { id, extension, bytes ->
        actionHistoryImageFile(context, id, extension).also { it.writeBytes(bytes) }.absolutePath
    }
