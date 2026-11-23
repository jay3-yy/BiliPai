package com.android.purebilibili.core.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState

/**
 * 用于控制全局底栏可见性的 CompositionLocal
 * 子页面可以通过此 Local 调用函数来显式显示/隐藏底栏
 * 参数: visible (Boolean)
 */
val LocalSetBottomBarVisible = compositionLocalOf<(Boolean) -> Unit> { 
    error("No SetBottomBarVisible provided") 
}

/**
 * 用于获取当前全局底栏可见性的 CompositionLocal (可选)
 */
val LocalBottomBarVisible = compositionLocalOf<Boolean> { true }

/** Final bottom content padding resolved by the app shell for top-level pages. */
val LocalBottomBarContentPadding = compositionLocalOf<Dp> { 0.dp }

/** 评论 IP 归属地显示开关，由应用根层收集偏好，默认开启。 */
val LocalCommentIpLocationEnabled = compositionLocalOf { true }

/** 评论性别显示开关，由应用根层收集偏好，默认关闭。 */
val LocalCommentSexEnabled = compositionLocalOf { false }

/** 楼中楼预览的正文末尾显示点赞数，根层统一订阅，默认开启。 */
val LocalSubReplyPreviewLikesEnabled = compositionLocalOf { true }

/** 评论详细时间开关，由应用根层收集偏好，避免每条评论各自订阅 DataStore。 */
val LocalDetailedCommentTimeEnabled = compositionLocalOf { false }

/** 评论楼中楼预览开关，由应用根层收集偏好，避免每条评论各自订阅 DataStore。 */
val LocalCommentReplyPreviewVisible = compositionLocalOf { true }

/**
 * App-shell Haze state for the bottom bar (wallpaper + page content as source).
 * Must not be used by nodes that live *inside* that source tree (causes prepareTree SO).
 */
val LocalMainHazeState = staticCompositionLocalOf<HazeState?> { null }

/**
 * Wallpaper-only Haze source. Card badge frosted glass samples this state so effects
 * are not descendants of the bottom-bar content source (avoids HWUI stack overflow).
 */
val LocalWallpaperHazeState = staticCompositionLocalOf<HazeState?> { null }
