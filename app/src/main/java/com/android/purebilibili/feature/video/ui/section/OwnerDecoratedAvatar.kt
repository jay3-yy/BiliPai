package com.android.purebilibili.feature.video.ui.section

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.android.purebilibili.core.ui.UserAvatarCornerMarkBadge
import com.android.purebilibili.core.ui.resolveUserAvatarCornerMark
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.data.repository.CreatorCardStats
import com.android.purebilibili.data.repository.VideoRepository

/**
 * UP 头像：头像框挂件 + 大会员/认证角标。
 * 角标数据来自已经在播详情里请求的用户卡片。
 */
@Composable
fun OwnerDecoratedAvatar(
    faceUrl: String,
    ownerMid: Long,
    modifier: Modifier = Modifier,
    badgeSize: Dp = 14.dp,
    fallbackOfficialType: Int? = null,
    fallbackVipStatus: Int? = null,
    faceModifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val displayFace = faceUrl
    var card by remember(ownerMid) { mutableStateOf(VideoRepository.getCachedCreatorCardStats(ownerMid)) }
    LaunchedEffect(ownerMid) {
        if (ownerMid <= 0L) return@LaunchedEffect
        card = VideoRepository.getCreatorCardStats(ownerMid).getOrNull()
    }
    val pendantUrl = card?.pendantImage.orEmpty()
    val hasPendant = pendantUrl.isNotBlank()
    val faceFraction = if (hasPendant) 0.72f else 1f
    val officialType = card?.officialType ?: fallbackOfficialType
    val vipStatus = card?.vipStatus ?: fallbackVipStatus
    val context = LocalContext.current
    val faceRequest = remember(context, displayFace) {
        ImageRequest.Builder(context)
            .data(FormatUtils.fixImageUrl(displayFace))
            .crossfade(false)
            .build()
    }
    val pendantRequest = remember(context, pendantUrl) {
        if (pendantUrl.isBlank()) null
        else ImageRequest.Builder(context)
            .data(FormatUtils.fixImageUrl(pendantUrl))
            .crossfade(false)
            .build()
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AsyncImage(
            model = faceRequest,
            contentDescription = contentDescription,
            modifier = faceModifier
                .fillMaxSize(faceFraction)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop,
        )
        if (pendantRequest != null) {
            AsyncImage(
                model = pendantRequest,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
        UserAvatarCornerMarkBadge(
            mark = resolveUserAvatarCornerMark(
                officialType = officialType,
                vipStatus = vipStatus,
            ),
            modifier = Modifier.align(Alignment.BottomEnd),
            badgeSize = badgeSize,
        )
    }
}
