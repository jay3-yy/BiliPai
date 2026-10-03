package com.example.hdr_player

import android.opengl.GLES11Ext
import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class Texture2dProgram {
    private val program: Int
    private val aPositionLoc: Int
    private val aTexCoordLoc: Int
    private val uTexMatrixLoc: Int
    private val uTargetPeakLoc: Int
    private val uStrengthLoc: Int
    private val uSaturationLoc: Int
    private val uHighlightBoostLoc: Int
    private val uPreDarkenLoc: Int
    private val uHighlightProtectLoc: Int
    private val uUseHdrLoc: Int
    private val uFrameIndexLoc: Int

    private val vertexBuffer: FloatBuffer
    private val texBuffer: FloatBuffer

    init {
        program = GlUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        aPositionLoc = GLES20.glGetAttribLocation(program, "aPosition")
        aTexCoordLoc = GLES20.glGetAttribLocation(program, "aTextureCoord")
        uTexMatrixLoc = GLES20.glGetUniformLocation(program, "uTexMatrix")
        uTargetPeakLoc = GLES20.glGetUniformLocation(program, "uTargetPeakNits")
        uStrengthLoc = GLES20.glGetUniformLocation(program, "uStrength")
        uSaturationLoc = GLES20.glGetUniformLocation(program, "uSaturation")
        uHighlightBoostLoc = GLES20.glGetUniformLocation(program, "uHighlightBoost")
        uPreDarkenLoc = GLES20.glGetUniformLocation(program, "uPreDarken")
        uHighlightProtectLoc = GLES20.glGetUniformLocation(program, "uHighlightProtect")
        uUseHdrLoc = GLES20.glGetUniformLocation(program, "uUseHdr")
        uFrameIndexLoc = GLES20.glGetUniformLocation(program, "uFrameIndex")

        vertexBuffer = ByteBuffer.allocateDirect(FULL_RECTANGLE_COORDS.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        vertexBuffer.put(FULL_RECTANGLE_COORDS).position(0)

        texBuffer = ByteBuffer.allocateDirect(FULL_RECTANGLE_TEX_COORDS.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        texBuffer.put(FULL_RECTANGLE_TEX_COORDS).position(0)
    }

    fun draw(
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
        GLES20.glUseProgram(program)
        GlUtil.checkGlError("glUseProgram")

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId)

        GLES20.glEnableVertexAttribArray(aPositionLoc)
        GLES20.glVertexAttribPointer(aPositionLoc, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        GLES20.glEnableVertexAttribArray(aTexCoordLoc)
        GLES20.glVertexAttribPointer(aTexCoordLoc, 2, GLES20.GL_FLOAT, false, 0, texBuffer)

        GLES20.glUniformMatrix4fv(uTexMatrixLoc, 1, false, texMatrix, 0)
        GLES20.glUniform1f(uTargetPeakLoc, targetPeakNits)
        GLES20.glUniform1f(uStrengthLoc, strength)
        GLES20.glUniform1f(uSaturationLoc, saturation)
        GLES20.glUniform1f(uHighlightBoostLoc, highlightBoost)
        GLES20.glUniform1f(uPreDarkenLoc, preDarken)
        GLES20.glUniform1f(uHighlightProtectLoc, highlightProtect)
        GLES20.glUniform1i(uUseHdrLoc, if (useHdr) 1 else 0)
        GLES20.glUniform1f(uFrameIndexLoc, frameIndex.toFloat())

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(aPositionLoc)
        GLES20.glDisableVertexAttribArray(aTexCoordLoc)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
    }

    companion object {
        private val FULL_RECTANGLE_COORDS = floatArrayOf(
            -1.0f, -1.0f,
            1.0f, -1.0f,
            -1.0f, 1.0f,
            1.0f, 1.0f
        )

        private val FULL_RECTANGLE_TEX_COORDS = floatArrayOf(
            0.0f, 0.0f,
            1.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 1.0f
        )

        private const val VERTEX_SHADER = """
            uniform mat4 uTexMatrix;
            attribute vec4 aPosition;
            attribute vec4 aTextureCoord;
            varying vec2 vTextureCoord;
            void main() {
                gl_Position = aPosition;
                vTextureCoord = (uTexMatrix * aTextureCoord).xy;
            }
        """

        private const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            precision highp float;
            #else
            precision mediump float;
            #endif
            uniform samplerExternalOES sTexture;
            uniform float uTargetPeakNits;
            uniform float uStrength;
            uniform float uSaturation;
            uniform float uHighlightBoost;
            uniform float uPreDarken;
            uniform float uHighlightProtect;
            uniform int uUseHdr;
            uniform float uFrameIndex;
            varying vec2 vTextureCoord;

            vec3 bt709ToBt2020(vec3 rgb) {
                mat3 m = mat3(
                    0.6274, 0.0691, 0.0164,
                    0.3293, 0.9195, 0.0880,
                    0.0433, 0.0114, 0.8956
                );
                return m * rgb;
            }

            float pqOetf(float L) {
                float m1 = 0.1593017578125;
                float m2 = 78.84375;
                float c1 = 0.8359375;
                float c2 = 18.8515625;
                float c3 = 18.6875;
                float Lm1 = pow(max(L, 0.0), m1);
                float num = c1 + c2 * Lm1;
                float den = 1.0 + c3 * Lm1;
                return pow(num / den, m2);
            }

            float softKneeNits(float valueNits, float kneeStartNits, float shoulderMaxNits) {
                float v = max(valueNits, 0.0);
                if (v <= kneeStartNits) {
                    return v;
                }
                float over = v - kneeStartNits;
                float span = max(shoulderMaxNits - kneeStartNits, 1.0);
                return kneeStartNits + (over * span) / (over + span);
            }

            void main() {
                vec3 rgb = texture2D(sTexture, vTextureCoord).rgb;
                if (uUseHdr == 0) {
                    gl_FragColor = vec4(rgb, 1.0);
                    return;
                }

                vec3 linear = pow(rgb, vec3(2.2));
                linear *= (1.0 - clamp(uPreDarken, 0.0, 0.8));
                vec3 wide = bt709ToBt2020(linear);

                float luma = dot(wide, vec3(0.2627, 0.6780, 0.0593));
                vec3 chroma = wide - vec3(luma);
                vec3 sat = vec3(luma) + chroma * clamp(uSaturation, 0.0, 1.2);

                float peak = max(uTargetPeakNits, 100.0);
                vec3 sdrNits = sat * 100.0;
                float toneStrength = clamp(uStrength, 0.0, 1.0);
                float protect = clamp(uHighlightProtect, 0.0, 1.0);
                float whiteAnchorNits = 0.55 * peak;
                vec3 anchorNits = sdrNits * (whiteAnchorNits / 100.0);
                vec3 mappedNits = mix(sdrNits, anchorNits, toneStrength);
                float lumaMapped = dot(mappedNits, vec3(0.2627, 0.6780, 0.0593));
                float t = pow(protect, 0.65);
                float x = max(lumaMapped / max(whiteAnchorNits, 1.0), 1.0e-5);
                float shadowLift = 1.0 + 0.05 * t * (1.0 - smoothstep(0.25, 0.65, x));
                float brightLift = smoothstep(0.55, 1.20, x);
                float highlightExpand = 1.0 + 2.20 * t * brightLift;
                float lumaExpanded = lumaMapped * shadowLift * highlightExpand;
                float highlightMask = smoothstep(0.75 * whiteAnchorNits, 1.05 * whiteAnchorNits, lumaExpanded);
                float boost = mix(1.0, clamp(uHighlightBoost, 0.5, 4.0), highlightMask);
                float boostedLuma = lumaExpanded + max(lumaExpanded - whiteAnchorNits, 0.0) * (boost - 1.0) * highlightMask;
                float kneeStartNits = mix(0.90 * peak, 0.68 * peak, t);
                float shoulderMaxNits = mix(1.8 * peak, 4.2 * peak, t);
                float lumaSoft = softKneeNits(boostedLuma, kneeStartNits, shoulderMaxNits);
                float lumaOut = max(lumaSoft, lumaMapped);
                float lumaScale = lumaOut / max(lumaMapped, 1.0e-4);
                lumaScale = min(lumaScale, 12.0);
                vec3 softNits = mappedNits * lumaScale;
                vec3 pq = vec3(
                    pqOetf(clamp(softNits.r / 10000.0, 0.0, 1.0)),
                    pqOetf(clamp(softNits.g / 10000.0, 0.0, 1.0)),
                    pqOetf(clamp(softNits.b / 10000.0, 0.0, 1.0))
                );

                vec3 outColor = pq;
                if (toneStrength > 0.05) {
                    float luma01 = clamp(dot(pq, vec3(0.2627, 0.6780, 0.0593)), 0.0, 1.0);
                    float noise = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233)) + uFrameIndex) * 43758.5453);
                    float ditherAmp = (1.0 / 4096.0);
                    float ditherScale = (1.0 - smoothstep(0.6, 1.0, luma01)) * toneStrength;
                    vec3 dither = vec3((noise - 0.5) * ditherAmp * ditherScale);
                    outColor = pq + dither;
                }

                gl_FragColor = vec4(clamp(outColor, 0.0, 1.0), 1.0);
            }
        """
    }
}

