package com.android.purebilibili.feature.search

import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.appElasticPress


import coil3.request.crossfade

import androidx.compose.foundation.background
import com.android.purebilibili.core.ui.components.appActionClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import com.android.purebilibili.core.ui.AdaptiveLoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.ui.AppSpacingTokens
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import com.android.purebilibili.core.ui.ImmersiveAppScaffold as AppScaffold
import com.android.purebilibili.core.ui.AppTopBar
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.globalWallpaperAwareBackground
import com.android.purebilibili.core.ui.skeleton.ContentSkeletonBlock
import com.android.purebilibili.core.ui.skeleton.rememberStaticSkeletonBlockColor
import com.android.purebilibili.core.ui.rememberAppBackIcon
import com.android.purebilibili.core.ui.resolveBottomSafeAreaPadding
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.data.model.response.TopicTopDetails
import com.android.purebilibili.data.model.response.normalizeSearchImageUrl
import com.android.purebilibili.feature.dynamic.components.DynamicCardV2
import com.android.purebilibili.feature.dynamic.components.DynamicCardActions
import com.android.purebilibili.feature.dynamic.components.DynamicCardInteractionActions
import com.android.purebilibili.feature.dynamic.components.DynamicCardNavigationActions
import com.android.purebilibili.feature.dynamic.components.DynamicFeedSkeletonCard
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.R
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.ui.rememberAppChromeLiquidGlassEnabled
import com.android.purebilibili.core.util.responsiveContentWidth
import com.android.purebilibili.data.model.response.DynamicPublishTopic
import com.android.purebilibili.feature.dynamic.components.DynamicAdaptiveSegmentedControl
import com.android.purebilibili.feature.dynamic.components.DynamicPublishComposer
import com.android.purebilibili.feature.home.components.BottomBarMatchedReusableLiquidDock
import com.android.purebilibili.feature.home.components.resolveFloatingDockGeometryScale
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

@Composable
fun TopicDetailScreen(
    topicId: Long,
    viewModel: TopicDetailViewModel = viewModel(),
    onBack: () -> Unit,
    onVideoClick: (String) -> Unit,
    onBangumiClick: (Long, Long) -> Unit,
    onUserClick: (Long) -> Unit,
    onTopicClick: (Long) -> Unit,
    onTopicKeywordClick: ((String) -> Unit)? = null,
    onLiveClick: (Long, String, String) -> Unit,
    onMusicClick: (Long) -> Unit = {},
    onCollectionClick: (Long, Long, String, String) -> Unit = { _, _, _, _ -> },
    onCourseClick: (String, String) -> Unit = { _, _ -> },
    onArticleClick: (Long, String) -> Unit,
    onDynamicDetailClick: (String) -> Unit,
    onMatchClick: ((Long) -> Unit)? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val liquidGlassEnabled = rememberAppChromeLiquidGlassEnabled(
        androidNativeEnabled = com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled,
    )
    val showInitialSkeleton = shouldShowTopicInitialSkeleton(
        isLoading = state.isLoading,
        hasDetails = state.details != null,
        itemCount = state.items.size,
    )
    // 骨架与真实卡片共用同一个 LazyColumn、同一个 contentType，色块因此用固定色：
    // 呼吸中的灰块叠在卡片淡入之下，观感上就是「闪」。
    val skeletonBlockColor = rememberStaticSkeletonBlockColor()
    val topicBackdrop = if (liquidGlassEnabled && !showInitialSkeleton) rememberLayerBackdrop() else null
    var showPublishComposer by remember { mutableStateOf(false) }
    var composerAccountId by remember { mutableStateOf(0L) }
    var clearingDraft by remember { mutableStateOf(false) }
    val draftScope = rememberCoroutineScope()

    LaunchedEffect(topicId) {
        viewModel.load(topicId)
    }

    AppScaffold(
        blurContentReady = !showInitialSkeleton,
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            AppTopBar(
                title = state.details?.topicItem?.name.orEmpty().ifBlank { "话题" },
                navigationIcon = {
                    AppIconButton(onClick = onBack) {
                        AppIcon(
                            imageVector = rememberAppBackIcon(),
                            contentDescription = "返回",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            TopicParticipateButton(
                liquidGlassEnabled = liquidGlassEnabled,
                backdrop = topicBackdrop,
                onClick = {
                    composerAccountId = com.android.purebilibili.core.store.TokenManager.midCache ?: 0L
                    showPublishComposer = true
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (topicBackdrop != null) Modifier.layerBackdrop(topicBackdrop) else Modifier)
                .globalWallpaperAwareBackground()
        ) {
            // 骨架与真实内容共用同一个 LazyColumn 与同一个 contentType：数据到达时
            // LazyList 在原地替换 item（配合 animateItem 交叉淡入），不再整棵子树重建。
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = padding.calculateTopPadding() + 12.dp,
                    bottom = resolveBottomSafeAreaPadding(
                        navigationBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                        extraBottomPadding = 16.dp
                    )
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .responsiveContentWidth(maxWidth = 960.dp)
                    .fillMaxSize()
            ) {
                when {
                    showInitialSkeleton -> {
                        item(key = "topic_header_skeleton") {
                            TopicHeaderCardSkeleton(modifier = Modifier.animateItem())
                        }
                        items(
                            count = TOPIC_DETAIL_SKELETON_ITEM_COUNT,
                            key = { index -> "topic_dynamic_skeleton_$index" },
                            contentType = { TOPIC_DYNAMIC_CARD_CONTENT_TYPE }
                        ) { _ ->
                            DynamicFeedSkeletonCard(
                                modifier = Modifier.animateItem(),
                                blockColor = skeletonBlockColor,
                            )
                        }
                    }

                    state.error != null && state.details == null && state.items.isEmpty() -> {
                        item(key = "topic_error") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillParentMaxHeight(0.6f),
                                contentAlignment = Alignment.Center
                            ) {
                                AppText(
                                    text = state.error ?: "话题加载失败",
                                    color = AppContentColors.secondaryText()
                                )
                            }
                        }
                    }

                    else -> {
                        item(key = "topic_header") {
                            TopicHeaderCard(
                                details = state.details,
                                onUserClick = onUserClick,
                            )
                        }
                        if (state.sortOptions.isNotEmpty()) {
                            item(key = "topic_sort") {
                                TopicSortControl(
                                    options = state.sortOptions.map { it.sortName.ifBlank { "动态" } },
                                    selectedIndex = state.sortOptions
                                        .indexOfFirst { it.sortBy == state.selectedSortBy }
                                        .coerceAtLeast(0),
                                    switching = state.isSwitchingSort,
                                    onSelected = { index ->
                                        state.sortOptions.getOrNull(index)?.let { option ->
                                            viewModel.selectSort(option.sortBy)
                                        }
                                    },
                                    modifier = Modifier,
                                )
                            }
                        }
                        itemsIndexed(
                            state.items,
                            key = { _, item -> item.id_str },
                            contentType = { _, _ -> TOPIC_DYNAMIC_CARD_CONTENT_TYPE }
                        ) { index, item ->
                            Box(modifier = Modifier.animateItem()) {
                                DynamicCardV2(
                                    item = item,
                                    gifImageLoader = context.imageLoader,
                                    actions = DynamicCardActions(
                                        navigation = DynamicCardNavigationActions(
                                            onVideoClick = onVideoClick,
                                            onBangumiClick = onBangumiClick,
                                            onUserClick = onUserClick,
                                            onTopicClick = onTopicClick,
                                            onTopicKeywordClick = onTopicKeywordClick,
                                            onLiveClick = onLiveClick,
                                            onMusicClick = onMusicClick,
                                            onCollectionClick = onCollectionClick,
                                            onCourseClick = onCourseClick,
                                            onArticleClick = onArticleClick,
                                            onDynamicDetailClick = onDynamicDetailClick,
                                            onMatchClick = onMatchClick,
                                        ),
                                        interaction = DynamicCardInteractionActions(
                                            onCommentClick = onDynamicDetailClick,
                                        ),
                                    ),
                                )
                            }
                            if (index == state.items.size - 3 && state.hasMore && !state.isLoadingMore) {
                                LaunchedEffect(state.offset) {
                                    viewModel.loadMore()
                                }
                            }
                        }
                        if (state.isLoadingMore) {
                            item(key = "topic_loading_more") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AdaptiveLoadingIndicator(
                                        size = 24.dp,
                                        strokeWidth = 2.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPublishComposer) {
        val topic = state.details?.topicItem
        val draftKey = "topic_$topicId"
        val draft = remember(composerAccountId, topicId) {
            com.android.purebilibili.feature.dynamic.components.DynamicDraftStore.load(context, composerAccountId, draftKey)
                ?: com.android.purebilibili.data.model.response.DynamicPublishDraft(
                    text = "",
                    topic = topic?.takeIf { it.id > 0L }?.let {
                        DynamicPublishTopic(id = it.id, name = it.name)
                    },
                )
        }
        DynamicPublishComposer(
            initialDraft = draft,
            isEditing = false,
            submitting = state.isPublishing || clearingDraft,
            draftAccountId = composerAccountId.takeIf { it > 0 },
            draftKey = draftKey,
            errorMessage = state.publishError,
            onDismiss = { showPublishComposer = false },
            onSubmit = { draft ->
                viewModel.publish(context, draft) { success, message ->
                    android.widget.Toast.makeText(
                        context,
                        message,
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                    if (success) {
                        clearingDraft = true
                        draftScope.launch {
                            val result = com.android.purebilibili.feature.dynamic.components.DynamicDraftStore.clear(context, composerAccountId, draftKey)
                            if (result.isFailure) android.widget.Toast.makeText(context, "发布成功，但草稿清除失败", android.widget.Toast.LENGTH_LONG).show()
                            clearingDraft = false
                            showPublishComposer = false
                        }
                    }
                }
            },
        )
    }
}

/** 话题动态卡的 lazy item 身份。骨架卡与真实卡片共用同一个 contentType，才能原地替换。 */
private const val TOPIC_DYNAMIC_CARD_CONTENT_TYPE = "topic_dynamic_card"

/** 话题详情首屏骨架卡片数量。 */
private const val TOPIC_DETAIL_SKELETON_ITEM_COUNT = 6

/**
 * 话题头卡骨架：对齐真实 [TopicHeaderCard] 的封面块 + 三行文字 + 圆形操作位。
 *
 * 色块用固定色（不参与呼吸脉冲）：骨架与真实头卡在同一个 LazyColumn 内原地替换，
 * 切换时只应看到「内容淡入」。
 */
@Composable
private fun TopicHeaderCardSkeleton(modifier: Modifier = Modifier) {
    val blockColor = rememberStaticSkeletonBlockColor()
    AppSurface(
        shape = AppShapes.container(ContainerLevel.Chip),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ContentSkeletonBlock(
                color = blockColor,
                shape = AppShapes.container(ContainerLevel.Chip),
                modifier = Modifier.size(58.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ContentSkeletonBlock(
                    color = blockColor,
                    modifier = Modifier
                        .fillMaxWidth(0.62f)
                        .height(18.dp),
                )
                ContentSkeletonBlock(
                    color = blockColor,
                    modifier = Modifier
                        .fillMaxWidth(0.88f)
                        .height(13.dp),
                )
                ContentSkeletonBlock(
                    color = blockColor,
                    modifier = Modifier
                        .fillMaxWidth(0.56f)
                        .height(12.dp),
                )
            }
            ContentSkeletonBlock(
                color = blockColor,
                shape = CircleShape,
                modifier = Modifier.size(34.dp),
            )
        }
    }
}

@Composable
private fun TopicSortControl(
    options: List<String>,
    selectedIndex: Int,
    switching: Boolean,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (options.isEmpty()) return
    val itemWidth = TOPIC_SORT_ITEM_WIDTH_DP.dp

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DynamicAdaptiveSegmentedControl(
            items = options,
            selectedIndex = selectedIndex,
            onSelected = onSelected,
            itemWidth = itemWidth,
            height = 40.dp,
            indicatorHeight = 34.dp,
            labelFontSize = com.android.purebilibili.core.ui.appTabLabelFontSize(),
            backdrop = null,
            modifier = Modifier.width(resolveTopicSortControlWidthDp(options.size).dp),
        )
        if (switching) {
            Spacer(modifier = Modifier.width(AppSpacingTokens.Small))
            AdaptiveLoadingIndicator(size = 18.dp, strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun TopicParticipateButton(
    liquidGlassEnabled: Boolean,
    backdrop: top.yukonga.miuix.kmp.blur.Backdrop?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val buttonModifier = modifier
        .width(TOPIC_PARTICIPATE_BUTTON_WIDTH_DP.dp)
        .height(52.dp)
    val topicIcon = painterResource(R.drawable.ms_tag_24)

    when (
        resolveTopicParticipateChrome(
            uiStyle = LocalAppUiStyle.current,
            liquidGlassEnabled = liquidGlassEnabled,
        )
    ) {
        TopicParticipateChrome.LIQUID_GLASS_DOCK -> BottomBarMatchedReusableLiquidDock(
            shape = AppShapes.container(ContainerLevel.Pill),
            modifier = buttonModifier,
            backdrop = backdrop,
            reuseEnabled = true,
            useNeutralLiquidContainer = true,
            drawShellLens = true,
            shellLensIntensity = resolveFloatingDockGeometryScale(52f),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(AppShapes.container(ContainerLevel.Pill))
                    .appActionClickable(onClick = onClick)
                    .padding(horizontal = AppSpacingTokens.Medium),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(
                    painter = topicIcon,
                    contentDescription = null,
                    tint = AppContentColors.accent(),
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(AppSpacingTokens.Small))
                AppText(
                    text = "参与话题",
                    color = AppContentColors.accent(),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        TopicParticipateChrome.MIUIX_COMPACT_BUTTON -> MiuixButton(
            onClick = onClick,
            modifier = buttonModifier.appElasticPress(),
            insideMargin = PaddingValues(horizontal = AppSpacingTokens.Medium),
        ) {
            AppIcon(
                painter = topicIcon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(AppSpacingTokens.Small))
            AppText("参与话题", fontWeight = FontWeight.SemiBold)
        }

        TopicParticipateChrome.MATERIAL_EXTENDED_FAB ->
            androidx.compose.material3.ExtendedFloatingActionButton(
            onClick = onClick,
            icon = {
                AppIcon(
                    painter = topicIcon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            },
            text = { AppText("参与话题", fontWeight = FontWeight.SemiBold) },
            modifier = buttonModifier,
        )
    }
}

@Composable
internal fun TopicTagCapsule(
    modifier: Modifier = Modifier,
    size: Dp = TOPIC_TAG_CAPSULE_SIZE_DP.dp,
    iconSize: Dp = TOPIC_TAG_CAPSULE_ICON_SIZE_DP.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(AppShapes.container(ContainerLevel.Chip))
            .background(AppContentColors.accent()),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(
            painter = painterResource(R.drawable.ms_tag_24),
            contentDescription = null,
            tint = AppContentColors.onAccent(),
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun TopicHeaderCard(
    details: TopicTopDetails?,
    onUserClick: (Long) -> Unit,
) {
    val topic = details?.topicItem
    val creator = details?.topicCreator
    val topicDescription = topic?.description
    val creatorName = creator?.name
    val creatorFace = creator?.face
    AppSurface(
        shape = AppShapes.container(ContainerLevel.Chip),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(Modifier.fillMaxWidth().heightIn(min = 160.dp).aspectRatio(1125f / 720f)) {
            AsyncImage(
                model = R.drawable.bili_topic_header,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.BottomCenter,
                modifier = Modifier.matchParentSize(),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                TopicTagCapsule(
                    modifier = Modifier.size(TOPIC_TAG_CAPSULE_SIZE_DP.dp),
                    iconSize = TOPIC_TAG_CAPSULE_ICON_SIZE_DP.dp,
                )
                Spacer(modifier = Modifier.width(AppSpacingTokens.Medium))
                Column(modifier = Modifier.weight(1f)) {
                    AppText(
                        text = topic?.name.orEmpty().ifBlank { "话题" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = androidx.compose.ui.graphics.Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!topicDescription.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(AppSpacingTokens.ExtraSmall))
                        AppText(
                            text = topicDescription,
                            style = MaterialTheme.typography.bodyMedium,
                            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(AppSpacingTokens.Small))
                    AppText(
                        text = buildString {
                            append("浏览 ${FormatUtils.formatStat(topic?.view ?: 0)}")
                            append(" · 讨论 ${FormatUtils.formatStat(topic?.discuss ?: 0)}")
                            if (!creatorName.isNullOrBlank()) {
                                append(" · $creatorName")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f)
                    )
                }
                if (!creatorFace.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(normalizeSearchImageUrl(creatorFace))
                            .crossfade(true)
                            .build(),
                        contentDescription = creatorName,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .appActionClickable(enabled = (creator?.uid ?: 0L) > 0L) {
                                onUserClick(creator?.uid ?: 0L)
                            },
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
    }
}
