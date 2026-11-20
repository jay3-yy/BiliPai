package com.android.purebilibili.navigation3

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.transition.LocalVideoCardSharedElementSourceRoute
import com.android.purebilibili.core.ui.transition.LocalVideoCardSourceTrackingEnabled
import com.android.purebilibili.core.ui.transition.VideoCardSourceAnchor
import com.android.purebilibili.core.ui.transition.VideoCardSourceInstances
import com.android.purebilibili.core.util.CardPositionManager
import com.android.purebilibili.feature.settings.LocalSettingsContainerOwner
import com.android.purebilibili.feature.settings.SettingsSharedContainerSources
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test

class NavigationSharedContainerSourceUiTest {
    @get:Rule val rule = createComposeRule()

    @After
    fun clearSource() {
        rule.runOnIdle { CardPositionManager.clear() }
    }

    @Test
    fun clickStagesSourceWithoutChoosingSharedFallbackAndDetachedSourceCannotBeReused() {
        val sources = SettingsSharedContainerSources()
        val mounted = mutableStateOf(true)
        var anchor: VideoCardSourceAnchor? = null
        rule.setContent {
            CompositionLocalProvider(
                LocalNavigationSharedContainerSources provides sources,
                LocalSettingsContainerOwner provides BiliPaiNavKey.MainHost,
                LocalVideoCardSharedElementSourceRoute provides "home",
                LocalVideoCardSourceTrackingEnabled provides true,
            ) {
                Box(Modifier.fillMaxSize().onGloballyPositioned {
                    sources.hostOrigin = it.positionInRoot()
                    sources.hostSize = it.size
                }) {
                    if (mounted.value) {
                        val source = requireNotNull(rememberSystemWindowControlSource(BiliPaiNavKey.Settings))
                        Box(Modifier.size(48.dp).then(source.anchorModifier(CircleShape))
                            .testTag("settings").clickable {
                                source.record()
                                anchor = VideoCardSourceInstances.takeAnchor(source.identity)
                            })
                    }
                }
            }
        }
        rule.onNodeWithTag("settings").performClick()
        rule.runOnIdle {
            // 系统窗口尚可认领该来源，不能仅因点击就启动应用内共享过渡。
            assertTrue(sources.sources.isEmpty())
            assertTrue(requireNotNull(anchor).prepareSharedFallback(BiliPaiNavKey.Settings))
            assertTrue(sources.sources.getValue(BiliPaiNavKey.Settings).available)
            mounted.value = false
        }
        rule.waitForIdle()
        rule.runOnIdle {
            assertFalse(requireNotNull(anchor).prepareSharedFallback(BiliPaiNavKey.Settings))
            assertFalse(sources.sources.getValue(BiliPaiNavKey.Settings).available)
        }
    }
}
