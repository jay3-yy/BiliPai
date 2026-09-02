package com.example.hdr_player

import android.content.Context
import android.hardware.display.DisplayManager
import android.opengl.EGL14
import android.os.Build
import android.view.Display

object HdrSupportChecker {
    fun check(context: Context): Map<String, Any> {
        val hdrTypes = mutableListOf<Int>()
        var displaySupported = false
        var reason = ""

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
                ?: displayManager.displays.firstOrNull()
            val types = display?.hdrCapabilities?.supportedHdrTypes
            if (types != null) {
                hdrTypes.addAll(types.toList())
                displaySupported = types.contains(Display.HdrCapabilities.HDR_TYPE_HDR10)
            }
        } else {
            reason = "api_too_low"
        }

        var eglSupported = false
        if (displaySupported) {
            val eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                val version = IntArray(2)
                if (EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
                    val extensions = EGL14.eglQueryString(
                        eglDisplay,
                        EGL14.EGL_EXTENSIONS
                    ) ?: ""
                    eglSupported = extensions.contains("EGL_EXT_gl_colorspace_bt2020_pq") ||
                        extensions.contains("EGL_KHR_gl_colorspace") ||
                        extensions.contains("EGL_EXT_gl_colorspace_bt2020_linear")
                    EGL14.eglTerminate(eglDisplay)
                }
            }
            if (!eglSupported) {
                reason = "egl_colorspace_extension_missing"
            }
        } else if (reason.isEmpty()) {
            reason = "display_hdr10_not_supported"
        }

        return mapOf(
            "displaySupported" to displaySupported,
            "eglSupported" to eglSupported,
            "reason" to reason,
            "hdrTypes" to hdrTypes
        )
    }
}
