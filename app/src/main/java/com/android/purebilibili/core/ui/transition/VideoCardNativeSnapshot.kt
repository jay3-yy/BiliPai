package com.android.purebilibili.core.ui.transition

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalGraphicsContext
import com.android.purebilibili.core.ui.LocalSharedTransitionEnabled
import com.android.purebilibili.core.util.CardPositionManager
import com.android.purebilibili.core.util.VideoCardPixelOwner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield

private const val NATIVE_CARD_RECORD_WAIT_MILLIS = 64L

private class NativeVideoCardCaptureRequest {
    var started = false
    var recorded: CompletableDeferred<Unit>? = null
}

// 系统窗口在应用内使用共享容器回退时仍需完整卡片像素，特别是部分可见的来源。
internal val LocalSystemVideoCardCaptureEnabled = compositionLocalOf { false }

@Composable
internal fun videoCardSourceCaptureEnabled(): Boolean =
    LocalSharedTransitionEnabled.current || LocalSystemVideoCardCaptureEnabled.current

internal const val VIDEO_CARD_FLYING_OVERLAY_COVER_DEPTH = 0.001f

/** Draw-owned latch; survives modifier rebuilding without subscribing composition. */
private class NativeVideoCardCoverLatch {
    var covered = false
}

/**
 * Click pre-arms OPENING before NavDisplay mounts the destination. Whether the flying overlay
 * has grown enough to cover the source slot.
 *
 * 保留给「列表卡内容显隐」这类**纯展示**判定（`VideoCardScrollLiteVisualPolicy`）：它们
 * 只关心相位与进度，不参与卡槽占位；卡槽占位一律走 [shouldHideStationarySourceCard]
 * 的 exposure + 点击锁存路径，避免两套判据互相打架。
 */
internal fun isVideoCardFlyingOverlayCoveringSource(
    phase: VideoCardTransitionBackgroundPhase,
    depthProgress: Float,
    isReturnGestureInProgress: Boolean,
): Boolean {
    if (isReturnGestureInProgress) return true
    return when (phase) {
        VideoCardTransitionBackgroundPhase.OPENING,
        VideoCardTransitionBackgroundPhase.RETURNING,
        -> depthProgress > VIDEO_CARD_FLYING_OVERLAY_COVER_DEPTH
        VideoCardTransitionBackgroundPhase.HELD -> true
        VideoCardTransitionBackgroundPhase.IDLE -> false
    }
}

/**
 * 单一判据：源卡槽是否必须空出。
 *
 * 只认 [VideoCardTransitionExposure]（外加返回手势标志），**不再读逐帧 depth 阈值**。
 *
 * 原因：depth 由 `MiuixVideoCardTransitionProgress.visualDepth` 提供，而后者在被覆盖的父
 * scope 分支会强制 `return 1f`，与飞行 overlay 自身的 depth 语义不同。用它做阈值判定，
 * 会让「overlay 是否已覆盖卡槽」在不同调用层得到不同答案——卡槽先空出、又被填回，
 * 就是返回途中最明显的跳动来源。
 *
 * exposure 是宿主已经解析过的粗粒度相位，两侧读到的是同一份事实源；`Opening` 期间
 * 由调用方用**点击锁存**承接「已覆盖」语义（见 [shouldHideStationarySourceCard]），
 * 锁存只单向切换，不与 depth 抖动耦合。
 */
internal fun shouldHideStationarySourceCard(
    isSharedMorphSourceCard: Boolean,
    exposure: VideoCardTransitionExposure?,
    isCoveredLatch: Boolean,
): Boolean {
    if (!isSharedMorphSourceCard) return false
    // 深度可能先到端点，导航 owner 才释放飞行 entry：整段保持空槽，直到真正 Idle。
    return when (exposure) {
        VideoCardTransitionExposure.SettledHidden,
        VideoCardTransitionExposure.BackPreview,
        VideoCardTransitionExposure.Returning,
        VideoCardTransitionExposure.Restoring -> true
        VideoCardTransitionExposure.Idle -> false
        // Opening / 未接线：用点击锁存，避免 depth 抖动导致卡槽反复空出/填回。
        VideoCardTransitionExposure.Opening, null -> isCoveredLatch
    }
}

/**
 * 点击锁存推进：只在 overlay **确实已覆盖源位**之后置位，回到 [VideoCardTransitionExposure.Idle] 清除。
 *
 * - `Opening` **不置位**：点击会提前 arm OPENING，但此时飞行 entry 尚未长出，置位会让卡槽
 *   先空一块（即「点击瞬间的卡片空洞」）。等相位离开 `Opening`（`SettledHidden` 及返回各相位）
 *   才证明整卡已经盖住源位。
 * - `SettledHidden / BackPreview / Returning / Restoring` 置位，并在其间保持。
 * - 未接线（null）保持现状，不凭空改写。
 * - 单向：中途 depth/settle 抖动不会撤销已置位的锁存，只在真正 Idle 回落。
 */
internal fun advanceSourceCardCoverLatch(
    latch: Boolean,
    exposure: VideoCardTransitionExposure?,
    isReturnGestureInProgress: Boolean = false,
): Boolean = when (exposure) {
    null -> latch
    VideoCardTransitionExposure.Idle -> false
    // 提前 arm 的 OPENING 不算「已覆盖」；但真正的预测返回手势接管时立即置位。
    VideoCardTransitionExposure.Opening -> latch || isReturnGestureInProgress
    else -> true
}


internal fun isRecordedNativeCardSource(
    bvid: String,
    sourceRoute: String?,
    activeSourceKey: String?,
    recordedSourceKey: String? = CardPositionManager.lastClickedVideoSourceKey,
    ownsRecordedLayer: Boolean = true,
): Boolean {
    if (!ownsRecordedLayer) return false
    val clicked = recordedSourceKey ?: return false
    // A click updates the pending key before navigation replaces the previous HELD session.
    if (activeSourceKey != clicked) return false
    val id = bvid.trim()
    if (id.isEmpty()) return false
    val route = normalizeSharedElementSourceRoute(sourceRoute) ?: return false
    return clicked == "$route:$id"
}

internal fun isNativeVideoCardLayerDrawable(widthPx: Int, heightPx: Int): Boolean =
    widthPx > 1 && heightPx > 1

/** Only the covered transition source can reuse frozen pixels; visible related cards keep recording. */
internal fun shouldRecordNativeVideoCardLayer(
    exposure: VideoCardTransitionExposure?,
    isSharedMorphSourceCard: Boolean,
): Boolean {
    if (!isSharedMorphSourceCard) return true
    return when (exposure) {
        VideoCardTransitionExposure.SettledHidden,
        VideoCardTransitionExposure.BackPreview,
        VideoCardTransitionExposure.Returning,
        VideoCardTransitionExposure.Restoring,
        -> false
        VideoCardTransitionExposure.Idle,
        VideoCardTransitionExposure.Opening,
        null,
        -> true
    }
}

/**
 * Records the stationary list card into a graphics layer so a click can freeze native pixels
 * instead of reconstructing title/spacing on the flying detail entry.
 *
 * The recorded layer can be drawn directly for ordinary cards via hardware-accelerated drawLayer.
 * Sources that leave composition, such as the now-playing bar, additionally freeze this layer
 * to a stable bitmap at click time.
 * While the flying overlay covers this card, skip drawing at the list coordinates.
 *
 * Unfrozen draws execute the content exactly once: it is replayed from the freshly recorded
 * layer instead of being drawn a second time. Recording per draw keeps click-time pixels
 * current; blitting the layer keeps the feed's steady-state draw cost single-pass.
 *
 * Recording stops entirely once the card is covered ([shouldRecordNativeVideoCardLayer]):
 * re-recording a fully hidden card every frame is the source page's single largest
 * avoidable cost during the detail round trip.
 */
@Composable
internal fun Modifier.recordNativeVideoCardLayer(
    layer: GraphicsLayer?,
    freezeProvider: () -> Boolean,
    bvid: String = "",
    sourceRoute: String?,
    enabled: Boolean = true,
    landingFeedbackEnabled: Boolean = false,
    onRecorded: (() -> Unit)? = null,
): Modifier {
    if (!enabled || layer == null) return this
    val bgState = LocalVideoCardTransitionBackgroundState.current
    val coverLatch = remember(layer, bvid, sourceRoute) { NativeVideoCardCoverLatch() }
    val sourceExposure = remember(bgState) {
        derivedStateOf(structuralEqualityPolicy()) { bgState.exposureProvider() }
    }
    val sourceReturnGesture = remember(bgState) {
        derivedStateOf(structuralEqualityPolicy()) {
            bgState.isReturnGestureInProgressProvider() || bgState.isGestureRestoreInProgressProvider()
        }
    }
    return videoCardLandingFeedback(
        layer = layer,
        bvid = bvid,
        sourceRoute = sourceRoute,
        enabled = landingFeedbackEnabled,
    ).drawWithContent {
        // Read the latch in draw. Waiting for recomposition after a click can otherwise
        // overwrite the frozen first-click card after OPENING has hidden its info band.
        val isSharedMorphSourceCard = isRecordedNativeCardSource(
            bvid = bvid,
            sourceRoute = sourceRoute,
            activeSourceKey = bgState.sourceKeyProvider(),
            ownsRecordedLayer = CardPositionManager.isNativeVideoCardLayerCurrentOwner(layer) ||
                CardPositionManager.isNativeCoverOverlayLayerCurrentOwner(layer),
        )
        // Neighbour cards must not subscribe to the transition driver. They still record
        // real content invalidations (images/statistics), rather than freezing stale pixels.
        val exposure = if (isSharedMorphSourceCard) sourceExposure.value else null
        coverLatch.covered = advanceSourceCardCoverLatch(
            latch = coverLatch.covered && isSharedMorphSourceCard,
            exposure = exposure,
            isReturnGestureInProgress = isSharedMorphSourceCard && sourceReturnGesture.value,
        )
        val frozen = freezeProvider()
        if (shouldRecordNativeVideoCardLayer(exposure, isSharedMorphSourceCard) && !frozen) {
            layer.record {
                this@drawWithContent.drawContent()
            }
            onRecorded?.invoke()
        }
        val hide = shouldHideStationarySourceCard(
            isSharedMorphSourceCard = isSharedMorphSourceCard,
            exposure = exposure,
            isCoveredLatch = coverLatch.covered,
        )
        if (!hide) {
            // 系统窗口转场已经持有独立位图，列表恢复直接画当前内容。
            // 冻结的统计/时长子图层不再回放，避免恢复首帧依赖已被重绘或释放的子节点。
            if (frozen && !isSharedMorphSourceCard) {
                drawContent()
            } else if (isNativeVideoCardLayerDrawable(layer.size.width, layer.size.height)) {
                drawLayer(layer)
            } else {
                drawContent()
            }
        }
    }
}

internal fun captureNativeVideoCardImage(
    layer: GraphicsLayer,
) {
    CardPositionManager.recordNativeCardLayer(
        layer.takeIf {
            isNativeVideoCardLayerDrawable(it.size.width, it.size.height)
        },
    )
}

internal fun captureNativeCoverOverlayLayer(
    layer: GraphicsLayer,
) {
    CardPositionManager.recordNativeCoverOverlayLayer(
        layer.takeIf {
            isNativeVideoCardLayerDrawable(it.size.width, it.size.height)
        },
    )
}

internal suspend fun captureNativeVideoCardBitmap(
    layer: GraphicsLayer,
    expectedSourceKey: String?,
) {
    if (!isNativeVideoCardLayerDrawable(layer.size.width, layer.size.height)) return
    if (!CardPositionManager.isNativeVideoCardLayerCurrentOwner(layer)) return
    val owner = CardPositionManager.videoCardPixelOwnerFor(expectedSourceKey) ?: return
    CardPositionManager.recordNativeCardBitmap(
        bitmap = layer.toImageBitmap(),
        expectedOwner = owner,
    )
}

/** 冻结已保留的来源图层；开始和完成时均只认同一次点击身份。 */
internal suspend fun captureWholeCardReturnBitmap(layer: GraphicsLayer, owner: VideoCardPixelOwner) {
    if (!CardPositionManager.isActiveVideoCardPixelOwner(owner)) return
    if (layer.isReleased) return
    if (!isNativeVideoCardLayerDrawable(layer.size.width, layer.size.height)) return
    if (!CardPositionManager.isNativeVideoCardLayerCurrentOwner(layer)) return
    val bitmap = layer.toImageBitmap()
    currentCoroutineContext().ensureActive()
    if (CardPositionManager.isNativeVideoCardLayerCurrentOwner(layer)) {
        CardPositionManager.recordWholeCardReturnBitmap(bitmap, owner)
    }
}

@Composable
internal fun rememberNativeVideoCardLayer() = rememberGraphicsLayer()

internal class NativeVideoCardSnapshotController(
    val modifier: Modifier,
    val coverOverlayModifier: Modifier,
    val freezeToBitmap: () -> Unit,
    private val captureAndContinue: (() -> Unit) -> Unit,
) {
    /** Record the current card, freeze its pixels, then continue navigation. */
    fun captureThen(onClick: () -> Unit) {
        captureAndContinue(onClick)
    }
}

private val DisabledNativeVideoCardSnapshotController = NativeVideoCardSnapshotController(
    modifier = Modifier,
    coverOverlayModifier = Modifier,
    freezeToBitmap = {},
    captureAndContinue = { it() },
)

private fun releaseOnDemandGraphicsLayer(
    graphicsContext: androidx.compose.ui.graphics.GraphicsContext,
    layer: GraphicsLayer?,
) {
    if (layer == null || layer.isReleased) return
    if (layer.size.width > 1 || layer.size.height > 1) {
        layer.record(
            density = androidx.compose.ui.unit.Density(1f),
            layoutDirection = androidx.compose.ui.unit.LayoutDirection.Ltr,
            size = androidx.compose.ui.unit.IntSize(1, 1),
        ) {}
    }
    graphicsContext.releaseGraphicsLayer(layer)
}

@Composable
internal fun rememberNativeVideoCardSnapshotController(
    key: Any,
    enabled: Boolean = true,
): NativeVideoCardSnapshotController {
    // 玻璃、MD3 和 Miuix 共用像素捕获门控，各材质布局独立。
    if (!enabled || !videoCardSourceCaptureEnabled()) {
        return DisabledNativeVideoCardSnapshotController
    }
    val graphicsContext = LocalGraphicsContext.current
    val layerState = remember(key, graphicsContext) { mutableStateOf<GraphicsLayer?>(null) }
    val coverOverlayLayerState = remember(key, graphicsContext) { mutableStateOf<GraphicsLayer?>(null) }
    val captureScope = rememberCoroutineScope()
    val captureAllowed = rememberVideoCardPixelCaptureAllowed()
    val backgroundState = LocalVideoCardTransitionBackgroundState.current
    val freezeState = remember(key) { mutableStateOf(false) }
    val recordingState = remember(key) { mutableStateOf(false) }
    val captureRequest = remember(key) { NativeVideoCardCaptureRequest() }
    val bvid = (key as? String).orEmpty()
    val sourceRoute = LocalVideoCardSharedElementSourceRoute.current

    fun releaseCardLayers() {
        val currentLayer = layerState.value
        val currentCoverLayer = coverOverlayLayerState.value
        layerState.value = null
        coverOverlayLayerState.value = null
        releaseOnDemandGraphicsLayer(graphicsContext, currentLayer)
        releaseOnDemandGraphicsLayer(graphicsContext, currentCoverLayer)
    }

    DisposableEffect(key, graphicsContext) {
        onDispose { releaseCardLayers() }
    }

    val activeLayer = layerState.value
    val activeCoverOverlayLayer = coverOverlayLayerState.value
    LaunchedEffect(activeLayer, activeCoverOverlayLayer) {
        if (activeLayer == null && activeCoverOverlayLayer == null) return@LaunchedEffect
        snapshotFlow {
            (activeLayer != null && CardPositionManager.isNativeVideoCardLayerCurrentOwner(activeLayer)) ||
                (activeCoverOverlayLayer != null &&
                    CardPositionManager.isNativeCoverOverlayLayerCurrentOwner(activeCoverOverlayLayer))
        }.collect { ownsNativeResources ->
            if (!ownsNativeResources && freezeState.value) {
                freezeState.value = false
                recordingState.value = false
            }
            if (!ownsNativeResources && !recordingState.value && !captureRequest.started) {
                captureRequest.recorded = null
                releaseCardLayers()
            }
        }
    }
    fun startWholeCardReturnCapture(layer: GraphicsLayer) {
        val expectedSourceKey = CardPositionManager.lastClickedVideoSourceKey
        val owner = CardPositionManager.videoCardPixelOwnerFor(expectedSourceKey)
        if (owner != null &&
            CardPositionManager.lastClickedVideoSourceChromeSnapshot?.isNowPlayingBar != true
        ) {
            captureScope.launch {
                captureVideoCardPixelsAfterEntry(
                    phaseProvider = backgroundState.phaseProvider,
                    isReturnGestureInProgress = {
                        backgroundState.isReturnGestureInProgressProvider() ||
                            backgroundState.isGestureRestoreInProgressProvider()
                    },
                    ownsCapture = {
                        !layer.isReleased &&
                            CardPositionManager.isActiveVideoCardPixelOwner(owner) &&
                            CardPositionManager.isNativeVideoCardLayerCurrentOwner(layer)
                    },
                    captureAllowed = { captureAllowed.value },
                    entryWorkReleased = { owner.entryWorkReleased },
                    capture = { captureWholeCardReturnBitmap(layer, owner) },
                )
            }
        }
    }

    return NativeVideoCardSnapshotController(
        modifier = Modifier
            .pointerInput(key, graphicsContext) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    // 利用按下到抬手之间的 60–120ms 窗口提前录制卡片图层，抬手点击当帧零延迟起飞。
                    if (!captureRequest.started && !freezeState.value) {
                        if (layerState.value?.takeUnless { it.isReleased } == null) {
                            layerState.value = graphicsContext.createGraphicsLayer()
                        }
                        if (coverOverlayLayerState.value?.takeUnless { it.isReleased } == null) {
                            coverOverlayLayerState.value = graphicsContext.createGraphicsLayer()
                        }
                        recordingState.value = true
                    }
                    val up = waitForUpOrCancellation()
                    if (up == null) {
                        if (!captureRequest.started && !freezeState.value) {
                            recordingState.value = false
                            releaseCardLayers()
                        }
                    } else {
                        captureScope.launch {
                            kotlinx.coroutines.delay(160L)
                            if (!captureRequest.started && !freezeState.value) {
                                recordingState.value = false
                                releaseCardLayers()
                            }
                        }
                    }
                }
            }
            .recordNativeVideoCardLayer(
                layer = activeLayer,
                freezeProvider = {
                    val currentLayer = layerState.value
                    freezeState.value && currentLayer != null &&
                        CardPositionManager.isNativeVideoCardLayerCurrentOwner(currentLayer)
                },
                bvid = bvid,
                sourceRoute = sourceRoute,
                enabled = recordingState.value || freezeState.value,
                landingFeedbackEnabled = true,
                onRecorded = { captureRequest.recorded?.complete(Unit) },
            ),
        coverOverlayModifier = Modifier.recordNativeVideoCardLayer(
            layer = activeCoverOverlayLayer,
            freezeProvider = {
                val currentCoverLayer = coverOverlayLayerState.value
                freezeState.value && currentCoverLayer != null &&
                    CardPositionManager.isNativeCoverOverlayLayerCurrentOwner(currentCoverLayer)
            },
            bvid = bvid,
            sourceRoute = sourceRoute,
            enabled = recordingState.value || freezeState.value,
        ),
        freezeToBitmap = {
            val expectedSourceKey = CardPositionManager.lastClickedVideoSourceKey
            val targetLayer = layerState.value ?: return@NativeVideoCardSnapshotController
            captureScope.launch(start = CoroutineStart.UNDISPATCHED) {
                captureNativeVideoCardBitmap(
                    layer = targetLayer,
                    expectedSourceKey = expectedSourceKey,
                )
            }
        },
        captureAndContinue = { onClick ->
            if (!captureRequest.started) {
                val prearmedLayer = layerState.value?.takeIf {
                    !it.isReleased && isNativeVideoCardLayerDrawable(it.size.width, it.size.height)
                }
                val prearmedCoverLayer = coverOverlayLayerState.value?.takeUnless { it.isReleased }
                if (prearmedLayer != null && prearmedCoverLayer != null) {
                    // 按下预热已录好像素：当帧同步冻结并直接触发导航，消除 1~2 帧等待延迟。
                    captureRequest.started = true
                    try {
                        freezeState.value = true
                        captureNativeVideoCardImage(prearmedLayer)
                        captureNativeCoverOverlayLayer(prearmedCoverLayer)
                        recordingState.value = false
                        startWholeCardReturnCapture(prearmedLayer)
                        onClick()
                    } finally {
                        captureRequest.started = false
                        captureRequest.recorded = null
                        val ownsAfterClick =
                            CardPositionManager.isNativeVideoCardLayerCurrentOwner(prearmedLayer) ||
                                CardPositionManager.isNativeCoverOverlayLayerCurrentOwner(prearmedCoverLayer)
                        if (!ownsAfterClick) {
                            freezeState.value = false
                            recordingState.value = false
                            releaseCardLayers()
                        }
                    }
                } else {
                    captureRequest.started = true
                    val cardRecorded = CompletableDeferred<Unit>()
                    captureRequest.recorded = cardRecorded
                    captureScope.launch {
                        val layer = layerState.value?.takeUnless { it.isReleased }
                            ?: graphicsContext.createGraphicsLayer().also { layerState.value = it }
                        val coverOverlayLayer = coverOverlayLayerState.value?.takeUnless { it.isReleased }
                            ?: graphicsContext.createGraphicsLayer().also { coverOverlayLayerState.value = it }
                        freezeState.value = false
                        recordingState.value = true
                        withTimeoutOrNull(NATIVE_CARD_RECORD_WAIT_MILLIS) {
                            cardRecorded.await()
                        }
                        yield()
                        freezeState.value = true
                        captureNativeVideoCardImage(layer)
                        captureNativeCoverOverlayLayer(coverOverlayLayer)
                        recordingState.value = false
                        startWholeCardReturnCapture(layer)
                        try {
                            onClick()
                        } finally {
                            captureRequest.started = false
                            captureRequest.recorded = null
                            val ownsAfterClick =
                                CardPositionManager.isNativeVideoCardLayerCurrentOwner(layer) ||
                                    CardPositionManager.isNativeCoverOverlayLayerCurrentOwner(coverOverlayLayer)
                            if (!ownsAfterClick) {
                                freezeState.value = false
                                recordingState.value = false
                                releaseCardLayers()
                            }
                        }
                    }
                }
            }
        },
    )
}
