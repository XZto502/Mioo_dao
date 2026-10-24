package com.mioo.dao.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mioo.dao.ui.theme.ImmersiveDialogEffect
import com.mioo.dao.ui.theme.immersiveDialogProperties

/**
 * X岛骰娘二级弹窗：快捷点数直接插入，或自定义 [起,止] 区间（发帖后由岛方掷骰）。
 */
@OptIn(ExperimentalLayoutApi::class)
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
            Text("骰娘")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 常用点数：芯片流式排布，点一下直接插入
                Text(
                    text = "常用点数",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("[1,100]", "[1,20]", "[1,10]", "[1,6]", "[0,1]").forEach { preset ->
                        AssistChip(
                            onClick = { onInsert(preset) },
                            label = { Text(preset, style = MaterialTheme.typography.labelLarge) }
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // 自定义区间：按实际语法 [起,止] 排布
                Text(
                    text = "自定义区间",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "[",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = rangeStart,
                        onValueChange = { v -> rangeStart = sanitizeDiceBound(v) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        placeholder = { Text("起") },
                        shape = RoundedCornerShape(10.dp)
                    )
                    Text(
                        text = ",",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = rangeEnd,
                        onValueChange = { v -> rangeEnd = sanitizeDiceBound(v) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        placeholder = { Text("止") },
                        shape = RoundedCornerShape(10.dp)
                    )
                    Text(
                        text = "]",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
