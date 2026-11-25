// 文件路径: feature/bangumi/ui/player/BangumiCollapsedPlayerBar.kt
package com.android.purebilibili.feature.bangumi.ui.player

import com.android.purebilibili.core.ui.AppContentColors

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppText

/**
 * 播放器折叠导航栏 (对齐 PiliPlus 的下滑收起播放器交互)
 *
 * 当用户下滑列表时，播放器向上收起至 56dp 紧凑顶栏。
 * 包含：返回按钮、中间的「继续播放/立即播放」快捷操作。
 * 点击整栏可展开并恢复播放。
 */
@Composable
fun BangumiCollapsedPlayerBar(
    scrollRatio: Float,
    topInset: Dp,
    isPlaying: Boolean,
    isCompleted: Boolean = false,
    hasPlayed: Boolean = true,
    onBack: () -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
    scrollRatioProvider: (() -> Float)? = null,
) {
    if (scrollRatioProvider == null && scrollRatio <= 0f) return

    Box(
        modifier = modifier
            .drawBehind {
                val ratio = (scrollRatioProvider?.invoke() ?: scrollRatio).coerceIn(0f, 1f)
                if (ratio > 0f) {
                    drawRect(
                        color = Color.Black,
                        alpha = (ratio * 0.85f).coerceIn(0f, 1f),
                    )
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val ratio = (scrollRatioProvider?.invoke() ?: scrollRatio).coerceIn(0f, 1f)
                    alpha = ((ratio - 0.2f) / 0.8f).coerceIn(0f, 1f)
                }
        ) {
            if (topInset > 0.dp) {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(topInset)
                        .background(MaterialTheme.colorScheme.surface)
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable(onClick = onPlayClick),
            ) {
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIconButton(
                        onClick = onBack,
                        modifier = Modifier.size(48.dp)
                    ) {
                        AppIcon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = AppContentColors.primaryText(),
                        )
                    }
                }

                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = AppContentColors.accent(),
                        modifier = Modifier.size(22.dp)
                    )
                    val playLabel = when {
                        !hasPlayed -> "立即播放"
                        isCompleted -> "重新播放"
                        isPlaying -> "暂停播放"
                        else -> "继续播放"
                    }
                    AppText(
                        text = playLabel,
                        color = AppContentColors.accent(),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}
