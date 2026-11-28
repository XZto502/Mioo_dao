package com.mioo.dao.ui.screens.settings

import com.mioo.dao.ui.screens.search.openAppDefaultLinksSettings

import androidx.compose.foundation.layout.heightIn

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.mioo.dao.ui.theme.isReducedMotionEnabled
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
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FormatLineSpacing
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CloudOff
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
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mioo.dao.data.model.ImageCacheLimit
import com.mioo.dao.data.model.ThemeMode
import com.mioo.dao.data.repository.BackupRepository
import com.mioo.dao.ui.theme.DaoTheme
import com.mioo.dao.ui.theme.settingsItemStyle
import com.mioo.dao.ui.theme.settingsDescriptionStyle
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit,
    onOpenNetworkDiagnostics: () -> Unit = {},
    highlightSection: String? = null,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settingsState.collectAsState()
    val updateChannel by viewModel.updateChannel.collectAsState()
    var showUpdateChannelDialog by remember { mutableStateOf(false) }
    val predictiveBackEnabled by viewModel.predictiveBackEnabled.collectAsState()
    val scrollTouchGuardEnabled by viewModel.scrollTouchGuardEnabled.collectAsState()
    val consent by viewModel.thirdPartyConsent.collectAsState()
    val allowThirdParty = consent?.allowThirdPartyServices == true
    val forumPreviewLines by viewModel.forumPreviewLines.collectAsState()
    var authMenuExpanded by remember { mutableStateOf(false) }

    var showAddFolderDialog by remember { mutableStateOf(false) }
    var editingFolderUuid by remember { mutableStateOf<String?>(null) }
    var deletingFolder by remember { mutableStateOf<com.mioo.dao.data.model.FeedFolder?>(null) }
    var newFolderName by remember { mutableStateOf("") }
    var newFolderUuid by remember { mutableStateOf("") }

    val context = LocalContext.current
    val kaomojiStore = remember(context) { com.mioo.dao.data.local.SettingsDataStore(context.applicationContext) }
    val customKaomoji by kaomojiStore.customKaomojiFlow.collectAsState(initial = emptyList())
    val kaomojiScope = rememberCoroutineScope()
    var showKaomojiManager by remember { mutableStateOf(false) }
    var confirmResetQuickInputs by remember { mutableStateOf(false) }
    var editingKaomoji by remember { mutableStateOf<com.mioo.dao.data.local.CustomKaomoji?>(null) }

    if (confirmResetQuickInputs) {
        AlertDialog(onDismissRequest = { confirmResetQuickInputs = false },
            title = { Text("恢复默认快捷输入") },
            text = { Text("恢复后，自定义的快捷输入项将被移除。") },
            dismissButton = { TextButton(onClick = { confirmResetQuickInputs = false }) { Text("取消") } },
            confirmButton = { TextButton(onClick = {
                kaomojiScope.launch { kaomojiStore.resetQuickInputs() }
                confirmResetQuickInputs = false
            }) { Text("恢复默认") } })
    }
    if (showKaomojiManager) {
        AlertDialog(onDismissRequest = { showKaomojiManager = false }, title = { Text("自定义颜文字") },
            text = {
                androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    item { TextButton(onClick = { editingKaomoji = com.mioo.dao.data.local.CustomKaomoji(java.util.UUID.randomUUID().toString(), "") }) { Text("添加") } }
                    items(customKaomoji.size) { index ->
                        val item = customKaomoji[index]
                        TextButton(onClick = { editingKaomoji = item }) { Text(item.alias.trim().ifEmpty { item.text }, maxLines = 1) }
                    }
                }
            }, confirmButton = { TextButton(onClick = { showKaomojiManager = false }) { Text("完成") } })
    }
    editingKaomoji?.let { item ->
        com.mioo.dao.ui.components.CustomKaomojiDialog(item, onDismiss = { editingKaomoji = null },
            onSave = { saved -> kaomojiScope.launch { kaomojiStore.updateCustomKaomoji(saved) }; editingKaomoji = null },
            onDelete = { deleted -> kaomojiScope.launch { kaomojiStore.updateCustomKaomoji(deleted, delete = true) }; editingKaomoji = null })
    }

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

    var pendingBackupSections by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showBackupPicker by remember { mutableStateOf(false) }
    var importResult by remember { mutableStateOf<BackupRepository.ImportResult?>(null) }
    val scope = rememberCoroutineScope()

    val backupExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val sections = pendingBackupSections
        scope.launch {
            runCatching {
                val json = viewModel.buildBackup(sections)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(json.toByteArray(Charsets.UTF_8))
                    }
                }
            }.onSuccess {
                Toast.makeText(context, "备份已导出（${sections.size} 部分）", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "导出失败: ${it.message}", Toast.LENGTH_SHORT).show()
            }
            pendingBackupSections = emptySet()
        }
    }

    val importBackupFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val text = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.bufferedReader().use { it.readText() }
                    }.orEmpty()
                }
                viewModel.restoreBackup(text)
            }.onSuccess { importResult = it }
                .onFailure { Toast.makeText(context, "恢复失败: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }

    val importFontLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = viewModel.importFont(context, uri)
        Toast.makeText(
            context,
            if (ok) "字体已导入" else "字体导入失败，请换一个文件重试",
            Toast.LENGTH_SHORT
        ).show()
    }

    val scrollState = rememberScrollState()
    val sectionPositions = remember { HashMap<String, Int>() }
    val contentOriginY = remember { floatArrayOf(0f) }

    var searching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val showSearch = searching || searchQuery.isNotEmpty()
    val searchFocus = remember { FocusRequester() }
    var searchFocusPending by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val reducedMotion = isReducedMotionEnabled()

    var activeHighlightSection by remember { mutableStateOf<String?>(null) }
    var highlightNonce by remember { mutableIntStateOf(0) }
    val highlightAlpha = remember { Animatable(0f) }
    val highlightPrimaryColor = MaterialTheme.colorScheme.primary
    val highlightShape = MaterialTheme.shapes.medium
    val scrollTopOffsetPx = with(LocalDensity.current) { 12.dp.roundToPx() }

    fun closeSearch() {
        searchFocusPending = false
        focusManager.clearFocus(force = true)
        keyboard?.hide()
        searchQuery = ""
        searching = false
    }

    fun triggerSectionJump(sectionId: String) {
        activeHighlightSection = sectionId
        highlightNonce += 1
    }

    LaunchedEffect(highlightSection) {
        if (!highlightSection.isNullOrBlank()) {
            triggerSectionJump(highlightSection)
        }
    }

    LaunchedEffect(searchQuery) {
        if (!isEffectiveSettingsQuery(searchQuery)) return@LaunchedEffect
        kotlinx.coroutines.delay(180L)
        val firstMatch = matchSettingSectionMetas(searchQuery).firstOrNull() ?: return@LaunchedEffect
        triggerSectionJump(firstMatch.id)
    }

    LaunchedEffect(activeHighlightSection, highlightNonce) {
        val target = activeHighlightSection ?: return@LaunchedEffect
        withFrameNanos {}
        var targetY = sectionPositions[target]
        var attempts = 0
        while (targetY == null && attempts < 15) {
            withFrameNanos {}
            targetY = sectionPositions[target]
            attempts++
        }
        if (targetY != null) {
            val dest = (targetY - scrollTopOffsetPx).coerceAtLeast(0)
            scrollState.scrollTo(dest)
        }
        highlightAlpha.snapTo(0f)
        if (reducedMotion) {
            highlightAlpha.snapTo(1f)
            kotlinx.coroutines.delay(850L)
            highlightAlpha.snapTo(0f)
        } else {
            highlightAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 180)
            )
            kotlinx.coroutines.delay(850L)
            highlightAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 600)
            )
        }
        activeHighlightSection = null
    }

    BackHandler(enabled = showSearch) {
        closeSearch()
    }

    fun Modifier.sectionAnchor(sectionId: String): Modifier = this.onGloballyPositioned { coords ->
        sectionPositions[sectionId] = (coords.positionInRoot().y - contentOriginY[0]).toInt()
    }

    fun Modifier.sectionHighlight(sectionId: String): Modifier = this.settingsSectionHighlight(
        isHighlighted = activeHighlightSection == sectionId,
        alphaProvider = { highlightAlpha.value },
        primaryColor = highlightPrimaryColor,
        shape = highlightShape
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    AnimatedContent(
                        targetState = showSearch,
                        transitionSpec = {
                            fadeIn(tween(if (reducedMotion) 0 else 180)) togetherWith
                                fadeOut(tween(if (reducedMotion) 0 else 130))
                        },
                        label = "settingsSearch"
                    ) { active ->
                        if (active) {
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(searchFocus)
                                    .onGloballyPositioned {
                                        if (searchFocusPending) {
                                            searchFocusPending = false
                                            searchFocus.requestFocus()
                                        }
                                    },
                                singleLine = true,
                                placeholder = { Text("搜索设置（如：饼干、通知、备份）") },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(
                                    onSearch = {
                                        focusManager.clearFocus(force = true)
                                        keyboard?.hide()
                                        matchSettingSectionMetas(searchQuery).firstOrNull()?.let {
                                            triggerSectionJump(it.id)
                                        }
                                    }
                                ),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                )
                            )
                        } else {
                            Text("应用设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (showSearch) {
                            closeSearch()
                        } else {
                            searchFocusPending = true
                            searching = true
                        }
                    }) {
                        Icon(
                            imageVector = if (showSearch) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = if (showSearch) "关闭搜索" else "搜索设置"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DaoTheme.colors.glassTopBar
                )
            )
        },
        modifier = modifier.fillMaxSize(),
        containerColor = secondaryPageBackground(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .navigationBarsPadding()
        ) {
            val effectiveQuery = isEffectiveSettingsQuery(searchQuery)
            val matchedSections = remember(searchQuery) {
                if (!effectiveQuery) emptyList() else matchSettingSectionMetas(searchQuery)
            }
            if (effectiveQuery && matchedSections.size != 1) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(secondaryCardBackground())
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (matchedSections.isEmpty()) {
                        Text(
                            text = "没有找到相关设置",
                            style = settingsDescriptionStyle(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            matchedSections.forEach { section ->
                                val selected = activeHighlightSection == section.id
                                val pillShape = RoundedCornerShape(10.dp)
                                Box(
                                    modifier = Modifier
                                        .clip(pillShape)
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primaryContainer
                                            else secondaryPageBackground(),
                                            pillShape
                                        )
                                        .border(
                                            1.dp,
                                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f),
                                            pillShape
                                        )
                                        .clickable(
                                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            focusManager.clearFocus(force = true)
                                            keyboard?.hide()
                                            triggerSectionJump(section.id)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = section.title,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coords ->
                            contentOriginY[0] = coords.positionInRoot().y
                        }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {

        Text(
            text = "外观与主题",
            style = settingsDescriptionStyle(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.sectionAnchor(SettingsSectionIds.APPEARANCE)
        )
        AppearanceSettingsGroup(
            settings = settings,
            viewModel = viewModel,
            cardModifier = Modifier.sectionHighlight(SettingsSectionIds.APPEARANCE)
        )
        SettingsGroupCard(
            Modifier
                .fillMaxWidth()
                .sectionHighlight(SettingsSectionIds.APPEARANCE)
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingsHeaderIcon(Icons.Outlined.Image, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("自定义顶栏背景", style = settingsItemStyle())
                }
                com.mioo.dao.ui.components.HeaderImageSettingsContent()
            }
        }
        Text("阅读与排版", style = settingsDescriptionStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingsGroupCard(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .sectionAnchor(SettingsSectionIds.KAOMOJI)
                .sectionHighlight(SettingsSectionIds.KAOMOJI)
        ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text("自定义颜文字", style = settingsItemStyle())
                Text("已添加 ${customKaomoji.size} 个", style = settingsDescriptionStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledTonalButton(onClick = { showKaomojiManager = true }) { Text("管理") }
        }
        SettingsGroupDivider()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text("创作快捷输入", style = settingsItemStyle())
                Text("长按编辑器底部快捷项可编辑", style = settingsDescriptionStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { confirmResetQuickInputs = true }) { Text("恢复默认") }
        }
        }
        SettingsGroupDivider()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .sectionAnchor(SettingsSectionIds.READING)
                .sectionHighlight(SettingsSectionIds.READING)
        ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("板块列表预览行数", style = settingsItemStyle())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 8, 10, 15).forEach { lines ->
                    FilterChip(
                        selected = forumPreviewLines == lines,
                        onClick = { viewModel.updateForumPreviewLines(lines) },
                        label = { Text("${lines} 行") },
                        shape = androidx.compose.foundation.shape.CircleShape
                    )
                }
            }
        }
        SettingsGroupDivider()
        // 6. Font Size Configuration (字体大小)
        SettingsGroupCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingsHeaderIcon(Icons.Default.FormatSize, contentDescription = "字体大小")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "字体大小", style = settingsItemStyle())
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "滑动缩放以调整串与回复的正文字号大小。",
                    style = settingsDescriptionStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f)
                )


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
                    text = "预览文字：欢迎来到喵岛！",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }



        SettingsGroupDivider()
        // 行间距
        SettingsGroupCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingsHeaderIcon(Icons.Default.FormatLineSpacing, contentDescription = "行间距")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "行间距", style = settingsItemStyle())
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "滑动调整行与行之间的距离，与字体大小独立生效，全应用即时应用。",
                    style = settingsDescriptionStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f)
                )


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
                    text = "预览文字：欢迎来到喵岛！\n行间距越大，正文越舒展，阅读长串更轻松。",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }



        SettingsGroupDivider()
        // 自定义字体
        SettingsGroupCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingsHeaderIcon(Icons.Default.FontDownload, contentDescription = "自定义字体")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "自定义字体", style = settingsItemStyle())
                }
                Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "导入手机存储中的 ttf、otf 或 ttc 字体文件，选择后应用于全局文字。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(onClick = {
                    importFontLauncher.launch(
                        arrayOf("font/ttf", "font/otf", "font/ttc", "application/octet-stream", "*/*")
                    )
                }) { Text("导入字体文件") }

                val fonts by viewModel.fontsState.collectAsState()
                LaunchedEffect(Unit) { viewModel.refreshFonts(context) }

                Spacer(modifier = Modifier.height(8.dp))
                if (fonts.isEmpty()) {
                    Text(
                        text = "尚未导入字体，点上方按钮从文件管理器/网盘选择字体文件",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                fonts.forEach { name ->
                    val preview = remember(name) {
                        runCatching {
                            androidx.compose.ui.text.font.FontFamily(
                                android.graphics.Typeface.createFromFile(
                                    java.io.File(com.mioo.dao.utils.FontStore.dir(context), name)
                                )
                            )
                        }.getOrNull()
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "预览 Aa喵岛 $name",
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = preview,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(onClick = { viewModel.deleteFont(context, name) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "删除字体",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(modifier = Modifier.padding(start = 62.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(8.dp))

                Text(text = "全局字体", style = MaterialTheme.typography.bodyMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                (listOf("" to "默认（系统）") + fonts.map { it to it }).forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.updateGlobalFont(value) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = settings.chineseFont.ifBlank { settings.englishFont } == value, onClick = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = label, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

            }
        }



        SettingsGroupDivider()
        // 引用自动显示开关
        SettingsGroupCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingsHeaderIcon(Icons.Default.FormatQuote, contentDescription = "引用自动显示")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "引用自动显示",
                        style = settingsItemStyle()
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "开启后，回复中的 >>No.xxx 引用以预览卡片形式显示在正文中，点击可弹窗查看详情；关闭后仅保留引用链接，点击弹出浮层查看所引用的内容",
                    style = settingsDescriptionStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f)
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






        }
        }
        // 骰点高亮（跑团/安科）
        SettingsGroupCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingsHeaderIcon(Icons.Default.Casino, contentDescription = "骰点高亮")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "骰点高亮（跑团/安科）",
                        style = settingsItemStyle()
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "开启后串正文里的骰子出目将显示为统一颜色的高亮胶囊。",
                    style = settingsDescriptionStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.updateDiceBadgesEnabled(!settings.diceBadgesEnabled)
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (settings.diceBadgesEnabled) "已开启" else "已关闭",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    androidx.compose.material3.Switch(
                        checked = settings.diceBadgesEnabled,
                        onCheckedChange = { viewModel.updateDiceBadgesEnabled(it) }
                    )
                }
            }
        }
        Text("发言与饼干", style = settingsDescriptionStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        // 1. Cookie Configuration (饼干设置)
        SettingsGroupCard(
            modifier = Modifier
                .fillMaxWidth()
                .sectionAnchor(SettingsSectionIds.COOKIES)
                .sectionHighlight(SettingsSectionIds.COOKIES),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingsHeaderIcon(Icons.Default.Cookie, contentDescription = "饼干设置")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "发言饼干管理 (User Hash)",
                        style = settingsItemStyle()
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "在此添加并选择发串和回复时使用的发言饼干 (userhash)。",
                    style = settingsDescriptionStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f)
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
                        if (!cookie.startsWith("{")) displayName = cookie.take(6)
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
                            if (sixId.isNotEmpty()) {
                                Text(
                                    text = "ID: $sixId",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 48.dp, bottom = 2.dp)
                                )
                            }
                            OutlinedTextField(
                        colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = secondaryCardBackground(), unfocusedContainerColor = secondaryCardBackground(), focusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), unfocusedBorderColor = Color.Transparent),
                                shape = RoundedCornerShape(16.dp),
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
                }

                HorizontalDivider(modifier = Modifier.padding(start = 62.dp, end = 16.dp, top = 12.dp, bottom = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                // 2. Auth Cookie (鉴权饼干)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingsHeaderIcon(Icons.Default.Cookie, contentDescription = "鉴权饼干")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "高级：鉴权饼干 (Auth)",
                        style = settingsItemStyle()
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
                        shape = RoundedCornerShape(16.dp),
                        value = authDisplayName,
                        onValueChange = {},
                        label = { Text("当前鉴权饼干") },
                        readOnly = true,
                        enabled = false,
                        modifier = Modifier.fillMaxWidth().clickable { authMenuExpanded = true },
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = Color.Transparent,
                            disabledContainerColor = secondaryCardBackground(),
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
        SettingsGroupCard(modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                SettingsHeaderIcon(Icons.Default.Cookie, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("发送前确认饼干", style = settingsItemStyle())
                    Text("发送回复或新串前选择饼干", fontSize = com.mioo.dao.ui.theme.readingTextStyle(12f, 20f).fontSize,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f))
                }
                androidx.compose.material3.Switch(checked = settings.confirmCookieBeforeSend,
                    onCheckedChange = viewModel::updateConfirmCookieBeforeSend)
            }
        }
        // 草稿箱开关
        SettingsGroupCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingsHeaderIcon(Icons.Default.Save, contentDescription = "草稿箱")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "草稿箱",
                        style = settingsItemStyle()
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "关闭后，回复不再自动保存，发串界面不再提供保存草稿功能",
                    style = settingsDescriptionStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f)
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
        // 7. Block Management (屏蔽管理)
        SettingsGroupCard(
            modifier = Modifier
                .fillMaxWidth()
                .sectionAnchor(SettingsSectionIds.BLOCKS)
                .sectionHighlight(SettingsSectionIds.BLOCKS),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingsHeaderIcon(
                        imageVector = Icons.Default.Block,
                        contentDescription = "屏蔽管理"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "屏蔽管理",
                        style = settingsItemStyle()
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "管理已屏蔽的串、发言饼干 ID 与关键字，也可在板块或串内长按卡片直接屏蔽。",
                    style = settingsDescriptionStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f)
                )
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "已屏蔽的串",
                    fontSize = com.mioo.dao.ui.theme.readingTextStyle(13f, 20f).fontSize,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                )
                if (settings.blockedThreads.isEmpty()) {
                    Text(
                        text = "暂无被屏蔽的串",
                        fontSize = com.mioo.dao.ui.theme.readingTextStyle(12.5f, 20f).fontSize,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
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
                        HorizontalDivider(modifier = Modifier.padding(start = 62.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "已屏蔽的发言饼干 ID",
                    fontSize = com.mioo.dao.ui.theme.readingTextStyle(13f, 20f).fontSize,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                )
                if (settings.blockedUsers.isEmpty()) {
                    Text(
                        text = "暂无被屏蔽的 ID",
                        fontSize = com.mioo.dao.ui.theme.readingTextStyle(12.5f, 20f).fontSize,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
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
                        HorizontalDivider(modifier = Modifier.padding(start = 62.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "已屏蔽的关键字",
                    fontSize = com.mioo.dao.ui.theme.readingTextStyle(13f, 20f).fontSize,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                )
                Spacer(modifier = Modifier.height(6.dp))

                var newKeyword by remember { mutableStateOf("") }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        shape = RoundedCornerShape(16.dp),
                        value = newKeyword,
                        onValueChange = { newKeyword = it },
                        placeholder = { Text("输入要屏蔽的关键字...", style = MaterialTheme.typography.bodyMedium) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = secondaryCardBackground(),
                            unfocusedContainerColor = secondaryCardBackground()
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
                        HorizontalDivider(modifier = Modifier.padding(start = 62.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                    }
                }
            }
        }
        Text("收藏与分享", style = settingsDescriptionStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        // 3. Multiple Feed Folders (多云端收藏夹)
        SettingsGroupCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingsHeaderIcon(androidx.compose.material.icons.Icons.Default.Save, contentDescription = "云端收藏夹")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "云端收藏夹",
                        style = settingsItemStyle()
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Box(modifier = Modifier.size(48.dp).clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { editingFolderUuid = null; newFolderName = ""; newFolderUuid = ""; showAddFolderDialog = true }, contentAlignment = Alignment.Center) {
                        Icon(androidx.compose.material.icons.Icons.Default.Add, contentDescription = "添加收藏夹")
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "添加额外的订阅 UUID（云端收藏夹），可在收藏界面分页独立浏览。",
                    style = settingsDescriptionStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f)
                )
                Spacer(modifier = Modifier.height(8.dp))

                settings.feedFolders.forEach { folder ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {
                                editingFolderUuid = folder.uuid; newFolderName = folder.name; newFolderUuid = folder.uuid; showAddFolderDialog = true
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = folder.name, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            Text(text = folder.uuid, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box(modifier = Modifier.size(48.dp).clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { deletingFolder = folder }, contentAlignment = Alignment.Center) {
                            Icon(androidx.compose.material.icons.Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(start = 62.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                }
            }
        }
        // 5. Subscription notifications
        val subscriptionQuietHoursConfig by viewModel.subscriptionQuietHoursConfig.collectAsState()
        val subscriptionCheckStatus by viewModel.subscriptionCheckStatus.collectAsState()
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

        SettingsGroupCard(
            modifier = Modifier
                .fillMaxWidth()
                .sectionAnchor(SettingsSectionIds.SUBSCRIPTION)
                .sectionHighlight(SettingsSectionIds.SUBSCRIPTION),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingsHeaderIcon(Icons.Default.Info, contentDescription = "订阅通知")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "X岛订阅新回复通知",
                        style = settingsItemStyle()
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "后台定期向 X 岛官方接口查询云端订阅（订阅 UUID）与本地收藏串是否有新回复，有更新时发送系统通知。\n" +
                        "说明：岛方未开放实时推送通道，第三方客户端均通过本地定时轮询获取订阅更新。",
                    style = settingsDescriptionStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) {
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
                    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
                    var intervalInput by remember(settings.notificationIntervalMinutes) {
                        mutableStateOf(settings.notificationIntervalMinutes.toString())
                    }
                    val intervalValidation = remember(intervalInput) {
                        com.mioo.dao.notification.SubscriptionPollPolicy.validateIntervalMinutes(intervalInput)
                    }
                    val applyInterval = {
                        val parsed = intervalValidation.minutes
                        if (parsed == null) {
                            Toast.makeText(
                                context,
                                intervalValidation.error ?: "请输入 15–180 之间的整数",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            viewModel.updateNotificationIntervalMinutes(parsed)
                            intervalInput = parsed.toString()
                            focusManager.clearFocus()
                            Toast.makeText(context, "已设为每 $parsed 分钟检查", Toast.LENGTH_SHORT).show()
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = secondaryPageBackground()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "基准检查间隔",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "冷门串自动降频 · 15–180 分钟",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                                    )
                                }
                                MiooCompactNumberField(
                                    value = intervalInput,
                                    onValueChange = { raw ->
                                        intervalInput = raw.filter { it in '0'..'9' }.take(3)
                                    },
                                    isError = intervalValidation.error != null,
                                    suffix = "分钟",
                                    imeAction = ImeAction.Done,
                                    onImeAction = applyInterval,
                                    modifier = Modifier.width(92.dp)
                                )
                                MiooTonalApplyChip(
                                    text = "应用",
                                    enabled = intervalValidation.minutes != null &&
                                        intervalValidation.minutes != settings.notificationIntervalMinutes,
                                    onClick = applyInterval
                                )
                            }
                            if (intervalValidation.error != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = intervalValidation.error,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Slider(
                                value = settings.notificationIntervalMinutes.coerceIn(15, 180).toFloat(),
                                onValueChange = {
                                    val rounded = it.toInt().coerceIn(15, 180)
                                    intervalInput = rounded.toString()
                                    viewModel.updateNotificationIntervalMinutes(rounded)
                                },
                                valueRange = 15f..180f,
                                steps = 10
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) {
                                viewModel.updateSubscriptionQuietHours(!subscriptionQuietHoursConfig.enabled)
                            }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "免打扰",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "开启后在设定时段内暂停后台检查",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                            )
                        }
                        androidx.compose.material3.Switch(
                            checked = subscriptionQuietHoursConfig.enabled,
                            onCheckedChange = { viewModel.updateSubscriptionQuietHours(it) }
                        )
                    }
                    if (subscriptionQuietHoursConfig.enabled) {
                        var startHourInput by remember(subscriptionQuietHoursConfig) {
                            mutableStateOf("%02d".format(subscriptionQuietHoursConfig.startHour))
                        }
                        var startMinuteInput by remember(subscriptionQuietHoursConfig) {
                            mutableStateOf("%02d".format(subscriptionQuietHoursConfig.startMinute))
                        }
                        var endHourInput by remember(subscriptionQuietHoursConfig) {
                            mutableStateOf("%02d".format(subscriptionQuietHoursConfig.endHour))
                        }
                        var endMinuteInput by remember(subscriptionQuietHoursConfig) {
                            mutableStateOf("%02d".format(subscriptionQuietHoursConfig.endMinute))
                        }
                        val quietValidation = remember(
                            startHourInput,
                            startMinuteInput,
                            endHourInput,
                            endMinuteInput
                        ) {
                            com.mioo.dao.notification.SubscriptionPollPolicy.validateQuietHoursInput(
                                startHourRaw = startHourInput,
                                startMinuteRaw = startMinuteInput,
                                endHourRaw = endHourInput,
                                endMinuteRaw = endMinuteInput
                            )
                        }
                        val isQuietDirty = quietValidation.error == null && (
                            quietValidation.startHour != subscriptionQuietHoursConfig.startHour ||
                                quietValidation.startMinute != subscriptionQuietHoursConfig.startMinute ||
                                quietValidation.endHour != subscriptionQuietHoursConfig.endHour ||
                                quietValidation.endMinute != subscriptionQuietHoursConfig.endMinute
                            )
                        val saveQuietHours = {
                            val sh = quietValidation.startHour
                            val sm = quietValidation.startMinute
                            val eh = quietValidation.endHour
                            val em = quietValidation.endMinute
                            if (sh == null || sm == null || eh == null || em == null) {
                                Toast.makeText(
                                    context,
                                    quietValidation.error ?: "请输入有效的 24 小时制时间",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                viewModel.updateSubscriptionQuietHoursRange(sh, eh, sm, em)
                                startHourInput = "%02d".format(sh)
                                startMinuteInput = "%02d".format(sm)
                                endHourInput = "%02d".format(eh)
                                endMinuteInput = "%02d".format(em)
                                focusManager.clearFocus()
                                Toast.makeText(
                                    context,
                                    "免打扰时段已设为 ${"%02d:%02d".format(sh, sm)}–${"%02d:%02d".format(eh, em)}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = secondaryPageBackground()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    MiooTimeInputGroup(
                                        label = "开始时间",
                                        hourValue = startHourInput,
                                        minuteValue = startMinuteInput,
                                        onHourChange = { startHourInput = it.filter { ch -> ch in '0'..'9' }.take(2) },
                                        onMinuteChange = { startMinuteInput = it.filter { ch -> ch in '0'..'9' }.take(2) },
                                        isHourError = com.mioo.dao.notification.QuietTimeField.START_HOUR in quietValidation.invalidFields ||
                                            com.mioo.dao.notification.QuietTimeField.RANGE in quietValidation.invalidFields,
                                        isMinuteError = com.mioo.dao.notification.QuietTimeField.START_MINUTE in quietValidation.invalidFields ||
                                            com.mioo.dao.notification.QuietTimeField.RANGE in quietValidation.invalidFields,
                                        onDone = saveQuietHours,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "至",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 16.dp)
                                    )
                                    MiooTimeInputGroup(
                                        label = "结束时间",
                                        hourValue = endHourInput,
                                        minuteValue = endMinuteInput,
                                        onHourChange = { endHourInput = it.filter { ch -> ch in '0'..'9' }.take(2) },
                                        onMinuteChange = { endMinuteInput = it.filter { ch -> ch in '0'..'9' }.take(2) },
                                        isHourError = com.mioo.dao.notification.QuietTimeField.END_HOUR in quietValidation.invalidFields ||
                                            com.mioo.dao.notification.QuietTimeField.RANGE in quietValidation.invalidFields,
                                        isMinuteError = com.mioo.dao.notification.QuietTimeField.END_MINUTE in quietValidation.invalidFields ||
                                            com.mioo.dao.notification.QuietTimeField.RANGE in quietValidation.invalidFields,
                                        onDone = saveQuietHours,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val summaryText = if (quietValidation.error != null) {
                                        quietValidation.error
                                    } else {
                                        val sh = quietValidation.startHour!!
                                        val sm = quietValidation.startMinute!!
                                        val eh = quietValidation.endHour!!
                                        val em = quietValidation.endMinute!!
                                        val startTotal = sh * 60 + sm
                                        val endTotal = eh * 60 + em
                                        if (startTotal > endTotal) {
                                            "跨夜时段 · ${"%02d:%02d".format(sh, sm)} 至次日 ${"%02d:%02d".format(eh, em)}"
                                        } else {
                                            "当日时段 · ${"%02d:%02d".format(sh, sm)} 至 ${"%02d:%02d".format(eh, em)}"
                                        }
                                    }
                                    Text(
                                        text = summaryText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (quietValidation.error != null) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                    MiooTonalApplyChip(
                                        text = "保存",
                                        enabled = isQuietDirty,
                                        onClick = saveQuietHours
                                    )
                                }
                            }
                        }
                    }
                    if (subscriptionCheckStatus.lastCheckAt > 0L) {
                        val timeText = remember(subscriptionCheckStatus.lastCheckAt) {
                            java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                                .format(java.util.Date(subscriptionCheckStatus.lastCheckAt))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "上次检查 $timeText · ${subscriptionCheckStatus.summary}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (viewModel.runSubscriptionCheckNow()) {
                                    Toast.makeText(context, "已开始检查订阅更新", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "检查过于频繁，请稍后再试", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Text("立即检查一次")
                        }
                        TextButton(
                            onClick = {
                                val appSettingsIntent = android.content.Intent(
                                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    android.net.Uri.fromParts("package", context.packageName, null)
                                )
                                runCatching { context.startActivity(appSettingsIntent) }
                            }
                        ) {
                            Text("前往应用设置")
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "提示：小米等国产系统可能会限制后台定时检查；如收不到通知，请在系统设置中允许喵岛自启动并将省电策略设为「无限制」。",
                        style = settingsDescriptionStyle(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f)
                    )
                }
            }
        }
        SettingsGroupCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("分享卡片显示喵岛水印", style = settingsItemStyle())
                    Text("在分享图底部附加喵岛标识与原串二维码",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                androidx.compose.material3.Switch(checked = settings.showShareCardBranding,
                    onCheckedChange = viewModel::setShowShareCardBranding)
            }
        }
        SettingsGroupCard(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text("创作模式", style = settingsItemStyle())
                    Text(
                        "收起底部常驻回复栏，改用悬浮按钮与全屏编辑卡片",
                        style = settingsDescriptionStyle(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                androidx.compose.material3.Switch(
                    checked = settings.enableCreatorMode,
                    onCheckedChange = viewModel::updateCreatorMode
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .sectionAnchor(SettingsSectionIds.GESTURES)
                .sectionHighlight(SettingsSectionIds.GESTURES),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        SettingsGroupCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("预测性返回", style = settingsItemStyle())
                    Text("侧滑返回时跟手预览上一级页面", style = settingsDescriptionStyle(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                androidx.compose.material3.Switch(
                    checked = predictiveBackEnabled,
                    onCheckedChange = viewModel::setPredictiveBackEnabled
                )
            }
        }
        SettingsGroupCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("滑动防误触", style = settingsItemStyle())
                    Text("提高起滑判定阈值，减少点击或斜滑时的误滑动",
                        style = settingsDescriptionStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                androidx.compose.material3.Switch(
                    checked = scrollTouchGuardEnabled,
                    onCheckedChange = viewModel::setScrollTouchGuardEnabled
                )
            }
        }
        }
        SettingsGroupCard(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(0.dp)) {
            Column(Modifier.fillMaxWidth().clickable { showUpdateChannelDialog = true }.padding(16.dp)) {
                Text("更新通道", style = settingsItemStyle())
                Spacer(Modifier.height(6.dp))
                Text(if (updateChannel == com.mioo.dao.utils.UpdateChannel.STABLE) "正式版" else "测试版",
                    style = settingsDescriptionStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (showUpdateChannelDialog) {
            AlertDialog(
                onDismissRequest = { showUpdateChannelDialog = false },
                shape = MaterialTheme.shapes.large, tonalElevation = 0.dp,
                title = { Text("更新通道", fontSize = com.mioo.dao.ui.theme.readingTextStyle(17.5f, 20f).fontSize, fontWeight = FontWeight.SemiBold) },
                text = {
                    Column {
                        com.mioo.dao.utils.UpdateChannel.values().forEach { channel ->
                            Row(Modifier.fillMaxWidth().clickable {
                                viewModel.setUpdateChannel(channel)
                            }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = updateChannel == channel,
                                    onClick = { viewModel.setUpdateChannel(channel) })
                                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                    Text(if (channel == com.mioo.dao.utils.UpdateChannel.STABLE) "正式版" else "测试版")
                                    Text(if (channel == com.mioo.dao.utils.UpdateChannel.STABLE) "稳定版本，适合日常使用"
                                        else "抢先体验新功能，可能存在未知问题",
                                        fontSize = com.mioo.dao.ui.theme.readingTextStyle(12f, 20f).fontSize, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        if (updateChannel == com.mioo.dao.utils.UpdateChannel.BETA) {
                            Text("切回正式版后，将在下个更高版本正式版发布时覆盖升级",
                                fontSize = com.mioo.dao.ui.theme.readingTextStyle(12f, 20f).fontSize, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showUpdateChannelDialog = false }) { Text("完成") } }
            )
        }
        Text("网络、备份与缓存", style = settingsDescriptionStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingsGroupCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { openAppDefaultLinksSettings(context) }
                    .padding(16.dp)
            ) {
                Text("默认打开", style = settingsItemStyle())
                Spacer(Modifier.height(6.dp))
                Text(
                    "在系统设置中允许喵岛打开 X 岛链接",
                    style = settingsDescriptionStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        SettingsGroupCard(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(0.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("第三方服务", style = settingsItemStyle())
                    Text(
                        "允许检查更新、外部搜索、以图搜源及网络兼容的第三方 DNS",
                        style = settingsDescriptionStyle(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                androidx.compose.material3.Switch(
                    checked = allowThirdParty,
                    enabled = consent?.hasShownComplianceDialog == true,
                    onCheckedChange = viewModel::updateThirdPartyServices
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .sectionAnchor(SettingsSectionIds.NETWORK)
                .sectionHighlight(SettingsSectionIds.NETWORK),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        SettingsGroupCard(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(0.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("网络兼容模式", style = settingsItemStyle())
                Spacer(Modifier.height(8.dp))
                Text("发串、回复或加载串内容经常超时、连接重置时可尝试开启。使用加密解析改善连接，开启后对新连接生效。",
                    style = settingsDescriptionStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f))
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (allowThirdParty && settings.networkCompatibilityEnabled) "已开启" else "已关闭",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    androidx.compose.material3.Switch(
                        checked = allowThirdParty && settings.networkCompatibilityEnabled,
                        enabled = allowThirdParty,
                        onCheckedChange = { viewModel.updateNetworkCompatibility(it) }
                    )
                }
            }
        }
        SettingsGroupCard(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(0.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = onOpenNetworkDiagnostics
                    )
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SettingsHeaderIcon(Icons.Default.NetworkCheck, contentDescription = "X 岛通信诊断")
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("X 岛通信诊断", style = settingsItemStyle())
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "检测 API、图床、网页域名连通性、阻塞环节与延迟",
                        style = settingsDescriptionStyle(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
        }
        // 省流量模式开关
        SettingsGroupCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingsHeaderIcon(Icons.Default.CloudOff, contentDescription = "省流量模式")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "省流量模式",
                        style = settingsItemStyle()
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "开启后串内与列表附图不再自动加载，仅显示占位，点击单张加载；点开大图不受影响",
                    style = settingsDescriptionStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.updateDataSaverEnabled(!settings.dataSaverEnabled)
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (settings.dataSaverEnabled) "已开启" else "已关闭",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    androidx.compose.material3.Switch(
                        checked = settings.dataSaverEnabled,
                        onCheckedChange = { viewModel.updateDataSaverEnabled(it) }
                    )
                }
            }
        }
        // 备份与恢复
        SettingsGroupCard(
            modifier = Modifier
                .fillMaxWidth()
                .sectionAnchor(SettingsSectionIds.BACKUP)
                .sectionHighlight(SettingsSectionIds.BACKUP),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingsHeaderIcon(Icons.Default.Backup, contentDescription = "备份与恢复")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "备份与恢复",
                        style = settingsItemStyle()
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "自选内容导出为一个 JSON 文件，可在此设备或另一台设备恢复；旧版纯饼干备份文件也可恢复",
                    style = settingsDescriptionStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        pendingBackupSections = BackupRepository.ALL_SECTIONS.toSet()
                        showBackupPicker = true
                    }) { Text("备份") }
                    OutlinedButton(onClick = {
                        importBackupFileLauncher.launch(
                            arrayOf("application/json", "text/plain", "application/octet-stream")
                        )
                    }) { Text("恢复") }
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .sectionAnchor(SettingsSectionIds.OFFLINE),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OfflineSettingsGroup(
                settings = settings,
                viewModel = viewModel,
                cardModifier = Modifier.sectionHighlight(SettingsSectionIds.OFFLINE)
            )
        }

        Spacer(modifier = Modifier.height(100.dp))
                }
            }
        }
    }

    if (showAddFolderDialog) {
        FolderInputDialog(
            values = FolderInputValues(editingFolderUuid, newFolderName, newFolderUuid, settings.feedFolders),
            onNameChange = { newFolderName = it },
            onUuidChange = { newFolderUuid = it },
            onDismiss = { showAddFolderDialog = false },
            onAdd = viewModel::addFeedFolder,
            onUpdate = viewModel::updateFeedFolder
        )
    }

    deletingFolder?.let { folder ->
        var dialogVisible by remember(folder.uuid) { mutableStateOf(false) }
        LaunchedEffect(folder.uuid) { dialogVisible = true }
        val dialogScale by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (dialogVisible) 1f else 0.96f,
            animationSpec = androidx.compose.animation.core.tween(220), label = "deleteFolderDialog"
        )
        fun closeDeleteDialog() {
            dialogVisible = false
            scope.launch { kotlinx.coroutines.delay(220); deletingFolder = null }
        }
        androidx.compose.material3.AlertDialog(
            modifier = Modifier.graphicsLayer { scaleX = dialogScale; scaleY = dialogScale; alpha = ((dialogScale - 0.96f) / 0.04f).coerceIn(0f, 1f) },
            onDismissRequest = { closeDeleteDialog() },
            title = { Text("删除云端收藏夹？") },
            text = { Text("确定删除“${folder.name}”吗？") },
            confirmButton = {
                FolderActionButton(onClick = { viewModel.removeFeedFolder(folder.uuid); closeDeleteDialog() }) { Text("删除") }
            },
            dismissButton = {
                FolderActionButton(onClick = { closeDeleteDialog() }) { Text("取消") }
            }
        )
    }
    if (showBackupPicker) {
        BackupPickerDialog(
            pendingBackupSections = pendingBackupSections,
            onSectionsChange = { pendingBackupSections = it },
            onDismiss = { showBackupPicker = false },
            onCancel = { showBackupPicker = false; pendingBackupSections = emptySet() },
            onExport = {
                val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date())
                backupExportLauncher.launch("mioo-backup-$stamp.json")
            }
        )
    }

    importResult?.let { BackupResultDialog(it) { importResult = null } }

}

internal fun toggleSection(current: Set<String>, key: String): Set<String> =
    if (key in current) current - key else current + key

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
/** 饼干的岛方 ID：仅认 JSON 里的 userhash 字段，原样返回（X岛为 7 位随机 ID，不截断）。 */
private fun cookieSixCharId(cookie: String): String {
    if (cookie.startsWith("{")) {
        runCatching {
            val json = org.json.JSONObject(cookie)
            val userhash = json.optString("userhash", "")
            if (userhash.isNotBlank()) return userhash
        }
    }
    return ""
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


@Composable
internal fun secondaryPageBackground(): Color {
    val bg = MaterialTheme.colorScheme.background
    return when { bg == Color.Black -> Color.Black; bg.luminance() < 0.5f -> Color(0xFF0E1015); else -> Color(0xFFF0F1F4) }
}

@Composable
internal fun secondaryCardBackground(): Color {
    val bg = MaterialTheme.colorScheme.background
    return when { bg == Color.Black -> Color(0xFF121318); bg.luminance() < 0.5f -> Color(0xFF16181E); else -> Color.White }
}

@Composable
internal fun SettingsGroupCard(modifier: Modifier = Modifier, elevation: androidx.compose.material3.CardElevation = CardDefaults.cardElevation(0.dp), content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = modifier, shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = secondaryCardBackground(), contentColor = MaterialTheme.colorScheme.onSurface), elevation = elevation, content = content)
}

@Composable
internal fun SettingsHeaderIcon(imageVector: ImageVector, contentDescription: String?) {
    Box(Modifier.size(36.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
        Icon(imageVector, contentDescription, modifier = Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
internal fun SettingsGroupDivider() {
    HorizontalDivider(modifier = Modifier.padding(start = 62.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
}

@Composable
internal fun FolderActionButton(onClick: () -> Unit, tonal: Boolean = false, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.clip(MaterialTheme.shapes.medium)
            .background(if (tonal) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(color = if (tonal) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant), content = content)
    }
}

@Composable
private fun MiooCompactNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: (() -> Unit)? = null,
    containerColor: Color = secondaryCardBackground()
) {
    var focused by remember { mutableStateOf(false) }
    val borderColor = when {
        isError -> MaterialTheme.colorScheme.error
        focused -> MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
    }
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .height(36.dp)
            .clip(shape)
            .background(containerColor, shape)
            .border(1.dp, borderColor, shape)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = "tnum",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = imeAction
            ),
            keyboardActions = KeyboardActions(
                onDone = { onImeAction?.invoke() },
                onNext = { onImeAction?.invoke() }
            ),
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { focused = it.isFocused }
        )
        if (!suffix.isNullOrEmpty()) {
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = suffix,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
            )
        }
    }
}

@Composable
private fun MiooTimeInputGroup(
    label: String,
    hourValue: String,
    minuteValue: String,
    onHourChange: (String) -> Unit,
    onMinuteChange: (String) -> Unit,
    isHourError: Boolean,
    isMinuteError: Boolean,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            MiooCompactNumberField(
                value = hourValue,
                onValueChange = onHourChange,
                isError = isHourError,
                imeAction = ImeAction.Next,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = ":",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            MiooCompactNumberField(
                value = minuteValue,
                onValueChange = onMinuteChange,
                isError = isMinuteError,
                imeAction = ImeAction.Done,
                onImeAction = onDone,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MiooTonalApplyChip(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(10.dp)
    val bgColor = if (enabled) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val textColor = if (enabled) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    }
    Box(
        modifier = Modifier
            .height(36.dp)
            .clip(shape)
            .background(bgColor, shape)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = textColor
        )
    }
}

private fun Modifier.settingsSectionHighlight(
    isHighlighted: Boolean,
    alphaProvider: () -> Float,
    primaryColor: Color,
    shape: androidx.compose.ui.graphics.Shape
): Modifier {
    if (!isHighlighted) return this
    return this
        .clip(shape)
        .drawWithContent {
            drawContent()
            val a = alphaProvider().coerceIn(0f, 1f)
            if (a > 0f) {
                val strokeWidthPx = 1.5.dp.toPx()
                val halfStroke = strokeWidthPx / 2f
                val radius = 16.dp.toPx()
                val corner = CornerRadius(radius, radius)
                drawRoundRect(
                    color = primaryColor.copy(alpha = 0.08f * a),
                    cornerRadius = corner
                )
                val innerRadius = (radius - halfStroke).coerceAtLeast(0f)
                drawRoundRect(
                    color = primaryColor.copy(alpha = 0.55f * a),
                    topLeft = androidx.compose.ui.geometry.Offset(halfStroke, halfStroke),
                    size = androidx.compose.ui.geometry.Size(
                        width = (size.width - strokeWidthPx).coerceAtLeast(0f),
                        height = (size.height - strokeWidthPx).coerceAtLeast(0f)
                    ),
                    cornerRadius = CornerRadius(innerRadius, innerRadius),
                    style = Stroke(width = strokeWidthPx)
                )
            }
        }
}
