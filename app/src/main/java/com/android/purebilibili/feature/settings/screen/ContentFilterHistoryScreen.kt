package com.android.purebilibili.feature.settings.screen

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.R
import com.android.purebilibili.core.store.CONTENT_FILTER_HISTORY_LIMIT
import com.android.purebilibili.core.store.ContentFilterHistory
import com.android.purebilibili.core.store.ContentFilterHit
import com.android.purebilibili.core.store.ContentFilterReason
import com.android.purebilibili.core.store.FilteredContentKind
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.AppFilterChip
import com.android.purebilibili.core.ui.components.AppHorizontalDivider
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.feature.settings.ui.SettingsPageScaffold

@Composable
internal fun ContentFilterHistoryScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val revision by ContentFilterHistory.revision.collectAsStateWithLifecycle()
    val account by TokenManager.accountIdentity.collectAsStateWithLifecycle()
    var kind by rememberSaveable { mutableStateOf(FilteredContentKind.COMMENT) }
    val entries = remember(revision, account, kind) { ContentFilterHistory.snapshot(kind) }

    ContentFilterHistoryPage(
        kind = kind,
        entries = entries,
        onKindSelected = { kind = it },
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContentFilterHistoryPage(
    kind: FilteredContentKind,
    entries: List<ContentFilterHit>,
    onKindSelected: (FilteredContentKind) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = key(kind) { rememberLazyListState() }
    SettingsPageScaffold(
        title = stringResource(R.string.filtered_content_history_title),
        onBack = onBack,
        backContentDescription = stringResource(R.string.common_back),
        bottomContentPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
        modifier = modifier,
        listState = listState,
        header = {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppText(
                    text = stringResource(R.string.filtered_content_history_limit, CONTENT_FILTER_HISTORY_LIMIT),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppContentColors.secondaryText(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppFilterChip(
                        selected = kind == FilteredContentKind.COMMENT,
                        onClick = { onKindSelected(FilteredContentKind.COMMENT) },
                        label = { AppText(stringResource(R.string.filtered_content_history_comments)) },
                    )
                    AppFilterChip(
                        selected = kind == FilteredContentKind.VIDEO,
                        onClick = { onKindSelected(FilteredContentKind.VIDEO) },
                        label = { AppText(stringResource(R.string.filtered_content_history_videos)) },
                    )
                }
            }
        },
        lazyListContent = {
            if (entries.isEmpty()) {
                item {
                    AppText(
                        text = stringResource(R.string.filtered_content_history_empty),
                        color = AppContentColors.secondaryText(),
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                items(entries, key = { it.id }) { hit ->
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        FilteredContentRow(hit, Modifier.padding(vertical = 12.dp))
                        AppHorizontalDivider()
                    }
                }
            }
        },
    )
}

@Composable
private fun FilteredContentRow(hit: ContentFilterHit, modifier: Modifier = Modifier) {
    val reason = if (hit.reason == ContentFilterReason.PLUGIN) hit.pluginName
    else stringResource(hit.reason.labelResource())
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AppText(if (hit.text.isBlank()) stringResource(R.string.filtered_content_history_no_text) else hit.text)
        if (hit.author.isNotBlank()) {
            AppText(hit.author, style = MaterialTheme.typography.bodySmall, color = AppContentColors.secondaryText())
        }
        AppText(reason, style = MaterialTheme.typography.labelMedium, color = AppContentColors.accent())
    }
}

@StringRes
private fun ContentFilterReason.labelResource(): Int = when (this) {
    ContentFilterReason.BLOCKED_USER -> R.string.filtered_content_reason_user
    ContentFilterReason.KEYWORD -> R.string.filtered_content_reason_keyword
    ContentFilterReason.TITLE_PATTERN -> R.string.filtered_content_reason_pattern
    ContentFilterReason.AD_TITLE -> R.string.filtered_content_reason_ad_title
    ContentFilterReason.GOODS -> R.string.filtered_content_reason_goods
    ContentFilterReason.AI_BOT -> R.string.ai_reply_filter_title
    ContentFilterReason.REPEATED_REPLY -> R.string.repeated_reply_filter_title
    ContentFilterReason.DURATION -> R.string.filtered_content_reason_duration
    ContentFilterReason.PLAY_COUNT -> R.string.filtered_content_reason_plays
    ContentFilterReason.LIKE_RATIO -> R.string.filtered_content_reason_likes
    ContentFilterReason.CATEGORY -> R.string.filtered_content_reason_category
    ContentFilterReason.SPONSORED -> R.string.filtered_content_reason_sponsored
    ContentFilterReason.CLICKBAIT -> R.string.filtered_content_reason_clickbait
    ContentFilterReason.TAG -> R.string.filtered_content_reason_tag
    ContentFilterReason.PLUGIN -> R.string.filtered_content_reason_plugin
}
