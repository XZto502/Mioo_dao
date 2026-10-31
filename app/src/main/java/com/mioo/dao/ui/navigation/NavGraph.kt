package com.mioo.dao.ui.navigation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.navigation.NavBackStackEntry
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import com.mioo.dao.ui.screens.feed.FeedScreen
import com.mioo.dao.ui.screens.feed.FeedViewModel
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
    object BrowsingHistory : Screen("browsing_history")
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

    // Detail routes (串、搜索、设置详情) keep one activity slide. The covered page does not move.
    // 板块 / 收藏 / 更多 share one pager and spring across, including through the middle page.
    val pageEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        MiooMotion.pageEnter(reducedMotion)
    }
    val pageHoldExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        MiooMotion.pageHoldExit(reducedMotion)
    }
    val pageRevealEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        MiooMotion.pageRevealEnter(reducedMotion)
    }
    val pagePopExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        MiooMotion.pagePopExit(reducedMotion)
    }
    val density = LocalDensity.current
    val tabScope = rememberCoroutineScope()
    val tabPagerState = rememberPagerState(initialPage = 0) { 3 }

    val bottomBarItems = remember {
        listOf(
            Triple(0, Icons.Default.Home, "板块"),
            Triple(1, Icons.Default.Bookmark, "收藏"),
            Triple(2, Icons.Default.Settings, "更多")
        )
    }

    val showBottomBar = currentRoute == Screen.Home.route
    // 切换板块抽屉打开/正在打开时收起悬浮 dock：不遮抽屉、不拦点击
    var forumDrawerOpen by remember { mutableStateOf(false) }
    // 大图查看器打开时收起悬浮 dock：不遮查看器底部药丸、不拦点击
    var isImageViewerOpen by remember { mutableStateOf(false) }
    // Scroll the feed downward (finger up) and the tab capsule slides off the bottom.
    // A short reverse drag, or hitting the top, brings it back.
    var bottomBarVisible by remember { mutableStateOf(true) }
    var bottomBarHeightPx by remember { mutableIntStateOf(0) }
    val bottomBarSlide = remember { Animatable(0f) }
    val hideAfterPx = with(density) { 36.dp.toPx() }
    val showAfterPx = with(density) { 16.dp.toPx() }
    val bottomBarScroll = remember(hideAfterPx, showAfterPx) {
        var away = 0f
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                // Compose 1.6 only reports Drag and Fling. Both should move the bar.
                if (source != NestedScrollSource.Drag && source != NestedScrollSource.Fling) {
                    return Offset.Zero
                }
                // Overscroll at the top: the list did not consume a downward drag.
                if (available.y > 0f && consumed.y == 0f) {
                    away = 0f
                    bottomBarVisible = true
                    return Offset.Zero
                }
                val dy = consumed.y
                if (dy < 0f) {
                    if (away < 0f) away = 0f
                    away += -dy
                    if (away >= hideAfterPx) {
                        away = 0f
                        bottomBarVisible = false
                    }
                } else if (dy > 0f) {
                    if (away > 0f) away = 0f
                    away -= dy
                    if (-away >= showAfterPx) {
                        away = 0f
                        bottomBarVisible = true
                    }
                }
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(currentRoute, tabPagerState.targetPage) {
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

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                // 抽屉打开时滑出收起，关上再滑回来；slideOut 让点击目标随动画一起移走
                AnimatedVisibility(
                    visible = !forumDrawerOpen && !isImageViewerOpen,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut()
                ) {
                    // Outer box draws into the gesture nav area so the 小白条 is immersive;
                    // capsule itself stays above the system inset.
                    // offset (not graphicsLayer) moves the touch target with the slide.
                    val slideFraction = bottomBarSlide.value
                    val slidePx = bottomBarHeightPx + with(density) { 12.dp.roundToPx() }
                    Box(
                        modifier = Modifier
                            .onSizeChanged { bottomBarHeightPx = it.height }
                            .offset { IntOffset(0, (slidePx * slideFraction).toInt()) }
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        NavigationBar(
                            windowInsets = WindowInsets(0, 0, 0, 0),
                            containerColor = DaoTheme.colors.glassNavBar,
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
                                    icon = { Icon(icon, contentDescription = label) },
                                    label = { Text(label) },
                                    selected = tabPagerState.targetPage == index,
                                    onClick = {
                                        if (tabPagerState.targetPage == index) return@NavigationBarItem
                                        tabScope.launch {
                                            if (reducedMotion) {
                                                tabPagerState.scrollToPage(index)
                                            } else {
                                                tabPagerState.animateScrollToPage(
                                                    page = index,
                                                    animationSpec = MiooMotion.tabPagerSpring()
                                                )
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.Transparent
    ) { _ ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.nestedScroll(bottomBarScroll),
            // Intentionally no paddingValues: content scrolls behind the floating nav bar.
            enterTransition = pageEnter,
            exitTransition = pageHoldExit,
            popEnterTransition = pageRevealEnter,
            popExitTransition = pagePopExit
        ) {
            // Cold start stays put. Tab changes are the pager, not a NavHost transition.
            composable(
                route = Screen.Home.route,
                enterTransition = { EnterTransition.None },
                popEnterTransition = { EnterTransition.None }
            ) { _ ->
                MainTabs(
                    pagerState = tabPagerState,
                    onNavigateToThread = navigateToThread,
                    onColdStartContentReady = onColdStartContentReady,
                    dockSinkFraction = { bottomBarSlide.value },
                    onForumDrawerOpenChange = { forumDrawerOpen = it },
                    onImageViewerOpenChange = { isImageViewerOpen = it },
                    onNavigateToSettings = {
                        navController.navigate(Screen.SettingsDetail.route)
                    },
                    onNavigateToHistory = {
                        navController.navigate(Screen.BrowsingHistory.route)
                    },
                    onNavigateToDrafts = {
                        navController.navigate(Screen.DraftBox.route)
                    },
                    onNavigateToSearch = {
                        navController.navigate(Screen.Search.route)
                    }
                )
            }

            composable(route = Screen.Search.route) { _ ->
                PageBackdrop {
                    val viewModel: SearchViewModel = hiltViewModel()
                    SearchScreen(
                        viewModel = viewModel,
                        onBackClick = { navController.popBackStack() },
                        onNavigateToThread = navigateToThread
                    )
                }
            }

            composable(route = Screen.SettingsDetail.route) { _ ->
                PageBackdrop {
                    val viewModel: SettingsViewModel = hiltViewModel()
                    SettingsScreen(
                        viewModel = viewModel,
                        onBackClick = {
                            navController.popBackStack()
                        }
                    )
                }
            }

            composable(route = Screen.BrowsingHistory.route) { _ ->
                PageBackdrop {
                    val viewModel: SettingsViewModel = hiltViewModel()
                    HistoryScreen(
                        viewModel = viewModel,
                        onBackClick = {
                            navController.popBackStack()
                        },
                        onNavigateToThread = navigateToThread
                    )
                }
            }

            composable(route = Screen.DraftBox.route) { _ ->
                PageBackdrop {
                    val viewModel: com.mioo.dao.ui.screens.drafts.DraftViewModel = hiltViewModel()
                    com.mioo.dao.ui.screens.drafts.DraftBoxScreen(
                        viewModel = viewModel,
                        onBackClick = {
                            navController.popBackStack()
                        },
                        onNavigateToThread = navigateToThread,
                        onUsePostDraft = {
                            // 回到主界面并切到板块 tab，ForumScreen 会从总线取草稿打开发串框
                            navController.popBackStack()
                            tabScope.launch {
                                if (tabPagerState.currentPage != 0) {
                                    tabPagerState.scrollToPage(0)
                                }
                            }
                        }
                    )
                }
            }

            composable(
                route = Screen.Thread.route,
                arguments = listOf(
                    navArgument("threadId") { type = NavType.StringType }
                ),
            ) { entry ->
                PageBackdrop {
                    val viewModel: ThreadViewModel = hiltViewModel(entry)
                    ThreadScreen(
                        viewModel = viewModel,
                        onBackClick = { navController.popBackStack() },
                        onNavigateToThread = navigateToThread
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MainTabs(
    pagerState: PagerState,
    onNavigateToThread: (String) -> Unit,
    onColdStartContentReady: () -> Unit,
    dockSinkFraction: () -> Float = { 0f },
    onForumDrawerOpenChange: (Boolean) -> Unit = {},
    onImageViewerOpenChange: (Boolean) -> Unit = {},
    onNavigateToSettings: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToDrafts: () -> Unit,
    onNavigateToSearch: () -> Unit
) {
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        // Neighbor stays composed so a two-page jump can slide across it.
        beyondBoundsPageCount = 1,
        userScrollEnabled = false,
        key = { it }
    ) { page ->
        PageBackdrop {
            when (page) {
                0 -> {
                    val viewModel: ForumViewModel = hiltViewModel()
                    ForumScreen(
                        viewModel = viewModel,
                        onNavigateToThread = onNavigateToThread,
                        onColdStartContentReady = onColdStartContentReady,
                        dockSinkFraction = dockSinkFraction,
                        onDrawerOpenChange = onForumDrawerOpenChange,
                        onImageViewerOpenChange = onImageViewerOpenChange
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
                else -> {
                    val viewModel: SettingsViewModel = hiltViewModel()
                    MoreScreen(
                        viewModel = viewModel,
                        onNavigateToSettings = onNavigateToSettings,
                        onNavigateToHistory = onNavigateToHistory,
                        onNavigateToDrafts = onNavigateToDrafts,
                        onNavigateToSearch = onNavigateToSearch
                    )
                }
            }
        }
    }
}
