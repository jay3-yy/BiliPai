/** 粒子消散效果的渲染核心。 */
package com.android.purebilibili.core.ui.animation.gl

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.RectF
import android.opengl.GLES30
import android.opengl.GLUtils
import com.android.purebilibili.R
import kotlin.random.Random

/** GLES3 transform-feedback renderer. All methods run on the owning EGL thread. */
internal class ParticleRenderer(
    private val resources: Resources,
    private val bitmap: Bitmap,
    private val bounds: RectF,
    private val density: Float,
    private val maxParticles: Int,
    /** true 时粒子自右向左消失，默认沿用上游的左→右扫描。 */
    private val sweepReversed: Boolean = false,
    private val dissolveOrigin: android.graphics.PointF? = null,
    private val timeScale: Float,
) {
    private var program = 0
    private val buffers = IntArray(2)
    private val texture = IntArray(1)
    private val feedback = IntArray(1)
    private var currentBuffer = 0
    private var firstDraw = true
    private var lastDrawNanos = -1L
    private var time = 0f
    private val seed = Random.nextFloat() * 2f
    private val uniforms = mutableMapOf<String, Int>()
    private lateinit var grid: ThanosParticleGrid
    private val matrix = floatArrayOf(
        bounds.width(), 0f, 0f,
        0f, bounds.height(), 0f,
        bounds.left, bounds.top, 1f,
    )

    fun initialize(width: Int, height: Int) {
        val vertex = compileShader(GLES30.GL_VERTEX_SHADER, R.raw.thanos_vertex)
        val fragment = try {
            compileShader(GLES30.GL_FRAGMENT_SHADER, R.raw.thanos_fragment)
        } catch (error: RuntimeException) {
            GLES30.glDeleteShader(vertex)
            throw error
        }
        try {
            program = GLES30.glCreateProgram()
            check(program != 0) { "Cannot create Thanos program" }
            GLES30.glAttachShader(program, vertex)
            GLES30.glAttachShader(program, fragment)
            // Must be specified before linking, in the exact 2+2+2+1 float buffer order.
            GLES30.glTransformFeedbackVaryings(
                program, arrayOf("outUV", "outPosition", "outVelocity", "outTime"),
                GLES30.GL_INTERLEAVED_ATTRIBS,
            )
            GLES30.glLinkProgram(program)
            val status = IntArray(1)
            GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0)
            check(status[0] == GLES30.GL_TRUE) { GLES30.glGetProgramInfoLog(program) }
        } finally {
            GLES30.glDeleteShader(vertex)
            GLES30.glDeleteShader(fragment)
        }
        listOf(
            "matrix", "rectSize", "reset", "time", "deltaTime", "particlesCount",
            "size", "gridSize", "tex", "seed", "dp", "longevity", "offset", "scale", "uvOffset",
            "sweepReversed", "dissolveOrigin", "useDissolveOrigin",
        ).forEach { uniforms[it] = GLES30.glGetUniformLocation(program, it) }

        grid = resolveThanosParticleGrid(bitmap.width, bitmap.height, density, maxParticles)
        GLES30.glGenBuffers(2, buffers, 0)
        for (buffer in buffers) {
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, buffer)
            GLES30.glBufferData(
                GLES30.GL_ARRAY_BUFFER, grid.count * THANOS_PARTICLE_STRIDE_BYTES,
                null, GLES30.GL_DYNAMIC_DRAW,
            )
        }
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glGenTransformFeedbacks(1, feedback, 0)
        GLES30.glGenTextures(1, texture, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        // Upstream overwrites each texel, whose alpha is already premultiplied by Bitmap.
        // TextureView then composites the transparent full-window layer normally.
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glClearColor(0f, 0f, 0f, 0f)
        resize(width, height)
        checkGlError()
    }

    fun resize(width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        GLES30.glUseProgram(program)
        GLES30.glUniform2f(uniform("size"), width.toFloat(), height.toFloat())
    }

    /** Matches upstream Animation.draw(), including reset, integration, and the tail. */
    fun isInFinalTail(): Boolean = shouldBeginThanosReflow(time, timeScale)

    fun draw(frameNanos: Long): Boolean {
        val delta = if (lastDrawNanos < 0) 0f else (frameNanos - lastDrawNanos) / 1_000_000_000f
        lastDrawNanos = frameNanos
        val scaledDelta = delta * timeScale
        time += scaledDelta
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        GLES30.glUseProgram(program)
        GLES30.glUniformMatrix3fv(uniform("matrix"), 1, false, matrix, 0)
        GLES30.glUniform1f(uniform("reset"), if (firstDraw) 1f else 0f)
        GLES30.glUniform1f(uniform("time"), time)
        GLES30.glUniform1f(uniform("deltaTime"), scaledDelta)
        GLES30.glUniform1f(uniform("particlesCount"), grid.count.toFloat())
        GLES30.glUniform3f(uniform("gridSize"), grid.columns.toFloat(), grid.rows.toFloat(), grid.pointSize)
        GLES30.glUniform2f(uniform("offset"), 0f, 0f)
        GLES30.glUniform1f(uniform("scale"), 1f)
        GLES30.glUniform1f(uniform("uvOffset"), 0.6f)
        GLES30.glUniform1f(uniform("sweepReversed"), if (sweepReversed) 1f else 0f)
        GLES30.glUniform1f(uniform("useDissolveOrigin"), if (dissolveOrigin != null) 1f else 0f)
        dissolveOrigin?.let { GLES30.glUniform2f(uniform("dissolveOrigin"), it.x, it.y) }
        GLES30.glUniform2f(uniform("rectSize"), bitmap.width.toFloat(), bitmap.height.toFloat())
        GLES30.glUniform1f(uniform("seed"), seed)
        GLES30.glUniform1f(uniform("dp"), density)
        GLES30.glUniform1f(uniform("longevity"), THANOS_LONGEVITY)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture[0])
        GLES30.glUniform1i(uniform("tex"), 0)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, buffers[currentBuffer])
        for (attribute in 0..3) {
            GLES30.glVertexAttribPointer(
                attribute, if (attribute == 3) 1 else 2, GLES30.GL_FLOAT, false,
                THANOS_PARTICLE_STRIDE_BYTES, attribute * 8,
            )
            GLES30.glEnableVertexAttribArray(attribute)
        }
        GLES30.glBindTransformFeedback(GLES30.GL_TRANSFORM_FEEDBACK, feedback[0])
        GLES30.glBindBufferBase(GLES30.GL_TRANSFORM_FEEDBACK_BUFFER, 0, buffers[1 - currentBuffer])
        GLES30.glBeginTransformFeedback(GLES30.GL_POINTS)
        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, grid.count)
        GLES30.glEndTransformFeedback()
        GLES30.glBindBufferBase(GLES30.GL_TRANSFORM_FEEDBACK_BUFFER, 0, 0)
        GLES30.glBindTransformFeedback(GLES30.GL_TRANSFORM_FEEDBACK, 0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        firstDraw = false
        currentBuffer = 1 - currentBuffer
        checkGlError()
        return shouldNotifyParticleAnimationComplete(false, time, THANOS_DURATION_SECONDS)
    }

    fun release() {
        GLES30.glDeleteBuffers(2, buffers, 0)
        GLES30.glDeleteTextures(1, texture, 0)
        GLES30.glDeleteTransformFeedbacks(1, feedback, 0)
        if (program != 0) GLES30.glDeleteProgram(program)
        program = 0
    }

    private fun uniform(name: String): Int = uniforms.getValue(name)

    private fun compileShader(type: Int, resourceId: Int): Int {
        val source = resources.openRawResource(resourceId).bufferedReader().use { it.readText() }
        val shader = GLES30.glCreateShader(type)
        check(shader != 0) { "Cannot allocate Thanos shader" }
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] != GLES30.GL_TRUE) {
            val message = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            error("Thanos shader: $message")
        }
        return shader
    }

    private fun checkGlError() {
        val error = GLES30.glGetError()
        check(error == GLES30.GL_NO_ERROR) { "Thanos GL error: $error" }
    }
}
