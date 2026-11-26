package com.android.purebilibili.feature.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppText

/**
 * 历史页顶部轻量入口条：将完整的「观看与阅读统计」独立至专属页面，保持历史列表清爽。
 */
@Composable
internal fun HistoryRecapEntryBanner(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = AppShapes.container(ContainerLevel.Card),
        color = AppSurfaceTokens.cardContainer(),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AppIcon(
                imageVector = Icons.Outlined.BarChart,
                contentDescription = null,
                tint = AppContentColors.accent(),
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                AppText(
                    text = "观看与阅读统计",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = AppContentColors.primaryText(),
                )
                AppText(
                    text = "查看观看时长、常看 UP 主、趋势图与完结纪念",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppContentColors.secondaryText(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            AppIcon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = AppContentColors.secondaryText().copy(alpha = 0.7f),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/**
 * 「那年今日 / 往日重温」轻量拾遗卡片，不弹窗、不红点、可随时关闭。
 */
@Composable
internal fun HistoryMemoryCard(
    pick: HistoryMemoryPick,
    onPlay: (HistoryMemoryPick) -> Unit,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    AppSurface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onPlay(pick) },
        shape = AppShapes.container(ContainerLevel.Card),
        color = AppSurfaceTokens.cardContainer(),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (pick.coverUrl.isNotBlank()) {
                AsyncImage(
                    model = pick.coverUrl,
                    contentDescription = pick.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 86.dp, height = 54.dp)
                        .aspectRatio(16f / 10f)
                        .clip(AppShapes.container(ContainerLevel.Chip)),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    AppIcon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = AppContentColors.accent(),
                        modifier = Modifier.size(14.dp),
                    )
                    AppText(
                        text = "${pick.badgeText} · ${pick.subtitle}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = AppContentColors.accent(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                AppText(
                    text = pick.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = AppContentColors.primaryText(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (pick.upName.isNotBlank()) {
                    AppText(
                        text = "UP ${pick.upName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppContentColors.secondaryText(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (onDismiss != null) {
                AppIconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp),
                ) {
                    AppIcon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "今日不再显示",
                        tint = AppContentColors.secondaryText(),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}
