package com.mioo.dao.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mioo.dao.ui.theme.DaoTheme
import com.mioo.dao.ui.theme.isReducedMotionEnabled
import com.mioo.dao.ui.theme.readingTextStyle
import com.mioo.dao.ui.theme.settingsDescriptionStyle

sealed interface GuideAction {
    data object None : GuideAction
    data class OpenSettings(val sectionId: String) : GuideAction
    data object OpenNetworkDiagnostics : GuideAction
    data object OpenDrafts : GuideAction
    data object OpenSearch : GuideAction
}

data class GuideItem(
    val id: String,
    val title: String,
    val summary: String,
    val triggerBadge: String,
    val keywords: List<String> = emptyList(),
    val action: GuideAction = GuideAction.None,
    val actionLabel: String? = null
)

data class GuideSection(
    val id: String,
    val title: String,
    val items: List<GuideItem>
)

object FeatureGuideCatalog {
    val sections: List<GuideSection> = listOf(
        GuideSection(
            id = "whats_new",
            title = "最近新增 · 1.1.8",
            items = listOf(
                GuideItem(
                    id = "new_subscription_dnd",
                    title = "订阅新回复提醒与自定义免打扰",
                    summary = "后台检查订阅串更新，可设夜间免打扰时段",
                    triggerBadge = "设置",
                    keywords = listOf("1.1.8", "通知", "提醒", "轮询", "静音", "dnd"),
                    action = GuideAction.OpenSettings("subscription"),
                    actionLabel = "前往设置"
                ),
                GuideItem(
                    id = "new_favorites_board_offline",
                    title = "收藏夹板块标签与离线统一",
                    summary = "收藏卡片直显所属板块，离线与云端同视图",
                    triggerBadge = "点击",
                    keywords = listOf("1.1.8", "收藏", "板块", "离线", "云端"),
                    action = GuideAction.None
                ),
                GuideItem(
                    id = "new_history_favorites_swipe",
                    title = "历史与收藏更流畅的横滑切换",
                    summary = "左右轻滑顺畅切换分组，减少误触",
                    triggerBadge = "横滑",
                    keywords = listOf("1.1.8", "历史", "收藏", "手势", "切页"),
                    action = GuideAction.None
                )
            )
        ),
        GuideSection(
            id = "reading",
            title = "读串与互动",
            items = listOf(
                GuideItem(
                    id = "read_quote_copy",
                    title = "点串号引用、长按串号复制",
                    summary = "点串号快速引用回复，长按直接复制串号",
                    triggerBadge = "点击",
                    keywords = listOf("引用", "复制", "串号", "长按", "楼层"),
                    action = GuideAction.None
                ),
                GuideItem(
                    id = "read_block_card",
                    title = "长按卡片屏蔽",
                    summary = "长按串或回复卡片，屏蔽串号、饼干或词条",
                    triggerBadge = "长按",
                    keywords = listOf("屏蔽", "拉黑", "过滤", "饼干", "关键词"),
                    action = GuideAction.OpenSettings("blocks"),
                    actionLabel = "屏蔽规则"
                ),
                GuideItem(
                    id = "read_page_jump",
                    title = "串内页码跳转",
                    summary = "点页码指示直跳任意页，追长串更省力",
                    triggerBadge = "点击",
                    keywords = listOf("跳页", "页码", "翻页", "长串"),
                    action = GuideAction.None
                ),
                GuideItem(
                    id = "read_po_and_search",
                    title = "只看 PO 与串内搜索",
                    summary = "顶栏一键只看 PO，或在当前串内检索回复",
                    triggerBadge = "点击",
                    keywords = listOf("po", "只看楼主", "串内搜索", "查找"),
                    action = GuideAction.None
                ),
                GuideItem(
                    id = "read_multi_share",
                    title = "多楼层分享卡片",
                    summary = "选中多条回复生成长图卡片，方便整理分享",
                    triggerBadge = "长按",
                    keywords = listOf("分享", "截图", "长图", "多选", "楼层"),
                    action = GuideAction.None
                )
            )
        ),
        GuideSection(
            id = "posting",
            title = "发言与创作",
            items = listOf(
                GuideItem(
                    id = "post_creator_mode",
                    title = "创作者模式展开编辑器",
                    summary = "展开全屏编辑区，写长文排版更专注",
                    triggerBadge = "点击",
                    keywords = listOf("创作者", "全屏", "编辑器", "长文", "发串"),
                    action = GuideAction.None
                ),
                GuideItem(
                    id = "post_kaomoji_reorder",
                    title = "颜文字长按拖动排序",
                    summary = "长按颜文字拖动调整顺序，常用放前面",
                    triggerBadge = "长按",
                    keywords = listOf("颜文字", "排序", "拖动", "自定义"),
                    action = GuideAction.OpenSettings("kaomoji"),
                    actionLabel = "颜文字"
                ),
                GuideItem(
                    id = "post_quick_input",
                    title = "快捷输入长按编辑",
                    summary = "长按快捷输入项，可修改或增删常用短语",
                    triggerBadge = "长按",
                    keywords = listOf("快捷输入", "短语", "编辑", "模板"),
                    action = GuideAction.None
                ),
                GuideItem(
                    id = "post_auto_drafts",
                    title = "离开自动存多份草稿",
                    summary = "退出编辑自动保存，支持多份草稿随时续写",
                    triggerBadge = "点击",
                    keywords = listOf("草稿", "自动保存", "恢复", "草稿箱"),
                    action = GuideAction.OpenDrafts,
                    actionLabel = "草稿箱"
                ),
                GuideItem(
                    id = "post_cookie_confirm",
                    title = "发送前确认饼干",
                    summary = "发串前核对当前饼干，避免误切或串号暴露",
                    triggerBadge = "设置",
                    keywords = listOf("饼干", "确认", "切换", "隐私", "cookie"),
                    action = GuideAction.OpenSettings("cookies"),
                    actionLabel = "饼干管理"
                ),
                GuideItem(
                    id = "post_doodle_dice_spoiler",
                    title = "涂鸦板与掷骰/剧透标签",
                    summary = "内置手绘涂鸦板，支持一键插入掷骰与剧透",
                    triggerBadge = "点击",
                    keywords = listOf("涂鸦", "画板", "掷骰", "roll", "剧透", "隐白"),
                    action = GuideAction.None
                )
            )
        ),
        GuideSection(
            id = "collections",
            title = "收藏与追串",
            items = listOf(
                GuideItem(
                    id = "collect_cloud_offline",
                    title = "云端收藏与离线缓存",
                    summary = "同步云端收藏夹，并缓存已读内容供离线看",
                    triggerBadge = "设置",
                    keywords = listOf("收藏", "云端", "离线", "缓存", "同步"),
                    action = GuideAction.OpenSettings("offline"),
                    actionLabel = "离线缓存"
                ),
                GuideItem(
                    id = "collect_subscription_dnd",
                    title = "订阅新回复提醒与免打扰",
                    summary = "定时轮询订阅串新回复，免打扰时段静音",
                    triggerBadge = "设置",
                    keywords = listOf("订阅", "提醒", "通知", "免打扰", "追串"),
                    action = GuideAction.OpenSettings("subscription"),
                    actionLabel = "提醒设置"
                ),
                GuideItem(
                    id = "collect_swipe_delete",
                    title = "收藏/历史左右滑切页与长按删除",
                    summary = "左右滑切换分类页，长按单条即可快捷删除",
                    triggerBadge = "横滑",
                    keywords = listOf("收藏", "历史", "横滑", "长按", "删除"),
                    action = GuideAction.None
                ),
                GuideItem(
                    id = "collect_clipboard_search",
                    title = "剪贴板串号识别与搜索直达",
                    summary = "自动识别复制的串号或链接，搜索页一键直达",
                    triggerBadge = "点击",
                    keywords = listOf("剪贴板", "串号", "直达", "搜索", "链接"),
                    action = GuideAction.OpenSearch,
                    actionLabel = "去搜索"
                )
            )
        ),
        GuideSection(
            id = "images",
            title = "图片浏览",
            items = listOf(
                GuideItem(
                    id = "image_cross_page",
                    title = "大图跨楼层/跨页左右连续翻看",
                    summary = "大图模式左右连翻，自动衔接本串跨页图片",
                    triggerBadge = "横滑",
                    keywords = listOf("大图", "看图", "跨页", "跨楼层", "连翻"),
                    action = GuideAction.None
                ),
                GuideItem(
                    id = "image_zoom_long",
                    title = "双击缩放与长图铺满",
                    summary = "双击放大或还原，长图自动适配宽度浏览",
                    triggerBadge = "点击",
                    keywords = listOf("双击", "缩放", "长图", "铺满", "手势"),
                    action = GuideAction.None
                )
            )
        ),
        GuideSection(
            id = "personalization",
            title = "个性化与排障",
            items = listOf(
                GuideItem(
                    id = "pref_predictive_back",
                    title = "预测返回与防误触",
                    summary = "支持预测性返回手势与滑动防误触保护",
                    triggerBadge = "设置",
                    keywords = listOf("预测返回", "返回", "防误触", "手势"),
                    action = GuideAction.OpenSettings("gestures"),
                    actionLabel = "手势设置"
                ),
                GuideItem(
                    id = "pref_appearance",
                    title = "主题/字号/自定义头图",
                    summary = "切换深浅与纯黑主题，自定字号与板块头图",
                    triggerBadge = "设置",
                    keywords = listOf("主题", "深色", "amoled", "字号", "头图", "毛玻璃"),
                    action = GuideAction.OpenSettings("appearance"),
                    actionLabel = "外观设置"
                ),
                GuideItem(
                    id = "pref_backup",
                    title = "备份与恢复",
                    summary = "导出或恢复饼干、屏蔽规则与偏好配置",
                    triggerBadge = "设置",
                    keywords = listOf("备份", "恢复", "导出", "导入", "迁移"),
                    action = GuideAction.OpenSettings("backup"),
                    actionLabel = "备份恢复"
                ),
                GuideItem(
                    id = "pref_network_diagnostics",
                    title = "网络诊断与兼容模式",
                    summary = "检测 X 岛连通链路，按需切换网络兼容模式",
                    triggerBadge = "设置",
                    keywords = listOf("网络", "诊断", "排障", "兼容模式", "连接"),
                    action = GuideAction.OpenNetworkDiagnostics,
                    actionLabel = "网络诊断"
                )
            )
        )
    )

    fun filterGuideSections(
        query: String,
        sections: List<GuideSection> = this.sections
    ): List<GuideSection> = com.mioo.dao.ui.screens.settings.filterGuideSections(query, sections)
}

fun filterGuideSections(
    query: String,
    sections: List<GuideSection> = FeatureGuideCatalog.sections
): List<GuideSection> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return sections
    val tokens = trimmed.split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return sections
    return sections.mapNotNull { section ->
        val matchedItems = section.items.filter { item ->
            tokens.all { token ->
                section.title.contains(token, ignoreCase = true) ||
                    item.title.contains(token, ignoreCase = true) ||
                    item.summary.contains(token, ignoreCase = true) ||
                    item.triggerBadge.contains(token, ignoreCase = true) ||
                    item.keywords.any { it.contains(token, ignoreCase = true) }
            }
        }
        if (matchedItems.isEmpty()) null else section.copy(items = matchedItems)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeatureGuideScreen(
    onBackClick: () -> Unit,
    onNavigateToSettingsSection: (String?) -> Unit,
    onNavigateToNetworkDiagnostics: () -> Unit,
    onNavigateToDrafts: () -> Unit,
    onNavigateToSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reducedMotion = isReducedMotionEnabled()
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    val showSearch = searching || query.isNotEmpty()
    val searchFocus = remember { FocusRequester() }
    var searchFocusPending by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    fun closeSearch() {
        searchFocusPending = false
        focusManager.clearFocus(force = true)
        keyboard?.hide()
        query = ""
        searching = false
    }

    val filteredSections = remember(query) {
        filterGuideSections(query)
    }

    BackHandler(enabled = showSearch) {
        closeSearch()
    }

    val dispatchAction: (GuideAction) -> Unit = { action ->
        when (action) {
            GuideAction.None -> Unit
            is GuideAction.OpenSettings -> onNavigateToSettingsSection(action.sectionId)
            GuideAction.OpenNetworkDiagnostics -> onNavigateToNetworkDiagnostics()
            GuideAction.OpenDrafts -> onNavigateToDrafts()
            GuideAction.OpenSearch -> onNavigateToSearch()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = secondaryPageBackground(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    AnimatedContent(
                        targetState = showSearch,
                        transitionSpec = {
                            fadeIn(tween(if (reducedMotion) 0 else 180)) togetherWith
                                fadeOut(tween(if (reducedMotion) 0 else 130))
                        },
                        label = "featureGuideSearch"
                    ) { active ->
                        if (active) {
                            TextField(
                                value = query,
                                onValueChange = { query = it },
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
                                placeholder = { Text("搜索功能、手势或关键词") },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                )
                            )
                        } else {
                            Text(
                                text = "功能指南",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (showSearch) closeSearch() else onBackClick()
                    }) {
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
                            contentDescription = if (showSearch) "关闭搜索" else "搜索功能指南"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DaoTheme.colors.glassTopBar
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (filteredSections.isEmpty()) {
                item(key = "empty") {
                    SettingsGroupCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "未找到匹配的功能说明",
                                style = readingTextStyle(14.5f, 20f, FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "试试搜索「长按」「横滑」「草稿」或「免打扰」",
                                style = settingsDescriptionStyle(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = { query = "" }
                                    )
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "清空关键词",
                                    style = readingTextStyle(12.5f, 18f, FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            } else {
                items(filteredSections, key = { it.id }) { section ->
                    GuideSectionCard(
                        section = section,
                        onAction = dispatchAction
                    )
                }
            }
        }
    }
}

@Composable
private fun GuideSectionCard(
    section: GuideSection,
    onAction: (GuideAction) -> Unit
) {
    SettingsGroupCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SettingsHeaderIcon(
                    imageVector = sectionIconFor(section.id),
                    contentDescription = section.title
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = section.title,
                    style = readingTextStyle(15f, 21f, FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
            )

            section.items.forEachIndexed { index, item ->
                GuideItemRow(
                    item = item,
                    onClick = if (item.action != GuideAction.None) {
                        { onAction(item.action) }
                    } else {
                        null
                    }
                )
                if (index < section.items.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)
                    )
                }
            }
        }
    }
}

@Composable
private fun GuideItemRow(
    item: GuideItem,
    onClick: (() -> Unit)?
) {
    val rowModifier = Modifier
        .fillMaxWidth()
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
            } else {
                Modifier
            }
        )
        .padding(horizontal = 16.dp, vertical = 12.dp)

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = item.title,
                    style = readingTextStyle(14.5f, 20f, FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false)
                )
                TriggerBadgePill(text = item.triggerBadge)
            }
            Text(
                text = item.summary,
                style = settingsDescriptionStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.80f)
            )
        }

        if (onClick != null) {
            Spacer(modifier = Modifier.width(10.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                    .padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.actionLabel ?: "前往",
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    softWrap = false
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun TriggerBadgePill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 1,
            softWrap = false
        )
    }
}

private fun sectionIconFor(sectionId: String): ImageVector = when (sectionId) {
    "whats_new" -> Icons.Outlined.AutoAwesome
    "reading" -> Icons.Outlined.Forum
    "posting" -> Icons.Outlined.Create
    "collections" -> Icons.Outlined.BookmarkBorder
    "images" -> Icons.Outlined.PhotoLibrary
    else -> Icons.Outlined.Tune
}
