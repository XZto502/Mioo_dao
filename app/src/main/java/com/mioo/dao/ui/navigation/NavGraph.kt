package com.mioo.dao.ui.navigation

import androidx.compose.foundation.ExperimentalFoundationApi
import com.mioo.dao.LocalPredictiveBackEnabled
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.activity.compose.BackHandler
import androidx.activity.OnBackPressedCallback
import androidx.activity.BackEventCompat
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.layout.layout
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.LocalOwnersProvider
import androidx.navigation.createGraph
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.animation.core.Spring
import androidx.compose.ui.zIndex
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import kotlinx.coroutines.flow.first
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import com.mioo.dao.ui.theme.readingBackground
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import com.mioo.dao.ui.theme.DaoTheme
import com.mioo.dao.ui.theme.MiooMotion
import com.mioo.dao.ui.theme.findActivity
import com.mioo.dao.ui.theme.isReducedMotionEnabled
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mioo.dao.ui.screens.feed.FeedScreen
import com.mioo.dao.ui.screens.feed.FeedViewModel
import com.mioo.dao.ui.screens.forum.BoardDrawerSlot
import com.mioo.dao.ui.screens.forum.ForumScreen
import com.mioo.dao.ui.screens.forum.ForumViewModel
import com.mioo.dao.ui.screens.search.SearchScreen
import com.mioo.dao.ui.screens.search.SearchViewModel
import com.mioo.dao.ui.screens.settings.NetworkDiagnosticsScreen
import com.mioo.dao.ui.screens.settings.NetworkDiagnosticsViewModel
import com.mioo.dao.ui.screens.settings.SettingsScreen
import com.mioo.dao.ui.screens.settings.SettingsViewModel
import com.mioo.dao.ui.screens.settings.MoreScreen
import com.mioo.dao.ui.screens.settings.HistoryScreen
import com.mioo.dao.ui.screens.thread.ThreadScreen
import com.mioo.dao.ui.screens.thread.ThreadViewModel


sealed class Screen(val route: String) {
    object Home : Screen("home")
    object SettingsDetail : Screen("settings_detail")
    object NetworkDiagnostics : Screen("network_diagnostics")
    object DraftBox : Screen("draft_box")
    object Search : Screen("search")
    object Thread : Screen("thread/{threadId}?replyDraftId={replyDraftId}&offline={offline}") {
        fun createRoute(threadId: String, replyDraftId: Long? = null, offline: Boolean = false) = when {
            offline -> "thread/$threadId?offline=true"
            replyDraftId != null -> "thread/$threadId?replyDraftId=$replyDraftId"
            else -> "thread/$threadId"
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MiooDaoNavGraph(
    modifier: Modifier = Modifier,
    initialThreadId: String? = null,
    pendingThreadId: String? = null,
    onPendingThreadConsumed: () -> Unit = {},
    /** First board list paint / error — dismiss system splash. */
    onColdStartContentReady: () -> Unit = {}
) {
    val navController = rememberNavController()
    val threadLeaveHandlers = remember { mutableMapOf<String, () -> Unit>() }
    var requestPageBack by remember { mutableStateOf<(() -> Unit)?>(null) }
    var flushPendingBack by remember { mutableStateOf<(() -> Unit)?>(null) }
    val isHomeRoute = remember(navController) { mutableStateOf(initialThreadId.isNullOrBlank()) }
    val visibleTopRoute = remember(navController) {
        mutableStateOf<String?>(if (!initialThreadId.isNullOrBlank()) Screen.Thread.route else Screen.Home.route)
    }
    val hostActivity = LocalContext.current as? android.app.Activity

    LaunchedEffect(initialThreadId) {
        if (!initialThreadId.isNullOrBlank()) {
            onColdStartContentReady()
        }
    }

    // A second tap can land while the detail page is still sliding over the list.
    // The list stays hit-testable until the cover finishes, so ignore that repeat.
    // No launchSingleTop here: thread→thread (跳转原串) targets the same route
    // pattern, and singleTop would reuse the top entry instead of stacking a new
    // one — the screen then never leaves the old thread (threadId is read once
    // in ThreadViewModel).
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val openThread: (String, Boolean) -> Unit = remember(navController, keyboardController, focusManager) {
        { id, offline ->
            if (id.isNotBlank()) {
                flushPendingBack?.invoke()
                val top = navController.currentBackStackEntry
                val topIsThread = top?.destination?.route == Screen.Thread.route
                val topId = top?.arguments?.getString("threadId")
                val alreadyOpen = topIsThread && topId == id
                if (!alreadyOpen) {
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                    navController.navigate(Screen.Thread.createRoute(id, offline = offline))
                }
            }
        }
    }

    val navigateToThread: (String) -> Unit = remember(openThread) { { openThread(it, false) } }
    val navigateToOfflineThread: (String) -> Unit = remember(openThread) { { openThread(it, true) } }

    var instantThreadTarget by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(pendingThreadId) {
        val id = pendingThreadId ?: return@LaunchedEffect
        if (id.isNotBlank()) {
            flushPendingBack?.invoke()
            // Plain push (see navigateToThread): singleTop breaks thread→thread.
            // 与 navigateToThread 相同的去重：同一串已在栈顶时不重复入栈（连点通知场景）
            val top = navController.currentBackStackEntry
            val alreadyOpen = top?.destination?.route == Screen.Thread.route &&
                top?.arguments?.getString("threadId") == id
            if (!alreadyOpen) {
                instantThreadTarget = id
                navController.navigate(Screen.Thread.createRoute(id))
            }
            onPendingThreadConsumed()
        }
    }

    // Read once per activity. Querying animator scale on every navigation janks the first frame.
    val reducedMotion = isReducedMotionEnabled()
    val predictiveBackEnabled = LocalPredictiveBackEnabled.current
    val latestPredictiveBack = rememberUpdatedState(predictiveBackEnabled)

    val density = LocalDensity.current
    var currentTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val latestTabIndex = rememberUpdatedState(currentTabIndex)
    var historyOpenGeneration by remember { mutableIntStateOf(0) }

    val bottomBarItems = remember {
        listOf(
            Triple(0, Icons.Default.Home, "板块"),
            Triple(1, Icons.Default.Bookmark, "收藏"),
            Triple(2, Icons.Default.History, "历史"),
            Triple(3, Icons.Default.Settings, "更多")
        )
    }

    // 板块抽屉包在底栏外层：底栏留在抽屉 content 里，由 scrim 压暗，不再滑出隐藏
    val boardDrawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val boardDrawerSlot = remember { BoardDrawerSlot() }
    val drawerBackProgress = remember { Animatable(0f) }
    val drawerScope = rememberCoroutineScope()
    LaunchedEffect(navController) {
        var prevRoute: String? = null
        navController.currentBackStackEntryFlow.collect { entry ->
            val route = entry.destination.route
            isHomeRoute.value = route == Screen.Home.route
            // 离开详情页即清零：返回列表后立刻重点同一串（快速预览习惯）不被 400ms 窗口吞掉
            if (route != Screen.Thread.route) {
            }
            if (route == Screen.Home.route && prevRoute == Screen.Thread.route &&
                latestTabIndex.value == 2) {
                historyOpenGeneration++
            }
            if (route != Screen.Home.route && boardDrawerState.currentValue != DrawerValue.Closed) {
                boardDrawerState.close()
            }
            prevRoute = route
        }
    }
    // 大图查看器打开时收起悬浮 dock：不遮查看器底部药丸、不拦点击
    var isImageViewerOpen by remember { mutableStateOf(false) }
    var isComposerOpen by remember { mutableStateOf(false) }
    // Scroll the feed downward (finger up) and the tab capsule slides off the bottom.
    // A short reverse drag, or hitting the top, brings it back.
    val bottomBarVisibleFlow = remember { kotlinx.coroutines.flow.MutableStateFlow(true) }
    val bottomBarSlide = remember { Animatable(0f) }
    val hideAfterPx = with(density) { 36.dp.toPx() }
    val showAfterPx = with(density) { 16.dp.toPx() }
    val composerOpenState = rememberUpdatedState(isComposerOpen)
    val bottomBarScrollAway = remember { floatArrayOf(0f) }
    LaunchedEffect(isComposerOpen) {
        bottomBarScrollAway[0] = 0f
        bottomBarVisibleFlow.value = true
    }
    val bottomBarScroll = remember(hideAfterPx, showAfterPx) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                // Scrolling the composer or its picker must not hide the home navigation.
                if (composerOpenState.value) return Offset.Zero
                // Compose 1.6 only reports Drag and Fling. Both should move the bar.
                if (source != NestedScrollSource.Drag && source != NestedScrollSource.Fling) {
                    return Offset.Zero
                }
                // Overscroll at the top: the list did not consume a downward drag.
                if (available.y > 0f && consumed.y == 0f) {
                    bottomBarScrollAway[0] = 0f
                    bottomBarVisibleFlow.value = true
                    return Offset.Zero
                }
                val dy = consumed.y
                if (dy < 0f) {
                    if (bottomBarScrollAway[0] < 0f) bottomBarScrollAway[0] = 0f
                    bottomBarScrollAway[0] += -dy
                    if (bottomBarScrollAway[0] >= hideAfterPx) {
                        bottomBarScrollAway[0] = 0f
                        bottomBarVisibleFlow.value = false
                    }
                } else if (dy > 0f) {
                    if (bottomBarScrollAway[0] > 0f) bottomBarScrollAway[0] = 0f
                    bottomBarScrollAway[0] -= dy
                    if (-bottomBarScrollAway[0] >= showAfterPx) {
                        bottomBarScrollAway[0] = 0f
                        bottomBarVisibleFlow.value = true
                    }
                }
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(currentTabIndex) {
        bottomBarVisibleFlow.value = true
    }
    // 260ms EaseOut dumped the travel into the first frames. 560ms felt slow.
    // 340ms FastOutSlowIn keeps the slide visible without dragging.
    val bottomBarSlideSpec = remember {
        MiooMotion.bottomBarTween<Float>()
    }
    LaunchedEffect(bottomBarVisibleFlow, reducedMotion) {
        bottomBarVisibleFlow.collectLatest { visible ->
            val target = if (visible) 0f else 1f
            if (reducedMotion) {
                bottomBarSlide.snapTo(target)
            } else {
                bottomBarSlide.animateTo(target, bottomBarSlideSpec)
            }
        }
    }

    val homeBusyFlow = remember { kotlinx.coroutines.flow.MutableStateFlow(false) }
    val homeScrolling = remember { BooleanArray(1) }
    val homeTouching = remember { BooleanArray(1) }
    val updateHomeBusy = remember(homeBusyFlow, homeScrolling, homeTouching) {
        { scrolling: Boolean?, touching: Boolean? ->
            scrolling?.let { homeScrolling[0] = it }
            touching?.let { homeTouching[0] = it }
            homeBusyFlow.value = homeScrolling[0] || homeTouching[0]
        }
    }

    val boardHeaderData = com.mioo.dao.ui.components.rememberCustomHeaderData()
    SyncCustomHeaderStatusBar(
        visibleTopRoute = { visibleTopRoute.value },
        currentTabIndex = currentTabIndex,
        drawerState = boardDrawerState,
        isOverlayOpen = isImageViewerOpen || isComposerOpen,
        headerData = boardHeaderData
    )

    ModalNavigationDrawer(
        modifier = modifier,
        drawerState = boardDrawerState,
        // Closed-state opening is direction-arbitrated by the board list. Having
        // both draggable handlers compete cancels opening while the list flings.
        gesturesEnabled = (boardDrawerState.currentValue == DrawerValue.Open ||
            boardDrawerState.targetValue == DrawerValue.Open) &&
            boardDrawerSlot.gesturesEnabled &&
            isHomeRoute.value &&
            currentTabIndex == 0 &&
            !isImageViewerOpen &&
            !isComposerOpen,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(
            alpha = 0.42f * (1f - drawerBackProgress.value)
        ),
        drawerContent = {
            Box(Modifier.graphicsLayer { translationX = -drawerBackProgress.value * size.width }) {
                boardDrawerSlot.content()
            }
            val backOwner = LocalOnBackPressedDispatcherOwner.current
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(backOwner, lifecycleOwner, boardDrawerState.currentValue) {
                val callback = object : OnBackPressedCallback(boardDrawerState.currentValue == DrawerValue.Open) {
                    override fun handleOnBackProgressed(backEvent: BackEventCompat) {
                        if (!latestPredictiveBack.value) return
                        drawerScope.launch { drawerBackProgress.snapTo(backEvent.progress.coerceIn(0f, 1f)) }
                    }
                    override fun handleOnBackCancelled() {
                        if (!latestPredictiveBack.value) return
                        drawerScope.launch { drawerBackProgress.animateTo(0f, spring()) }
                    }
                    override fun handleOnBackPressed() {
                        drawerScope.launch {
                            if (drawerBackProgress.value > 0f) {
                                drawerBackProgress.animateTo(1f, tween(if (reducedMotion) 0 else 180))
                                boardDrawerState.snapTo(DrawerValue.Closed)
                                drawerBackProgress.snapTo(0f)
                            } else {
                                boardDrawerState.close()
                            }
                        }
                    }
                }
                backOwner?.onBackPressedDispatcher?.addCallback(lifecycleOwner, callback)
                onDispose { callback.remove() }
            }
        }
    ) {
        ParallaxPageHost(
            navController,
            reducedMotion,
            predictiveBackEnabled,
            homeBusyFlow = homeBusyFlow,
            initialThreadId = initialThreadId,
            consumeInstantOpen = { entry ->
                val target = instantThreadTarget
                val match = target != null &&
                    entry.destination.route == Screen.Thread.route &&
                    entry.arguments?.getString("threadId") == target
                if (match) instantThreadTarget = null
                match
            },
            registerBackRequest = { requestPageBack = it },
            registerFlushPendingBack = { flushPendingBack = it },
            onPrepareBack = { entry -> threadLeaveHandlers[entry.id]?.invoke() },
            onCommitBack = { entry ->
                navController.currentBackStackEntry != entry || navController.popBackStack()
            },
            onVisibleRouteChange = { route ->
                if (route != null) visibleTopRoute.value = route
            }
        ) { entry ->
            when (entry.destination.route) {
            Screen.Home.route -> {
                BackHandler(enabled = isHomeRoute.value && currentTabIndex != 0 &&
                    boardDrawerState.currentValue == DrawerValue.Closed && !isImageViewerOpen && !isComposerOpen) {
                    currentTabIndex = 0
                }
                DisposableEffect(updateHomeBusy) {
                    onDispose { updateHomeBusy(false, false) }
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(updateHomeBusy) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    updateHomeBusy(null, event.changes.any { it.pressed })
                                }
                            }
                        }
                        .nestedScroll(bottomBarScroll)
                ) {
                    MainTabs(
                        currentTabIndex = currentTabIndex,
                        reducedMotion = reducedMotion,
                        historyOpenGeneration = historyOpenGeneration,
                        boardHeader = boardHeaderData,
                        onNavigateToThread = navigateToThread,
                        onNavigateToOfflineThread = navigateToOfflineThread,
                        onColdStartContentReady = onColdStartContentReady,
                        dockSinkFraction = { bottomBarSlide.value },
                        drawerState = boardDrawerState,
                        boardDrawerSlot = boardDrawerSlot,
                        onForumScrollInProgressChange = remember(updateHomeBusy) {
                            { scrolling -> updateHomeBusy(scrolling, null) }
                        },
                        onImageViewerOpenChange = { isImageViewerOpen = it },
                        onComposerOpenChange = { isComposerOpen = it },
                        onNavigateToSettings = {
                            flushPendingBack?.invoke()
                            navController.navigate(Screen.SettingsDetail.route)
                        },
                        onNavigateToDrafts = {
                            flushPendingBack?.invoke()
                            navController.navigate(Screen.DraftBox.route)
                        },
                        onNavigateToSearch = {
                            flushPendingBack?.invoke()
                            navController.navigate(Screen.Search.route)
                        }
                    )
                    // Keep the dock on the home layer so incoming destinations cover it.
                    FloatingBottomDock(
                        modifier = Modifier.align(Alignment.BottomCenter),
                        visible = !isImageViewerOpen && !isComposerOpen,
                        reducedMotion = reducedMotion,
                        bottomBarSlide = { bottomBarSlide.value },
                        bottomBarItems = bottomBarItems,
                        currentTabIndex = currentTabIndex,
                        onTabSelect = { index ->
                            flushPendingBack?.invoke()
                            if (index == 2) historyOpenGeneration++
                            if (currentTabIndex != index) currentTabIndex = index
                        }
                    )
                }
            }

            Screen.Search.route -> {
                PageBackdrop {
                    val viewModel: SearchViewModel = hiltViewModel()
                    SearchScreen(
                        viewModel = viewModel,
                        onBackClick = { requestPageBack?.invoke() },
                        onNavigateToThread = navigateToThread
                    )
                }
            }

            Screen.SettingsDetail.route -> {
                PageBackdrop {
                    val viewModel: SettingsViewModel = hiltViewModel()
                    SettingsScreen(
                        viewModel = viewModel,
                        onBackClick = {
                            requestPageBack?.invoke()
                        },
                        onOpenNetworkDiagnostics = {
                            if (navController.currentBackStackEntry?.destination?.route == Screen.SettingsDetail.route) {
                                navController.navigate(Screen.NetworkDiagnostics.route)
                            }
                        }
                    )
                }
            }

            Screen.NetworkDiagnostics.route -> {
                PageBackdrop {
                    val viewModel: NetworkDiagnosticsViewModel = hiltViewModel(entry)
                    NetworkDiagnosticsScreen(
                        viewModel = viewModel,
                        onBackClick = {
                            requestPageBack?.invoke()
                        }
                    )
                }
            }

            Screen.DraftBox.route -> {
                PageBackdrop {
                    val viewModel: com.mioo.dao.ui.screens.drafts.DraftViewModel = hiltViewModel()
                    com.mioo.dao.ui.screens.drafts.DraftBoxScreen(
                        viewModel = viewModel,
                        onBackClick = {
                            requestPageBack?.invoke()
                        },
                        onUseReplyDraft = { draft ->
                            if (navController.currentBackStackEntry?.destination?.route == Screen.DraftBox.route) {
                                navController.navigate(Screen.Thread.createRoute(draft.targetId, draft.id))
                            }
                        },
                        onUsePostDraft = {
                            // 回到主界面并切到板块 tab，ForumScreen 会从总线取草稿打开发串框
                            requestPageBack?.invoke()
                            currentTabIndex = 0
                        }
                    )
                }
            }

            Screen.Thread.route -> {
                PageBackdrop {
                    val viewModel: ThreadViewModel = hiltViewModel(entry)
                    val isRootExternalThread = !initialThreadId.isNullOrBlank() &&
                        navController.previousBackStackEntry == null &&
                        navController.currentBackStackEntry?.id == entry.id
                    ThreadScreen(
                        viewModel = viewModel,
                        onBackClick = {
                            if (navController.previousBackStackEntry == null) {
                                hostActivity?.finish()
                            } else {
                                requestPageBack?.invoke()
                            }
                        },
                        onNavigateToThread = navigateToThread,
                        registerSystemBack = if (isRootExternalThread) null else remember(entry.id) {
                            { handler ->
                                if (handler == null) threadLeaveHandlers.remove(entry.id)
                                else threadLeaveHandlers[entry.id] = handler
                                Unit
                            }
                        },
                        retireReadingList = LocalRetireReadingList.current
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun FloatingBottomDock(
    modifier: Modifier = Modifier,
    visible: Boolean,
    reducedMotion: Boolean,
    bottomBarSlide: () -> Float,
    bottomBarItems: List<Triple<Int, androidx.compose.ui.graphics.vector.ImageVector, String>>,
    currentTabIndex: Int,
    onTabSelect: (Int) -> Unit
) {
    val density = LocalDensity.current
    var bottomBarHeightPx by remember { mutableIntStateOf(0) }
    val slideExtraPx = with(density) { 12.dp.roundToPx() }
    val pageBgColor = readingBackground()
    val backdropBrush = remember(pageBgColor) {
        Brush.verticalGradient(listOf(pageBgColor.copy(alpha = 0f), pageBgColor.copy(alpha = 0.94f), pageBgColor))
    }
    val dockOffscreen by remember(bottomBarSlide) {
        derivedStateOf { bottomBarSlide() >= 0.995f }
    }
    AnimatedVisibility(
        modifier = modifier,
        visible = visible,
        enter = if (reducedMotion) EnterTransition.None else slideInVertically { it } + fadeIn(),
        exit = if (reducedMotion) ExitTransition.None else slideOutVertically { it } + fadeOut()
    ) {
        // Outer box draws into the gesture nav area so the 小白条 is immersive;
        // capsule itself stays above the system inset.
        // Translate on the render layer during motion, then park off-screen once settled.
        val slidePx = bottomBarHeightPx + slideExtraPx
        Box(
            modifier = Modifier
                .onSizeChanged { bottomBarHeightPx = it.height }
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) {
                        placeable.place(0, if (dockOffscreen) slidePx else 0)
                    }
                }
                .graphicsLayer {
                    translationY = if (dockOffscreen) 0f else slidePx * bottomBarSlide()
                }
                .fillMaxWidth()
                .background(backdropBrush)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            NavigationBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                containerColor = DaoTheme.colors.glassNavBar.copy(alpha = 0.96f),
                tonalElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
                    .border(
                        width = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
                    )
            ) {
                bottomBarItems.forEach { (index, icon, label) ->
                    NavigationBarItem(
                        icon = {
                            val displayIcon = if (currentTabIndex == index) icon else when (index) {
                                0 -> Icons.Outlined.Home
                                1 -> Icons.Outlined.BookmarkBorder
                                3 -> Icons.Outlined.Settings
                                else -> icon
                            }
                            Icon(displayIcon, contentDescription = label)
                        },
                        label = { Text(label) },
                        selected = currentTabIndex == index,
                        onClick = { onTabSelect(index) }
                    )
                }
            }
        }
    }
}

val LocalPageTransitionSettled = androidx.compose.runtime.staticCompositionLocalOf<() -> Boolean> { { true } }
private val LocalRetireReadingList = androidx.compose.runtime.staticCompositionLocalOf<() -> Boolean> { { false } }

internal fun predictiveBackFinishDurationMs(progress: Float): Int =
    (220f * progress.coerceIn(0f, 1f)).toInt().coerceIn(65, 220)

internal fun computePredictiveLayerProgress(startProgress: Float, gestureProgress: Float): Float =
    (startProgress.coerceIn(0f, 1f) * (1f - 0.9f * gestureProgress.coerceIn(0f, 1f))).coerceIn(0f, 1f)

private class PageLayer(val entry: NavBackStackEntry, initiallyOpen: Boolean) {
    var progress by mutableFloatStateOf(if (initiallyOpen) 1f else 0f)
    var settled by mutableStateOf(initiallyOpen)
    var coverLayer by mutableStateOf<PageLayer?>(null)
    var pendingRetirement by mutableStateOf(false)
    var readingListRetired by mutableStateOf(false)
    var committed = false
    val firstLayout = CompletableDeferred<Unit>()
    var animation: kotlinx.coroutines.Job? = null

    suspend fun animateTo(
        target: Float,
        spec: androidx.compose.animation.core.AnimationSpec<Float>,
        initialVelocity: Float = 0f
    ) {
        animate(progress, target, initialVelocity, spec) { value, _ -> progress = value }
        progress = target
    }
}

private class PageBackGesture(val layer: PageLayer, val predictive: Boolean) {
    var finishing = false
    private var startProgress: Float? = null
    val hasMoved: Boolean get() = startProgress != null

    fun update(progress: Float) {
        if (!predictive || finishing || (progress <= 0f && !hasMoved)) return
        val start = startProgress ?: layer.progress.also {
            layer.animation?.cancel()
            startProgress = it
        }
        layer.progress = computePredictiveLayerProgress(start, progress)
    }
}
@Composable
private fun ParallaxPageHost(
    controller: NavHostController,
    reducedMotion: Boolean,
    predictiveBackEnabled: Boolean,
    homeBusyFlow: kotlinx.coroutines.flow.StateFlow<Boolean>,
    initialThreadId: String? = null,
    consumeInstantOpen: (NavBackStackEntry) -> Boolean = { false },
    registerBackRequest: ((() -> Unit)?) -> Unit,
    registerFlushPendingBack: ((() -> Unit)?) -> Unit,
    onPrepareBack: (NavBackStackEntry) -> Unit,
    onCommitBack: (NavBackStackEntry) -> Boolean,
    onVisibleRouteChange: (String?) -> Unit = {},
    content: @Composable (NavBackStackEntry) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val storeOwner = checkNotNull(LocalViewModelStoreOwner.current)
    val startDestination = if (!initialThreadId.isNullOrBlank()) Screen.Thread.route else Screen.Home.route
    val graph = remember(controller, startDestination, initialThreadId) {
        controller.createGraph(startDestination = startDestination) {
            composable(Screen.Home.route) {}
            composable(Screen.Search.route) {}
            composable(Screen.SettingsDetail.route) {}
            composable(Screen.NetworkDiagnostics.route) {}
            composable(Screen.DraftBox.route) {}
            composable(
                Screen.Thread.route,
                arguments = listOf(
                    navArgument("threadId") {
                        type = NavType.StringType
                        if (!initialThreadId.isNullOrBlank()) defaultValue = initialThreadId
                    },
                    navArgument("replyDraftId") {
                        type = NavType.LongType
                        defaultValue = 0L
                    },
                    navArgument("offline") {
                        type = NavType.BoolType
                        defaultValue = false
                    }
                )
            ) {}
        }
    }
    remember(controller, storeOwner, graph) {
        controller.setViewModelStore(storeOwner.viewModelStore)
        controller.graph = graph
        Unit
    }
    val navigator = remember(controller) { controller.navigatorProvider.getNavigator(ComposeNavigator::class.java) }
    DisposableEffect(controller, lifecycleOwner) {
        controller.setLifecycleOwner(lifecycleOwner)
        onDispose {
            controller.visibleEntries.value.forEach { navigator.onTransitionComplete(it) }
        }
    }

    val entries by navigator.backStack.collectAsState()
    val latestEntries = rememberUpdatedState(entries)
    val stateHolder = rememberSaveableStateHolder()
    val layers = remember { androidx.compose.runtime.mutableStateListOf<PageLayer>() }
    val topVisibleRoute by remember {
        derivedStateOf {
            layers.lastOrNull { !it.pendingRetirement }?.entry?.destination?.route
        }
    }
    LaunchedEffect(topVisibleRoute) {
        onVisibleRouteChange(topVisibleRoute)
    }
    val scope = rememberCoroutineScope()
    val latestReducedMotion = rememberUpdatedState(reducedMotion)
    val latestPredictiveBack = rememberUpdatedState(predictiveBackEnabled)
    val latestCommitBack = rememberUpdatedState(onCommitBack)
    val latestPrepareBack = rememberUpdatedState(onPrepareBack)

    var gesture by remember { mutableStateOf<PageBackGesture?>(null) }
    val backOwner = LocalOnBackPressedDispatcherOwner.current

    fun finishTransitions() {
        controller.visibleEntries.value.forEach { navigator.onTransitionComplete(it) }
    }

    fun syncCoverLayers() {
        for (i in layers.indices) {
            var nextActive: PageLayer? = null
            for (j in i + 1 until layers.size) {
                if (!layers[j].pendingRetirement) {
                    nextActive = layers[j]
                    break
                }
            }
            layers[i].coverLayer = nextActive
        }
    }

    fun commitRetiredLayer(layer: PageLayer): Boolean {
        if (layer.committed) return true
        if (controller.currentBackStackEntry?.id != layer.entry.id) {
            layers.remove(layer)
            syncCoverLayers()
            if (gesture?.layer === layer) gesture = null
            return true
        }
        latestPrepareBack.value(layer.entry)
        val ok = latestCommitBack.value(layer.entry)
        if (ok) {
            layer.committed = true
            layers.remove(layer)
            syncCoverLayers()
            if (gesture?.layer === layer) gesture = null
        }
        return ok
    }

    fun flushPendingRetirements() {
        val pending = layers.filter { it.pendingRetirement }
        if (pending.isEmpty()) return
        for (layer in pending) {
            layer.animation?.cancel()
            commitRetiredLayer(layer)
        }
    }

    LaunchedEffect(entries) {
        val activeIds = entries.map { it.id }.toSet()
        entries.forEachIndexed { index, entry ->
            if (layers.none { it.entry.id == entry.id }) {
                val instant = index == 0 || reducedMotion || consumeInstantOpen(entry)
                val layer = PageLayer(entry, initiallyOpen = instant)
                layers.add(layer)
                if (!instant) {
                    layer.animation = scope.launch {
                        layer.firstLayout.await()
                        withFrameNanos { }
                        layer.animateTo(1f, tween(380, easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)))
                        layer.settled = true
                        finishTransitions()
                    }
                }
            }
        }
        val removed = layers.filter { it.entry.id !in activeIds }
        for (layer in removed) {
            layer.animation?.cancel()
            layers.remove(layer)
        }
        syncCoverLayers()
        gesture?.takeIf { it.layer in removed }?.let {
            gesture = null
        }
        if (layers.none { it.animation?.isActive == true }) finishTransitions()
    }

    fun beginBack(predictive: Boolean): PageBackGesture? {
        flushPendingRetirements()
        val stack = latestEntries.value
        if (stack.size < 2 || gesture?.finishing == true) return null
        val layer = layers.lastOrNull { it.entry.id == stack.last().id && !it.pendingRetirement } ?: return null
        if (!predictive) layer.animation?.cancel()
        return PageBackGesture(layer, predictive && !latestReducedMotion.value).also { gesture = it }
    }

    fun finishBack(active: PageBackGesture) {
        if (active.finishing) return
        active.finishing = true
        active.layer.animation?.cancel()
        active.layer.animation = scope.launch {
            if (active.predictive) {
                val duration = predictiveBackFinishDurationMs(active.layer.progress)
                active.layer.animateTo(
                    0f,
                    tween(duration, easing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f))
                )
            } else {
                active.layer.animateTo(0f, tween(if (latestReducedMotion.value) 0 else 340, easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)))
            }
            // Reveal the destination immediately. Give the next touch time to
            // arrive before releasing the off-screen composition.
            active.layer.pendingRetirement = true
            syncCoverLayers()
            if (gesture === active) gesture = null
            kotlinx.coroutines.withTimeoutOrNull(4000L) {
                com.mioo.dao.ui.components.awaitContinuousIdle(homeBusyFlow, 180L)
                if (active.layer.entry.destination.route == Screen.Thread.route) {
                    latestPrepareBack.value(active.layer.entry)
                    active.layer.readingListRetired = true
                    // Give Compose a frame to release the list before dropping its owners.
                    withFrameNanos { }
                    withFrameNanos { }
                    if (homeBusyFlow.value) {
                        com.mioo.dao.ui.components.awaitContinuousIdle(homeBusyFlow, 180L)
                    }
                }
            }
            if (!commitRetiredLayer(active.layer)) {
                active.layer.readingListRetired = false
                active.layer.pendingRetirement = false
                syncCoverLayers()
                active.layer.animateTo(1f, spring(stiffness = Spring.StiffnessMediumLow))
                finishTransitions()
            }
            if (gesture === active) gesture = null
        }
    }

    val latestBeginBack = rememberUpdatedState<(Boolean) -> PageBackGesture?> { beginBack(it) }
    val latestFinishBack = rememberUpdatedState<(PageBackGesture) -> Unit> { finishBack(it) }
    val latestFlushPending = rememberUpdatedState { flushPendingRetirements() }
    val callback = remember(controller, scope) {
        object : OnBackPressedCallback(false) {
            override fun handleOnBackStarted(backEvent: BackEventCompat) {
                if (latestPredictiveBack.value) latestBeginBack.value(true)?.update(backEvent.progress)
            }

            override fun handleOnBackProgressed(backEvent: BackEventCompat) {
                val active = gesture ?: return
                if (active.predictive && !active.finishing) active.update(backEvent.progress)
            }

            override fun handleOnBackCancelled() {
                val active = gesture ?: return
                if (active.finishing) return
                gesture = null
                if (!active.hasMoved) return
                active.layer.animation = scope.launch {
                    active.layer.animateTo(
                        1f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 800f)
                    )
                    active.layer.settled = true
                    finishTransitions()
                }
            }

            override fun handleOnBackPressed() {
                val active = gesture ?: latestBeginBack.value(false) ?: return
                latestFinishBack.value(active)
            }
        }
    }
    DisposableEffect(backOwner, lifecycleOwner, callback) {
        backOwner?.onBackPressedDispatcher?.addCallback(lifecycleOwner, callback)
        onDispose { callback.remove() }
    }
    SideEffect { callback.isEnabled = layers.count { !it.pendingRetirement } > 1 }
    val requestBack = remember {
        { latestBeginBack.value(false)?.let { latestFinishBack.value(it) }; Unit }
    }
    val flushBack = remember { { latestFlushPending.value() } }
    DisposableEffect(registerBackRequest, requestBack) {
        registerBackRequest(requestBack)
        onDispose { registerBackRequest(null) }
    }
    DisposableEffect(registerFlushPendingBack, flushBack) {
        registerFlushPendingBack(flushBack)
        onDispose { registerFlushPendingBack(null) }
    }

    val edgeShadowBrush = remember {
        Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.14f)))
    }

    Box(
        modifier = Modifier.fillMaxSize().clip(androidx.compose.ui.graphics.RectangleShape).background(readingBackground()),
        contentAlignment = Alignment.TopStart
    ) {
        layers.forEachIndexed { index, layer ->
            key(layer.entry.id) {
                val isSettled = remember(layer) { { layer.settled } }
                val retireReadingList = remember(layer) { { layer.readingListRetired } }
                Box(Modifier.fillMaxSize().zIndex(index.toFloat()).onSizeChanged {
                    if (it.width > 0 && it.height > 0) layer.firstLayout.complete(Unit)
                }.graphicsLayer {
                    val currentProgress = if (layer.pendingRetirement) 0f else layer.progress.coerceIn(0f, 1f)
                    val coverProgress = layer.coverLayer?.progress?.coerceIn(0f, 1f) ?: 0f
                    translationY = 0f
                    scaleX = 1f
                    scaleY = 1f
                    shape = androidx.compose.ui.graphics.RectangleShape
                    clip = false
                    translationX = kotlin.math.round(size.width * (1f - currentProgress - 0.25f * coverProgress))
                    // Keep Home's display list cached behind opaque layers instead of dropping drawContent().
                    alpha = if (coverProgress >= 0.999f || layer.pendingRetirement || (index > 0 && currentProgress <= 0.001f)) 0f else 1f
                }.pointerInput(Unit) {
                    // This modifier must be inside the translated layer so its hit area
                    // follows the page instead of covering the revealed destination.
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent()
                    }
                }) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        LocalPageTransitionSettled provides isSettled,
                        LocalRetireReadingList provides retireReadingList
                    ) {
                        layer.entry.LocalOwnersProvider(stateHolder) { content(layer.entry) }
                    }
                    Box(Modifier.matchParentSize().graphicsLayer {
                        val c = layer.coverLayer?.progress?.coerceIn(0f, 1f) ?: 0f
                        alpha = if (c > 0.001f && c < 0.999f) 0.12f * c else 0f
                    }.background(Color.Black))
                    if (index > 0) {
                        Box(Modifier.fillMaxHeight().width(12.dp).graphicsLayer {
                            translationX = -size.width
                            val p = if (layer.pendingRetirement) 0f else layer.progress.coerceIn(0f, 1f)
                            alpha = if (p > 0.001f && p < 0.999f) 1f else 0f
                        }.background(edgeShadowBrush))
                    }
                }
            }
        }
    }
}
@Composable
private fun SyncCustomHeaderStatusBar(
    visibleTopRoute: () -> String?,
    currentTabIndex: Int,
    drawerState: androidx.compose.material3.DrawerState,
    isOverlayOpen: Boolean,
    headerData: com.mioo.dao.ui.components.CustomHeaderData
) {
    val view = androidx.compose.ui.platform.LocalView.current
    val context = LocalContext.current
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val isDrawerClosed by remember(drawerState) {
        derivedStateOf {
            drawerState.currentValue == DrawerValue.Closed &&
                drawerState.targetValue == DrawerValue.Closed
        }
    }
    val route = visibleTopRoute()
    val isHome = route == Screen.Home.route
    val isThread = route == Screen.Thread.route
    val hasHeader = headerData.enabled
    val density = LocalDensity.current
    val screenWidthPx = with(density) { androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val statusBarHeightPx = WindowInsets.statusBars.getTop(density).toFloat()
    val estimatedBarHeightPx = statusBarHeightPx + with(density) { 64.dp.toPx() }
    val applyToThread = headerData.settings.applyToThread
    val useLightStatusBarIcons = headerData.useLightStatusBarIcons(
        containerWidthPx = screenWidthPx,
        containerHeightPx = estimatedBarHeightPx,
        statusBarHeightPx = statusBarHeightPx
    )
    val customHeaderVisible = hasHeader && when {
        isHome -> currentTabIndex == 0 && isDrawerClosed
        isThread -> applyToThread
        else -> false
    }
    val effectiveLightStatusBars = com.mioo.dao.ui.components.resolveEffectiveLightStatusBars(
        darkTheme = darkTheme,
        isHomeRoute = isHome,
        isThreadRoute = isThread,
        currentTabIndex = currentTabIndex,
        isDrawerClosed = isDrawerClosed,
        isOverlayOpen = false,
        hasCustomHeaderImage = hasHeader,
        applyHeaderToThread = applyToThread,
        useLightStatusBarIcons = useLightStatusBarIcons
    )
    val overrideValue: Boolean? = if (customHeaderVisible) effectiveLightStatusBars else null
    val lastApplied = remember { arrayOfNulls<Boolean>(1) }

    DisposableEffect(view, context, overrideValue, effectiveLightStatusBars, isOverlayOpen) {
        com.mioo.dao.ui.theme.activeLightStatusBarsOverride = overrideValue
        // Only write to WindowInsetsController when custom header is active or when clearing a
        // previously applied custom-header polarity override (avoids redundant IPC on overlay close).
        val shouldSyncWindow = overrideValue != null || lastApplied[0] != null
        if (shouldSyncWindow && !isOverlayOpen && !com.mioo.dao.ui.theme.isActivitySystemBarsOverridden) {
            val window = context.findActivity()?.window
            if (window != null) {
                androidx.core.view.WindowCompat.getInsetsController(window, view)
                    .isAppearanceLightStatusBars = effectiveLightStatusBars
                lastApplied[0] = overrideValue
            }
        }
        onDispose { }
    }
    DisposableEffect(Unit) {
        onDispose {
            com.mioo.dao.ui.theme.activeLightStatusBarsOverride = null
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MainTabs(
    currentTabIndex: Int,
    reducedMotion: Boolean,
    historyOpenGeneration: Int,
    boardHeader: com.mioo.dao.ui.components.CustomHeaderData,
    onNavigateToThread: (String) -> Unit,
    onNavigateToOfflineThread: (String) -> Unit,
    onColdStartContentReady: () -> Unit,
    dockSinkFraction: () -> Float = { 0f },
    drawerState: androidx.compose.material3.DrawerState,
    boardDrawerSlot: BoardDrawerSlot,
    onForumScrollInProgressChange: (Boolean) -> Unit = {},
    onImageViewerOpenChange: (Boolean) -> Unit = {},
    onComposerOpenChange: (Boolean) -> Unit = {},
    onNavigateToSettings: () -> Unit,
    onNavigateToDrafts: () -> Unit,
    onNavigateToSearch: () -> Unit
) {
    val tabState = rememberSaveableStateHolder()
    val slidePx = with(LocalDensity.current) { 20.dp.roundToPx() }
    var mountedTabs by remember { mutableStateOf(setOf(currentTabIndex)) }
    var activeTab by remember { mutableIntStateOf(currentTabIndex) }
    var outgoingTab by remember { mutableStateOf<Int?>(null) }
    var transitionDirection by remember { mutableIntStateOf(1) }
    val enterProgress = remember { Animatable(1f) }
    val exitAlpha = remember { Animatable(0f) }

    val boardHeaderState = rememberUpdatedState(boardHeader)
    val activeTabState = rememberUpdatedState(activeTab)
    val outgoingTabState = rememberUpdatedState(outgoingTab)
    val reducedMotionState = rememberUpdatedState(reducedMotion)

    // Keep modifier instances stable across tab switches so ForumScreen never recomposes
    // or detaches hardware layers mid-transition.
    val boardHeaderMod = remember(slidePx) {
        Modifier.graphicsLayer {
            val splitEnter = activeTabState.value == 0 &&
                outgoingTabState.value != null &&
                boardHeaderState.value.enabled &&
                !reducedMotionState.value
            translationX = if (splitEnter) -slidePx * (1f - enterProgress.value) else 0f
        }
    }
    val boardBodyMod = remember(slidePx) {
        Modifier.graphicsLayer {
            val splitEnter = activeTabState.value == 0 &&
                outgoingTabState.value != null &&
                boardHeaderState.value.enabled &&
                !reducedMotionState.value
            if (splitEnter) {
                val p = enterProgress.value
                alpha = p
                translationX = -slidePx * (1f - p)
                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
            } else {
                alpha = 1f
                translationX = 0f
                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Auto
            }
        }
    }

    LaunchedEffect(currentTabIndex, reducedMotion) {
        if (currentTabIndex !in mountedTabs) {
            mountedTabs = mountedTabs + currentTabIndex
        }
        if (currentTabIndex == activeTab) return@LaunchedEffect
        transitionDirection = if (currentTabIndex >= activeTab) 1 else -1
        if (reducedMotion) {
            outgoingTab = null
            activeTab = currentTabIndex
            enterProgress.snapTo(1f)
            exitAlpha.snapTo(0f)
        } else {
            outgoingTab = activeTab
            activeTab = currentTabIndex
            enterProgress.snapTo(0f)
            exitAlpha.snapTo(1f)
            val enterEasing = if (activeTab == 0 && boardHeader.enabled) {
                androidx.compose.animation.core.LinearOutSlowInEasing
            } else {
                FastOutSlowInEasing
            }
            kotlinx.coroutines.coroutineScope {
                launch { enterProgress.animateTo(1f, tween(160, easing = enterEasing)) }
                launch { exitAlpha.animateTo(0f, tween(70, easing = LinearEasing)) }
            }
            outgoingTab = null
        }
    }

    Box(Modifier.fillMaxSize().background(readingBackground())) {
        for (page in 0..3) {
            if (page !in mountedTabs) continue
            key(page) {
                val isActive = page == activeTab
                val isOutgoing = page == outgoingTab && outgoingTab != activeTab
                val isVisible = isActive || isOutgoing
                val isTransitioning = outgoingTab != null
                val splitBoardEnter = isActive && isTransitioning && page == 0 && boardHeader.enabled && !reducedMotion
                val isPageActive = remember(page) { { activeTabState.value == page } }
                Box(
                    Modifier
                        .fillMaxSize()
                        .zIndex(
                            when {
                                isActive -> 2f
                                isOutgoing -> 1f
                                else -> -1f
                            }
                        )
                        .drawWithContent {
                            if (isVisible) drawContent()
                        }
                        .pointerInput(isVisible) {
                            if (!isVisible) {
                                awaitPointerEventScope {
                                    while (true) {
                                        awaitPointerEvent(PointerEventPass.Initial)
                                            .changes.forEach { it.consume() }
                                    }
                                }
                            }
                        }
                        .graphicsLayer {
                            when {
                                isOutgoing -> {
                                    alpha = exitAlpha.value
                                    translationX = 0f
                                    compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                                }
                                isActive && isTransitioning && !splitBoardEnter -> {
                                    val p = enterProgress.value
                                    alpha = p
                                    translationX = transitionDirection * slidePx * (1f - p)
                                    compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                                }
                                isActive -> {
                                    alpha = 1f
                                    translationX = 0f
                                    compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Auto
                                }
                                else -> {
                                    alpha = 0f
                                    translationX = 0f
                                    compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Auto
                                }
                            }
                        }
                ) {
                    TabPageContent(
                        page = page,
                        tabState = tabState,
                        historyOpenGeneration = historyOpenGeneration,
                        onNavigateToThread = onNavigateToThread,
                        onNavigateToOfflineThread = onNavigateToOfflineThread,
                        onColdStartContentReady = onColdStartContentReady,
                        dockSinkFraction = dockSinkFraction,
                        drawerState = drawerState,
                        boardDrawerSlot = boardDrawerSlot,
                        onImageViewerOpenChange = onImageViewerOpenChange,
                        onComposerOpenChange = onComposerOpenChange,
                        reducedMotion = reducedMotion,
                        boardHeader = boardHeader,
                        boardHeaderMod = boardHeaderMod,
                        boardBodyMod = boardBodyMod,
                        isPageActive = isPageActive,
                        onForumScrollInProgressChange = onForumScrollInProgressChange,
                        onNavigateToSettings = onNavigateToSettings,
                        onNavigateToDrafts = onNavigateToDrafts,
                        onNavigateToSearch = onNavigateToSearch
                    )
                }
            }
        }
    }
}

@Composable
private fun TabPageContent(
    page: Int,
    tabState: androidx.compose.runtime.saveable.SaveableStateHolder,
    historyOpenGeneration: Int,
    onNavigateToThread: (String) -> Unit,
    onNavigateToOfflineThread: (String) -> Unit,
    onColdStartContentReady: () -> Unit,
    dockSinkFraction: () -> Float,
    drawerState: androidx.compose.material3.DrawerState,
    boardDrawerSlot: BoardDrawerSlot,
    onImageViewerOpenChange: (Boolean) -> Unit,
    onComposerOpenChange: (Boolean) -> Unit,
    reducedMotion: Boolean,
    boardHeader: com.mioo.dao.ui.components.CustomHeaderData,
    boardHeaderMod: Modifier,
    boardBodyMod: Modifier,
    isPageActive: () -> Boolean,
    onForumScrollInProgressChange: (Boolean) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToDrafts: () -> Unit,
    onNavigateToSearch: () -> Unit
) {
    tabState.SaveableStateProvider(page) {
        when (page) {
            0 -> {
                val viewModel: ForumViewModel = hiltViewModel()
                ForumScreen(
                    viewModel = viewModel,
                    onNavigateToThread = onNavigateToThread,
                    onColdStartContentReady = onColdStartContentReady,
                    dockSinkFraction = dockSinkFraction,
                    drawerState = drawerState,
                    boardDrawerSlot = boardDrawerSlot,
                    onScrollInProgressChange = onForumScrollInProgressChange,
                    onImageViewerOpenChange = onImageViewerOpenChange,
                    onComposerOpenChange = onComposerOpenChange,
                    headerData = boardHeader,
                    headerModifier = boardHeaderMod,
                    bodyModifier = boardBodyMod
                )
            }
            1 -> PageBackdrop {
                val viewModel: FeedViewModel = hiltViewModel()
                FeedScreen(
                    viewModel = viewModel,
                    onNavigateToThread = onNavigateToThread,
                    onNavigateToOfflineThread = onNavigateToOfflineThread,
                    onImageViewerOpenChange = onImageViewerOpenChange,
                    isActiveTab = isPageActive
                )
            }
            2 -> PageBackdrop {
                HistoryScreen(
                    onNavigateToThread = onNavigateToThread,
                    onImageViewerOpenChange = onImageViewerOpenChange,
                    openGeneration = historyOpenGeneration,
                    isActiveTab = isPageActive,
                    reducedMotion = reducedMotion
                )
            }
            else -> PageBackdrop {
                val viewModel: SettingsViewModel = hiltViewModel()
                MoreScreen(
                    viewModel = viewModel,
                    onNavigateToSettings = onNavigateToSettings,
                    onNavigateToDrafts = onNavigateToDrafts,
                    onNavigateToSearch = onNavigateToSearch
                )
            }
        }
    }
}
