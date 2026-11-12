package com.mioo.dao.ui.screens.settings


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mioo.dao.data.model.ImageCacheLimit
import com.mioo.dao.data.model.ThemeMode
import com.mioo.dao.ui.theme.settingsItemStyle
import com.mioo.dao.ui.theme.settingsDescriptionStyle
import androidx.compose.ui.graphics.Color


@Composable
internal fun AppearanceSettingsGroup(settings: com.mioo.dao.data.model.UserSettings, viewModel: SettingsViewModel) {
        Text("外观与主题", style = settingsDescriptionStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingsGroupCard(Modifier.fillMaxWidth()) {
        // 3. Dark Mode Toggle (显示模式)
        SettingsGroupCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingsHeaderIcon(Icons.Default.DarkMode, contentDescription = "显示模式")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "显示模式", style = settingsItemStyle())
                }
                Spacer(modifier = Modifier.height(8.dp))
                // 系统实时深色状态 + 当前生效的深色状态（点击回调里不能调 Composable，提前算好）
                val systemDark = isSystemInDarkTheme()
                val darkNow = when (settings.themeMode) {
                    ThemeMode.DARK -> true
                    ThemeMode.LIGHT -> false
                    ThemeMode.SYSTEM -> systemDark
                }

                ThemeMode.values().forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // 纯黑 (AMOLED) 仅深色可用：切到浅色（含跟随系统的浅色）时回落动态取色；
                                // 跟随系统分支用系统实时深色判断，与当前模式无关
                                val newDark = when (mode) {
                                    ThemeMode.DARK -> true
                                    ThemeMode.LIGHT -> false
                                    ThemeMode.SYSTEM -> systemDark
                                }
                                if (!newDark && settings.themeColor == "amoled") {
                                    viewModel.updateThemeColor("dynamic")
                                }
                                viewModel.updateThemeMode(mode)
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = settings.themeMode == mode,
                            onClick = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when(mode) {
                                ThemeMode.SYSTEM -> "跟随系统"
                                ThemeMode.LIGHT -> "浅色主题"
                                ThemeMode.DARK -> "深色主题"
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        SettingsGroupDivider()
        // 4. Theme Color Selection (主题颜色)
        SettingsGroupCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingsHeaderIcon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = "主题颜色"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "主题色选择", style = settingsItemStyle())
                }
                Spacer(modifier = Modifier.height(8.dp))

                // 纯黑 (AMOLED) 仅在深色模式（含跟随系统的深色）下提供
                val darkNow = when (settings.themeMode) {
                    ThemeMode.DARK -> true
                    ThemeMode.LIGHT -> false
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                }
                val colors = listOf(
                    "dynamic" to "动态取色 (Material You)",
                    "classic" to "经典紫色 (Classic M3)",
                    "teal" to "青蓝色 (Cyan)",
                    "pink" to "樱花粉 (Sakura Pink)",
                    "green" to "森林绿 (Forest Green)"
                ) + if (darkNow) {
                    listOf("amoled" to "纯黑 (AMOLED)")
                } else {
                    emptyList()
                }


                colors.forEach { (colorKey, colorLabel) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.updateThemeColor(colorKey) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = settings.themeColor == colorKey,
                            onClick = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = colorLabel,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        SettingsGroupDivider()
        // 5. Glass effect (毛玻璃特效)
        SettingsGroupCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingsHeaderIcon(
                        imageVector = Icons.Default.BlurOn,
                        contentDescription = "毛玻璃特效"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "毛玻璃特效",
                        style = settingsItemStyle()
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "开启后顶栏、底栏与卡片使用半透明毛玻璃风格，并显示背景流光；关闭后使用实色界面，可略微提升性能。完整模糊效果需 Android 12+，更低版本会自动使用约 95% 不透明底色。",
                    style = settingsDescriptionStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.updateGlassEffectEnabled(!settings.glassEffectEnabled)
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (settings.glassEffectEnabled) "已开启" else "已关闭",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    androidx.compose.material3.Switch(
                        checked = settings.glassEffectEnabled,
                        onCheckedChange = { viewModel.updateGlassEffectEnabled(it) }
                    )
                }
            }
        }

        }
}

@Composable
internal fun OfflineSettingsGroup(settings: com.mioo.dao.data.model.UserSettings, viewModel: SettingsViewModel) {
        val offlinePolicy by viewModel.offlineUpdatePolicy.collectAsState()
        SettingsGroupCard(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(0.dp)) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("离线串自动更新", style = settingsItemStyle())
                        Text("后台定期更新已保存的串和图片，默认关闭。系统通常每小时检查一次，可能延后执行。",
                            style = settingsDescriptionStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    androidx.compose.material3.Switch(checked = offlinePolicy.first, onCheckedChange = viewModel::updateOfflineAutoUpdate)
                }
                if (offlinePolicy.first) {
                    Spacer(Modifier.height(8.dp))
                    Text("更新网络条件", style = settingsDescriptionStyle())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = offlinePolicy.second, onClick = { viewModel.updateOfflineWifiOnly(true) }, label = { Text("仅 Wi-Fi") })
                        FilterChip(selected = !offlinePolicy.second, onClick = { viewModel.updateOfflineWifiOnly(false) }, label = { Text("所有网络") })
                    }
                    Text(if (offlinePolicy.second) "仅使用非计费 Wi-Fi 更新" else "允许使用移动数据下载串内图片，可能消耗较多流量",
                        style = settingsDescriptionStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // 6. Offline and Caching Settings (离线与缓存设置)
        val cacheSize by viewModel.cacheSizeState.collectAsState()
        val isPreloading by viewModel.isPreloadingState.collectAsState()
        val preloadProgress by viewModel.preloadProgressState.collectAsState()

        SettingsGroupCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingsHeaderIcon(Icons.Default.Save, contentDescription = "离线缓存")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "离线阅读与智能预加载",
                        style = settingsItemStyle()
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "开启后会在网络连接可用时预先下载帖子，方便您在无网环境下进行阅读。",
                    style = settingsDescriptionStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f)
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Cache Size & Clear
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "本地离线数据大小",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                        )
                        Text(
                            text = cacheSize,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    OutlinedButton(
                        onClick = { viewModel.clearCache() },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("清除数据")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Image Cache Size & Clear
                val imageCacheSize by viewModel.imageCacheSizeState.collectAsState()
                val context = LocalContext.current
                var cacheLimitDraft by remember(settings.cacheLimitMb) {
                    mutableStateOf(settings.cacheLimitMb.toFloat())
                }
                LaunchedEffect(settings.cacheLimitMb) {
                    viewModel.updateImageCacheSize(context)
                    kotlinx.coroutines.delay(700)
                    viewModel.updateImageCacheSize(context)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "图片缓存",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                        )
                        Text(
                            text = "$imageCacheSize / ${cacheLimitDraft.toInt()} MB",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    OutlinedButton(
                        onClick = { viewModel.clearImageCache(context) },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("清理图片")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "图片缓存上限: ${cacheLimitDraft.toInt()} MB",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "只限制图片磁盘缓存，默认 1024 MB。超出后自动删掉最久没看的图片，松手后立即生效。",
                    style = settingsDescriptionStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f)
                )
                Slider(
                    value = cacheLimitDraft,
                    onValueChange = { cacheLimitDraft = it },
                    onValueChangeFinished = { viewModel.updateCacheLimitMb(cacheLimitDraft.toInt()) },
                    valueRange = ImageCacheLimit.MIN_MB.toFloat()..ImageCacheLimit.MAX_MB.toFloat(),
                    steps = ImageCacheLimit.SLIDER_STEPS
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${ImageCacheLimit.MIN_MB} MB",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${ImageCacheLimit.MAX_MB} MB",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(modifier = Modifier.padding(start = 62.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(16.dp))

                // Smart Preload Mode
                Text(
                    text = "自动预加载网络模式",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "WIFI_ONLY" to "仅限 WiFi",
                        "ALL_NETWORKS" to "所有网络",
                        "DISABLED" to "关闭"
                    ).forEach { (mode, label) ->
                        val selected = settings.smartPreloadMode == mode
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.updateSmartPreloadMode(mode) },
                            label = { Text(label) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Preload Count Slider — 关闭预加载时无意义，隐藏
                if (settings.smartPreloadMode != "DISABLED") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "列表预加载数: ${settings.preloadCount} 串",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                        )
                    }
                    Slider(
                        value = settings.preloadCount.toFloat(),
                        onValueChange = { viewModel.updatePreloadCount(it.toInt()) },
                        valueRange = 5f..30f,
                        steps = 4
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(modifier = Modifier.padding(start = 62.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(16.dp))

                // Preload Bookmarks — 正式版屏蔽批量缓存，避免对岛服务器造成过大负担
                if (com.mioo.dao.BuildConfig.BETA || com.mioo.dao.BuildConfig.DEBUG) {
                    Button(
                        onClick = { viewModel.preloadBookmarks() },
                        enabled = !isPreloading,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(if (isPreloading) "正在缓存收藏夹..." else "一键缓存所有收藏串")
                    }

                    if (isPreloading && preloadProgress != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val (current, total) = preloadProgress!!
                        val progressFraction = if (total > 0) current.toFloat() / total.toFloat() else 0f
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LinearProgressIndicator(
                                progress = { progressFraction },
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "$current / $total",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
}
