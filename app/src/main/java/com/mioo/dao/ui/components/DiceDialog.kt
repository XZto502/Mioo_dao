package com.mioo.dao.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mioo.dao.ui.theme.ImmersiveDialogEffect
import com.mioo.dao.ui.theme.immersiveDialogProperties

/**
 * X岛骰娘二级弹窗：快捷点数直接插入，或自定义 [起,止] 区间（发帖后由岛方掷骰）。
 */
@Composable
fun DiceDialog(
    onDismiss: () -> Unit,
    onInsert: (String) -> Unit
) {
    var rangeStart by remember { mutableStateOf("1") }
    var rangeEnd by remember { mutableStateOf("100") }
    val startOk = rangeStart.toIntOrNull() != null
    val endOk = rangeEnd.toIntOrNull() != null

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = immersiveDialogProperties(),
        title = {
            ImmersiveDialogEffect()
            Text("骰娘 [起,止]")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 快捷点数：点了直接插入，与跳转页码的 首页/末页 同款交互
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = { onInsert("[1,100]") }, modifier = Modifier.weight(1f)) {
                        Text("[1,100]", style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(onClick = { onInsert("[1,20]") }, modifier = Modifier.weight(1f)) {
                        Text("[1,20]", style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(onClick = { onInsert("[1,10]") }, modifier = Modifier.weight(1f)) {
                        Text("[1,10]", style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(onClick = { onInsert("[0,1]") }, modifier = Modifier.weight(1f)) {
                        Text("[0,1]", style = MaterialTheme.typography.labelMedium)
                    }
                }

                // 自定义区间
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = rangeStart,
                        onValueChange = { v -> rangeStart = sanitizeDiceBound(v) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Text("起") }
                    )
                    OutlinedTextField(
                        value = rangeEnd,
                        onValueChange = { v -> rangeEnd = sanitizeDiceBound(v) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Text("止") }
                    )
                }
                Text(
                    text = "预览  [$rangeStart,$rangeEnd]，发帖后由岛方掷骰回填结果",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val start = rangeStart.toIntOrNull() ?: return@Button
                    val end = rangeEnd.toIntOrNull() ?: return@Button
                    // 自动纠正起止顺序，避免 [100,1]
                    onInsert("[${minOf(start, end)},${maxOf(start, end)}]")
                },
                enabled = startOk && endOk
            ) {
                Text("插入")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/** Keep optional leading '-' and digits only, max length for safety. */
private fun sanitizeDiceBound(raw: String): String {
    if (raw.isEmpty()) return ""
    val sb = StringBuilder(raw.length.coerceAtMost(8))
    for ((i, c) in raw.withIndex()) {
        when {
            c.isDigit() -> sb.append(c)
            c == '-' && i == 0 && sb.isEmpty() -> sb.append(c)
        }
        if (sb.length >= 8) break
    }
    return sb.toString()
}
