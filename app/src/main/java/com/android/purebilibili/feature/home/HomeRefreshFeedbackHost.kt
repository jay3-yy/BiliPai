package com.android.purebilibili.feature.home

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

/** 提示事件及计时在独立组合范围内处理，不让提示变化重组首页分页与导航。 */
@Composable
internal fun HomeRefreshFeedbackHost(
    viewModel: HomeViewModel,
    snackbarHostState: SnackbarHostState,
    currentCategory: HomeCategory,
    isRefreshing: Boolean,
    homeFeedWorkAllowed: Boolean,
    useRegularHomeGrid: Boolean,
    gridStates: Map<HomeCategory, HomeFeedGridState>,
    easterEggEnabled: Boolean,
    refreshDeltaTipText: MutableState<String?>,
    easterEggDialogVisible: MutableState<Boolean>,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val refreshKey by viewModel.refreshKey.collectAsStateWithLifecycle()
    val refreshMessage by viewModel.refreshMessage.collectAsStateWithLifecycle()
    val refreshNewItemsKey by viewModel.refreshNewItemsKey.collectAsStateWithLifecycle()
    val refreshNewItemsCount by viewModel.refreshNewItemsCount.collectAsStateWithLifecycle()
    val refreshNewItemsHandledKey by viewModel.refreshNewItemsHandledKey.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.feedbackEvents.collect { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
        }
    }
    val lastFilteredCount by com.android.purebilibili.core.plugin.json.JsonPluginManager.lastFilteredCount.collectAsStateWithLifecycle()

    //  当有视频被过滤时显示提示
    LaunchedEffect(lastFilteredCount) {
        if (lastFilteredCount > 0) {
            snackbarHostState.showSnackbar(
                message = " 已过滤 $lastFilteredCount 个视频",
                duration = SnackbarDuration.Short
            )
        }
    }

    var showEasterEggDialog by easterEggDialogVisible

    //  [彩蛋] 下拉刷新成功后显示趣味提示（仅在开关开启时）
    LaunchedEffect(refreshKey, easterEggEnabled) {
        val message = refreshMessage
        if (message != null && refreshKey > 0 && easterEggEnabled) {
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = "关闭彩蛋",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                showEasterEggDialog = true
            }
        }
    }

    LaunchedEffect(refreshNewItemsKey, isRefreshing, currentCategory, homeFeedWorkAllowed, useRegularHomeGrid) {
        if (!homeFeedWorkAllowed) return@LaunchedEffect
        val refreshKey = refreshNewItemsKey
        if (!shouldHandleRefreshNewItemsEvent(refreshKey, refreshNewItemsHandledKey)) {
            return@LaunchedEffect
        }
        val count = refreshNewItemsCount ?: return@LaunchedEffect
        if (count > 0) {
            val targetGridState = when (currentCategory) {
                HomeCategory.RECOMMEND -> gridStates[HomeCategory.RECOMMEND]
                HomeCategory.FOLLOW -> gridStates[HomeCategory.FOLLOW]
                else -> null
            }
            if (targetGridState != null && shouldResetToTopAfterIncrementalRefresh(
                    currentCategory = currentCategory,
                    newItemsCount = count,
                    isRefreshing = isRefreshing,
                    firstVisibleItemIndex = targetGridState.firstVisibleItemIndex,
                    firstVisibleItemScrollOffset = targetGridState.firstVisibleItemScrollOffset
                )
            ) {
                // 等 PullToRefresh 释放手势后再回顶，避免在手势临界点抢占滚动。
                yield()
                targetGridState.scrollToItem(0)
            }
        }
        refreshDeltaTipText.value = if (count > 0) "新增 $count 条内容" else "暂无新内容"
        // The scroll/result has been handled; leaving during the tip must not replay it on return.
        viewModel.markRefreshNewItemsHandled(refreshKey)
        try {
            delay(2200)
        } finally {
            refreshDeltaTipText.value = null
        }
    }

    //  [彩蛋] 关闭确认对话框
    if (showEasterEggDialog) {
        AppAlertDialog(
            onDismissRequest = { showEasterEggDialog = false },
            title = {
                AppText(
                    "关闭趣味提示？",
                    color = AppContentColors.primaryText()
                )
            },
            text = {
                AppText(
                    "关闭后下拉刷新将不再显示趣味消息。\n\n你可以在「设置」中随时重新开启。",
                    color = AppContentColors.secondaryText()
                )
            },
            confirmButton = {
                AppTextButton(
                    onClick = {
                        coroutineScope.launch {
                            SettingsManager.setEasterEggEnabled(context, false)
                        }
                        showEasterEggDialog = false
                    }
                ) { AppText("关闭彩蛋", color = AppContentColors.error()) }
            },
            dismissButton = {
                AppTextButton(
                    onClick = { showEasterEggDialog = false }
                ) { AppText("保留彩蛋") }
            },
            containerColor = AppSurfaceTokens.cardContainer()
        )
    }
}
