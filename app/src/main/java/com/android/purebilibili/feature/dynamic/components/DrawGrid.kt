// 文件路径: feature/dynamic/components/DrawGrid.kt
package com.android.purebilibili.feature.dynamic.components

import com.android.purebilibili.core.ui.AppContentColors

import coil3.network.NetworkHeaders
import coil3.network.httpHeaders

import coil3.request.crossfade
import com.android.purebilibili.core.ui.components.AppIcon

import com.android.purebilibili.core.ui.AppSpacingTokens

import com.android.purebilibili.core.ui.MediaContrastPalette
import com.android.purebilibili.core.ui.rememberAppSparklesIcon

import androidx.compose.foundation.background
import com.android.purebilibili.core.ui.components.appActionClickable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import com.android.purebilibili.core.ui.components.PageAwareAsyncImage
import coil3.imageLoader
import com.android.purebilibili.data.model.response.DrawItem
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.R

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
    onImageClick: (Int, Rect?) -> Unit = { _, _ -> },
    onImagePreviewClick: ((Int, ImagePreviewSourceAnchor?) -> Unit)? = null,
) {
    if (items.isEmpty()) return

    val context = LocalContext.current
    val defaultImageLoader = context.imageLoader
    // Plain map: bounds update while feed cards scroll and must not invalidate the grid.
    val galleryRects = remember(items) { mutableMapOf<Int, Rect>() }
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
                galleryRects = galleryRects,
                onImageClick = onImageClick,
                onImagePreviewClick = onImagePreviewClick,
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
                                galleryRects = galleryRects,
                                onImageClick = onImageClick,
                                onImagePreviewClick = onImagePreviewClick,
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
    galleryRects: MutableMap<Int, Rect>,
    onImageClick: (Int, Rect?) -> Unit,
    onImagePreviewClick: ((Int, ImagePreviewSourceAnchor?) -> Unit)?,
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
    // 预览打开期间隐藏原位卡片，回位落地后恢复。
    // 优先按图片 URL 身份匹配，几何判定仅作无身份键入口的回退，
    // 避免 Dialog 窗口/滚动容器坐标差异导致的"原图残留"。
    val sourceHidden = isImagePreviewSourceHidden(imageRectRef.value, imageUrl)

    Box(
        modifier = modifier
            .alpha(if (sourceHidden) 0f else 1f)
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .onGloballyPositioned { coordinates ->
                imageRectRef.value = coordinates.boundsInWindow()
                galleryRects[index] = imageRectRef.value!!
            }
            .appActionClickable(
                interactionSource = null,
                indication = null,
                enabled = !sourceHidden,
            ) {
                val rect = imageRectRef.value
                val anchor = rect?.let {
                    ImagePreviewSourceAnchor(
                        rect = it,
                        cornerRadiusDp = cornerRadius.value,
                        galleryRects = galleryRects.toMap(),
                        sourceKey = imageUrl,
                    )
                }
                if (onImagePreviewClick != null) {
                    prepareImagePreviewSourceTransition(anchor?.rect, imageUrl)
                }
                onImageClick(index, rect)
                onImagePreviewClick?.invoke(index, anchor)
            },
        contentAlignment = Alignment.Center
    ) {
        if (imageUrl.isNotEmpty()) {
            val decodeSize = remember(displayCount) {
                resolveDynamicGridDecodeSize(displayCount = displayCount)
            }
            val imageRequest = remember(context, imageUrl, decodeSize) {
                coil3.request.ImageRequest.Builder(context)
                    .data(imageUrl)
                    // Reuse this exact source identity as the preview's placeholder key.
                    .memoryCacheKey(imageUrl)
                    .size(decodeSize.widthPx, decodeSize.heightPx)
                    .httpHeaders(DRAW_GRID_BILIBILI_REFERER_HEADERS)
                    // The Hero flight is the only transition; don't fade the source
                    // again if its list item is recreated while the preview is open.
                    .crossfade(false)
                    .build()
            }
            PageAwareAsyncImage(
                model = imageRequest,
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
                tint = AppContentColors.secondaryText().copy(alpha = 0.5f)
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

        // 实况与长图徽标
        val isLive = !item.live_url.isNullOrBlank()
        if (isLive) {
            Row(
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
                    ),
                horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.ExtraSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LivePhotoIcon(enabled = true, tint = MediaContrastPalette.Foreground)
                AppText(
                    stringResource(R.string.dynamic_live_photo_badge),
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

private val DRAW_GRID_BILIBILI_REFERER_HEADERS: NetworkHeaders =
    NetworkHeaders.Builder().set("Referer", "https://www.bilibili.com/").build()
