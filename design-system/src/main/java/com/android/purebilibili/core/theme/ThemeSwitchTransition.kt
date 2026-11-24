package com.android.purebilibili.core.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntSize
import com.android.purebilibili.core.ui.LocalSystemReduceMotion
import com.android.purebilibili.core.ui.motion.AppMotionEasing
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.roundToInt

enum class ThemeSwitchTransitionStyle(
    val storageValue: String,
    val label: String,
    val description: String,
) {
    RADIAL_REVEAL(
        storageValue = "radial_reveal",
        label = "径向揭示",
        description = "从点击位置向外柔和扩散展示新主题",
    ),
    CROSSFADE(
        storageValue = "crossfade",
        label = "柔和淡入",
        description = "全屏平滑过渡到新主题",
    ),
    NONE(
        storageValue = "none",
        label = "立即切换",
        description = "不使用转场动画，立即应用新主题",
    );

    companion object {
        fun fromStorageValue(rawValue: String?): ThemeSwitchTransitionStyle =
            entries.firstOrNull { it.storageValue == rawValue || it.name == rawValue }
                ?: RADIAL_REVEAL
    }
}

internal const val THEME_SWITCH_RADIAL_REVEAL_DURATION_MS = 280
internal const val THEME_SWITCH_CROSSFADE_DURATION_MS = 260

fun resolveEffectiveThemeSwitchTransitionStyle(
    preferred: ThemeSwitchTransitionStyle,
    animationsEnabled: Boolean,
    systemReduceMotion: Boolean,
): ThemeSwitchTransitionStyle =
    if (!animationsEnabled || systemReduceMotion) ThemeSwitchTransitionStyle.NONE else preferred

fun resolveRadialRevealMaxRadius(
    origin: Offset,
    width: Float,
    height: Float,
): Float {
    if (width <= 0f || height <= 0f) return 0f
    val topLeft = hypot(origin.x, origin.y)
    val topRight = hypot(width - origin.x, origin.y)
    val bottomLeft = hypot(origin.x, height - origin.y)
    val bottomRight = hypot(width - origin.x, height - origin.y)
    return maxOf(topLeft, topRight, bottomLeft, bottomRight)
}

fun resolveSnapshotOverlayAlpha(
    style: ThemeSwitchTransitionStyle,
    progress: Float,
): Float {
    val clamped = progress.coerceIn(0f, 1f)
    return when (style) {
        ThemeSwitchTransitionStyle.NONE -> 0f
        ThemeSwitchTransitionStyle.CROSSFADE -> 1f - clamped
        ThemeSwitchTransitionStyle.RADIAL_REVEAL ->
            1f - ((clamped - 0.85f) / 0.15f).coerceIn(0f, 1f)
    }
}

class ThemeSwitchTransitionController internal constructor(
    private val runner: (anchorInWindow: Offset, onCommit: () -> Unit) -> Unit,
) {
    var isTransitionActive: Boolean by mutableStateOf(false)
        internal set

    fun runTransition(
        anchorInWindow: Offset = Offset.Unspecified,
        onCommit: () -> Unit,
    ) {
        runner(anchorInWindow, onCommit)
    }
}

val LocalThemeSwitchTransitionController =
    staticCompositionLocalOf<ThemeSwitchTransitionController?> { null }

/**
 * 窗口级主题切换快照转场宿主。
 * 空闲时不启用离屏录制也不挂载叠加层；仅在触发主题或液态玻璃切换时抓取 1 帧静态快照，
 * 等底层原子提交并完成首帧重组后执行一次性揭示动画，结束后立即释放快照内存。
 */
@Composable
fun ThemeSwitchSnapshotHost(
    preferredStyle: ThemeSwitchTransitionStyle,
    animationsEnabled: Boolean,
    systemReduceMotion: Boolean = LocalSystemReduceMotion.current,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val effectiveStyle by rememberUpdatedState(
        resolveEffectiveThemeSwitchTransitionStyle(
            preferred = preferredStyle,
            animationsEnabled = animationsEnabled,
            systemReduceMotion = systemReduceMotion,
        )
    )
    val scope: CoroutineScope = rememberCoroutineScope()
    val captureLayer = rememberGraphicsLayer()

    var captureRequested by remember { mutableStateOf(false) }
    var captureReady by remember { mutableStateOf<CompletableDeferred<Unit>?>(null) }
    var snapshotBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var activeStyle by remember { mutableStateOf(ThemeSwitchTransitionStyle.NONE) }
    var anchorInWindow by remember { mutableStateOf(Offset.Unspecified) }
    var hostOriginInWindow by remember { mutableStateOf(Offset.Zero) }
    val revealProgress = remember { Animatable(0f) }
    val revealPath = remember { Path() }
    var activeJob by remember { mutableStateOf<Job?>(null) }
    var activeGeneration by remember { mutableStateOf(0) }

    val controller = remember(scope, captureLayer) {
        lateinit var instance: ThemeSwitchTransitionController
        instance = ThemeSwitchTransitionController { anchor, onCommit ->
            val style = effectiveStyle
            activeGeneration += 1
            val generation = activeGeneration
            if (style == ThemeSwitchTransitionStyle.NONE) {
                activeJob?.cancel()
                snapshotBitmap = null
                captureRequested = false
                captureReady?.cancel()
                captureReady = null
                instance.isTransitionActive = false
                onCommit()
                return@ThemeSwitchTransitionController
            }
            activeJob?.cancel()
            activeJob = scope.launch {
                var committed = false
                try {
                    val deferred = CompletableDeferred<Unit>()
                    captureReady = deferred
                    captureRequested = true
                    deferred.await()
                    captureRequested = false
                    captureReady = null

                    val bitmap = if (
                        !captureLayer.isReleased &&
                        captureLayer.size.width > 0 &&
                        captureLayer.size.height > 0
                    ) {
                        captureLayer.toImageBitmap()
                    } else null

                    if (bitmap == null) {
                        committed = true
                        onCommit()
                        return@launch
                    }

                    snapshotBitmap = bitmap
                    activeStyle = style
                    anchorInWindow = anchor
                    revealProgress.snapTo(0f)
                    instance.isTransitionActive = true

                    committed = true
                    onCommit()

                    // 让新主题与玻璃着色器在静态快照遮罩下完成首帧布局与绘制后再开始揭示。
                    withFrameNanos { }
                    withFrameNanos { }

                    val durationMs = when (style) {
                        ThemeSwitchTransitionStyle.RADIAL_REVEAL -> THEME_SWITCH_RADIAL_REVEAL_DURATION_MS
                        ThemeSwitchTransitionStyle.CROSSFADE -> THEME_SWITCH_CROSSFADE_DURATION_MS
                        ThemeSwitchTransitionStyle.NONE -> 0
                    }
                    if (durationMs > 0) {
                        revealProgress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = durationMs,
                                easing = AppMotionEasing.M1Standard,
                            ),
                        )
                    }
                } finally {
                    if (generation == activeGeneration) {
                        if (!committed) {
                            onCommit()
                        }
                        captureRequested = false
                        captureReady?.cancel()
                        captureReady = null
                        snapshotBitmap = null
                        activeStyle = ThemeSwitchTransitionStyle.NONE
                        instance.isTransitionActive = false
                    }
                }
            }
        }
        instance
    }

    DisposableEffect(Unit) {
        onDispose {
            activeJob?.cancel()
            snapshotBitmap = null
            captureRequested = false
            captureReady?.cancel()
            captureReady = null
            controller.isTransitionActive = false
        }
    }

    CompositionLocalProvider(LocalThemeSwitchTransitionController provides controller) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    hostOriginInWindow = coordinates.positionInWindow()
                },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        if (captureRequested) {
                            captureLayer.record {
                                this@drawWithContent.drawContent()
                            }
                            drawLayer(captureLayer)
                            captureReady?.complete(Unit)
                        } else {
                            drawContent()
                        }
                    },
            ) {
                content()
            }

            val bitmap = snapshotBitmap
            if (bitmap != null) {
                val progress = revealProgress.value
                val style = activeStyle
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val dstSize = IntSize(
                        size.width.roundToInt().coerceAtLeast(1),
                        size.height.roundToInt().coerceAtLeast(1),
                    )
                    val alpha = resolveSnapshotOverlayAlpha(style, progress)
                    if (alpha <= 0f) return@Canvas
                    when (style) {
                        ThemeSwitchTransitionStyle.CROSSFADE -> {
                            drawImage(
                                image = bitmap,
                                dstSize = dstSize,
                                alpha = alpha,
                            )
                        }
                        ThemeSwitchTransitionStyle.RADIAL_REVEAL -> {
                            val localOrigin = if (anchorInWindow == Offset.Unspecified) {
                                Offset(size.width * 0.5f, size.height * 0.5f)
                            } else {
                                Offset(
                                    x = (anchorInWindow.x - hostOriginInWindow.x).coerceIn(0f, size.width),
                                    y = (anchorInWindow.y - hostOriginInWindow.y).coerceIn(0f, size.height),
                                )
                            }
                            val radius = resolveRadialRevealMaxRadius(
                                origin = localOrigin,
                                width = size.width,
                                height = size.height,
                            ) * progress.coerceIn(0f, 1f)
                            if (radius <= 0.5f) {
                                drawImage(
                                    image = bitmap,
                                    dstSize = dstSize,
                                    alpha = alpha,
                                )
                            } else {
                                revealPath.reset()
                                revealPath.addOval(
                                    Rect(
                                        left = localOrigin.x - radius,
                                        top = localOrigin.y - radius,
                                        right = localOrigin.x + radius,
                                        bottom = localOrigin.y + radius,
                                    )
                                )
                                clipPath(revealPath, clipOp = ClipOp.Difference) {
                                    drawImage(
                                        image = bitmap,
                                        dstSize = dstSize,
                                        alpha = alpha,
                                    )
                                }
                            }
                        }
                        ThemeSwitchTransitionStyle.NONE -> Unit
                    }
                }
            }
        }
    }
}
