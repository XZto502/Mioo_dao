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
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.LocalOwnersProvider
import androidx.navigation.createGraph
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.SideEffect
import androidx.compose.animation.core.Spring
import androidx.compose.ui.zIndex
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import com.mioo.dao.ui.theme.readingBackground
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import com.mioo.dao.ui.theme.DaoTheme
import com.mioo.dao.ui.theme.MiooMotion
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
import com.mioo.dao.ui.screens.settings.SettingsScreen
import com.mioo.dao.ui.screens.settings.SettingsViewModel
import com.mioo.dao.ui.screens.settings.MoreScreen
import com.mioo.dao.ui.screens.settings.HistoryScreen
import com.mioo.dao.ui.screens.thread.ThreadScreen
import com.mioo.dao.ui.screens.thread.ThreadViewModel


sealed class Screen(val route: String) {
    object Home : Screen("home")
    object SettingsDetail : Screen("settings_detail")
    object DraftBox : Screen("draft_box")
    object Search : Screen("search")
    object Thread : Screen("thread/{threadId}") {
        fun createRoute(threadId: String) = "thread/$threadId"
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MiooDaoNavGraph(
    modifier: Modifier = Modifier,
    pendingThreadId: String? = null,
    onPendingThreadConsumed: () -> Unit = {},
    /** First board list paint / error — dismiss system splash. */
    onColdStartContentReady: () -> Unit = {}
) {
    val navController = rememberNavController()
    val threadLeaveHandlers = remember { mutableMapOf<String, () -> Unit>() }
    var requestPageBack by remember { mutableStateOf<(() -> Unit)?>(null) }
    var committingPageBack by remember { mutableStateOf(false) }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // A second tap can land while the detail page is still sliding over the list.
    // The list stays hit-testable until the cover finishes, so ignore that repeat.
    // No launchSingleTop here: thread→thread (跳转原串) targets the same route
    // pattern, and singleTop would reuse the top entry instead of stacking a new
    // one — the screen then never leaves the old thread (threadId is read once
    // in ThreadViewModel). alreadyOpen + the 400ms window cover the dedupe.
    val lastThreadTapAt = remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    val navigateToThread: (String) -> Unit = remember(navController) {
        { id: String ->
            if (id.isNotBlank()) {
                val now = android.os.SystemClock.uptimeMillis()
                val top = navController.currentBackStackEntry
                val topIsThread = top?.destination?.route == Screen.Thread.route
                val topId = top?.arguments?.getString("threadId")
                val repeatWhileCovered = !topIsThread && now - lastThreadTapAt.longValue < 400L
                val alreadyOpen = topIsThread && topId == id
                if (!repeatWhileCovered && !alreadyOpen) {
                    lastThreadTapAt.longValue = now
                    navController.navigate(Screen.Thread.createRoute(id))
                }
            }
        }
    }
    // 离开详情页即清零：返回列表后立刻重点同一串（快速预览习惯）不被 400ms 窗口吞掉
    LaunchedEffect(currentRoute) {
        if (currentRoute != Screen.Thread.route) {
            lastThreadTapAt.longValue = 0L
        }
    }

    androidx.compose.runtime.LaunchedEffect(pendingThreadId) {
        val id = pendingThreadId ?: return@LaunchedEffect
        if (id.isNotBlank()) {
            // Plain push (see navigateToThread): singleTop breaks thread→thread.
            // 与 navigateToThread 相同的去重：同一串已在栈顶时不重复入栈（连点通知场景）
            val top = navController.currentBackStackEntry
            val alreadyOpen = top?.destination?.route == Screen.Thread.route &&
                top?.arguments?.getString("threadId") == id
            if (!alreadyOpen) {
                navController.navigate(Screen.Thread.createRoute(id))
            }
            onPendingThreadConsumed()
        }
    }

    // Read once per activity. Querying animator scale on every navigation janks the first frame.
    val context = LocalContext.current
    val reducedMotion = remember(context) { isReducedMotionEnabled(context) }
    val predictiveBackEnabled = LocalPredictiveBackEnabled.current
    val latestPredictiveBack = rememberUpdatedState(predictiveBackEnabled)

    val density = LocalDensity.current
    var currentTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var historyOpenGeneration by remember { mutableIntStateOf(0) }
    var previousRoute by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(currentRoute) {
        if (currentRoute == Screen.Home.route && previousRoute == Screen.Thread.route &&
            currentTabIndex == 2) {
            historyOpenGeneration++
        }
        previousRoute = currentRoute
    }

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
    LaunchedEffect(currentRoute) {
        if (currentRoute != Screen.Home.route && boardDrawerState.currentValue != DrawerValue.Closed) {
            boardDrawerState.close()
        }
    }
    // 大图查看器打开时收起悬浮 dock：不遮查看器底部药丸、不拦点击
    var isImageViewerOpen by remember { mutableStateOf(false) }
    var isComposerOpen by remember { mutableStateOf(false) }
    // Scroll the feed downward (finger up) and the tab capsule slides off the bottom.
    // A short reverse drag, or hitting the top, brings it back.
    var bottomBarVisible by remember { mutableStateOf(true) }
    var bottomBarHeightPx by remember { mutableIntStateOf(0) }
    val bottomBarSlide = remember { Animatable(0f) }
    val hideAfterPx = with(density) { 36.dp.toPx() }
    val showAfterPx = with(density) { 16.dp.toPx() }
    val composerOpenState = rememberUpdatedState(isComposerOpen)
    val bottomBarScrollAway = remember { floatArrayOf(0f) }
    LaunchedEffect(isComposerOpen) {
        bottomBarScrollAway[0] = 0f
        bottomBarVisible = true
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
                    bottomBarVisible = true
                    return Offset.Zero
                }
                val dy = consumed.y
                if (dy < 0f) {
                    if (bottomBarScrollAway[0] < 0f) bottomBarScrollAway[0] = 0f
                    bottomBarScrollAway[0] += -dy
                    if (bottomBarScrollAway[0] >= hideAfterPx) {
                        bottomBarScrollAway[0] = 0f
                        bottomBarVisible = false
                    }
                } else if (dy > 0f) {
                    if (bottomBarScrollAway[0] > 0f) bottomBarScrollAway[0] = 0f
                    bottomBarScrollAway[0] -= dy
                    if (-bottomBarScrollAway[0] >= showAfterPx) {
                        bottomBarScrollAway[0] = 0f
                        bottomBarVisible = true
                    }
                }
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(currentTabIndex) {
        bottomBarVisible = true
    }
    // 260ms EaseOut dumped the travel into the first frames. 560ms felt slow.
    // 340ms FastOutSlowIn keeps the slide visible without dragging.
    val bottomBarSlideSpec = remember {
        MiooMotion.bottomBarTween<Float>()
    }
    LaunchedEffect(bottomBarVisible, reducedMotion) {
        val target = if (bottomBarVisible) 0f else 1f
        if (reducedMotion) {
            bottomBarSlide.snapTo(target)
        } else {
            bottomBarSlide.animateTo(target, bottomBarSlideSpec)
        }
    }

    ModalNavigationDrawer(
        modifier = modifier,
        drawerState = boardDrawerState,
        gesturesEnabled = boardDrawerSlot.gesturesEnabled &&
            currentRoute == Screen.Home.route &&
            currentTabIndex == 0 &&
            !isImageViewerOpen &&
            !isComposerOpen,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.42f * (1f - drawerBackProgress.value)),
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
            registerBackRequest = { requestPageBack = it },
            onCommitBack = { entry ->
                committingPageBack = true
                try {
                    val leave = threadLeaveHandlers[entry.id]
                    if (leave != null) {
                        leave()
                        navController.currentBackStackEntry != entry
                    } else {
                        navController.popBackStack()
                    }
                } finally {
                    committingPageBack = false
                }
            }
        ) { entry ->
            when (entry.destination.route) {
            Screen.Home.route -> {
                BackHandler(enabled = currentRoute == Screen.Home.route && currentTabIndex != 0 &&
                    boardDrawerState.currentValue == DrawerValue.Closed && !isImageViewerOpen && !isComposerOpen) {
                    currentTabIndex = 0
                }
                Box(Modifier.fillMaxSize().nestedScroll(bottomBarScroll)) {
                    MainTabs(
                        currentTabIndex = currentTabIndex,
                        reducedMotion = reducedMotion,
                        historyOpenGeneration = historyOpenGeneration,
                        onNavigateToThread = navigateToThread,
                        onColdStartContentReady = onColdStartContentReady,
                        dockSinkFraction = { bottomBarSlide.value },
                        drawerState = boardDrawerState,
                        boardDrawerSlot = boardDrawerSlot,
                        onImageViewerOpenChange = { isImageViewerOpen = it },
                        onComposerOpenChange = { isComposerOpen = it },
                        onNavigateToSettings = {
                            navController.navigate(Screen.SettingsDetail.route)
                        },
                        onNavigateToDrafts = {
                            navController.navigate(Screen.DraftBox.route)
                        },
                        onNavigateToSearch = {
                            navController.navigate(Screen.Search.route)
                        }
                    )
                    // Keep the dock on the home layer so incoming destinations cover it.
                    AnimatedVisibility(
                        modifier = Modifier.align(Alignment.BottomCenter),
                        visible = !isImageViewerOpen && !isComposerOpen,
                        enter = if (reducedMotion) EnterTransition.None else slideInVertically { it } + fadeIn(),
                        exit = if (reducedMotion) ExitTransition.None else slideOutVertically { it } + fadeOut()
                    ) {
                        // Outer box draws into the gesture nav area so the 小白条 is immersive;
                        // capsule itself stays above the system inset.
                        // offset (not graphicsLayer) moves the touch target with the slide.
                        val slideFraction = bottomBarSlide.value
                        val slidePx = bottomBarHeightPx + with(density) { 12.dp.roundToPx() }
                        val pageBgColor = readingBackground()
                        Box(
                            modifier = Modifier
                                .onSizeChanged { bottomBarHeightPx = it.height }
                                .offset { IntOffset(0, (slidePx * slideFraction).toInt()) }
                                .fillMaxWidth()
                                .background(Brush.verticalGradient(listOf(pageBgColor.copy(alpha = 0f), pageBgColor.copy(alpha = 0.94f), pageBgColor)))
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
                                        onClick = {
                                            if (index == 2) historyOpenGeneration++
                                            if (currentTabIndex == index) return@NavigationBarItem
                                            currentTabIndex = index
                                        }
                                    )
                                }
                            }
                        }
                    }
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
                        onNavigateToThread = navigateToThread,
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
                    ThreadScreen(
                        viewModel = viewModel,
                        onBackClick = {
                            if (committingPageBack) navController.popBackStack() else requestPageBack?.invoke()
                        },
                        onNavigateToThread = navigateToThread,
                        registerSystemBack = remember(entry.id) {
                            { handler ->
                                if (handler == null) threadLeaveHandlers.remove(entry.id)
                                else threadLeaveHandlers[entry.id] = handler
                                Unit
                            }
                        }
                    )
                }
            }
            }
        }
    }
}

private class PageLayer(val entry: NavBackStackEntry, initiallyOpen: Boolean) {
    val transition = Animatable(if (initiallyOpen) 1f else 0f)
    var animation: kotlinx.coroutines.Job? = null
}

private class PageBackGesture(val layer: PageLayer, val startValue: Float) {
    var finishing = false
}

@Composable
private fun ParallaxPageHost(
    controller: NavHostController,
    reducedMotion: Boolean,
    predictiveBackEnabled: Boolean,
    registerBackRequest: ((() -> Unit)?) -> Unit,
    onCommitBack: (NavBackStackEntry) -> Boolean,
    content: @Composable (NavBackStackEntry) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val storeOwner = checkNotNull(LocalViewModelStoreOwner.current)
    val graph = remember(controller) {
        controller.createGraph(startDestination = Screen.Home.route) {
            composable(Screen.Home.route) {}
            composable(Screen.Search.route) {}
            composable(Screen.SettingsDetail.route) {}
            composable(Screen.DraftBox.route) {}
            composable(Screen.Thread.route, arguments = listOf(navArgument("threadId") {
                type = NavType.StringType
            })) {}
        }
    }
    controller.setViewModelStore(storeOwner.viewModelStore)
    controller.graph = graph
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
    val scope = rememberCoroutineScope()
    val latestReducedMotion = rememberUpdatedState(reducedMotion)
    val latestPredictiveBack = rememberUpdatedState(predictiveBackEnabled)
    val latestCommitBack = rememberUpdatedState(onCommitBack)
    val enterEasing = remember { CubicBezierEasing(0.2f, 0f, 0f, 1f) }
    val exitEasing = remember { CubicBezierEasing(0.2f, 0f, 0.25f, 1f) }
    var gesture by remember { mutableStateOf<PageBackGesture?>(null) }
    val backOwner = LocalOnBackPressedDispatcherOwner.current

    fun finishTransitions() {
        controller.visibleEntries.value.forEach { navigator.onTransitionComplete(it) }
    }

    LaunchedEffect(entries) {
        val activeIds = entries.map { it.id }.toSet()
        entries.forEachIndexed { index, entry ->
            if (layers.none { it.entry.id == entry.id }) {
                val layer = PageLayer(entry, initiallyOpen = index == 0 || reducedMotion)
                layers.add(layer)
                if (index > 0 && !reducedMotion) {
                    layer.animation = scope.launch {
                        layer.transition.animateTo(1f, tween(300, easing = enterEasing))
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
        if (layers.none { it.animation?.isActive == true }) finishTransitions()
    }

    fun beginBack(): PageBackGesture? {
        val stack = latestEntries.value
        if (stack.size < 2 || gesture?.finishing == true) return null
        val layer = layers.lastOrNull { it.entry.id == stack.last().id } ?: return null
        layer.animation?.cancel()
        return PageBackGesture(layer, layer.transition.value.coerceIn(0f, 1f)).also { gesture = it }
    }

    fun finishBack(active: PageBackGesture, predictive: Boolean) {
        if (active.finishing) return
        active.finishing = true
        scope.launch {
            val remaining = active.layer.transition.value.coerceIn(0f, 1f)
            val duration = if (latestReducedMotion.value) 0 else if (predictive) {
                (240 * remaining).toInt().coerceIn(110, 240)
            } else 280
            active.layer.transition.animateTo(0f, tween(duration, easing = if (predictive) exitEasing else enterEasing))
            if (latestEntries.value.lastOrNull()?.id == active.layer.entry.id) {
                if (!latestCommitBack.value(active.layer.entry)) {
                    active.layer.transition.animateTo(1f, spring(stiffness = Spring.StiffnessMediumLow))
                    finishTransitions()
                }
            }
            if (gesture === active) gesture = null
        }
    }

    val latestBeginBack = rememberUpdatedState<() -> PageBackGesture?> { beginBack() }
    val latestFinishBack = rememberUpdatedState<(PageBackGesture, Boolean) -> Unit> { active, predictive ->
        finishBack(active, predictive)
    }
    val callback = remember(controller, scope) {
        object : OnBackPressedCallback(false) {
            override fun handleOnBackStarted(backEvent: BackEventCompat) {
                if (latestPredictiveBack.value) latestBeginBack.value()
            }

            override fun handleOnBackProgressed(backEvent: BackEventCompat) {
                val active = gesture ?: return
                if (!active.finishing) scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                    active.layer.transition.snapTo(
                        (active.startValue * (1f - backEvent.progress.coerceIn(0f, 1f) * 0.92f)).coerceIn(0f, 1f)
                    )
                }
            }

            override fun handleOnBackCancelled() {
                val active = gesture ?: return
                if (!active.finishing) scope.launch {
                    active.layer.transition.animateTo(
                        1f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                    )
                    finishTransitions()
                    if (gesture === active) gesture = null
                }
            }

            override fun handleOnBackPressed() {
                val activeGesture = gesture
                val active = activeGesture ?: latestBeginBack.value() ?: return
                latestFinishBack.value(active, activeGesture != null)
            }
        }
    }
    DisposableEffect(backOwner, lifecycleOwner, callback) {
        backOwner?.onBackPressedDispatcher?.addCallback(lifecycleOwner, callback)
        onDispose { callback.remove() }
    }
    SideEffect { callback.isEnabled = entries.size > 1 }
    val requestBack = remember {
        { latestBeginBack.value()?.let { latestFinishBack.value(it, false) }; Unit }
    }
    DisposableEffect(registerBackRequest, requestBack) {
        registerBackRequest(requestBack)
        onDispose { registerBackRequest(null) }
    }

    Box(Modifier.fillMaxSize().clip(androidx.compose.ui.graphics.RectangleShape).background(readingBackground())) {
        layers.forEachIndexed { index, layer ->
            key(layer.entry.id) {
                val next = layers.getOrNull(index + 1)
                Box(Modifier.fillMaxSize().zIndex(index.toFloat()).pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent()
                    }
                }.graphicsLayer {
                    val currentProgress = layer.transition.value.coerceIn(0f, 1f)
                    val coverProgress = next?.transition?.value?.coerceIn(0f, 1f) ?: 0f
                    translationX = size.width * (1f - currentProgress - 0.28f * coverProgress)
                    alpha = 1f - 0.15f * coverProgress
                    shadowElevation = if (currentProgress > 0.001f && currentProgress < 0.999f) 16.dp.toPx() else 0f
                }) {
                    layer.entry.LocalOwnersProvider(stateHolder) { content(layer.entry) }
                }
            }
        }
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MainTabs(
    currentTabIndex: Int,
    reducedMotion: Boolean,
    historyOpenGeneration: Int,
    onNavigateToThread: (String) -> Unit,
    onColdStartContentReady: () -> Unit,
    dockSinkFraction: () -> Float = { 0f },
    drawerState: androidx.compose.material3.DrawerState,
    boardDrawerSlot: BoardDrawerSlot,
    onImageViewerOpenChange: (Boolean) -> Unit = {},
    onComposerOpenChange: (Boolean) -> Unit = {},
    onNavigateToSettings: () -> Unit,
    onNavigateToDrafts: () -> Unit,
    onNavigateToSearch: () -> Unit
) {
    val tabState = rememberSaveableStateHolder()
    val slidePx = with(LocalDensity.current) { 20.dp.roundToPx() }
    AnimatedContent(
        targetState = currentTabIndex,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            if (reducedMotion) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                val direction = if (targetState >= initialState) 1 else -1
                (fadeIn(tween(160, delayMillis = 30, easing = FastOutSlowInEasing)) +
                    slideInHorizontally(tween(160, delayMillis = 30, easing = FastOutSlowInEasing)) {
                        direction * slidePx
                    }) togetherWith fadeOut(tween(70, easing = LinearEasing))
            }
        },
        label = "home tab"
    ) { page ->
        tabState.SaveableStateProvider(page) {
        PageBackdrop {
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
                        onImageViewerOpenChange = onImageViewerOpenChange,
                        onComposerOpenChange = onComposerOpenChange
                    )
                }
                1 -> {
                    val viewModel: FeedViewModel = hiltViewModel()
                    FeedScreen(
                        viewModel = viewModel,
                        onNavigateToThread = onNavigateToThread,
                        onImageViewerOpenChange = onImageViewerOpenChange
                    )
                }
                2 -> HistoryScreen(onNavigateToThread = onNavigateToThread, onImageViewerOpenChange = onImageViewerOpenChange, openGeneration = historyOpenGeneration)
                else -> {
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
    }
}
