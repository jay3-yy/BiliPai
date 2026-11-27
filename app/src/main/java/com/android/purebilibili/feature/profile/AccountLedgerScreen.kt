package com.android.purebilibili.feature.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppTopBar
import com.android.purebilibili.core.ui.ImmersiveAppScaffold
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.ui.components.*
import com.android.purebilibili.core.ui.rememberAppBackIcon
import com.android.purebilibili.data.model.AccountLedgerKind

@Composable
internal fun AccountLedgerScreen(
    initialKind: AccountLedgerKind,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    model: AccountLedgerViewModel = viewModel(),
) {
    val identity by TokenManager.accountIdentity.collectAsStateWithLifecycle()
    val rawState by model.state.collectAsStateWithLifecycle()
    var selected by rememberSaveable(identity.mid, identity.generation, initialKind) { mutableStateOf(initialKind) }
    val state = rawState.takeIf { it.identity == identity } ?: AccountLedgerState(identity = identity)
    LaunchedEffect(model, identity, rawState.identity, selected) {
        if (rawState.identity == identity) model.load(selected)
    }
    AccountLedgerContent(
        state = state,
        selected = selected,
        onSelect = { selected = it },
        onRefresh = { model.load(selected, refresh = true) },
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
internal fun AccountLedgerContent(
    state: AccountLedgerState,
    selected: AccountLedgerKind,
    onSelect: (AccountLedgerKind) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val section = state.section(selected)
    val loggedIn = (state.identity.mid ?: 0L) > 0L
    ImmersiveAppScaffold(
        modifier = modifier,
        topBar = {
            Column {
                AppTopBar(
                    title = selected.title,
                    navigationIcon = { AppIconButton(onClick = onBack) { AppIcon(rememberAppBackIcon(), "返回") } },
                    actions = { AppTextButton(onClick = onRefresh, enabled = loggedIn && !section.loading) { AppText("刷新") } },
                )
                val options = AccountLedgerKind.entries.map { AppSegmentOption(it, it.title) }
                if (LocalAppThemeConfig.current.liquidGlassEnabled) {
                    AppThemeAdaptiveTabRow(
                        options = options,
                        selectedValue = selected,
                        onSelectionChange = onSelect,
                        role = AppTabRowRole.SECONDARY,
                        // 等宽玻璃槽位的指示器覆盖完整槽位，原生标签栏保留原有几何。
                        liquidGlassAdaptIndicatorToLabel = false,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                } else {
                    AppNativeTabRow(
                        options = options,
                        selectedValue = selected,
                        onSelectionChange = onSelect,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            key(state.identity, selected) {
                val listState = rememberLazyListState()
                AppLazyColumn(
                    state = listState,
                    modifier = Modifier.widthIn(max = 760.dp).fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    item(key = "account") {
                        if (loggedIn) {
                            AppText(
                                "展示近期${selected.title}变动记录（UID ${state.identity.mid}）",
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                color = AppContentColors.secondaryText(),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                        } else {
                            AppEmptyState(
                                title = "请先登录",
                                message = "登录后即可查看硬币与经验明细",
                                presentation = AppContentStatePresentation.INLINE,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    if (loggedIn) {
                        if (section.loading) item(key = "loading") {
                            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                AppCircularProgressIndicator()
                            }
                        }
                        section.error?.let { error ->
                            item(key = "error") {
                                AppErrorState(
                                    title = "加载失败",
                                    message = if (section.entries != null) "$error\n以下保留上次加载的记录" else error,
                                    presentation = AppContentStatePresentation.INLINE,
                                    primaryAction = if (!section.loading) AppContentStateAction("重试", onRefresh) else null,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        val entries = section.entries
                        if (entries != null) {
                            if (entries.isEmpty() && !section.loading && section.error == null) {
                                item(key = "empty") {
                                    AppEmptyState(
                                        title = "暂无近期${selected.title}",
                                        presentation = AppContentStatePresentation.INLINE,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                            itemsIndexed(entries, key = { index, _ -> "entry_$index" }) { _, entry ->
                                val delta = formatLedgerDelta(entry.delta)
                                val deltaColor = when (delta.direction) {
                                    LedgerDeltaDirection.INCREASE -> AppContentColors.accent()
                                    LedgerDeltaDirection.DECREASE -> AppContentColors.error()
                                    LedgerDeltaDirection.ZERO, LedgerDeltaDirection.UNKNOWN -> AppContentColors.secondaryText()
                                }
                                AppListItem(
                                    headlineContent = { AppText(entry.reason?.takeIf { it.isNotBlank() } ?: "未提供变动原因") },
                                    supportingContent = { AppText(entry.time?.takeIf { it.isNotBlank() } ?: "时间未知") },
                                    trailingContent = {
                                        Column(horizontalAlignment = Alignment.End) {
                                            AppText(
                                                delta.text,
                                                color = deltaColor,
                                                modifier = Modifier.widthIn(max = 120.dp),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            AppText(if (selected == AccountLedgerKind.COINS) "硬币" else "经验", color = AppContentColors.secondaryText())
                                        }
                                    },
                                )
                            }
                        } else if (!section.loading && section.error == null) {
                            item(key = "pending") { AppText("等待加载…", Modifier.padding(16.dp)) }
                        }
                    }
                }
            }
        }
    }
}
