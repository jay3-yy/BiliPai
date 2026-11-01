package com.android.purebilibili.core.ui.transition

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import com.android.purebilibili.core.ui.LocalSharedTransitionEnabled
import com.android.purebilibili.core.util.CardPositionManager
import com.android.purebilibili.core.util.VideoCardPixelOwner
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

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
        if (shouldRecordNativeVideoCardLayer(exposure, isSharedMorphSourceCard) && !freezeProvider()) {
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
            if (isNativeVideoCardLayerDrawable(layer.size.width, layer.size.height)) {
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
    val capture: () -> Unit,
    val freezeToBitmap: () -> Unit,
)

private val DisabledNativeVideoCardSnapshotController = NativeVideoCardSnapshotController(
    modifier = Modifier,
    coverOverlayModifier = Modifier,
    capture = {},
    freezeToBitmap = {},
)

@Composable
internal fun rememberNativeVideoCardSnapshotController(
    key: Any,
    enabled: Boolean = true,
): NativeVideoCardSnapshotController {
    // Shared card-morph work only, across materials: disabling transitions must also skip
    // layer allocation, recording and capture. Native MD3/Miuix card layout stays untouched.
    if (!enabled || !LocalSharedTransitionEnabled.current) {
        return DisabledNativeVideoCardSnapshotController
    }
    val layer = rememberNativeVideoCardLayer()
    val coverOverlayLayer = rememberNativeVideoCardLayer()
    val captureScope = rememberCoroutineScope()
    val captureAllowed = rememberVideoCardPixelCaptureAllowed()
    val backgroundState = LocalVideoCardTransitionBackgroundState.current
    val freezeState = remember(key) { mutableStateOf(false) }
    val bvid = (key as? String).orEmpty()
    val sourceRoute = LocalVideoCardSharedElementSourceRoute.current
    return NativeVideoCardSnapshotController(
        modifier = Modifier.recordNativeVideoCardLayer(
            layer = layer,
            freezeProvider = {
                freezeState.value && CardPositionManager.isNativeVideoCardLayerCurrentOwner(layer)
            },
            bvid = bvid,
            sourceRoute = sourceRoute,
            enabled = enabled,
            landingFeedbackEnabled = true,
        ),
        coverOverlayModifier = Modifier.recordNativeVideoCardLayer(
            layer = coverOverlayLayer,
            freezeProvider = {
                freezeState.value &&
                    CardPositionManager.isNativeCoverOverlayLayerCurrentOwner(coverOverlayLayer)
            },
            bvid = bvid,
            sourceRoute = sourceRoute,
            enabled = enabled,
        ),
        capture = {
            freezeState.value = true
            captureNativeVideoCardImage(layer)
            captureNativeCoverOverlayLayer(coverOverlayLayer)
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
        },
        freezeToBitmap = {
            val expectedSourceKey = CardPositionManager.lastClickedVideoSourceKey
            captureScope.launch(start = CoroutineStart.UNDISPATCHED) {
                captureNativeVideoCardBitmap(
                    layer = layer,
                    expectedSourceKey = expectedSourceKey,
                )
            }
        },
    )
}
