package com.android.purebilibili.feature.video

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import com.android.purebilibili.core.ui.transition.LocalVideoCardSourceTrackingEnabled
import com.android.purebilibili.core.ui.transition.VideoCardSourceInstances
import com.android.purebilibili.core.ui.transition.videoCardSourceAnchor
import com.android.purebilibili.core.ui.transition.videoCardSourceBoundsInRoot
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class VideoCardSourceAnchorUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun recycledCardCannotTakeOverReturnSessionForSameVideo() {
        var generation by mutableStateOf(0)
        var bounds: Rect? = null
        var originalAnchor: com.android.purebilibili.core.ui.transition.VideoCardSourceAnchor? = null
        compose.setContent {
            CompositionLocalProvider(LocalVideoCardSourceTrackingEnabled provides true) {
                key(generation) {
                    Box(Modifier.size(120.dp, 90.dp)
                        .videoCardSourceAnchor("BV1same", RoundedCornerShape(12.dp))
                        .onPlaced { bounds = it.videoCardSourceBoundsInRoot() })
                }
            }
        }
        compose.runOnIdle {
            VideoCardSourceInstances.recordClick("BV1same", requireNotNull(bounds))
            originalAnchor = VideoCardSourceInstances.takeAnchor("BV1same")
            assertNotNull(originalAnchor?.boundsInRoot())
            generation++
        }
        compose.runOnIdle {
            assertNull(originalAnchor?.boundsInRoot())
            VideoCardSourceInstances.recordClick("BV1same", requireNotNull(bounds))
            assertNotNull(VideoCardSourceInstances.takeAnchor("BV1same")?.boundsInRoot())
            assertNull(originalAnchor?.boundsInRoot())
        }
    }

    @Test
    fun partiallyVisibleCardKeepsItsCompleteSourceAnchor() {
        var bounds: Rect? = null
        compose.setContent {
            CompositionLocalProvider(LocalVideoCardSourceTrackingEnabled provides true) {
                Box(Modifier.size(120.dp, 80.dp).clipToBounds()) {
                    Box(Modifier.offset(y = 40.dp).size(120.dp, 90.dp)
                        .videoCardSourceAnchor("BV1partial", RoundedCornerShape(12.dp))
                        .onGloballyPositioned { bounds = it.videoCardSourceBoundsInRoot() })
                }
            }
        }
        compose.runOnIdle {
            VideoCardSourceInstances.recordClick("BV1partial", requireNotNull(bounds))
            val anchor = requireNotNull(VideoCardSourceInstances.takeAnchor("BV1partial"))
            assertNull(anchor.boundsInRoot())
            org.junit.Assert.assertEquals(bounds, anchor.boundsInRoot(allowPartial = true))
        }
    }

    @Test
    fun disabledTransitionDoesNotRegisterCardSource() {
        var bounds: Rect? = null
        compose.setContent {
            CompositionLocalProvider(LocalVideoCardSourceTrackingEnabled provides false) {
                Column {
                    Box(Modifier.size(120.dp, 90.dp)
                        .videoCardSourceAnchor("BV1disabled", RoundedCornerShape(12.dp))
                        .onPlaced { bounds = it.videoCardSourceBoundsInRoot() })
                }
            }
        }
        compose.runOnIdle {
            VideoCardSourceInstances.recordClick("BV1disabled", requireNotNull(bounds))
            assertNull(VideoCardSourceInstances.takeAnchor("BV1disabled"))
        }
    }
}
