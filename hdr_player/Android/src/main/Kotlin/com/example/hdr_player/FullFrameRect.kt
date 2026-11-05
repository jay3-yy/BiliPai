package com.example.hdr_player

import android.opengl.GLES20

class FullFrameRect {
    private val program = Texture2dProgram()

    fun drawFrame(
        texId: Int,
        texMatrix: FloatArray,
        targetPeakNits: Float,
        strength: Float,
        saturation: Float,
        highlightBoost: Float,
        preDarken: Float,
        highlightProtect: Float,
        useHdr: Boolean,
        frameIndex: Int
    ) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        program.draw(
            texId,
            texMatrix,
            targetPeakNits,
            strength,
            saturation,
            highlightBoost,
            preDarken,
            highlightProtect,
            useHdr,
            frameIndex
        )
    }
}

