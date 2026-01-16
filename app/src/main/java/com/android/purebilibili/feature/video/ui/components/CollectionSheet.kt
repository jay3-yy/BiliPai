// 文件路径: feature/video/ui/components/CollectionSheet.kt
package com.android.purebilibili.feature.video.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.core.theme.iOSBlue
import com.android.purebilibili.data.model.response.UgcEpisode
import com.android.purebilibili.data.model.response.UgcSeason
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.outlined.XmarkCircle
import io.github.alexzhirkevich.cupertino.icons.outlined.Play

/**
 *  视频合集底部弹窗
 * 显示合集中的所有视频列表
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionSheet(
    ugcSeason: UgcSeason,
    currentBvid: String,
    onDismiss: () -> Unit,
    onEpisodeClick: (UgcEpisode) -> Unit
) {
    val allEpisodes = ugcSeason.sections.flatMap { it.episodes }
    val currentIndex = allEpisodes.indexOfFirst { it.bvid == currentBvid }
    
    com.android.purebilibili.core.ui.IOSModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        windowInsets = WindowInsets(0.dp)  //  沉浸式
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)  //  确保整个区域有背景色
        ) {
            //  标题栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = ugcSeason.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "共 ${allEpisodes.size} 个视频",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                IconButton(onClick = onDismiss) {
                    Icon(
                        CupertinoIcons.Default.XmarkCircle,
                        contentDescription = "关闭",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Divider(color = MaterialTheme.colorScheme.outlineVariant)
            
            //  视频列表
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(max = 400.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                itemsIndexed(allEpisodes) { index, episode ->
                    val isCurrentEpisode = episode.bvid == currentBvid
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { 
                                if (!isCurrentEpisode) {
                                    onEpisodeClick(episode)
                                }
                            }
                            .background(
                                if (isCurrentEpisode) iOSBlue.copy(alpha = 0.1f)
                                else Color.Transparent
                            )
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        //  视频封面缩略图
                        Box(
                            modifier = Modifier
                                .width(120.dp)
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            // 封面图
                            val context = LocalContext.current
                            episode.arc?.pic?.let { pic ->
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(FormatUtils.fixImageUrl(pic))
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = episode.title,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            }
                            
                            // 时长标签
                            episode.arc?.duration?.let { duration ->
                                if (duration > 0) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(4.dp)
                                            .background(
                                                Color.Black.copy(alpha = 0.7f),
                                                RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = formatDuration(duration),
                                            fontSize = 10.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                            
                            // 正在播放覆盖层
                            if (isCurrentEpisode) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(iOSBlue.copy(alpha = 0.5f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        CupertinoIcons.Default.Play,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        //  视频信息
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = episode.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isCurrentEpisode) iOSBlue 
                                        else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isCurrentEpisode) FontWeight.SemiBold 
                                            else FontWeight.Normal,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            
                            //  正在播放标识
                            if (isCurrentEpisode) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "正在播放",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = iOSBlue,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
            
            //  内容底部间距 + 导航栏区域填充（合并为一个 Spacer，用 surface 色填充）
            val navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp + navBarHeight)  // 16dp 内容间距 + 导航栏高度
                    .background(MaterialTheme.colorScheme.surface)
            )
        }
    }
}

/**
 * 格式化时长
 */
private fun formatDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format("%02d:%02d", minutes, secs)
    }
}
