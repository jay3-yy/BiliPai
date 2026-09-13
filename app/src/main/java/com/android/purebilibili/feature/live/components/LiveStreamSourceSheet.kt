package com.android.purebilibili.feature.live.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SettingsInputComponent
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppModalBottomSheet
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppSingleChoiceRow
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.feature.live.LivePlaybackCandidate

/**
 * 手动线路选择弹层
 *
 * 列出直播流的全部候选（协议/格式/编码）与各候选内的 CDN 线路，
 * 点击即切换；当前播放的候选与线路高亮。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LiveStreamSourceSheet(
    candidates: List<LivePlaybackCandidate>,
    activeCandidateIndex: Int,
    activeUrlIndex: Int,
    onSelect: (candidateIndex: Int, urlIndex: Int) -> Unit,
    onDismiss: () -> Unit
) {
    AppModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = AppSpacingTokens.ExtraLarge,
                    vertical = AppSpacingTokens.Small,
                ),
            verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.Large)
        ) {
            AppText(
                text = "直播线路",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            if (candidates.isEmpty()) {
                AppText(
                    text = "暂无可用线路，请稍后重试",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = AppSpacingTokens.Large)
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small),
                    modifier = Modifier.height(420.dp)
                ) {
                    itemsIndexed(candidates) { candidateIndex, candidate ->
                        val urls = candidate.urls
                        if (urls.isNotEmpty()) {
                            urls.forEachIndexed { urlIndex, _ ->
                                val isActive =
                                    candidateIndex == activeCandidateIndex && urlIndex == activeUrlIndex
                                LiveSourceRow(
                                    candidate = candidate,
                                    urlIndex = urlIndex,
                                    totalUrls = urls.size,
                                    isActive = isActive,
                                    onClick = { onSelect(candidateIndex, urlIndex) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveSourceRow(
    candidate: LivePlaybackCandidate,
    urlIndex: Int,
    totalUrls: Int,
    isActive: Boolean,
    onClick: () -> Unit
) {
    AppSingleChoiceRow(
        selected = isActive,
        onClick = onClick,
        shape = AppShapes.container(ContainerLevel.Card),
        modifier = Modifier.fillMaxWidth()
    ) {
            AppIcon(
                imageVector = Icons.Outlined.SettingsInputComponent,
                contentDescription = null,
                tint = AppSurfaceTokens.onSurfaceVariantActions()
            )
            Spacer(Modifier.width(AppSpacingTokens.Medium))
            Column(modifier = Modifier.weight(1f)) {
                AppText(
                    text = buildString {
                        append(resolveLiveStreamProtocolLabel(candidate.protocolName))
                        append(" · ")
                        append(candidate.formatName.ifBlank { "?" })
                        append(" · ")
                        append(candidate.codecName.uppercase().ifBlank { "?" })
                    },
                    color = AppSurfaceTokens.onSurfaceContainerHigh(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                AppText(
                    text = "线路 ${urlIndex + 1}/$totalUrls",
                    color = AppSurfaceTokens.onSurfaceVariantSummary(),
                    style = MaterialTheme.typography.bodySmall
                )
            }
    }
}

internal fun resolveLiveStreamProtocolLabel(protocolName: String): String {
    return when (protocolName) {
        "http_hls" -> "HLS"
        "http_stream" -> "FLV"
        else -> protocolName.ifBlank { "未知" }
    }
}
