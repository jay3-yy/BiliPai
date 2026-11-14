package com.android.purebilibili.feature.audio.screen

import com.android.purebilibili.core.ui.AppContentColors

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import com.android.purebilibili.core.ui.components.AppHorizontalPager as HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.motion.AppMotionTokens
import com.android.purebilibili.feature.audio.player.MusicQueueItemUi
import com.android.purebilibili.feature.home.components.LiquidGlassTuning
import com.android.purebilibili.feature.home.components.biliPaiFloatingDockShell
import com.android.purebilibili.feature.home.components.resolveLiquidGlassTuning
import top.yukonga.miuix.kmp.blur.Backdrop as MiuixBackdrop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

internal fun musicCoverDragTarget(source: Int, count: Int, offset: Float, spacing: Float, rightToLeft: Boolean): Int {
    if (source !in 0 until count || spacing <= 0f) return source
    val direction = if (rightToLeft) -1 else 1
    val steps = (abs(offset) / spacing).roundToInt() * if (offset < 0f) -1 else 1
    return (source + steps * direction).coerceIn(0, count - 1)
}

internal fun musicCoverPreviewShift(page: Int, source: Int, target: Int): Float = when {
    source < 0 || target < 0 -> 0f
    target > source && page in (source + 1)..target -> -1f
    target < source && page in target until source -> 1f
    else -> 0f
}

internal fun musicCoverTapTarget(center: Int, x: Float, left: Float, width: Float, rightToLeft: Boolean): Int {
    val step = when {
        x < left -> -1
        x > left + width -> 1
        else -> 0
    }
    return center + step * (if (rightToLeft) -1 else 1)
}

/**
 * 3D 实体 CD 盒切歌转盘 / 唱片架 (Cover Flow)。
 * 1:1 像素级复刻折叠屏半折悬停态 (Tabletop) 观赏级切歌体验。
 *
 * 核心视觉与交互特色：
 * 1. 原生 Compose 3D 透视：相机距离跟随卡片尺寸、两侧封面朝中心偏转，
 *    层叠推拉仅预绘当前项的直接相邻项。
 * 2. 实体 CD 盒质感：微圆角亚克力透光包边、左侧侧脊厚度高光（Jewel Case Spine）、
 *    顶部/底部铰链卡扣、表面斜向光斑折射、右上角时间戳徽章 (04:24)。
 * 3. 镜面地面倒影与接触阴影：短距离封面镜像以透明度遮罩消隐，并用独立接触阴影建立落地感。
 * 4. 悬浮胶囊控制条 (Pill Bar)：底栏药丸型毛玻璃一体化控制栏 (歌名 - 歌手、点赞、上一曲、播放/暂停、下一曲)。
 */
@Composable
internal fun Music3DCoverFlow(
    queue: List<MusicQueueItemUi>,
    currentIndex: Int,
    isPlaying: Boolean,
    onItemClick: (Int) -> Unit,
    onPlayPause: () -> Unit = {},
    onPrevious: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    isLiked: Boolean = false,
    isLikeStatusKnown: Boolean = true,
    onLikeClick: (() -> Unit)? = null,
    cardSizeDp: Int = 155,
    controlsWidthDp: Int = 440,
    showTransportControls: Boolean = true,
    shelfBelowControls: Boolean = false,
    progressContent: (@Composable () -> Unit)? = null,
    glassEnabled: Boolean = false,
    miuixBackdrop: MiuixBackdrop? = null,
    liquidGlassTuning: LiquidGlassTuning = resolveLiquidGlassTuning(progress = 0.5f),
    glassTintColor: Color = Color.Unspecified,
    isDarkEnvironment: Boolean = true,
    reduceMotion: Boolean = false,
    queueAction: (@Composable (Int) -> Unit)? = null,
    onQueueMove: ((Int, Int) -> Unit)? = null,
    queueShelfControls: Boolean = false,
) {
    if (queue.isEmpty()) return

    val validCurrentIndex = currentIndex.coerceIn(0, queue.size - 1)
    // 队列长度变化时一起替换分页状态与内容，避免外层先更新页数、
    // 内层延迟组合仍用旧队列生成封面键，导致索引越界。
    val pagerState = key(queue.size) {
        rememberPagerState(
            initialPage = validCurrentIndex,
            pageCount = { queue.size }
        )
    }
    val coroutineScope = rememberCoroutineScope()
    val latestQueue by rememberUpdatedState(queue)
    val latestMove by rememberUpdatedState(onQueueMove)
    val latestItemClick by rememberUpdatedState(onItemClick)
    val rightToLeft = LocalLayoutDirection.current == LayoutDirection.Rtl
    var draggedKey by remember { mutableStateOf<String?>(null) }
    var dragSourceIndex by remember { mutableIntStateOf(-1) }
    var dragTargetIndex by remember { mutableIntStateOf(-1) }
    var dragOrder by remember { mutableStateOf<List<String>>(emptyList()) }
    val dragOffset = remember { mutableFloatStateOf(0f) }
    var suppressCoverTap by remember { mutableStateOf(false) }
    val dragStartHaptic = com.android.purebilibili.core.ui.rememberAppHapticFeedback(
        com.android.purebilibili.core.ui.AppHapticScene.LONG_PRESS,
    )
    val reorderHaptic = com.android.purebilibili.core.ui.rememberAppHapticFeedback(
        com.android.purebilibili.core.ui.AppHapticScene.ADJUSTMENT,
    )
    val reflectionMaskColor = MusicContentColor
    val shelfShadowColor = MusicShadowColor
    var entrancePlayed by rememberSaveable { mutableStateOf(false) }
    val entranceProgress = androidx.compose.runtime.remember {
        Animatable(if (entrancePlayed) 1f else 0f)
    }
    val entranceSpec = AppMotionTokens.emphasizedSpec<Float>()
    val reducedMotionEntranceSpec = AppMotionTokens.standardSpec<Float>()

    LaunchedEffect(reduceMotion) {
        if (!entrancePlayed) {
            entranceProgress.animateTo(
                targetValue = 1f,
                animationSpec = if (reduceMotion) reducedMotionEntranceSpec else entranceSpec,
            )
            entrancePlayed = true
        }
    }

    // 队列编辑时跟随条目身份；其他唱片架保留按播放索引同步的原有行为。
    LaunchedEffect(if (queueAction != null) queue.getOrNull(currentIndex)?.stableId else currentIndex) {
        if (currentIndex in queue.indices && pagerState.currentPage != currentIndex) {
            if (reduceMotion) pagerState.scrollToPage(currentIndex)
            else pagerState.animateScrollToPage(currentIndex)
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val shelf: @Composable () -> Unit = {
                // Measure controls first; only the shelf yields height on short panes.
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .heightIn(max = (cardSizeDp + 36 + (cardSizeDp * 0.30f).toInt()).dp),
                ) {
                    val fittedCardSizeDp = minOf(
                        cardSizeDp,
                        (maxHeight.value - 36 - cardSizeDp * 0.30f).toInt().coerceAtLeast(0)
                    )
                    val horizontalPadding = ((maxWidth - fittedCardSizeDp.dp) / 2).coerceAtLeast(0.dp)
                    if (fittedCardSizeDp > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height((fittedCardSizeDp + 36 + fittedCardSizeDp * 0.30f).dp)
                                .then(if (onQueueMove != null) Modifier.pointerInput(rightToLeft, fittedCardSizeDp) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { position ->
                                            val cardWidth = fittedCardSizeDp.dp.toPx()
                                            val cardLeft = (size.width - cardWidth) / 2f
                                            val cardTop = ((size.height - cardWidth * 1.3f - 8.dp.toPx()) / 2f).coerceAtLeast(0f)
                                            val source = pagerState.settledPage
                                            val item = latestQueue.getOrNull(source)
                                            if (item != null && !pagerState.isScrollInProgress &&
                                                position.x in cardLeft..(cardLeft + cardWidth) &&
                                                position.y in cardTop..(cardTop + cardWidth)
                                            ) {
                                                draggedKey = item.stableId
                                                suppressCoverTap = true
                                                dragSourceIndex = source
                                                dragTargetIndex = source
                                                dragOrder = latestQueue.map { it.stableId }
                                                dragOffset.floatValue = 0f
                                                dragStartHaptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                        },
                                        onDrag = { change, amount ->
                                            if (draggedKey != null) {
                                                change.consume()
                                                dragOffset.floatValue += amount.x
                                                val spacing = fittedCardSizeDp.dp.toPx() * 0.86f
                                                val target = musicCoverDragTarget(
                                                    dragSourceIndex, dragOrder.size, dragOffset.floatValue, spacing, rightToLeft,
                                                )
                                                if (target != dragTargetIndex) {
                                                    dragTargetIndex = target
                                                    reorderHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                }
                                            }
                                        },
                                        onDragEnd = {
                                            if (draggedKey != null && dragTargetIndex != dragSourceIndex &&
                                                latestQueue.map { it.stableId } == dragOrder &&
                                                latestQueue.getOrNull(dragSourceIndex)?.stableId == draggedKey
                                            ) {
                                                latestMove?.invoke(dragSourceIndex, dragTargetIndex)
                                                reorderHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                            draggedKey = null
                                            dragSourceIndex = -1
                                            dragTargetIndex = -1
                                            dragOrder = emptyList()
                                            dragOffset.floatValue = 0f
                                        },
                                        onDragCancel = {
                                            draggedKey = null
                                            dragSourceIndex = -1
                                            dragTargetIndex = -1
                                            dragOrder = emptyList()
                                            dragOffset.floatValue = 0f
                                        },
                                    )
                                } else Modifier)
                                .then(if (queueShelfControls) Modifier.pointerInput(rightToLeft, fittedCardSizeDp) {
                                    var pressedCoverId: String? = null
                                    detectTapGestures(
                                        onPress = { position ->
                                            suppressCoverTap = false
                                            val cardWidth = fittedCardSizeDp.dp.toPx()
                                            val cardLeft = (size.width - cardWidth) / 2f
                                            val cardTop = ((size.height - cardWidth * 1.3f - 8.dp.toPx()) / 2f).coerceAtLeast(0f)
                                            val target = musicCoverTapTarget(
                                                pagerState.settledPage, position.x, cardLeft, cardWidth, rightToLeft,
                                            )
                                            pressedCoverId = if (position.y in cardTop..(cardTop + cardWidth)) {
                                                latestQueue.getOrNull(target)?.stableId
                                            } else null
                                            tryAwaitRelease()
                                        },
                                        onTap = {
                                            if (!suppressCoverTap && !pagerState.isScrollInProgress) {
                                                val index = latestQueue.indexOfFirst { it.stableId == pressedCoverId }
                                                if (index >= 0) latestItemClick(index)
                                            }
                                        },
                                    )
                                } else Modifier),
                        ) {
                        key(pagerState) {
                        HorizontalPager(
                            state = pagerState,
                            userScrollEnabled = draggedKey == null,
                            key = if (queueAction != null) { index -> queue[index].stableId } else null,
                            contentPadding = PaddingValues(horizontal = horizontalPadding),
                            beyondViewportPageCount = if (queue.size > 2) 2 else if (queue.size > 1) 1 else 0,
                            modifier = Modifier.fillMaxSize()
                        ) { page ->
                            val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                            if (abs(pageOffset) >= 3f) return@HorizontalPager
                            val item = queue[page]
                            val isCenter = abs(pageOffset) < 0.45f
                            val entranceDistance = abs(page - validCurrentIndex).toFloat()
                            val previewShift = musicCoverPreviewShift(page, dragSourceIndex, dragTargetIndex)
                            val animatedShift = animateFloatAsState(
                                targetValue = previewShift,
                                animationSpec = if (reduceMotion || draggedKey == null) snap() else spring(),
                                label = "queueReorderShift",
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .zIndex(if (draggedKey == item.stableId) 20f else 10f - abs(pageOffset))
                                    .graphicsLayer {
                                        val itemEntrance = resolveMusicCoverFlowItemEntranceProgress(
                                            overallProgress = entranceProgress.value,
                                            distanceFromCenter = entranceDistance,
                                        )
                                        // 1. 设置透视摄像机距离（深邃景深）
                                        cameraDistance = (fittedCardSizeDp * 0.075f).coerceIn(8f, 14f) * density

                                        // 2. Y 轴 3D 旋转角度（双向内旋折角，向中心聚拢呈弧形展台）
                                        val visualPageOffset = pageOffset - animatedShift.value
                                        rotationY = (visualPageOffset * -32f).coerceIn(-52f, 52f) *
                                            (if (reduceMotion) 1f else itemEntrance)

                                        // 3. 缩放景深层次：中心卡片 100%，近邻卡片 ~84%，次邻卡片 ~68%
                                        val depthScale = (1f - (abs(visualPageOffset) * 0.16f)).coerceIn(0.52f, 1f)
                                        val scale = depthScale *
                                            (if (reduceMotion) 1f else (0.96f + 0.04f * itemEntrance)) *
                                            if (draggedKey == item.stableId) 1.04f else 1f
                                        scaleX = scale
                                        scaleY = scale

                                        // 4. 按距离均匀收拢，避免远端卡片挤成一排
                                        val offsetMag = abs(pageOffset)
                                        // Place near neighbors beside the focus and tuck outer neighbors behind them.
                                        val spacingShift = -(offsetMag - minOf(offsetMag, 1f) * 0.86f -
                                            (offsetMag - 1f).coerceAtLeast(0f) * 0.48f) * fittedCardSizeDp.dp.toPx()
                                        val finalTranslationX = if (pageOffset > 0f) -spacingShift else spacingShift
                                        translationX = finalTranslationX *
                                            (if (reduceMotion) 1f else itemEntrance) +
                                            animatedShift.value * (if (rightToLeft) -1f else 1f) * fittedCardSizeDp.dp.toPx() * 0.86f +
                                            if (draggedKey == item.stableId) dragOffset.floatValue else 0f
                                        translationY = (if (reduceMotion) 0f else (1f - itemEntrance) * 18.dp.toPx()) -
                                            if (draggedKey == item.stableId && !reduceMotion) 6.dp.toPx() else 0f

                                        // 5. 层次高保真可见度
                                        alpha = resolveMusicCoverFlowItemAlpha(abs(visualPageOffset)) * itemEntrance
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = if (queueShelfControls) {
                                        Modifier.semantics {
                                            onClick(label = "播放${item.title}") {
                                                val index = latestQueue.indexOfFirst { it.stableId == item.stableId }
                                                if (index < 0) false else { latestItemClick(index); true }
                                            }
                                        }
                                    } else Modifier.clickable {
                                            coroutineScope.launch {
                                                if (reduceMotion) pagerState.scrollToPage(page)
                                                else pagerState.animateScrollToPage(page)
                                            }
                                            onItemClick(page)
                                        }
                                ) {
                                    // 实体 CD 盒主体
                                    Box(
                                        modifier = Modifier
                                            .size(fittedCardSizeDp.dp)
                                            .shadow(
                                                elevation = if (isCenter) 20.dp else 8.dp,
                                                shape = RoundedCornerShape(6.dp),
                                                ambientColor = shelfShadowColor.copy(alpha = if (isCenter) 0.42f else 0.22f),
                                                spotColor = shelfShadowColor.copy(alpha = if (isCenter) 0.58f else 0.30f)
                                            )
                                            .clip(RoundedCornerShape(6.dp))
                                            .border(
                                                width = 1.dp,
                                                brush = Brush.linearGradient(
                                                    listOf(
                                                        MaterialTheme.colorScheme.surfaceBright.copy(alpha = 0.52f),
                                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f),
                                                        MaterialTheme.colorScheme.surfaceBright.copy(alpha = 0.38f),
                                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
                                                    )
                                                ),
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                    ) {
                                        // 唱片封面大图
                                        AsyncImage(
                                            model = item.coverUrl,
                                            contentDescription = item.title,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )

                                        // 实体 CD 盒左侧侧脊厚度与铰链卡扣（Jewel Case Spine & Hinges）
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .width(7.dp)
                                                .background(
                                                    Brush.horizontalGradient(
                                                        listOf(
                                                            MaterialTheme.colorScheme.surfaceBright.copy(alpha = 0.50f),
                                                            shelfShadowColor.copy(alpha = 0.40f),
                                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f),
                                                            Color.Transparent
                                                        )
                                                    )
                                                )
                                        ) {
                                            // 顶部透明铰链卡扣
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopStart)
                                                    .padding(top = 4.dp, start = 1.dp)
                                                    .size(width = 3.dp, height = 7.dp)
                                                    .background(MaterialTheme.colorScheme.surfaceBright.copy(alpha = 0.45f), RoundedCornerShape(1.dp))
                                            )
                                            // 底部透明铰链卡扣
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.BottomStart)
                                                    .padding(bottom = 4.dp, start = 1.dp)
                                                    .size(width = 3.dp, height = 7.dp)
                                                    .background(MaterialTheme.colorScheme.surfaceBright.copy(alpha = 0.45f), RoundedCornerShape(1.dp))
                                            )
                                        }

                                        // 亚克力塑料表面斜向光斑漫反射（Acrylic Sheen）
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    Brush.linearGradient(
                                                        0.0f to MaterialTheme.colorScheme.surfaceBright.copy(alpha = 0.10f),
                                                        0.22f to MaterialTheme.colorScheme.surfaceBright.copy(alpha = 0.05f),
                                                         0.50f to Color.Transparent
                                                    )
                                                )
                                        )
                                    }

                                    // 地面微阴影接触线（Ground Contact Shadow）
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 2.dp)
                                            .width((fittedCardSizeDp * 0.82f).dp)
                                            .height(6.dp)
                                            .graphicsLayer {
                                                alpha = resolveMusicCoverFlowShadowEntranceProgress(entranceProgress.value)
                                            }
                                            .background(
                                                Brush.radialGradient(
                                                    listOf(
                                                        shelfShadowColor.copy(alpha = 0.58f),
                                                        Color.Transparent
                                                    )
                                                )
                                            )
                                    )

                                    // 地面镜面倒影：真实封面镜像，随距离渐隐（Floor Mirror Reflection）
                                    Box(
                                        modifier = Modifier
                                            .size(width = fittedCardSizeDp.dp, height = (fittedCardSizeDp * 0.30f).dp)
                                            .graphicsLayer {
                                                scaleY = -1f // 倒影垂直反转
                                                alpha = resolveMusicCoverFlowShadowEntranceProgress(entranceProgress.value)
                                                compositingStrategy = CompositingStrategy.Offscreen
                                            }
                                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                            .drawWithContent {
                                                drawContent()
                                                drawRect(
                                                    brush = Brush.verticalGradient(
                                                        0f to reflectionMaskColor.copy(alpha = 0.92f),
                                                        0.55f to reflectionMaskColor.copy(alpha = 0.34f),
                                                        1f to reflectionMaskColor.copy(alpha = 0.02f),
                                                    ),
                                                    blendMode = BlendMode.DstIn,
                                                )
                                            }
                                    ) {
                                        AsyncImage(
                                            model = item.coverUrl,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(fittedCardSizeDp.dp),
                                            contentScale = ContentScale.Crop,
                                            alpha = 0.34f
                                        )
                                    }
                                }
                            }
                        }
                        }
                        }
                    }

                }
            }
            if (!shelfBelowControls) shelf()

            queueAction?.invoke(pagerState.settledPage.coerceIn(queue.indices))

            progressContent?.let { progress ->
                Spacer(Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .widthIn(max = controlsWidthDp.dp)
                        .fillMaxWidth()
                        .graphicsLayer {
                            val chromeEntrance = resolveMusicCoverFlowShadowEntranceProgress(
                                entranceProgress.value
                            )
                            alpha = chromeEntrance
                            translationY = if (reduceMotion) 0f else (1f - chromeEntrance) * 12.dp.toPx()
                        }
                ) {
                    progress()
                }
            }

            Spacer(Modifier.height(12.dp))

            // 底部悬浮胶囊控制条（主题色容器 + 歌名 - 歌手 + 心形/上一首/播放/下一首）
            val playingItem = queue[validCurrentIndex]
            val pillContainerColor = AppSurfaceTokens.surfaceContainer()
            val pillBorderColor = MaterialTheme.colorScheme.outlineVariant
            val pillContentColor = AppContentColors.primaryText()
            // 玻璃开启时各唱片架共用背景采样；关闭时保留原生控制条。
            val useControlGlass = glassEnabled && miuixBackdrop != null

            AppSurface(
                shape = CircleShape,
                color = if (useControlGlass) Color.Transparent else pillContainerColor,
                border = BorderStroke(
                    width = 0.8.dp,
                    color = pillBorderColor
                ),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .widthIn(max = controlsWidthDp.dp)
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
                    .height(56.dp)
                    .graphicsLayer {
                        val chromeEntrance = resolveMusicCoverFlowShadowEntranceProgress(
                            entranceProgress.value
                        )
                        alpha = chromeEntrance
                        translationY = if (reduceMotion) 0f else (1f - chromeEntrance) * 12.dp.toPx()
                    }
                    .then(if (useControlGlass) Modifier.biliPaiFloatingDockShell(
                        backdrop = miuixBackdrop,
                        containerColor = pillContainerColor,
                        pressProgress = 0f,
                        shape = CircleShape,
                        enabled = true,
                        liquidGlassTuning = liquidGlassTuning,
                        darkTheme = isDarkEnvironment,
                    ) else Modifier)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 18.dp, end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 歌名 - 歌手（以 " - " 分隔）
                    AppText(
                        text = "${playingItem.title} - ${playingItem.artist.ifBlank { "未知艺术家" }}",
                        color = pillContentColor.copy(alpha = 0.95f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (showTransportControls) {
                        Spacer(Modifier.width(10.dp))

                        // 点赞按钮（未点赞薄线心，已点赞红粉心）
                        if (onLikeClick != null && isLikeStatusKnown) {
                            AppIconButton(
                                onClick = onLikeClick,
                                modifier = Modifier.size(48.dp)
                            ) {
                                AppIcon(
                                    imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = if (isLiked) "取消喜欢" else "喜欢",
                                    tint = if (isLiked) MusicLikeColor else pillContentColor.copy(alpha = 0.82f),
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        } else {
                            // 交互状态未知时保留同尺寸中性占位，避免图标翻转，也不提供会写错状态的入口。
                            Box(modifier = Modifier.size(48.dp)) {
                                AppIcon(
                                    imageVector = Icons.Outlined.FavoriteBorder,
                                    contentDescription = null,
                                    tint = pillContentColor.copy(alpha = 0.28f),
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(17.dp)
                                )
                            }
                        }

                        // 上一首
                        AppIconButton(
                            onClick = onPrevious ?: {},
                            enabled = onPrevious != null,
                            modifier = Modifier.size(48.dp)
                        ) {
                            AppIcon(
                                imageVector = Icons.Filled.SkipPrevious,
                                contentDescription = "上一首",
                                tint = pillContentColor.copy(alpha = if (onPrevious != null) 0.88f else 0.28f),
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        // 播放 / 暂停
                        AppIconButton(
                            onClick = onPlayPause,
                            modifier = Modifier.size(48.dp)
                        ) {
                            AppIcon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "暂停" else "播放",
                                tint = pillContentColor.copy(alpha = 0.95f),
                                modifier = Modifier.size(21.dp)
                            )
                        }

                        // 下一首
                        AppIconButton(
                            onClick = onNext ?: {},
                            enabled = onNext != null,
                            modifier = Modifier.size(48.dp)
                        ) {
                            AppIcon(
                                imageVector = Icons.Filled.SkipNext,
                                contentDescription = "下一首",
                                tint = pillContentColor.copy(alpha = if (onNext != null) 0.88f else 0.28f),
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    } else if (onLikeClick != null && isLikeStatusKnown) {
                        // 在大屏分屏下已由左侧主控切歌，此处仅保留单手快速点赞
                        Spacer(Modifier.width(8.dp))
                        AppIconButton(
                            onClick = onLikeClick,
                            modifier = Modifier.size(48.dp)
                        ) {
                            AppIcon(
                                imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = if (isLiked) "取消喜欢" else "喜欢",
                                tint = if (isLiked) MusicLikeColor else pillContentColor.copy(alpha = 0.82f),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }
            if (shelfBelowControls) {
                Spacer(Modifier.height(20.dp))
                shelf()
            }
        }
    }
}
