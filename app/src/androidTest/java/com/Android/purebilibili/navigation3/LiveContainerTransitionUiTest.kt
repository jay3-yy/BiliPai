package com.android.purebilibili.navigation3

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.android.purebilibili.feature.settings.SettingsContainerAnchor
import com.android.purebilibili.feature.settings.SettingsContainerSource
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.nav.runtime.NavChange
import top.yukonga.miuix.kmp.nav.transition.NavGesture
import top.yukonga.miuix.kmp.nav.transition.NavRole
import top.yukonga.miuix.kmp.nav.transition.NavTransitionScope

class LiveContainerTransitionUiTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun depthUpdatesDoNotRecomposeTheEntryOrRedrawUnchangedLiveContent() {
        val depth = mutableFloatStateOf(0.2f)
        val draws = AtomicInteger()
        val commits = AtomicInteger()
        val scope = object : NavTransitionScope {
            override val relativeDepth get() = depth.floatValue
            override val role = NavRole.Covered
            override val change = NavChange.Push
            override val gesture: NavGesture? = null
            override val layoutSize = IntSize(200, 200)
            override val layoutDirection = LayoutDirection.Ltr
            override val density = Density(1f)
        }
        rule.setContent {
            val layer = rememberGraphicsLayer()
            val transition = remember(layer) {
                SettingsContainerTransition(
                    source = SettingsContainerSource(
                        SettingsContainerAnchor(), layer, BiliPaiNavKey.LiveList,
                        Rect(0f, 0f, 40f, 40f), IntSize(200, 200), Offset.Zero,
                        Color.Black, 0f, 0f,
                    ),
                    pageColor = { Color.Black },
                )
            }
            val modifier = with(transition) { Modifier.transformEntry(scope) }
            SideEffect { commits.incrementAndGet() }
            Box(Modifier.size(80.dp).then(modifier).drawWithContent {
                draws.incrementAndGet()
                drawRect(Color.Red)
            })
        }
        rule.waitForIdle()
        val initialCommits = commits.get()
        rule.runOnIdle { draws.set(0) }
        for (value in listOf(0.4f, 0.8f, 1f, 0.6f, 0.1f)) {
            rule.runOnIdle { depth.floatValue = value }
            rule.waitForIdle()
            rule.runOnIdle {
                assertEquals(initialCommits, commits.get())
                assertEquals(0, draws.get())
            }
        }
    }
}
