package com.mioo.dao.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mioo.dao.ui.components.homePageColor
import com.mioo.dao.ui.components.homeCardColor
import com.mioo.dao.data.model.XdResponse
import com.mioo.dao.ui.theme.DaoTheme
import com.mioo.dao.utils.UpdatePromptController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    viewModel: SettingsViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToDrafts: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isCheckingUpdate by remember { mutableStateOf(false) }
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val cardColor = homeCardColor()
    val cardBorder: BorderStroke? = null
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = homePageColor(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(title = { Text("更多", style = MaterialTheme.typography.titleMedium.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize * 1.125f), fontWeight = FontWeight.Bold, maxLines = 1) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DaoTheme.colors.glassTopBar))
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())
            .verticalScroll(rememberScrollState()).navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = cardColor,
                shadowElevation = 0.dp, tonalElevation = 0.dp, border = cardBorder) {
                Column {
                    MoreEntry(Icons.Default.Search, "搜索", "串号直达、本地检索与全站搜索", onNavigateToSearch)
                    MoreDivider()
                    MoreEntry(Icons.Default.Settings, "应用设置", "饼干管理、显示模式、字号与屏蔽规则", onNavigateToSettings)
                    MoreDivider()
                    MoreEntry(Icons.Default.Description, "草稿箱", "未发完的回复与新串草稿，随时恢复编辑", onNavigateToDrafts)
                }
            }
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = cardColor,
                shadowElevation = 0.dp, tonalElevation = 0.dp, border = cardBorder) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MoreIcon(Icons.Outlined.Info)
                        Spacer(Modifier.width(12.dp))
                        Text("关于 喵岛", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(Modifier.height(8.dp))
                    MoreInfoRow("作者") { MoreValue("Sanae-Koishi") }
                    MoreInfoRow("版本") {
                        MoreValue("v${com.mioo.dao.BuildConfig.VERSION_NAME}")
                        Spacer(Modifier.width(8.dp))
                        Surface(onClick = {
                                isCheckingUpdate = true
                                viewModel.checkUpdate { response ->
                                    isCheckingUpdate = false
                                    when (response) {
                                        is XdResponse.Success -> {
                                            val release = response.data
                                            val currentVersion = com.mioo.dao.BuildConfig.VERSION_NAME
                                            
                                            val cleanCurrent = currentVersion.trim().lowercase().removePrefix("v")
                                            val cleanLatest = release.tagName.trim().lowercase().removePrefix("v")
                                            
                                            val isNewer = if (cleanCurrent == cleanLatest) false else {
                                                val currentParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }
                                                val latestParts = cleanLatest.split(".").mapNotNull { it.toIntOrNull() }
                                                val minLength = minOf(currentParts.size, latestParts.size)
                                                var newer = false
                                                var decided = false
                                                for (i in 0 until minLength) {
                                                    if (latestParts[i] > currentParts[i]) {
                                                        newer = true
                                                        decided = true
                                                        break
                                                    }
                                                    if (latestParts[i] < currentParts[i]) {
                                                        newer = false
                                                        decided = true
                                                        break
                                                    }
                                                }
                                                if (!decided) latestParts.size > currentParts.size else newer
                                            }
                                            
                                            if (isNewer) {
                                                val shown = UpdatePromptController.offerManual(release)
                                                if (!shown) {
                                                    android.widget.Toast.makeText(context, "更新已在下载", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                android.widget.Toast.makeText(context, "已经是最新版本", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        is XdResponse.Error -> {
                                            android.widget.Toast.makeText(context, "检查更新失败: ${response.message}", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                        }, enabled = !isCheckingUpdate, shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                            Text(if (isCheckingUpdate) "正在检查…" else "检查更新", Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1, softWrap = false)
                        }
                    }
                    MoreInfoRow("开源协议") { MoreValue("GPL v3") }
                    MoreInfoRow("项目源码") {
                        Surface(onClick = {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/XZto502/Mioo_dao"))) }
                        }, shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)) {
                            Text("GitHub · XZto502/Mioo_dao", Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary,
                                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f))
                    Spacer(Modifier.height(10.dp))
                    Text("喵岛是为 X 岛设计的 Material Design 3 第三方客户端", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                }
            }
        }
    }
}

@Composable
private fun MoreIcon(icon: ImageVector) {
    Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
        contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun MoreEntry(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        MoreIcon(icon)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text(subtitle, fontSize = 12.5.sp, fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
    }
}

@Composable
private fun MoreDivider() = HorizontalDivider(Modifier.padding(start = 66.dp, end = 16.dp), thickness = 0.5.dp,
    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f))

@Composable
private fun MoreInfoRow(label: String, content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 34.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

@Composable
private fun MoreValue(text: String) {
    Text(text, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, softWrap = false)
}
