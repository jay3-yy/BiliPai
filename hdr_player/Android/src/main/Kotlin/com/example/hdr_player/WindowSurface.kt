package com.example.hdr_player

import android.opengl.EGLSurface
import android.view.Surface

class WindowSurface {
    private val eglCore: EglCore
    private val eglSurface: EGLSurface

    constructor(eglCore: EglCore, surface: Surface) {
        this.eglCore = eglCore
        this.eglSurface = eglCore.createWindowSurface(surface)
    }

    constructor(eglCore: EglCore, eglSurface: EGLSurface) {
        this.eglCore = eglCore
        this.eglSurface = eglSurface
    }

    fun makeCurrent() {
        eglCore.makeCurrent(eglSurface)
    }

    fun swapBuffers(): Boolean {
        return eglCore.swapBuffers(eglSurface)
    }

    fun setPresentationTime(nsecs: Long) {
        eglCore.setPresentationTime(eglSurface, nsecs)
    }

    fun release() {
        eglCore.releaseSurface(eglSurface)
    }
}

