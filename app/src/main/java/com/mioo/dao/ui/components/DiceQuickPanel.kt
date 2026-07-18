package com.mioo.dao.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * X岛骰子快捷面板：插入 `[起,止]` 区间表达式，由岛方掷骰回填结果。
 * 例：`[1,100]`、`[0,1]`、`[10,20]`
 */
@Composable
fun DiceQuickPanel(
    onInsert: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var rangeStart by remember { mutableStateOf("1") }
    var rangeEnd by remember { mutableStateOf("100") }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
        unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.25f)
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Text(
            text = "骰子 · 语法 [起,止]，发帖后由岛方掷骰",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "[",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = rangeStart,
                onValueChange = { v ->
                    // 允许负号（如 [-10,10]）与数字
                    rangeStart = sanitizeDiceBound(v)
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(8.dp),
                colors = fieldColors,
                placeholder = { Text("起") },
                label = { Text("起") }
            )
            Text(
                text = ",",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = rangeEnd,
                onValueChange = { v ->
                    rangeEnd = sanitizeDiceBound(v)
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(8.dp),
                colors = fieldColors,
                placeholder = { Text("止") },
                label = { Text("止") }
            )
            Text(
                text = "]",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FilledTonalButton(
                onClick = {
                    val start = rangeStart.toIntOrNull()
                    val end = rangeEnd.toIntOrNull()
                    if (start == null || end == null) return@FilledTonalButton
                    // 自动纠正起止顺序，避免 [100,1]
                    val lo = minOf(start, end)
                    val hi = maxOf(start, end)
                    onInsert("[$lo,$hi]")
                },
                enabled = rangeStart.toIntOrNull() != null && rangeEnd.toIntOrNull() != null,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text("插入")
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "预览  [${rangeStart.ifBlank { "起" }},${rangeEnd.ifBlank { "止" }}]",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
    }
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
