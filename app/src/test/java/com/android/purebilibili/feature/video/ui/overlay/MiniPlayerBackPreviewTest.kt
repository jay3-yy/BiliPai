package com.android.purebilibili.feature.video.ui.overlay

import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MiniPlayerBackPreviewTest {
    private val detail = MiniPlayerMotionTarget(Rect(0f, 40f, 400f, 265f), 0f)
    private val predictedMini = MiniPlayerMotionTarget(Rect(160f, 510f, 390f, 640f), 16f)

    @Test
    fun cancelledGestureReturnsToOriginalBoundsAndCorners() {
        var progress = 0.7f
        val preview = MiniPlayerBackPreview(predictedMini) { progress }
        val dragged = preview.poseFrom(detail, catchUp = 1f)

        progress = 0.35f
        val restoring = preview.poseFrom(detail, catchUp = 1f)
        assertTrue(restoring.bounds.width > dragged.bounds.width)
        assertTrue(restoring.cornerRadiusPx < dragged.cornerRadiusPx)

        progress = 0f
        assertEquals(detail, preview.poseFrom(detail, catchUp = 1f))
    }

    @Test
    fun commitUsesDisplayedPoseEvenWhenCaptureWasLateAndMiniBoundsChange() {
        var progress = 0.75f
        val preview = MiniPlayerBackPreview(predictedMini) { progress }
        val displayed = preview.poseFrom(detail, catchUp = 0.5f)
        val release = preview.freezeForCommit(detail, catchUp = 0.5f)

        progress = 1f
        val measuredMini = MiniPlayerMotionTarget(Rect(152f, 500f, 392f, 636f), 18f)
        assertEquals(displayed, release)
        assertTrue(preview.releaseFraction < preview.fraction())
        assertEquals(displayed, release.interpolateTo(measuredMini, 0f))
        assertEquals(measuredMini, release.interpolateTo(measuredMini, 1f))
    }
}
