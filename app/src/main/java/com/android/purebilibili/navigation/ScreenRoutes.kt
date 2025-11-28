package com.android.purebilibili.navigation

sealed class ScreenRoutes(val route: String) {
    object Home : ScreenRoutes("home")
    object Search : ScreenRoutes("search")
    object Settings : ScreenRoutes("settings")
    object Login : ScreenRoutes("login")
    object Profile : ScreenRoutes("profile")

    // 🔥 新增路由：历史记录和收藏
    object History : ScreenRoutes("history")
    object Favorite : ScreenRoutes("favorite")

    object VideoPlayer : ScreenRoutes("video_player/{bvid}?cid={cid}") {
        fun createRoute(bvid: String, cid: Long = 0): String {
            return "video_player/$bvid?cid=$cid"
        }
    }
}