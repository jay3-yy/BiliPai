package com.android.purebilibili.feature.settings.screen

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.R
import com.android.purebilibili.core.store.CONTENT_FILTER_HISTORY_LIMIT
import com.android.purebilibili.core.store.ContentFilterHistory
import com.android.purebilibili.core.store.ContentFilterHit
import com.android.purebilibili.core.store.ContentFilterReason
import com.android.purebilibili.core.store.ContentFilterRules
import com.android.purebilibili.core.store.FilteredContentKind
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.AppFilterChip
import com.android.purebilibili.core.ui.components.AppHorizontalDivider
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.data.repository.BlockedUpRepository
import com.android.purebilibili.feature.settings.ui.SettingsPageScaffold
import kotlinx.coroutines.launch

@Composable
internal fun ContentFilterHistoryScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onVideoClick: (String) -> Unit = {},
    onDynamicClick: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val revision by ContentFilterHistory.revision.collectAsStateWithLifecycle()
    val account by TokenManager.accountIdentity.collectAsStateWithLifecycle()
    var kind by rememberSaveable { mutableStateOf(FilteredContentKind.COMMENT) }
    val entries = remember(revision, account, kind) { ContentFilterHistory.snapshot(kind) }

    ContentFilterHistoryPage(
        kind = kind,
        entries = entries,
        onKindSelected = { kind = it },
        onClearKind = { ContentFilterHistory.clear(kind) },
        onOpenHit = { hit ->
            when (kind) {
                FilteredContentKind.VIDEO -> {
                    if (hit.id.startsWith("BV", ignoreCase = true)) {
                        onVideoClick(hit.id)
                    }
                }
                FilteredContentKind.DYNAMIC -> {
                    if (hit.id.isNotBlank() && hit.id.all(Char::isDigit)) {
                        onDynamicClick(hit.id)
                    }
                }
                FilteredContentKind.COMMENT -> Unit
            }
        },
        onAllowKeyword = { hit ->
            val rule = hit.matchedRule
            if (rule.isNotBlank()) {
                scope.launch {
                    val removed = ContentFilterRules.removeKeyword(context, rule)
                    ContentFilterHistory.remove(kind, hit.id)
                    val msg = if (removed) {
                        context.getString(R.string.filtered_content_action_keyword_allowed, rule)
                    } else {
                        context.getString(R.string.filtered_content_action_keyword_already_removed, rule)
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }
        },
        onUnblockUser = { hit ->
            if (hit.authorMid > 0L) {
                scope.launch {
                    BlockedUpRepository.getInstance(context).unblockUp(hit.authorMid)
                    ContentFilterHistory.remove(kind, hit.id)
                    Toast.makeText(
                        context,
                        context.getString(
                            R.string.filtered_content_action_user_unblocked,
                            hit.author.ifBlank { hit.authorMid.toString() },
                        ),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        },
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
    onClearKind: () -> Unit,
    onOpenHit: (ContentFilterHit) -> Unit,
    onAllowKeyword: (ContentFilterHit) -> Unit,
    onUnblockUser: (ContentFilterHit) -> Unit,
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppText(
                        text = stringResource(R.string.filtered_content_history_limit, CONTENT_FILTER_HISTORY_LIMIT),
                        style = MaterialTheme.typography.bodySmall,
                        color = AppContentColors.secondaryText(),
                        modifier = Modifier.weight(1f),
                    )
                    if (entries.isNotEmpty()) {
                        AppTextButton(onClick = onClearKind) {
                            AppText(stringResource(R.string.filtered_content_history_clear))
                        }
                    }
                }
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
                    AppFilterChip(
                        selected = kind == FilteredContentKind.DYNAMIC,
                        onClick = { onKindSelected(FilteredContentKind.DYNAMIC) },
                        label = { AppText(stringResource(R.string.filtered_content_history_dynamics)) },
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
                    val canOpen = when (kind) {
                        FilteredContentKind.VIDEO -> hit.id.startsWith("BV", ignoreCase = true)
                        FilteredContentKind.DYNAMIC -> hit.id.isNotBlank() && hit.id.all(Char::isDigit)
                        FilteredContentKind.COMMENT -> false
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (canOpen) Modifier.clickable { onOpenHit(hit) } else Modifier,
                            )
                            .padding(horizontal = 16.dp),
                    ) {
                        FilteredContentRow(
                            hit = hit,
                            onAllowKeyword = { onAllowKeyword(hit) },
                            onUnblockUser = { onUnblockUser(hit) },
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                        AppHorizontalDivider()
                    }
                }
            }
        },
    )
}

@Composable
private fun FilteredContentRow(
    hit: ContentFilterHit,
    onAllowKeyword: () -> Unit,
    onUnblockUser: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val baseReason = if (hit.reason == ContentFilterReason.PLUGIN) hit.pluginName
    else stringResource(hit.reason.labelResource())
    val reason = if (hit.reason == ContentFilterReason.KEYWORD && hit.matchedRule.isNotBlank()) {
        "$baseReason · ${hit.matchedRule}"
    } else {
        baseReason
    }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AppText(if (hit.text.isBlank()) stringResource(R.string.filtered_content_history_no_text) else hit.text)
        if (hit.author.isNotBlank()) {
            AppText(hit.author, style = MaterialTheme.typography.bodySmall, color = AppContentColors.secondaryText())
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText(
                text = reason,
                style = MaterialTheme.typography.labelMedium,
                color = AppContentColors.accent(),
                modifier = Modifier.weight(1f),
            )
            if (hit.reason == ContentFilterReason.KEYWORD && hit.matchedRule.isNotBlank()) {
                AppTextButton(onClick = onAllowKeyword) {
                    AppText(stringResource(R.string.filtered_content_action_allow_keyword))
                }
            } else if (hit.reason == ContentFilterReason.BLOCKED_USER && hit.authorMid > 0L) {
                AppTextButton(onClick = onUnblockUser) {
                    AppText(stringResource(R.string.filtered_content_action_unblock_user))
                }
            }
        }
    }
}

@StringRes
private fun ContentFilterReason.labelResource(): Int = when (this) {
    ContentFilterReason.BLOCKED_USER -> R.string.filtered_content_reason_user
    ContentFilterReason.KEYWORD -> R.string.filtered_content_reason_keyword
    ContentFilterReason.TITLE_PATTERN -> R.string.filtered_content_reason_pattern
    ContentFilterReason.AD_TITLE -> R.string.filtered_content_reason_ad_title
    ContentFilterReason.GOODS -> R.string.filtered_content_reason_goods
    ContentFilterReason.GOODS_DYNAMIC -> R.string.filtered_content_reason_goods_dynamic
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
    ContentFilterReason.DYNAMIC_FORWARD -> R.string.filtered_content_reason_dynamic_forward
    ContentFilterReason.DYNAMIC_LOTTERY -> R.string.filtered_content_reason_dynamic_lottery
    ContentFilterReason.DYNAMIC_RESERVE -> R.string.filtered_content_reason_dynamic_reserve
    ContentFilterReason.DYNAMIC_ARTICLE -> R.string.filtered_content_reason_dynamic_article
    ContentFilterReason.DYNAMIC_LIVE -> R.string.filtered_content_reason_dynamic_live
}
