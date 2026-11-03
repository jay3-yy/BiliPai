@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.android.purebilibili.feature.bangumi.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.android.purebilibili.core.ui.common.verticalPriorityHorizontalPagerSwipe
import com.android.purebilibili.feature.video.screen.rememberVideoContentTabSelectionAnimationSpec
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.ui.LocalNavigationBackHandler
import com.android.purebilibili.core.ui.components.*
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.data.model.response.BangumiDetail
import com.android.purebilibili.feature.bangumi.BangumiReviewScreen
import com.android.purebilibili.navigation.animatePagerSelection
import com.android.purebilibili.navigation.tabSelectedPage
import com.android.purebilibili.navigation.tabIndicatorPosition
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

/** 在当前内容栏展开详情，播放器保持原位；手机与大屏复用相同内容。 */
@Composable
internal fun BangumiIntroductionPanel(
    detail: BangumiDetail,
    onDismiss: () -> Unit,
    onOpenWeb: (String, String) -> Unit,
    onOpenArticle: (Long, String) -> Unit,
    onTagClick: (String) -> Unit,
    onUserClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()
    val pagerDragged by pagerState.interactionSource.collectIsDraggedAsState()
    val customPagerInteractions = remember(pagerState) { MutableInteractionSource() }
    val customPagerDragged by customPagerInteractions.collectIsDraggedAsState()
    val tabAnimationSpec = rememberVideoContentTabSelectionAnimationSpec()
    val reduceMotion = com.android.purebilibili.core.ui.motion.rememberSystemReduceMotion()
    val listState = rememberLazyListState()
    val backdrop = rememberLayerBackdrop()
    val glass = LocalAppThemeConfig.current.liquidGlassEnabled
    LocalNavigationBackHandler(enabled = true, onBackCompleted = onDismiss)
    val headerHeightPx = remember { mutableIntStateOf(0) }
    val header: @Composable (Modifier) -> Unit = { headerModifier ->
        Row(
            modifier = headerModifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppThemeAdaptiveTabRow(
                options = listOf(AppSegmentOption(0, "详情"), AppSegmentOption(1, "点评")),
                selectedValue = pagerState.tabSelectedPage,
                onSelectionChange = { page ->
                    scope.launch {
                        if (reduceMotion) pagerState.scrollToPage(page) else animatePagerSelection(pagerState, page, tabAnimationSpec)
                    }
                },
                indicatorPositionProvider = { pagerState.tabIndicatorPosition },
                isScrollInProgressProvider = { pagerState.isScrollInProgress },
                isPagerDraggingProvider = { pagerDragged || customPagerDragged },
                pagerTargetPositionProvider = { pagerState.targetPage.toFloat() },
                role = AppTabRowRole.SECONDARY,
                modifier = Modifier.weight(1f),
                centerContent = false,
                compactMiuixWhenTwoOptions = false,
                liquidGlassAlignShellStart = true,
                scrollable = true,
                // 玻璃几何遵守动态长度设置；原生标签保留各自尺寸。
                liquidGlassAdaptIndicatorToLabel = !glass,
                miuixBackdrop = if (glass) backdrop else null,
            )
            AppIconButton(onClick = onDismiss) {
                AppIcon(Icons.Rounded.Close, contentDescription = "关闭")
            }
        }
    }
    Box(modifier = modifier.background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier.fillMaxSize()
                // 采样覆盖标签坐标；玻璃标签作为兄弟覆盖层，避免录入自身。
                .then(if (glass) Modifier.layerBackdrop(backdrop) else Modifier)
                .background(MaterialTheme.colorScheme.background),
        ) {
            if (glass) {
                Spacer(Modifier.layout { _, constraints ->
                    layout(constraints.maxWidth, headerHeightPx.intValue.coerceIn(constraints.minHeight, constraints.maxHeight)) {}
                })
            } else {
                header(Modifier)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                AppHorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize().verticalPriorityHorizontalPagerSwipe(
                        state = pagerState,
                        enabled = glass,
                        forceVerticalPriority = true,
                        dragInteractionSource = customPagerInteractions,
                    ),
                    userScrollEnabled = !glass,
                    flingBehavior = if (glass) androidx.compose.foundation.pager.PagerDefaults.flingBehavior(
                        pagerState, snapAnimationSpec = tabAnimationSpec,
                    ) else null,
                    beyondViewportPageCount = 1,
                ) { page ->
                    when (page) {
                        0 -> AppLazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            item {
                                SelectionContainer {
                                    AppText(detail.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                            detail.stat?.let { stat ->
                                item {
                                    AppText(
                                        "${FormatUtils.formatStat(stat.views)}播放 · ${FormatUtils.formatStat(stat.danmakus)}弹幕",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AppContentColors.secondaryText(),
                                    )
                                }
                            }
                            item {
                                AppText(
                                    listOf(
                                        detail.areas.orEmpty().joinToString(" ") { it.name },
                                        detail.publish?.pubTimeShow.orEmpty().ifBlank { detail.publish?.pubTime.orEmpty() },
                                        detail.newEp?.desc.orEmpty(),
                                    ).filter(String::isNotBlank).joinToString("  "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppContentColors.secondaryText(),
                                )
                            }
                            listOf("简介" to detail.evaluate, "演职人员" to detail.actors, "制作人员" to detail.staff)
                                .filter { it.second.isNotBlank() }
                                .forEach { (title, content) ->
                                    item {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            AppText("$title：", modifier = Modifier.padding(top = 14.dp), style = MaterialTheme.typography.titleMedium)
                                            SelectionContainer { AppText(content, color = AppContentColors.secondaryText()) }
                                        }
                                    }
                                }
                            if (!detail.styles.isNullOrEmpty()) {
                                item {
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        detail.styles.orEmpty().forEach { tag ->
                                            AppTextButton(onClick = { onTagClick(tag) }) { AppText(tag) }
                                        }
                                    }
                                }
                            }
                            item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
                        }
                        1 -> if (detail.mediaId > 0L) {
                            key(detail.mediaId) {
                                BangumiReviewScreen(
                                    mediaId = detail.mediaId,
                                    title = detail.title,
                                    onBack = onDismiss,
                                    onOpenWeb = onOpenWeb,
                                    onOpenArticle = onOpenArticle,
                                    onUserClick = onUserClick,
                                    embedded = true,
                                    contentActive = !pagerState.isScrollInProgress && pagerState.settledPage == 1,
                                )
                            }
                        } else {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                AppText("当前内容暂无点评", color = AppContentColors.secondaryText())
                            }
                        }
                    }
                }
            }
        }
        if (glass) {
            header(Modifier.align(Alignment.TopStart).onSizeChanged { headerHeightPx.intValue = it.height })
        }
    }
}
