package com.android.purebilibili.feature.home

import androidx.compose.foundation.layout.*
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.GridCells
import com.android.purebilibili.core.ui.components.AppLazyVerticalGrid as LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppScaffold
import com.android.purebilibili.core.ui.AppTopBar
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.rememberAppBackIcon
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.ui.globalWallpaperAwareBackground
import com.android.purebilibili.core.ui.globalWallpaperAwareChromeColor
import com.android.purebilibili.core.ui.blur.BlurSurfaceType
import com.android.purebilibili.core.ui.blur.hazeSourceCompat
import com.android.purebilibili.core.ui.blur.recoverableBlurEnabled
import com.android.purebilibili.core.ui.blur.rememberChromeBackdropSource
import com.android.purebilibili.core.ui.blur.rememberRecoverableHazeState
import com.android.purebilibili.core.ui.blur.shouldAllowRenderEffectBackedHazeEffect
import com.android.purebilibili.core.ui.blur.unifiedBlur
import com.android.purebilibili.core.ui.performance.isLowBlurBudgetForced
import com.android.purebilibili.core.store.HomeSettings
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.util.LocalWindowSizeClass
import com.android.purebilibili.feature.home.components.BiliPaiImmersiveTopBar
import com.android.purebilibili.feature.home.components.shouldUseBiliPaiProgressiveTopBlur
import com.android.purebilibili.core.ui.components.*
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.feature.home.components.cards.ElegantVideoCard

@Composable
internal fun WeeklySeriesScreen(
    initialNumber: Int?,
    onBack: () -> Unit,
    onVideoClick: (VideoItem, List<VideoItem>) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WeeklySeriesViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val homeSettings by SettingsManager.getHomeSettings(context)
        .collectAsStateWithLifecycle(initialValue = HomeSettings())
    LaunchedEffect(viewModel, initialNumber) { viewModel.initialize(initialNumber) }
    WeeklySeriesContent(
        state = state,
        homeSettings = homeSettings,
        onBack = onBack,
        onVideoClick = { video -> onVideoClick(video, state.videos) },
        onSelect = viewModel::select,
        onRetry = viewModel::retry,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeeklySeriesContent(
    state: WeeklySeriesUiState,
    homeSettings: HomeSettings,
    onBack: () -> Unit,
    onVideoClick: (VideoItem) -> Unit,
    onSelect: (Int) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPeriods by rememberSaveable { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    LaunchedEffect(state.number) { gridState.scrollToItem(0) }
    val windowSizeClass = LocalWindowSizeClass.current
    val layoutDirection = LocalLayoutDirection.current
    val themeConfig = LocalAppThemeConfig.current
    val lowBlurBudget = isLowBlurBudgetForced()
    val hazeState = if (themeConfig.headerBlurEnabled && !lowBlurBudget &&
        shouldAllowRenderEffectBackedHazeEffect(android.os.Build.VERSION.SDK_INT)
    ) rememberRecoverableHazeState() else null
    val hazeActive = hazeState?.let { recoverableBlurEnabled(it) } == true
    val progressiveRequested = shouldUseBiliPaiProgressiveTopBlur(
        enabled = themeConfig.progressiveTopBlurEnabled && !themeConfig.headerBlurEnabled,
        hasBackdrop = true,
    ) && !lowBlurBudget
    // Keep one content source through loading/period changes. It never records the header itself.
    val chromeSource = if (progressiveRequested || themeConfig.liquidGlassEnabled) {
        rememberChromeBackdropSource()
    } else null
    val chromeBackdrop = chromeSource?.takeIf { it.isReady }?.backdrop
    val progressiveActive = progressiveRequested && chromeBackdrop != null
    val fadeActive = themeConfig.progressiveTopFadeEnabled && !themeConfig.headerBlurEnabled
    val chromeActive = hazeActive || progressiveActive || fadeActive
    val pageColor = MaterialTheme.colorScheme.background
    val chromeColor = globalWallpaperAwareChromeColor(pageColor)

    AppScaffold(
        modifier = modifier,
        containerColor = pageColor,
        topBar = {
            // Header effects follow their own settings in both glass and native MD3/Miuix.
            BiliPaiImmersiveTopBar(
                backdrop = chromeBackdrop,
                enabled = progressiveActive,
                headerBlurActive = hazeActive,
                fadeEnabled = fadeActive,
                surfaceColor = chromeColor,
                extendBelowBounds = false,
                modifier = if (hazeActive) {
                    Modifier
                        .unifiedBlur(hazeState = requireNotNull(hazeState), surfaceType = BlurSurfaceType.HEADER)
                        .background(chromeColor.copy(alpha = AppSurfaceTokens.FrostedScrimAlpha))
                } else Modifier,
            ) {
                CompositionLocalProvider(LocalAppTopBarIconBackdrop provides chromeBackdrop) {
                    AppTopBar(
                        title = "每周必看",
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = if (chromeActive) Color.Transparent else chromeColor,
                            scrolledContainerColor = if (chromeActive) Color.Transparent else chromeColor,
                        ),
                        navigationIcon = {
                            AppIconButton(onClick = onBack) { AppIcon(rememberAppBackIcon(), contentDescription = "返回") }
                        },
                        actions = {
                            AppTextButton(onClick = { showPeriods = true }, enabled = !state.loading, modifier = Modifier.heightIn(min = 48.dp)) {
                                AppText(state.number?.let { "第${it}期 · 选期" } ?: "选择期数")
                            }
                        }
                    )
                }
            }
        }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .then(chromeSource?.modifier ?: Modifier)
                .then(
                    if (hazeState != null &&
                        com.android.purebilibili.core.ui.blur.LocalChromeBackdropRecordingAllowed.current
                    ) Modifier.hazeSourceCompat(hazeState) else Modifier
                )
                .globalWallpaperAwareBackground(pageColor)
                .padding(
                    start = padding.calculateStartPadding(layoutDirection),
                    end = padding.calculateEndPadding(layoutDirection),
                ),
            contentAlignment = Alignment.TopCenter,
        ) {
            val contentWidth = minOf(maxWidth, resolveHomeFeedMaxContentWidth())
            val gridColumns = resolveHomeFeedGridColumns(
                contentWidthDp = contentWidth.value.toInt(),
                displayMode = homeSettings.displayMode,
                fixedColumnCount = resolveHomeFeedStoredColumnCount(
                    widthSizeClass = windowSizeClass.widthSizeClass,
                    compactColumnCount = homeSettings.effectiveGridColumnCountCompact,
                    defaultColumnCount = homeSettings.effectiveGridColumnCountForWindow(windowSizeClass),
                ),
                cardWidthPreset = homeSettings.homeFeedCardWidthPreset,
                widthSizeClass = windowSizeClass.widthSizeClass,
            )
            val cardLayout = resolveHomeFeedCardLayout(
                style = homeSettings.homeFeedCardStyle,
                gridColumns = gridColumns,
                widthSizeClass = windowSizeClass.widthSizeClass,
            )
            when {
                state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { AppCircularProgressIndicator() }
                state.error != null -> Column(
                    Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    AppText(state.error)
                    AppTextButton(onClick = onRetry) { AppText("重试") }
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(gridColumns),
                    state = gridState,
                    contentPadding = PaddingValues(
                        start = cardLayout.outerPaddingDp.dp,
                        end = cardLayout.outerPaddingDp.dp,
                        top = padding.calculateTopPadding() + cardLayout.outerPaddingDp.dp,
                        bottom = padding.calculateBottomPadding() + cardLayout.outerPaddingDp.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(cardLayout.itemSpacingDp.dp),
                    verticalArrangement = Arrangement.spacedBy(cardLayout.verticalItemSpacingDp.dp),
                    // Full-height viewport lets the scroll content pass underneath the sampled header.
                    modifier = Modifier
                        .width(contentWidth)
                        .fillMaxHeight(),
                ) {
                    if (state.label.isNotBlank() || state.subject.isNotBlank()) {
                        item(key = "weekly_description", span = { GridItemSpan(maxLineSpan) }) {
                            AppText(
                                listOf(state.label, state.subject).filter { it.isNotBlank() }.joinToString("\n"),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                    }
                    if (state.reminder.isNotBlank()) {
                        item(key = "weekly_reminder", span = { GridItemSpan(maxLineSpan) }) {
                            AppText(
                                state.reminder,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = AppSurfaceTokens.onSurfaceVariantSummary(),
                            )
                        }
                    }
                    if (state.videos.isEmpty()) {
                        item(key = "weekly_empty", span = { GridItemSpan(maxLineSpan) }) {
                            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                AppText("本期暂无视频")
                            }
                        }
                    }
                    val videoKeys = state.videos.mapIndexed { index, video -> "${video.bvid}:$index" }
                    val alignRows = gridColumns > 1 && !homeSettings.showFullVideoCardContent
                    val renderCard: @Composable (Int, Modifier) -> Unit = { index, cardModifier ->
                        val video = state.videos[index]
                        ElegantVideoCard(
                            video = video,
                            modifier = cardModifier,
                            index = index,
                            animationEnabled = false,
                            glassEnabled = false,
                            blurEnabled = false,
                            coverAspectRatio = cardLayout.coverAspectRatio,
                            compactMetadata = cardLayout.compactMetadata,
                            titleMinLines = cardLayout.titleMinLines,
                            titleMaxLines = cardLayout.titleMaxLines,
                            compactStatsOnCover = homeSettings.compactVideoStatsOnCover || cardLayout.compactStatsOnCover,
                            homeDurationStyle = homeSettings.homeDurationStyle,
                            showUpBadge = homeSettings.showHomeUpBadges,
                            showUpAvatar = homeSettings.showHomeUpAvatars,
                            onClick = { _, _ -> onVideoClick(video) },
                        )
                    }
                    if (!alignRows) {
                        itemsIndexed(state.videos, key = { index, _ -> videoKeys[index] }) { index, _ -> renderCard(index, Modifier) }
                    } else {
                        resolveHomeFeedAlignedRows(state.videos.size, gridColumns).forEach { row ->
                            item(key = "weekly_row_${videoKeys[row.first]}", span = { GridItemSpan(maxLineSpan) }) {
                                HomeAlignedVideoRow(
                                    indices = row,
                                    itemKeys = videoKeys,
                                    columns = gridColumns,
                                    horizontalArrangement = Arrangement.spacedBy(cardLayout.itemSpacingDp.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    content = renderCard,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (showPeriods) {
        AppAlertDialog(
            onDismissRequest = { showPeriods = false },
            title = { AppText("选择期数") },
            text = {
                if (state.periods.isEmpty()) {
                    Column {
                        AppText(state.periodsError ?: "暂无可选期数")
                        AppTextButton(onClick = { showPeriods = false; onRetry() }) { AppText("重试") }
                    }
                } else {
                    val periodListState = rememberLazyListState(
                        initialFirstVisibleItemIndex = state.periods.indexOfFirst { it.number == state.number }.coerceAtLeast(0)
                    )
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp), state = periodListState) {
                        items(state.periods, key = { it.number }) { period ->
                            AppSingleChoiceRow(
                                selected = period.number == state.number,
                                onClick = { showPeriods = false; onSelect(period.number) }
                            ) {
                                AppText(period.name.ifBlank { "第${period.number}期" }, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            },
            confirmButton = { AppTextButton(onClick = { showPeriods = false }) { AppText("关闭") } }
        )
    }
}
