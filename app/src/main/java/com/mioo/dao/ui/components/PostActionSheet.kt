package com.mioo.dao.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.Reply
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mioo.dao.ui.theme.ImmersiveDialogEffect
import com.mioo.dao.ui.theme.immersiveDialogProperties

data class PostAction(val label: String, val icon: ImageVector, val destructive: Boolean = false, val onClick: () -> Unit)

object PostActionIcons {
    val quote = Icons.Outlined.Reply
    val share = Icons.Outlined.Share
    val block = Icons.Outlined.Block
    val blockUser = Icons.Outlined.PersonOff
    val copy = Icons.Outlined.ContentCopy
    val report = Icons.Outlined.Flag
}

@Composable
fun PostActionSheet(title: String, onDismiss: () -> Unit, actions: List<PostAction>) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = immersiveDialogProperties(),
        title = { ImmersiveDialogEffect(); Text(title) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                actions.forEach { action ->
                    val color = if (action.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    TextButton(onClick = { action.onClick(); onDismiss() }, modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(action.icon, null, Modifier.size(20.dp), tint = color)
                            Spacer(Modifier.width(12.dp))
                            Text(action.label, color = color)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
