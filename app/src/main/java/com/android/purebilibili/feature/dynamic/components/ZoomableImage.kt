package com.android.purebilibili.feature.dynamic.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.asPainter
import kotlin.math.abs
import com.android.purebilibili.core.ui.LocalSystemReduceMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal const val ZOOMABLE_IMAGE_TAG = "zoomable_image"

/**
 * 可缩放的图片组件
 * - 支持双指缩放
 * - 支持双击放大
 * - 支持长图滑动
 * - 自动处理边界限制
 */
@Composable
fun ZoomableImage(
    model: Any?,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    onZoomChange: (Float) -> Unit = {},
    // Reports image bounds in this viewport, before any parent layer transforms.
    onDisplayRectChange: (Rect?) -> Unit = {},
    onVerticalDismissDragStart: () -> Unit = {},
    onVerticalDismissDrag: (Offset) -> Unit = {},
    onVerticalDismissDragEnd: (Float) -> Unit = {},
    onVerticalDismissDragCancel: () -> Unit = {},
    onExtremeAspectRatioDetected: () -> Unit = {},
    onLongPress: () -> Unit = {},
    onClick: () -> Unit = {},
    resetZoomTrigger: Int = 0,
    gesturesEnabled: Boolean = true,
    displayRectTrackingEnabled: Boolean = true,
    originalImageUrl: String? = null,
    originalQuality: Boolean = false,
    publicContent: Boolean = false,
    initialImage: coil3.Image? = null,
    onImageDrawn: () -> Unit = {},
    onImageError: () -> Unit = {},
) {
    // Gesture coroutines outlive recomposition; always use the current host callbacks.
    val latestOnZoomChange by rememberUpdatedState(onZoomChange)
    val latestOnDisplayRectChange by rememberUpdatedState(onDisplayRectChange)
    val latestOnDragStart by rememberUpdatedState(onVerticalDismissDragStart)
    val latestOnDrag by rememberUpdatedState(onVerticalDismissDrag)
    val latestOnDragEnd by rememberUpdatedState(onVerticalDismissDragEnd)
    val latestOnDragCancel by rememberUpdatedState(onVerticalDismissDragCancel)
    val latestOnLongPress by rememberUpdatedState(onLongPress)
    val latestOnClick by rememberUpdatedState(onClick)
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    
    // 图片原始尺寸
    var imageSize by remember { mutableStateOf(initialImage?.let { IntSize(it.width, it.height) } ?: IntSize.Zero) }
    val imageContext = LocalContext.current
    val initialPainter = remember(initialImage, imageContext) { initialImage?.asPainter(imageContext) }
    val latestOnImageDrawn by rememberUpdatedState(onImageDrawn)
    var drawableReady by remember { mutableStateOf(false) }
    // 容器尺寸
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    // The preview surface moves/scales while dragging. Keep dismiss deltas in the root frame,
    // rather than feeding its own animated local coordinates back into the next pointer event.
    val inputCoordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    LaunchedEffect(containerSize, imageSize, scale, offsetX, offsetY, displayRectTrackingEnabled) {
        if (displayRectTrackingEnabled) {
            latestOnDisplayRectChange(
                resolveZoomableImageLocalDisplayRect(imageSize, containerSize, scale, offsetX, offsetY)
            )
        }
    }
    
    val motionScope = rememberCoroutineScope()
    var motionJob by remember { mutableStateOf<Job?>(null) }
    val reduceMotion by rememberUpdatedState(LocalSystemReduceMotion.current)
    val density by rememberUpdatedState(LocalDensity.current.density)

    fun stopMotion() {
        motionJob?.cancel()
        motionJob = null
    }

    fun animateTransform(targetScale: Float, targetOffset: Offset, durationMillis: Int) {
        stopMotion()
        val startScale = scale
        val startOffset = Offset(offsetX, offsetY)
        motionJob = motionScope.launch {
            // A single progress drives geometry so zoom and pan cannot finish separately.
            Animatable(0f).animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = if (reduceMotion) 0 else durationMillis,
                    easing = CubicBezierEasing(0f, 0f, 0.58f, 1f),
                ),
            ) {
                scale = startScale + (targetScale - startScale) * value
                val bounds = resolveZoomableImagePanBounds(imageSize, containerSize, scale)
                val offset = clampZoomableImageOffset(
                    startOffset + (targetOffset - startOffset) * value,
                    bounds,
                )
                offsetX = offset.x
                offsetY = offset.y
                latestOnZoomChange(scale)
            }
        }
    }

    fun flingImage(velocity: Offset) {
        stopMotion()
        if (reduceMotion || scale <= 1.01f) return
        val bounds = resolveZoomableImagePanBounds(imageSize, containerSize, scale)
        val initialVelocity = resolveZoomableImageFlingVelocity(
            velocity, Offset(offsetX, offsetY), bounds, density,
        )
        if (initialVelocity == Offset.Zero) return
        motionJob = motionScope.launch {
            AnimationState(
                initialValue = Offset(offsetX, offsetY),
                typeConverter = Offset.VectorConverter,
                initialVelocity = initialVelocity,
            ).animateDecay(
                animationSpec = exponentialDecay(
                    frictionMultiplier = 3f,
                    absVelocityThreshold = 10f * density,
                ),
            ) {
                val offset = clampZoomableImageOffset(value, bounds)
                val stoppedAtBoundary = offset == Offset(offsetX, offsetY) && offset != value
                offsetX = offset.x
                offsetY = offset.y
                if (stoppedAtBoundary) cancelAnimation()
            }
        }
    }

    // Offscreen pages and a closing preview must not keep moving behind the Hero flight.
    LaunchedEffect(gesturesEnabled, displayRectTrackingEnabled, reduceMotion) {
        if (!gesturesEnabled || !displayRectTrackingEnabled || reduceMotion) stopMotion()
    }
    LaunchedEffect(containerSize, imageSize) {
        stopMotion()
        val offset = clampZoomableImageOffset(
            Offset(offsetX, offsetY),
            resolveZoomableImagePanBounds(imageSize, containerSize, scale),
        )
        offsetX = offset.x
        offsetY = offset.y
    }

    // External exit preparation shares ownership with double-tap and fling animations.
    LaunchedEffect(resetZoomTrigger) {
        if (resetZoomTrigger > 0 && scale > 1f) {
            animateTransform(1f, Offset.Zero, 180)
        }
    }

    fun onDoubleTap(tapOffset: Offset) {
        if (imageSize.width <= 0 || imageSize.height <= 0 ||
            containerSize.width <= 0 || containerSize.height <= 0
        ) return
        val targetScale = if (scale > 1.01f) 1f else resolveZoomableImageScaleLimits(
            imageWidth = imageSize.width,
            imageHeight = imageSize.height,
            containerWidth = containerSize.width,
            containerHeight = containerSize.height,
        ).doubleTapScale
        val center = Offset(containerSize.width / 2f, containerSize.height / 2f)
        val targetOffset = if (targetScale == 1f) Offset.Zero else clampZoomableImageOffset(
            (center - tapOffset) * (targetScale - 1f),
            resolveZoomableImagePanBounds(imageSize, containerSize, targetScale),
        )
        animateTransform(targetScale, targetOffset, 250)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .onGloballyPositioned { inputCoordinates[0] = it }
            .pointerInput(gesturesEnabled) {
                if (!gesturesEnabled) return@pointerInput
                detectTapGestures(
                    onDoubleTap = { onDoubleTap(it) },
                    onLongPress = { latestOnLongPress() },
                    onTap = { latestOnClick() }
                )
            }
            .pointerInput(gesturesEnabled) {
                if (!gesturesEnabled) return@pointerInput
                // 手势监听：缩放 + 拖拽
                awaitEachGesture {
                    var zoom = 1f
                    var pan = Offset.Zero
                    var pastTouchSlop = false
                    val touchSlop = viewConfiguration.touchSlop
                    var isMultiTouch = false
                    var gestureMode = ZoomableImageGestureMode.UNDECIDED
                    var verticalDismissStarted = false
                    var gestureCanceled = false
                    // 竖滑松手速度（px/s）：EMA 平滑瞬时速度，供回位动画延续手势动量。
                    var lastVerticalDragTimeMs = 0L
                    var verticalDragVelocityY = 0f
                    
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    stopMotion()
                    val panVelocityTracker = VelocityTracker()
                    fun rootPosition(position: Offset): Offset = inputCoordinates[0]
                        ?.takeIf { it.isAttached }?.localToRoot(position) ?: position
                    panVelocityTracker.addPosition(down.uptimeMillis, rootPosition(down.position))
                    val rootPositions = mutableMapOf(down.id to rootPosition(down.position))
                    
                    try {
                        do {
                            // Decide the image gesture before Pager/tap handlers process movement.
                            // Horizontal/undecided movement remains unconsumed for the Pager.
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (!isMultiTouch && event.changes.size == 1) {
                                val change = event.changes.first()
                                panVelocityTracker.addPosition(change.uptimeMillis, rootPosition(change.position))
                            }
                            if (event.changes.none { it.pressed }) {
                                if (verticalDismissStarted) event.changes.forEach { it.consume() }
                                break
                            }
                            // Once vertical dismissal owns this stream, another recognizer's
                            // consumed flag cannot revoke it; actual cancellation uses finally.
                            val canceled = !verticalDismissStarted && event.changes.any { it.isConsumed }
                            if (canceled) {
                                gestureCanceled = true
                                break
                            }

                            if (event.changes.size > 1) {
                                isMultiTouch = true
                            }

                            val currentRootPositions = event.changes.filter { it.pressed }
                                .associate { it.id to rootPosition(it.position) }
                            val rootDeltas = currentRootPositions.mapNotNull { (id, position) ->
                                rootPositions[id]?.let { position - it }
                            }
                            val rootPanChange = if (rootDeltas.isEmpty()) Offset.Zero else
                                rootDeltas.fold(Offset.Zero) { sum, delta -> sum + delta } / rootDeltas.size.toFloat()
                            rootPositions.clear()
                            rootPositions.putAll(currentRootPositions)
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()

                            if (!pastTouchSlop || gestureMode == ZoomableImageGestureMode.UNDECIDED) {
                                zoom *= zoomChange
                                pan += rootPanChange

                                val centroidSize = event.calculateCentroidSize(useCurrent = false)
                                val zoomMotion = abs(1 - zoom) * centroidSize
                                val panMotion = pan.getDistance()

                                if (zoomMotion > touchSlop || panMotion > touchSlop) {
                                    pastTouchSlop = true
                                    gestureMode = resolveZoomableImageGestureMode(
                                        isMultiTouch = isMultiTouch,
                                        scale = scale,
                                        panX = pan.x,
                                        panY = pan.y
                                    )

                                    if (gestureMode == ZoomableImageGestureMode.VERTICAL_DISMISS) {
                                        verticalDismissStarted = true
                                        latestOnDragStart()
                                    }
                                }
                            }

                            if (isMultiTouch && gestureMode != ZoomableImageGestureMode.IMAGE_INTERACTION) {
                                if (verticalDismissStarted) {
                                    verticalDismissStarted = false
                                    latestOnDragCancel()
                                }
                                gestureMode = ZoomableImageGestureMode.IMAGE_INTERACTION
                            }

                            if (pastTouchSlop) {
                                when (gestureMode) {
                                    ZoomableImageGestureMode.VERTICAL_DISMISS -> {
                                        if (rootPanChange != Offset.Zero) {
                                            latestOnDrag(rootPanChange)
                                        }
                                        val moveTimeMs = event.changes.firstOrNull()?.uptimeMillis ?: 0L
                                        if (lastVerticalDragTimeMs != 0L && moveTimeMs > lastVerticalDragTimeMs) {
                                            val instantVelocityY = rootPanChange.y / (moveTimeMs - lastVerticalDragTimeMs) * 1000f
                                            verticalDragVelocityY = if (verticalDragVelocityY == 0f) {
                                                instantVelocityY
                                            } else {
                                                verticalDragVelocityY * 0.6f + instantVelocityY * 0.4f
                                            }
                                        }
                                        lastVerticalDragTimeMs = moveTimeMs
                                        event.changes.forEach {
                                            if (it.position != it.previousPosition) {
                                                it.consume()
                                            }
                                        }
                                    }
                                    ZoomableImageGestureMode.IMAGE_INTERACTION -> {
                                        val centroid = event.calculateCentroid(useCurrent = false)
                                        if (zoomChange != 1f || panChange != Offset.Zero) {
                                            val oldScale = scale
                                            val maxScale = resolveZoomableImageScaleLimits(
                                                imageWidth = imageSize.width,
                                                imageHeight = imageSize.height,
                                                containerWidth = containerSize.width,
                                                containerHeight = containerSize.height
                                            ).maxScale
                                            scale = (scale * zoomChange).coerceIn(1f, maxScale)

                                            if (oldScale != scale) {
                                                val zoomFactor = scale / oldScale
                                                val dx = (1 - zoomFactor) * (centroid.x - containerSize.width / 2f - offsetX)
                                                val dy = (1 - zoomFactor) * (centroid.y - containerSize.height / 2f - offsetY)
                                                offsetX += dx
                                                offsetY += dy
                                            }

                                            offsetX += panChange.x
                                            offsetY += panChange.y

                                            val offset = clampZoomableImageOffset(
                                                Offset(offsetX, offsetY),
                                                resolveZoomableImagePanBounds(imageSize, containerSize, scale),
                                            )
                                            offsetX = offset.x
                                            offsetY = offset.y

                                            latestOnZoomChange(scale)
                                        }

                                        if (isMultiTouch || scale > 1.01f) {
                                            event.changes.forEach {
                                                if (it.position != it.previousPosition) {
                                                    it.consume()
                                                }
                                            }
                                        }
                                    }
                                    ZoomableImageGestureMode.HORIZONTAL_PAGER,
                                    ZoomableImageGestureMode.UNDECIDED -> Unit
                                }
                            }
                            val ownsMovement = gestureMode == ZoomableImageGestureMode.VERTICAL_DISMISS ||
                                (gestureMode == ZoomableImageGestureMode.IMAGE_INTERACTION &&
                                    (isMultiTouch || scale > 1.01f))
                            val finalEvent = awaitPointerEvent(PointerEventPass.Final)
                            if (!ownsMovement && finalEvent.changes.any {
                                    it.isConsumed && it.position != it.previousPosition
                                }) {
                                gestureCanceled = true
                                break
                            }
                        } while (!gestureCanceled && event.changes.any { it.pressed })

                        if (!gestureCanceled && !isMultiTouch &&
                            gestureMode == ZoomableImageGestureMode.IMAGE_INTERACTION
                        ) {
                            val velocity = panVelocityTracker.calculateVelocity()
                            flingImage(Offset(velocity.x, velocity.y))
                        }
                        if (verticalDismissStarted) {
                            verticalDismissStarted = false
                            if (gestureCanceled) {
                                latestOnDragCancel()
                            } else {
                                latestOnDragEnd(verticalDragVelocityY)
                            }
                        }
                    } finally {
                        // Node disposal or pointerInput cancellation must also restore the host
                        // drag state; otherwise Pager can remain disabled after an interrupted swipe.
                        if (verticalDismissStarted) latestOnDragCancel()
                    }
                }
            }
    ) {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            imageLoader = imageLoader,
            placeholder = initialPainter,
            error = initialPainter,
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center)
                .testTag(ZOOMABLE_IMAGE_TAG)
                .drawWithContent {
                    drawContent()
                    if (drawableReady && imageSize.width > 0 && imageSize.height > 0) latestOnImageDrawn()
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
            },
            onLoading = { drawableReady = it.painter != null },
            onSuccess = { state ->
                drawableReady = true
                val originalSize = state.painter.intrinsicSize
                if (originalSize.width > 0 && originalSize.height > 0) {
                    imageSize = IntSize(originalSize.width.toInt(), originalSize.height.toInt())
                    
                    if (isExtremeAspectRatio(imageSize.width, imageSize.height)) {
                        onExtremeAspectRatioDetected()
                    }
                }
            },
            onError = { drawableReady = initialPainter != null; onImageError() },
            // 使用 Fit 模式确保初始完整显示
            contentScale = ContentScale.Fit
        )
        if (originalImageUrl != null && originalQuality) {
            OriginalImageRegion(
                imageUrl = originalImageUrl,
                publicContent = publicContent,
                enabled = gesturesEnabled && displayRectTrackingEnabled,
                viewport = { containerSize },
                displayRect = { sourceSize ->
                    resolveZoomableImageLocalDisplayRect(sourceSize, containerSize, scale, offsetX, offsetY)
                },
            )
        }
    }
}
