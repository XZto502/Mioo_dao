package com.mioo.dao.ui.screens.settings

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Restore
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    var newCookieInput by remember { mutableStateOf("") }
    var authMenuExpanded by remember { mutableStateOf(false) }
    
    var showAddFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var newFolderUuid by remember { mutableStateOf("") }
    
    val context = LocalContext.current

    val scanLauncher = rememberLauncherForActivityResult(
        contract = ScanContract()
    ) { result ->
        if (result.contents != null) {
            var cookieToAdd = result.contents
            try {
                val json = org.json.JSONObject(result.contents)
                if (json.has("cookie") || json.has("userhash")) {
                    cookieToAdd = result.contents
                }
            } catch (e: Exception) {
                // Not valid JSON, use raw text
            }
            viewModel.addCookie(cookieToAdd)
            Toast.makeText(context, "导入成功", Toast.LENGTH_SHORT).show()
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
        containerColor = Color.Transparent
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
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
                
                OutlinedTextField(
                    value = newCookieInput,
                    onValueChange = { newCookieInput = it },
                    label = { Text("新增发言饼干") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        Row {
                            IconButton(
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
                                }
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = "扫码输入")
                            }
                            IconButton(
                                onClick = {
                                    if (newCookieInput.isNotBlank()) {
                                        viewModel.addCookie(newCookieInput)
                                        newCookieInput = ""
                                        Toast.makeText(context, "发言饼干已添加", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "添加")
                            }
                        }
                    }
                )
                
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

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { viewModel.selectCookie(index) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = displayName,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            IconButton(
                                onClick = { viewModel.deleteCookie(cookie) }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "删除饼干",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
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
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = settings.themeMode == mode,
                            onClick = { viewModel.updateThemeMode(mode) }
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
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = settings.themeColor == colorKey,
                            onClick = { viewModel.updateThemeColor(colorKey) }
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

        // 5. Offline and Caching Settings (离线与缓存设置)
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
                            text = "本地离线缓存大小",
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
                        Text("清除缓存")
                    }
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

                // Preload Count Slider
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

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(16.dp))

                // Preload Bookmarks
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

        Spacer(modifier = Modifier.height(100.dp))
    }
}

    if (showAddFolderDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showAddFolderDialog = false },
            title = { Text("添加云端收藏夹") },
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
