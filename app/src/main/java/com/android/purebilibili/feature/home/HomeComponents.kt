// 文件路径: feature/home/HomeComponents.kt
package com.android.purebilibili.feature.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.android.purebilibili.core.theme.*
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.core.util.animateEnter
import com.android.purebilibili.core.util.bouncyClickable
import com.android.purebilibili.data.model.response.VideoItem

// --- 卡片组件 (保持不变) ---
@Composable
fun ElegantVideoCard(video: VideoItem, index: Int, onClick: (String, Long) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateEnter(index, video.bvid)
            .shadow(4.dp, RoundedCornerShape(12.dp), spotColor = MaterialTheme.colorScheme.onSurface.copy(0.06f))
            .bouncyClickable(scaleDown = 0.97f) { onClick(video.bvid, 0) },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1.65f).clip(RoundedCornerShape(12.dp))) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(FormatUtils.fixImageUrl(if (video.pic.startsWith("//")) "https:${video.pic}" else video.pic))
                        .crossfade(true).build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(modifier = Modifier.fillMaxWidth().height(48.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.5f)))))
                Row(modifier = Modifier.align(Alignment.BottomStart).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("▶ ${FormatUtils.formatStat(video.stat.view.toLong())}", color = Color.White.copy(0.9f), fontSize = 10.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(FormatUtils.formatDuration(video.duration), color = Color.White.copy(0.9f), fontSize = 10.sp)
                }
            }
            Column(modifier = Modifier.padding(10.dp)) {
                Text(text = video.title, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium, fontSize = 13.5.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurface))
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = video.owner.name, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1)
                    Icon(Icons.Default.MoreVert, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f), modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
fun ImmersiveVideoCard(video: VideoItem, index: Int, onClick: (String, Long) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateEnter(index, video.bvid)
            .shadow(6.dp, RoundedCornerShape(16.dp), spotColor = MaterialTheme.colorScheme.onSurface.copy(0.08f))
            .bouncyClickable(scaleDown = 0.98f) { onClick(video.bvid, 0) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1.77f)) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(FormatUtils.fixImageUrl(if (video.pic.startsWith("//")) "https:${video.pic}" else video.pic))
                        .crossfade(true).build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).background(Color.Black.copy(0.5f), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 2.dp)) {
                    Text(FormatUtils.formatDuration(video.duration), color = Color.White, fontSize = 11.sp)
                }
            }
            Row(modifier = Modifier.padding(12.dp)) {
                AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(FormatUtils.fixImageUrl(video.owner.face)).crossfade(true).build(), contentDescription = null, modifier = Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant))
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(text = video.title, maxLines = 2, style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "${video.owner.name} · ${FormatUtils.formatStat(video.stat.view.toLong())}播放", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// 🔥🔥🔥 全新设计的 HomeTopBar - 集成搜索栏（优化防闪烁）
@Composable
fun HomeTopBar(
    user: UserState,
    isScrolled: Boolean,
    onAvatarClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit
) {
    // 动态背景色 - 未滚动时使用半透明surface，滚动后显示完全不透明
    val containerColor by animateColorAsState(
        targetValue = if (isScrolled)
            MaterialTheme.colorScheme.surface
        else
            MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        animationSpec = tween(durationMillis = 300),
        label = "TopBarBg"
    )

    // 动态阴影 - 使用更平滑的过渡
    val elevation by animateDpAsState(
        targetValue = if (isScrolled) 4.dp else 0.dp,
        animationSpec = tween(durationMillis = 300),
        label = "TopBarElevation"
    )

    Surface(
        color = containerColor,
        shadowElevation = elevation,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // 状态栏占位
            Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

            // 顶部栏内容区域
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // 第一行：头像、标题、设置
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 左侧：头像
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                if (user.isLogin)
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                                else
                                    Color.LightGray.copy(0.5f)
                            )
                            .clickable { onAvatarClick() }
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(0.2f), CircleShape)
                    ) {
                        if (user.isLogin && user.face.isNotEmpty()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(FormatUtils.fixImageUrl(user.face))
                                    .crossfade(true).build(),
                                contentDescription = "Avatar",
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("未", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // 中间：标题
                    Text(
                        text = "BiliPai",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.5).sp,
                            fontSize = 22.sp
                        ),
                        color = BiliPink
                    )

                    // 右侧：设置按钮
                    IconButton(
                        onClick = onSettingsClick,
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 第二行：搜索栏（优化阴影效果）
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .shadow(
                            elevation = 3.dp,
                            shape = RoundedCornerShape(24.dp),
                            spotColor = Color.Black.copy(0.08f)
                        )
                        .clip(RoundedCornerShape(24.dp))
                        .clickable { onSearchClick() },
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                    tonalElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = BiliPink,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "搜索视频、UP主...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun ErrorState(msg: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "加载失败",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            msg,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = BiliPink)
        ) {
            Text("重试")
        }
    }
}

@Composable
fun WelcomeDialog(githubUrl: String, onConfirm: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = {},
        title = { Text("欢迎") },
        text = {
            Column {
                Text("本应用仅供学习使用。")
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { uriHandler.openUri(githubUrl) }) {
                    Text(
                        "开源地址: $githubUrl",
                        fontSize = 12.sp,
                        color = BiliPink
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = BiliPink)
            ) {
                Text("好的")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun VideoGridItem(video: VideoItem, index: Int, onClick: (String, Long) -> Unit) {
    ElegantVideoCard(video = video, index = index, onClick = onClick)
}