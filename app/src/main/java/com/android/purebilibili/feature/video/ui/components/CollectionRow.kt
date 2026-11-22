// 文件路径: feature/video/ui/components/CollectionRow.kt
package com.android.purebilibili.feature.video.ui.components

import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.data.model.response.UgcSeason
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight

/**
 *  视频合集展示行
 * 显示合集名称、当前集数/总集数
 */
@Composable
fun CollectionRow(
    ugcSeason: UgcSeason,
    currentBvid: String,
    currentCid: Long = 0L,
    isPlaying: Boolean = false,
    immersive: Boolean = false,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val allEpisodes = remember(ugcSeason.sections) { ugcSeason.sections.flatMap { it.episodes } }
    // 计算当前视频在合集中的位置
    val currentIndex = resolveCurrentUgcEpisodeIndex(
        episodes = allEpisodes,
        currentBvid = currentBvid,
        currentCid = currentCid
    )
    val currentPosition = if (currentIndex >= 0) currentIndex + 1 else 0
    val totalCount = allEpisodes.size.takeIf { it > 0 } ?: ugcSeason.ep_count
    val accentColor = if (immersive) Color.White else AppContentColors.accent()
    val titleColor = if (immersive) Color.White.copy(alpha = 0.94f) else AppContentColors.primaryText()
    val secondaryColor = if (immersive) Color.White.copy(alpha = 0.78f) else AppContentColors.secondaryText()

    AppSurface(
        modifier = modifier
            .padding(horizontal = if (immersive) 0.dp else 8.dp)
            .fillMaxWidth(),
        shape = if (immersive) {
            androidx.compose.ui.graphics.RectangleShape
        } else {
            androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
        },
        // PiliPlus 同款：非沉浸时为圆角单行卡片（略浅于页面背景的容器色）
        color = if (immersive) {
            Color.Transparent
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        }
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .heightIn(min = 48.dp)
                .padding(
                    horizontal = if (immersive) 12.dp else 8.dp,
                    vertical = if (immersive) 10.dp else 0.dp,
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            //  合集信息（PiliPlus 式单行：合集：标题 …… 播放指示 n/total >）
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                AppText(
                    text = "合集：",
                    style = MaterialTheme.typography.labelMedium,
                    color = titleColor,
                    fontWeight = FontWeight.Medium
                )
                AppText(
                    text = ugcSeason.title,
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.labelMedium,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (currentPosition > 0 && totalCount > 0) {
                CollectionPlaybackIndicator(
                    isPlaying = isPlaying,
                    color = accentColor,
                )
                Spacer(modifier = Modifier.width(6.dp))
                AppText(
                    text = "$currentPosition/$totalCount",
                    style = MaterialTheme.typography.labelMedium,
                    color = secondaryColor,
                    fontWeight = FontWeight.Medium
                )
            }


            Spacer(modifier = Modifier.width(6.dp))

            //  右侧箭头
            AppIcon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = "查看合集",
                tint = secondaryColor.copy(alpha = if (immersive) 0.8f else 0.5f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/** 合集与直播头像共用同一组音柱形态和节奏。 */
@Composable
private fun CollectionPlaybackIndicator(
    isPlaying: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    com.android.purebilibili.core.ui.components.PlaybackBarsIndicator(
        isPlaying = isPlaying,
        color = color,
        contentDescription = if (isPlaying) "合集视频正在播放" else "合集视频已暂停",
        modifier = modifier,
    )
}
