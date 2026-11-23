package com.android.purebilibili.feature.dynamic.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppModalBottomSheet
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.components.AppFilterChip
import com.android.purebilibili.core.ui.components.AppHorizontalDivider
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.components.appVerticalScroll as verticalScroll
import com.android.purebilibili.data.model.response.RelationTagItem
import com.android.purebilibili.feature.dynamic.DynamicFeedFilterState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun DynamicFilterSheet(
    filterState: DynamicFeedFilterState,
    followGroups: List<RelationTagItem>,
    isLoadingGroupMembers: Boolean,
    onFilterChange: (DynamicFeedFilterState) -> Unit,
    onSelectFollowGroup: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    AppModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppSpacingTokens.Large)
                .navigationBarsPadding()
                .padding(bottom = AppSpacingTokens.Large),
            verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.Medium),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppText(
                    text = "动态筛选",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = AppContentColors.primaryText(),
                )
                if (filterState.isActive) {
                    AppTextButton(
                        onClick = {
                            onSelectFollowGroup(null)
                            onFilterChange(DynamicFeedFilterState())
                        },
                    ) {
                        AppText("重置")
                    }
                }
            }

            if (followGroups.isNotEmpty()) {
                AppText(
                    text = if (isLoadingGroupMembers) "按关注分组筛选（加载中…）" else "按关注分组筛选",
                    style = MaterialTheme.typography.labelLarge,
                    color = AppContentColors.secondaryText(),
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AppFilterChip(
                        selected = filterState.selectedFollowGroupTagId == null,
                        onClick = { onSelectFollowGroup(null) },
                        label = { AppText("全部分组") },
                    )
                    followGroups.forEach { tag ->
                        AppFilterChip(
                            selected = filterState.selectedFollowGroupTagId == tag.tagid,
                            onClick = {
                                val next = if (filterState.selectedFollowGroupTagId == tag.tagid) null else tag.tagid
                                onSelectFollowGroup(next)
                            },
                            label = { AppText(tag.name) },
                        )
                    }
                }
                AppHorizontalDivider()
            }

            AppText(
                text = "屏蔽动态类型",
                style = MaterialTheme.typography.labelLarge,
                color = AppContentColors.secondaryText(),
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AppFilterChip(
                    selected = filterState.hideForward,
                    onClick = { onFilterChange(filterState.copy(hideForward = !filterState.hideForward)) },
                    label = { AppText("隐藏转发") },
                )
                AppFilterChip(
                    selected = filterState.hideLottery,
                    onClick = { onFilterChange(filterState.copy(hideLottery = !filterState.hideLottery)) },
                    label = { AppText("隐藏抽奖") },
                )
                AppFilterChip(
                    selected = filterState.hideGoods,
                    onClick = { onFilterChange(filterState.copy(hideGoods = !filterState.hideGoods)) },
                    label = { AppText("隐藏带货") },
                )
                AppFilterChip(
                    selected = filterState.hideReserve,
                    onClick = { onFilterChange(filterState.copy(hideReserve = !filterState.hideReserve)) },
                    label = { AppText("隐藏预约") },
                )
                AppFilterChip(
                    selected = filterState.hideArticle,
                    onClick = { onFilterChange(filterState.copy(hideArticle = !filterState.hideArticle)) },
                    label = { AppText("隐藏专栏") },
                )
                AppFilterChip(
                    selected = filterState.hideLiveRcmd,
                    onClick = { onFilterChange(filterState.copy(hideLiveRcmd = !filterState.hideLiveRcmd)) },
                    label = { AppText("隐藏直播推荐") },
                )
            }

            AppHorizontalDivider()

            AppText(
                text = "关键词屏蔽已并入「插件中心 → 内容过滤 → 共享屏蔽词」，勾选「动态内容」即可跨页面生效。",
                style = MaterialTheme.typography.bodySmall,
                color = AppContentColors.secondaryText(),
            )
            Spacer(modifier = Modifier.height(AppSpacingTokens.Small))
        }
    }
}
