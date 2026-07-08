package com.mioo.dao.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import com.mioo.dao.ui.theme.DaoTheme
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
    object Thread : Screen("thread/{threadId}") {
        fun createRoute(threadId: String) = "thread/$threadId"
    }
}

@Composable
fun MiooDaoNavGraph(
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val bottomBarItems = listOf(
        Triple(Screen.Forum.route, Icons.Default.Home, "板块"),
        Triple(Screen.Feed.route, Icons.Default.Bookmark, "收藏"),
        Triple(Screen.Settings.route, Icons.Default.Settings, "更多")
    )

    // Show bottom bar only on parent tab screens
    val showBottomBar = currentRoute in listOf(Screen.Forum.route, Screen.Feed.route, Screen.Settings.route)

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
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
            composable(Screen.Forum.route) {
                val viewModel: ForumViewModel = hiltViewModel()
                ForumScreen(
                    viewModel = viewModel,
                    onNavigateToThread = { id ->
                        navController.navigate(Screen.Thread.createRoute(id))
                    }
                )
            }

            // Feed screen
            composable(Screen.Feed.route) {
                val viewModel: FeedViewModel = hiltViewModel()
                FeedScreen(
                    viewModel = viewModel,
                    onNavigateToThread = { id ->
                        navController.navigate(Screen.Thread.createRoute(id))
                    }
                )
            }

            // More (Settings Root) screen
            composable(Screen.Settings.route) {
                val viewModel: SettingsViewModel = hiltViewModel()
                MoreScreen(
                    viewModel = viewModel,
                    onNavigateToSettings = {
                        navController.navigate(Screen.SettingsDetail.route)
                    },
                    onNavigateToHistory = {
                        navController.navigate(Screen.BrowsingHistory.route)
                    }
                )
            }

            // Settings Detail screen
            composable(
                route = Screen.SettingsDetail.route,
                enterTransition = {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec = tween(300)
                    )
                },
                exitTransition = {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec = tween(300)
                    )
                },
                popEnterTransition = {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = tween(300)
                    )
                },
                popExitTransition = {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = tween(300)
                    )
                }
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
                enterTransition = {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec = tween(300)
                    )
                },
                exitTransition = {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec = tween(300)
                    )
                },
                popEnterTransition = {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = tween(300)
                    )
                },
                popExitTransition = {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = tween(300)
                    )
                }
            ) {
                val viewModel: SettingsViewModel = hiltViewModel()
                HistoryScreen(
                    viewModel = viewModel,
                    onBackClick = {
                        navController.popBackStack()
                    },
                    onNavigateToThread = { id ->
                        navController.navigate(Screen.Thread.createRoute(id))
                    }
                )
            }

            // Thread screen
            composable(
                route = Screen.Thread.route,
                arguments = listOf(
                    navArgument("threadId") { type = NavType.StringType }
                ),
                enterTransition = {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec = tween(300)
                    )
                },
                exitTransition = {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec = tween(300)
                    )
                },
                popEnterTransition = {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = tween(300)
                    )
                },
                popExitTransition = {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = tween(300)
                    )
                }
            ) {
                val viewModel: ThreadViewModel = hiltViewModel()
                ThreadScreen(
                    viewModel = viewModel,
                    onBackClick = { navController.popBackStack() },
                    onNavigateToThread = { id ->
                        navController.navigate(Screen.Thread.createRoute(id))
                    }
                )
            }
        }
    }
}
