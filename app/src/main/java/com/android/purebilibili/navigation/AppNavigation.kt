package com.android.purebilibili.navigation

import android.content.Intent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.android.purebilibili.feature.home.HomeScreen
import com.android.purebilibili.feature.home.HomeViewModel
import com.android.purebilibili.feature.login.LoginScreen
import com.android.purebilibili.feature.profile.ProfileScreen
import com.android.purebilibili.feature.search.SearchScreen
import com.android.purebilibili.feature.settings.SettingsScreen
import com.android.purebilibili.feature.list.CommonListScreen
import com.android.purebilibili.feature.list.HistoryViewModel
import com.android.purebilibili.feature.list.FavoriteViewModel
import com.android.purebilibili.feature.video.VideoActivity // 🔥 导入新的 VideoActivity

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController()
) {
    val homeViewModel: HomeViewModel = viewModel()
    val context = LocalContext.current // 🔥 获取 Context 用于启动 Activity

    // 辅助函数：统一跳转到视频播放 Activity
    fun navigateToVideo(bvid: String) {
        val intent = Intent(context, VideoActivity::class.java).apply {
            putExtra("bvid", bvid)
        }
        context.startActivity(intent)
    }

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
                // 🔥 修改点：点击视频直接跳转 Activity
                onVideoClick = { bvid, _ -> navigateToVideo(bvid) },
                onSearchClick = { navController.navigate(ScreenRoutes.Search.route) },
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
                // 🔥 修改点
                onVideoClick = { bvid, _ -> navigateToVideo(bvid) }
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
                // 🔥 修改点
                onVideoClick = { bvid, _ -> navigateToVideo(bvid) }
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
                // 🔥 修改点
                onVideoClick = { bvid, _ -> navigateToVideo(bvid) }
            )
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