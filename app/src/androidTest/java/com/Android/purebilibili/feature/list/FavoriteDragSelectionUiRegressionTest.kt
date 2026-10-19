package com.android.purebilibili.feature.list

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.purebilibili.core.ui.adaptive.MotionTier
import com.android.purebilibili.data.model.response.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FavoriteDragSelectionUiRegressionTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var grid: LazyGridState
    private val selected = mutableStateOf(emptySet<String>())
    private var played: String? = null
    private var origin = Offset.Zero
    private var downTime = 0L
    private var finger: Offset? = null

    private fun show(columns: Int) {
        val items = (1L..60L).map { VideoItem(id = it, aid = it, bvid = "BV$it", title = "收藏视频$it") }
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                MaterialTheme {
                    grid = rememberLazyGridState()
                    Box(Modifier.size(360.dp, 640.dp).onGloballyPositioned {
                        origin = it.localToScreen(Offset.Zero)
                    }) {
                        CommonListContent(
                            items = items,
                            allowAutomaticLoadMore = false,
                            isLoading = false,
                            error = null,
                            searchQuery = "",
                            columns = columns,
                            spacing = 12.dp,
                            padding = PaddingValues(0.dp),
                            cardAnimationEnabled = false,
                            cardTransitionEnabled = false,
                            cardMotionTier = MotionTier.Reduced,
                            showOnlineCount = false,
                            videoCardAppearance = CommonListVideoCardAppearance(false, false, false, false),
                            onVideoClick = { bvid, _, _, _ -> played = bvid },
                            onLoadMore = {},
                            onUnfavorite = null,
                            historyBatchMode = selected.value.isNotEmpty(),
                            historySelectedKeys = selected.value,
                            onHistoryToggleSelect = { key ->
                                selected.value = if (key in selected.value) selected.value - key else selected.value + key
                            },
                            onFavoriteDragSelectionChange = { selected.value = it },
                            gridState = grid,
                        )
                    }
                }
            }
        }
    }

    private fun point(index: Int): Offset {
        val item = grid.layoutInfo.visibleItemsInfo.single { it.index == index }
        return Offset(
            item.offset.x + item.size.width / 2f,
            (item.offset.y + item.size.height / 2f).coerceIn(70f, grid.layoutInfo.viewportSize.height - 70f),
        )
    }

    // 边缘自动滚动期间界面本就不会空闲，不能在抬手前经 Espresso 等待 idle。
    // 注入真实的 Android 触摸事件，保持同一 downTime 和同一根手指贯穿整个选择手势。
    private fun touch(action: Int, point: Offset) {
        if (action == MotionEvent.ACTION_DOWN) downTime = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(
            downTime, SystemClock.uptimeMillis(), action, origin.x + point.x, origin.y + point.y, 0,
        )
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        try {
            InstrumentationRegistry.getInstrumentation().sendPointerSync(event)
        } finally {
            event.recycle()
        }
    }

    private fun startHolding(point: Offset) {
        finger = point
        touch(MotionEvent.ACTION_DOWN, point)
        rule.waitUntil(5_000) { "BV1" in selected.value }
    }

    private fun move(point: Offset) {
        finger = point
        touch(MotionEvent.ACTION_MOVE, point)
    }

    private fun release() {
        finger?.let { touch(MotionEvent.ACTION_UP, it) }
        finger = null
        rule.waitForIdle()
    }

    @Test fun singleColumnLongPressSelectsAndSameFingerDragReversesTheRange() {
        show(1)
        val anchor = point(0)
        val target = point(1)
        try {
            startHolding(anchor)
            assertEquals(setOf("BV1"), selected.value)
            move(target)
            rule.waitUntil(5_000) { selected.value == setOf("BV1", "BV2") }
            move(anchor)
            rule.waitUntil(5_000) { selected.value == setOf("BV1") }
        } finally {
            release()
        }
        rule.runOnIdle { assertEquals(null, played) }
    }

    @Test fun doubleColumnDragIncludesTheInterveningVideoWithoutReleasing() {
        show(2)
        val anchor = point(0)
        val target = point(3)
        try {
            startHolding(anchor)
            move(target)
            rule.waitUntil(5_000) { selected.value == setOf("BV1", "BV2", "BV3", "BV4") }
        } finally {
            release()
        }
        rule.runOnIdle { assertEquals(null, played) }
    }

    @Test fun holdingAtBottomAndTopEdgesScrollsAndExtendsSelection() {
        show(2)
        try {
            startHolding(point(0))
            move(Offset(280f, grid.layoutInfo.viewportSize.height - 2f))
            rule.waitUntil(5_000) { grid.firstVisibleItemIndex >= 6 }
            val lastIndex = grid.firstVisibleItemIndex
            assertTrue(selected.value.containsAll((1..lastIndex).map { "BV$it" }))
            move(Offset(80f, 2f))
            rule.waitUntil(5_000) { grid.firstVisibleItemIndex < lastIndex }
        } finally {
            release()
        }
    }

    @Test fun ordinarySwipeStillScrollsWithoutStartingSelection() {
        show(1)
        rule.onNode(hasScrollToIndexAction()).performTouchInput { swipeUp(durationMillis = 600) }
        rule.runOnIdle {
            assertTrue(grid.firstVisibleItemIndex > 0 || grid.firstVisibleItemScrollOffset > 0)
            assertEquals(emptySet<String>(), selected.value)
            assertEquals(null, played)
        }
    }
}
