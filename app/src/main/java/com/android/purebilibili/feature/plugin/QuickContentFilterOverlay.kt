package com.android.purebilibili.feature.plugin

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.plugin.BuiltinPluginIds
import com.android.purebilibili.core.plugin.PluginManager
import com.android.purebilibili.core.store.ContentFilterRules
import com.android.purebilibili.core.store.ContentFilterScope
import com.android.purebilibili.core.store.home.DynamicSettingsStore
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppModalBottomSheet
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.LocalBottomBarContentPadding
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppFilterChip
import com.android.purebilibili.core.ui.components.AppHorizontalDivider
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppOutlinedTextField
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.components.appVerticalScroll as verticalScroll
import com.android.purebilibili.data.repository.BlockedUpRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val QUICK_HIDE_FEEDBACK_AUTO_DISMISS_MS = 5_000L

/**
 * 隐藏视频或动态后的两级无打扰交互宿主：
 * 1. 底部轻量反馈胶囊（5 秒自动消失，不打断连续浏览）；
 * 2. 点击「精细屏蔽」后展开即时屏蔽面板（候选词一键点选、自定义关键词、拉黑 UP 主与动态类型一键隐藏）。
 */
@Composable
fun QuickContentFilterHost(
    pendingFeedback: QuickContentFilterTarget?,
    onDismissFeedback: () -> Unit,
    editingTarget: QuickContentFilterTarget?,
    onOpenEditor: (QuickContentFilterTarget) -> Unit,
    onDismissEditor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bottomInset = LocalBottomBarContentPadding.current

    LaunchedEffect(pendingFeedback, editingTarget) {
        if (pendingFeedback != null && editingTarget == null) {
            delay(QUICK_HIDE_FEEDBACK_AUTO_DISMISS_MS)
            onDismissFeedback()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = bottomInset + 12.dp, start = 16.dp, end = 16.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = pendingFeedback != null && editingTarget == null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            val target = pendingFeedback ?: return@AnimatedVisibility
            AppSurface(
                shape = AppShapes.container(ContainerLevel.Card),
                color = AppSurfaceTokens.surfaceContainerHigh(),
                tonalElevation = 4.dp,
                shadowElevation = 6.dp,
                modifier = Modifier.widthIn(max = 460.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AppIcon(
                        imageVector = Icons.Outlined.Tune,
                        contentDescription = null,
                        tint = AppContentColors.accent(),
                        modifier = Modifier.size(18.dp),
                    )
                    AppText(
                        text = target.sourceLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppContentColors.primaryText(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    AppTextButton(
                        onClick = {
                            onDismissFeedback()
                            onOpenEditor(target)
                        },
                    ) {
                        AppText("精细屏蔽", fontWeight = FontWeight.SemiBold)
                    }
                    AppIconButton(
                        onClick = onDismissFeedback,
                        modifier = Modifier.size(32.dp),
                    ) {
                        AppIcon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "关闭提示",
                            tint = AppContentColors.secondaryText(),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }

    if (editingTarget != null) {
        QuickContentFilterSheet(
            target = editingTarget,
            onDismiss = onDismissEditor,
        )
    }
}

private enum class QuickKeywordMatchMode(val label: String, val hint: String) {
    CONTAINS("包含即屏蔽", "标题或正文包含任一词即屏蔽"),
    EXACT("精准匹配", "仅完整独立词命中，避免误杀长词"),
    ALL_OF("同时包含", "将所选多个词合并为一条组合规则，同时出现才屏蔽"),
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickContentFilterSheet(
    target: QuickContentFilterTarget,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedCandidates by remember(target) { mutableStateOf<Set<String>>(emptySet()) }
    var customKeywordsText by remember(target) { mutableStateOf("") }
    var matchMode by remember(target) { mutableStateOf(QuickKeywordMatchMode.CONTAINS) }
    var selectedScopes by remember(target) { mutableStateOf(setOf(target.defaultScope)) }
    var blockAuthor by remember(target) { mutableStateOf(false) }
    var selectedDynamicShortcuts by remember(target) { mutableStateOf<Set<QuickDynamicTypeShortcut>>(emptySet()) }
    var isSaving by remember(target) { mutableStateOf(false) }

    val customKeywords = remember(customKeywordsText) {
        customKeywordsText
            .lineSequence()
            .flatMap { it.split(',', '，') }
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toList()
    }
    val rawKeywords = remember(selectedCandidates, customKeywords) {
        (selectedCandidates.toList() + customKeywords).distinctBy { it.lowercase() }
    }
    val formattedKeywords = remember(rawKeywords, matchMode) {
        if (rawKeywords.isEmpty()) emptyList()
        else when (matchMode) {
            QuickKeywordMatchMode.CONTAINS -> rawKeywords
            QuickKeywordMatchMode.EXACT -> rawKeywords.map { word ->
                if (word.startsWith("=") || word.startsWith("regex:", ignoreCase = true) || '+' in word) word
                else "=$word"
            }
            QuickKeywordMatchMode.ALL_OF -> {
                val (explicit, plain) = rawKeywords.partition {
                    it.startsWith("=") || it.startsWith("regex:", ignoreCase = true) || '+' in it
                }
                buildList {
                    addAll(explicit)
                    if (plain.size >= 2) add(plain.joinToString("+"))
                    else addAll(plain)
                }
            }
        }
    }
    val hasAction = (formattedKeywords.isNotEmpty() && selectedScopes.isNotEmpty()) ||
        blockAuthor ||
        selectedDynamicShortcuts.isNotEmpty()

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
            AppText(
                text = "精细屏蔽",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = AppContentColors.primaryText(),
            )
            if (target.previewText.isNotBlank()) {
                AppText(
                    text = target.previewText,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppContentColors.secondaryText(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (target.candidateKeywords.isNotEmpty()) {
                AppText(
                    text = "点选屏蔽关键词",
                    style = MaterialTheme.typography.labelLarge,
                    color = AppContentColors.secondaryText(),
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    target.candidateKeywords.forEach { keyword ->
                        val selected = keyword in selectedCandidates
                        AppFilterChip(
                            selected = selected,
                            onClick = {
                                selectedCandidates = if (selected) selectedCandidates - keyword else selectedCandidates + keyword
                            },
                            label = { AppText(keyword) },
                        )
                    }
                }
            }

            AppOutlinedTextField(
                value = customKeywordsText,
                onValueChange = { customKeywordsText = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 1,
                maxLines = 3,
                placeholderText = "输入屏蔽词（支持 =精准词、词1+词2、regex:正则）",
                placeholder = { AppText("输入屏蔽词（支持 =精准词、词1+词2、regex:正则）") },
            )

            AppText(
                text = "匹配方式 · ${matchMode.hint}",
                style = MaterialTheme.typography.labelLarge,
                color = AppContentColors.secondaryText(),
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                QuickKeywordMatchMode.entries.forEach { mode ->
                    AppFilterChip(
                        selected = matchMode == mode,
                        onClick = { matchMode = mode },
                        label = { AppText(mode.label) },
                    )
                }
            }

            AppText(
                text = "屏蔽词生效范围",
                style = MaterialTheme.typography.labelLarge,
                color = AppContentColors.secondaryText(),
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(
                    ContentFilterScope.VIDEO to "视频标题",
                    ContentFilterScope.DYNAMIC to "动态内容",
                    ContentFilterScope.COMMENT to "评论正文",
                    ContentFilterScope.DANMAKU to "弹幕内容",
                ).forEach { (filterScope, label) ->
                    val checked = filterScope in selectedScopes
                    AppFilterChip(
                        selected = checked,
                        onClick = {
                            selectedScopes = if (checked) selectedScopes - filterScope else selectedScopes + filterScope
                        },
                        label = { AppText(label) },
                    )
                }
            }

            if (target.authorMid > 0L || target.dynamicTypeShortcuts.isNotEmpty()) {
                AppHorizontalDivider()
                AppText(
                    text = "快捷屏蔽",
                    style = MaterialTheme.typography.labelLarge,
                    color = AppContentColors.secondaryText(),
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (target.authorMid > 0L) {
                        val authorTitle = target.authorName.ifBlank { "UP 主 (${target.authorMid})" }
                        AppFilterChip(
                            selected = blockAuthor,
                            onClick = { blockAuthor = !blockAuthor },
                            label = { AppText("拉黑 $authorTitle") },
                        )
                    }
                    target.dynamicTypeShortcuts.forEach { shortcut ->
                        val checked = shortcut in selectedDynamicShortcuts
                        AppFilterChip(
                            selected = checked,
                            onClick = {
                                selectedDynamicShortcuts = if (checked) {
                                    selectedDynamicShortcuts - shortcut
                                } else {
                                    selectedDynamicShortcuts + shortcut
                                }
                            },
                            label = { AppText(shortcut.label) },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppTextButton(onClick = onDismiss, enabled = !isSaving) {
                    AppText("取消")
                }
                AppButton(
                    enabled = hasAction && !isSaving,
                    onClick = {
                        isSaving = true
                        scope.launch {
                            val summaryParts = mutableListOf<String>()
                            if (formattedKeywords.isNotEmpty() && selectedScopes.isNotEmpty()) {
                                val added = ContentFilterRules.appendKeywords(
                                    context = context,
                                    newKeywords = formattedKeywords,
                                    ensureScopes = selectedScopes,
                                )
                                PluginManager.setEnabled(BuiltinPluginIds.FEED_FILTER, true)
                                summaryParts.add(if (added > 0) "已新增 $added 个屏蔽词" else "已更新屏蔽词范围")
                            }
                            if (blockAuthor && target.authorMid > 0L) {
                                BlockedUpRepository.getInstance(context).blockUpWithBilibiliSync(
                                    mid = target.authorMid,
                                    name = target.authorName,
                                    face = target.authorFace,
                                )
                                summaryParts.add("已拉黑该 UP 主")
                            }
                            if (selectedDynamicShortcuts.isNotEmpty()) {
                                val current = DynamicSettingsStore.observeFeedTypeFilters(context).first()
                                DynamicSettingsStore.setFeedTypeFilters(
                                    context = context,
                                    filters = current.copy(
                                        hideForward = current.hideForward || QuickDynamicTypeShortcut.FORWARD in selectedDynamicShortcuts,
                                        hideLottery = current.hideLottery || QuickDynamicTypeShortcut.LOTTERY in selectedDynamicShortcuts,
                                        hideGoods = current.hideGoods || QuickDynamicTypeShortcut.GOODS in selectedDynamicShortcuts,
                                        hideReserve = current.hideReserve || QuickDynamicTypeShortcut.RESERVE in selectedDynamicShortcuts,
                                        hideArticle = current.hideArticle || QuickDynamicTypeShortcut.ARTICLE in selectedDynamicShortcuts,
                                        hideLiveRcmd = current.hideLiveRcmd || QuickDynamicTypeShortcut.LIVE_RCMD in selectedDynamicShortcuts,
                                    ),
                                )
                                summaryParts.add("已开启同类动态自动隐藏")
                            }
                            if (summaryParts.isNotEmpty()) {
                                Toast.makeText(context, summaryParts.joinToString("，"), Toast.LENGTH_SHORT).show()
                            }
                            isSaving = false
                            onDismiss()
                        }
                    },
                ) {
                    AppText("立即生效")
                }
            }
        }
    }
}
