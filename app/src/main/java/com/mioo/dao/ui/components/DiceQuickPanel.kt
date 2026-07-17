package com.mioo.dao.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * X岛骰娘快捷面板：点选插入 `r NdM` 表达式。
 */
@Composable
fun DiceQuickPanel(
    onInsert: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var customCount by remember { mutableStateOf("1") }
    var customSides by remember { mutableStateOf("100") }
    var customBonus by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Text(
            text = "骰娘 · 插入后发帖由岛方掷骰",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )

        val rows = DICE_SHORTCUTS.chunked(DICE_PER_ROW)
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEach { item ->
                    TextButton(
                        onClick = { onInsert(item.insertText) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (row.size < DICE_PER_ROW) {
                    repeat(DICE_PER_ROW - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "自定义",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val fieldColors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.25f)
            )
            OutlinedTextField(
                value = customCount,
                onValueChange = { v ->
                    customCount = v.filter { it.isDigit() }.take(2)
                },
                modifier = Modifier.width(56.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(8.dp),
                colors = fieldColors,
                placeholder = { Text("1") }
            )
            Text("d", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = customSides,
                onValueChange = { v ->
                    customSides = v.filter { it.isDigit() }.take(4)
                },
                modifier = Modifier.width(72.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(8.dp),
                colors = fieldColors,
                placeholder = { Text("100") }
            )
            OutlinedTextField(
                value = customBonus,
                onValueChange = { v ->
                    // 允许 + - 与数字，如 +20 / -10
                    customBonus = v.filter { it.isDigit() || it == '+' || it == '-' }.take(5)
                },
                modifier = Modifier.width(72.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(8.dp),
                colors = fieldColors,
                placeholder = { Text("±") }
            )
            FilledTonalButton(
                onClick = {
                    val n = customCount.toIntOrNull()?.coerceIn(1, 99) ?: 1
                    val m = customSides.toIntOrNull()?.coerceIn(2, 9999) ?: 100
                    val bonus = customBonus.trim().let { b ->
                        when {
                            b.isEmpty() -> ""
                            b.startsWith("+") || b.startsWith("-") -> b
                            b.all { it.isDigit() } -> "+$b"
                            else -> ""
                        }
                    }
                    onInsert("r ${n}d${m}$bonus")
                },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text("插入")
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}
