package com.mioo.dao.ui.screens.settings


import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.mioo.dao.data.repository.BackupRepository


@Composable
internal fun BackupPickerDialog(
    pendingBackupSections: Set<String>,
    onSectionsChange: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
    onCancel: () -> Unit,
    onExport: () -> Unit
) {
    run {
        val sectionLabels = listOf(
            "cookies" to "发言饼干（含备注/鉴权/选中）",
            "history" to "浏览历史（含阅读位置）",
            "replyHistory" to "发言历史（含附图）",
            "newThreadHistory" to "发串历史（含附图）",
            "bookmarks" to "本地收藏",
            "drafts" to "草稿箱",
            "settings" to "应用设置（主题/字体/开关等）",
            "blocks" to "屏蔽与板块（串/饼干/关键词/置顶/展开）"
        )
        AlertDialog(
            onDismissRequest = { onDismiss() },
            properties = com.mioo.dao.ui.theme.immersiveDialogProperties(),
            title = { com.mioo.dao.ui.theme.ImmersiveDialogEffect(); Text("选择备份内容") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    sectionLabels.forEach { (key, label) ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { onSectionsChange(toggleSection(pendingBackupSections, key)) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = key in pendingBackupSections,
                                onCheckedChange = { onSectionsChange(toggleSection(pendingBackupSections, key)) }
                            )
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDismiss()
                        onExport()
                    },
                    enabled = pendingBackupSections.isNotEmpty()
                ) { Text("导出") }
            },
            dismissButton = { TextButton(onClick = { onCancel() }) { Text("取消") } }
        )
    }

}

@Composable
internal fun BackupResultDialog(r: BackupRepository.ImportResult, onDismiss: () -> Unit) {
    run {
        AlertDialog(
            onDismissRequest = { onDismiss() },
            properties = com.mioo.dao.ui.theme.immersiveDialogProperties(),
            title = { com.mioo.dao.ui.theme.ImmersiveDialogEffect(); Text("恢复完成") },
            text = {
                val lines = buildList {
                    if (r.legacyCookiesOnly) add("旧版饼干备份：已导入 ${r.cookies} 个饼干")
                    else {
                        if (r.cookies >= 0) add("发言饼干：${r.cookies} 个")
                        if (r.history >= 0) add("浏览历史：${r.history} 条")
                        if (r.replyHistory >= 0) add("发言历史：${r.replyHistory} 条")
                        if (r.newThreadHistory >= 0) add("发串历史：${r.newThreadHistory} 条")
                        if (r.bookmarks >= 0) add("本地收藏：${r.bookmarks} 条")
                        if (r.drafts >= 0) add("草稿箱：${r.drafts} 条")
                        if (r.settings) add("应用设置：已恢复")
                        if (r.blocks) add("屏蔽与板块：已恢复")
                    }
                }
                Text(lines.joinToString("\n"))
            },
            confirmButton = { TextButton(onClick = { onDismiss() }) { Text("好") } }
        )
    }
}
