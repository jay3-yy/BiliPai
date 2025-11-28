package com.android.purebilibili.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.android.purebilibili.feature.home.HomeScreen
import com.android.purebilibili.feature.home.HomeViewModel
import com.android.purebilibili.feature.login.LoginScreen
import com.android.purebilibili.feature.player.VideoPlayerScreen
import com.android.purebilibili.feature.profile.ProfileScreen
import com.android.purebilibili.feature.search.SearchScreen
import com.android.purebilibili.feature.settings.SettingsScreen
import com.android.purebilibili.feature.list.CommonListScreen
import com.android.purebilibili.feature.list.HistoryViewModel
import com.android.purebilibili.feature.list.FavoriteViewModel

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController()
) {
    val homeViewModel: HomeViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = ScreenRoutes.Home.route
    ) {
        // --- 1. 首页 ---
        composable(
            route = ScreenRoutes.Home.route,
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(300)) },
            popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(300)) }
        ) {
            HomeScreen(
                viewModel = homeViewModel,
                onVideoClick = { bvid, cid ->
                    navController.navigate(ScreenRoutes.VideoPlayer.createRoute(bvid, cid))
                },
                onSearchClick = { navController.navigate(ScreenRoutes.Search.route) },
                // 🔥 修改点：区分未登录(去登录)和已登录(去个人中心)的跳转逻辑
                onAvatarClick = { navController.navigate(ScreenRoutes.Login.route) },
                onProfileClick = { navController.navigate(ScreenRoutes.Profile.route) },
                onSettingsClick = { navController.navigate(ScreenRoutes.Settings.route) }
            )
        }

        // --- 2. 个人中心 ---
        composable(
            route = ScreenRoutes.Profile.route,
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(300)) },
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(300)) },
            popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(300)) },
            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(300)) }
        ) {
            ProfileScreen(
                onBack = { navController.popBackStack() },
                onGoToLogin = { navController.navigate(ScreenRoutes.Login.route) },
                onLogoutSuccess = { homeViewModel.refresh() },
                onSettingsClick = { navController.navigate(ScreenRoutes.Settings.route) },
                onHistoryClick = { navController.navigate(ScreenRoutes.History.route) },
                onFavoriteClick = { navController.navigate(ScreenRoutes.Favorite.route) }
            )
        }

        // --- 3. 历史记录 ---
        composable(
            route = ScreenRoutes.History.route,
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(300)) },
            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(300)) }
        ) {
            val historyViewModel: HistoryViewModel = viewModel()
            CommonListScreen(
                viewModel = historyViewModel,
                onBack = { navController.popBackStack() },
                onVideoClick = { bvid, cid -> navController.navigate(ScreenRoutes.VideoPlayer.createRoute(bvid, cid)) }
            )
        }

        // --- 4. 收藏 ---
        composable(
            route = ScreenRoutes.Favorite.route,
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(300)) },
            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(300)) }
        ) {
            val favoriteViewModel: FavoriteViewModel = viewModel()
            CommonListScreen(
                viewModel = favoriteViewModel,
                onBack = { navController.popBackStack() },
                onVideoClick = { bvid, cid -> navController.navigate(ScreenRoutes.VideoPlayer.createRoute(bvid, cid)) }
            )
        }

        // --- 其他页面 ---
        composable(
            route = ScreenRoutes.Search.route,
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(300)) },
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(300)) },
            popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(300)) },
            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(300)) }
        ) {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onVideoClick = { bvid, cid -> navController.navigate(ScreenRoutes.VideoPlayer.createRoute(bvid, cid)) }
            )
        }

        composable(
            route = ScreenRoutes.VideoPlayer.route,
            arguments = listOf(
                navArgument("bvid") { type = NavType.StringType },
                navArgument("cid") { type = NavType.LongType; defaultValue = 0L }
            ),
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(300)) },
            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(300)) }
        ) { backStackEntry ->
            val bvid = backStackEntry.arguments?.getString("bvid") ?: ""
            val cid = backStackEntry.arguments?.getLong("cid") ?: 0L
            VideoPlayerScreen(bvid = bvid, cid = cid, onBack = { navController.popBackStack() })
        }

        composable(
            route = ScreenRoutes.Settings.route,
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(300)) },
            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(300)) }
        ) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = ScreenRoutes.Login.route,
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Up, tween(300)) },
            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Down, tween(300)) }
        ) {
            LoginScreen(
                onClose = { navController.popBackStack() },
                onLoginSuccess = {
                    navController.popBackStack()
                    homeViewModel.refresh()
                }
            )
        }
    }
}