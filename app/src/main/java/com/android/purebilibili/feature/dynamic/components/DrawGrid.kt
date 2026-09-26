// 文件路径: feature/dynamic/components/DrawGrid.kt
package com.android.purebilibili.feature.dynamic.components

import coil3.network.NetworkHeaders
import coil3.network.httpHeaders

import coil3.request.crossfade
import com.android.purebilibili.core.ui.components.AppIcon

import com.android.purebilibili.core.ui.AppSpacingTokens

import com.android.purebilibili.core.ui.MediaContrastPalette
import com.android.purebilibili.core.ui.rememberAppSparklesIcon

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
//  Material Icons
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.imageLoader
import com.android.purebilibili.data.model.response.DrawItem
import com.android.purebilibili.core.ui.components.AppText

/**
 *  图片九宫格V2（支持GIF + 点击预览）
 *  🎨 [优化] 更大圆角、单图大尺寸、多图角标
 *  📍 [新增] 支持返回图片位置用于展开动画
 */
@Composable
fun DrawGridV2(
    items: List<DrawItem>,
    gifImageLoader: ImageLoader,
    maxDisplayImages: Int? = DYNAMIC_FEED_PREVIEW_MAX_IMAGES,
    onImageClick: (Int, Rect?) -> Unit = { _, _ -> }  //  [修改] 图片点击回调，新增 Rect 参数
) {
    if (items.isEmpty()) return

    val context = LocalContext.current
    val defaultImageLoader = context.imageLoader
    val totalCount = items.size  //  保存总图片数
    val displayCount = resolveDrawGridDisplayCount(
        totalImages = totalCount,
        maxDisplayImages = maxDisplayImages
    )
    val displayItems = items.take(displayCount)
    val columns = resolveDrawGridColumnCount(displayItems.size)

    val isSingleImage = displayItems.size == 1
    val gridSpacing = resolveDrawGridSpacingDp().dp
    val cornerRadius = resolveDrawGridCornerRadiusDp().dp

    Box {
        if (isSingleImage) {
            val singleItem = displayItems.first()
            DrawGridImage(
                item = singleItem,
                index = 0,
                totalCount = totalCount,
                displayCount = displayItems.size,
                modifier = Modifier
                    .fillMaxWidth(
                        resolveSingleImageWidthFraction(
                            width = singleItem.width,
                            height = singleItem.height
                        )
                    )
                    .aspectRatio(
                        resolveSingleImageAspectRatio(
                            width = singleItem.width,
                            height = singleItem.height
                        )
                    ),
                gifImageLoader = gifImageLoader,
                defaultImageLoader = defaultImageLoader,
                cornerRadius = cornerRadius,
                scaleMode = resolveDrawGridScaleMode(displayItems.size),
                onImageClick = onImageClick
            )
        } else {
            var globalIndex = 0
            Column(verticalArrangement = Arrangement.spacedBy(gridSpacing)) {
                displayItems.chunked(columns).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(gridSpacing)
                    ) {
                        row.forEach { item ->
                            val currentIndex = globalIndex++
                            DrawGridImage(
                                item = item,
                                index = currentIndex,
                                totalCount = totalCount,
                                displayCount = displayItems.size,
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f),
                                gifImageLoader = gifImageLoader,
                                defaultImageLoader = defaultImageLoader,
                                cornerRadius = cornerRadius,
                                scaleMode = resolveDrawGridScaleMode(displayItems.size),
                                onImageClick = onImageClick
                            )
                        }
                        repeat(columns - row.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawGridImage(
    item: DrawItem,
    index: Int,
    totalCount: Int,
    displayCount: Int,
    modifier: Modifier,
    gifImageLoader: ImageLoader,
    defaultImageLoader: ImageLoader,
    cornerRadius: androidx.compose.ui.unit.Dp,
    scaleMode: DrawGridScaleMode,
    onImageClick: (Int, Rect?) -> Unit
) {
    val context = LocalContext.current
    val imageUrl = remember(item.src) {
        val rawSrc = item.src.trim()
        when {
            rawSrc.startsWith("https://") -> rawSrc
            rawSrc.startsWith("http://") -> rawSrc.replace("http://", "https://")
            rawSrc.startsWith("//") -> "https:$rawSrc"
            rawSrc.isNotEmpty() -> "https://$rawSrc"
            else -> ""
        }
    }
    val isGif = imageUrl.endsWith(".gif", ignoreCase = true)
    // boundsInWindow changes on every scroll frame. Keep it outside snapshot state so
    // measuring a waterfall item never back-writes into composition and reflows the grid.
    val imageRectRef = remember { object { var value: Rect? = null } }
    // 预览打开期间隐藏原位卡片，回位落地后恢复
    val sourceHidden = isImagePreviewSourceHidden(imageRectRef.value)

    Box(
        modifier = modifier
            .alpha(if (sourceHidden) 0f else 1f)
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .onGloballyPositioned { coordinates ->
                imageRectRef.value = coordinates.boundsInWindow()
            }
            .clickable(enabled = !sourceHidden) { onImageClick(index, imageRectRef.value) },
        contentAlignment = Alignment.Center
    ) {
        if (imageUrl.isNotEmpty()) {
            AsyncImage(
                model = coil3.request.ImageRequest.Builder(context)
                    .data(imageUrl)
                    .httpHeaders(NetworkHeaders.Builder().set("Referer", "https://www.bilibili.com/").build())
                    .crossfade(!isGif)
                    .build(),
                imageLoader = if (isGif) gifImageLoader else defaultImageLoader,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = when (scaleMode) {
                    DrawGridScaleMode.FIT -> ContentScale.Fit
                    DrawGridScaleMode.CROP -> ContentScale.Crop
                }
            )
        } else {
            AppIcon(
                rememberAppSparklesIcon(),
                contentDescription = null,
                modifier = Modifier.size(AppSpacingTokens.DoubleExtraLarge),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }

        if (shouldDrawGridShowMoreBadge(index = index, displayCount = displayCount, totalCount = totalCount)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MediaContrastPalette.Scrim.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                AppText(
                    "+${totalCount - displayCount}",
                    color = MediaContrastPalette.Foreground,
                    fontSize = MaterialTheme.typography.titleLarge.fontSize,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            }
        }

        // 实况与长图徽标（Live 标记位于右上角）
        val isLive = !item.live_url.isNullOrBlank()
        if (isLive) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(AppSpacingTokens.ExtraSmall)
                    .background(
                        MediaContrastPalette.Scrim.copy(alpha = 0.6f),
                        RoundedCornerShape(AppSpacingTokens.ExtraSmall)
                    )
                    .padding(
                        horizontal = AppSpacingTokens.Small,
                        vertical = AppSpacingTokens.Micro
                    )
            ) {
                AppText(
                    "Live",
                    color = MediaContrastPalette.Foreground,
                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )
            }
        } else if (displayCount == 1 &&
            shouldShowDrawGridLongImageBadge(width = item.width, height = item.height)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(AppSpacingTokens.ExtraSmall)
                    .background(
                        MediaContrastPalette.Scrim.copy(alpha = 0.5f),
                        RoundedCornerShape(AppSpacingTokens.ExtraSmall)
                    )
                    .padding(
                        horizontal = AppSpacingTokens.ExtraSmall,
                        vertical = AppSpacingTokens.Micro
                    )
            ) {
                AppText(
                    "长图",
                    color = MediaContrastPalette.Foreground,
                    fontSize = MaterialTheme.typography.labelSmall.fontSize
                )
            }
        }
    }
}
