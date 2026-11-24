package com.android.purebilibili.feature.settings

import com.android.purebilibili.core.ui.AppContentColors

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.android.purebilibili.R
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.*

private val SETTINGS_SEARCH_QUICK_TOPICS = listOf(
    "小窗",
    "后台播放",
    "空降助手",
    "液态玻璃",
    "画质",
    "双击点赞",
    "弹幕",
    "底栏",
    "主题色",
    "自动连播",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsSearchQuickTopicsSection(
    onTopicClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chipShape = AppShapes.container(ContainerLevel.Tag)
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        AppText(
            text = "大家常找",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SETTINGS_SEARCH_QUICK_TOPICS.forEach { topic ->
                AppSurface(
                    shape = chipShape,
                    color = AppSurfaceTokens.cardContainer(),
                    modifier = Modifier
                        .clip(chipShape)
                        .clickable { onTopicClick(topic) },
                ) {
                    AppText(
                        text = topic,
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppContentColors.primaryText(),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun SettingsSearchHistorySection(
    history: List<String>,
    onQueryClick: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AppText(stringResource(R.string.settings_search_history_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (history.isNotEmpty()) {
                AppTextButton(onClick = onClear, modifier = Modifier.heightIn(min = 48.dp)) {
                    AppText(stringResource(R.string.settings_search_history_clear_all))
                }
            }
        }
        if (history.isEmpty()) {
            AppText(
                stringResource(R.string.settings_search_history_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = AppContentColors.secondaryText(),
                modifier = Modifier.padding(vertical = 16.dp),
            )
        } else AppPreferenceGroup {
            history.forEachIndexed { index, query ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            .clickable { onQueryClick(query) }.padding(horizontal = 16.dp, vertical = 12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        AppText(query, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    AppIconButton(onClick = { onDelete(query) }, modifier = Modifier.size(48.dp)) {
                        AppIcon(Icons.Rounded.Close, contentDescription = stringResource(R.string.settings_search_history_delete, query))
                    }
                }
                if (index != history.lastIndex) AppPreferenceDivider()
            }
        }
    }
}
