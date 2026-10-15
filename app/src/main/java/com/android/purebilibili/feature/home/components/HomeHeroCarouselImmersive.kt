package com.android.purebilibili.feature.home.components

import com.android.purebilibili.core.ui.AppContentColors

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import com.android.purebilibili.core.ui.components.AppHorizontalPager as HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.data.model.response.VideoItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val HERO_IMMERSIVE_AUTO_ADVANCE_MS = 7_000L

/**
 * 沉浸式 hero 变体（折叠屏/平板展开态，容器宽 ≥ 840dp 时由 HomeCategoryPage 切换）。
 * 与 TV 首页同一套视觉语言：全幅封面 + 当前封面高斯模糊氛围层 + 底部渐隐，锐利层与
 * 模糊层同为 FillWidth 顶对齐、逐像素对位，无横向断层。
 * 数据与点击契约复用既有 hero 管线（selectHomeHeroCarouselItems 去重/门控/回调）。
 * [horizontalEscapeDp] 为网格 contentPadding 的水平值，用于越界绘制实现真全幅。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HomeHeroCarouselImmersive(
    videos: List<VideoItem>,
    horizontalEscapeDp: Dp,
    onVideoClick: (VideoItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (videos.isEmpty()) return
    val pagerState = rememberPagerState { videos.size }
    //  自动轮播：用户拖动/落定期间跳过 tick，避免与手势抢页；手势结束后下一轮继续。
    LaunchedEffect(pagerState, videos.size) {
        while (videos.size > 1 && isActive) {
            delay(HERO_IMMERSIVE_AUTO_ADVANCE_MS)
            if (!pagerState.isScrollInProgress) {
                pagerState.animateScrollToPage((pagerState.currentPage + 1) % videos.size)
            }
        }
    }

    //  [高度鲁棒] 用可见宽度强制 2.2:1：祖先若传入有界 maxHeight（如铰链分栏、
    //  折叠桌面形态），aspectRatio 会被钳制成矮条；这里显式按宽算高，不受影响。
    //  越界绘制 (escape) 后画布宽为 W+2*escape，页面比例按同一宽度推导。
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .escapeHorizontal(horizontalEscapeDp)
            .clipToBounds(),
    ) {
        val heroHeight = maxWidth / 2.2f
        Box(
            Modifier
                .fillMaxWidth()
                .height(heroHeight)
        ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.matchParentSize(),
        ) { page ->
            videos.getOrNull(page)?.let { item ->
                HeroCarouselPage(item = item, onClick = { onVideoClick(item) })
            }
        }
        //  文字可读性 scrim：亮色封面下保住白字对比，覆盖下方约一半高度。
        Box(
            Modifier
                .matchParentSize()
                .background(Brush.verticalGradient(
                    0.45f to Color.Transparent,
                    1f to Color(0x99000000),
                )),
        )
        //  底缘融入信息流表面：窄带渐变，不侵蚀文字区的 scrim。
        Box(
            Modifier
                .matchParentSize()
                .background(Brush.verticalGradient(
                    0.85f to Color.Transparent,
                    1f to MaterialTheme.colorScheme.surface,
                )),
        )
        val current = videos.getOrNull(pagerState.currentPage)
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.75f)
                .padding(start = 24.dp, bottom = 20.dp),
        ) {
            AppText(
                text = current?.title.orEmpty(),
                style = MaterialTheme.typography.titleLarge.copy(
                    shadow = Shadow(color = Color(0xB3000000), blurRadius = 16f),
                ),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val ownerName = current?.owner?.name.orEmpty()
            if (ownerName.isNotBlank()) {
                AppText(
                    text = ownerName,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xE6FFFFFF),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
            val meta = buildList {
                if (current != null && current.duration > 0) add(FormatUtils.formatDuration(current.duration))
                if (current != null && current.stat.view > 0) add(FormatUtils.formatStat(current.stat.view.toLong()) + "播放")
                if (current != null && current.stat.danmaku > 0) add(FormatUtils.formatStat(current.stat.danmaku.toLong()) + "弹幕")
            }.joinToString(" · ")
            if (meta.isNotBlank()) {
                AppText(
                    text = meta,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xCCFFFFFF),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        //  指示点移到右下角，与左侧文字错开。
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 20.dp, end = 20.dp),
        ) {
            videos.forEachIndexed { i, _ ->
                Box(
                    Modifier
                        .size(width = if (i == pagerState.currentPage) 16.dp else 5.dp, height = 5.dp)
                        .clip(CircleShape)
                        .background(
                            if (i == pagerState.currentPage) AppContentColors.selectionAccent(
                                com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled,
                            )
                            else Color(0x80FFFFFF)
                        ),
                )
            }
        }
        }
    }
}

@Composable
private fun HeroCarouselPage(item: VideoItem, onClick: () -> Unit) {
    val context = LocalContext.current
    Box(
        Modifier
            .fillMaxSize()
            // onClickLabel 让读屏用户知道卡片指向哪条视频。
            .clickable(onClickLabel = item.title) { onClick() },
    ) {
        // 氛围层：极小尺寸请求 + blur；低版本系统无 RenderEffect 时小图拉伸本身即是柔和模糊
        val ambientModel = remember(item.pic) {
            ImageRequest.Builder(context).data(item.pic.toHttpsUrl()).size(48).build()
        }
        AsyncImage(
            model = ambientModel,
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            alignment = Alignment.TopStart,
            modifier = Modifier.matchParentSize().blur(48.dp),
        )
        // 锐利封面：DstIn 渐隐融入氛围层
        val model = remember(item.pic) {
            ImageRequest.Builder(context).data(item.pic.toHttpsUrl()).build()
        }
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            alignment = Alignment.TopStart,
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(0.35f to Color.Black, 1f to Color.Transparent),
                        blendMode = BlendMode.DstIn,
                    )
                },
        )
    }
}

/** 网格 item 横向越界 contentPadding，实现真全幅；仍处于网格裁剪边界内。 */
private fun Modifier.escapeHorizontal(amount: Dp): Modifier = layout { measurable, constraints ->
    val extra = amount.roundToPx()
    if (extra <= 0 || constraints.maxWidth == Constraints.Infinity) {
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
    } else {
        val width = constraints.maxWidth + extra * 2
        val placeable = measurable.measure(
            Constraints(minWidth = width, maxWidth = width, minHeight = 0, maxHeight = constraints.maxHeight),
        )
        layout(constraints.maxWidth, placeable.height) { placeable.placeRelative(-extra, 0) }
    }
}

private fun String.toHttpsUrl(): String = if (startsWith("//")) "https:$this" else this
