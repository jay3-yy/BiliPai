package com.android.purebilibili.feature.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.AppTopBar
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.ImmersiveAppScaffold
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppFilterChip
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppLazyColumn
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.rememberAppBackIcon
import kotlin.math.roundToInt

/** 使用入口的统计快照，确保完整列表与回顾卡片的时间范围、排序和占比一致。 */
@Composable
internal fun PersonalRecapUpsScreen(
    window: PersonalRecapWindow,
    stats: PersonalVideoRecapStats,
    onBack: () -> Unit,
    onUpClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val stateHolder: PersonalRecapUpsViewModel = viewModel(
        factory = viewModelFactory { initializer { PersonalRecapUpsViewModel(window, stats) } },
    )
    val state by stateHolder.uiState.collectAsStateWithLifecycle()
    PersonalRecapUpsContent(
        state = state,
        onWindowChange = stateHolder::selectWindow,
        onSortOrderChange = stateHolder::selectSortOrder,
        onRetry = stateHolder::retry,
        onBack = onBack,
        onUpClick = onUpClick,
        modifier = modifier,
    )
}

@Composable
private fun PersonalRecapUpsContent(
    state: PersonalRecapUpsUiState,
    onWindowChange: (PersonalRecapWindow) -> Unit,
    onSortOrderChange: (PersonalRecapUpSortOrder) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onUpClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollStates = rememberSaveableStateHolder()
    val stats = state.stats
    val sortedUps = remember(stats, state.sortOrder) {
        sortPersonalRecapUps(stats?.topUps.orEmpty(), state.sortOrder)
    }
    ImmersiveAppScaffold(
        modifier = modifier,
        topBar = {
            AppTopBar(
                title = "最近爱看",
                navigationIcon = {
                    AppIconButton(onClick = onBack) {
                        AppIcon(rememberAppBackIcon(), contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 840.dp).fillMaxSize()) {
                PersonalRecapWindowSelector(
                    window = state.window,
                    onWindowChange = onWindowChange,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PersonalRecapUpSortOrder.entries.forEach { order ->
                        AppFilterChip(
                            selected = state.sortOrder == order,
                            onClick = { onSortOrderChange(order) },
                            label = { AppText(order.label) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
                // 时间范围与排序分别保留滚动位置，切换口径时不复用旧排名的锚点。
                scrollStates.SaveableStateProvider("${state.window.name}:${state.sortOrder.name}") {
                    AppLazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        when {
                            state.error != null -> item(key = "error") {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    AppText("${state.window.label}的观看记录加载失败", style = MaterialTheme.typography.titleSmall)
                                    AppText(state.error, style = MaterialTheme.typography.bodySmall, color = AppContentColors.secondaryText())
                                    AppTextButton(onClick = onRetry) { AppText("重试") }
                                }
                            }
                            stats == null -> item(key = "loading") {
                                AppText("正在整理${state.window.label}的观看记录…", style = MaterialTheme.typography.bodyMedium)
                            }
                            else -> {
                                item(key = "summary") {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        AppText(state.window.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                        AppText(
                                            "观看 ${stats.videoCount} 个视频 · ${stats.topUps.size} 位 UP 主",
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        AppText(
                                            when (state.sortOrder) {
                                                PersonalRecapUpSortOrder.VIDEO_COUNT ->
                                                    "按观看视频数排序，占比以本时段全部已统计视频数为基数。点击 UP 主进入空间。"
                                                PersonalRecapUpSortOrder.ESTIMATED_DURATION ->
                                                    "按估算观看时长排序，占比以本时段全部已统计视频的估算时长为基数。时长按历史播放进度估算，不代表实际停留时间。"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = AppContentColors.secondaryText(),
                                        )
                                        if (state.sortOrder == PersonalRecapUpSortOrder.ESTIMATED_DURATION && stats.totalDurationSec == 0L) {
                                            AppText(
                                                "该时段估算总时长为 0，暂不计算时长占比。",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = AppContentColors.secondaryText(),
                                            )
                                        }
                                        if (stats.historyMayBeIncomplete) {
                                            AppText(
                                                "较早的观看记录尚未全部载入，以下仅统计已载入记录。",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = AppContentColors.secondaryText(),
                                            )
                                        }
                                        Spacer(Modifier.height(6.dp))
                                    }
                                }
                                if (stats.topUps.isEmpty()) {
                                    item(key = "empty") {
                                        AppText(
                                            if (stats.videoCount == 0) "这段时间还没有观看记录。"
                                            else "这段时间的观看记录中没有可展示的 UP 主信息。",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = AppContentColors.secondaryText(),
                                        )
                                    }
                                }
                                itemsIndexed(sortedUps, key = { _, up -> up.mid }) { index, up ->
                                    PersonalRecapUpRow(
                                        up = up,
                                        rank = index + 1,
                                        share = personalRecapUpShare(up, stats, state.sortOrder),
                                        sortOrder = state.sortOrder,
                                        onClick = { onUpClick(up.mid) },
                                        modifier = Modifier.fillMaxWidth(),
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

@Composable
private fun PersonalRecapUpRow(
    up: PersonalUpRecap,
    rank: Int,
    share: Float?,
    sortOrder: PersonalRecapUpSortOrder,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppSurface(
        modifier = modifier,
        shape = AppShapes.container(ContainerLevel.Card),
        color = AppSurfaceTokens.cardContainer(),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .clickable(role = Role.Button, onClickLabel = "进入${up.name}的空间", onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText("$rank", style = MaterialTheme.typography.labelMedium, color = AppContentColors.secondaryText())
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (up.face.isNotBlank()) {
                    AsyncImage(model = up.face, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    AppIcon(Icons.Outlined.PersonOutline, contentDescription = null, modifier = Modifier.size(24.dp))
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AppText(up.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                AppText(
                    "${up.watchCount} 个视频" + if (share != null) {
                        " · ${sortOrder.shareLabel} ${(share * 100).roundToInt()}%"
                    } else {
                        ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = AppContentColors.secondaryText(),
                )
                if (up.totalDurationSec > 0L || sortOrder == PersonalRecapUpSortOrder.ESTIMATED_DURATION) {
                    val duration = if (up.totalDurationSec == 0L) "0 秒" else formatRecapDuration(up.totalDurationSec)
                    AppText("估算观看 $duration", style = MaterialTheme.typography.bodySmall, color = AppContentColors.secondaryText())
                }
            }
            AppIcon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = AppContentColors.secondaryText())
        }
    }
}
