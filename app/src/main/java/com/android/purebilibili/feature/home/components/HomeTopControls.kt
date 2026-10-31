package com.android.purebilibili.feature.home.components

import com.android.purebilibili.core.ui.AppContentColors

import coil3.request.crossfade

import com.android.purebilibili.core.ui.AppSpacingTokens

import com.android.purebilibili.core.ui.OpticalContrastPalette

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppPressShiftContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import com.android.purebilibili.core.ui.components.AppText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.feature.home.UserState

@Composable
internal fun HomeTopAvatarContent(
    user: UserState,
    shape: Shape,
    fallbackBackgroundColor: Color,
    modifier: Modifier = Modifier
) {
    val displayFace = user.face
    if (user.isLogin && displayFace.isNotEmpty()) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(FormatUtils.fixImageUrl(displayFace))
                .size(128, 128)
                .crossfade(true)
                .build(),
            contentDescription = "用户头像",
            contentScale = ContentScale.Crop,
            modifier = modifier
                .fillMaxSize()
                .clip(shape)
        )
    } else {
        androidx.compose.foundation.layout.Box(
            modifier = modifier
                .fillMaxSize()
                .clip(shape)
                .background(fallbackBackgroundColor),
            contentAlignment = Alignment.Center
        ) {
            AppIcon(
                imageVector = Icons.Filled.Person,
                contentDescription = if (user.isLogin) "用户头像" else "未登录，点击登录",
                tint = AppContentColors.accent(),
                modifier = Modifier.fillMaxSize(0.6f),
            )
        }
    }
}

@Composable
internal fun HomeTopSearchPillContent(
    searchIcon: ImageVector,
    contentColor: Color,
    textFontSize: TextUnit,
    iconTextGap: Dp,
    modifier: Modifier = Modifier,
    pressed: Boolean = false,
    searchIconOpticalScale: Float = 1f,
) {
    AppPressShiftContent(
        pressed = pressed,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(
                imageVector = searchIcon,
                contentDescription = "搜索",
                tint = contentColor,
                modifier = Modifier
                    .size(AppSpacingTokens.Large + AppSpacingTokens.Micro)
                    .graphicsLayer {
                        scaleX = searchIconOpticalScale
                        scaleY = searchIconOpticalScale
                    }
            )
            Spacer(modifier = Modifier.width(iconTextGap))
            AppText(
                text = "搜索视频、UP主...",
                style = MaterialTheme.typography.bodyMedium,
                fontSize = textFontSize,
                fontWeight = FontWeight.Normal,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun HomeTopUnreadBadge(
    text: String,
    layout: HomeTopRightUnreadBadgeLayout,
    borderColor: Color,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .defaultMinSize(
                minWidth = layout.minWidth,
                minHeight = layout.minHeight
            )
            .background(AppContentColors.error(), CircleShape)
            .border(width = AppSpacingTokens.Micro / 2, color = borderColor, shape = CircleShape)
            .padding(
                horizontal = layout.horizontalPadding,
                vertical = layout.verticalPadding
            ),
        contentAlignment = Alignment.Center
    ) {
        AppText(
            text = text,
            color = OpticalContrastPalette.Highlight,
            fontSize = MaterialTheme.typography.labelSmall.fontSize,
            lineHeight = MaterialTheme.typography.labelSmall.lineHeight,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}
