package com.android.purebilibili.feature.video

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.* // 🔥 使用 Outlined 图标
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.android.purebilibili.core.theme.BiliPink
import com.android.purebilibili.core.theme.TextPrimary
import com.android.purebilibili.core.theme.TextSecondary
import com.android.purebilibili.core.theme.TextTertiary
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.core.util.bouncyClickable
import com.android.purebilibili.data.model.response.RelatedVideo
import com.android.purebilibili.data.model.response.ViewInfo

// 1. 视频头部信息 (美化关注按钮和字体)
@Composable
fun VideoHeaderSection(info: ViewInfo) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(FormatUtils.fixImageUrl(info.owner.face))
                    .crossfade(true).build(),
                contentDescription = null,
                modifier = Modifier.size(42.dp).clip(CircleShape).background(Color(0xFFF0F0F0))
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "UP主",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
            }

            // 🔥 美化：关注按钮
            Surface(
                onClick = { },
                color = BiliPink,
                shape = RoundedCornerShape(16.dp), // 更圆润
                modifier = Modifier.height(30.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp)
                ) {
                    Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("关注", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Medium)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 标题
        var expanded by remember { mutableStateOf(false) }
        Text(
            text = info.title,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 24.sp),
            maxLines = if (expanded) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis,
            color = TextPrimary,
            modifier = Modifier.clickable { expanded = !expanded }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 数据栏 (图标更小，颜色更淡)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.PlayCircle, null, Modifier.size(15.dp), tint = TextTertiary)
            Text(" ${FormatUtils.formatStat(info.stat.view.toLong())}  ", fontSize = 12.sp, color = TextTertiary)

            Icon(Icons.Outlined.Subject, null, Modifier.size(15.dp), tint = TextTertiary)
            Text(" ${FormatUtils.formatStat(info.stat.danmaku.toLong())}  ", fontSize = 12.sp, color = TextTertiary)

            Text("  ${info.bvid}", fontSize = 12.sp, color = TextTertiary)
        }
    }
}

// 2. 按钮行 (美化：使用 Outlined 图标，布局更均匀)
@Composable
fun ActionButtonsRow(info: ViewInfo, onCommentClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceAround // 🔥 均匀分布
    ) {
        // 使用 Outlined 图标
        ActionButton(Icons.Outlined.ThumbUp, FormatUtils.formatStat(info.stat.like.toLong()))
        // MonetizationOn 没有 Outlined 版，暂时用实心的，或者替换为其他图标
        ActionButton(Icons.Default.MonetizationOn, "投币")
        ActionButton(Icons.Outlined.Star, "收藏")
        ActionButton(Icons.Outlined.Share, "分享")

        // 评论按钮
        val replyCount = runCatching { info.stat.reply }.getOrDefault(0)
        ActionButton(
            icon = Icons.Outlined.Comment,
            text = if (replyCount > 0) FormatUtils.formatStat(replyCount.toLong()) else "评论",
            onClick = onCommentClick
        )
    }
}

@Composable
fun ActionButton(icon: ImageVector, text: String, isActive: Boolean = false, onClick: () -> Unit = {}) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(vertical = 8.dp)
            .bouncyClickable { onClick() }
            .width(56.dp) // 固定点击区域宽度
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isActive) BiliPink else Color(0xFF616161), // 🔥 颜色加深一点点，非纯灰
            modifier = Modifier.size(26.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(text, fontSize = 11.sp, color = if (isActive) BiliPink else TextSecondary, fontWeight = FontWeight.Medium)
    }
}

// 3. 简介 (保持不变)
@Composable
fun DescriptionSection(desc: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).animateContentSize()) {
        if (desc.isNotBlank()) {
            Text(
                text = desc,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 20.sp
            )
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(top = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Text(text = if (expanded) "收起" else "展开更多", color = TextTertiary, fontSize = 12.sp)
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// 4. 推荐视频单项 (保持不变)
@Composable
fun RelatedVideoItem(video: RelatedVideo, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Box(modifier = Modifier.width(140.dp).height(88.dp).clip(RoundedCornerShape(6.dp))) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(FormatUtils.fixImageUrl(video.pic))
                    .crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Text(
                text = FormatUtils.formatDuration(video.duration),
                color = Color.White,
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
                    .padding(horizontal = 2.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f).height(88.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(text = video.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(text = video.owner.name, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
        }
    }
}