// 文件路径: feature/bangumi/BangumiDetailScreen.kt
package com.android.purebilibili.feature.bangumi
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import com.android.purebilibili.core.theme.iOSYellow
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
    viewModel: BangumiViewModel = viewModel()
) {
    val detailState by viewModel.detailState.collectAsStateWithLifecycle()
    
    // 加载详情
    LaunchedEffect(seasonId, epId, mediaId) {
        viewModel.loadSeasonDetail(seasonId = seasonId, epId = epId, mediaId = mediaId)
    }
    
    AppScaffold(
        blurContentReady = detailState !is BangumiDetailState.Loading,
        topBar = {
            AppTopBar(
                title = "番剧详情",
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
                            color = MaterialTheme.colorScheme.error
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
                        onReviewsClick = onReviewsClick
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
                        onReviewsClick = onReviewsClick
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
    onReviewsClick: (Long, String) -> Unit
) {
    // 状态管理
    val isFollowing = isBangumiFollowed(detail.userStatus)
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
                            model = FormatUtils.fixImageUrl(detail.cover),
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
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
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
                                            tint = iOSYellow, // Assuming this is available
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        AppText(
                                            text = String.format("%.1f", rating.score),
                                            color = iOSYellow,
                                            fontWeight = FontWeight.Bold
                                        )
                                        AppText(
                                            text = " (${rating.count}人评分)",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                            
                            // Stats
                            detail.stat?.let { stat ->
                                AppText(
                                    text = "${FormatUtils.formatStat(stat.views)}播放 · ${FormatUtils.formatStat(stat.favorites)}追番",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
                
                // Action Buttons
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
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
                                containerColor = if(isFollowing) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
                                contentColor = if(isFollowing) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.weight(1f),
                            shape = AppShapes.container(ContainerLevel.Chip)
                        ) {
                            AppIcon(
                                if (isFollowing) Icons.Outlined.Check else Icons.Outlined.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            AppText(resolveBangumiFollowStatusLabel(detail.userStatus))
                        }
                        if (canReviewBangumi(detail.mediaId, detail.rights)) {
                            AppOutlinedButton(
                                onClick = { onReviewsClick(detail.mediaId, detail.title) },
                                modifier = Modifier.weight(1f),
                                shape = AppShapes.container(ContainerLevel.Chip)
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
                                fontSize = 18.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            AppText(
                                text = detail.evaluate,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp,
                                lineHeight = 22.sp
                            )
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
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
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
                                         color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
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
                                             fontSize = 12.sp,
                                             color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                         )
                                     }
                                 }
                             }
                         }
                     }
                     
                     items(displayedEpisodes, key = { it.id }) { episode ->
                         EpisodeChip(
                                episode = episode,
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
                                 fontWeight = FontWeight.Bold,
                                 fontSize = 20.sp
                             )
                         }

                         items(section.episodes.orEmpty(), key = { it.id }) { episode ->
                             EpisodeChip(
                                 episode = episode,
                                 onClick = { onEpisodeClick(episode) }
                             )
                         }
                     }
                 
                 // Related Seasons
                 if (!detail.seasons.isNullOrEmpty() && detail.seasons.size > 1) {
                     item(span = { GridItemSpan(maxLineSpan) }) {
                         AppText(
                            text = "相关季度",
                            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                     }
                     
                     items(detail.seasons, key = { it.seasonId }) { season ->
                         val isCurrentSeason = season.seasonId == detail.seasonId
                         AppSurface(
                            onClick = { if (!isCurrentSeason) onSeasonClick(season.seasonId) },
                            shape = AppShapes.container(ContainerLevel.Chip),
                            color = if (isCurrentSeason) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            modifier = Modifier.height(48.dp) // Fixed height for consistency
                         ) {
                             Box(contentAlignment = Alignment.Center) {
                                 AppText(
                                    text = season.seasonTitle.ifEmpty { season.title },
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    fontSize = 14.sp,
                                    color = if (isCurrentSeason) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
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
                        label = { AppText("集数 (1-${detail.episodes.size})") },
                        singleLine = true,
                        isError = jumpErrorMessage != null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (jumpErrorMessage != null) {
                        AppText(
                            text = jumpErrorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                AppTextButton(
                    onClick = {
                        val epNumber = jumpInputText.toIntOrNull()
                        if (epNumber == null || epNumber < 1 || epNumber > detail.episodes.size) {
                            jumpErrorMessage = "请输入 1-${detail.episodes.size} 之间的数字"
                        } else {
                            val targetEpisode = detail.episodes.getOrNull(epNumber - 1)
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
        BangumiFollowStatusDialog(
            currentStatus = detail.userStatus?.followStatus ?: 0,
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
    onReviewsClick: (Long, String) -> Unit
) {
    //  [修复] 使用 detail 本身作为 key，这样当 ViewModel 更新 detail 时，状态会正确同步
    val isFollowing = isBangumiFollowed(detail.userStatus)
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
    
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // 头部封面和信息
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)  //  [修复] 增大高度防止文字被裁切
                ) {
                    // 封面背景（模糊）
                    AsyncImage(
                        model = FormatUtils.fixImageUrl(detail.cover),
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
                            model = FormatUtils.fixImageUrl(detail.cover),
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
                                fontSize = 20.sp,
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
                                            tint = iOSYellow,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        AppText(
                                            text = String.format("%.1f", rating.score),
                                            color = iOSYellow,
                                            fontWeight = FontWeight.Bold
                                        )
                                        AppText(
                                            text = " (${rating.count}人评分)",
                                            color = Color.White.copy(alpha = 0.7f),
                                            fontSize = 12.sp
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
                                    fontSize = 12.sp
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            // 播放量
                            detail.stat?.let { stat ->
                                AppText(
                                    text = "${FormatUtils.formatStat(stat.views)}播放 · ${FormatUtils.formatStat(stat.favorites)}追番",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
            
            // 操作按钮
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 追番按钮
                    if (isFollowing) {
                        //  已追番：使用带边框的样式，更清晰可见
                        AppOutlinedButton(
                            onClick = {
                                showFollowStatusDialog = true
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp, 
                                MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            AppIcon(
                                Icons.Outlined.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            AppText(resolveBangumiFollowStatusLabel(detail.userStatus))
                        }
                    } else {
                        //  未追番：使用填充的主色按钮
                        AppButton(
                            onClick = {
                                onFollowStatusSelect(BANGUMI_FOLLOW_STATUS_WATCHING)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = resolveFilledButtonContainerColor(MaterialTheme.colorScheme),

                                contentColor = resolveFilledButtonContentColor(MaterialTheme.colorScheme)
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            AppIcon(
                                Icons.Outlined.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            AppText("追番")
                        }
                    }
                    if (canReviewBangumi(detail.mediaId, detail.rights)) {
                        AppOutlinedButton(
                            onClick = { onReviewsClick(detail.mediaId, detail.title) },
                            modifier = Modifier.weight(1f)
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
            
            // 简介
            if (detail.evaluate.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        AppText(
                            text = "简介",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        AppText(
                            text = detail.evaluate,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
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
                            text = "选集 (${detail.episodes.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
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
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                
                //  分页选择器（超过50集时显示）
                if (detail.episodes.size > 50) {
                    item {
                        val episodesPerPage = 50
                        val totalPages = (detail.episodes.size + episodesPerPage - 1) / episodesPerPage
                        
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            items(totalPages, key = { it }) { page ->
                                val isCurrentPage = page == selectedPreviewPage
                                
                                AppSurface(
                                    onClick = { selectedPreviewPage = page },
                                    color = if (isCurrentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = AppShapes.container(ContainerLevel.Card)
                                ) {
                                    AppText(
                                        text = resolveBangumiEpisodePageLabel(
                                            episodeCount = detail.episodes.size,
                                            page = page,
                                            episodesPerPage = episodesPerPage,
                                            descending = episodesDescending
                                        ),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        fontSize = 12.sp,
                                        color = if (isCurrentPage) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                
                //  剧集预览（只显示前6个，点击展开完整列表）
                item {
                    val previewEpisodes = if (detail.episodes.size > 50) {
                        val window = resolveBangumiEpisodePreviewWindow(
                            episodeCount = detail.episodes.size,
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
                                onClick = { onEpisodeClick(episode) }
                            )
                        }
                        
                        // 更多按钮
                        if (detail.episodes.size > 6) {
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
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            AppText(
                                                text = "全部${detail.episodes.size}集",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
            if (!detail.seasons.isNullOrEmpty() && detail.seasons.size > 1) {
                item {
                    AppText(
                        text = "相关季度",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
                
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(detail.seasons, key = { it.seasonId }) { season ->
                            val isCurrentSeason = season.seasonId == detail.seasonId
                            AppSurface(
                                modifier = Modifier.clickable {
                                    if (!isCurrentSeason) {
                                        onSeasonClick(season.seasonId)
                                    }
                                },
                                shape = AppShapes.container(ContainerLevel.Chip),
                                color = if (isCurrentSeason) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            ) {
                                AppText(
                                    text = season.seasonTitle.ifEmpty { season.title },
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    fontSize = 14.sp,
                                    color = if (isCurrentSeason) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
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
                            label = { AppText("集数 (1-${detail.episodes.size})") },
                            singleLine = true,
                            isError = jumpErrorMessage != null,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (jumpErrorMessage != null) {
                            AppText(
                                text = jumpErrorMessage!!,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    com.android.purebilibili.core.ui.AppDialogAction(
                        onClick = {
                            val epNumber = jumpInputText.toIntOrNull()
                            if (epNumber == null || epNumber < 1 || epNumber > detail.episodes.size) {
                                jumpErrorMessage = "请输入 1-${detail.episodes.size} 之间的数字"
                            } else {
                                val targetEpisode = detail.episodes.getOrNull(epNumber - 1)
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
            BangumiFollowStatusDialog(
                currentStatus = detail.userStatus?.followStatus ?: 0,
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
                fontSize = 18.sp,
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
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium
        )
        AppText(
            text = value,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(episodes.take(20), key = { it.id }) { episode ->
                EpisodeChip(
                    episode = episode,
                    onClick = { onEpisodeClick(episode) }
                )
            }
        }
    }
}

@Composable
private fun BangumiFollowStatusDialog(
    currentStatus: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { AppText("追番状态") },
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
                                fontSize = 15.sp,
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
                AppText("取消追番")
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
                    val badgeColors = resolveAdaptivePrimaryAccentColors(MaterialTheme.colorScheme)
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
                            fontSize = 9.sp
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
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            // 标题
            if (episode.longTitle.isNotEmpty()) {
                Spacer(modifier = Modifier.width(6.dp))
                AppText(
                    text = episode.longTitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    fontSize = 18.sp,
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
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            //  季度标签（如果有多个季度）
            if (!detail.seasons.isNullOrEmpty() && detail.seasons.size > 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    items(detail.seasons, key = { it.seasonId }) { season ->
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
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color.Transparent
                            }
                        ) {
                            AppText(
                                text = season.seasonTitle.ifEmpty { season.title },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                fontSize = 14.sp,
                                fontWeight = if (isCurrentSeason) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCurrentSeason) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
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
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                            color = if (isCurrentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
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
                                fontSize = 12.sp,
                                color = if (isCurrentPage) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
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
            
            // VIP 角标
            if (episode.badge.isNotEmpty()) {
                val badgeColors = resolveAdaptivePrimaryAccentColors(MaterialTheme.colorScheme)
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
                        fontSize = 8.sp
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.width(10.dp))
        
        // 剧集信息
        Column(
            modifier = Modifier.weight(1f)
        ) {
            // 集数
            AppText(
                text = "第${episode.title}话",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            
            // 标题
            if (episode.longTitle.isNotEmpty()) {
                AppText(
                    text = episode.longTitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
