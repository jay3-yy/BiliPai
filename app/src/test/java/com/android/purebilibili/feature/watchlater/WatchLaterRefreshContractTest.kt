package com.android.purebilibili.feature.watchlater

import com.android.purebilibili.core.refresh.WatchLaterRefreshBus
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

class WatchLaterRefreshContractTest {
    @Test
    fun watchLaterRefreshSignal_emitsForActiveCollectors() = runTest {
        val events = Channel<Unit>(capacity = 1)
        val collector = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            WatchLaterRefreshBus.changes.collect {
                events.send(Unit)
                cancel()
            }
        }

        WatchLaterRefreshBus.notifyChanged()

        events.receive()
        collector.cancel()
    }

    @Test
    fun actionRepository_notifiesWatchLaterRefreshAfterSuccessfulToggle() {
        val source = sourceText("src/main/java/com/android/purebilibili/data/repository/ActionRepository.kt")

        assertTrue(
            source.contains("WatchLaterRefreshBus.notifyChanged()"),
            "稍后再看接口成功切换后必须通知列表刷新"
        )
    }

    @Test
    fun watchLaterViewModel_collectsRefreshSignalAndReloadsList() {
        val source = sourceText("src/main/java/com/android/purebilibili/feature/watchlater/WatchLaterScreen.kt")

        assertTrue(
            source.contains("WatchLaterRefreshBus.changes.collect"),
            "稍后再看列表必须订阅刷新信号"
        )
        assertTrue(
            source.contains("loadData()"),
            "收到刷新信号后必须重新加载列表"
        )
    }

    @Test
    fun bottomWatchLaterTab_refreshesWhenPagerPageBecomesActive() {
        val source = sourceText("src/main/java/com/android/purebilibili/navigation/AppNavigation.kt")
        val watchLaterSection = source.substringAfter("BiliPaiNavEntryContentRole.WATCH_LATER")

        assertTrue(
            watchLaterSection.contains("isBottomPagerPageActive"),
            "稍后再看底栏页必须感知 pager 激活状态"
        )
        assertTrue(
            watchLaterSection.contains("watchLaterViewModel.loadData("),
            "稍后再看底栏页激活时必须重新拉取列表"
        )
    }

    @Test
    fun watchLaterScreen_usesDenseListCardsInsteadOfAdaptiveGrid() {
        val source = sourceText("src/main/java/com/android/purebilibili/feature/watchlater/WatchLaterScreen.kt")
        val listBranch = source
            .substringAfter("state.items.isEmpty() ->")
            .substringBefore("if (showBatchDeleteConfirm)")

        assertTrue(
            listBranch.contains("LazyColumn("),
            "稍后再看主列表应使用长条列表卡片，避免窄 dp 设备退成单列大卡"
        )
        assertFalse(
            listBranch.contains("GridCells.Adaptive"),
            "稍后再看主列表不应继续依赖自适应网格"
        )
        assertTrue(
            listBranch.contains("WatchLaterVideoCard("),
            "长条列表必须复用稍后再看专用卡片"
        )
        assertTrue(
            listBranch.contains("selectedBvids"),
            "长条列表必须保留批量选择状态"
        )
        assertTrue(
            listBranch.contains("startVideoDissolve"),
            "长条列表必须保留单项删除入口"
        )
    }

    @Test
    fun watchLaterTopBar_keepsBatchDeleteOutOfTheActionRow() {
        val source = sourceText("src/main/java/com/android/purebilibili/feature/watchlater/WatchLaterScreen.kt")
        val topBarSection = source
            .substringAfter("AppTopBar(")
            .substringBefore("containerColor = MaterialTheme.colorScheme.background")

        // PiliPlus 交互：仅通过卡片长按进入多选，顶栏不再提供批量删除入口
        assertFalse(
            topBarSection.contains("批量删除"),
            "顶栏操作区不应放置批量删除入口"
        )
        assertTrue(
            source.contains("onLongClick") && source.contains("isBatchMode = true"),
            "多选应通过卡片长按进入"
        )
        assertTrue(
            topBarSection.contains("text = { AppText(\"全部听\") }") &&
                topBarSection.contains("onPlayAllAudioClick?.invoke"),
            "全部听应放进管理菜单，避免 MIUIX 顶栏操作区挤压标题"
        )
    }

    private fun sourceText(path: String): String {
        val sourceFile = listOf(
            File(path),
            File("app/$path")
        ).firstOrNull { it.exists() }
        require(sourceFile != null) { "Cannot locate $path from ${File(".").absolutePath}" }
        return sourceFile.readText()
    }
}
