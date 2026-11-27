package com.android.purebilibili.feature.anime4k.gl

import android.content.Context
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import android.view.Surface
import com.android.purebilibili.core.ui.adaptive.DeviceThermalBudgetState
import com.android.purebilibili.core.ui.adaptive.DeviceThermalTier
import com.android.purebilibili.core.ui.performance.DEFAULT_ADPF_TARGET_FRAME_NANOS
import com.android.purebilibili.core.ui.performance.PerformanceHintSessionController
import com.android.purebilibili.core.ui.performance.resolveAdaptiveGlTargetFrameDurationNanos
import com.android.purebilibili.plugin.sdk.Anime4KConfig
import com.android.purebilibili.plugin.sdk.Anime4KPreset
import com.android.purebilibili.plugin.sdk.VideoEnhancementAlgorithm
import com.android.purebilibili.feature.anime4k.resolveAnime4KInputSize
import com.android.purebilibili.feature.anime4k.resolveAnime4KRenderProfile
import com.android.purebilibili.feature.anime4k.resolveFsr1TargetSize
import com.android.purebilibili.feature.anime4k.resolveFsrRcasSharpnessStops
import com.android.purebilibili.feature.anime4k.resolveThermallyAdjustedEnhancement
import com.android.purebilibili.feature.anime4k.shouldApplyFsr1Enhancement
import kotlin.math.roundToInt

internal class Anime4KPipelineRenderer(
    context: Context,
    initialConfig: Anime4KConfig,
    private val onFrameAvailable: () -> Unit,
    private val onInputSurfaceChanged: (Surface?) -> Unit,
    private val onFirstFrameRendered: () -> Unit,
    private val onPipelineError: (Throwable) -> Unit
) : GLSurfaceView.Renderer {

    private data class ProgramHandles(
        val program: Int,
        val positionAttr: Int,
        val texCoordAttr: Int,
        val texMatrixUniform: Int,
        val flipUniform: Int,
        val positionScaleUniform: Int,
        val textureUniform: Int,
        val extraUniform0: Int = -1,
        val extraUniform1: Int = -1,
    )

    private data class CompiledPass(
        val program: Int,
        val positionAttr: Int,
        val texCoordAttr: Int,
        val texMatrixUniform: Int,
        val flipUniform: Int,
        val positionScaleUniform: Int,
        val samplerLocations: IntArray,
        val sizeLocations: IntArray,
    )

    private class PreparedPassStep(
        val compiled: CompiledPass,
        val boundTargets: Array<FboTarget>,
        val outputTarget: FboTarget,
    )

    private class PreparedAnime4KPlan(
        val preset: Anime4KPreset,
        val cnnInputWidth: Int,
        val cnnInputHeight: Int,
        val outputWidth: Int,
        val outputHeight: Int,
        val nativeTarget: FboTarget,
        val steps: Array<PreparedPassStep>,
        val finalTarget: FboTarget,
    )

    private val shaderRepository = Anime4KShaderRepository(context)
    private val programBinaryCache = GlProgramBinaryCache(context.applicationContext.cacheDir)
    private val performanceHintSession = PerformanceHintSessionController(context)
    private val fboManager = FboManager()
    private val textureMatrix = FloatArray(16)
    private val identityMatrix = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
    private val compiledPasses = mutableMapOf<String, CompiledPass>()
    private var preparedAnime4KPlan: PreparedAnime4KPlan? = null

    private var externalTextureId = 0
    private var inputSurface: Anime4KInputSurface? = null
    private var outputWidth = 0
    private var outputHeight = 0
    private var inputWidth = 0
    private var inputHeight = 0
    private var flipHorizontal = false
    private var flipVertical = false
    private var displayScaleMode = Anime4KDisplayScaleMode.FIT
    private var config = initialConfig
    private var activeEffectiveAlgorithm: VideoEnhancementAlgorithm? = null
    private var activeEffectivePreset: Anime4KPreset? = null
    private var maxTextureSize = 1
    private var maxTextureUnits = 1
    @Volatile
    private var frameAvailable = false
    private var hasLatchedFrame = false
    private var notifiedFirstFrame = false
    private var failed = false
    private var renderSampleCount = 0
    private var accumulatedRenderNs = 0L
    private var explicitTargetFrameRateHz = 0f
    private var activeAdpfTargetDurationNs = DEFAULT_ADPF_TARGET_FRAME_NANOS
    private var pendingAdpfTargetDurationNs = 0L
    private var pendingAdpfTargetHits = 0
    private var lastLatchedFrameNs = 0L
    private var skipNextAdpfReport = true
    private var externalHandles: ProgramHandles? = null
    private var displayHandles: ProgramHandles? = null
    private var fsrEasuHandles: ProgramHandles? = null
    private var fsrRcasHandles: ProgramHandles? = null

    override fun onSurfaceCreated(
        unused: javax.microedition.khronos.opengles.GL10?,
        eglConfig: javax.microedition.khronos.egl.EGLConfig?
    ) {
        failed = false
        hasLatchedFrame = false
        notifiedFirstFrame = false
        resetPerformanceStats()
        try {
            releaseGlResources(releaseInput = true)
            programBinaryCache.onGlContextReady()
            skipNextAdpfReport = true
            lastLatchedFrameNs = 0L
            performanceHintSession.ensureStartedForCurrentThread(activeAdpfTargetDurationNs)
            maxTextureSize = queryGlInteger(GLES30.GL_MAX_TEXTURE_SIZE)
            maxTextureUnits = queryGlInteger(GLES30.GL_MAX_TEXTURE_IMAGE_UNITS)
            externalHandles = createProgramHandles(
                fragmentSource = Anime4KShaders.EXTERNAL_COPY,
                description = "OES 输入转换",
                textureUniformName = "uTexture",
            )
            displayHandles = createProgramHandles(
                fragmentSource = Anime4KShaders.DISPLAY_COPY,
                description = "最终画面输出",
                textureUniformName = "uTexture",
            )

            prepareRenderPipeline(config)
            ensureInputSurface()
            GLES30.glDisable(GLES30.GL_BLEND)
            GLES30.glClearColor(0f, 0f, 0f, 0f)
        } catch (error: Throwable) {
            failPipeline(error)
        }
    }

    override fun onSurfaceChanged(
        unused: javax.microedition.khronos.opengles.GL10?,
        width: Int,
        height: Int
    ) {
        val safeWidth = width.coerceAtLeast(1)
        val safeHeight = height.coerceAtLeast(1)
        if (outputWidth != safeWidth || outputHeight != safeHeight) {
            outputWidth = safeWidth
            outputHeight = safeHeight
            invalidateFboTargets()
            resetPerformanceStats()
        }
    }

    override fun onDrawFrame(unused: javax.microedition.khronos.opengles.GL10?) {
        if (failed) return
        try {
            val renderStartedNs = System.nanoTime()
            val latchedNewFrame = frameAvailable
            if (latchedNewFrame) {
                frameAvailable = false
                inputSurface?.surfaceTexture?.updateTexImage()
                inputSurface?.surfaceTexture?.getTransformMatrix(textureMatrix)
                hasLatchedFrame = true
                updateAdaptiveAdpfTarget(renderStartedNs)
            }
            if (!hasLatchedFrame || outputWidth <= 0 || outputHeight <= 0) {
                GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
                return
            }

            performanceHintSession.ensureStartedForCurrentThread(activeAdpfTargetDurationNs)
            renderFrame()
            val renderElapsedNs = System.nanoTime() - renderStartedNs
            if (skipNextAdpfReport) {
                // 跳过首帧与 FBO 重建首帧，避免一次性显存分配毛刺误触发 PowerHAL 长时间拉频。
                skipNextAdpfReport = false
            } else {
                performanceHintSession.reportWorkDuration(renderElapsedNs)
            }
            observeRenderTime(renderElapsedNs)
            if (!notifiedFirstFrame) {
                notifiedFirstFrame = true
                onFirstFrameRendered()
            }
        } catch (error: Throwable) {
            failPipeline(error)
        }
    }

    fun setConfig(value: Anime4KConfig) {
        val pipelineChanged = config.algorithm != value.algorithm || config.preset != value.preset
        if (!pipelineChanged && config.fsrSharpness == value.fsrSharpness) return
        try {
            config = value
            // FSR 锐度是逐帧上传的 uniform，拖动滑条时无需重建 shader/FBO 管线。
            if (!pipelineChanged) return
            notifiedFirstFrame = false
            invalidateFboTargets()
            resetPerformanceStats()
            prepareRenderPipeline(value)
            // 切模型只更换 GL pass，不能重建解码 Surface，否则播放器可能重新选择视频流。
            ensureInputSurface()
        } catch (error: Throwable) {
            failPipeline(error)
        }
    }

    fun setInputSize(width: Int, height: Int) {
        val safeWidth = width.coerceAtLeast(0)
        val safeHeight = height.coerceAtLeast(0)
        if (inputWidth != safeWidth || inputHeight != safeHeight) {
            inputWidth = safeWidth
            inputHeight = safeHeight
            invalidateFboTargets()
            resetPerformanceStats()
        }
        inputSurface?.setDefaultBufferSize(inputWidth, inputHeight)
    }

    fun setFlip(horizontal: Boolean, vertical: Boolean) {
        flipHorizontal = horizontal
        flipVertical = vertical
    }

    fun setDisplayScaleMode(scaleMode: Anime4KDisplayScaleMode) {
        displayScaleMode = scaleMode
    }

    fun setTargetFrameRateHz(frameRateHz: Float) {
        explicitTargetFrameRateHz = if (frameRateHz.isFinite() && frameRateHz > 1f) frameRateHz else 0f
        if (explicitTargetFrameRateHz > 1f) {
            activeAdpfTargetDurationNs = resolveAdaptiveGlTargetFrameDurationNanos(
                observedFrameIntervalNanos = 0L,
                explicitFrameRateHz = explicitTargetFrameRateHz,
            )
            pendingAdpfTargetDurationNs = 0L
            pendingAdpfTargetHits = 0
        }
    }

    /** 宿主 GLSurfaceView 进入 onPause 时主动释放输入 Surface 并关闭 ADPF 会话，防止后台驻留。 */
    fun onHostPaused() {
        performanceHintSession.close()
        lastLatchedFrameNs = 0L
        skipNextAdpfReport = true
        releaseInputSurface()
    }

    fun ensureInputSurface() {
        if (inputSurface != null || failed) return
        externalTextureId = IntArray(1).also { GLES30.glGenTextures(1, it, 0) }[0]
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, externalTextureId)
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES30.GL_TEXTURE_MIN_FILTER,
            GLES30.GL_LINEAR
        )
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES30.GL_TEXTURE_MAG_FILTER,
            GLES30.GL_LINEAR
        )
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES30.GL_TEXTURE_WRAP_S,
            GLES30.GL_CLAMP_TO_EDGE
        )
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES30.GL_TEXTURE_WRAP_T,
            GLES30.GL_CLAMP_TO_EDGE
        )
        inputSurface = Anime4KInputSurface(externalTextureId) {
            frameAvailable = true
            onFrameAvailable()
        }.also { it.setDefaultBufferSize(inputWidth, inputHeight) }
        onInputSurfaceChanged(inputSurface?.surface)
    }

    fun releaseInputSurface() {
        inputSurface?.let {
            onInputSurfaceChanged(null)
            it.release()
        }
        inputSurface = null
        if (externalTextureId != 0) {
            GLES30.glDeleteTextures(1, intArrayOf(externalTextureId), 0)
            externalTextureId = 0
        }
        hasLatchedFrame = false
        frameAvailable = false
    }

    fun release() {
        releaseGlResources(releaseInput = true)
    }

    /** GL 线程退出并销毁 EGL 后调用；此时只释放独立的原生输入资源，不再调用 GLES。 */
    fun releaseInputAfterThreadExit() {
        performanceHintSession.close()
        val detachedInput = inputSurface
        inputSurface = null
        externalTextureId = 0
        hasLatchedFrame = false
        frameAvailable = false
        if (detachedInput != null) {
            onInputSurfaceChanged(null)
            detachedInput.release()
        }
    }

    private fun updateAdaptiveAdpfTarget(frameStartNs: Long) {
        if (explicitTargetFrameRateHz > 1f) return
        val prevNs = lastLatchedFrameNs
        lastLatchedFrameNs = frameStartNs
        if (prevNs <= 0L) return
        val intervalNs = frameStartNs - prevNs
        val candidate = resolveAdaptiveGlTargetFrameDurationNanos(intervalNs)
        if (candidate == activeAdpfTargetDurationNs) {
            pendingAdpfTargetDurationNs = 0L
            pendingAdpfTargetHits = 0
            return
        }
        if (candidate == pendingAdpfTargetDurationNs) {
            pendingAdpfTargetHits += 1
            if (pendingAdpfTargetHits >= 2) {
                activeAdpfTargetDurationNs = candidate
                pendingAdpfTargetDurationNs = 0L
                pendingAdpfTargetHits = 0
            }
        } else {
            pendingAdpfTargetDurationNs = candidate
            pendingAdpfTargetHits = 1
        }
    }

    private fun renderFrame() {
        val thermalTier = DeviceThermalBudgetState.tier
        performanceHintSession.setPreferPowerEfficiency(thermalTier != DeviceThermalTier.NORMAL)
        val effectiveConfig = resolveThermallyAdjustedEnhancement(
            config = config,
            thermalTier = thermalTier,
        )
        // 热降档或回温切换执行档位时，先释放旧管线残留的高精度 FBO 显存。
        if (activeEffectiveAlgorithm != effectiveConfig.algorithm ||
            activeEffectivePreset != effectiveConfig.preset
        ) {
            if (activeEffectiveAlgorithm != null) {
                invalidateFboTargets()
            }
            activeEffectiveAlgorithm = effectiveConfig.algorithm
            activeEffectivePreset = effectiveConfig.preset
        }
        if (effectiveConfig.algorithm == VideoEnhancementAlgorithm.FSR_1_0) {
            ensureFsrProgramsReady()
            renderFsrFrame()
            return
        }
        val sourceWidth = inputWidth.takeIf { it > 0 } ?: outputWidth
        val sourceHeight = inputHeight.takeIf { it > 0 } ?: outputHeight
        val (cnnInputWidth, cnnInputHeight) = resolveAnime4KInputSize(
            inputWidth = sourceWidth,
            inputHeight = sourceHeight,
            glMaxTextureSize = maxTextureSize
        )
        val plan = obtainPreparedAnime4KPlan(
            preset = effectiveConfig.preset,
            cnnInputWidth = cnnInputWidth,
            cnnInputHeight = cnnInputHeight,
        )
        drawExternal(plan.nativeTarget)
        val steps = plan.steps
        for (i in steps.indices) {
            val step = steps[i]
            drawMpvPass(step.compiled, step.boundTargets, step.outputTarget)
        }
        drawDisplay(plan.finalTarget)
    }

    private fun obtainPreparedAnime4KPlan(
        preset: Anime4KPreset,
        cnnInputWidth: Int,
        cnnInputHeight: Int,
    ): PreparedAnime4KPlan {
        val cached = preparedAnime4KPlan
        if (cached != null &&
            cached.preset == preset &&
            cached.cnnInputWidth == cnnInputWidth &&
            cached.cnnInputHeight == cnnInputHeight &&
            cached.outputWidth == outputWidth &&
            cached.outputHeight == outputHeight
        ) {
            return cached
        }
        val profile = resolveAnime4KRenderProfile(preset)
        val nativeTarget = fboManager.obtain(
            width = cnnInputWidth,
            height = cnnInputHeight,
            precision = FboPrecision.RGBA8,
        )
        val (steps, finalTarget) = buildShaderChainPlan(
            shaderFiles = shaderRepository.loadChain(profile.shaderChain),
            nativeTarget = nativeTarget,
        )
        return PreparedAnime4KPlan(
            preset = preset,
            cnnInputWidth = cnnInputWidth,
            cnnInputHeight = cnnInputHeight,
            outputWidth = outputWidth,
            outputHeight = outputHeight,
            nativeTarget = nativeTarget,
            steps = steps,
            finalTarget = finalTarget,
        ).also { preparedAnime4KPlan = it }
    }

    /**
     * 公开 Android 视频实现已验证的 FSR 路径：OES 直接执行 EASU 到 RGBA8，
     * RCAS 再直接写入展示 Surface，避免额外的 OES 拷贝和半浮点附件。
     */
    private fun renderFsrFrame() {
        val sourceWidth = inputWidth.takeIf { it > 0 } ?: outputWidth
        val sourceHeight = inputHeight.takeIf { it > 0 } ?: outputHeight
        val displayTransform = resolveAnime4KDisplayTransform(
            outputWidth = outputWidth,
            outputHeight = outputHeight,
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            scaleMode = displayScaleMode
        )
        val requestedWidth = (outputWidth * displayTransform.scaleX).roundToInt()
        val requestedHeight = (outputHeight * displayTransform.scaleY).roundToInt()
        if (
            !shouldApplyFsr1Enhancement(
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight,
                outputWidth = requestedWidth,
                outputHeight = requestedHeight
            )
        ) {
            // 明显高于屏幕分辨率的视频已经有足够采样信息，避免额外 GPU 负载。
            val nativeSize = resolveAnime4KInputSize(sourceWidth, sourceHeight, maxTextureSize)
            val nativeTarget = fboManager.obtain(
                width = nativeSize.first,
                height = nativeSize.second,
                precision = FboPrecision.RGBA8
            )
            drawExternal(nativeTarget)
            drawDisplay(nativeTarget)
            return
        }
        val targetSize = resolveFsrTargetSize(
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            requestedWidth = requestedWidth,
            requestedHeight = requestedHeight
        )
        val easuTarget = fboManager.obtain(
            width = targetSize.first,
            height = targetSize.second,
            precision = FboPrecision.RGBA8
        )
        drawFsrEasu(easuTarget, sourceWidth, sourceHeight)
        drawFsrRcas(easuTarget)
    }

    private fun resolveFsrTargetSize(
        sourceWidth: Int,
        sourceHeight: Int,
        requestedWidth: Int,
        requestedHeight: Int
    ): Pair<Int, Int> {
        return resolveFsr1TargetSize(
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            requestedWidth = requestedWidth,
            requestedHeight = requestedHeight,
            glMaxTextureSize = maxTextureSize
        )
    }

    private fun drawFsrEasu(target: FboTarget, sourceWidth: Int, sourceHeight: Int) {
        val handles = requireNotNull(fsrEasuHandles) { "AMD FSR 1.0 EASU 尚未初始化" }
        bindTarget(target)
        GLES30.glUseProgram(handles.program)
        bindTexture(
            uniformLocation = handles.textureUniform,
            target = GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            texture = externalTextureId,
            unit = 0,
        )
        GLES30.glUniform2f(
            handles.extraUniform0,
            sourceWidth.toFloat(),
            sourceHeight.toFloat(),
        )
        setVertexUniforms(handles, textureMatrix, flipHorizontal, flipVertical)
        QuadRenderUtils.draw(handles.positionAttr, handles.texCoordAttr)
    }

    private fun drawFsrRcas(target: FboTarget) {
        val handles = requireNotNull(fsrRcasHandles) { "AMD FSR 1.0 RCAS 尚未初始化" }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, outputWidth, outputHeight)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        GLES30.glUseProgram(handles.program)
        bindTexture(
            uniformLocation = handles.textureUniform,
            target = GLES30.GL_TEXTURE_2D,
            texture = target.texture,
            unit = 0,
        )
        GLES30.glUniform2f(
            handles.extraUniform0,
            target.width.toFloat(),
            target.height.toFloat(),
        )
        GLES30.glUniform1f(
            handles.extraUniform1,
            resolveFsrRcasSharpnessStops(config.fsrSharpness),
        )
        val displayTransform = resolveAnime4KDisplayTransform(
            outputWidth = outputWidth,
            outputHeight = outputHeight,
            sourceWidth = inputWidth.takeIf { it > 0 } ?: target.width,
            sourceHeight = inputHeight.takeIf { it > 0 } ?: target.height,
            scaleMode = displayScaleMode,
        )
        setVertexUniforms(
            handles = handles,
            matrix = identityMatrix,
            horizontalFlip = false,
            verticalFlip = false,
            positionScaleX = displayTransform.scaleX,
            positionScaleY = displayTransform.scaleY,
        )
        QuadRenderUtils.draw(handles.positionAttr, handles.texCoordAttr)
    }

    /** 预计算整条 Anime4K Pass 执行拓扑与 FBO 绑定，避免播放期每帧分配集合和解析表达式。 */
    private fun buildShaderChainPlan(
        shaderFiles: List<Anime4KShaderFile>,
        nativeTarget: FboTarget,
    ): Pair<Array<PreparedPassStep>, FboTarget> {
        val steps = ArrayList<PreparedPassStep>(16)
        val logicalTargets = mutableMapOf("MAIN" to nativeTarget)
        val deferredBindings = shaderFiles
            .flatMap(Anime4KShaderFile::passes)
            .filter { it.hook != "MAIN" }
            .flatMap(MpvAnime4KShaderPass::bindings)
            .filterNot { it == "MAIN" || it == "HOOKED" || it == "NATIVE" }
            .toSet()
        SUPPORTED_HOOK_ORDER.forEach { hook ->
            shaderFiles.forEach { shaderFile ->
                shaderFile.passes
                    .filter { it.hook == hook }
                    .forEach { pass ->
                        planShaderPass(
                            shaderFile = shaderFile,
                            pass = pass,
                            logicalTargets = logicalTargets,
                            nativeTarget = nativeTarget,
                            outSteps = steps,
                        )
                    }
                val fileLocalOutputs = shaderFile.passes
                    .filter { it.hook == hook }
                    .mapNotNull(MpvAnime4KShaderPass::save)
                    .filterNot { it == "MAIN" || it in deferredBindings }
                logicalTargets.keys.removeAll(fileLocalOutputs.toSet())
            }
        }
        val unsupportedHooks = shaderFiles
            .flatMap(Anime4KShaderFile::passes)
            .map(MpvAnime4KShaderPass::hook)
            .filterNot(SUPPORTED_HOOK_ORDER::contains)
            .distinct()
        check(unsupportedHooks.isEmpty()) {
            "Anime4K shader 使用了尚未支持的 Hook：${unsupportedHooks.joinToString()}"
        }
        return steps.toTypedArray() to (logicalTargets["MAIN"] ?: nativeTarget)
    }

    private fun planShaderPass(
        shaderFile: Anime4KShaderFile,
        pass: MpvAnime4KShaderPass,
        logicalTargets: MutableMap<String, FboTarget>,
        nativeTarget: FboTarget,
        outSteps: MutableList<PreparedPassStep>,
    ) {
        val hookTarget = logicalTargets["MAIN"]
            ?: error("${shaderFile.name} 缺少 MAIN 输入")
        val sizeEnvironment = buildSizeEnvironment(
            logicalTargets = logicalTargets,
            hookTarget = hookTarget,
            nativeTarget = nativeTarget,
        )
        if (pass.whenExpression != null &&
            evaluateMpvShaderExpression(pass.whenExpression, sizeEnvironment) == 0.0
        ) {
            return
        }

        val boundTargets = Array(pass.bindings.size) { index ->
            when (val binding = pass.bindings[index]) {
                "HOOKED" -> hookTarget
                "NATIVE" -> nativeTarget
                else -> logicalTargets[binding]
                    ?: error("${shaderFile.name} 的 ${pass.description} 缺少纹理 $binding")
            }
        }
        check(boundTargets.size <= maxTextureUnits) {
            "${pass.description} 需要 ${boundTargets.size} 个纹理单元，设备仅支持 $maxTextureUnits 个"
        }

        val targetWidth = resolveTargetDimension(
            expression = pass.widthExpression,
            fallback = hookTarget.width,
            sizes = sizeEnvironment,
        )
        val targetHeight = resolveTargetDimension(
            expression = pass.heightExpression,
            fallback = hookTarget.height,
            sizes = sizeEnvironment,
        )
        val protectedTextureIds = buildSet {
            add(nativeTarget.texture)
            logicalTargets.values.forEach { add(it.texture) }
            boundTargets.forEach { add(it.texture) }
        }
        val outputTarget = fboManager.obtain(
            width = targetWidth,
            height = targetHeight,
            precision = if (pass.components == 1) FboPrecision.R16F else FboPrecision.RGBA16F,
            protectedTextureIds = protectedTextureIds,
        )
        outSteps.add(
            PreparedPassStep(
                compiled = obtainCompiledPass(pass),
                boundTargets = boundTargets,
                outputTarget = outputTarget,
            )
        )

        val outputName = pass.save ?: "MAIN"
        logicalTargets[outputName] = outputTarget
    }

    private fun buildSizeEnvironment(
        logicalTargets: Map<String, FboTarget>,
        hookTarget: FboTarget,
        nativeTarget: FboTarget
    ): Map<String, Anime4KTextureSize> {
        val sizes = logicalTargets.mapValues { (_, target) ->
            Anime4KTextureSize(target.width, target.height)
        }.toMutableMap()
        sizes["HOOKED"] = Anime4KTextureSize(hookTarget.width, hookTarget.height)
        sizes["NATIVE"] = Anime4KTextureSize(nativeTarget.width, nativeTarget.height)
        sizes["OUTPUT"] = Anime4KTextureSize(outputWidth, outputHeight)
        return sizes
    }

    private fun resolveTargetDimension(
        expression: String?,
        fallback: Int,
        sizes: Map<String, Anime4KTextureSize>
    ): Int {
        val value = expression
            ?.let { evaluateMpvShaderExpression(it, sizes).roundToInt() }
            ?: fallback
        return value.coerceIn(1, maxTextureSize)
    }

    private fun drawExternal(target: FboTarget) {
        val handles = requireNotNull(externalHandles) { "OES 输入转换尚未初始化" }
        bindTarget(target)
        GLES30.glUseProgram(handles.program)
        bindTexture(
            uniformLocation = handles.textureUniform,
            target = GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            texture = externalTextureId,
            unit = 0,
        )
        setVertexUniforms(handles, textureMatrix, flipHorizontal, flipVertical)
        QuadRenderUtils.draw(handles.positionAttr, handles.texCoordAttr)
    }

    private fun drawMpvPass(
        compiled: CompiledPass,
        boundTargets: Array<FboTarget>,
        outputTarget: FboTarget,
    ) {
        bindTarget(outputTarget)
        GLES30.glUseProgram(compiled.program)
        for (index in boundTargets.indices) {
            val target = boundTargets[index]
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + index)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, target.texture)
            GLES30.glUniform1i(compiled.samplerLocations[index], index)
            GLES30.glUniform2f(
                compiled.sizeLocations[index],
                target.width.toFloat(),
                target.height.toFloat(),
            )
        }
        setVertexUniforms(
            texMatrixUniform = compiled.texMatrixUniform,
            flipUniform = compiled.flipUniform,
            positionScaleUniform = compiled.positionScaleUniform,
            matrix = identityMatrix,
            horizontalFlip = false,
            verticalFlip = false,
        )
        QuadRenderUtils.draw(compiled.positionAttr, compiled.texCoordAttr)
    }

    private fun drawDisplay(target: FboTarget) {
        val handles = requireNotNull(displayHandles) { "最终画面输出尚未初始化" }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, outputWidth, outputHeight)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        GLES30.glUseProgram(handles.program)
        bindTexture(
            uniformLocation = handles.textureUniform,
            target = GLES30.GL_TEXTURE_2D,
            texture = target.texture,
            unit = 0,
        )
        val displayTransform = resolveAnime4KDisplayTransform(
            outputWidth = outputWidth,
            outputHeight = outputHeight,
            sourceWidth = inputWidth.takeIf { it > 0 } ?: target.width,
            sourceHeight = inputHeight.takeIf { it > 0 } ?: target.height,
            scaleMode = displayScaleMode,
        )
        setVertexUniforms(
            handles = handles,
            matrix = identityMatrix,
            horizontalFlip = false,
            verticalFlip = false,
            positionScaleX = displayTransform.scaleX,
            positionScaleY = displayTransform.scaleY,
        )
        QuadRenderUtils.draw(handles.positionAttr, handles.texCoordAttr)
    }

    private fun obtainCompiledPass(pass: MpvAnime4KShaderPass): CompiledPass {
        val key = "${pass.sourceName}:${pass.index}"
        return compiledPasses.getOrPut(key) {
            val program = createProgram(
                fragmentSource = buildMpvAnime4KFragmentShader(pass),
                description = "${pass.sourceName} / ${pass.description}",
            )
            CompiledPass(
                program = program,
                positionAttr = GLES30.glGetAttribLocation(program, "aPosition"),
                texCoordAttr = GLES30.glGetAttribLocation(program, "aTexCoord"),
                texMatrixUniform = GLES30.glGetUniformLocation(program, "uTexMatrix"),
                flipUniform = GLES30.glGetUniformLocation(program, "uFlip"),
                positionScaleUniform = GLES30.glGetUniformLocation(program, "uPositionScale"),
                samplerLocations = IntArray(pass.bindings.size) { index ->
                    GLES30.glGetUniformLocation(program, "uTexture$index")
                },
                sizeLocations = IntArray(pass.bindings.size) { index ->
                    GLES30.glGetUniformLocation(program, "uTextureSize$index")
                },
            )
        }
    }

    private fun prepareShaderChain(preset: Anime4KPreset) {
        val profile = resolveAnime4KRenderProfile(preset)
        shaderRepository.loadChain(profile.shaderChain)
            .flatMap(Anime4KShaderFile::passes)
            .forEach { pass ->
                check(pass.bindings.size <= maxTextureUnits) {
                    "${pass.description} 需要 ${pass.bindings.size} 个纹理单元，设备仅支持 $maxTextureUnits 个"
                }
                obtainCompiledPass(pass)
            }
    }

    private fun prepareRenderPipeline(value: Anime4KConfig) {
        when (value.algorithm) {
            VideoEnhancementAlgorithm.ANIME4K -> {
                // 实际创建附件比仅检查扩展字符串更可靠。
                val rgba16fProbe = fboManager.obtain(
                    width = 1,
                    height = 1,
                    precision = FboPrecision.RGBA16F,
                )
                fboManager.obtain(
                    width = 1,
                    height = 1,
                    precision = FboPrecision.R16F,
                    protectedTextureIds = setOf(rgba16fProbe.texture),
                )
                invalidateFboTargets()
                prepareShaderChain(value.preset)
            }

            VideoEnhancementAlgorithm.FSR_1_0 -> {
                ensureFsrProgramsReady()
            }
        }
    }

    private fun ensureFsrProgramsReady() {
        if (fsrEasuHandles == null) {
            fsrEasuHandles = createProgramHandles(
                fragmentSource = Fsr1Shaders.EASU,
                description = "AMD FSR 1.0 EASU",
                textureUniformName = "uTexture",
                extraUniform0Name = "uInputSize",
            )
        }
        if (fsrRcasHandles == null) {
            fsrRcasHandles = createProgramHandles(
                fragmentSource = Fsr1Shaders.RCAS,
                description = "AMD FSR 1.0 RCAS",
                textureUniformName = "uTexture",
                extraUniform0Name = "uTextureSize",
                extraUniform1Name = "uSharpnessStops",
            )
        }
    }

    private fun invalidateFboTargets() {
        preparedAnime4KPlan = null
        fboManager.release()
        skipNextAdpfReport = true
    }

    private fun bindTarget(target: FboTarget) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, target.framebuffer)
        // TBDR 移动 GPU 丢弃旧附件内容，避免全屏覆写前从 DRAM 加载上一帧像素到片上 Tile 缓存。
        GLES30.glInvalidateFramebuffer(GLES30.GL_FRAMEBUFFER, 1, COLOR_ATTACHMENT0_INVALIDATE, 0)
        GLES30.glViewport(0, 0, target.width, target.height)
    }

    private fun bindTexture(
        uniformLocation: Int,
        target: Int,
        texture: Int,
        unit: Int,
    ) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit)
        GLES30.glBindTexture(target, texture)
        GLES30.glUniform1i(uniformLocation, unit)
    }

    private fun setVertexUniforms(
        handles: ProgramHandles,
        matrix: FloatArray,
        horizontalFlip: Boolean,
        verticalFlip: Boolean,
        positionScaleX: Float = 1f,
        positionScaleY: Float = 1f,
    ) {
        setVertexUniforms(
            texMatrixUniform = handles.texMatrixUniform,
            flipUniform = handles.flipUniform,
            positionScaleUniform = handles.positionScaleUniform,
            matrix = matrix,
            horizontalFlip = horizontalFlip,
            verticalFlip = verticalFlip,
            positionScaleX = positionScaleX,
            positionScaleY = positionScaleY,
        )
    }

    private fun setVertexUniforms(
        texMatrixUniform: Int,
        flipUniform: Int,
        positionScaleUniform: Int,
        matrix: FloatArray,
        horizontalFlip: Boolean,
        verticalFlip: Boolean,
        positionScaleX: Float = 1f,
        positionScaleY: Float = 1f,
    ) {
        GLES30.glUniformMatrix4fv(texMatrixUniform, 1, false, matrix, 0)
        GLES30.glUniform2f(
            flipUniform,
            if (horizontalFlip) 1f else 0f,
            if (verticalFlip) 1f else 0f,
        )
        GLES30.glUniform2f(positionScaleUniform, positionScaleX, positionScaleY)
    }

    private fun createProgramHandles(
        fragmentSource: String,
        description: String,
        textureUniformName: String,
        extraUniform0Name: String? = null,
        extraUniform1Name: String? = null,
    ): ProgramHandles {
        val program = createProgram(fragmentSource, description)
        return ProgramHandles(
            program = program,
            positionAttr = GLES30.glGetAttribLocation(program, "aPosition"),
            texCoordAttr = GLES30.glGetAttribLocation(program, "aTexCoord"),
            texMatrixUniform = GLES30.glGetUniformLocation(program, "uTexMatrix"),
            flipUniform = GLES30.glGetUniformLocation(program, "uFlip"),
            positionScaleUniform = GLES30.glGetUniformLocation(program, "uPositionScale"),
            textureUniform = GLES30.glGetUniformLocation(program, textureUniformName),
            extraUniform0 = extraUniform0Name?.let { GLES30.glGetUniformLocation(program, it) } ?: -1,
            extraUniform1 = extraUniform1Name?.let { GLES30.glGetUniformLocation(program, it) } ?: -1,
        )
    }

    private fun createProgram(fragmentSource: String, description: String): Int {
        val cachedProgram = programBinaryCache.tryLoadProgram(Anime4KShaders.VERTEX, fragmentSource)
        if (cachedProgram != 0) return cachedProgram

        val vertex = compileShader(
            type = GLES30.GL_VERTEX_SHADER,
            source = Anime4KShaders.VERTEX,
            description = "$description vertex",
        )
        val fragment = compileShader(
            type = GLES30.GL_FRAGMENT_SHADER,
            source = fragmentSource,
            description = "$description fragment",
        )
        val program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vertex)
        GLES30.glAttachShader(program, fragment)
        if (programBinaryCache.isSupported) {
            GLES30.glProgramParameteri(
                program,
                GLES30.GL_PROGRAM_BINARY_RETRIEVABLE_HINT,
                GLES30.GL_TRUE,
            )
        }
        GLES30.glLinkProgram(program)
        val linked = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linked, 0)
        GLES30.glDeleteShader(vertex)
        GLES30.glDeleteShader(fragment)
        if (linked[0] != GLES30.GL_TRUE) {
            val log = GLES30.glGetProgramInfoLog(program)
            GLES30.glDeleteProgram(program)
            error("Anime4K program 链接失败（$description）：$log")
        }
        programBinaryCache.saveProgram(program, Anime4KShaders.VERTEX, fragmentSource)
        return program
    }

    private fun compileShader(type: Int, source: String, description: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] != GLES30.GL_TRUE) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            error("Anime4K shader 编译失败（$description）：$log")
        }
        return shader
    }

    private fun queryGlInteger(name: Int): Int {
        return IntArray(1)
            .also { GLES30.glGetIntegerv(name, it, 0) }[0]
            .coerceAtLeast(1)
    }

    private fun releaseGlResources(releaseInput: Boolean) {
        performanceHintSession.close()
        invalidateFboTargets()
        buildSet {
            externalHandles?.let { add(it.program) }
            displayHandles?.let { add(it.program) }
            fsrEasuHandles?.let { add(it.program) }
            fsrRcasHandles?.let { add(it.program) }
            compiledPasses.values.forEach { add(it.program) }
        }.filter { it != 0 }
            .forEach(GLES30::glDeleteProgram)
        externalHandles = null
        displayHandles = null
        fsrEasuHandles = null
        fsrRcasHandles = null
        compiledPasses.clear()
        activeEffectiveAlgorithm = null
        activeEffectivePreset = null
        if (releaseInput) releaseInputSurface()
    }

    private fun failPipeline(error: Throwable) {
        if (failed) return
        failed = true
        Log.e(TAG, "画质增强渲染失败", error)
        runCatching { releaseInputSurface() }
        onPipelineError(error)
    }

    private fun observeRenderTime(renderNs: Long) {
        renderSampleCount += 1
        accumulatedRenderNs += renderNs
        if (renderSampleCount >= PERFORMANCE_LOG_SAMPLE_COUNT) {
            val averageMs = accumulatedRenderNs / renderSampleCount / 1_000_000.0
            Log.d(
                TAG,
                "algorithm=${config.algorithm}, preset=${config.preset}, " +
                    "averageSubmitMs=${"%.2f".format(averageMs)}"
            )
            renderSampleCount = 0
            accumulatedRenderNs = 0L
        }
    }

    private fun resetPerformanceStats() {
        renderSampleCount = 0
        accumulatedRenderNs = 0L
    }

    private companion object {
        const val TAG = "Anime4KRenderer"
        const val PERFORMANCE_LOG_SAMPLE_COUNT = 120
        val SUPPORTED_HOOK_ORDER = listOf("MAIN", "PREKERNEL")
        val COLOR_ATTACHMENT0_INVALIDATE = intArrayOf(GLES30.GL_COLOR_ATTACHMENT0)
    }
}
