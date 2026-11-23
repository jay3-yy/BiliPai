package com.android.purebilibili.core.ui.adaptive

import android.view.InputDevice
import com.android.purebilibili.core.util.AppDisplayContext
import com.android.purebilibili.core.util.AppWindowAdaptiveInfo
import com.android.purebilibili.core.util.WindowHeightSizeClass
import com.android.purebilibili.core.util.WindowSizeClass
import com.android.purebilibili.core.util.WindowWidthSizeClass
import com.android.purebilibili.core.util.isPrecisePointerSource
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExternalDragSourcePolicyTest {

    @Test
    fun buildsVideoAndDynamicShareLinks() {
        assertEquals(
            "测试视频 https://www.bilibili.com/video/BV1xx411c7mD",
            buildVideoDragShareText(" BV1xx411c7mD ", "  测试\n视频 "),
        )
        assertEquals(
            "https://www.bilibili.com/video/BV1xx411c7mD",
            buildVideoDragShareText("BV1xx411c7mD"),
        )
        assertNull(buildVideoDragShareText("   ", "标题"))

        assertEquals(
            "动态摘要 https://t.bilibili.com/987654321",
            buildDynamicDragShareText(" 987654321 ", "动态\t摘要"),
        )
        assertNull(buildDynamicDragShareText(""))
    }

    @Test
    fun enablesExternalDragSourceOnlyWhenPrecisePointerConnected() {
        val tabletWithoutPointer = AppWindowAdaptiveInfo(
            windowSizeClass = WindowSizeClass(
                widthSizeClass = WindowWidthSizeClass.Expanded,
                heightSizeClass = WindowHeightSizeClass.Medium,
                widthDp = 900.dp,
                heightDp = 700.dp,
            ),
            displayContext = AppDisplayContext(
                currentWindowWidthDp = 900,
                currentWindowHeightDp = 700,
                isInMultiWindowMode = true,
            ),
            precisePointerConnected = false,
        )
        assertFalse(shouldEnableExternalDragSource(tabletWithoutPointer))
        assertTrue(shouldEnableExternalDragSource(tabletWithoutPointer.copy(precisePointerConnected = true)))
    }

    @Test
    fun startsExternalDragOnlyOnHorizontalDominantGesture() {
        val slop = 16f
        // 垂直列表滚动不得误触发跨应用拖出
        assertFalse(shouldStartExternalDrag(dx = 12f, dy = 48f, touchSlop = slop))
        assertFalse(shouldStartExternalDrag(dx = 30f, dy = 30f, touchSlop = slop))
        // 位移不足 1.5 倍 touchSlop 不触发
        assertFalse(shouldStartExternalDrag(dx = 20f, dy = 0f, touchSlop = slop))
        // 水平主导且超过阈值触发
        assertTrue(shouldStartExternalDrag(dx = 30f, dy = 10f, touchSlop = slop))
        assertTrue(shouldStartExternalDrag(dx = -32f, dy = -8f, touchSlop = slop))
    }

    @Test
    fun precisePointerSourceIncludesMouseTouchpadAndStylus() {
        assertTrue(isPrecisePointerSource(InputDevice.SOURCE_MOUSE))
        assertTrue(isPrecisePointerSource(InputDevice.SOURCE_TOUCHPAD))
        assertTrue(isPrecisePointerSource(InputDevice.SOURCE_STYLUS))
        assertTrue(isPrecisePointerSource(InputDevice.SOURCE_BLUETOOTH_STYLUS))
        assertFalse(isPrecisePointerSource(InputDevice.SOURCE_TOUCHSCREEN))
    }
}
