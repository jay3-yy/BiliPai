// 文件路径: feature/video/VideoDetailScreen.kt
package com.android.purebilibili.feature.video

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.View
import android.view.Window
import android.view.WindowManager // 🔥 新增导入
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.purebilibili.core.theme.BiliPink
import com.android.purebilibili.data.model.response.RelatedVideo
import com.android.purebilibili.data.model.response.ReplyItem
import com.android.purebilibili.data.model.response.ViewInfo
import kotlinx.coroutines.launch

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun VideoDetailScreen(
    bvid: String,
    coverUrl: String,
    onBack: () -> Unit,
    isInPipMode: Boolean = false,
    isVisible: Boolean = true,
    viewModel: PlayerViewModel = viewModel()
) {
    val context = LocalContext.current
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    val uiState by viewModel.uiState.collectAsState()

    // 1. 直接通过系统配置判断是否全屏
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // 画中画状态
    var isPipMode by remember { mutableStateOf(isInPipMode) }
    LaunchedEffect(isInPipMode) { isPipMode = isInPipMode }

    // 🔥🔥🔥【关键修复】退出页面时重置亮度
    // 解决回到首页后提示“亮度被顶层应用控制”的问题
    DisposableEffect(Unit) {
        onDispose {
            val window = context.findActivity()?.window
            val layoutParams = window?.attributes
            // 强制重置为系统默认 (-1f)
            layoutParams?.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            window?.attributes = layoutParams
        }
    }

    val playerState = rememberVideoPlayerState(
        context = context,
        viewModel = viewModel,
        bvid = bvid
    )

    // 2. 辅助函数：切换屏幕方向
    fun toggleOrientation() {
        val activity = context.findActivity() ?: return
        if (isLandscape) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    // 3. 沉浸式状态栏控制
    val backgroundColor = MaterialTheme.colorScheme.background
    val isLightBackground = remember(backgroundColor) { backgroundColor.luminance() > 0.5f }

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context.findActivity())?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)

            if (isLandscape) {
                // 横屏：隐藏状态栏和导航栏，黑色背景
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                window.statusBarColor = Color.Black.toArgb()
                window.navigationBarColor = Color.Black.toArgb()
            } else {
                // 竖屏：显示状态栏，恢复原来的颜色
                insetsController.show(WindowInsetsCompat.Type.systemBars())
                insetsController.isAppearanceLightStatusBars = isLightBackground
                window.statusBarColor = Color.Transparent.toArgb()
                window.navigationBarColor = Color.Transparent.toArgb()
            }
        }
    }

    // 4. 界面布局
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isLandscape) Color.Black else MaterialTheme.colorScheme.background)
    ) {
        if (isLandscape) {
            // === 横屏全屏模式 ===
            VideoPlayerSection(
                playerState = playerState,
                uiState = uiState,
                isFullscreen = true,
                isInPipMode = isPipMode,
                onToggleFullscreen = { toggleOrientation() },
                onQualityChange = { qid, pos -> viewModel.changeQuality(qid, pos) },
                onBack = { toggleOrientation() }
            )
        } else {
            // === 竖屏普通模式 ===
            Column(modifier = Modifier.fillMaxSize()) {
                // 播放器容器 (16:9)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black)
                ) {
                    VideoPlayerSection(
                        playerState = playerState,
                        uiState = uiState,
                        isFullscreen = false,
                        isInPipMode = isPipMode,
                        onToggleFullscreen = { toggleOrientation() },
                        onQualityChange = { qid, pos -> viewModel.changeQuality(qid, pos) },
                        onBack = onBack
                    )
                }

                // 下方内容区域
                when (uiState) {
                    is PlayerUiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = BiliPink)
                        }
                    }

                    is PlayerUiState.Success -> {
                        val success = uiState as PlayerUiState.Success
                        VideoContentSection(
                            info = success.info,
                            relatedVideos = success.related,
                            replies = success.replies,
                            replyCount = success.replyCount,
                            emoteMap = success.emoteMap,
                            isRepliesLoading = success.isRepliesLoading,
                            onRelatedVideoClick = { vid -> viewModel.loadVideo(vid) }
                        )
                    }

                    is PlayerUiState.Error -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text((uiState as PlayerUiState.Error).msg)
                                Spacer(Modifier.height(16.dp))
                                Button(
                                    onClick = { viewModel.loadVideo(bvid) },
                                    colors = ButtonDefaults.buttonColors(containerColor = BiliPink)
                                ) { Text("重试") }
                            }
                        }
                    }
                }
            }
        }
    }
}

// 扩展函数：查找 Context 对应的 Activity
private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

@Composable
fun VideoContentSection(
    info: ViewInfo,
    relatedVideos: List<RelatedVideo>,
    replies: List<ReplyItem>,
    replyCount: Int,
    emoteMap: Map<String, String>,
    isRepliesLoading: Boolean,
    onRelatedVideoClick: (String) -> Unit
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val commentHeaderIndex = 6 + relatedVideos.size + 1

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item { VideoHeaderSection(info = info) }

        item {
            ActionButtonsRow(
                info = info,
                onCommentClick = {
                    coroutineScope.launch {
                        listState.animateScrollToItem(commentHeaderIndex)
                    }
                }
            )
        }

        item {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )
        }

        item { DescriptionSection(desc = info.desc) }

        item {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(thickness = 8.dp, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        }

        item { RelatedVideosHeader() }

        items(relatedVideos, key = { it.bvid }) { video ->
            RelatedVideoItem(video = video, onClick = { onRelatedVideoClick(video.bvid) })
        }

        item {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(thickness = 8.dp, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        }

        item {
            ReplyHeader(count = replyCount)
        }

        if (replies.isEmpty() && replyCount > 0 && isRepliesLoading) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(64.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BiliPink)
                }
            }
        } else {
            items(replies, key = { it.rpid }) { reply ->
                ReplyItemView(
                    item = reply,
                    emoteMap = emoteMap,
                    onClick = { },
                    onSubClick = { }
                )
            }

            if (replies.size < replyCount) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text("加载更多...", color = BiliPink)
                    }
                }
            }
        }
    }
}