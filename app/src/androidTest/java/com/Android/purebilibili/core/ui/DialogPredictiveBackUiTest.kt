package com.android.purebilibili.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.window.DialogProperties
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.findViewTreeNavigationEventDispatcherOwner
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 从真实 Dialog 窗口分发手势，覆盖原生宿主内部返回处理的优先级。 */
@RunWith(Parameterized::class)
class DialogPredictiveBackUiTest(private val style: AppUiStyle) {
    @get:Rule val rule = createComposeRule()

    @Test
    fun cancellationKeepsDraftAndNextGestureDismissesOnceAfterExit() {
        val visible = mutableStateOf(true)
        val draft = mutableStateOf("未保存草稿")
        var dismissals = 0
        lateinit var dispatcher: NavigationEventDispatcher
        rule.setContent {
            CompositionLocalProvider(
                LocalAppUiStyle provides style,
                LocalAppThemeConfig provides AppThemeConfig(liquidGlassEnabled = false),
            ) {
                MaterialTheme { MiuixTheme {
                    if (visible.value) AppAlertDialog(
                        onDismissRequest = { dismissals++; visible.value = false },
                        title = { Text("编辑") },
                        text = {
                            val view = LocalView.current
                            SideEffect {
                                dispatcher = checkNotNull(view.findViewTreeNavigationEventDispatcherOwner())
                                    .navigationEventDispatcher
                            }
                            TextField(value = draft.value, onValueChange = { draft.value = it })
                        },
                        confirmButton = { Text("保存") },
                    )
                } }
            }
        }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        val input = DirectNavigationEventInput()
        rule.runOnIdle {
            dispatcher.addInput(input)
            input.backStarted(NavigationEvent(progress = 0f))
            input.backProgressed(NavigationEvent(progress = 0.8f))
            input.backCancelled()
        }
        rule.mainClock.advanceTimeBy(64)
        rule.onNodeWithText("未保存草稿").assertTextEquals("未保存草稿")
        rule.runOnIdle {
            assertEquals(0, dismissals)
            input.backStarted(NavigationEvent(progress = 0f))
            input.backProgressed(NavigationEvent(progress = 0.6f))
            input.backCompleted()
            assertEquals(0, dismissals)
        }
        rule.mainClock.advanceTimeBy(400)
        rule.runOnIdle { assertEquals(1, dismissals) }
        rule.onNodeWithText("未保存草稿").assertDoesNotExist()
    }

    @Test
    fun dialogThatDisallowsBackConsumesGestureWithoutDismissal() {
        var dismissals = 0
        lateinit var dispatcher: NavigationEventDispatcher
        rule.setContent {
            CompositionLocalProvider(
                LocalAppUiStyle provides style,
                LocalAppThemeConfig provides AppThemeConfig(liquidGlassEnabled = false),
            ) {
                MaterialTheme { MiuixTheme {
                    AppAlertDialog(
                        onDismissRequest = { dismissals++ },
                        properties = DialogProperties(dismissOnBackPress = false),
                        text = {
                            val view = LocalView.current
                            SideEffect {
                                dispatcher = checkNotNull(view.findViewTreeNavigationEventDispatcherOwner())
                                    .navigationEventDispatcher
                            }
                            Text("进行中")
                        },
                    )
                } }
            }
        }
        rule.runOnIdle {
            val input = DirectNavigationEventInput()
            dispatcher.addInput(input)
            input.backStarted(NavigationEvent())
            input.backProgressed(NavigationEvent(progress = 1f))
            input.backCompleted()
            dispatcher.removeInput(input)
        }
        rule.runOnIdle { assertEquals(0, dismissals) }
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun styles() = listOf(arrayOf(AppUiStyle.MATERIAL3), arrayOf(AppUiStyle.MIUIX))
    }
}
