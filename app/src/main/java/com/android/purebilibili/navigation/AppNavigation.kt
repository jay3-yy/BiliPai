// 文件路径: navigation/AppNavigation.kt
package com.android.purebilibili.navigation

import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState // 🔥 新增
import androidx.compose.runtime.getValue // 🔥 新增
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
import com.android.purebilibili.feature.profile.ProfileScreen
import com.android.purebilibili.feature.search.SearchScreen
import com.android.purebilibili.feature.settings.SettingsScreen
import com.android.purebilibili.feature.list.CommonListScreen
import com.android.purebilibili.feature.list.HistoryViewModel
import com.android.purebilibili.feature.list.FavoriteViewModel
import com.android.purebilibili.feature.video.VideoDetailScreen

// 定义路由参数结构
object VideoRoute {
    const val base = "video"
    const val route = "$base/{bvid}?cid={cid}&cover={cover}"

    // 构建 helper
    fun createRoute(bvid: String, cid: Long, coverUrl: String): String {
        val encodedCover = Uri.encode(coverUrl)
        return "$base/$bvid?cid=$cid&cover=$encodedCover"
    }
}

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController()
) {
    val homeViewModel: HomeViewModel = viewModel()

    // 统一跳转逻辑
    fun navigateToVideo(bvid: String, cid: Long = 0L, coverUrl: String = "") {
        navController.navigate(VideoRoute.createRoute(bvid, cid, coverUrl))
    }

    // 动画时长
    val animDuration = 350

    NavHost(
        navController = navController,
        startDestination = ScreenRoutes.Home.route
    ) {
        // --- 1. 首页 ---
        composable(
            route = ScreenRoutes.Home.route,
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(animDuration)) },
            popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(animDuration)) }
        ) {
            HomeScreen(
                viewModel = homeViewModel,
                onVideoClick = { bvid, cid, cover -> navigateToVideo(bvid, cid, cover) },
                onSearchClick = { navController.navigate(ScreenRoutes.Search.route) },
                onAvatarClick = { navController.navigate(ScreenRoutes.Login.route) },
                onProfileClick = { navController.navigate(ScreenRoutes.Profile.route) },
                onSettingsClick = { navController.navigate(ScreenRoutes.Settings.route) }
            )
        }

        // --- 2. 视频详情页 ---
        composable(
            route = VideoRoute.route,
            arguments = listOf(
                navArgument("bvid") { type = NavType.StringType },
                navArgument("cid") { type = NavType.LongType; defaultValue = 0L },
                navArgument("cover") { type = NavType.StringType; defaultValue = "" }
            ),
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(animDuration)) },
            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(animDuration)) }
        ) { backStackEntry ->
            val bvid = backStackEntry.arguments?.getString("bvid") ?: ""
            val coverUrl = backStackEntry.arguments?.getString("cover") ?: ""

            VideoDetailScreen(
                bvid = bvid,
                coverUrl = coverUrl,
                isInPipMode = false,
                isVisible = true,
                onBack = { navController.popBackStack() }
            )
        }

        // --- 3. 个人中心 ---
        composable(
            route = ScreenRoutes.Profile.route,
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(animDuration)) },
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(animDuration)) },
            popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(animDuration)) },
            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(animDuration)) }
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

        // --- 4. 历史记录 ---
        composable(
            route = ScreenRoutes.History.route,
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(animDuration)) },
            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(animDuration)) }
        ) {
            val historyViewModel: HistoryViewModel = viewModel()
            CommonListScreen(
                viewModel = historyViewModel,
                onBack = { navController.popBackStack() },
                onVideoClick = { bvid, cid -> navigateToVideo(bvid, cid, "") }
            )
        }

        // --- 5. 收藏 ---
        composable(
            route = ScreenRoutes.Favorite.route,
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(animDuration)) },
            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(animDuration)) }
        ) {
            val favoriteViewModel: FavoriteViewModel = viewModel()
            CommonListScreen(
                viewModel = favoriteViewModel,
                onBack = { navController.popBackStack() },
                onVideoClick = { bvid, cid -> navigateToVideo(bvid, cid, "") }
            )
        }

        // --- 6. 搜索 (核心修复) ---
        composable(
            route = ScreenRoutes.Search.route,
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(animDuration)) },
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(animDuration)) },
            popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(animDuration)) },
            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(animDuration)) }
        ) {
            // 🔥 从 homeViewModel 获取最新的用户状态 (包括头像)
            val homeState by homeViewModel.uiState.collectAsState()

            SearchScreen(
                userFace = homeState.user.face, // 传入头像 URL
                onBack = { navController.popBackStack() },
                onVideoClick = { bvid, cid -> navigateToVideo(bvid, cid, "") },
                onAvatarClick = {
                    // 如果已登录 -> 去个人中心，未登录 -> 去登录页
                    if (homeState.user.isLogin) {
                        navController.navigate(ScreenRoutes.Profile.route)
                    } else {
                        navController.navigate(ScreenRoutes.Login.route)
                    }
                }
            )
        }

        // --- Settings & Login ---
        composable(
            route = ScreenRoutes.Settings.route,
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(animDuration)) },
            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(animDuration)) }
        ) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = ScreenRoutes.Login.route,
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Up, tween(animDuration)) },
            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Down, tween(animDuration)) }
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