package com.android.purebilibili.core.ui.skeleton

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import com.android.purebilibili.core.ui.components.AppLazyVerticalGrid as LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as lazyGridItems
import androidx.compose.foundation.lazy.items as lazyListItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel

/** 内容列表首屏骨架共用扫光状态，位置仅在绘制阶段读取。 */
@Composable
fun rememberContentSkeletonPulseState(animated: Boolean = true): State<Float> =
    rememberSkeletonSweepState(animated = animated)

@Composable
fun rememberContentSkeletonBlockColor(pulse: State<Float>): () -> Color {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val onSurface = MaterialTheme.colorScheme.onSurface
    return remember(pulse, isDark, onSurface) {
        skeletonFill(
            base = onSurface.copy(alpha = if (isDark) CONTENT_SKELETON_DARK_MIN_ALPHA else CONTENT_SKELETON_LIGHT_MAX_ALPHA),
            highlight = onSurface.copy(alpha = if (isDark) CONTENT_SKELETON_DARK_MAX_ALPHA else CONTENT_SKELETON_LIGHT_MIN_ALPHA),
            position = { pulse.value },
        )
    }
}

@Composable
fun ContentSkeletonBlock(
    color: Color,
    modifier: Modifier = Modifier,
    shape: Shape = AppShapes.container(ContainerLevel.Tag),
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(color),
    )
}

@Composable
fun ContentSkeletonBlock(
    color: () -> Color,
    modifier: Modifier = Modifier,
    shape: Shape = AppShapes.container(ContainerLevel.Tag),
) {
    Box(modifier = modifier.clip(shape).skeletonBackground(color))
}

/** Keep the real font metrics and wrapping; paint one placeholder per measured text line. */
@Composable
internal fun ContentSkeletonText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    colorProvider: () -> Color,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    minLines: Int = 1,
) {
    val textLayout = remember { androidx.compose.runtime.mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    com.android.purebilibili.core.ui.components.AppText(
        text = text,
        style = style,
        maxLines = maxLines,
        minLines = minLines,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        tapToCopyEnabled = false,
        color = Color.Transparent,
        onTextLayout = { textLayout.value = it },
        modifier = modifier
            .clearAndSetSemantics { }
            .drawWithContent {
                // Measurements are written during layout and read only during drawing.
                val layout = textLayout.value ?: return@drawWithContent
                val brush = colorProvider.skeletonBrush(size)
                for (line in 0 until layout.lineCount) {
                    val top = layout.getLineTop(line)
                    val bottom = layout.getLineBottom(line)
                    val inset = (bottom - top) * 0.18f
                    drawRoundRect(
                        brush = brush,
                        topLeft = androidx.compose.ui.geometry.Offset(layout.getLineLeft(line), top + inset),
                        size = androidx.compose.ui.geometry.Size(
                            (layout.getLineRight(line) - layout.getLineLeft(line)).coerceAtLeast(0f),
                            (bottom - top - inset * 2f).coerceAtLeast(0f),
                        ),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
                    )
                }
            },
    )
}

@Composable
fun ContentVideoGridSkeleton(
    modifier: Modifier = Modifier,
    minItemWidth: Dp = 160.dp,
    coverAspectRatio: Float = 16f / 10f,
    itemCount: Int = 8,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    horizontalSpacing: Dp = 8.dp,
    verticalSpacing: Dp = 8.dp,
) {
    val animationGate = com.android.purebilibili.core.ui.rememberDecorativeAnimationGate(
        respectComponentMotion = false, // 扫光使用独立的骨架动效开关。
    )
    val pulse = rememberContentSkeletonPulseState(animated = itemCount > 0 && animationGate.active)
    val blockColor = rememberContentSkeletonBlockColor(pulse)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = minItemWidth),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing),
        userScrollEnabled = false,
        modifier = modifier.fillMaxSize().then(animationGate.modifier),
    ) {
        val skeletonKeys = List(itemCount.coerceAtLeast(0)) { it }
        lazyGridItems(
            items = skeletonKeys,
            key = { "content_video_grid_skeleton_$it" },
            contentType = { "content_video_grid_skeleton" },
        ) {
            ContentVideoGridItemSkeleton(
                coverAspectRatio = coverAspectRatio,
                blockColorProvider = blockColor,
            )
        }
    }
}

@Composable
fun ContentVideoGridSkeletonFixedColumns(
    columns: Int,
    modifier: Modifier = Modifier,
    coverAspectRatio: Float = 16f / 10f,
    rows: Int = 4,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    spacing: Dp = 8.dp,
) {
    val safeColumns = columns.coerceAtLeast(1)
    val skeletonKeys = List(safeColumns * rows.coerceAtLeast(1)) { it }
    val animationGate = com.android.purebilibili.core.ui.rememberDecorativeAnimationGate(
        respectComponentMotion = false, // 扫光使用独立的骨架动效开关。
    )
    val pulse = rememberContentSkeletonPulseState(animated = animationGate.active)
    val blockColor = rememberContentSkeletonBlockColor(pulse)
    LazyVerticalGrid(
        columns = GridCells.Fixed(safeColumns),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing),
        userScrollEnabled = false,
        modifier = modifier.fillMaxSize().then(animationGate.modifier),
    ) {
        lazyGridItems(
            items = skeletonKeys,
            key = { "content_video_fixed_skeleton_$it" },
            contentType = { "content_video_fixed_skeleton" },
        ) {
            ContentVideoGridItemSkeleton(
                coverAspectRatio = coverAspectRatio,
                blockColorProvider = blockColor,
            )
        }
    }
}

@Composable
fun ContentVideoGridItemSkeleton(
    coverAspectRatio: Float = 4f / 3f,
    blockColor: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
    blockColorProvider: (() -> Color)? = null,
) {
    val colorProvider = blockColorProvider ?: { blockColor }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
    ) {
        ContentSkeletonBlock(
            color = colorProvider,
            shape = AppShapes.mediaCover(),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(coverAspectRatio),
        )
        Spacer(modifier = Modifier.height(8.dp))
        ContentSkeletonBlock(
            color = colorProvider,
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .height(16.dp),
        )
        Spacer(modifier = Modifier.height(6.dp))
        ContentSkeletonBlock(
            color = colorProvider,
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .height(12.dp),
        )
    }
}

/** 横向媒体行：左封面 + 右侧标题/副标题，适配直播/专栏/番剧/话题列表。 */
@Composable
fun MediaListRowSkeleton(
    modifier: Modifier = Modifier,
    coverWidth: Dp = 128.dp,
    coverAspectRatio: Float = 16f / 10f,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    contentGap: Dp = 12.dp,
    blockColor: Color? = null,
    blockColorProvider: (() -> Color)? = null,
) {
    val animationGate = com.android.purebilibili.core.ui.rememberDecorativeAnimationGate(
        respectComponentMotion = false,
    )
    val color = blockColorProvider ?: if (blockColor != null) {
        { blockColor }
    } else {
        rememberContentSkeletonBlockColor(rememberContentSkeletonPulseState(animated = animationGate.active))
    }
    Row(
        modifier = modifier
            .then(animationGate.modifier)
            .fillMaxWidth()
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ContentSkeletonBlock(
            color = color,
            shape = AppShapes.mediaCover(),
            modifier = Modifier
                .width(coverWidth)
                .aspectRatio(coverAspectRatio),
        )
        Spacer(modifier = Modifier.width(contentGap))
        Column(modifier = Modifier.weight(1f)) {
            ContentSkeletonBlock(
                color = color,
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .height(16.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            ContentSkeletonBlock(
                color = color,
                modifier = Modifier
                    .fillMaxWidth(0.62f)
                    .height(12.dp),
            )
            Spacer(modifier = Modifier.height(6.dp))
            ContentSkeletonBlock(
                color = color,
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(12.dp),
            )
        }
    }
}

/** 用户行：头像 + 两行文案，适配 UP / 主播搜索。 */
@Composable
fun UserListRowSkeleton(
    modifier: Modifier = Modifier,
    avatarSize: Dp = 48.dp,
    blockColor: Color? = null,
    blockColorProvider: (() -> Color)? = null,
) {
    val animationGate = com.android.purebilibili.core.ui.rememberDecorativeAnimationGate(
        respectComponentMotion = false,
    )
    val color = blockColorProvider ?: if (blockColor != null) {
        { blockColor }
    } else {
        rememberContentSkeletonBlockColor(rememberContentSkeletonPulseState(animated = animationGate.active))
    }
    Row(
        modifier = modifier
            .then(animationGate.modifier)
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ContentSkeletonBlock(
            color = color,
            shape = CircleShape,
            modifier = Modifier.size(avatarSize),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            ContentSkeletonBlock(
                color = color,
                modifier = Modifier
                    .fillMaxWidth(0.45f)
                    .height(14.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            ContentSkeletonBlock(
                color = color,
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(12.dp),
            )
        }
    }
}

/**
 * 通用静态骨架色块（不参与扫光动画）。
 *
 * 与 [rememberContentSkeletonPulseState] 的扫光不同：凡是「骨架与真实内容共用同一个 LazyList、
 * 靠 item 身份原地替换」的页面，都应该用固定色。运动中的灰块与随后的内容淡入叠加，观感上
 * 就是「闪」；固定色只负责占位，切换时只剩「内容淡入」这一件事。
 */
@Composable
fun rememberStaticSkeletonBlockColor(): Color {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val onSurface = MaterialTheme.colorScheme.onSurface
    return remember(isDark, onSurface) {
        onSurface.copy(
            alpha = if (isDark) STATIC_SKELETON_DARK_ALPHA else STATIC_SKELETON_LIGHT_ALPHA
        )
    }
}

/** 评论区专用静态骨架色，语义等同 [rememberStaticSkeletonBlockColor]。 */
@Composable
fun rememberCommentSkeletonBlockColor(): Color = rememberStaticSkeletonBlockColor()

/** 单条评论骨架的正文行数：按槽位错落，避免整屏等高方块显得机械。 */
internal fun resolveCommentSkeletonBodyLineCount(slotIndex: Int): Int =
    when (slotIndex % 3) {
        0 -> 2
        1 -> 1
        else -> 3
    }

/**
 * 评论行骨架。
 *
 * 尺寸严格对齐真实评论项 `ReplyItemView` 的布局策略（水平内边距 12dp、头像 36dp、
 * 头像与内容间距 8dp、操作行 32dp），单槽总高约 130dp。骨架换成真实评论时行高基本一致，
 * 列表不会被突然撑长或缩短——这是消除「跳动」的关键。
 */
@Composable
fun CommentListItemSkeleton(
    modifier: Modifier = Modifier,
    blockColor: Color? = null,
    slotIndex: Int = 0,
) {
    val color = blockColor ?: rememberCommentSkeletonBlockColor()
    val bodyLineCount = resolveCommentSkeletonBodyLineCount(slotIndex)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = COMMENT_SKELETON_HORIZONTAL_PADDING_DP.dp,
                end = COMMENT_SKELETON_HORIZONTAL_PADDING_DP.dp,
                top = COMMENT_SKELETON_TOP_PADDING_DP.dp,
                bottom = COMMENT_SKELETON_BOTTOM_PADDING_DP.dp,
            ),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            ContentSkeletonBlock(
                color = color,
                shape = CircleShape,
                modifier = Modifier.size(COMMENT_SKELETON_AVATAR_SIZE_DP.dp),
            )
            Spacer(modifier = Modifier.width(COMMENT_SKELETON_AVATAR_GAP_DP.dp))
            Column(modifier = Modifier.weight(1f)) {
                ContentSkeletonBlock(
                    color = color,
                    modifier = Modifier
                        .fillMaxWidth(0.34f)
                        .height(COMMENT_SKELETON_NAME_HEIGHT_DP.dp),
                )
                Spacer(modifier = Modifier.height(COMMENT_SKELETON_NAME_GAP_DP.dp))
                ContentSkeletonBlock(
                    color = color,
                    modifier = Modifier
                        .fillMaxWidth(0.46f)
                        .height(COMMENT_SKELETON_META_HEIGHT_DP.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(COMMENT_SKELETON_BODY_GAP_DP.dp))
        repeat(bodyLineCount) { line ->
            ContentSkeletonBlock(
                color = color,
                modifier = Modifier
                    .fillMaxWidth(if (line == bodyLineCount - 1) 0.72f else 0.96f)
                    .height(COMMENT_SKELETON_BODY_HEIGHT_DP.dp),
            )
            if (line != bodyLineCount - 1) {
                Spacer(modifier = Modifier.height(COMMENT_SKELETON_BODY_LINE_GAP_DP.dp))
            }
        }
        Spacer(modifier = Modifier.height(COMMENT_SKELETON_ACTION_GAP_DP.dp))
        Box(
            modifier = Modifier.height(COMMENT_SKELETON_ACTION_HEIGHT_DP.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            ContentSkeletonBlock(
                color = color,
                modifier = Modifier
                    .fillMaxWidth(0.42f)
                    .height(COMMENT_SKELETON_ACTION_BAR_HEIGHT_DP.dp),
            )
        }
    }
}

@Composable
fun CommentListSkeleton(
    modifier: Modifier = Modifier,
    itemCount: Int = 6,
    contentPadding: PaddingValues = PaddingValues(vertical = 4.dp),
) {
    val blockColor = rememberCommentSkeletonBlockColor()
    val skeletonKeys = List(itemCount.coerceAtLeast(0)) { it }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        userScrollEnabled = false,
    ) {
        lazyListItems(
            items = skeletonKeys,
            key = { "comment_list_skeleton_$it" },
            contentType = { "comment_list_skeleton" },
        ) { slotIndex ->
            CommentListItemSkeleton(blockColor = blockColor, slotIndex = slotIndex)
        }
    }
}

/**
 * 可嵌入现有 LazyColumn 的评论骨架组。
 *
 * 只负责铺出与真实评论同高、同间距的静态占位行；真正「稳定槽位」由调用方按
 * slot 拆成多个 lazy item 实现，避免骨架到内容切换时 item 结构整体替换。
 */
@Composable
fun CommentListColumnSkeleton(
    modifier: Modifier = Modifier,
    itemCount: Int = 5,
) {
    val blockColor = rememberCommentSkeletonBlockColor()
    Column(modifier = modifier.fillMaxWidth()) {
        repeat(itemCount.coerceAtLeast(0)) { slotIndex ->
            CommentListItemSkeleton(blockColor = blockColor, slotIndex = slotIndex)
        }
    }
}

@Composable
fun ContentMediaListSkeleton(
    modifier: Modifier = Modifier,
    itemCount: Int = 8,
    contentPadding: PaddingValues = PaddingValues(vertical = 8.dp),
    useUserRow: Boolean = false,
    mediaRowCoverWidth: Dp = 128.dp,
    mediaRowCoverAspectRatio: Float = 16f / 10f,
    mediaRowContentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    mediaRowContentGap: Dp = 12.dp,
) {
    val animationGate = com.android.purebilibili.core.ui.rememberDecorativeAnimationGate(
        respectComponentMotion = false, // 扫光使用独立的骨架动效开关。
    )
    val pulse = rememberContentSkeletonPulseState(animated = itemCount > 0 && animationGate.active)
    val blockColor = rememberContentSkeletonBlockColor(pulse)
    val skeletonKeys = List(itemCount.coerceAtLeast(0)) { it }
    LazyColumn(
        modifier = modifier.fillMaxSize().then(animationGate.modifier),
        contentPadding = contentPadding,
        userScrollEnabled = false,
    ) {
        lazyListItems(
            items = skeletonKeys,
            key = { "content_media_list_skeleton_$it" },
            contentType = { if (useUserRow) "user_row_skeleton" else "media_row_skeleton" },
        ) {
            if (useUserRow) {
                UserListRowSkeleton(blockColorProvider = blockColor)
            } else {
                MediaListRowSkeleton(
                    coverWidth = mediaRowCoverWidth,
                    coverAspectRatio = mediaRowCoverAspectRatio,
                    contentPadding = mediaRowContentPadding,
                    contentGap = mediaRowContentGap,
                    blockColorProvider = blockColor,
                )
            }
        }
    }
}

/** 文章详情首屏：标题、作者信息与正文段落，避免用居中转圈造成内容跳变。 */
@Composable
fun ArticleDetailSkeleton(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(20.dp),
) {
    val animationGate = com.android.purebilibili.core.ui.rememberDecorativeAnimationGate(
        respectComponentMotion = false, // 扫光使用独立的骨架动效开关。
    )
    val pulse = rememberContentSkeletonPulseState(animated = animationGate.active)
    val blockColor = rememberContentSkeletonBlockColor(pulse)
    LazyColumn(
        modifier = modifier.fillMaxSize().then(animationGate.modifier),
        contentPadding = contentPadding,
        userScrollEnabled = false,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ContentSkeletonBlock(blockColor, Modifier.fillMaxWidth(0.92f).height(28.dp))
            Spacer(Modifier.height(10.dp))
            ContentSkeletonBlock(blockColor, Modifier.fillMaxWidth(0.68f).height(28.dp))
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ContentSkeletonBlock(blockColor, Modifier.size(42.dp), CircleShape)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    ContentSkeletonBlock(blockColor, Modifier.fillMaxWidth(0.36f).height(14.dp))
                    Spacer(Modifier.height(7.dp))
                    ContentSkeletonBlock(blockColor, Modifier.fillMaxWidth(0.52f).height(12.dp))
                }
            }
        }
        lazyListItems(List(7) { it }) { index ->
            ContentSkeletonBlock(
                blockColor,
                Modifier
                    .fillMaxWidth(if (index % 3 == 2) 0.72f else 1f)
                    .height(16.dp),
            )
        }
    }
}

/** 海报型详情首屏：番剧封面、标题资料、操作区与后续章节。 */
@Composable
fun PosterDetailSkeleton(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
) {
    val animationGate = com.android.purebilibili.core.ui.rememberDecorativeAnimationGate(
        respectComponentMotion = false, // 扫光使用独立的骨架动效开关。
    )
    val pulse = rememberContentSkeletonPulseState(animated = animationGate.active)
    val blockColor = rememberContentSkeletonBlockColor(pulse)
    LazyColumn(
        modifier = modifier.fillMaxSize().then(animationGate.modifier),
        contentPadding = contentPadding,
        userScrollEnabled = false,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row {
                ContentSkeletonBlock(
                    blockColor,
                    Modifier.width(132.dp).aspectRatio(0.75f),
                    AppShapes.container(ContainerLevel.Card),
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ContentSkeletonBlock(blockColor, Modifier.fillMaxWidth(0.9f).height(24.dp))
                    ContentSkeletonBlock(blockColor, Modifier.fillMaxWidth(0.58f).height(14.dp))
                    ContentSkeletonBlock(blockColor, Modifier.fillMaxWidth(0.72f).height(14.dp))
                    ContentSkeletonBlock(blockColor, Modifier.widthIn(min = 96.dp).fillMaxWidth(0.48f).height(40.dp))
                }
            }
        }
        item { ContentSkeletonBlock(blockColor, Modifier.fillMaxWidth().height(44.dp)) }
        item { ContentSkeletonBlock(blockColor, Modifier.fillMaxWidth(0.34f).height(20.dp)) }
        lazyListItems(List(4) { it }) { index ->
            ContentSkeletonBlock(
                blockColor,
                Modifier.fillMaxWidth(if (index == 3) 0.66f else 1f).height(15.dp),
            )
        }
    }
}

/** 分类入口网格：图标块 + 分类名，对齐直播“全部标签”等目录页。 */
@Composable
fun ContentCategoryGridSkeleton(
    columns: Int,
    modifier: Modifier = Modifier,
    rows: Int = 4,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    spacing: Dp = 16.dp,
) {
    val safeColumns = columns.coerceAtLeast(1)
    val animationGate = com.android.purebilibili.core.ui.rememberDecorativeAnimationGate(
        respectComponentMotion = false, // 扫光使用独立的骨架动效开关。
    )
    val pulse = rememberContentSkeletonPulseState(animated = animationGate.active)
    val blockColor = rememberContentSkeletonBlockColor(pulse)
    LazyVerticalGrid(
        columns = GridCells.Fixed(safeColumns),
        modifier = modifier.fillMaxSize().then(animationGate.modifier),
        contentPadding = contentPadding,
        userScrollEnabled = false,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        lazyGridItems(List(safeColumns * rows.coerceAtLeast(1)) { it }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ContentSkeletonBlock(
                    blockColor,
                    Modifier.fillMaxWidth().aspectRatio(1f),
                    AppShapes.container(ContainerLevel.Card),
                )
                Spacer(Modifier.height(8.dp))
                ContentSkeletonBlock(blockColor, Modifier.fillMaxWidth(0.62f).height(13.dp))
            }
        }
    }
}

/** 热搜页首屏：顶部视觉横幅 + 排名文本行。 */
@Composable
fun TrendingListSkeleton(
    modifier: Modifier = Modifier,
    itemCount: Int = 10,
    contentPadding: PaddingValues = PaddingValues(bottom = 24.dp),
) {
    val animationGate = com.android.purebilibili.core.ui.rememberDecorativeAnimationGate(
        respectComponentMotion = false, // 扫光使用独立的骨架动效开关。
    )
    val pulse = rememberContentSkeletonPulseState(animated = animationGate.active)
    val blockColor = rememberContentSkeletonBlockColor(pulse)
    LazyColumn(
        modifier = modifier.fillMaxSize().then(animationGate.modifier),
        contentPadding = contentPadding,
        userScrollEnabled = false,
    ) {
        item {
            ContentSkeletonBlock(
                blockColor,
                Modifier.fillMaxWidth().height(240.dp),
                AppShapes.container(ContainerLevel.Card),
            )
        }
        lazyListItems(List(itemCount.coerceAtLeast(0)) { it }) { index ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ContentSkeletonBlock(blockColor, Modifier.width(24.dp).height(18.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    ContentSkeletonBlock(
                        blockColor,
                        Modifier.fillMaxWidth(if (index % 3 == 0) 0.82f else 0.64f).height(16.dp),
                    )
                    Spacer(Modifier.height(7.dp))
                    ContentSkeletonBlock(blockColor, Modifier.fillMaxWidth(0.38f).height(11.dp))
                }
            }
        }
    }
}

private const val CONTENT_SKELETON_LIGHT_MIN_ALPHA = 0.06f
private const val CONTENT_SKELETON_LIGHT_MAX_ALPHA = 0.11f
private const val CONTENT_SKELETON_DARK_MIN_ALPHA = 0.10f
private const val CONTENT_SKELETON_DARK_MAX_ALPHA = 0.16f

// 静态骨架色块（取原脉冲区间的中值），用于「同列表原地替换」的骨架。
private const val STATIC_SKELETON_LIGHT_ALPHA = 0.10f
private const val STATIC_SKELETON_DARK_ALPHA = 0.14f

// 评论骨架尺寸：严格对齐真实评论项布局策略。

private const val COMMENT_SKELETON_HORIZONTAL_PADDING_DP = 12
private const val COMMENT_SKELETON_TOP_PADDING_DP = 10
private const val COMMENT_SKELETON_BOTTOM_PADDING_DP = 2
private const val COMMENT_SKELETON_AVATAR_SIZE_DP = 36
private const val COMMENT_SKELETON_AVATAR_GAP_DP = 8
private const val COMMENT_SKELETON_NAME_HEIGHT_DP = 14
private const val COMMENT_SKELETON_NAME_GAP_DP = 6
private const val COMMENT_SKELETON_META_HEIGHT_DP = 12
private const val COMMENT_SKELETON_BODY_GAP_DP = 6
private const val COMMENT_SKELETON_BODY_HEIGHT_DP = 16
private const val COMMENT_SKELETON_BODY_LINE_GAP_DP = 4
private const val COMMENT_SKELETON_ACTION_GAP_DP = 8
private const val COMMENT_SKELETON_ACTION_HEIGHT_DP = 32
private const val COMMENT_SKELETON_ACTION_BAR_HEIGHT_DP = 14
