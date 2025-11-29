package com.android.purebilibili.feature.home

import androidx.compose.foundation.background
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

// 🔥 1. 优雅卡片 (双列) - 优化版
@Composable
fun ElegantVideoCard(video: VideoItem, index: Int, onClick: (String, Long) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateEnter(index, video.bvid)
            // 优化阴影：更淡、更散
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(12.dp),
                spotColor = Color.Black.copy(0.06f),
                ambientColor = Color.Black.copy(0.03f)
            )
            .bouncyClickable(scaleDown = 0.97f) { onClick(video.bvid, 0) },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(0.dp) // 禁用默认阴影，使用 shadow Modifier
    ) {
        Column {
            // 封面区
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.65f) // 16:10 黄金比例
                    .clip(RoundedCornerShape(12.dp))
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(FormatUtils.fixImageUrl(if (video.pic.startsWith("//")) "https:${video.pic}" else video.pic))
                        .crossfade(true).build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // 渐变遮罩 (更自然)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.5f))))
                )

                // 播放数据
                Row(
                    modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("▶ ${FormatUtils.formatStat(video.stat.view.toLong())}", color = Color.White.copy(0.9f), fontSize = 10.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(FormatUtils.formatDuration(video.duration), color = Color.White.copy(0.9f), fontSize = 10.sp)
                }
            }

            // 内容区
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = video.title,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.5.sp,
                        lineHeight = 19.sp,
                        color = TextPrimary // 使用 Theme 中定义的深色
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = video.owner.name,
                        fontSize = 11.sp,
                        color = TextTertiary, // 使用浅灰
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                    Icon(Icons.Default.MoreVert, null, tint = TextTertiary.copy(0.5f), modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

// 🔥 2. 沉浸卡片 (单列) - 优化版
@Composable
fun ImmersiveVideoCard(video: VideoItem, index: Int, onClick: (String, Long) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateEnter(index, video.bvid)
            .shadow(8.dp, RoundedCornerShape(16.dp), spotColor = Color.Black.copy(0.08f))
            .bouncyClickable(scaleDown = 0.98f) { onClick(video.bvid, 0) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
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
                // 时长胶囊
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(FormatUtils.formatDuration(video.duration), color = Color.White, fontSize = 11.sp)
                }
            }

            Row(modifier = Modifier.padding(12.dp)) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(FormatUtils.fixImageUrl(video.owner.face)).crossfade(true).build(),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFF0F0F0))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = video.title,
                        maxLines = 2,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${video.owner.name} · ${FormatUtils.formatStat(video.stat.view.toLong())}播放",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

// 🔥 3. 悬浮顶部栏
@Composable
fun FloatingHomeHeader(user: UserState, onAvatarClick: () -> Unit, onSearchClick: () -> Unit, onSettingsClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(56.dp) // 降低高度，更精致
    ) {
        // 毛玻璃效果模拟 (半透明白底 + 阴影)
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(28.dp),
            color = Color.White.copy(alpha = 0.92f), // 稍微透明一点
            shadowElevation = 3.dp,
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 头像
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .clickable { onAvatarClick() }
                ) {
                    if (user.isLogin && user.face.isNotEmpty()) {
                        AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(FormatUtils.fixImageUrl(user.face)).crossfade(true).build(), contentDescription = "Avatar", modifier = Modifier.fillMaxSize())
                    } else {
                        Box(Modifier.fillMaxSize().background(Color(0xFFE0E0E0)), contentAlignment = Alignment.Center) { Text("未", fontSize = 10.sp, color = Color.Gray) }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // 搜索框
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFF2F3F5)) // 更淡的灰
                        .clickable { onSearchClick() }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, null, tint = Color(0xFFA0A4A9), modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = if (user.isLogin) "Hi, ${user.name}" else "搜索...", color = Color(0xFFA0A4A9), fontSize = 13.sp, maxLines = 1)
                }

                Spacer(modifier = Modifier.width(12.dp))

                // 设置
                IconButton(onClick = onSettingsClick, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color(0xFF757575), modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

// 🔥 4. 其他组件
@Composable
fun ErrorState(msg: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("加载失败", style = MaterialTheme.typography.titleMedium)
        Text(msg, color = TextSecondary, fontSize = 12.sp)
        Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = BiliPink)) { Text("重试") }
    }
}

@Composable
fun WelcomeDialog(githubUrl: String, onConfirm: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = {},
        title = { Text("欢迎", fontWeight = FontWeight.Bold) },
        text = { Text("本应用仅供学习使用。", style = MaterialTheme.typography.bodyMedium) },
        confirmButton = { Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = BiliPink)) { Text("好的") } },
        containerColor = Color.White
    )
}

// 🔥 5. [新增] 修复报错：通用 VideoGridItem
// SearchScreen 和 CommonListScreen 会调用这个函数
@Composable
fun VideoGridItem(video: VideoItem, index: Int, onClick: (String, Long) -> Unit) {
    // 默认使用双列优雅卡片
    ElegantVideoCard(video = video, index = index, onClick = onClick)
}