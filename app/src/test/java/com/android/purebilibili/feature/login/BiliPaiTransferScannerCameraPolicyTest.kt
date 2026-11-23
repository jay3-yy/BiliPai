package com.android.purebilibili.feature.login

import androidx.camera.core.CameraSelector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BiliPaiTransferScannerCameraPolicyTest {

    @Test
    fun prefersBackCameraWhenAvailable() {
        assertEquals(
            CameraSelector.DEFAULT_BACK_CAMERA,
            resolvePreferredScannerCameraSelector(hasBackCamera = true, hasFrontCamera = true),
        )
        assertEquals(
            CameraSelector.DEFAULT_BACK_CAMERA,
            resolvePreferredScannerCameraSelector(hasBackCamera = true, hasFrontCamera = false),
        )
    }

    @Test
    fun fallsBackToFrontCameraWhenOnlyFrontExists() {
        assertEquals(
            CameraSelector.DEFAULT_FRONT_CAMERA,
            resolvePreferredScannerCameraSelector(hasBackCamera = false, hasFrontCamera = true),
        )
    }

    @Test
    fun returnsNullWhenDeviceHasNoCamera() {
        assertNull(
            resolvePreferredScannerCameraSelector(hasBackCamera = false, hasFrontCamera = false),
        )
    }
}
