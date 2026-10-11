package com.mioo.dao.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.navigation.NavBackStackEntry
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import com.mioo.dao.ui.theme.DaoTheme
import com.mioo.dao.ui.theme.MiooMotion
import com.mioo.dao.ui.theme.isReducedMotionEnabled
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
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
    object Forum : Screen("forum")
    object Feed : Screen("feed")
    object Settings : Screen("settings")
    object SettingsDetail : Screen("settings_detail")
    object BrowsingHistory : Screen("browsing_history")
    object Search : Screen("search")
    object Thread : Screen("thread/{threadId}") {
        fun createRoute(threadId: String) = "thread/$threadId"
    }
}

private val TabRoutes = setOf(
    Screen.Forum.route,
    Screen.Feed.route,
    Screen.Settings.route
)

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
    val navigateToThread: (String) -> Unit = remember(navController) {
        var lastThreadAt = 0L
        { id: String ->
            if (id.isNotBlank()) {
                val now = android.os.SystemClock.uptimeMillis()
                val top = navController.currentBackStackEntry
                val topIsThread = top?.destination?.route == Screen.Thread.route
                val topId = top?.arguments?.getString("threadId")
                val repeatWhileCovered = !topIsThread && now - lastThreadAt < 400L
                val alreadyOpen = topIsThread && topId == id
                if (!repeatWhileCovered && !alreadyOpen) {
                    lastThreadAt = now
                    navController.navigate(Screen.Thread.createRoute(id)) {
                        launchSingleTop = true
                    }
                }
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(pendingThreadId) {
        val id = pendingThreadId ?: return@LaunchedEffect
        if (id.isNotBlank()) {
            navController.navigate(Screen.Thread.createRoute(id)) {
                launchSingleTop = true
            }
            onPendingThreadConsumed()
        }
    }

    // Read once per activity. Querying animator scale on every navigation janks the first frame.
    val context = LocalContext.current
    val reducedMotion = remember(context) { isReducedMotionEnabled(context) }

    // Detail routes (串、搜索、设置详情) keep one activity slide. The covered page does not move.
    // 板块 / 收藏 / 更多: adjacent pairs spring-slide; 板块 ↔ 更多 crossfades in [tabEnter] / [tabExit].
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
    val tabRisePx = with(density) { 12.dp.roundToPx() }

    val bottomBarItems = remember {
        listOf(
            Triple(Screen.Forum.route, Icons.Default.Home, "板块"),
            Triple(Screen.Feed.route, Icons.Default.Bookmark, "收藏"),
            Triple(Screen.Settings.route, Icons.Default.Settings, "更多")
        )
    }

    val showBottomBar = currentRoute in TabRoutes

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                // Outer box draws into the gesture nav area so the 小白条 is immersive;
                // capsule itself stays above the system inset.
                Box(
                    modifier = Modifier
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
                        bottomBarItems.forEach { (route, icon, label) ->
                            NavigationBarItem(
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label) },
                                selected = currentRoute == route,
                                onClick = {
                                    if (currentRoute != route) {
                                        navController.navigate(route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                }
                            )
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
            startDestination = Screen.Forum.route,
            // Intentionally no paddingValues: content scrolls behind the floating nav bar.
            enterTransition = pageEnter,
            exitTransition = pageHoldExit,
            popEnterTransition = pageRevealEnter,
            popExitTransition = pagePopExit
        ) {
            // Cold start stays put: [tabEnter] is none until another tab is involved.
            composable(
                route = Screen.Forum.route,
                enterTransition = { tabEnter(reducedMotion, tabRisePx) },
                exitTransition = { tabExit(reducedMotion, tabRisePx, popping = false) },
                popEnterTransition = { tabEnter(reducedMotion, tabRisePx) },
                popExitTransition = { tabExit(reducedMotion, tabRisePx, popping = true) }
            ) { _ ->
                PageBackdrop {
                    val viewModel: ForumViewModel = hiltViewModel()
                    ForumScreen(
                        viewModel = viewModel,
                        onNavigateToThread = navigateToThread,
                        onColdStartContentReady = onColdStartContentReady
                    )
                }
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

            composable(
                route = Screen.Feed.route,
                enterTransition = { tabEnter(reducedMotion, tabRisePx) },
                exitTransition = { tabExit(reducedMotion, tabRisePx, popping = false) },
                popEnterTransition = { tabEnter(reducedMotion, tabRisePx) },
                popExitTransition = { tabExit(reducedMotion, tabRisePx, popping = true) }
            ) { _ ->
                PageBackdrop {
                    val viewModel: FeedViewModel = hiltViewModel()
                    FeedScreen(
                        viewModel = viewModel,
                        onNavigateToThread = navigateToThread
                    )
                }
            }

            composable(
                route = Screen.Settings.route,
                enterTransition = { tabEnter(reducedMotion, tabRisePx) },
                exitTransition = { tabExit(reducedMotion, tabRisePx, popping = false) },
                popEnterTransition = { tabEnter(reducedMotion, tabRisePx) },
                popExitTransition = { tabExit(reducedMotion, tabRisePx, popping = true) }
            ) { _ ->
                PageBackdrop {
                    val viewModel: SettingsViewModel = hiltViewModel()
                    MoreScreen(
                        viewModel = viewModel,
                        onNavigateToSettings = {
                            navController.navigate(Screen.SettingsDetail.route)
                        },
                        onNavigateToHistory = {
                            navController.navigate(Screen.BrowsingHistory.route)
                        },
                        onNavigateToSearch = {
                            navController.navigate(Screen.Search.route)
                        }
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

            composable(
                route = Screen.Thread.route,
                arguments = listOf(
                    navArgument("threadId") { type = NavType.StringType }
                ),
            ) { _ ->
                PageBackdrop {
                    val viewModel: ThreadViewModel = hiltViewModel()
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

private fun tabIndex(route: String?): Int = when (route) {
    Screen.Forum.route -> 0
    Screen.Feed.route -> 1
    Screen.Settings.route -> 2
    else -> -1
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabEnter(
    reducedMotion: Boolean,
    risePx: Int
): EnterTransition {
    val from = tabIndex(initialState.destination.route)
    val to = tabIndex(targetState.destination.route)
    if (from < 0 || to < 0 || from == to) return EnterTransition.None
    // Skip 收藏. A horizontal slide here would carry the middle tab across.
    if (kotlin.math.abs(to - from) > 1) {
        return MiooMotion.tabCrossEnter(
            openingSkip = to > from,
            risePx = risePx,
            reducedMotion = reducedMotion
        )
    }
    return MiooMotion.tabSlideEnter(
        towardEnd = to > from,
        // Returning to 板块 is a pop, so the page underneath is not the top layer.
        incomingOnTop = targetState.destination.route != Screen.Forum.route,
        reducedMotion = reducedMotion
    )
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabExit(
    reducedMotion: Boolean,
    risePx: Int,
    popping: Boolean
): ExitTransition {
    val from = tabIndex(initialState.destination.route)
    val to = tabIndex(targetState.destination.route)
    if (from < 0 || to < 0 || from == to) {
        return if (popping) {
            MiooMotion.pagePopExit(reducedMotion)
        } else {
            MiooMotion.pageHoldExit(reducedMotion)
        }
    }
    if (kotlin.math.abs(to - from) > 1) {
        return MiooMotion.tabCrossExit(
            openingSkip = to > from,
            risePx = risePx,
            reducedMotion = reducedMotion
        )
    }
    return MiooMotion.tabSlideExit(
        towardEnd = to > from,
        incomingOnTop = targetState.destination.route != Screen.Forum.route,
        reducedMotion = reducedMotion
    )
}
