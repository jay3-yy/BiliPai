package com.android.purebilibili.feature.anime4k.gl

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/** 复用的全屏四边形，坐标与纹理坐标均为 GL_TRIANGLE_STRIP。 */
internal object QuadRenderUtils {
    private val vertices: FloatBuffer = ByteBuffer
        .allocateDirect(4 * 4 * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(
                floatArrayOf(
                    -1f, -1f, 0f, 0f,
                    1f, -1f, 1f, 0f,
                    -1f, 1f, 0f, 1f,
                    1f, 1f, 1f, 1f
                )
            )
            position(0)
        }

    fun draw(program: Int) {
        draw(
            positionHandle = GLES30.glGetAttribLocation(program, "aPosition"),
            texCoordHandle = GLES30.glGetAttribLocation(program, "aTexCoord"),
        )
    }

    fun draw(positionHandle: Int, texCoordHandle: Int) {
        vertices.position(0)
        GLES30.glVertexAttribPointer(positionHandle, 2, GLES30.GL_FLOAT, false, 16, vertices)
        GLES30.glEnableVertexAttribArray(positionHandle)
        vertices.position(2)
        GLES30.glVertexAttribPointer(texCoordHandle, 2, GLES30.GL_FLOAT, false, 16, vertices)
        GLES30.glEnableVertexAttribArray(texCoordHandle)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glDisableVertexAttribArray(positionHandle)
        GLES30.glDisableVertexAttribArray(texCoordHandle)
    }
}
