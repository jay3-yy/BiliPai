// 文件路径: feature/bangumi/BangumiDetailScreen.kt
package com.android.purebilibili.feature.bangumi

import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.resolveFilledButtonContainerColor
import com.android.purebilibili.core.ui.resolveFilledButtonContentColor
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.components.AppSingleChoiceRow
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppHorizontalDivider

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import com.android.purebilibili.core.ui.components.AppLazyVerticalGrid as LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
//  Material Icons
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.android.purebilibili.core.theme.resolveAdaptivePrimaryAccentColors
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.ImmersiveAppScaffold as AppScaffold
import com.android.purebilibili.core.ui.AppTopBar
import com.android.purebilibili.core.ui.rememberAppBackIcon
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppOutlinedButton
import com.android.purebilibili.core.ui.components.AppOutlinedTextField
import com.android.purebilibili.core.ui.components.AppStatusBadge
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.data.model.response.BangumiDetail
import com.android.purebilibili.data.model.response.BangumiEpisode
import com.android.purebilibili.core.util.LocalWindowSizeClass
import androidx.compose.ui.platform.LocalConfiguration
import com.android.purebilibili.core.util.responsiveContentWidth
// [重构] 使用提取的可复用组件
import com.android.purebilibili.feature.bangumi.ui.detail.RatingRow
import com.android.purebilibili.feature.bangumi.ui.detail.rememberBangumiEpisodeProgressLookup
import com.android.purebilibili.feature.bangumi.ui.detail.FollowButton
import com.android.purebilibili.feature.bangumi.ui.detail.SeasonSelector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.skeleton.PosterDetailSkeleton

/**
 * 番剧详情页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BangumiDetailScreen(
    seasonId: Long,
    epId: Long = 0,
    mediaId: Long = 0,
    onBack: () -> Unit,
    onEpisodeClick: (Long, BangumiEpisode) -> Unit,  // 点击剧集播放
    onSeasonClick: (Long) -> Unit = {},        //  点击切换季度
    onReviewsClick: (Long, String) -> Unit = { _, _ -> },
    onUserClick: (Long) -> Unit = {},
    viewModel: BangumiViewModel = viewModel(),
    modifier: Modifier = Modifier,
    contentTransitionReady: Boolean = true,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Keep the entering container stable while bounds animate. Never replace loaded content on return.
    val detailState = if (contentTransitionReady) uiState.detailState else BangumiDetailState.Loading

    // 加载详情
    LaunchedEffect(seasonId, epId, mediaId) {
        viewModel.loadSeasonDetail(seasonId = seasonId, epId = epId, mediaId = mediaId)
    }

    AppScaffold(
        modifier = modifier,
        blurContentReady = detailState !is BangumiDetailState.Loading,
        topBar = {
            val isCourse = (detailState as? BangumiDetailState.Success)?.detail?.let {
                it.seasonType == 10
            } == true
            AppTopBar(
                title = if (isCourse) "课程详情" else "番剧详情",
                navigationIcon = {
                    AppIconButton(onClick = onBack) {
                        AppIcon(rememberAppBackIcon(), contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        when (val state = detailState) {
            is BangumiDetailState.Loading -> {
                PosterDetailSkeleton(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                )
            }
            is BangumiDetailState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AppText(
                            text = state.message,
                            color = AppContentColors.error()
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        AppButton(
                            onClick = {
                                viewModel.loadSeasonDetail(
                                    seasonId = seasonId,
                                    epId = epId,
                                    mediaId = mediaId
                                )
                            }
                        ) {
                            AppText("重试")
                        }
                    }
                }
            }
            is BangumiDetailState.Success -> {
                val actionSeasonId = resolveBangumiActionSeasonId(
                    routeSeasonId = seasonId,
                    detailSeasonId = state.detail.seasonId
                )
                if (LocalWindowSizeClass.current.shouldUseSplitLayout) {
                    TabletBangumiDetailContent(
                        detail = state.detail,
                        paddingValues = paddingValues,
                        onEpisodeClick = { episode -> onEpisodeClick(actionSeasonId, episode) },
                        onSeasonClick = onSeasonClick,
                        onFollowStatusSelect = { status ->
                            viewModel.updateFollowStatus(actionSeasonId, status)
                        },
                        onReviewsClick = onReviewsClick,
                        onUserClick = onUserClick
                    )
                } else {
                    MobileBangumiDetailContent(
                        detail = state.detail,
                        paddingValues = paddingValues,
                        onEpisodeClick = { episode -> onEpisodeClick(actionSeasonId, episode) },
                        onSeasonClick = onSeasonClick,
                        onFollowStatusSelect = { status ->
                            viewModel.updateFollowStatus(actionSeasonId, status)
                        },
                        onReviewsClick = onReviewsClick,
                        onUserClick = onUserClick
                    )
                }
            }
        }
    }
}

@Composable
private fun TabletBangumiDetailContent(
    detail: BangumiDetail,
    paddingValues: PaddingValues,
    onEpisodeClick: (BangumiEpisode) -> Unit,
    onSeasonClick: (Long) -> Unit,
    onFollowStatusSelect: (Int) -> Unit,
    onReviewsClick: (Long, String) -> Unit,
    onUserClick: (Long) -> Unit = {}
) {
    // 状态管理
    val isFollowing = isBangumiFollowed(detail.userStatus)
    val coverUrl = if (detail.seasonType == 10) {
        FormatUtils.resolveVideoCoverUrl(detail.cover, useLowQuality = false)
    } else {
        FormatUtils.fixImageUrl(detail.cover)
    }
    var showFollowStatusDialog by remember { mutableStateOf(false) }

    // 选集相关状态
    var showJumpDialog by remember { mutableStateOf(false) }
    var jumpInputText by remember { mutableStateOf("") }
    var jumpErrorMessage by remember { mutableStateOf<String?>(null) }
    var episodesDescending by remember(detail.seasonId) { mutableStateOf(false) }
    var selectedEpisodePage by remember(detail.seasonId, detail.episodes?.size) {
        mutableIntStateOf(0)
    }
    val allEpisodes = detail.episodes.orEmpty()
    val orderedEpisodes = remember(allEpisodes, episodesDescending) {
        orderBangumiEpisodes(allEpisodes, episodesDescending)
    }
    val episodesPerPage = 50
    val episodePageCount = resolveBangumiEpisodePageCount(orderedEpisodes.size, episodesPerPage)
    val displayedEpisodes = remember(orderedEpisodes, selectedEpisodePage) {
        if (episodePageCount <= 1) {
            orderedEpisodes
        } else {
            val safePage = selectedEpisodePage.coerceIn(0, episodePageCount - 1)
            val start = safePage * episodesPerPage
            orderedEpisodes.subList(start, minOf(start + episodesPerPage, orderedEpisodes.size))
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        // LEFT PANE: Info & Introduction (40%)
        Column(
            modifier = Modifier
                .weight(4f)
                .fillMaxHeight()
                .padding(end = 24.dp)
                .responsiveContentWidth() // Double check constraint
                .background(MaterialTheme.colorScheme.surface)
        ) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header (Cover + Title)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Cover
                        AsyncImage(
                            model = coverUrl,
                            contentDescription = detail.title,
                            modifier = Modifier
                                .width(140.dp)
                                .aspectRatio(0.75f)
                                .clip(AppShapes.container(ContainerLevel.Card)),
                            contentScale = ContentScale.Crop
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        // Title & Stats
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppText(
                                text = detail.title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = AppContentColors.primaryText(),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )

                            // Rating
                            detail.rating?.let { rating ->
                                if (rating.score > 0) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        AppIcon(
                                            Icons.Outlined.Star,
                                            contentDescription = null,
                                            tint = AppContentColors.rating(), // Assuming this is available
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        AppText(
                                            text = String.format("%.1f", rating.score),
                                            color = AppContentColors.rating(),
                                            fontWeight = FontWeight.Bold
                                        )
                                        AppText(
                                            text = " (${rating.count}人评分)",
                                            color = AppContentColors.secondaryText(),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }

                            // Stats
                            detail.stat?.let { stat ->
                                val isCourse = detail.seasonType == 10
                                val followVerb = if (isCourse) "收藏" else "追番"
                                AppText(
                                    text = "${FormatUtils.formatStat(stat.views)}播放 · ${FormatUtils.formatStat(stat.favorites)}$followVerb",
                                    color = AppContentColors.secondaryText(),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                // UP 主信息（课堂/课程或合作视频）
                detail.upInfo?.let { up ->
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(AppShapes.container(ContainerLevel.Card))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .clickable(enabled = up.mid > 0L) { onUserClick(up.mid) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = FormatUtils.fixImageUrl(up.avatar),
                                contentDescription = up.uname,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                AppText(
                                    text = up.uname,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val subText = if (up.follower > 0L) {
                                    "${FormatUtils.formatStat(up.follower)}粉丝"
                                } else {
                                    up.brief.orEmpty()
                                }
                                if (subText.isNotBlank()) {
                                    AppText(
                                        text = subText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AppContentColors.secondaryText(),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // Action Buttons
                item {
                    val isCourse = detail.seasonType == 10
                    val targetEpisode = remember(detail) {
                        val lastEpId = detail.userStatus?.progress?.lastEpId ?: 0L
                        detail.episodes?.firstOrNull { it.id == lastEpId } ?: detail.episodes?.firstOrNull()
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (targetEpisode != null) {
                            AppButton(
                                onClick = { onEpisodeClick(targetEpisode) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                modifier = Modifier.weight(1f).height(48.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = resolveFilledButtonContainerColor(MaterialTheme.colorScheme),
                                    contentColor = resolveFilledButtonContentColor(MaterialTheme.colorScheme)
                                )
                            ) {
                                AppIcon(
                                    Icons.Outlined.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                val playLabel = if (isCourse) {
                                    if ((detail.userStatus?.progress?.lastEpId ?: 0L) > 0L) "继续学习" else "开始学习"
                                } else {
                                    if ((detail.userStatus?.progress?.lastEpId ?: 0L) > 0L) "继续观看" else "立即播放"
                                }
                                AppText(
                                    text = playLabel,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        // Follow Button
                        AppButton(
                            onClick = {
                                if (isFollowing) {
                                    showFollowStatusDialog = true
                                } else {
                                    onFollowStatusSelect(BANGUMI_FOLLOW_STATUS_WATCHING)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isFollowing || targetEpisode != null) MaterialTheme.colorScheme.surfaceVariant else AppContentColors.accent(),
                                contentColor = if (isFollowing || targetEpisode != null) AppContentColors.secondaryText() else AppContentColors.onAccent()
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1f).height(48.dp),
                        ) {
                            AppIcon(
                                if (isFollowing) Icons.Outlined.Check else Icons.Outlined.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            AppText(if (isCourse) {
                                if (isFollowing) "已收藏" else "收藏"
                            } else {
                                resolveBangumiFollowStatusLabel(detail.userStatus)
                            })
                        }
                        if (canReviewBangumi(detail.mediaId, detail.rights)) {
                            AppOutlinedButton(
                                onClick = { onReviewsClick(detail.mediaId, detail.title) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                modifier = Modifier.weight(1f).height(48.dp),
                            ) {
                                AppText("点评")
                            }
                        }
                    }
                }

                item {
                    BangumiDetailMetaSection(detail = detail)
                }

                // Introduction
                if (detail.evaluate.isNotEmpty()) {
                    item {
                        Column {
                            AppText(
                                text = "简介",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            AppText(
                                text = detail.evaluate,
                                color = AppContentColors.secondaryText(),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                // 课程概述图片 (PUGV brief images)
                if (!detail.briefImgs.isNullOrEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AppText(
                                text = "课程概述",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            detail.briefImgs.orEmpty().forEach { briefImg ->
                                if (briefImg.url.isNotBlank()) {
                                    val ratio = (1f / briefImg.aspectRatio.coerceAtLeast(0.1f)).coerceIn(0.2f, 5f)
                                    AsyncImage(
                                        model = FormatUtils.resolveVideoCoverUrl(
                                            briefImg.url,
                                            useLowQuality = false
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(ratio)
                                            .clip(AppShapes.container(ContainerLevel.Card)),
                                        contentScale = ContentScale.FillWidth
                                    )
                                }
                            }
                        }
                    }
                }
                if (detail.actors.isNotBlank() || detail.staff.isNotBlank()) {
                    item {
                        BangumiCreditsSection(
                            detail = detail,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // RIGHT PANE: Episodes & Seasons (60%)
        Column(
            modifier = Modifier
                .weight(6f)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.3f)) // Distinct background
        ) {
            // Re-implementing correctly using a single LazyVerticalGrid
            LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                contentPadding = PaddingValues(24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                 // Header: Episodes Title
                 if (allEpisodes.isNotEmpty()) {
                     item(span = { GridItemSpan(maxLineSpan) }) {
                         Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom=8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppText(
                                text = "选集 (${allEpisodes.size})",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AppTextButton(onClick = {
                                    episodesDescending = !episodesDescending
                                    selectedEpisodePage = 0
                                }) { AppText(if (episodesDescending) "倒序" else "正序") }
                                AppTextButton(onClick = {
                                    jumpInputText = ""
                                    jumpErrorMessage = null
                                    showJumpDialog = true
                                }) { AppText("跳转") }
                            }
                        }
                     }

                     if (episodePageCount > 1) {
                         item(span = { GridItemSpan(maxLineSpan) }) {
                             LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                 items(episodePageCount, key = { it }) { page ->
                                     val selected = page == selectedEpisodePage
                                     AppSurface(
                                         onClick = { selectedEpisodePage = page },
                                         color = if (selected) AppContentColors.accent() else MaterialTheme.colorScheme.surfaceVariant,
                                         shape = AppShapes.container(ContainerLevel.Card)
                                     ) {
                                         AppText(
                                             text = resolveBangumiEpisodePageLabel(
                                                 episodeCount = allEpisodes.size,
                                                 page = page,
                                                 episodesPerPage = episodesPerPage,
                                                 descending = episodesDescending
                                             ),
                                             modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                             style = MaterialTheme.typography.bodySmall,
                                             color = if (selected) AppContentColors.onAccent() else AppContentColors.secondaryText()
                                         )
                                     }
                                 }
                             }
                         }
                     }

                     items(displayedEpisodes, key = { it.id }) { episode ->
                         EpisodeChip(
                                episode = episode,
                                progressFraction = rememberBangumiEpisodeProgressLookup()(episode),
                                onClick = { onEpisodeClick(episode) }
                         )
                     }
                 }

                 detail.section.orEmpty()
                     .filter { !it.episodes.isNullOrEmpty() }
                     .forEachIndexed { index, section ->
                         item(span = { GridItemSpan(maxLineSpan) }) {
                             AppText(
                                 text = resolveBangumiSectionTitle(section, index),
                                 modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                                 style = MaterialTheme.typography.titleLarge,
                                 fontWeight = FontWeight.Bold
                             )
                         }

                         items(section.episodes.orEmpty(), key = { it.id }) { episode ->
                             EpisodeChip(
                                 episode = episode,
                                 progressFraction = rememberBangumiEpisodeProgressLookup()(episode),
                                 onClick = { onEpisodeClick(episode) }
                             )
                         }
                     }

                 // Related Seasons
                 if (!detail.seasons.isNullOrEmpty() && detail.seasons.orEmpty().size > 1) {
                     item(span = { GridItemSpan(maxLineSpan) }) {
                         AppText(
                            text = "相关季度",
                            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                     }

                     items(detail.seasons.orEmpty(), key = { it.seasonId }) { season ->
                         val isCurrentSeason = season.seasonId == detail.seasonId
                         AppSurface(
                            onClick = { if (!isCurrentSeason) onSeasonClick(season.seasonId) },
                            shape = AppShapes.container(ContainerLevel.Chip),
                            color = if (isCurrentSeason) AppContentColors.accent() else MaterialTheme.colorScheme.surface,
                            modifier = Modifier.height(48.dp) // Fixed height for consistency
                         ) {
                             Box(contentAlignment = Alignment.Center) {
                                 AppText(
                                    text = season.seasonTitle.ifEmpty { season.title },
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isCurrentSeason) AppContentColors.onAccent() else AppContentColors.primaryText(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                             }
                         }
                     }
                 }
            }
        }
    }

    // Dialogs (Shared logic)
    if (showJumpDialog && !detail.episodes.isNullOrEmpty()) {
         AppAlertDialog(
            onDismissRequest = { showJumpDialog = false },
            title = { AppText("跳转到第几集") },
            text = {
                Column {
                    AppOutlinedTextField(
                        value = jumpInputText,
                        onValueChange = {
                            jumpInputText = it.filter { char -> char.isDigit() }
                            jumpErrorMessage = null
                        },
                        label = { AppText("集数 (1-${detail.episodes.orEmpty().size})") },
                        singleLine = true,
                        isError = jumpErrorMessage != null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (jumpErrorMessage != null) {
                        AppText(
                            text = jumpErrorMessage!!,
                            color = AppContentColors.error(),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                AppTextButton(
                    onClick = {
                        val epNumber = jumpInputText.toIntOrNull()
                        if (epNumber == null || epNumber < 1 || epNumber > detail.episodes.orEmpty().size) {
                            jumpErrorMessage = "请输入 1-${detail.episodes.orEmpty().size} 之间的数字"
                        } else {
                            val targetEpisode = detail.episodes.orEmpty().getOrNull(epNumber - 1)
                            if (targetEpisode != null) {
                                onEpisodeClick(targetEpisode)
                            }
                            showJumpDialog = false
                        }
                    }
                ) { AppText("跳转") }
            },
            dismissButton = {
                AppTextButton(onClick = { showJumpDialog = false }) { AppText("取消") }
            }
        )
    }
    if (showFollowStatusDialog) {
        val isCourse = detail.seasonType == 10
        BangumiFollowStatusDialog(
            currentStatus = detail.userStatus?.followStatus ?: 0,
            isCourse = isCourse,
            onSelect = { status ->
                showFollowStatusDialog = false
                onFollowStatusSelect(status)
            },
            onDismiss = { showFollowStatusDialog = false }
        )
    }
}

@Composable
private fun MobileBangumiDetailContent(
    detail: BangumiDetail,
    paddingValues: PaddingValues,
    onEpisodeClick: (BangumiEpisode) -> Unit,
    onSeasonClick: (Long) -> Unit,
    onFollowStatusSelect: (Int) -> Unit,
    onReviewsClick: (Long, String) -> Unit,
    onUserClick: (Long) -> Unit = {}
) {
    //  [修复] 使用 detail 本身作为 key，这样当 ViewModel 更新 detail 时，状态会正确同步
    val isFollowing = isBangumiFollowed(detail.userStatus)
    val coverUrl = if (detail.seasonType == 10) {
        FormatUtils.resolveVideoCoverUrl(detail.cover, useLowQuality = false)
    } else {
        FormatUtils.fixImageUrl(detail.cover)
    }
    var showFollowStatusDialog by remember { mutableStateOf(false) }

    //  [修复] 移除 LaunchedEffect，避免重置用户的点击状态
    // 状态同步现在通过 remember 的 key 来实现

    //  选集相关状态（必须在函数顶层定义）
    var showEpisodeSheet by remember { mutableStateOf(false) }
    var showJumpDialog by remember { mutableStateOf(false) }
    var jumpInputText by remember { mutableStateOf("") }
    var jumpErrorMessage by remember { mutableStateOf<String?>(null) }
    var selectedPreviewPage by remember(detail.seasonId, detail.episodes?.size) {
        mutableIntStateOf(0)
    }
    var episodesDescending by remember(detail.seasonId) { mutableStateOf(false) }
    val orderedEpisodes = remember(detail.episodes, episodesDescending) {
        orderBangumiEpisodes(detail.episodes.orEmpty(), episodesDescending)
    }

    val detailListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val heroHeight = 320.dp
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = detailListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // 头部封面和信息
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(heroHeight)
                ) {
                    BangumiDetailHeroBackdrop(
                        listState = detailListState,
                        height = heroHeight,
                        topInset = paddingValues.calculateTopPadding(),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        // 封面背景（模糊）
                        AsyncImage(
                            model = coverUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )

                        // 渐变遮罩
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.3f),
                                            Color.Black.copy(alpha = 0.8f)
                                        )
                                    )
                                )
                        )
                    }

                    // 信息区域
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(16.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        // 封面图
                        AsyncImage(
                            model = coverUrl,
                            contentDescription = detail.title,
                            modifier = Modifier
                                .width(120.dp)
                                .aspectRatio(0.75f)
                                .clip(AppShapes.container(ContainerLevel.Chip)),
                            contentScale = ContentScale.Crop
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        // 标题和信息
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            AppText(
                                text = detail.title,
                                color = Color.White,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // 评分
                            detail.rating?.let { rating ->
                                if (rating.score > 0) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        AppIcon(
                                            Icons.Outlined.Star,
                                            contentDescription = null,
                                            tint = AppContentColors.rating(),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        AppText(
                                            text = String.format("%.1f", rating.score),
                                            color = AppContentColors.rating(),
                                            fontWeight = FontWeight.Bold
                                        )
                                        AppText(
                                            text = " (${rating.count}人评分)",
                                            color = Color.White.copy(alpha = 0.7f),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // 更新状态
                            detail.newEp?.desc?.let { desc ->
                                AppText(
                                    text = desc,
                                    color = Color.White.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // 播放量
                            detail.stat?.let { stat ->
                                val isCourse = detail.seasonType == 10
                                val followVerb = if (isCourse) "收藏" else "追番"
                                AppText(
                                    text = "${FormatUtils.formatStat(stat.views)}播放 · ${FormatUtils.formatStat(stat.favorites)}$followVerb",
                                    color = Color.White.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            // UP 主信息（课堂/课程或合作视频）
            detail.upInfo?.let { up ->
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clip(AppShapes.container(ContainerLevel.Card))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .clickable(enabled = up.mid > 0L) { onUserClick(up.mid) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = FormatUtils.fixImageUrl(up.avatar),
                            contentDescription = up.uname,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            AppText(
                                text = up.uname,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val subText = if (up.follower > 0L) {
                                "${FormatUtils.formatStat(up.follower)}粉丝"
                            } else {
                                up.brief.orEmpty()
                            }
                            if (subText.isNotBlank()) {
                                AppText(
                                    text = subText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppContentColors.secondaryText(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // 操作按钮
            item {
                val isCourse = detail.seasonType == 10
                val targetEpisode = remember(detail) {
                    val lastEpId = detail.userStatus?.progress?.lastEpId ?: 0L
                    detail.episodes?.firstOrNull { it.id == lastEpId } ?: detail.episodes?.firstOrNull()
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (targetEpisode != null) {
                        AppButton(
                            onClick = { onEpisodeClick(targetEpisode) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1f).height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = resolveFilledButtonContainerColor(MaterialTheme.colorScheme),
                                contentColor = resolveFilledButtonContentColor(MaterialTheme.colorScheme)
                            )
                        ) {
                            AppIcon(
                                Icons.Outlined.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            val playLabel = if (isCourse) {
                                if ((detail.userStatus?.progress?.lastEpId ?: 0L) > 0L) "继续学习" else "开始学习"
                            } else {
                                if ((detail.userStatus?.progress?.lastEpId ?: 0L) > 0L) "继续观看" else "立即播放"
                            }
                            AppText(
                                text = playLabel,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    // 追番/收藏按钮
                    if (isFollowing) {
                        //  已追番/已收藏：使用带边框的样式，更清晰可见
                        AppOutlinedButton(
                            onClick = {
                                showFollowStatusDialog = true
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = AppContentColors.accent()
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                AppContentColors.accent()
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            AppIcon(
                                Icons.Outlined.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            AppText(if (isCourse) "已收藏" else resolveBangumiFollowStatusLabel(detail.userStatus))
                        }
                    } else {
                        //  未追番/未收藏：使用填充的主色按钮
                        AppButton(
                            onClick = {
                                onFollowStatusSelect(BANGUMI_FOLLOW_STATUS_WATCHING)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (targetEpisode != null) MaterialTheme.colorScheme.surfaceVariant else resolveFilledButtonContainerColor(MaterialTheme.colorScheme),
                                contentColor = if (targetEpisode != null) AppContentColors.secondaryText() else resolveFilledButtonContentColor(MaterialTheme.colorScheme)
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            AppIcon(
                                Icons.Outlined.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            AppText(if (isCourse) "收藏" else "追番")
                        }
                    }
                    if (canReviewBangumi(detail.mediaId, detail.rights)) {
                        AppOutlinedButton(
                            onClick = { onReviewsClick(detail.mediaId, detail.title) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1f).height(48.dp),
                        ) {
                            AppText("点评")
                        }
                    }
                }
            }

            item {
                BangumiDetailMetaSection(
                    detail = detail,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // 简介（折叠：默认 3 行省略，点击展开/收起）
            if (detail.evaluate.isNotEmpty()) {
                item {
                    var isEvaluateExpanded by remember(detail.seasonId) {
                        mutableStateOf(false)
                    }
                    var isEvaluateOverflowing by remember(detail.seasonId) {
                        mutableStateOf(false)
                    }
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        AppText(
                            text = "简介",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        AppText(
                            text = detail.evaluate,
                            color = AppContentColors.secondaryText(),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = if (isEvaluateExpanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis,
                            onTextLayout = { isEvaluateOverflowing = it.hasVisualOverflow },
                            modifier = Modifier.clickable(
                                enabled = isEvaluateOverflowing || isEvaluateExpanded
                            ) {
                                isEvaluateExpanded = !isEvaluateExpanded
                            }
                        )
                    }
                }
            }

            // 课程概述图片 (PUGV brief images)
            if (!detail.briefImgs.isNullOrEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppText(
                            text = "课程概述",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        detail.briefImgs.orEmpty().forEach { briefImg ->
                            if (briefImg.url.isNotBlank()) {
                                val ratio = (1f / briefImg.aspectRatio.coerceAtLeast(0.1f)).coerceIn(0.2f, 5f)
                                AsyncImage(
                                    model = FormatUtils.resolveVideoCoverUrl(
                                        briefImg.url,
                                        useLowQuality = false
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(ratio)
                                        .clip(AppShapes.container(ContainerLevel.Card)),
                                    contentScale = ContentScale.FillWidth
                                )
                            }
                        }
                    }
                }
            }
            if (detail.actors.isNotBlank() || detail.staff.isNotBlank()) {
                item {
                    BangumiCreditsSection(
                        detail = detail,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            // 剧集列表
            if (!detail.episodes.isNullOrEmpty()) {
                item {
                    //  选集标题和快速跳转
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        AppText(
                            text = "选集 (${detail.episodes.orEmpty().size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppTextButton(onClick = {
                                episodesDescending = !episodesDescending
                                selectedPreviewPage = 0
                            }) {
                                AppText(if (episodesDescending) "倒序" else "正序")
                            }
                            AppSurface(
                                onClick = {
                                    jumpInputText = ""
                                    jumpErrorMessage = null
                                    showJumpDialog = true
                                },
                                color = Color.Transparent
                            ) {
                                AppText(
                                    text = "跳转",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = AppContentColors.secondaryText()
                                )
                            }
                        }
                    }
                }

                //  分页选择器（超过50集时显示）
                if (detail.episodes.orEmpty().size > 50) {
                    item {
                        val episodesPerPage = 50
                        val totalPages = (detail.episodes.orEmpty().size + episodesPerPage - 1) / episodesPerPage

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            items(totalPages, key = { it }) { page ->
                                val isCurrentPage = page == selectedPreviewPage

                                AppSurface(
                                    onClick = { selectedPreviewPage = page },
                                    color = if (isCurrentPage) AppContentColors.accent() else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = AppShapes.container(ContainerLevel.Card)
                                ) {
                                    AppText(
                                        text = resolveBangumiEpisodePageLabel(
                                            episodeCount = detail.episodes.orEmpty().size,
                                            page = page,
                                            episodesPerPage = episodesPerPage,
                                            descending = episodesDescending
                                        ),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isCurrentPage) AppContentColors.onAccent() else AppContentColors.secondaryText()
                                    )
                                }
                            }
                        }
                    }
                }

                //  剧集预览（只显示前6个，点击展开完整列表）
                item {
                    val previewEpisodes = if (detail.episodes.orEmpty().size > 50) {
                        val window = resolveBangumiEpisodePreviewWindow(
                            episodeCount = detail.episodes.orEmpty().size,
                            selectedPage = selectedPreviewPage,
                            episodesPerPage = 50,
                            previewCount = 6
                        )
                        orderedEpisodes.subList(window.startIndex, window.endExclusive)
                    } else {
                        orderedEpisodes.take(6)
                    }

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(previewEpisodes, key = { it.id }) { episode ->
                            EpisodeChip(
                                episode = episode,
                                progressFraction = rememberBangumiEpisodeProgressLookup()(episode),
                                onClick = { onEpisodeClick(episode) }
                            )
                        }

                        // 更多按钮
                        if (detail.episodes.orEmpty().size > 6) {
                            item {
                                AppSurface(
                                    onClick = { showEpisodeSheet = true },
                                    modifier = Modifier
                                        .width(80.dp)
                                        .aspectRatio(16f / 9f),
                                    shape = AppShapes.container(ContainerLevel.Chip),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            AppIcon(
                                                Icons.Outlined.MoreHoriz,
                                                contentDescription = "更多",
                                                tint = AppContentColors.secondaryText()
                                            )
                                            AppText(
                                                text = "全部${detail.episodes.orEmpty().size}集",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = AppContentColors.secondaryText()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            detail.section.orEmpty()
                .filter { !it.episodes.isNullOrEmpty() }
                .forEachIndexed { index, section ->
                    item {
                        BangumiSectionPreview(
                            title = resolveBangumiSectionTitle(section, index),
                            episodes = section.episodes.orEmpty(),
                            onEpisodeClick = onEpisodeClick,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                }

            // 相关季度
            if (!detail.seasons.isNullOrEmpty() && detail.seasons.orEmpty().size > 1) {
                item {
                    AppText(
                        text = "相关季度",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(detail.seasons.orEmpty(), key = { it.seasonId }) { season ->
                            val isCurrentSeason = season.seasonId == detail.seasonId
                            AppSurface(
                                modifier = Modifier.clickable {
                                    if (!isCurrentSeason) {
                                        onSeasonClick(season.seasonId)
                                    }
                                },
                                shape = AppShapes.container(ContainerLevel.Chip),
                                color = if (isCurrentSeason) {
                                    AppContentColors.accent()
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            ) {
                                AppText(
                                    text = season.seasonTitle.ifEmpty { season.title },
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isCurrentSeason) {
                                        AppContentColors.onAccent()
                                    } else {
                                        AppContentColors.primaryText()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        //  快速跳转对话框（在 LazyColumn 外部）
        if (showJumpDialog && !detail.episodes.isNullOrEmpty()) {
            com.android.purebilibili.core.ui.AppAlertDialog(
                onDismissRequest = { showJumpDialog = false },
                title = { AppText("跳转到第几集") },
                text = {
                    Column {
                        AppOutlinedTextField(
                            value = jumpInputText,
                            onValueChange = {
                                jumpInputText = it.filter { char -> char.isDigit() }
                                jumpErrorMessage = null
                            },
                            label = { AppText("集数 (1-${detail.episodes.orEmpty().size})") },
                            singleLine = true,
                            isError = jumpErrorMessage != null,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (jumpErrorMessage != null) {
                            AppText(
                                text = jumpErrorMessage!!,
                                color = AppContentColors.error(),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    com.android.purebilibili.core.ui.AppDialogAction(
                        onClick = {
                            val epNumber = jumpInputText.toIntOrNull()
                            if (epNumber == null || epNumber < 1 || epNumber > detail.episodes.orEmpty().size) {
                                jumpErrorMessage = "请输入 1-${detail.episodes.orEmpty().size} 之间的数字"
                            } else {
                                val targetEpisode = detail.episodes.orEmpty().getOrNull(epNumber - 1)
                                if (targetEpisode != null) {
                                    onEpisodeClick(targetEpisode)
                                }
                                showJumpDialog = false
                            }
                        }
                    ) {
                        AppText("跳转")
                    }
                },
                dismissButton = {
                    com.android.purebilibili.core.ui.AppDialogAction(onClick = { showJumpDialog = false }) {
                        AppText("取消")
                    }
                }
            )
        }

        //  官方风格：底部弹出选集面板（在 LazyColumn 外部）
        if (showEpisodeSheet && !detail.episodes.isNullOrEmpty()) {
            EpisodeSelectionSheet(
                detail = detail,
                onDismiss = { showEpisodeSheet = false },
                onEpisodeClick = { episode ->
                    onEpisodeClick(episode)
                    showEpisodeSheet = false
                },
                onSeasonClick = onSeasonClick
            )
        }
        if (showFollowStatusDialog) {
            val isCourse = detail.seasonType == 10
            BangumiFollowStatusDialog(
                currentStatus = detail.userStatus?.followStatus ?: 0,
                isCourse = isCourse,
                onSelect = { status ->
                    showFollowStatusDialog = false
                    onFollowStatusSelect(status)
                },
                onDismiss = { showFollowStatusDialog = false }
            )
        }
    }
}

@Composable
private fun BangumiDetailMetaSection(
    detail: BangumiDetail,
    modifier: Modifier = Modifier
) {
    val metaChips = remember(detail) { resolveBangumiDetailMetaChips(detail) }
    val restrictionLabels = remember(detail) { resolveBangumiRestrictionLabels(detail) }
    if (metaChips.isEmpty() && restrictionLabels.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (metaChips.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 0.dp)
            ) {
                items(metaChips, key = { it }) { chip ->
                    AppStatusBadge(
                        label = chip,
                        emphasized = false,
                    )
                }
            }
        }
        if (restrictionLabels.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 0.dp)
            ) {
                items(restrictionLabels, key = { it }) { label ->
                    AppStatusBadge(
                        label = label,
                        emphasized = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun BangumiCreditsSection(
    detail: BangumiDetail,
    modifier: Modifier = Modifier
) {
    AppSurface(
        modifier = modifier,
        shape = AppShapes.container(ContainerLevel.Card),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppText(
                text = "演职人员",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            detail.actors.takeIf { it.isNotBlank() }?.let { actors ->
                BangumiCreditRow(label = "声优 / 演员", value = actors)
            }
            detail.staff.takeIf { it.isNotBlank() }?.let { staff ->
                BangumiCreditRow(label = "制作人员", value = staff)
            }
        }
    }
}

@Composable
private fun BangumiCreditRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        AppText(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = AppContentColors.accent(),
            fontWeight = FontWeight.Medium
        )
        AppText(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = AppContentColors.secondaryText()
        )
    }
}

@Composable
private fun BangumiSectionPreview(
    title: String,
    episodes: List<BangumiEpisode>,
    onEpisodeClick: (BangumiEpisode) -> Unit,
    modifier: Modifier = Modifier
) {
    if (episodes.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth()) {
        AppText(
            text = title,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(episodes.take(20), key = { it.id }) { episode ->
                EpisodeChip(
                    episode = episode,
                    progressFraction = rememberBangumiEpisodeProgressLookup()(episode),
                    onClick = { onEpisodeClick(episode) }
                )
            }
        }
    }
}

@Composable
private fun BangumiFollowStatusDialog(
    currentStatus: Int,
    isCourse: Boolean = false,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { AppText(if (isCourse) "收藏状态" else "追番状态") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BANGUMI_FOLLOW_STATUS_OPTIONS.forEach { option ->
                    AppSingleChoiceRow(
                        selected = currentStatus == option.status,
                        onClick = { onSelect(option.status) },
                        shape = AppShapes.container(ContainerLevel.Chip),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                            AppText(
                                text = option.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (currentStatus == option.status) {
                                    FontWeight.SemiBold
                                } else {
                                    FontWeight.Normal
                                },
                                color = AppSurfaceTokens.onSurfaceContainerHigh(),
                                modifier = Modifier.weight(1f)
                            )
                    }
                }
            }
        },
        confirmButton = {
            AppTextButton(onClick = { onSelect(BANGUMI_FOLLOW_STATUS_UNFOLLOW) }) {
                AppText(if (isCourse) "取消收藏" else "取消追番")
            }
        },
        dismissButton = {
            AppTextButton(onClick = onDismiss) {
                AppText("关闭")
            }
        }
    )
}

@Composable
private fun EpisodeChip(
    episode: BangumiEpisode,
    progressFraction: Float? = null,
    onClick: () -> Unit
) {
    //  带封面图的设计，集数和标题在同一行
    Column(
        modifier = Modifier
            .width(120.dp)
            .clickable(onClick = onClick)
    ) {
        // 缩略图
        AppSurface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
            shape = AppShapes.container(ContainerLevel.Chip),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box {
                AsyncImage(
                    model = FormatUtils.fixImageUrl(episode.cover),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // 角标（如：会员）
                if (episode.badge.isNotEmpty()) {
                    val badgeColors = com.android.purebilibili.feature.bangumi.bangumiBrandBadgeColors(episode.badge)
                        ?: resolveAdaptivePrimaryAccentColors(MaterialTheme.colorScheme)
                    AppSurface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp),
                        color = badgeColors.backgroundColor,
                        shape = AppShapes.container(ContainerLevel.Tag)
                    ) {
                        AppText(
                            text = episode.badge,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            color = badgeColors.contentColor,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                // 单集观看进度条
                if (progressFraction != null && progressFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressFraction.coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .background(AppContentColors.accent())
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        //  集数和标题在同一行：数字在左，标题在右
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // 集数数字
            AppText(
                text = episode.title.ifEmpty { episode.id.toString() },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = AppContentColors.primaryText()
            )

            // 标题
            if (episode.longTitle.isNotEmpty()) {
                Spacer(modifier = Modifier.width(6.dp))
                AppText(
                    text = episode.longTitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = AppContentColors.secondaryText(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 *  官方风格：底部弹出选集面板
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EpisodeSelectionSheet(
    detail: BangumiDetail,
    onDismiss: () -> Unit,
    onEpisodeClick: (BangumiEpisode) -> Unit,
    onSeasonClick: (Long) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sourceEpisodes = detail.episodes.orEmpty()
    var episodesDescending by remember(detail.seasonId) { mutableStateOf(false) }
    var selectedPage by remember(detail.seasonId, sourceEpisodes.size) { mutableIntStateOf(0) }
    val episodes = remember(sourceEpisodes, episodesDescending) {
        orderBangumiEpisodes(sourceEpisodes, episodesDescending)
    }

    com.android.purebilibili.core.ui.AppModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,  // 使用自定义标题栏
        windowInsets = WindowInsets(0.dp)  //  沉浸式
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)  // 占屏幕80%高度
                .navigationBarsPadding()  //  底部安全区域
        ) {
            //  标题栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AppText(
                    text = "选集 (${detail.episodes?.size ?: 0})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppTextButton(onClick = {
                        episodesDescending = !episodesDescending
                        selectedPage = 0
                    }) {
                        AppText(if (episodesDescending) "倒序" else "正序")
                    }
                    AppIconButton(onClick = onDismiss) {
                        AppIcon(
                            Icons.Outlined.Close,
                            contentDescription = "关闭",
                            tint = AppContentColors.secondaryText()
                        )
                    }
                }
            }

            //  季度标签（如果有多个季度）
            if (!detail.seasons.isNullOrEmpty() && detail.seasons.orEmpty().size > 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    items(detail.seasons.orEmpty(), key = { it.seasonId }) { season ->
                        val isCurrentSeason = season.seasonId == detail.seasonId

                        AppSurface(
                            onClick = {
                                if (!isCurrentSeason) {
                                    onSeasonClick(season.seasonId)
                                    onDismiss()
                                }
                            },
                            shape = AppShapes.container(ContainerLevel.Card),
                            color = if (isCurrentSeason) {
                                AppContentColors.accent()
                            } else {
                                Color.Transparent
                            }
                        ) {
                            AppText(
                                text = season.seasonTitle.ifEmpty { season.title },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isCurrentSeason) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCurrentSeason) {
                                    AppContentColors.onAccent()
                                } else {
                                    AppContentColors.secondaryText()
                                }
                            )
                        }
                    }
                }
            }

            //  更新信息
            detail.newEp?.desc?.let { desc ->
                AppText(
                    text = desc,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppContentColors.secondaryText()
                )
            }

            AppHorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )

            //  分页选择器（超过50集时显示）
            val episodesPerPage = 50
            val totalPages = resolveBangumiEpisodePageCount(episodes.size, episodesPerPage)

            if (totalPages > 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    items(totalPages, key = { it }) { page ->
                        val isCurrentPage = page == selectedPage

                        AppSurface(
                            onClick = { selectedPage = page },
                            color = if (isCurrentPage) AppContentColors.accent() else MaterialTheme.colorScheme.surfaceVariant,
                            shape = AppShapes.container(ContainerLevel.Card)
                        ) {
                            AppText(
                                text = resolveBangumiEpisodePageLabel(
                                    episodeCount = episodes.size,
                                    page = page,
                                    episodesPerPage = episodesPerPage,
                                    descending = episodesDescending
                                ),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isCurrentPage) AppContentColors.onAccent() else AppContentColors.secondaryText()
                            )
                        }
                    }
                }
            }

            //  剧集列表（两列网格布局）
            val displayEpisodes = if (totalPages > 1) {
                val pageStart = selectedPage * episodesPerPage
                val pageEnd = minOf(pageStart + episodesPerPage, episodes.size)
                episodes.subList(pageStart, pageEnd)
            } else {
                episodes
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(
                    count = displayEpisodes.size,
                    key = { index -> displayEpisodes[index].id }
                ) { index ->
                    val episode = displayEpisodes[index]
                    EpisodeListItem(
                        episode = episode,
                        progressFraction = rememberBangumiEpisodeProgressLookup()(episode),
                        onClick = { onEpisodeClick(episode) }
                    )
                }
            }
        }
    }
}

/**
 *  官方风格：剧集列表项（用于底部面板）
 */
@Composable
private fun EpisodeListItem(
    episode: BangumiEpisode,
    progressFraction: Float? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 缩略图
        Box(
            modifier = Modifier
                .width(80.dp)
                .aspectRatio(16f / 9f)
                .clip(AppShapes.container(ContainerLevel.Tag))
        ) {
            AsyncImage(
                model = FormatUtils.fixImageUrl(episode.cover),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // 单集观看进度条
            if (progressFraction != null && progressFraction > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progressFraction.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(AppContentColors.accent())
                    )
                }
            }

            // VIP 角标
            if (episode.badge.isNotEmpty()) {
                val badgeColors = com.android.purebilibili.feature.bangumi.bangumiBrandBadgeColors(episode.badge)
                        ?: resolveAdaptivePrimaryAccentColors(MaterialTheme.colorScheme)
                AppSurface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp),
                    color = badgeColors.backgroundColor,
                    shape = AppShapes.container(ContainerLevel.Tag)
                ) {
                    AppText(
                        text = episode.badge,
                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp),
                        color = badgeColors.contentColor,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // 剧集信息
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 集数
                AppText(
                    text = "第${episode.title}话",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = AppContentColors.primaryText(),
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (episode.duration > 0L) {
                    Spacer(modifier = Modifier.width(6.dp))
                    AppText(
                        text = FormatUtils.formatDuration((episode.duration / 1000L).toInt()),
                        style = MaterialTheme.typography.labelSmall,
                        color = AppContentColors.secondaryText()
                    )
                }
            }

            // 标题
            if (episode.longTitle.isNotEmpty()) {
                AppText(
                    text = episode.longTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppContentColors.secondaryText(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
