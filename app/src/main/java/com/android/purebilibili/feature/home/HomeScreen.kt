package com.android.purebilibili.feature.home

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.android.purebilibili.core.theme.*
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.core.util.animateEnter
import com.android.purebilibili.core.util.bouncyClickable
import com.android.purebilibili.data.model.response.VideoItem
// 引用 SettingsScreen 中的常量
import com.android.purebilibili.feature.settings.GITHUB_URL

// 🔥 别名兼容
@Composable
fun VideoGridItem(video: VideoItem, index: Int = 0, onClick: (String, Long) -> Unit) {
    ElegantVideoCard(video = video, index = index, onClick = onClick)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    onVideoClick: (String, Long) -> Unit,
    onAvatarClick: () -> Unit,
    onProfileClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val pullRefreshState = rememberPullToRefreshState()
    val context = LocalContext.current

    // --- 首次启动检测逻辑 ---
    var showWelcomeDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        // 默认为 true (是第一次运行)
        val isFirstRun = prefs.getBoolean("is_first_run", true)
        if (isFirstRun) {
            showWelcomeDialog = true
        }
    }

    // 1. 获取系统状态栏高度
    val density = LocalDensity.current
    val statusBarHeight = WindowInsets.statusBars.getTop(density).let {
        with(density) { it.toDp() }
    }
    // 2. 计算列表顶部需要的 padding
    val topContentPadding = statusBarHeight + 54.dp + 12.dp

    if (pullRefreshState.isRefreshing) {
        LaunchedEffect(true) { viewModel.refresh() }
    }
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) pullRefreshState.startRefresh() else pullRefreshState.endRefresh()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .nestedScroll(pullRefreshState.nestedScrollConnection)
        ) {
            // --- 1. 列表层 (最底层) ---
            if (state.isLoading && state.videos.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BiliPink)
                }
            } else if (state.error != null && state.videos.isEmpty()) {
                ErrorState(state.error!!) { viewModel.refresh() }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = topContentPadding,
                        bottom = 80.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(state.videos) { index, video ->
                        ElegantVideoCard(
                            video = video,
                            index = index,
                            onClick = onVideoClick
                        )
                    }
                }
            }

            // --- 2. 刷新球 (中间层) ---
            PullToRefreshContainer(
                state = pullRefreshState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = topContentPadding - 20.dp),
                containerColor = BiliPink,
                contentColor = Color.White
            )

            // --- 3. 顶部栏 (最顶层) ---
            FloatingHomeHeader(
                user = state.user,
                onAvatarClick = {
                    if (state.user.isLogin) onProfileClick() else onAvatarClick()
                },
                onSearchClick = onSearchClick,
                onSettingsClick = onSettingsClick
            )
        }
    }

    // --- 4. 首次启动欢迎弹窗 ---
    if (showWelcomeDialog) {
        WelcomeDialog(
            githubUrl = GITHUB_URL,
            onConfirm = {
                // 用户点击确定后，记录为非首次运行
                val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("is_first_run", false).apply()
                showWelcomeDialog = false
            }
        )
    }
}

// 🔥 欢迎/声明弹窗
@Composable
fun WelcomeDialog(githubUrl: String, onConfirm: () -> Unit) {
    val uriHandler = LocalUriHandler.current

    AlertDialog(
        onDismissRequest = {}, // 禁止点击外部关闭，强制用户看一眼
        title = { Text("欢迎使用 PureBiliBili", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "本应用为开源免费项目，仅供学习 Compose 开发与交流使用。\n\n请勿用于任何商业用途。",
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(16.dp))

                // 如果 GitHub 地址不为空，显示链接
                if (githubUrl.isNotBlank()) {
                    Row(
                        modifier = Modifier.clickable { uriHandler.openUri(githubUrl) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("开源地址：", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                        Text(
                            "点击访问 GitHub 仓库",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = BiliPink,
                                textDecoration = TextDecoration.Underline
                            )
                        )
                    }
                } else {
                    // 这里的文本也更新为你的仓库地址，以防 GITHUB_URL 获取失败
                    Text("开源地址：https://github.com/jay3-yy/BiliPai/", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = BiliPink)
            ) {
                Text("我已知悉", color = Color.White)
            }
        },
        containerColor = Color.White,
        tonalElevation = 6.dp
    )
}

@Composable
fun FloatingHomeHeader(
    user: UserState,
    onAvatarClick: () -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 2.dp, spotColor = Color.Black.copy(alpha = 0.05f))
            .background(Color.White.copy(alpha = 0.98f))
            .statusBarsPadding()
            .height(54.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .border(1.dp, Color(0xFFE5E5EA), CircleShape)
                    .bouncyClickable { onAvatarClick() }
            ) {
                if (user.isLogin && user.face.isNotEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(FormatUtils.fixImageUrl(user.face)).crossfade(true).build(),
                        contentDescription = "Avatar",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(Color(0xFFE0E0E0)))
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF2F2F7))
                    .clickable { onSearchClick() }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, null, tint = Color(0xFF8E8E93), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (user.isLogin) "Hi, ${user.name}" else "搜索...",
                    color = Color(0xFF8E8E93),
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // 🔥 右上角改为设置入口
            SettingsIcon(onClick = onSettingsClick)
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(0.5.dp)
                .background(Color(0xFFC6C6C8))
        )
    }
}

// 设置图标组件
@Composable
fun SettingsIcon(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .bouncyClickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = "Settings",
            tint = Color(0xFF1C1C1E),
            modifier = Modifier.size(26.dp)
        )
    }
}

@Composable
fun ElegantVideoCard(video: VideoItem, index: Int, onClick: (String, Long) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateEnter(index, video.bvid)
            .bouncyClickable(scaleDown = 0.95f) { onClick(video.bvid, 0) },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1.6f).clip(RoundedCornerShape(12.dp))) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(FormatUtils.fixImageUrl(if (video.pic.startsWith("//")) "https:${video.pic}" else video.pic))
                        .crossfade(true).build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(Modifier.fillMaxWidth().height(48.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.5f)))))
                Text("▶ ${FormatUtils.formatStat(video.stat.view)}", color = Color.White, fontSize = 10.sp, modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
            }
            Column(Modifier.padding(10.dp)) {
                Text(
                    text = video.title,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        lineHeight = 18.sp,
                        color = TextPrimary
                    )
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(video.owner.name, fontSize = 12.sp, color = TextTertiary, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.MoreVert, null, tint = Color.LightGray, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
fun ErrorState(msg: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("加载失败: $msg", color = TextSecondary)
        Button(onClick = onRetry) { Text("重试") }
    }
}