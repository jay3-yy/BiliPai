package com.android.purebilibili.feature.live.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Report
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.components.AppAssistChip
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppInputChip
import com.android.purebilibili.core.ui.rememberAppClearIcon
import androidx.compose.material3.ExperimentalMaterial3Api
import com.android.purebilibili.core.ui.components.AppFilterChip
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.AppModalBottomSheet
import com.android.purebilibili.core.ui.components.AppOutlinedTextField
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import com.android.purebilibili.core.ui.components.AppLazyVerticalGrid as LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.ui.components.AppHorizontalDivider
import com.android.purebilibili.core.ui.components.AppSegmentOption
import com.android.purebilibili.core.ui.components.AppThemeAdaptiveTabRow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.android.purebilibili.data.repository.DefaultLiveReportReasons
import com.android.purebilibili.data.repository.LiveEmoticonItem
import com.android.purebilibili.data.repository.LiveEmoticonPackage
import com.android.purebilibili.data.repository.LiveReportReason
import com.android.purebilibili.data.repository.LiveShieldInfo
import com.android.purebilibili.data.repository.LiveShieldUser
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.feature.live.LiveDanmakuItem
import com.android.purebilibili.feature.live.resolveLiveSheetVisualSpec

@Composable
fun LiveReportDialog(
    target: LiveDanmakuItem,
    onDismiss: () -> Unit,
    onReport: (LiveReportReason) -> Unit
) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        icon = { AppIcon(Icons.Outlined.Report, contentDescription = null) },
        title = { AppText("举报弹幕") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.Medium)) {
                AppText(
                    text = "@${target.uname.ifBlank { target.uid.toString() }}：${target.text}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small)) {
                    DefaultLiveReportReasons.forEach { reason ->
                        AppAssistChip(
                            onClick = { onReport(reason) },
                            label = { AppText(reason.label) }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            AppTextButton(onClick = onDismiss) {
                AppText("取消")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveEmoticonSheet(
    packages: List<LiveEmoticonPackage>,
    onInsertText: (LiveEmoticonItem) -> Unit,
    onSendEmoticon: (LiveEmoticonItem) -> Unit,
    onDismiss: () -> Unit
) {
    AppModalBottomSheet(onDismissRequest = onDismiss) {
        LiveEmoticonPanel(packages, onInsertText, onSendEmoticon)
    }
}

@Composable
fun LiveEmoticonPanel(
    packages: List<LiveEmoticonPackage>,
    onInsertText: (LiveEmoticonItem) -> Unit,
    onSendEmoticon: (LiveEmoticonItem) -> Unit,
    enabled: Boolean = true,
) {
    val visualSpec = remember { resolveLiveSheetVisualSpec() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val pager = androidx.compose.foundation.pager.rememberPagerState(pageCount = { packages.size.coerceAtLeast(1) })
    Column(Modifier.fillMaxWidth().height(visualSpec.emoticonListMaxHeightDp.dp)) {
        com.android.purebilibili.core.ui.components.AppHorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            val pkg = packages.getOrNull(page)
            val emotes = pkg?.items.orEmpty()
            if (emotes.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AppText("当前直播间暂无可用表情", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val first = emotes.first()
                val width = (first.width.coerceAtLeast(80) / 80f * 40f).dp
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(width),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(emotes, key = { index, item -> "${pkg?.id}-${item.emoticonUnique}-$index" }) { _, item ->
                        LiveEmoticonGridCell(item, enabled) {
                            if (item.dmType == 0) onInsertText(item) else onSendEmoticon(item)
                        }
                    }
                }
            }
        }
        if (packages.size > 1) AppThemeAdaptiveTabRow(
            role = com.android.purebilibili.core.ui.components.AppTabRowRole.SECONDARY,
            options = packages.mapIndexed { index, pkg -> AppSegmentOption(index, pkg.name) },
            selectedValue = pager.currentPage,
            onSelectionChange = { scope.launch { com.android.purebilibili.navigation.animatePagerSelection(pager, it) } },
            scrollable = true,
            centerContent = false,
            enabled = enabled,
            indicatorPositionProvider = { pager.currentPage + pager.currentPageOffsetFraction },
            isScrollInProgressProvider = { pager.isScrollInProgress },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun LiveEmoticonGridCell(
    item: LiveEmoticonItem,
    enabled: Boolean,
    onClick: () -> Unit
) {
    var showPreview by remember { mutableStateOf(false) }
    Box(Modifier.combinedClickable(enabled = enabled, onClick = onClick, onLongClick = { showPreview = true })) {
    AppSurface(
        color = AppSurfaceTokens.cardContainer(),
        shape = AppShapes.container(ContainerLevel.Chip),
        modifier = Modifier
            .aspectRatio(item.width.coerceAtLeast(1).toFloat() / item.height.coerceAtLeast(1))
            .clip(AppShapes.container(ContainerLevel.Chip))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(AppSpacingTokens.Small),
            contentAlignment = Alignment.Center
        ) {
            if (item.url.isNotBlank()) {
                AsyncImage(
                    model = item.url,
                    contentDescription = item.description.ifBlank { item.emoji },
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                AppText(
                    text = item.emoji.ifBlank { item.description.ifBlank { "表情" } },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
    }
    if (showPreview) AppAlertDialog(
        onDismissRequest = { showPreview = false },
        title = { AppText(item.description.ifBlank { item.emoji }) },
        text = { AsyncImage(model = item.url, contentDescription = item.displayText, modifier = Modifier.fillMaxWidth().height(160.dp), contentScale = ContentScale.Fit) },
        confirmButton = { AppTextButton(onClick = { showPreview = false }) { AppText("关闭") } },
    )
}

@Composable
fun LiveDmBlockSheet(
    shieldInfo: LiveShieldInfo?,
    isLoggedIn: Boolean,
    onAddKeyword: (String) -> Unit,
    onAddUser: (Long) -> Unit,
    onDeleteKeyword: (String) -> Unit,
    onUnblockUser: (LiveShieldUser) -> Unit,
    onSetRule: (String, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var showAdd by remember { mutableStateOf(false) }
    var value by remember { mutableStateOf("") }
    var pendingKeyword by remember { mutableStateOf<String?>(null) }
    var pendingUser by remember { mutableStateOf<LiveShieldUser?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val pager = androidx.compose.foundation.pager.rememberPagerState(pageCount = { 2 })
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        com.android.purebilibili.core.ui.ImmersiveAppScaffold(
            topBar = {
                com.android.purebilibili.core.ui.AppTopBar(
                    title = "弹幕屏蔽",
                    navigationIcon = {
                        AppIconButton(onClick = onDismiss) { AppIcon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
                    },
                    actions = {
                        AppTextButton(enabled = isLoggedIn, onClick = { value = ""; showAdd = true }) { AppText("添加") }
                    },
                )
            },
        ) { padding ->
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
                val landscape = maxWidth > maxHeight
                val rules: @Composable () -> Unit = {
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp)) {
                        if (!isLoggedIn) AppText("登录后可同步屏蔽设置", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LiveRuleSection(shieldInfo, isLoggedIn, onSetRule)
                    }
                }
                val lists: @Composable () -> Unit = {
                    Column(Modifier.fillMaxSize()) {
                        AppThemeAdaptiveTabRow(
                            role = com.android.purebilibili.core.ui.components.AppTabRowRole.SECONDARY,
                            options = listOf(AppSegmentOption(0, "关键词"), AppSegmentOption(1, "用户")),
                            selectedValue = pager.currentPage,
                            onSelectionChange = { scope.launch { com.android.purebilibili.navigation.animatePagerSelection(pager, it) } },
                            indicatorPositionProvider = { pager.currentPage + pager.currentPageOffsetFraction },
                            isScrollInProgressProvider = { pager.isScrollInProgress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        com.android.purebilibili.core.ui.components.AppHorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
                            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
                                if (page == 0) LiveKeywordSection(shieldInfo, isLoggedIn) { pendingKeyword = it }
                                else LiveShieldUserSection(shieldInfo, isLoggedIn) { pendingUser = it }
                            }
                        }
                    }
                }
                if (landscape) Row(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) { rules() }
                    Box(Modifier.weight(1f)) { lists() }
                } else Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().heightIn(max = 260.dp)) { rules() }
                    Box(Modifier.weight(1f)) { lists() }
                }
            }
        }
    }
    if (showAdd) AppAlertDialog(
        onDismissRequest = { showAdd = false },
        title = { AppText(if (pager.currentPage == 0) "添加屏蔽词" else "屏蔽用户") },
        text = {
            AppOutlinedTextField(
                value = value,
                onValueChange = { value = if (pager.currentPage == 0) it.take(20) else it.filter(Char::isDigit).take(20) },
                singleLine = true,
                placeholder = { AppText(if (pager.currentPage == 0) "关键词" else "用户 UID") },
            )
        },
        confirmButton = {
            AppTextButton(
                enabled = if (pager.currentPage == 0) value.trim().isNotBlank() else (value.toLongOrNull() ?: 0L) > 0L,
                onClick = {
                    if (pager.currentPage == 0) onAddKeyword(value.trim()) else value.toLongOrNull()?.let(onAddUser)
                    showAdd = false
                },
            ) { AppText("确定") }
        },
        dismissButton = { AppTextButton(onClick = { showAdd = false }) { AppText("取消") } },
    )
    if (pendingKeyword != null || pendingUser != null) AppAlertDialog(
        onDismissRequest = { pendingKeyword = null; pendingUser = null },
        title = { AppText("确定删除该规则？") },
        text = { AppText(pendingKeyword ?: pendingUser?.uname.orEmpty()) },
        confirmButton = {
            AppTextButton(onClick = {
                pendingKeyword?.let(onDeleteKeyword)
                pendingUser?.let(onUnblockUser)
                pendingKeyword = null
                pendingUser = null
            }) { AppText("删除") }
        },
        dismissButton = { AppTextButton(onClick = { pendingKeyword = null; pendingUser = null }) { AppText("取消") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LiveRuleSection(
    shieldInfo: LiveShieldInfo?,
    enabled: Boolean,
    onSetRule: (String, Int) -> Unit
) {
    var level by remember(shieldInfo?.level) { androidx.compose.runtime.mutableFloatStateOf((shieldInfo?.level ?: 0).toFloat()) }
    val active = (shieldInfo?.level ?: 0) > 0 || (shieldInfo?.rank ?: 0) > 0 || (shieldInfo?.verify ?: 0) > 0
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small)) {
        AppText("全局屏蔽", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppText(if (active) "屏蔽已开启" else "屏蔽未开启", modifier = Modifier.weight(1f))
            com.android.purebilibili.core.ui.components.AppSwitch(
                checked = active,
                enabled = enabled,
                onCheckedChange = { on ->
                    if (on) { onSetRule("rank", 1); onSetRule("verify", 1) }
                    else { onSetRule("level", 0); onSetRule("rank", 0); onSetRule("verify", 0) }
                },
            )
        }
        AppText("用户等级：${level.toInt()} 以下")
        com.android.purebilibili.core.ui.components.AppSlider(
            value = level,
            onValueChange = { level = it },
            valueRange = 0f..60f,
            steps = 59,
            enabled = enabled,
            onValueChangeFinished = { if (level.toInt() != shieldInfo?.level) onSetRule("level", level.toInt()) },
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small)) {
            AppFilterChip(
                selected = (shieldInfo?.rank ?: 0) > 0,
                enabled = enabled,
                onClick = { onSetRule("rank", if ((shieldInfo?.rank ?: 0) > 0) 0 else 1) },
                label = { AppText("非正式会员") },
            )
            AppFilterChip(
                selected = (shieldInfo?.verify ?: 0) > 0,
                enabled = enabled,
                onClick = { onSetRule("verify", if ((shieldInfo?.verify ?: 0) > 0) 0 else 1) },
                label = { AppText("未绑定手机用户") },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LiveKeywordSection(
    shieldInfo: LiveShieldInfo?,
    enabled: Boolean,
    onDeleteKeyword: (String) -> Unit
) {
    val clearIcon = rememberAppClearIcon()
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small)) {
        AppText("关键词", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        val keywords = shieldInfo?.keywords.orEmpty()
        if (keywords.isEmpty()) {
            AppText("暂无屏蔽词", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small),
                verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small),
                modifier = Modifier.fillMaxWidth()
            ) {
                keywords.forEach { item ->
                    AppInputChip(
                        selected = false,
                        onClick = {},
                        label = {
                            AppText(
                                text = item.keyword,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        trailingIcon = {
                            AppIconButton(
                                onClick = { if (enabled) onDeleteKeyword(item.keyword) },
                                enabled = enabled,
                                modifier = Modifier.size(24.dp)
                            ) {
                                AppIcon(
                                    clearIcon,
                                    contentDescription = "删除屏蔽词",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveShieldUserSection(
    shieldInfo: LiveShieldInfo?,
    enabled: Boolean,
    onUnblockUser: (LiveShieldUser) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small)) {
        AppText("用户", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        val users = shieldInfo?.users.orEmpty()
        if (users.isEmpty()) {
            AppText("暂无屏蔽用户", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small),
                modifier = Modifier.fillMaxWidth()
            ) {
                users.forEach { user ->
                    AppSurface(
                        color = AppSurfaceTokens.cardContainer(),
                        shape = AppShapes.container(ContainerLevel.Chip),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = AppSpacingTokens.Medium,
                                    vertical = AppSpacingTokens.Small
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIcon(
                                Icons.Outlined.Block,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            AppText(
                                text = user.uname.ifBlank { user.uid.toString() },
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = AppSpacingTokens.Medium)
                            )
                            AppTextButton(enabled = enabled, onClick = { onUnblockUser(user) }) {
                                AppText("解除")
                            }
                        }
                    }
                }
            }
        }
    }
}
