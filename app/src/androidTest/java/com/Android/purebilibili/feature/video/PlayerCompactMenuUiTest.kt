package com.android.purebilibili.feature.video

import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.window.DialogWindowProvider
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.ui.AppThemeConfig
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.ui.components.AppCompactMenuColumn
import com.android.purebilibili.core.ui.components.AppCompactMenuItem
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppWindowAction
import com.android.purebilibili.core.ui.components.AppWindowActionMenu
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.popup.AppGlassPopupHost
import com.android.purebilibili.feature.home.components.resolveLiquidGlassTuning
import com.android.purebilibili.feature.video.subtitle.rememberPiliSubtitleTools
import com.android.purebilibili.feature.video.ui.components.DanmakuSettingsPanel
import com.android.purebilibili.feature.video.ui.components.VideoSettingsPanel
import com.android.purebilibili.feature.video.ui.overlay.SubtitleControlCallbacks
import com.android.purebilibili.feature.video.ui.overlay.SubtitleControlUiState
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
import androidx.test.platform.app.InstrumentationRegistry
import com.android.purebilibili.feature.video.ui.components.QualitySelectionMenu
import com.android.purebilibili.feature.video.ui.components.PlayerSelectionPopup
import com.android.purebilibili.feature.video.ui.components.PlayerSelectionItem
import com.android.purebilibili.feature.video.ui.components.PlayerListPopupPlacement
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(AndroidJUnit4::class)
class PlayerCompactMenuUiTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun qualityMenu_preservesSelectionAccessHintsAndOriginalOptionIndex() {
        val style = mutableStateOf(AppUiStyle.MATERIAL3)
        val glass = mutableStateOf(false)
        val nativePopup = mutableStateOf(false)
        val visible = mutableStateOf(true)
        var selectedIndex = -1
        composeTestRule.setContent {
            MenuTheme(style.value, glass.value, nativePopup.value) {
                if (visible.value) {
                    QualitySelectionMenu(
                        qualities = listOf("4K", "1080P", "720P"),
                        qualityIds = listOf(120, 80, 64),
                        switchableQualityIds = listOf(80, 64),
                        currentQuality = "1080P",
                        onQualitySelected = { selectedIndex = it; visible.value = false },
                        onDismiss = { visible.value = false },
                    )
                }
            }
        }
        for (uiStyle in AppUiStyle.entries) {
            for (useGlass in listOf(false, true)) {
                for (useNativePopup in listOf(false, true)) {
                    composeTestRule.runOnIdle {
                        visible.value = false
                        style.value = uiStyle
                        glass.value = useGlass
                        nativePopup.value = useNativePopup
                        selectedIndex = -1
                    }
                    composeTestRule.runOnIdle { visible.value = true }
                    composeTestRule.onNodeWithText("需要大会员").assertIsDisplayed()
                    composeTestRule.onNodeWithText("4K").assertIsNotEnabled()
                        .performTouchInput { click() }
                    composeTestRule.onNodeWithText("1080P").assertIsSelected().assertIsNotEnabled()
                    composeTestRule.runOnIdle { assertEquals(-1, selectedIndex) }
                    composeTestRule.onNodeWithText("720P").performClick()
                    composeTestRule.runOnIdle { assertEquals(2, selectedIndex) }
                    composeTestRule.onNodeWithText("720P").assertDoesNotExist()
                }
            }
        }
    }

    @Test
    fun compactMore_preservesSummariesDisabledItemsBackNavigationAndDismissBeforeCallback() {
        val style = mutableStateOf(AppUiStyle.MATERIAL3)
        var expanded = false
        var selected = 0
        composeTestRule.setContent {
            MenuTheme(style.value) {
                AppWindowActionMenu(
                    compact = true,
                    onExpandedChange = { expanded = it },
                    groups = listOf(listOf(
                        AppWindowAction(
                            label = "线路", summary = "按当前网络选择",
                            children = listOf(
                                AppWindowAction(label = "不可用", enabled = false, onClick = { selected = -1 }),
                                AppWindowAction(label = "备用线路", onClick = {
                                    assertFalse(expanded)
                                    selected++
                                }),
                            ),
                        ),
                    )),
                ) { AppText("更多") }
            }
        }
        for (uiStyle in AppUiStyle.entries) {
            composeTestRule.runOnIdle { style.value = uiStyle }
            composeTestRule.onNodeWithText("更多").performClick()
            composeTestRule.onNodeWithText("按当前网络选择").assertIsDisplayed()
            composeTestRule.onNodeWithText("线路").performClick()
            composeTestRule.onNodeWithText("不可用").assertIsNotEnabled()
                .performTouchInput { click() }
            composeTestRule.onNodeWithText("返回").performClick()
            composeTestRule.onNodeWithText("按当前网络选择").assertIsDisplayed()
            composeTestRule.onNodeWithText("线路").performClick()
            composeTestRule.onNodeWithText("备用线路").performClick()
            composeTestRule.onNodeWithText("备用线路").assertDoesNotExist()
        }
        composeTestRule.runOnIdle { assertEquals(AppUiStyle.entries.size, selected) }
    }

    @Test
    fun narrowMenuWithLargeFont_keepsLongSummaryAndLastOptionReachable() {
        var selected = false
        val summary = "这是一段需要换行的音质说明，窄窗口和大字体下仍应完整展示"
        composeTestRule.setContent {
            MenuTheme(AppUiStyle.MATERIAL3) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f)) {
                    Box(Modifier.width(160.dp)) {
                        AppCompactMenuColumn {
                            AppCompactMenuItem(text = "高音质", summary = summary, onClick = {})
                            repeat(20) { index ->
                                AppCompactMenuItem(text = "选项 $index", onClick = { selected = true })
                            }
                        }
                    }
                }
            }
        }
        composeTestRule.onNodeWithText(summary).assertIsDisplayed()
        composeTestRule.onNodeWithText("选项 19").performScrollTo().performClick()
        composeTestRule.runOnIdle { assertEquals(true, selected) }
    }

    @Test
    fun longPopup_canSelectFirstAndLastOptionsAfterOverscrollAcrossPopupHosts() {
        val nativePopup = mutableStateOf(false)
        val visible = mutableStateOf(false)
        val selectedOptions = mutableListOf<Int>()
        composeTestRule.setContent {
            MenuTheme(AppUiStyle.MIUIX, nativePopup = nativePopup.value) {
                if (visible.value) {
                    PlayerSelectionPopup(
                        title = "播放速度",
                        showTitle = false,
                        maxHeight = 96.dp,
                        onDismissRequest = { visible.value = false },
                    ) {
                        repeat(60) { index ->
                            PlayerSelectionItem(
                                text = "速度 $index",
                                onClick = { selectedOptions.add(index) },
                            )
                        }
                    }
                }
            }
        }
        for (useNativePopup in listOf(false, true)) {
            composeTestRule.runOnIdle {
                nativePopup.value = useNativePopup
                selectedOptions.clear()
                visible.value = true
            }
            composeTestRule.onNodeWithText("速度 59").performScrollTo()
                .assertIsDisplayed().performTouchInput { click() }
            composeTestRule.onNodeWithText("速度 0").performScrollTo()
                .performTouchInput { swipeDown(durationMillis = 120) }
            composeTestRule.onNodeWithText("速度 0").assertIsDisplayed()
                .performTouchInput { click() }
            composeTestRule.runOnIdle {
                assertEquals(listOf(59, 0), selectedOptions)
                visible.value = false
            }
        }
    }

    @Test
    fun anchoredPopup_prefersMeasuredButtonAboveWithoutWindowDimAcrossMaterialsAndHosts() {
        val style = mutableStateOf(AppUiStyle.MATERIAL3)
        val glass = mutableStateOf(false)
        val nativePopup = mutableStateOf(false)
        val visible = mutableStateOf(false)
        var anchor = IntRect.Zero
        var popupBounds = Rect.Zero
        var safeBounds = IntRect.Zero
        var popupWindow: Window? = null
        var edgePx = 0
        var gapPx = 0
        composeTestRule.setContent {
            MenuTheme(style.value, glass.value, nativePopup.value) {
                val density = LocalDensity.current
                val direction = LocalLayoutDirection.current
                val windowSize = LocalWindowInfo.current.containerSize
                val insets = WindowInsets.safeDrawing
                SideEffect {
                    edgePx = with(density) { 4.dp.roundToPx() }
                    gapPx = with(density) { 4.dp.roundToPx() }
                    safeBounds = IntRect(
                        insets.getLeft(density, direction), insets.getTop(density),
                        windowSize.width - insets.getRight(density, direction),
                        windowSize.height - insets.getBottom(density),
                    )
                }
                var measuredAnchor by remember { mutableStateOf<IntRect?>(null) }
                Box(Modifier.fillMaxSize()) {
                    AppSurface(
                        onClick = { visible.value = true },
                        modifier = Modifier.align(Alignment.Center).size(48.dp)
                            .onGloballyPositioned {
                                val bounds = it.boundsInWindow()
                                anchor = IntRect(
                                    bounds.left.roundToInt(), bounds.top.roundToInt(),
                                    bounds.right.roundToInt(), bounds.bottom.roundToInt(),
                                )
                                measuredAnchor = anchor
                            },
                    ) { AppText("打开") }
                    if (visible.value) {
                        PlayerSelectionPopup(
                            title = "锚定选项", showTitle = false, maxHeight = 80.dp,
                            maxWidth = 320.dp, placement = PlayerListPopupPlacement.ANCHOR_END,
                            anchorBounds = measuredAnchor,
                            onDismissRequest = { visible.value = false },
                        ) {
                            val view = LocalView.current
                            Box(
                                Modifier.onGloballyPositioned {
                                    val location = IntArray(2)
                                    view.getLocationOnScreen(location)
                                    val local = checkNotNull(it.parentLayoutCoordinates).boundsInWindow()
                                    popupBounds = local.translate(location[0].toFloat(), location[1].toFloat())
                                },
                            )
                            PlayerSelectionItem(
                                text = "较长的清晰度选项名称会在安全窗口内换行而不是越过窗口边缘",
                                onClick = { visible.value = false },
                            )
                        }
                    }
                }
            }
        }
        for (uiStyle in AppUiStyle.entries) {
            for (useGlass in listOf(false, true)) {
                for (useNativePopup in listOf(false, true)) {
                    composeTestRule.runOnIdle {
                        style.value = uiStyle
                        glass.value = useGlass
                        nativePopup.value = useNativePopup
                        popupWindow = null
                        popupBounds = Rect.Zero
                    }
                    composeTestRule.onNodeWithText("打开").performClick()
                    composeTestRule.runOnIdle {
                        assertTrue(popupBounds.height > 0f)
                        assertEquals(anchor.top - gapPx.toFloat(), popupBounds.bottom, 1f)
                        assertTrue(popupBounds.left >= safeBounds.left + edgePx - 1f)
                        assertTrue(popupBounds.right <= safeBounds.right - edgePx + 1f)
                        assertTrue(popupBounds.top >= safeBounds.top + edgePx - 1f)
                        visible.value = false
                    }
                }
            }
        }
    }

    @Test
    fun longQualityMenu_initiallyRevealsSelectedOptionAndKeepsFirstOptionReachable() {
        val nativePopup = mutableStateOf(false)
        val visible = mutableStateOf(false)
        val qualities = List(36) { "画质 $it" }
        var selectedIndex = -1
        composeTestRule.setContent {
            MenuTheme(AppUiStyle.MIUIX, nativePopup = nativePopup.value) {
                if (visible.value) {
                    QualitySelectionMenu(
                        qualities = qualities,
                        qualityIds = List(qualities.size) { 64 },
                        switchableQualityIds = listOf(64),
                        currentQuality = qualities.last(),
                        onQualitySelected = { selectedIndex = it },
                        onDismiss = { visible.value = false },
                    )
                }
            }
        }
        for (useNativePopup in listOf(false, true)) {
            composeTestRule.runOnIdle {
                nativePopup.value = useNativePopup
                selectedIndex = -1
                visible.value = true
            }
            composeTestRule.onNodeWithText(qualities.last()).assertIsDisplayed().assertIsSelected()
            composeTestRule.onNodeWithText(qualities.first()).performScrollTo().performClick()
            composeTestRule.runOnIdle {
                assertEquals(0, selectedIndex)
                visible.value = false
            }
        }
    }

    @Test
    fun anchoredPopup_withoutMeasuredButtonDoesNotUseSyntheticPlacement() {
        composeTestRule.setContent {
            MenuTheme(AppUiStyle.MATERIAL3, nativePopup = false) {
                PlayerSelectionPopup(
                    title = "尚未定位", placement = PlayerListPopupPlacement.ANCHOR_END,
                    onDismissRequest = {},
                ) {
                    PlayerSelectionItem(text = "不应显示", onClick = {})
                }
            }
        }
        composeTestRule.onNodeWithText("不应显示").assertDoesNotExist()
    }

    @Test
    fun playerSettingsHandsOffToSubtitleAndDanmakuPanelsBeforeReopening() {
        val style = mutableStateOf(AppUiStyle.MATERIAL3)
        val glass = mutableStateOf(false)
        val settingsVisible = mutableStateOf(false)
        val danmakuVisible = mutableStateOf(false)
        composeTestRule.setContent {
            MenuTheme(style.value, glass.value) {
                AppGlassPopupHost(
                    glassAvailable = glass.value,
                    tuning = resolveLiquidGlassTuning(0.5f),
                ) {
                    val subtitles = rememberPiliSubtitleTools(
                        bvid = "local-menu", cid = 1L, player = null,
                        nativeControls = SubtitleControlUiState(),
                        nativeCallbacks = SubtitleControlCallbacks(),
                        isFullscreen = true,
                    )
                    AppTextButton(
                        onClick = { settingsVisible.value = true },
                    ) { AppText("打开播放器设置") }
                    if (settingsVisible.value) {
                        VideoSettingsPanel(
                            sleepTimerMinutes = null, onSleepTimerChange = {},
                            isFullscreen = true, onReload = {}, currentQualityLabel = "",
                            onSubtitleSettings = subtitles.onSubtitleSettings,
                            onSaveSubtitle = subtitles.onSaveSubtitle,
                            onDanmakuSettingsClick = { danmakuVisible.value = true },
                            onDismiss = { settingsVisible.value = false },
                        )
                    }
                    if (danmakuVisible.value) {
                        DanmakuSettingsPanel(
                            opacity = 1f, fontScale = 1f, speed = 1f,
                            onOpacityChange = {}, onFontScaleChange = {}, onSpeedChange = {},
                            onDismiss = { danmakuVisible.value = false },
                        )
                    }
                }
            }
        }
        for (uiStyle in AppUiStyle.entries) {
            for (useGlass in listOf(false, true)) {
                composeTestRule.runOnIdle { style.value = uiStyle; glass.value = useGlass }
                composeTestRule.onNodeWithText("打开播放器设置").performClick()
                composeTestRule.onNodeWithText("字幕设置").performScrollTo().performClick()
                composeTestRule.onNodeWithText("字幕样式").assertIsDisplayed()
                assertTrue(InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(GLOBAL_ACTION_BACK))
                composeTestRule.waitUntil(5_000) {
                    composeTestRule.onAllNodesWithText("字幕样式").fetchSemanticsNodes().isEmpty()
                }
                composeTestRule.onNodeWithText("打开播放器设置").performClick()
                composeTestRule.onNodeWithText("保存字幕").performScrollTo().performClick()
                composeTestRule.onNodeWithText("当前视频没有可导出的字幕，请先加载字幕。").assertIsDisplayed()
                composeTestRule.onNodeWithText("关闭").performClick()
                composeTestRule.onNodeWithText("打开播放器设置").performClick()
                composeTestRule.onNodeWithText("弹幕设置").performScrollTo().performClick()
                composeTestRule.onNodeWithText("屏蔽规则").assertIsDisplayed()
                assertTrue(InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(GLOBAL_ACTION_BACK))
                composeTestRule.waitUntil(5_000) {
                    composeTestRule.onAllNodesWithText("屏蔽规则").fetchSemanticsNodes().isEmpty()
                }
            }
        }
    }

    @Composable
    private fun MenuTheme(
        style: AppUiStyle,
        glass: Boolean = false,
        nativePopup: Boolean = true,
        content: @Composable () -> Unit,
    ) {
        CompositionLocalProvider(
            LocalAppUiStyle provides style,
            LocalAppThemeConfig provides AppThemeConfig(
                liquidGlassEnabled = glass, nativeMiuixPopupsEnabled = nativePopup,
            ),
        ) {
            MaterialTheme { MiuixTheme { content() } }
        }
    }
}
