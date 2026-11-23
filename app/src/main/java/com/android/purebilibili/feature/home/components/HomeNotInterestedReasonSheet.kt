package com.android.purebilibili.feature.home.components

import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.AppHorizontalDivider

import com.android.purebilibili.core.ui.AppSpacingTokens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.components.AppText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppModalBottomSheet
import com.android.purebilibili.data.model.response.RecommendationFeedbackReason
import com.android.purebilibili.data.model.response.RecommendationFeedbackType
import com.android.purebilibili.data.model.response.VideoItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeNotInterestedReasonSheet(
    video: VideoItem,
    reasons: List<RecommendationFeedbackReason>,
    onReasonSelected: (RecommendationFeedbackReason) -> Unit,
    onQuickFilterClick: (() -> Unit)? = null,
    onDismissRequest: () -> Unit
) {
    AppModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // M3 ModalBottomSheet 路径的 modalWindowInsets 已消费导航栏 insets（此处为 0）；
                // 这层 padding 是给 CenteredDialog/平板限宽弹层路径兜底的，勿删。
                .navigationBarsPadding()
                .padding(bottom = AppSpacingTokens.Medium)
        ) {
            AppText(
                text = "选择不感兴趣的原因",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = AppSpacingTokens.Large + AppSpacingTokens.ExtraSmall, vertical = AppSpacingTokens.Small)
            )
            AppText(
                text = if (video.recommendationFeedback?.supportsServerSync == true && reasons.any { (it.id ?: 0L) > 0L }) {
                    "选择后移除视频并提交反馈，推荐调整可能需要时间。"
                } else {
                    "当前来源仅支持本地屏蔽，不会同步推荐偏好；关闭可取消。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = AppContentColors.secondaryText(),
                modifier = Modifier.padding(horizontal = AppSpacingTokens.Large + AppSpacingTokens.ExtraSmall, vertical = AppSpacingTokens.ExtraSmall)
            )
            AppText(
                text = video.title,
                style = MaterialTheme.typography.bodyMedium,
                color = AppContentColors.secondaryText(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = AppSpacingTokens.Large + AppSpacingTokens.ExtraSmall, vertical = AppSpacingTokens.ExtraSmall)
            )

            ReasonGroup(
                title = "减少推荐",
                reasons = reasons.filter { it.type == RecommendationFeedbackType.DISLIKE },
                onReasonSelected = onReasonSelected
            )
            ReasonGroup(
                title = "内容反馈",
                reasons = reasons.filter { it.type == RecommendationFeedbackType.FEEDBACK },
                onReasonSelected = onReasonSelected
            )
            if (onQuickFilterClick != null) {
                AppHorizontalDivider(
                    modifier = Modifier.padding(
                        horizontal = AppSpacingTokens.Large + AppSpacingTokens.ExtraSmall,
                        vertical = AppSpacingTokens.Small,
                    ),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = AppSpacingTokens.TripleExtraLarge + AppSpacingTokens.ExtraSmall)
                        .clickable { onQuickFilterClick() }
                        .padding(
                            horizontal = AppSpacingTokens.Large + AppSpacingTokens.ExtraSmall,
                            vertical = AppSpacingTokens.Medium,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppText(
                        text = "按关键词 / UP 主精细屏蔽…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = AppContentColors.accent(),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    AppText(
                        text = "立即编辑",
                        style = MaterialTheme.typography.labelMedium,
                        color = AppContentColors.accent(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReasonGroup(
    title: String,
    reasons: List<RecommendationFeedbackReason>,
    onReasonSelected: (RecommendationFeedbackReason) -> Unit
) {
    if (reasons.isEmpty()) return
    AppText(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = AppContentColors.accent(),
        modifier = Modifier.padding(start = AppSpacingTokens.Large + AppSpacingTokens.ExtraSmall, top = AppSpacingTokens.Large, end = AppSpacingTokens.Large + AppSpacingTokens.ExtraSmall, bottom = AppSpacingTokens.ExtraSmall)
    )
    reasons.forEachIndexed { index, reason ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AppSpacingTokens.TripleExtraLarge + AppSpacingTokens.ExtraSmall)
                .clickable { onReasonSelected(reason) }
                .padding(horizontal = AppSpacingTokens.Large + AppSpacingTokens.ExtraSmall, vertical = AppSpacingTokens.Medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppText(
                text = reason.name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.padding(horizontal = AppSpacingTokens.ExtraSmall))
            AppText(
                text = "选择",
                style = MaterialTheme.typography.labelMedium,
                color = AppContentColors.secondaryText()
            )
        }
        if (index != reasons.lastIndex) {
            AppHorizontalDivider(
                modifier = Modifier.padding(horizontal = AppSpacingTokens.Large + AppSpacingTokens.ExtraSmall),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            )
        }
    }
}
