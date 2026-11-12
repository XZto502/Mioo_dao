package com.mioo.dao.ui.screens.settings


import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch


internal data class FolderInputValues(
    val editingFolderUuid: String?,
    val newFolderName: String,
    val newFolderUuid: String,
    val folders: List<com.mioo.dao.data.model.FeedFolder>
)

@Composable
internal fun FolderInputDialog(
    values: FolderInputValues,
    onNameChange: (String) -> Unit,
    onUuidChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit,
    onUpdate: (String, String, String) -> Unit
) {
    val (editingFolderUuid, newFolderName, newFolderUuid, folders) = values
    val scope = rememberCoroutineScope()
    run {
        var dialogVisible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { dialogVisible = true }
        val dialogScale by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (dialogVisible) 1f else 0.96f,
            animationSpec = androidx.compose.animation.core.tween(220), label = "folderDialog"
        )
        fun closeFolderDialog() {
            dialogVisible = false
            scope.launch { kotlinx.coroutines.delay(220); onDismiss() }
        }
        androidx.compose.material3.AlertDialog(
            modifier = Modifier.graphicsLayer { scaleX = dialogScale; scaleY = dialogScale; alpha = ((dialogScale - 0.96f) / 0.04f).coerceIn(0f, 1f) },
            shape = RoundedCornerShape(22.dp),
            tonalElevation = 0.dp,
            containerColor = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF181A20) else Color.White,
            onDismissRequest = { closeFolderDialog() },
            properties = com.mioo.dao.ui.theme.immersiveDialogProperties(),
            title = {
                com.mioo.dao.ui.theme.ImmersiveDialogEffect()
                Text(if (editingFolderUuid == null) "添加云端收藏夹" else "编辑云端收藏夹", fontSize = 17.5.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            },
            text = {
                Column {
                    val fieldColor = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF22252D) else Color(0xFFF2F4F8)
                    val fieldColors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = fieldColor, unfocusedContainerColor = fieldColor,
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        unfocusedBorderColor = Color.Transparent
                    )
                    Text("收藏夹名称", Modifier.padding(start = 2.dp, bottom = 6.dp), fontSize = 12.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = newFolderName, onValueChange = { onNameChange(it) },
                        shape = RoundedCornerShape(12.dp), colors = fieldColors,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.5.sp),
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(14.dp))
                    Text("订阅 UUID", Modifier.padding(start = 2.dp, bottom = 6.dp), fontSize = 12.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = newFolderUuid, onValueChange = { onUuidChange(it) },
                        shape = RoundedCornerShape(12.dp), colors = fieldColors,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.5.sp),
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            Box(Modifier.size(48.dp).clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) { onUuidChange(java.util.UUID.randomUUID().toString().replace("-", "").take(16)) },
                                contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.Refresh, "随机生成订阅 ID", Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    )
                    Text("可填入已有订阅 ID 多端同步，或点右侧图标随机生成",
                        Modifier.padding(start = 2.dp, top = 6.dp), fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    if (newFolderUuid.isNotBlank() && folders.any { it.uuid == newFolderUuid.trim() && it.uuid != editingFolderUuid }) {
                        Text("该订阅 ID 已存在", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                FolderActionButton(tonal = true,
                    onClick = {
                        if (newFolderName.isNotBlank() && newFolderUuid.isNotBlank() && folders.none { it.uuid == newFolderUuid.trim() && it.uuid != editingFolderUuid }) {
                            if (editingFolderUuid == null) onAdd(newFolderName.trim(), newFolderUuid.trim())
                            else onUpdate(editingFolderUuid!!, newFolderName.trim(), newFolderUuid.trim())
                            closeFolderDialog()
                            onNameChange("")
                            onUuidChange("")
                        }
                    }
                ) {
                    Text(if (editingFolderUuid == null) "添加" else "保存")
                }
            },
            dismissButton = {
                FolderActionButton(onClick = { closeFolderDialog() }) {
                    Text("取消")
                }
            }
        )
    }

}
