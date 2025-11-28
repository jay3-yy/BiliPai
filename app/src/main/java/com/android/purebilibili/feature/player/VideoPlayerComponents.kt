package com.android.purebilibili.feature.player

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.android.purebilibili.core.theme.BiliPink
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.core.util.bouncyClickable
import com.android.purebilibili.data.model.response.RelatedVideo
import com.android.purebilibili.data.model.response.ViewInfo

// 1. 视频头部信息
@Composable
fun VideoHeaderSection(info: ViewInfo) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(FormatUtils.fixImageUrl(info.owner.face))
                    .crossfade(true).build(),
                contentDescription = null,
                modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.LightGray)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = info.owner.name, style = MaterialTheme.typography.titleSmall, color = BiliPink)
                Text(text = "UP主", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
            Button(
                onClick = { },
                colors = ButtonDefaults.buttonColors(containerColor = BiliPink),
                modifier = Modifier.height(32.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) { Text("+ 关注", fontSize = 12.sp) }
        }
        Spacer(modifier = Modifier.height(12.dp))
        var expanded by remember { mutableStateOf(false) }
        Text(
            text = info.title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = if (expanded) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable { expanded = !expanded }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp), tint = Color.Gray)
            Text(" ${FormatUtils.formatStat(info.stat.view)}  ", fontSize = 12.sp, color = Color.Gray)
            Icon(Icons.Default.FormatListBulleted, null, Modifier.size(16.dp), tint = Color.Gray)
            Text(" ${FormatUtils.formatStat(info.stat.danmaku)}  ", fontSize = 12.sp, color = Color.Gray)
            Text("  ${info.bvid}", fontSize = 12.sp, color = Color.Gray)
        }
    }
}

// 2. 按钮行
@Composable
fun ActionButtonsRow(info: ViewInfo) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        ActionButton(Icons.Rounded.ThumbUp, FormatUtils.formatStat(info.stat.like))
        ActionButton(Icons.Default.MonetizationOn, "投币")
        ActionButton(Icons.Default.Star, "收藏")
        ActionButton(Icons.Default.Share, "分享")
    }
}

@Composable
fun ActionButton(icon: ImageVector, text: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(8.dp)
            .bouncyClickable { }
    ) {
        Icon(icon, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(28.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text, fontSize = 12.sp, color = Color.Gray)
    }
}

// 3. 简介
@Composable
fun DescriptionSection(desc: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).animateContentSize()) {
        if (desc.isNotBlank()) {
            Text(
                text = desc,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.DarkGray,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 20.sp
            )
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(top = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Text(text = if (expanded) "收起" else "展开更多", color = Color.Gray, fontSize = 12.sp)
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// 4. 推荐视频单项
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
            Text(text = video.owner.name, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}