package com.mioo.dao.ui.screens.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FormatLineSpacing
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Image
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mioo.dao.data.model.ImageCacheLimit
import com.mioo.dao.data.model.ThemeMode
import com.mioo.dao.ui.theme.DaoTheme
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settingsState.collectAsState()
    var authMenuExpanded by remember { mutableStateOf(false) }
    
    var showAddFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var newFolderUuid by remember { mutableStateOf("") }
    
    val context = LocalContext.current

    val scanLauncher = rememberLauncherForActivityResult(
        contract = ScanContract()
    ) { result ->
        if (result.contents == null) return@rememberLauncherForActivityResult
        val cookieToAdd = parseImportedCookie(result.contents)
        if (cookieToAdd == null) {
            Toast.makeText(context, "无效的饼干二维码", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        viewModel.addCookie(cookieToAdd)
        Toast.makeText(context, "导入成功", Toast.LENGTH_SHORT).show()
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scanQrFromUri(context, uri) { decodedText ->
                if (decodedText == null) {
                    Toast.makeText(context, "未在图片中检测到二维码", Toast.LENGTH_LONG).show()
                    return@scanQrFromUri
                }
                val cookieToAdd = parseImportedCookie(decodedText)
                if (cookieToAdd == null) {
                    Toast.makeText(context, "无效的饼干二维码", Toast.LENGTH_SHORT).show()
                    return@scanQrFromUri
                }
                viewModel.addCookie(cookieToAdd)
                Toast.makeText(context, "导入成功", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val exportFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(viewModel.exportCookiesJson().toByteArray(Charsets.UTF_8))
            }
            Toast.makeText(context, "饼干备份已导出到文件", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, "导出失败: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()
                ?.use { it.readText() }.orEmpty()
            val n = viewModel.importCookiesJson(text)
            if (n > 0) {
                Toast.makeText(context, "已从文件导入 $n 个饼干", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "文件中没有可导入的饼干", Toast.LENGTH_SHORT).show()
            }
        }.onFailure {
            Toast.makeText(context, "导入失败: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("应用设置", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DaoTheme.colors.glassTopBar
                )
            )
        },
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

        // 1. Cookie Configuration (饼干设置)
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Cookie, contentDescription = "饼干设置")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "发言饼干管理 (User Hash)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "在此添加并选择您发帖和回复时使用的发言饼干 (userhash)。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val options = ScanOptions().apply {
                                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                setPrompt("请扫描含有饼干的二维码")
                                setBeepEnabled(false)
                                setOrientationLocked(true)
                                setCaptureActivity(CustomCaptureActivity::class.java)
                                setBarcodeImageEnabled(false)
                            }
                            scanLauncher.launch(options)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("扫码添加")
                    }
                    OutlinedButton(
                        onClick = {
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("相册选图扫码")
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // List of speaking cookies
                if (settings.cookiesList.isEmpty()) {
                    Text(
                        text = "暂无发言饼干，请在上方添加或扫码导入",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                    )
                } else {
                    settings.cookiesList.forEachIndexed { index, cookie ->
                        val isSelected = settings.selectedCookieIndex == index
                        var displayName = cookie
                        try {
                            if (cookie.startsWith("{")) {
                                val json = org.json.JSONObject(cookie)
                                displayName = json.optString("name", "")
                                if (displayName.isEmpty()) {
                                    displayName = json.optString("cookie", "")
                                }
                                if (displayName.isEmpty()) {
                                    displayName = json.optString("userhash", cookie)
                                }
                            }
                        } catch (e: Exception) {}
                        if (!cookie.startsWith("{")) displayName = cookieSixCharId(cookie)
                        val sixId = cookieSixCharId(cookie)
                        val note = settings.cookieNotes[cookie].orEmpty()
                        val label = if (note.isNotBlank()) "$note · $displayName" else displayName

                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.selectCookie(index) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = null
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = label,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                IconButton(onClick = { viewModel.deleteCookie(cookie) }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "删除饼干",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            Text(
                                text = "ID: $sixId",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 48.dp, bottom = 2.dp)
                            )
                            OutlinedTextField(
                                value = note,
                                onValueChange = { viewModel.setCookieNote(cookie, it) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 48.dp, end = 8.dp),
                                singleLine = true,
                                placeholder = { Text("备注名（如：日常饼干）") },
                                textStyle = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
                                .format(java.util.Date())
                            exportFileLauncher.launch("mioo-cookies-$stamp.json")
                        }) { Text("导出到文件") }
                        OutlinedButton(onClick = {
                            importFileLauncher.launch(
                                arrayOf("application/json", "text/plain", "application/octet-stream")
                            )
                        }) { Text("从文件导入") }
                    }
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                
                // 2. Auth Cookie (鉴权饼干)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Cookie, contentDescription = "鉴权饼干")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "高级：鉴权饼干 (Auth)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                // Dropdown to select from existing cookies
                androidx.compose.foundation.layout.Box {
                    var authDisplayName = settings.authCookie
                    if (authDisplayName.isNotEmpty()) {
                        try {
                            if (authDisplayName.startsWith("{")) {
                                val json = org.json.JSONObject(authDisplayName)
                                authDisplayName = json.optString("name", "")
                                if (authDisplayName.isEmpty()) authDisplayName = json.optString("cookie", "")
                                if (authDisplayName.isEmpty()) authDisplayName = json.optString("userhash", settings.authCookie)
                            }
                        } catch (e: Exception) {}
                    } else {
                        authDisplayName = "无 (不使用鉴权)"
                    }

                    OutlinedTextField(
                        value = authDisplayName,
                        onValueChange = {},
                        label = { Text("当前鉴权饼干") },
                        readOnly = true,
                        enabled = false,
                        modifier = Modifier.fillMaxWidth().clickable { authMenuExpanded = true },
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "展开"
                            )
                        }
                    )
                    androidx.compose.material3.DropdownMenu(
                        expanded = authMenuExpanded,
                        onDismissRequest = { authMenuExpanded = false }
                    ) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("无 (不使用鉴权)") },
                            onClick = {
                                viewModel.updateAuthCookie("")
                                authMenuExpanded = false
                            }
                        )
                        settings.cookiesList.forEach { cookieStr ->
                            var displayName = cookieStr
                            try {
                                if (cookieStr.startsWith("{")) {
                                    val json = org.json.JSONObject(cookieStr)
                                    displayName = json.optString("name", "")
                                    if (displayName.isEmpty()) displayName = json.optString("cookie", "")
                                    if (displayName.isEmpty()) displayName = json.optString("userhash", cookieStr)
                                }
                            } catch (e: Exception) {}

                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(displayName) },
                                onClick = {
                                    viewModel.updateAuthCookie(cookieStr)
                                    authMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // 3. Multiple Feed Folders (多云端收藏夹)
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(androidx.compose.material.icons.Icons.Default.Save, contentDescription = "云端收藏夹")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "云端收藏夹",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = { showAddFolderDialog = true }) {
                        Icon(androidx.compose.material.icons.Icons.Default.Add, contentDescription = "添加收藏夹")
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "添加额外的收藏夹UUID，可在收藏界面作为卡片独立浏览。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                settings.feedFolders.forEach { folder ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = folder.name, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            Text(text = folder.uuid, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { viewModel.removeFeedFolder(folder.uuid) }) {
                            Icon(androidx.compose.material.icons.Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    androidx.compose.material3.HorizontalDivider()
                }
            }
        }

        // 3. Dark Mode Toggle (显示模式)
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DarkMode, contentDescription = "显示模式")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "显示模式", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                
                ThemeMode.values().forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.updateThemeMode(mode) }
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

        // 4. Theme Color Selection (主题颜色)
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = "主题颜色"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "主题色选择", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                
                val colors = listOf(
                    "dynamic" to "动态取色 (Material You)",
                    "classic" to "经典紫色 (Classic M3)",
                    "teal" to "青蓝色 (Cyan)",
                    "pink" to "樱花粉 (Sakura Pink)",
                    "green" to "森林绿 (Forest Green)"
                )
                
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

        // 5. Glass effect (毛玻璃特效)
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.BlurOn,
                        contentDescription = "毛玻璃特效"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "毛玻璃特效",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "开启后顶栏、底栏与卡片使用半透明毛玻璃风格，并显示背景流光；关闭后使用实色界面，可略微提升性能。完整模糊效果需 Android 12+，更低版本会自动使用约 95% 不透明底色。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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

        // 6. Font Size Configuration (字体大小)
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FormatSize, contentDescription = "字体大小")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "字体大小", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "滑动缩放以调整帖子和回复的内容文本字号大小。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "最小", style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = "${(settings.fontSizeScale * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(text = "最大", style = MaterialTheme.typography.bodySmall)
                }
                
                Slider(
                    value = settings.fontSizeScale,
                    onValueChange = { viewModel.updateFontSize(it) },
                    valueRange = 0.8f..1.5f,
                    steps = 6
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "预览文字：欢迎来到喵岛讨论区！",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 行间距
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FormatLineSpacing, contentDescription = "行间距")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "行间距", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "滑动调整行与行之间的距离，与字体大小独立生效，全应用即时应用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "最小", style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = "${(settings.lineHeightScale * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(text = "最大", style = MaterialTheme.typography.bodySmall)
                }

                Slider(
                    value = settings.lineHeightScale,
                    onValueChange = { viewModel.updateLineHeight(it) },
                    valueRange = 0.8f..2.0f,
                    steps = 11
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "预览文字：欢迎来到喵岛讨论区！\n行间距越大，正文越舒展，阅读长串更轻松。",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 字体样式
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FontDownload, contentDescription = "字体样式")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "字体样式", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "更换全应用字体，与字体大小、行间距独立生效。部分机型手写体会回退为默认字体。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                val fontOptions = listOf(
                    Triple("default", "默认字体", null as FontFamily?),
                    Triple("serif", "衬线字体 (Serif)", FontFamily.Serif),
                    Triple("monospace", "等宽字体 (Monospace)", FontFamily.Monospace),
                    Triple("cursive", "手写体 (Cursive)", FontFamily.Cursive)
                )
                fontOptions.forEach { (key, label, family) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.updateFontFamily(key) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = settings.fontFamily == key, onClick = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = family
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 7. Block Management (屏蔽管理)
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Block,
                        contentDescription = "屏蔽管理"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "屏蔽管理",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "已屏蔽的串",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (settings.blockedThreads.isEmpty()) {
                    Text(
                        text = "暂无被屏蔽的串",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else {
                    settings.blockedThreads.forEach { threadId ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "No.$threadId",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            IconButton(onClick = { viewModel.removeBlockedThread(threadId) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "取消屏蔽",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "已屏蔽的发言饼干 ID",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (settings.blockedUsers.isEmpty()) {
                    Text(
                        text = "暂无被屏蔽的 ID",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else {
                    settings.blockedUsers.forEach { userHash ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = userHash,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            IconButton(onClick = { viewModel.removeBlockedUser(userHash) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "取消屏蔽",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "已屏蔽的关键字",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                
                var newKeyword by remember { mutableStateOf("") }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newKeyword,
                        onValueChange = { newKeyword = it },
                        placeholder = { Text("输入要屏蔽的关键字...", style = MaterialTheme.typography.bodyMedium) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (newKeyword.isNotBlank()) {
                                viewModel.addBlockedKeyword(newKeyword.trim())
                                newKeyword = ""
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "添加",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                if (settings.blockedKeywords.isEmpty()) {
                    Text(
                        text = "暂无屏蔽的关键字",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else {
                    settings.blockedKeywords.forEach { keyword ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = keyword,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            IconButton(onClick = { viewModel.removeBlockedKeyword(keyword) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "取消屏蔽",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }

        // 5. Subscription notifications (蓝岛-style local poll)
        val notifPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                viewModel.updateSubscriptionNotifications(true)
                Toast.makeText(context, "已开启订阅通知", Toast.LENGTH_SHORT).show()
            } else {
                viewModel.updateSubscriptionNotifications(false)
                Toast.makeText(context, "需要通知权限才能提醒新回复", Toast.LENGTH_SHORT).show()
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = "订阅通知")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "X岛订阅新回复通知",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "后台定期向 X岛 官方接口查询：云端订阅夹（feed UUID）与本地收藏串是否有新回复，有更新则发系统通知。\n" +
                        "说明：岛方未对第三方开放实时推送通道，蓝岛等客户端也是本地轮询同一套订阅数据，并非互相收对方 App 的推送。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val enable = !settings.subscriptionNotificationsEnabled
                            if (enable && android.os.Build.VERSION.SDK_INT >= 33) {
                                notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.updateSubscriptionNotifications(enable)
                            }
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (settings.subscriptionNotificationsEnabled) "已开启" else "已关闭",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    androidx.compose.material3.Switch(
                        checked = settings.subscriptionNotificationsEnabled,
                        onCheckedChange = { enable ->
                            if (enable && android.os.Build.VERSION.SDK_INT >= 33) {
                                notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.updateSubscriptionNotifications(enable)
                            }
                        }
                    )
                }
                if (settings.subscriptionNotificationsEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "检查间隔: ${settings.notificationIntervalMinutes} 分钟（最短 15 分钟）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = settings.notificationIntervalMinutes.toFloat(),
                        onValueChange = {
                            viewModel.updateNotificationIntervalMinutes(it.toInt())
                        },
                        valueRange = 15f..180f,
                        steps = 10
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 草稿箱开关
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Save, contentDescription = "草稿箱")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "草稿箱",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "关闭后，未发送的回复与新串内容不再自动保存到草稿箱",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.updateDraftBoxEnabled(!settings.draftBoxEnabled)
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (settings.draftBoxEnabled) "已开启" else "已关闭",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    androidx.compose.material3.Switch(
                        checked = settings.draftBoxEnabled,
                        onCheckedChange = { viewModel.updateDraftBoxEnabled(it) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 引用自动显示开关
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FormatQuote, contentDescription = "引用自动显示")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "引用自动显示",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "开启后，回复中的 >>No.xxx 引用以收起卡片形式显示在正文中，点击可展开；关闭后仅保留链接文字，点击弹出浮层查看原帖",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.updateQuoteAutoDisplay(!settings.quoteAutoDisplayEnabled)
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (settings.quoteAutoDisplayEnabled) "已开启" else "已关闭",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    androidx.compose.material3.Switch(
                        checked = settings.quoteAutoDisplayEnabled,
                        onCheckedChange = { viewModel.updateQuoteAutoDisplay(it) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 6. Offline and Caching Settings (离线与缓存设置)
        val cacheSize by viewModel.cacheSizeState.collectAsState()
        val isPreloading by viewModel.isPreloadingState.collectAsState()
        val preloadProgress by viewModel.preloadProgressState.collectAsState()

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Save, contentDescription = "离线缓存")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "离线阅读与智能预加载",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "开启后会在网络连接可用时预先下载帖子，方便您在无网环境下进行阅读。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
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
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
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



        Spacer(modifier = Modifier.height(100.dp))
    }
}

    if (showAddFolderDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showAddFolderDialog = false },
            properties = com.mioo.dao.ui.theme.immersiveDialogProperties(),
            title = {
                com.mioo.dao.ui.theme.ImmersiveDialogEffect()
                Text("添加云端收藏夹")
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        label = { Text("收藏夹名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newFolderUuid,
                        onValueChange = { newFolderUuid = it },
                        label = { Text("订阅 UUID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        if (newFolderName.isNotBlank() && newFolderUuid.isNotBlank()) {
                            viewModel.addFeedFolder(newFolderName, newFolderUuid)
                            showAddFolderDialog = false
                            newFolderName = ""
                            newFolderUuid = ""
                        }
                    }
                ) {
                    Text("添加")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showAddFolderDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * Accept a cookie QR. JSON must contain a non-blank cookie or userhash.
 * A raw userhash is stored only when it looks like a cookie, not a URL or sentence.
 * Returns the same string the app already saves (full JSON, or the raw hash).
 */
private fun parseImportedCookie(raw: String?): String? {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty()) return null
    if (text.startsWith("{")) {
        return runCatching {
            val json = org.json.JSONObject(text)
            val value = json.optString("cookie").ifBlank { json.optString("userhash") }
            if (value.isNotBlank()) text else null
        }.getOrNull()
    }
    if (text.length !in 8..512) return null
    if (text.any { it.isWhitespace() }) return null
    if (text.startsWith("http://", ignoreCase = true) || text.startsWith("https://", ignoreCase = true)) {
        return null
    }
    return text
}

/** X岛风格 6 位 ID：JSON 饼干取 userhash 字段前 6 位，否则取饼干值前 6 位 */
private fun cookieSixCharId(cookie: String): String {
    if (cookie.startsWith("{")) {
        runCatching {
            val json = org.json.JSONObject(cookie)
            val userhash = json.optString("userhash", "")
            if (userhash.isNotBlank()) return userhash.take(6)
            val value = json.optString("cookie", "")
            if (value.isNotBlank()) return value.take(6)
        }
    }
    return cookie.take(6)
}

private fun scanQrFromUri(context: Context, uri: Uri, onResult: (String?) -> Unit) {
    Thread {
        val text = try {
            decodeQrFromUri(context, uri)
        } catch (_: OutOfMemoryError) {
            null
        } catch (_: Exception) {
            null
        }
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            onResult(text)
        }
    }.start()
}

private fun decodeQrFromUri(context: Context, uri: Uri): String? {
    val resolver = context.contentResolver
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { stream ->
        android.graphics.BitmapFactory.decodeStream(stream, null, bounds)
    }
    val width = bounds.outWidth
    val height = bounds.outHeight
    if (width <= 0 || height <= 0) return null
    var sample = 1
    while (width / sample > 1024 || height / sample > 1024) {
        sample *= 2
    }
    val opts = android.graphics.BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
    }
    val bitmap = resolver.openInputStream(uri)?.use { stream ->
        android.graphics.BitmapFactory.decodeStream(stream, null, opts)
    } ?: return null
    return try {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val source = com.google.zxing.RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
        val binaryBitmap = com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(source))
        com.google.zxing.qrcode.QRCodeReader().decode(binaryBitmap).text
    } finally {
        bitmap.recycle()
    }
}
