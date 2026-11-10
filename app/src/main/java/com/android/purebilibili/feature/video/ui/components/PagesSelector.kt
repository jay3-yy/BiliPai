package com.android.purebilibili.feature.video.ui.components

import com.android.purebilibili.core.util.rememberHapticFeedback
import com.android.purebilibili.core.util.HapticType

import com.android.purebilibili.core.ui.AppContentColors

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.ui.AppSurfaceTokens
import kotlin.math.PI
import kotlin.math.sin
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.components.AppText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Velocity
import com.android.purebilibili.data.model.response.Page

/** Keep the part list's edge drag and remaining fling inside the list, away from its pager. */
internal object PagePreviewNestedScrollConnection : NestedScrollConnection {
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset = Offset(x = available.x, y = 0f)

    override suspend fun onPostFling(
        consumed: Velocity,
        available: Velocity,
    ): Velocity = Velocity(x = available.x, y = 0f)
}

/** Below-video preview; every expanded part list uses the native player episode menu. */
@Composable
fun PagesSelector(
    pages: List<Page>,
    currentPageIndex: Int,
    onPageSelect: (Int) -> Unit,
    coverUrl: String = "",
    isPlaying: Boolean = false,
    modifier: Modifier = Modifier,
    onOpenExpanded: (() -> Unit)? = null,
) {
    if (pages.isEmpty()) return
    val selectionHaptic = rememberHapticFeedback(com.android.purebilibili.core.ui.AppHapticScene.MENUS)
    var showEpisodeMenu by rememberSaveable(pages.firstOrNull()?.cid) { mutableStateOf(false) }
    val currentPage = pages.getOrNull(currentPageIndex) ?: pages.first()
    val currentTitle = "P${currentPage.page} · ${currentPage.part.ifBlank { "第${currentPage.page}P" }}"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppText(
                text = "视频选集",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = AppContentColors.primaryText()
            )
            Spacer(modifier = Modifier.width(8.dp))
            AppText(
                text = "正在播放：$currentTitle",
                fontSize = 12.sp,
                color = AppContentColors.secondaryText(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(10.dp))
            AppText(
                text = "共${pages.size}P",
                fontSize = 13.sp,
                color = AppContentColors.accent(),
                modifier = Modifier
                    .clip(AppShapes.container(ContainerLevel.Pill))
                    .clickable { if (onOpenExpanded != null) onOpenExpanded() else showEpisodeMenu = true }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        PagesHorizontalPreviewRow(
            titles = pages.map { "P${it.page} · ${it.part.ifBlank { "第${it.page}P" }}" },
            currentIndex = currentPageIndex,
            isPlaying = isPlaying,
            onSelect = { index ->
                if (index != currentPageIndex) selectionHaptic(HapticType.LIGHT)
                onPageSelect(index)
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (showEpisodeMenu) {
        PlayerEpisodeMenu(
            pages = pages,
            currentPageIndex = currentPageIndex,
            cover = coverUrl,
            onDismiss = { showEpisodeMenu = false },
            onPageSelect = onPageSelect,
        )
    }
}

/** 正在播放指示：三根竖条随播放节奏起伏，未播放时保持静止波形。 */
@Composable
private fun PlayingEqualizerIcon(
    playing: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val animationGate = com.android.purebilibili.core.ui.rememberDecorativeAnimationGate()
    val animate = playing && animationGate.active
    val phase = if (animate) {
        val transition = rememberInfiniteTransition(label = "pagePlayback")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "pagePlaybackPhase",
        )
    } else {
        null
    }
    Canvas(modifier = modifier.then(animationGate.modifier)) {
        val barCount = 3
        val gap = size.width * 0.22f
        val barWidth = (size.width - gap * (barCount - 1)) / barCount
        val currentPhase = phase?.value ?: 0f
        repeat(barCount) { index ->
            val ratio = if (animate) {
                0.28f + 0.72f * (0.5f + 0.5f * sin(
                    currentPhase * 2f * PI.toFloat() + index * 2.1f
                ))
            } else {
                IdleEqualizerRatios[index]
            }
            val barHeight = (size.height * ratio).coerceAtLeast(1f)
            drawRoundRect(
                color = color,
                topLeft = Offset(index * (barWidth + gap), (size.height - barHeight) / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
            )
        }
    }
}

/**
 *  横向分集卡片行：固定 150dp 卡片宽、10dp 间距、35dp 行高，
 * 当前分 P 在卡片内额外显示播放波形图标并使用强调色文字，其余分 P 使用主文字色；
 * 当前分 P 变化时横向滚动到对应卡片。
 */
@Composable
private fun PagesHorizontalPreviewRow(
    titles: List<String>,
    currentIndex: Int,
    isPlaying: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (titles.isEmpty()) return

    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val activeIndex = currentIndex.coerceIn(0, titles.lastIndex)
    var hasPositioned by remember(titles) { mutableStateOf(false) }

    LaunchedEffect(activeIndex, titles.size) {
        // 定位当前项：其左边缘停在视口左侧半张卡片宽度处，首帧不做动画，之后平滑滚动。
        val halfItemPx = with(density) { PagesPreviewItemWidth.toPx() / 2f }
        if (hasPositioned) {
            listState.animateScrollToItem(activeIndex)
            listState.animateScrollBy(-halfItemPx)
        } else {
            listState.scrollToItem(activeIndex)
            listState.scrollBy(-halfItemPx)
            hasPositioned = true
        }
    }

    // 液态玻璃选中态与详情标签共用主题色；原生分支仍使用自身主题主色。
    val selectedAccent = playerOptionContentColor(true, AppContentColors.selectionAccent(
        com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled,
    ))
    LazyRow(
        state = listState,
        modifier = modifier
            .height(PagesPreviewRowHeight)
            .nestedScroll(PagePreviewNestedScrollConnection),
        horizontalArrangement = Arrangement.spacedBy(PagesPreviewItemSpacing),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        items(titles.size) { index ->
            val title = titles[index]
            val isCurrent = index == activeIndex
            Row(
                modifier = Modifier
                    .width(PagesPreviewItemWidth)
                    .fillMaxHeight()
                    .clip(playerOptionShape(RoundedCornerShape(PagesPreviewItemCorner)))
                    .background(playerOptionContainerColor(isCurrent, AppSurfaceTokens.cardContainer()))
                    .clickable { onSelect(index) }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isCurrent) {
                    PlayingEqualizerIcon(
                        playing = isPlaying,
                        color = selectedAccent,
                        modifier = Modifier.size(PagePreviewEqualizerSize),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                AppText(
                    text = title,
                    fontSize = 13.sp,
                    color = playerOptionContentColor(isCurrent, if (isCurrent) selectedAccent else AppContentColors.primaryText()),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private val PagesPreviewItemWidth = 150.dp
private val PagesPreviewItemSpacing = 10.dp
private val PagesPreviewRowHeight = 35.dp
private val PagesPreviewItemCorner = 6.dp
private val PagePreviewEqualizerSize = 12.dp

/** 未播放时保持的静止波形高度比例。 */
private val IdleEqualizerRatios = floatArrayOf(0.45f, 0.9f, 0.6f)

