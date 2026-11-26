package com.android.purebilibili.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.store.CreatorSignalSnapshot
import com.android.purebilibili.core.store.TodayWatchFeedbackSnapshot
import com.android.purebilibili.core.store.TodayWatchFeedbackStore
import com.android.purebilibili.core.store.TodayWatchProfileStore
import com.android.purebilibili.core.store.resolveDislikedCreatorName
import com.android.purebilibili.core.store.withoutDislikedCreator
import com.android.purebilibili.core.store.withoutDislikedKeyword
import com.android.purebilibili.core.store.withoutDislikedVideo
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppHapticScene
import com.android.purebilibili.core.ui.AppModalBottomSheet
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppFilterChip
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppOutlinedButton
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.components.appVerticalScroll
import com.android.purebilibili.core.ui.rememberAppHapticFeedback
import com.android.purebilibili.plugin.sdk.RecommendationStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 今日推荐单「兴趣画像与负反馈管理」弹窗：
 * 展示常看 UP 画像，并支持逐条撤销误点的「减少该 UP 推荐」「不感兴趣」与屏蔽词。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun TodayWatchTasteSheet(
    mode: TodayWatchMode,
    currentStrategy: RecommendationStrategy,
    onStrategyChange: (RecommendationStrategy) -> Unit,
    onUpClick: (Long) -> Unit,
    onUndoCreator: (Long) -> Unit,
    onUndoVideo: (String) -> Unit,
    onUndoKeyword: (String) -> Unit,
    onClearAllNegativeFeedback: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val haptics = rememberAppHapticFeedback(AppHapticScene.CONTROLS)
    var selectedStrategy by remember(currentStrategy) { mutableStateOf(currentStrategy) }
    var feedback by remember { mutableStateOf(TodayWatchFeedbackSnapshot()) }
    var creators by remember { mutableStateOf<List<CreatorSignalSnapshot>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val (loadedFeedback, loadedCreators) = withContext(Dispatchers.IO) {
            TodayWatchFeedbackStore.getSnapshot(context) to
                TodayWatchProfileStore.getCreatorSignals(context, limit = 8)
        }
        feedback = loadedFeedback
        creators = loadedCreators
        loaded = true
    }

    val hasAnyNegative = feedback.dislikedCreatorMids.isNotEmpty() ||
        feedback.dislikedKeywords.isNotEmpty() ||
        feedback.recentDislikedVideos.isNotEmpty() ||
        feedback.dislikedBvids.isNotEmpty()
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.84f).dp

    AppModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .navigationBarsPadding()
                .appVerticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppIcon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = AppContentColors.accent(),
                    modifier = Modifier.size(20.dp),
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    AppText(
                        text = "兴趣画像与负反馈管理",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = AppContentColors.primaryText(),
                    )
                    AppText(
                        text = if (mode == TodayWatchMode.RELAX) {
                            "当前模式：轻松看 · 偏好短时长与高赞宝藏"
                        } else {
                            "当前模式：深度学习 · 偏好干货与合拍时长"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = AppContentColors.secondaryText(),
                    )
                }
            }

            // 推荐策略内联切换
            AppSurface(
                shape = AppShapes.container(ContainerLevel.Card),
                color = AppSurfaceTokens.cardContainer(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppText(
                        text = "推荐策略",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = AppContentColors.primaryText(),
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf(
                            RecommendationStrategy.BALANCED to "均衡推荐",
                            RecommendationStrategy.AFFINITY to "兴趣优先",
                            RecommendationStrategy.EXPLORE to "探索优先",
                        ).forEach { (strategy, label) ->
                            AppFilterChip(
                                selected = selectedStrategy == strategy,
                                onClick = {
                                    if (selectedStrategy != strategy) {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedStrategy = strategy
                                        onStrategyChange(strategy)
                                    }
                                },
                                label = { AppText(label) },
                            )
                        }
                    }
                    AppText(
                        text = when (selectedStrategy) {
                            RecommendationStrategy.BALANCED -> "兼顾常看偏好、新鲜度与内容多样性"
                            RecommendationStrategy.AFFINITY -> "优先匹配你近期高完播的 UP 主与主题"
                            RecommendationStrategy.EXPLORE -> "加大新 UP、新主题与近期宝藏内容的比重"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = AppContentColors.secondaryText(),
                    )
                }
            }

            // 常看 UP 兴趣画像
            AppSurface(
                shape = AppShapes.container(ContainerLevel.Card),
                color = AppSurfaceTokens.cardContainer(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppText(
                        text = "近期高完播偏好 UP",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = AppContentColors.primaryText(),
                    )
                    if (!loaded) {
                        AppText(
                            text = "正在读取本地画像…",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppContentColors.secondaryText(),
                        )
                    } else if (creators.isEmpty()) {
                        AppText(
                            text = "多完整看完几支喜欢的视频，系统会自动学习你的口味与时长舒适区。",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppContentColors.secondaryText(),
                        )
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            creators.forEach { signal ->
                                AppSurface(
                                    shape = AppShapes.container(ContainerLevel.Pill),
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                                    modifier = Modifier
                                        .clip(AppShapes.container(ContainerLevel.Pill))
                                        .clickable(enabled = signal.mid > 0L) {
                                            onDismiss()
                                            onUpClick(signal.mid)
                                        },
                                ) {
                                    AppText(
                                        text = "${signal.name} · ${signal.watchCount}次",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 已减少推荐的 UP
            if (feedback.dislikedCreatorMids.isNotEmpty()) {
                AppSurface(
                    shape = AppShapes.container(ContainerLevel.Card),
                    color = AppSurfaceTokens.cardContainer(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            AppIcon(
                                imageVector = Icons.Outlined.PersonOff,
                                contentDescription = null,
                                tint = AppContentColors.secondaryText(),
                                modifier = Modifier.size(16.dp),
                            )
                            AppText(
                                text = "已减少推荐的 UP（点击恢复）",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = AppContentColors.primaryText(),
                            )
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            feedback.dislikedCreatorMids.forEach { mid ->
                                val name = feedback.resolveDislikedCreatorName(mid)
                                AppSurface(
                                    shape = AppShapes.container(ContainerLevel.Pill),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                                    modifier = Modifier
                                        .clip(AppShapes.container(ContainerLevel.Pill))
                                        .clickable {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            feedback = feedback.withoutDislikedCreator(mid)
                                            onUndoCreator(mid)
                                        },
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        AppText(
                                            text = name,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AppContentColors.primaryText(),
                                        )
                                        AppIcon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "恢复推荐",
                                            tint = AppContentColors.secondaryText(),
                                            modifier = Modifier.size(13.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 已降权关键词
            if (feedback.dislikedKeywords.isNotEmpty()) {
                AppSurface(
                    shape = AppShapes.container(ContainerLevel.Card),
                    color = AppSurfaceTokens.cardContainer(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AppText(
                            text = "已降权关键词（点击移除）",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = AppContentColors.primaryText(),
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            feedback.dislikedKeywords.forEach { keyword ->
                                AppSurface(
                                    shape = AppShapes.container(ContainerLevel.Pill),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                                    modifier = Modifier
                                        .clip(AppShapes.container(ContainerLevel.Pill))
                                        .clickable {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            feedback = feedback.withoutDislikedKeyword(keyword)
                                            onUndoKeyword(keyword)
                                        },
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        AppText(
                                            text = keyword,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AppContentColors.primaryText(),
                                        )
                                        AppIcon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "移除关键词",
                                            tint = AppContentColors.secondaryText(),
                                            modifier = Modifier.size(13.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 近期不感兴趣记录
            AppSurface(
                shape = AppShapes.container(ContainerLevel.Card),
                color = AppSurfaceTokens.cardContainer(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppText(
                        text = "近期跳过 / 不感兴趣",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = AppContentColors.primaryText(),
                    )
                    if (feedback.recentDislikedVideos.isEmpty()) {
                        AppText(
                            text = "暂无不感兴趣记录。在推荐单里长按或点右侧菜单可随时调整口味。",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppContentColors.secondaryText(),
                        )
                    } else {
                        feedback.recentDislikedVideos
                            .sortedByDescending { it.dislikedAtMillis }
                            .take(8)
                            .forEach { item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        AppText(
                                            text = item.title.ifBlank { item.bvid },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = AppContentColors.primaryText(),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        AppText(
                                            text = item.creatorName.ifBlank { "未知 UP" },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AppContentColors.secondaryText(),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    AppTextButton(
                                        onClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            feedback = feedback.withoutDislikedVideo(item.bvid)
                                            onUndoVideo(item.bvid)
                                        },
                                    ) {
                                        AppText("撤销")
                                    }
                                }
                            }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AppOutlinedButton(
                    enabled = hasAnyNegative,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        feedback = TodayWatchFeedbackSnapshot()
                        onClearAllNegativeFeedback()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    AppText("清空负反馈")
                }
                AppButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    AppText("完成")
                }
            }
        }
    }
}
