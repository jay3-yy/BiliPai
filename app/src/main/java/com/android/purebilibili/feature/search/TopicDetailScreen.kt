package com.android.purebilibili.feature.search
import com.android.purebilibili.core.ui.components.appElasticPress


import coil3.request.crossfade

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.android.purebilibili.core.ui.skeleton.rememberContentSkeletonBlockColor
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
import com.android.purebilibili.feature.dynamic.components.rememberDynamicFeedSkeletonPulseState
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
    onDynamicDetailClick: (String) -> Unit
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
    val topicBackdrop = if (liquidGlassEnabled && !showInitialSkeleton) rememberLayerBackdrop() else null
    var showPublishComposer by remember { mutableStateOf(false) }

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
                onClick = { showPublishComposer = true },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (topicBackdrop != null) Modifier.layerBackdrop(topicBackdrop) else Modifier)
                .globalWallpaperAwareBackground()
        ) {
            when {
                showInitialSkeleton -> {
                    TopicDetailLoadingSkeleton(modifier = Modifier.fillMaxSize())
                }
                state.error != null && state.details == null && state.items.isEmpty() -> {
                    AppText(
                        text = state.error ?: "话题加载失败",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
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
                        item {
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
                        itemsIndexed(state.items, key = { _, item -> item.id_str }) { index, item ->
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
                                    ),
                                    interaction = DynamicCardInteractionActions(
                                        onCommentClick = onDynamicDetailClick,
                                    ),
                                ),
                            )
                            if (index == state.items.size - 3 && state.hasMore && !state.isLoadingMore) {
                                LaunchedEffect(state.offset) {
                                    viewModel.loadMore()
                                }
                            }
                        }
                        if (state.isLoadingMore) {
                            item {
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
        DynamicPublishComposer(
            initialDraft = com.android.purebilibili.data.model.response.DynamicPublishDraft(
                text = "",
                topic = topic?.takeIf { it.id > 0L }?.let {
                    DynamicPublishTopic(id = it.id, name = it.name)
                },
            ),
            isEditing = false,
            submitting = state.isPublishing,
            errorMessage = state.publishError,
            onDismiss = { showPublishComposer = false },
            onSubmit = { draft ->
                viewModel.publish(context, draft) { success, message ->
                    android.widget.Toast.makeText(
                        context,
                        message,
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                    if (success) showPublishComposer = false
                }
            },
        )
    }
}

@Composable
private fun TopicDetailLoadingSkeleton(modifier: Modifier = Modifier) {
    val dynamicPulseState = rememberDynamicFeedSkeletonPulseState()
    // provider 保持稳定实例,骨架卡的脉冲值在 draw 阶段读取,卡片内容不逐帧重组。
    val dynamicPulseProvider = remember(dynamicPulseState) { { dynamicPulseState.value } }
    val headerBlockColor = rememberContentSkeletonBlockColor(dynamicPulseProvider())
    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = false,
        modifier = modifier,
    ) {
        item {
            AppSurface(
                shape = AppShapes.container(ContainerLevel.Chip),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ContentSkeletonBlock(
                        color = headerBlockColor,
                        shape = AppShapes.container(ContainerLevel.Chip),
                        modifier = Modifier.size(58.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ContentSkeletonBlock(
                            color = headerBlockColor,
                            modifier = Modifier
                                .fillMaxWidth(0.62f)
                                .height(18.dp),
                        )
                        ContentSkeletonBlock(
                            color = headerBlockColor,
                            modifier = Modifier
                                .fillMaxWidth(0.88f)
                                .height(13.dp),
                        )
                        ContentSkeletonBlock(
                            color = headerBlockColor,
                            modifier = Modifier
                                .fillMaxWidth(0.56f)
                                .height(12.dp),
                        )
                    }
                    ContentSkeletonBlock(
                        color = headerBlockColor,
                        shape = CircleShape,
                        modifier = Modifier.size(34.dp),
                    )
                }
            }
        }
        items(6) {
            DynamicFeedSkeletonCard(pulse = dynamicPulseProvider)
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
                    .clickable(onClick = onClick)
                    .padding(horizontal = AppSpacingTokens.Medium),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(
                    painter = topicIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(AppSpacingTokens.Small))
                AppText(
                    text = "参与话题",
                    color = MaterialTheme.colorScheme.primary,
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
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(
            painter = painterResource(R.drawable.ms_tag_24),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
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
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!topicDescription.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(AppSpacingTokens.ExtraSmall))
                    AppText(
                        text = topicDescription,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
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
                        .clickable(enabled = (creator?.uid ?: 0L) > 0L) {
                            onUserClick(creator?.uid ?: 0L)
                        },
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}
