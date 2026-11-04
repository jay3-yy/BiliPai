package com.android.purebilibili.feature.video.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.ui.AppThemeConfig
import com.android.purebilibili.core.ui.LocalNavigationBackHandler
import com.android.purebilibili.core.ui.ProvideAppThemeConfig
import com.android.purebilibili.core.ui.popup.AppGlassPopupHost
import com.android.purebilibili.feature.home.components.resolveLiquidGlassTuning
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Real window dismissal, including a page dispatcher below the comment overlay. */
@RunWith(AndroidJUnit4::class)
class CommentOverlayBackUiRegressionTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun materialCommentMenuConsumesOnlyFirstBack() = verifyBack(AppUiStyle.MATERIAL3, search = false)
    @Test fun miuixCommentMenuConsumesOnlyFirstBack() = verifyBack(AppUiStyle.MIUIX, search = false)
    @Test fun materialCommentSearchConsumesOnlyFirstBack() = verifyBack(AppUiStyle.MATERIAL3, search = true)
    @Test fun miuixCommentSearchConsumesOnlyFirstBack() = verifyBack(AppUiStyle.MIUIX, search = true)
    @Test fun materialGlassCommentMenuConsumesOnlyFirstBack() = verifyBack(AppUiStyle.MATERIAL3, search = false, glass = true)
    @Test fun miuixGlassCommentMenuConsumesOnlyFirstBack() = verifyBack(AppUiStyle.MIUIX, search = false, glass = true)
    @Test fun materialGlassCommentSearchConsumesOnlyFirstBack() = verifyBack(AppUiStyle.MATERIAL3, search = true, glass = true)
    @Test fun miuixGlassCommentSearchConsumesOnlyFirstBack() = verifyBack(AppUiStyle.MIUIX, search = true, glass = true)

    private fun verifyBack(style: AppUiStyle, search: Boolean, glass: Boolean = false) {
        val visible = mutableStateOf(false)
        var pageBacks = 0
        var pageClicks = 0
        var dismissals = 0
        val dismiss = { visible.value = false; dismissals++; Unit }
        rule.setContent {
            CompositionLocalProvider(LocalAppUiStyle provides style) {
                ProvideAppThemeConfig(AppThemeConfig(liquidGlassEnabled = glass)) {
                    MiuixTheme {
                        MaterialTheme {
                            AppGlassPopupHost(glassAvailable = glass, tuning = resolveLiquidGlassTuning(progress = 0.5f)) {
                                val pageOwner = rememberNavigationEventDispatcherOwner(
                                    parent = LocalNavigationEventDispatcherOwner.current,
                                )
                                CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides pageOwner) {
                                    LocalNavigationBackHandler(enabled = true) { pageBacks++ }
                                    Column {
                                        Button(onClick = { visible.value = true }) { Text("打开评论弹层") }
                                        Button(onClick = { pageClicks++ }) { Text("详情页操作") }
                                    }
                                    if (visible.value) {
                                        if (search) {
                                            CommentSearchSheet(
                                                replies = emptyList(),
                                                onCommentClick = {},
                                                onDismiss = dismiss,
                                            )
                                        } else {
                                            ReplyActionSheet(
                                                canCheckFraud = false,
                                                canReport = false,
                                                onDismiss = dismiss,
                                                onCopyAll = {},
                                                onFreeCopy = {},
                                                onSave = {},
                                                onReply = {},
                                                onReport = {},
                                                onToggleTop = {},
                                                anchorBounds = Rect(80f, 80f, 81f, 81f),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        rule.onNodeWithText("打开评论弹层").performClick()
        val overlayLabel = if (search) "搜索评论" else "复制全部"
        rule.onNodeWithText(overlayLabel).assertIsDisplayed()

        pressBack()
        rule.waitUntil(timeoutMillis = 5_000) { !visible.value }
        rule.waitForIdle()
        rule.onNodeWithText(overlayLabel).assertDoesNotExist()
        rule.runOnIdle {
            assertEquals(1, dismissals)
            assertEquals(0, pageBacks)
        }
        // An orphaned sheet/menu scrim would intercept this click.
        rule.onNodeWithText("详情页操作").performClick()
        rule.runOnIdle { assertEquals(1, pageClicks) }

        pressBack()
        rule.runOnIdle { assertEquals(1, pageBacks) }

        // Reopening must install a fresh top-level back handler, not retain the old window.
        rule.onNodeWithText("打开评论弹层").performClick()
        rule.onNodeWithText(overlayLabel).assertIsDisplayed()
        pressBack()
        rule.waitUntil(timeoutMillis = 5_000) { !visible.value }
        rule.runOnIdle {
            assertEquals(2, dismissals)
            assertEquals(1, pageBacks)
        }
    }
}
