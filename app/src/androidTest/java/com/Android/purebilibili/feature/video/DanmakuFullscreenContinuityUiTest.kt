package com.android.purebilibili.feature.video

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.purebilibili.danmaku.engine.DanmakuItem
import com.android.purebilibili.danmaku.engine.DanmakuPlaybackState
import com.android.purebilibili.danmaku.engine.DanmakuRenderView
import com.android.purebilibili.danmaku.engine.DanmakuWindow
import com.android.purebilibili.feature.video.danmaku.DanmakuSessionFactory
import com.android.purebilibili.feature.video.danmaku.DanmakuViewport
import com.android.purebilibili.feature.video.ui.section.VideoDanmakuRenderSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DanmakuFullscreenContinuityUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun fullscreenMoveAndTemporaryDeactivationRetainTheLoadedTimeline() {
        val fullscreen = mutableStateOf(false)
        val active = mutableStateOf(true)
        val identity = Any()
        val manager = DanmakuSessionFactory.acquire(
            rule.activity, rule.activity.lifecycleScope, identity,
        )
        val density = rule.activity.resources.displayMetrics.density
        try {
            rule.setContent {
                val content = remember {
                    movableContentOf<Boolean> { expanded ->
                        val width = if (expanded) 320 else 240
                        val height = if (expanded) 240 else 135
                        VideoDanmakuRenderSurface(
                            manager = manager,
                            viewport = DanmakuViewport(
                                (width * density).toInt(), (height * density).toInt(), density,
                            ),
                            useFullscreenTextSize = expanded,
                            renderingPaused = false,
                            modifier = Modifier.size(width.dp, height.dp),
                        )
                    }
                }
                ReusableContentHost(active.value) {
                    Box(Modifier.fillMaxSize()) {
                        if (fullscreen.value) content(true)
                        else Box { content(false) }
                    }
                }
            }
            lateinit var original: DanmakuRenderView
            rule.runOnIdle {
                original = requireNotNull(findRenderer(rule.activity.window.decorView))
                original.engine.replaceWindow(
                    DanmakuWindow(1, listOf(1), listOf(DanmakuItem().apply {
                        text = "保留半屏弹幕时间线"
                        showAtTime = 12_000L
                    })),
                    currentPositionMs = 12_000L,
                )
                original.engine.start(12_000L)
                original.engine.pause()
            }
            repeat(2) {
                for (expanded in listOf(true, false)) {
                    rule.runOnIdle { fullscreen.value = expanded }
                    rule.runOnIdle {
                        assertSame(original, findRenderer(rule.activity.window.decorView))
                        assertEquals(1L, original.engine.diagnostics.replaceWindowCount)
                        assertEquals(1, original.engine.diagnostics.residentItemCount)
                        assertEquals(DanmakuPlaybackState.PAUSED, original.engine.playbackState)
                    }
                }
            }
            rule.runOnIdle { active.value = false }
            rule.runOnIdle {
                assertEquals(DanmakuPlaybackState.PAUSED, original.engine.playbackState)
                active.value = true
            }
            rule.runOnIdle {
                assertSame(original, findRenderer(rule.activity.window.decorView))
                assertEquals(1L, original.engine.diagnostics.replaceWindowCount)
                assertEquals(1, original.engine.diagnostics.residentItemCount)
            }
        } finally {
            rule.activityRule.scenario.close()
            DanmakuSessionFactory.release(identity, manager)
        }
    }

    private fun findRenderer(view: View): DanmakuRenderView? {
        if (view is DanmakuRenderView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            findRenderer(view.getChildAt(index))?.let { return it }
        }
        return null
    }
}
