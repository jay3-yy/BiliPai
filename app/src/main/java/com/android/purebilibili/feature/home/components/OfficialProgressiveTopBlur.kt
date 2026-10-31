package com.android.purebilibili.feature.home.components

import android.annotation.SuppressLint
import android.graphics.BlendMode
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

internal const val OFFICIAL_BACKDROP_MIN_FULL_SDK = 3_700_002

/** 只接受已发布的完整版本号；方法存在检查同时防止不完整的平台实现。 */
internal fun isOfficialProgressiveBlurSupported(): Boolean =
    Build.VERSION.SDK_INT >= 36 && Build.VERSION.SDK_INT_FULL >= OFFICIAL_BACKDROP_MIN_FULL_SDK &&
        OfficialBackdropApi.available

/**
 * null 表示应使用原有渐进模糊。失败只触发一次回退，重新选择方案后可重新尝试。
 * 不创建时钟、无限动画或逐帧回调，背景变化由平台合成器处理。
 */
@Composable
@SuppressLint("NewApi") // 完整版本与公开方法检查在资源创建前完成。
internal fun rememberOfficialProgressiveTopBlur(
    shape: Shape,
    blurRadiusDp: Float,
    gradient: ProgressiveBlur,
): Modifier? {
    if (!isOfficialProgressiveBlurSupported() || !gradient.angle.isFinite() ||
        !gradient.startFraction.isFinite() || !gradient.endFraction.isFinite() ||
        !gradient.curve.isFinite() || gradient.curve <= 0f ||
        kotlin.math.abs(gradient.endFraction - gradient.startFraction) <= 0.0001f
    ) return null
    return rememberSupportedOfficialProgressiveTopBlur(shape, blurRadiusDp, gradient)
}

@RequiresApi(33)
@Composable
private fun rememberSupportedOfficialProgressiveTopBlur(
    shape: Shape,
    blurRadiusDp: Float,
    gradient: ProgressiveBlur,
): Modifier? {
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    if (failed) return null
    val renderer = remember {
        try {
            OfficialProgressiveBackdropRenderer()
        } catch (_: RuntimeException) {
            null
        } catch (_: LinkageError) {
            null
        }
    } ?: return null
    DisposableEffect(renderer) { onDispose { renderer.release() } }
    return remember(renderer, shape, blurRadiusDp, gradient, scope) {
        Modifier.drawWithCache {
            val outline = Path().apply {
                addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache))
            }
            val ready = size.width > 0f && size.height > 0f && renderer.configure(
                width = ceil(size.width).toInt(),
                height = ceil(size.height).toInt(),
                radius = blurRadiusDp * density,
                gradient = gradient,
            )
            onDrawWithContent {
                if (size.width > 0f && size.height > 0f) {
                    clipPath(outline) {
                        drawIntoCanvas { canvas ->
                            if ((!ready || !renderer.draw(canvas.nativeCanvas)) && renderer.failOnce()) {
                                // 绘制结束后再切换方案，不从 draw 阶段回写当前组合。
                                scope.launch {
                                    yield()
                                    failed = true
                                }
                            }
                        }
                    }
                }
                drawContent()
            }
        }
    }
}

@RequiresApi(33)
private class OfficialProgressiveBackdropRenderer {
    private val node = RenderNode("ProgressiveTopBackdrop")
    // 四个档位各持有独立 shader；尺寸和渐变变化只更新 uniform 与效果快照。
    private val bandShaders = Array(4) { RuntimeShader(PROGRESSIVE_BAND_SHADER) }
    private val blurEffects = arrayOfNulls<RenderEffect>(4)
    private var blurRadius = Float.NaN
    private var configuredWidth = 0
    private var configuredHeight = 0
    private var configuredRadius = Float.NaN
    private var configuredGradient: ProgressiveBlur? = null
    private var failureReported = false

    fun failOnce(): Boolean {
        if (failureReported) return false
        failureReported = true
        return true
    }

    fun configure(width: Int, height: Int, radius: Float, gradient: ProgressiveBlur): Boolean {
        if (configuredWidth == width && configuredHeight == height &&
            configuredRadius == radius && configuredGradient == gradient
        ) return true
        return try {
            if (blurRadius != radius) {
                // 模糊半径与窗口尺寸无关；收起顶栏或调整窗口时复用三个模糊档位。
                for (level in 1..3) {
                    val levelRadius = radius * level / 3f
                    blurEffects[level] = RenderEffect.createBlurEffect(levelRadius, levelRadius, Shader.TileMode.CLAMP)
                }
                blurRadius = radius
            }
            node.setPosition(0, 0, width, height)
            node.setClipToBounds(true)
            val effect = createProgressiveBackdropEffect(
                width.toFloat(), height.toFloat(), gradient, bandShaders, blurEffects,
            )
            if (OfficialBackdropApi.setEffect(node, effect)) {
                // 透明 SrcOver 保留背景效果；CLEAR 会使背景效果不参与合成。
                val canvas = node.beginRecording(width, height)
                try {
                    canvas.drawColor(android.graphics.Color.TRANSPARENT)
                } finally {
                    node.endRecording()
                }
                configuredWidth = width
                configuredHeight = height
                configuredRadius = radius
                configuredGradient = gradient
                true
            } else false
        } catch (_: RuntimeException) {
            false
        } catch (_: LinkageError) {
            false
        }
    }

    fun draw(canvas: android.graphics.Canvas): Boolean {
        if (!canvas.isHardwareAccelerated) return false
        return try {
            canvas.drawRenderNode(node)
            true
        } catch (_: RuntimeException) {
            false
        } catch (_: LinkageError) {
            false
        }
    }

    fun release() {
        OfficialBackdropApi.setEffect(node, null)
        node.discardDisplayList()
        blurEffects.fill(null)
        blurRadius = Float.NaN
        configuredGradient = null
    }
}

/**
 * 四档半径按渐变位置线性混合，清晰端保留原始背景；权重之和为 1。
 * 模糊、遮罩、混合全部由平台 RenderEffect 链执行，缓存随尺寸与材质参数重建。
 */
@RequiresApi(33)
private fun createProgressiveBackdropEffect(
    width: Float,
    height: Float,
    gradient: ProgressiveBlur,
    bandShaders: Array<RuntimeShader>,
    blurEffects: Array<RenderEffect?>,
): RenderEffect {
    val angle = Math.toRadians(gradient.angle.toDouble())
    val dx = cos(angle).toFloat()
    val dy = sin(angle).toFloat()
    val minProjection = minOf(0f, width * dx) + minOf(0f, height * dy)
    val extent = (kotlin.math.abs(width * dx) + kotlin.math.abs(height * dy)).coerceAtLeast(1f)
    var result: RenderEffect? = null
    for (level in 0..3) {
        val shader = bandShaders[level].apply {
            setFloatUniform("direction", dx, dy)
            setFloatUniform("projection", minProjection, extent)
            setFloatUniform("ramp", gradient.startFraction, gradient.endFraction, gradient.curve)
            setFloatUniform("level", level.toFloat())
        }
        val band = RenderEffect.createRuntimeShaderEffect(shader, "content")
        val layer = if (level == 0) band else RenderEffect.createChainEffect(
            band,
            requireNotNull(blurEffects[level]),
        )
        result = result?.let { RenderEffect.createBlendModeEffect(it, layer, BlendMode.PLUS) } ?: layer
    }
    return requireNotNull(result)
}

private const val PROGRESSIVE_BAND_SHADER = """
    uniform shader content;
    uniform float2 direction;
    uniform float2 projection;
    uniform float3 ramp;
    uniform float level;
    half4 main(float2 p) {
        float position = (dot(p, direction) - projection.x) / projection.y;
        float progress = clamp((position - ramp.x) / (ramp.y - ramp.x), 0.0, 1.0);
        float strength = pow(1.0 - progress, ramp.z);
        float weight = max(1.0 - abs(strength * 3.0 - level), 0.0);
        return content.eval(p) * half(weight);
    }
"""

/** 通过公开方法接入，保持现有 compileSdk 与旧系统安装要求。 */
private object OfficialBackdropApi {
    private val method by lazy {
        try {
            RenderNode::class.java.getMethod("setBackdropRenderEffect", RenderEffect::class.java)
        } catch (_: ReflectiveOperationException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }

    val available: Boolean get() = method != null

    fun setEffect(node: RenderNode, effect: RenderEffect?): Boolean = try {
        method?.let {
            it.invoke(node, effect)
            true
        } ?: false
    } catch (_: ReflectiveOperationException) {
        false
    } catch (_: RuntimeException) {
        false
    }
}
