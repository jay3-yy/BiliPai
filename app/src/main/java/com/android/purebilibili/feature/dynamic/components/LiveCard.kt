// 文件路径: feature/dynamic/components/LiveCard.kt
package com.android.purebilibili.feature.dynamic.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.AppCard
import com.android.purebilibili.core.ui.components.AppCardDefaults
import com.android.purebilibili.core.ui.components.AppCardShape
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.PageAwareAsyncImage
import com.android.purebilibili.core.ui.components.appActionClickable
import com.android.purebilibili.core.ui.rememberAppPlayIcon
import com.android.purebilibili.data.model.response.LiveMajor
import com.android.purebilibili.data.model.response.LiveRcmdMajor
import com.android.purebilibili.feature.dynamic.model.LiveContentInfo
import com.android.purebilibili.feature.live.LiveRoomCover
import com.android.purebilibili.feature.live.rememberLiveCardContainerTransition
import kotlinx.serialization.json.Json

/**
 *  直播卡片
 */
@Composable
fun LiveCard(
    liveRcmd: LiveRcmdMajor,
    modifier: Modifier = Modifier,
    onLiveClick: (roomId: Long, title: String, uname: String) -> Unit = { _, _, _ -> }
) {
    // 解析直播内容 JSON
    val liveInfo = remember(liveRcmd.content) {
        try {
            val json = Json { ignoreUnknownKeys = true }
            json.decodeFromString<LiveContentInfo>(liveRcmd.content)
        } catch (e: Exception) {
            //  添加日志帮助调试
            Log.e("LiveCard", "Failed to parse live_rcmd content: ${e.message}")
            Log.d("LiveCard", "Raw content: ${liveRcmd.content.take(500)}")
            null
        }
    }

    if (liveInfo != null) {
        val playInfo = liveInfo.live_play_info
        DynamicLiveRoomCard(
            roomId = playInfo?.room_id ?: 0L,
            title = playInfo?.title,
            coverUrl = playInfo?.cover,
            viewerCount = playInfo?.online,
            modifier = modifier,
            onLiveClick = onLiveClick,
        )
    } else {
        // 无法解析时显示占位
        AppCard(
            modifier = modifier,
            shape = AppCardShape.Semantic(ContainerLevel.Card),
            colors = AppCardDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier.padding(AppSpacingTokens.Medium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(AppSpacingTokens.DoubleExtraLarge + AppSpacingTokens.Small)
                        .background(AppContentColors.accent(), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    AppIcon(
                        imageVector = rememberAppPlayIcon(),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(AppSpacingTokens.Large),
                    )
                }
                Spacer(modifier = Modifier.width(AppSpacingTokens.Medium))
                AppText(
                    "直播中",
                    fontSize = MaterialTheme.typography.labelMedium.fontSize,
                    fontWeight = FontWeight.Medium,
                    color = AppContentColors.primaryText()
                )
            }
        }
    }
}

@Composable
fun LiveMajorCard(
    live: LiveMajor,
    modifier: Modifier = Modifier,
    onLiveClick: (roomId: Long, title: String, uname: String) -> Unit = { _, _, _ -> }
) {
    val roomId = live.id.trim().toLongOrNull() ?: 0L
    if (roomId <= 0L) return
    DynamicLiveRoomCard(
        roomId = roomId,
        title = live.title.ifBlank { "直播间" },
        coverUrl = live.cover,
        viewerCount = null,
        modifier = modifier,
        onLiveClick = onLiveClick,
    )
}

@Composable
private fun DynamicLiveRoomCard(
    roomId: Long,
    title: String?,
    coverUrl: String?,
    viewerCount: Int?,
    modifier: Modifier,
    onLiveClick: (roomId: Long, title: String, uname: String) -> Unit,
) {
    val containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val transition = rememberLiveCardContainerTransition(
        roomId = roomId,
        title = title.orEmpty(),
        hostName = "",
        color = containerColor,
        cornerRadius = AppShapes.resolveContainerCornerDp(ContainerLevel.Card, LocalAppUiStyle.current),
        onClick = { onLiveClick(roomId, title.orEmpty(), "") },
    )
    AppCard(
        modifier = modifier
            .then(transition.modifier)
            .appActionClickable(onClick = transition.onClick),
        shape = AppCardShape.Semantic(ContainerLevel.Card),
        colors = AppCardDefaults.colors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacingTokens.Small),
            verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small),
        ) {
            if (!coverUrl.isNullOrBlank()) {
                val context = LocalContext.current
                val request = remember(context, coverUrl) {
                    val url = if (coverUrl.startsWith("http://")) {
                        coverUrl.replaceFirst("http://", "https://")
                    } else {
                        coverUrl
                    }
                    ImageRequest.Builder(context)
                        .data(url)
                        .httpHeaders(LIVE_CARD_BILIBILI_REFERER_HEADERS)
                        .crossfade(true)
                        .build()
                }
                LiveRoomCover(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(AppShapes.container(ContainerLevel.Chip))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    PageAwareAsyncImage(
                        model = request,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    DynamicLiveBadge(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(AppSpacingTokens.Small),
                    )
                }
            } else {
                DynamicLiveBadge()
            }

            Column(verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.Micro)) {
                AppText(
                    text = title ?: "直播中",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = AppContentColors.primaryText(),
                )
                if (viewerCount != null) {
                    val viewerColor = AppContentColors.secondaryText()
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.Micro),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(
                            rememberAppPlayIcon(),
                            contentDescription = null,
                            modifier = Modifier.size(AppSpacingTokens.Medium + AppSpacingTokens.Micro),
                            tint = viewerColor,
                        )
                        AppText(
                            text = "$viewerCount 人观看",
                            style = MaterialTheme.typography.labelSmall,
                            color = viewerColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DynamicLiveBadge(modifier: Modifier = Modifier) {
    AppText(
        text = "直播中",
        modifier = modifier
            .background(AppContentColors.accent(), AppShapes.container(ContainerLevel.Tag))
            .padding(horizontal = AppSpacingTokens.ExtraSmall, vertical = AppSpacingTokens.Micro),
        style = MaterialTheme.typography.labelSmall,
        color = AppContentColors.onAccent(),
        fontWeight = FontWeight.Bold,
    )
}

private val LIVE_CARD_BILIBILI_REFERER_HEADERS: NetworkHeaders =
    NetworkHeaders.Builder().set("Referer", "https://www.bilibili.com/").build()
