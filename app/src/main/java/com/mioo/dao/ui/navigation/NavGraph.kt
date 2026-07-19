package com.mioo.dao.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.navigation.NavBackStackEntry
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
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
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
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

@Composable
fun MiooDaoNavGraph(
    modifier: Modifier = Modifier,
    pendingThreadId: String? = null,
    onPendingThreadConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val coroutineScope = rememberCoroutineScope()
    val navigateToThread: (String) -> Unit = remember(navController, coroutineScope) {
        { id: String ->
            coroutineScope.launch {
                // Short delay lets the click ripple start without blocking navigation
                kotlinx.coroutines.delay(32)
                navController.navigate(Screen.Thread.createRoute(id))
            }
            Unit
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

    // Motion: ease-out curves, exit faster than enter, no ease-in.
    // Tab switches are high-frequency → short fade only. Thread stays fade+scale (no slide)
    // so first-frame HTML/image work is not fighting a horizontal transition.
    val reducedMotion = isReducedMotionEnabled()
    val fadeEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        MiooMotion.tabEnter(reducedMotion)
    }
    val fadeExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        MiooMotion.tabExit(reducedMotion)
    }
    val secondaryEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        MiooMotion.secondaryEnter(reducedMotion)
    }
    val secondaryExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        MiooMotion.secondaryExit(reducedMotion)
    }
    val threadEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        MiooMotion.threadEnter(reducedMotion)
    }
    val threadExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        MiooMotion.threadExit(reducedMotion)
    }

    val bottomBarItems = remember {
        listOf(
            Triple(Screen.Forum.route, Icons.Default.Home, "板块"),
            Triple(Screen.Feed.route, Icons.Default.Bookmark, "收藏"),
            Triple(Screen.Settings.route, Icons.Default.Settings, "更多")
        )
    }

    // Show bottom bar only on parent tab screens
    val showBottomBar = currentRoute in listOf(Screen.Forum.route, Screen.Feed.route, Screen.Settings.route)

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
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Forum.route
            // We intentionally do NOT apply paddingValues here so the content can scroll behind the floating nav bar.
            // Inner screens handle bottom padding via their LazyColumn contentPadding.
        ) {
            // Forum screen (Default start destination)
            composable(
                route = Screen.Forum.route,
                enterTransition = fadeEnter,
                exitTransition = fadeExit,
                popEnterTransition = fadeEnter,
                popExitTransition = fadeExit
            ) {
                val viewModel: ForumViewModel = hiltViewModel()
                ForumScreen(
                    viewModel = viewModel,
                    onNavigateToThread = navigateToThread
                )
            }

            composable(
                route = Screen.Search.route,
                enterTransition = secondaryEnter,
                exitTransition = secondaryExit,
                popEnterTransition = secondaryEnter,
                popExitTransition = secondaryExit
            ) {
                val viewModel: SearchViewModel = hiltViewModel()
                SearchScreen(
                    viewModel = viewModel,
                    onBackClick = { navController.popBackStack() },
                    onNavigateToThread = navigateToThread
                )
            }

            // Feed screen
            composable(
                route = Screen.Feed.route,
                enterTransition = fadeEnter,
                exitTransition = fadeExit,
                popEnterTransition = fadeEnter,
                popExitTransition = fadeExit
            ) {
                val viewModel: FeedViewModel = hiltViewModel()
                FeedScreen(
                    viewModel = viewModel,
                    onNavigateToThread = navigateToThread
                )
            }

            // More (Settings Root) screen
            composable(
                route = Screen.Settings.route,
                enterTransition = fadeEnter,
                exitTransition = fadeExit,
                popEnterTransition = fadeEnter,
                popExitTransition = fadeExit
            ) {
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

            // Settings Detail screen
            composable(
                route = Screen.SettingsDetail.route,
                enterTransition = secondaryEnter,
                exitTransition = secondaryExit,
                popEnterTransition = secondaryEnter,
                popExitTransition = secondaryExit
            ) {
                val viewModel: SettingsViewModel = hiltViewModel()
                SettingsScreen(
                    viewModel = viewModel,
                    onBackClick = {
                        navController.popBackStack()
                    }
                )
            }

            // Browsing History screen
            composable(
                route = Screen.BrowsingHistory.route,
                enterTransition = secondaryEnter,
                exitTransition = secondaryExit,
                popEnterTransition = secondaryEnter,
                popExitTransition = secondaryExit
            ) {
                val viewModel: SettingsViewModel = hiltViewModel()
                HistoryScreen(
                    viewModel = viewModel,
                    onBackClick = {
                        navController.popBackStack()
                    },
                    onNavigateToThread = navigateToThread
                )
            }

            // Thread screen
            composable(
                route = Screen.Thread.route,
                arguments = listOf(
                    navArgument("threadId") { type = NavType.StringType }
                ),
                enterTransition = threadEnter,
                exitTransition = threadExit,
                popEnterTransition = threadEnter,
                popExitTransition = threadExit
            ) {
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
