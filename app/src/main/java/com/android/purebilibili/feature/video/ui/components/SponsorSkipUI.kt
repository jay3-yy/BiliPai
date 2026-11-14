// 文件路径: feature/video/SponsorSkipUI.kt
package com.android.purebilibili.feature.video.ui.components

import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.AppFilterChip
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppTextButton

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.rememberAppClearIcon
import com.android.purebilibili.data.model.response.SponsorCategory
import com.android.purebilibili.feature.plugin.sponsorBlockAllowedActionTypes
import com.android.purebilibili.data.model.response.SponsorSegment
import com.android.purebilibili.feature.video.viewmodel.SponsorContributionPhase
import com.android.purebilibili.feature.video.viewmodel.SponsorContributionUiState
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel

/**
 * 空降助手跳过按钮 UI
 * 显示在视频右下角，允许用户跳过广告片段
 */
@Composable
fun SponsorSkipButton(
    segment: SponsorSegment?,
    visible: Boolean,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
    onVote: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val clearIcon = rememberAppClearIcon()

    AnimatedVisibility(
        visible = visible && segment != null,
        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(tween(200)),
        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(tween(200)),
        modifier = modifier
    ) {
        segment?.let { seg ->
            AppSurface(
                modifier = Modifier
                    .padding(16.dp)
                    .clip(AppShapes.container(ContainerLevel.Card)),
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 类别标签
                    AppText(
                        text = seg.categoryName,
                        color = MaterialTheme.colorScheme.inversePrimary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )

                    // 跳过按钮
                    AppSurface(
                        modifier = Modifier
                            .clip(AppShapes.container(ContainerLevel.Chip))
                            .clickable { onSkip() },
                        color = AppContentColors.accent()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            AppIcon(
                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = "跳过",
                                tint = AppContentColors.onAccent(),
                                modifier = Modifier.size(16.dp)
                            )
                            AppText(
                                text = "跳过",
                                color = AppContentColors.onAccent(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    AppTextButton(onClick = { onVote(1) }) { AppText("有用", color = MaterialTheme.colorScheme.inverseOnSurface) }
                    AppTextButton(onClick = { onVote(0) }) { AppText("不准确", color = MaterialTheme.colorScheme.inverseOnSurface) }

                    // 关闭按钮
                    AppIconButton(onClick = onDismiss) {
                        AppIcon(
                            imageVector = clearIcon,
                            contentDescription = "忽略",
                            tint = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Submission review appears only after an explicit action in the player's More menu.
 * The ViewModel owns the draft; no contribution entry floats over the video.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SponsorContributionOverlay(
    state: SponsorContributionUiState,
    onCategoryChange: (String) -> Unit,
    onActionTypeChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
) {
    if (state.showsReview) {
        AppAlertDialog(
            onDismissRequest = {
                if (state.phase != SponsorContributionPhase.SUBMITTING) onCancel()
            },
            title = {
                AppText(
                    when (state.phase) {
                        SponsorContributionPhase.SUCCESS -> "社区片段已提交"
                        SponsorContributionPhase.SUBMITTING -> "正在提交社区片段"
                        else -> "确认提交社区片段"
                    }
                )
            },
            text = {
                // 横屏或窄分屏下只滚动表单正文，标题与提交操作保持可见。
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AppText(
                        text = if (state.actionType == "full") {
                            "整个视频"
                        } else {
                            "${formatSponsorContributionTime(state.startMs ?: 0L)} – ${formatSponsorContributionTime(state.endMs ?: 0L)}"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (state.phase != SponsorContributionPhase.SUCCESS) {
                        AppText(
                            text = "选择片段类别",
                            style = MaterialTheme.typography.labelLarge,
                            color = AppContentColors.secondaryText(),
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SponsorCategory.ALL_CATEGORIES
                                .filter { category ->
                                    state.actionType != "full" ||
                                        "full" in sponsorBlockAllowedActionTypes(category)
                                }
                                .forEach { category ->
                                AppFilterChip(
                                    selected = state.category == category,
                                    onClick = { onCategoryChange(category) },
                                    enabled = state.phase == SponsorContributionPhase.REVIEW,
                                    label = { AppText(SponsorCategory.getCategoryName(category)) },
                                )
                            }
                        }
                        AppText(
                            text = "动作类型",
                            style = MaterialTheme.typography.labelLarge,
                            color = AppContentColors.secondaryText(),
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            sponsorBlockAllowedActionTypes(state.category)
                                .filter { actionType -> state.actionType != "full" || actionType == "full" }
                                .forEach { actionType ->
                                AppFilterChip(
                                    selected = state.actionType == actionType,
                                    onClick = { onActionTypeChange(actionType) },
                                    enabled = state.phase == SponsorContributionPhase.REVIEW,
                                    label = { AppText(sponsorActionTypeLabel(actionType)) },
                                )
                            }
                        }
                        AppText(
                            text = "提交将发送类别、时间段、视频标识和社区用户 ID 至 ${state.serverBaseUrl}",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppContentColors.secondaryText(),
                        )
                    }
                    state.message?.let { message ->
                        AppText(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.phase == SponsorContributionPhase.SUCCESS) {
                                AppContentColors.accent()
                            } else {
                                AppContentColors.error()
                            },
                        )
                    }
                }
            },
            confirmButton = {
                when (state.phase) {
                    SponsorContributionPhase.REVIEW -> {
                        AppTextButton(onClick = onSubmit) { AppText("确认提交") }
                    }
                    SponsorContributionPhase.SUCCESS -> {
                        AppTextButton(onClick = onCancel) { AppText("完成") }
                    }
                    SponsorContributionPhase.SUBMITTING -> AppText("提交中…")
                    else -> Unit
                }
            },
            dismissButton = {
                if (state.phase == SponsorContributionPhase.REVIEW) {
                    AppTextButton(onClick = onCancel) { AppText("取消") }
                }
            },
        )
    }
}

private fun sponsorActionTypeLabel(actionType: String): String = when (actionType) {
    "skip" -> "跳过"
    "mute" -> "静音"
    "full" -> "整段标记"
    "poi" -> "精彩时刻"
    else -> actionType
}

private fun formatSponsorContributionTime(positionMs: Long): String {
    val totalSeconds = (positionMs.coerceAtLeast(0L) / 1_000L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

/**
 * 自动跳过提示 Toast
 * 屏幕顶部短暂显示
 */
@Composable
fun SponsorSkipToast(
    message: String?,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible && message != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        message?.let {
            AppSurface(
                modifier = Modifier
                    .padding(top = 60.dp, start = 16.dp, end = 16.dp)
                    .clip(AppShapes.container(ContainerLevel.Floating)),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppIcon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                    AppText(
                        text = it,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
