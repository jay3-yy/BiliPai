package com.android.purebilibili.feature.space

import com.android.purebilibili.core.ui.components.AppSurface

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.purebilibili.core.ui.AppScaffold
import com.android.purebilibili.core.ui.AppTopBar
import com.android.purebilibili.core.ui.components.AppCircularProgressIndicator
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppNativeTabRow
import com.android.purebilibili.core.ui.components.AppSegmentOption
import com.android.purebilibili.core.ui.components.AppTabRowIndicatorPresentation
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.data.model.response.SpaceGuardMemberItem

/** UP主充电排行页（对齐 PiliPlus member_upower_rank）。 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SpaceUpowerRankScreen(
    mid: Long,
    name: String,
    count: Long,
    onBack: () -> Unit,
    onUserClick: (Long) -> Unit,
) {
    val viewModel: SpaceUpowerRankViewModel = viewModel(
        key = "space_upower_rank_$mid",
        factory = viewModelFactory {
            initializer {
                SpaceUpowerRankViewModel(upMid = mid, upName = name, initialCount = count)
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val onSelectLevel: (Int?) -> Unit = viewModel::selectLevel
    val onRetry: () -> Unit = viewModel::retry
    AppScaffold(
        topBar = {
            AppTopBar(
                title = "${state.upName}的充电排行榜",
                navigationIcon = {
                    AppIconButton(onClick = onBack) {
                        AppIcon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (state.levelTabs.isNotEmpty()) {
                // AppNativeTabRow 的 selectedValue 非空，用 -1 表示「全部」
                val selectedValue = state.selectedPrivilegeType ?: -1
                val options = buildList {
                    add(AppSegmentOption(-1, "全部"))
                    state.levelTabs.forEach { tab ->
                        add(AppSegmentOption(tab.privilegeType, "${tab.name}(${tab.memberTotal})"))
                    }
                }
                AppNativeTabRow(
                    options = options,
                    selectedValue = selectedValue,
                    onSelectionChange = { onSelectLevel(if (it == -1) null else it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    indicatorPresentation = AppTabRowIndicatorPresentation.TONAL_PILL,
                )
            }

            val errorMessage = state.error
            when {
                state.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        AppCircularProgressIndicator()
                    }
                }
                errorMessage != null -> {
                    SpaceSupporterErrorState(
                        message = errorMessage,
                        onRetry = onRetry,
                    )
                }
                state.items.isEmpty() -> {
                    SpaceSupporterEmptyState(text = "还没有人为TA充电")
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(
                            count = state.items.size,
                            key = { index -> "${state.items[index].mid}_${state.items[index].day}_$index" },
                        ) { index ->
                            val item = state.items[index]
                            SpaceUpowerRankRow(
                                rank = index + 1,
                                avatarUrl = item.avatar,
                                nickname = item.nickname,
                                days = item.day,
                                onClick = { if (item.mid > 0) onUserClick(item.mid) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpaceUpowerRankRow(
    rank: Int,
    avatarUrl: String,
    nickname: String,
    days: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppText(
            text = rank.toString(),
            modifier = Modifier.width(32.dp),
            style = MaterialTheme.typography.titleMedium.copy(
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Bold,
            ),
            color = when (rank) {
                1 -> Color(0xFFfdad13)
                2 -> Color(0xFF8aace1)
                3 -> Color(0xFFdfa777)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        SpaceSupporterAvatar(
            url = avatarUrl,
            size = 38.dp,
        )
        Spacer(modifier = Modifier.width(12.dp))
        AppText(
            text = nickname,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        // elec 回退数据没有充电天数，隐藏天数标签
        if (days > 0) {
            AppText(
                text = "${days}天",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** UP主大航海（舰队）页（对齐 PiliPlus member_guard）。 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SpaceMemberGuardScreen(
    mid: Long,
    name: String,
    count: Long,
    onBack: () -> Unit,
    onUserClick: (Long) -> Unit,
) {
    val viewModel: SpaceMemberGuardViewModel = viewModel(
        key = "space_member_guard_$mid",
        factory = viewModelFactory {
            initializer {
                SpaceMemberGuardViewModel(ruid = mid, upName = name, initialCount = count)
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val onRetry: () -> Unit = viewModel::retry
    val onLoadMore: () -> Unit = viewModel::loadMore
    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && lastVisible >= total - 3
        }
    }
    androidx.compose.runtime.LaunchedEffect(shouldLoadMore, state.hasMore) {
        if (shouldLoadMore && state.hasMore && !state.isLoadingMore && !state.isLoading) {
            onLoadMore()
        }
    }

    AppScaffold(
        topBar = {
            AppTopBar(
                title = "${state.upName}的舰队",
                navigationIcon = {
                    AppIconButton(onClick = onBack) {
                        AppIcon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        when {
            state.isLoading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    AppCircularProgressIndicator()
                }
            }
            state.error != null && state.items.isEmpty() -> {
                Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                    SpaceSupporterErrorState(
                        message = state.error.orEmpty(),
                        onRetry = onRetry,
                    )
                }
            }
            state.items.isEmpty() && state.tops.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                    SpaceSupporterEmptyState(text = "TA的舰队还没有成员")
                }
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 24.dp),
                ) {
                    if (state.tops.isNotEmpty()) {
                        item(key = "guard_podium") {
                            SpaceGuardPodium(tops = state.tops, onUserClick = onUserClick)
                        }
                    }
                    items(
                        count = state.items.size,
                        key = { index -> "guard_${state.items[index].uid}_$index" },
                    ) { index ->
                        val item = state.items[index]
                        SpaceGuardMemberRow(
                            avatarUrl = item.face,
                            username = item.username,
                            guardLevel = item.guardLevel,
                            onClick = { if (item.uid > 0) onUserClick(item.uid) },
                        )
                    }
                    if (state.isLoadingMore) {
                        item(key = "guard_loading_more") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                AppCircularProgressIndicator()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpaceGuardPodium(
    tops: List<SpaceGuardMemberItem>,
    onUserClick: (Long) -> Unit,
) {
    // 第 2、1、3 名横向排列（PiliPlus _buildTopItems 布局）
    val ordered = when (tops.size) {
        1 -> listOf(tops[0])
        2 -> listOf(tops[1], tops[0])
        else -> listOf(tops[1], tops[0], tops[2])
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.Center,
    ) {
        ordered.forEachIndexed { index, member ->
            val isFirst = index == 1 || ordered.size == 1
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable(enabled = member.uid > 0) { onUserClick(member.uid) }
                    .padding(horizontal = 16.dp),
            ) {
                Box {
                    SpaceSupporterAvatar(
                        url = member.face,
                        size = if (isFirst) 50.dp else 42.dp,
                    )
                    AppSurface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(if (isFirst) 18.dp else 16.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            AppText(
                                text = (tops.indexOf(member) + 1).toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }
                AppText(
                    text = member.username,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun SpaceGuardMemberRow(
    avatarUrl: String,
    username: String,
    guardLevel: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SpaceSupporterAvatar(url = avatarUrl, size = 32.dp)
        Spacer(modifier = Modifier.width(12.dp))
        AppText(
            text = username,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        AppText(
            text = resolveSpaceGuardLevelLabel(guardLevel),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun SpaceSupporterAvatar(url: String, size: androidx.compose.ui.unit.Dp) {
    val context = androidx.compose.ui.platform.LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(FormatUtils.buildSizedImageUrl(url, width = 120, height = 120))
            .crossfade(true)
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

@Composable
private fun SpaceSupporterErrorState(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AppText(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AppTextButton(onClick = onRetry) {
            AppText("重试")
        }
    }
}

@Composable
private fun SpaceSupporterEmptyState(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        AppText(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun resolveSpaceGuardLevelLabel(level: Int): String = when (level) {
    1 -> "总督"
    2 -> "提督"
    3 -> "舰长"
    else -> "舰长"
}
