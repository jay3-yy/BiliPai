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
import com.android.purebilibili.core.ui.LocalSharedTransitionEnabled
import com.android.purebilibili.core.ui.transition.LocalSystemVideoCardCaptureEnabled
import com.android.purebilibili.core.ui.transition.NativeVideoCardSnapshotController
import com.android.purebilibili.core.ui.transition.LocalVideoCardSourceTrackingEnabled
import com.android.purebilibili.core.ui.transition.VideoCardSourceInstances
import com.android.purebilibili.core.ui.transition.rememberNativeVideoCardSnapshotController
import com.android.purebilibili.core.ui.transition.videoCardSourceAnchor
import com.android.purebilibili.core.ui.transition.videoCardSourceBoundsInRoot
import com.android.purebilibili.core.util.CardPositionManager
import org.junit.Assert.assertEquals
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
    fun partiallyVisibleCardKeepsCompleteSystemSourceWithInAppMorphDisabled() {
        var bounds: Rect? = null
        var captureContinued = false
        lateinit var snapshot: NativeVideoCardSnapshotController
        val bvid = "BV1partial"
        compose.setContent {
            CompositionLocalProvider(
                LocalSharedTransitionEnabled provides false,
                LocalSystemVideoCardCaptureEnabled provides true,
                LocalVideoCardSourceTrackingEnabled provides true,
            ) {
                snapshot = rememberNativeVideoCardSnapshotController(bvid)
                Box(Modifier.size(120.dp, 80.dp).clipToBounds()) {
                    Box(Modifier.offset(y = 40.dp).size(120.dp, 90.dp)
                        .videoCardSourceAnchor(bvid, RoundedCornerShape(12.dp))
                        .then(snapshot.modifier)
                        .onGloballyPositioned { bounds = it.videoCardSourceBoundsInRoot() })
                }
            }
        }
        compose.runOnIdle {
            CardPositionManager.recordVideoCardPosition(
                bvid = bvid,
                sourceRoute = "home",
                bounds = requireNotNull(bounds),
                screenWidth = 360f,
                screenHeight = 640f,
            )
            snapshot.captureThen { captureContinued = true }
        }
        compose.waitUntil(timeoutMillis = 2_000) { captureContinued }
        compose.runOnIdle {
            try {
                val anchor = requireNotNull(VideoCardSourceInstances.takeAnchor(bvid))
                assertNull(anchor.boundsInRoot())
                assertEquals(bounds, anchor.boundsInRoot(allowPartial = true))
                val fullBounds = requireNotNull(anchor.bounds(windowCoordinates = true, allowPartial = true))
                val layer = requireNotNull(CardPositionManager.nativeCardLayerForOwner(
                    CardPositionManager.lastClickedVideoPixelOwner,
                ))
                assertEquals(fullBounds.width(), layer.size.width)
                assertEquals(fullBounds.height(), layer.size.height)
            } finally {
                CardPositionManager.clear()
            }
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
