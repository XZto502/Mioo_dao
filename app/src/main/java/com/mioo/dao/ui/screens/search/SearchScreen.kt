package com.mioo.dao.ui.screens.search

import com.mioo.dao.LocalThirdPartyServicesAllowed

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.sp
import com.mioo.dao.ui.components.rememberResponsiveListConfiguration
import com.mioo.dao.ui.components.rememberResponsiveListFling
import com.mioo.dao.ui.screens.settings.secondaryPageBackground
import com.mioo.dao.ui.screens.settings.secondaryCardBackground
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import com.mioo.dao.ui.components.rememberInitialListViewport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.mioo.dao.data.model.LocalSearchHit
import com.mioo.dao.data.model.XdWebSearch
import com.mioo.dao.data.model.effectiveTitle
import com.mioo.dao.ui.theme.DaoTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onBackClick: () -> Unit,
    onNavigateToThread: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state = viewModel.uiState.collectAsState()
    val preferredEngine by remember(state) { derivedStateOf { state.value.preferredEngine } }
    val allowThirdParty = LocalThirdPartyServicesAllowed.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val customTabToolbarColor = MaterialTheme.colorScheme.surface.toArgb()
    val customTabsController = rememberCustomTabsWarmController(
        enabled = allowThirdParty,
        preconnectOrigin = engineOriginUrl(preferredEngine)
    )
    var linksApproved by remember(context) { mutableStateOf<Boolean?>(null) }
    val scope = rememberCoroutineScope()
    DisposableEffect(lifecycleOwner, context, allowThirdParty) {
        var check: Job? = null
        fun checkLinks() {
            if (!allowThirdParty) return
            check?.cancel()
            check = scope.launch {
                linksApproved = withContext(Dispatchers.IO) { areXIslandLinksApproved(context) }
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) checkLinks()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) checkLinks()
        onDispose {
            check?.cancel()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("搜索", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DaoTheme.colors.glassTopBar
                )
            )
        },
        modifier = modifier,
        containerColor = secondaryPageBackground(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .padding(horizontal = 12.dp)
        ) {
            SearchQueryInputBar(
                state = state,
                onQueryChange = viewModel::onQueryChange,
                onSearchSubmit = {
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                    viewModel.searchNow()
                }
            )

            if (!allowThirdParty) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "在本地历史、收藏和缓存中搜索，也可直接输入串号。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (allowThirdParty) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "搜索引擎",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                val engines = remember { XdWebSearch.Engine.entries.toList() }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(engines, key = { it.name }) { engine ->
                        FilterChip(
                            selected = preferredEngine == engine,
                            onClick = { viewModel.selectEngine(engine) },
                            label = { Text(engine.label) }
                        )
                    }
                }
                if (linksApproved == false) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "未授权默认打开 X 岛域名（nmbxd.com / nmbxd1.com），点此授权后点击搜索串结果可直达 ›",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { openAppDefaultLinksSettings(context) }
                    )
                }
            }

            // 全站搜索入口只保留结果区的「用 xx 全站搜索」卡片，避免多个按钮都跳浏览器造成困惑；
            // 本地搜索输入即搜（防抖自动执行），无需独立按钮

            SearchRecentQueriesSection(
                state = state,
                onSelectRecent = viewModel::applyRecent
            )

            Spacer(modifier = Modifier.height(12.dp))

            SearchResultsSection(
                state = state,
                listState = listState,
                allowThirdParty = allowThirdParty,
                preferredEngine = preferredEngine,
                onNavigateToThread = onNavigateToThread,
                onLaunchSiteSearch = {
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                    val url = viewModel.prepareSiteSearchUrl() ?: return@SearchResultsSection
                    val launched = launchCustomTabSearch(
                        context = context,
                        url = url,
                        toolbarColorArgb = customTabToolbarColor,
                        isDarkTheme = isDarkTheme,
                        session = customTabsController.getSession(),
                        preferredPackage = customTabsController.packageName
                    )
                    if (!launched) {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }
                    }
                }
            )
        }
    }
}

private val SearchHitCardShape = RoundedCornerShape(16.dp)
private val SearchSourcePillShape = RoundedCornerShape(8.dp)
private val SearchHtmlTagRegex = Regex("<[^>]+>")
private val SearchWhitespaceRegex = Regex("\\s+")

@Composable
private fun SearchQueryInputBar(
    state: androidx.compose.runtime.State<SearchUiState>,
    onQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit
) {
    val query by remember(state) { derivedStateOf { state.value.query } }
    val isSearching by remember(state) { derivedStateOf { state.value.isSearching } }
    var showSpinner by remember { mutableStateOf(false) }
    LaunchedEffect(isSearching) {
        if (isSearching) {
            kotlinx.coroutines.delay(160)
            showSpinner = true
        } else {
            showSpinner = false
        }
    }
    OutlinedTextField(
        shape = SearchHitCardShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = secondaryCardBackground(),
            unfocusedContainerColor = secondaryCardBackground(),
            focusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
            unfocusedBorderColor = Color.Transparent
        ),
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearchSubmit() }),
        placeholder = { Text("串号 / 关键词") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (showSpinner) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
    )
}

@Composable
private fun SearchRecentQueriesSection(
    state: androidx.compose.runtime.State<SearchUiState>,
    onSelectRecent: (String) -> Unit
) {
    val showRecent by remember(state) {
        derivedStateOf { state.value.recentQueries.isNotEmpty() && state.value.query.isBlank() }
    }
    if (!showRecent) return
    val recentQueries by remember(state) { derivedStateOf { state.value.recentQueries } }
    Spacer(modifier = Modifier.height(12.dp))
    Text("最近搜索", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    Spacer(modifier = Modifier.height(6.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(recentQueries) { q ->
            SuggestionChip(
                onClick = { onSelectRecent(q) },
                label = { Text(q, maxLines = 1) }
            )
        }
    }
}

@Composable
private fun SearchSiteHintQueryText(state: androidx.compose.runtime.State<SearchUiState>) {
    val trimmedQuery by remember(state) { derivedStateOf { state.value.query.trim() } }
    Text(
        text = "site:nmbxd.com OR site:nmbxd1.com $trimmedQuery",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun SearchResultsSection(
    state: androidx.compose.runtime.State<SearchUiState>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    allowThirdParty: Boolean,
    preferredEngine: XdWebSearch.Engine,
    onNavigateToThread: (String) -> Unit,
    onLaunchSiteSearch: () -> Unit
) {
    val result by remember(state) { derivedStateOf { state.value.result } }
    val hasNonBlankQuery by remember(state) { derivedStateOf { state.value.query.isNotBlank() } }
    val isSearching by remember(state) { derivedStateOf { state.value.isSearching } }
    // Defer mounting the site-search hint card by one frame when transitioning from blank -> non-blank
    // so character #1 does not stack recent-chip unmount + site-hint card creation in the same frame.
    var showSiteHint by remember { mutableStateOf(hasNonBlankQuery) }
    LaunchedEffect(hasNonBlankQuery) {
        if (!hasNonBlankQuery) {
            showSiteHint = false
        } else if (!showSiteHint) {
            androidx.compose.runtime.withFrameNanos { }
            showSiteHint = true
        }
    }

    val responsiveConfiguration = rememberResponsiveListConfiguration()
    val responsiveFling = rememberResponsiveListFling()
    val hasResults = result.directThreadId != null || result.hits.isNotEmpty()
    val resultResetKey = remember(result.directThreadId, result.hits.size, result.hits.firstOrNull()?.threadId) {
        "${result.directThreadId}:${result.hits.size}:${result.hits.firstOrNull()?.threadId}"
    }
    val initialViewport = rememberInitialListViewport(
        hasContent = hasResults,
        resetKey = resultResetKey,
        steps = 5
    )
    val cardBg = secondaryCardBackground()
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalViewConfiguration provides responsiveConfiguration
    ) {
        LazyColumn(
            state = listState,
            flingBehavior = responsiveFling,
            userScrollEnabled = !hasResults || initialViewport.ready,
            modifier = Modifier.fillMaxSize().then(initialViewport.modifier),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            result.directThreadId?.let { tid ->
                item(key = "direct_$tid", contentType = "direct") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SearchHitCardShape)
                            .background(cardBg)
                            .clickable { onNavigateToThread(tid) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Tag,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "打开串 No.$tid",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "按串号直达（不依赖搜索）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (allowThirdParty && showSiteHint) {
                item(key = "site_hint", contentType = "site_hint") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SearchHitCardShape)
                            .background(cardBg)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Button,
                                onClick = onLaunchSiteSearch
                            )
                            .padding(14.dp)
                    ) {
                        Text(
                            text = "用 ${preferredEngine.label} 全站搜索",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        SearchSiteHintQueryText(state = state)
                    }
                }
            }

            if (result.hits.isNotEmpty()) {
                item(key = "local_header", contentType = "header") {
                    Text(
                        text = "本地结果",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            if (!isSearching && hasNonBlankQuery && result.isEmpty) {
                item(key = "local_empty", contentType = "empty") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "本地没有匹配",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (allowThirdParty) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "点上方「全站搜索」检索岛上内容",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            items(
                items = result.hits,
                key = { "${it.source}_${it.threadId}" },
                contentType = { "hit" }
            ) { hit ->
                val onHitClick = remember(hit.threadId, onNavigateToThread) {
                    { onNavigateToThread(hit.threadId) }
                }
                SearchHitRow(hit = hit, cardBg = cardBg, onClick = onHitClick)
            }
        }
    }
}

@Composable
private fun SearchHitRow(
    hit: LocalSearchHit,
    cardBg: Color,
    onClick: () -> Unit
) {
    val cleanSnippet = remember(hit.snippet) {
        hit.snippet
            .replace(SearchHtmlTagRegex, " ")
            .replace("&nbsp;", " ")
            .replace("&gt;", ">")
            .replace("&lt;", "<")
            .replace(SearchWhitespaceRegex, " ")
            .trim()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SearchHitCardShape)
            .background(cardBg)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(SearchSourcePillShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = hit.source.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "No.${hit.threadId}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            if (hit.newReplyCount > 0) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "+${hit.newReplyCount}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        hit.title.effectiveTitle()?.let { title ->
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = cleanSnippet.ifBlank { "（无摘要）" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}
