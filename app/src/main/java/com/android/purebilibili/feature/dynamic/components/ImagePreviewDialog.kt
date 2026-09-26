// 文件路径: feature/dynamic/components/ImagePreviewDialog.kt
package com.android.purebilibili.feature.dynamic.components

import coil3.network.NetworkHeaders
import coil3.network.httpHeaders

import coil3.request.crossfade
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText

import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.AppChromeSizeTokens

import com.android.purebilibili.core.ui.MediaContrastPalette

import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
//  Material Icons
import androidx.compose.material3.*
import com.android.purebilibili.core.ui.components.AppFilledIconButton
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppIconButtonDefaults
import com.android.purebilibili.core.ui.components.AppTextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect as ComposeRenderEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContextWrapper
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.toArgb
import com.android.purebilibili.core.ui.LocalPredictiveBackGestureEnabled
import com.android.purebilibili.core.ui.rememberAppShareIcon
import com.android.purebilibili.core.ui.setWindowNavigationBarColor
import com.android.purebilibili.core.ui.rememberAppLikeFilledIcon
import com.android.purebilibili.core.ui.rememberAppLikeIcon
import androidx.compose.ui.geometry.Offset
import androidx.media3.common.Player
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import com.android.purebilibili.core.ui.rememberAppRefreshIcon
import com.android.purebilibili.core.ui.rememberAppChevronDownIcon
import com.android.purebilibili.core.ui.rememberAppChevronUpIcon
import com.android.purebilibili.core.ui.rememberAppClearIcon
import com.android.purebilibili.core.ui.rememberAppDownloadIcon
import com.android.purebilibili.core.ui.rememberAppVisibilityOffIcon
import com.android.purebilibili.core.ui.rememberAppVisibilityOnIcon
import com.android.purebilibili.core.ui.AdaptiveLoadingIndicator
import com.android.purebilibili.core.ui.motion.continuityTween
import com.android.purebilibili.core.ui.motion.emphasizedEnterTween
import com.android.purebilibili.core.ui.motion.emphasizedExitTween
import com.android.purebilibili.core.ui.motion.interactiveSnapSpring
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.core.util.rememberHapticFeedback
import java.io.File
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState

/**
 *  图片预览对话框 - 支持左右滑动切换和3D立体动画
 */

internal const val IMAGE_PREVIEW_BACKDROP_TAG = "image_preview_backdrop"
internal const val IMAGE_PREVIEW_PAGE_TAG = "image_preview_page"
internal const val IMAGE_PREVIEW_COMMENT_PANEL_TAG = "image_preview_comment_panel"
internal const val IMAGE_PREVIEW_ORIGINAL_CHIP_TAG = "image_preview_original_chip"
internal const val IMAGE_PREVIEW_PAGE_INDICATOR_TAG = "image_preview_page_indicator"
private const val IMAGE_PREVIEW_SHARE_CACHE_MAX_AGE_MS = 24L * 60L * 60L * 1000L

private class ImagePreviewBlurEffectCache {
    private val effects = mutableMapOf<Int, ComposeRenderEffect>()

    fun resolve(radiusPx: Float): ComposeRenderEffect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || radiusPx <= 0.01f) return null
        val radiusKey = radiusPx.toInt().coerceAtLeast(1)
        return effects.getOrPut(radiusKey) {
            RenderEffect.createBlurEffect(
                radiusKey.toFloat(),
                radiusKey.toFloat(),
                Shader.TileMode.CLAMP,
            ).asComposeRenderEffect()
        }
    }
}

private data class ImagePreviewOverlayRequest(
    val token: Long,
    val images: List<String>,
    val livePhotoVideos: Map<String, String>,
    val initialIndex: Int,
    val sourceRect: androidx.compose.ui.geometry.Rect?,
    val sourceCornerRadiusDp: Float,
    val textContent: ImagePreviewTextContent?,
    val defaultTextVisible: Boolean,
    val onImageLongPress: ((String) -> Unit)?,
    val onDismiss: () -> Unit
)

/**
 * 源缩略图在图片预览打开期间应隐藏，否则飞出的图片会与原位卡片重影；
 * overlay request 在回位动画结束后才清空，因此卡片等「飞回落地」才恢复。
 * 匹配规则：捕获的 bounds 中心落在 request.sourceRect 外扩 8px 范围内。
 */
@Composable
fun isImagePreviewSourceHidden(bounds: androidx.compose.ui.geometry.Rect?): Boolean {
    if (bounds == null) return false
    val request by ImagePreviewOverlayController.request.collectAsStateWithLifecycle()
    val sourceRect = request?.sourceRect ?: return false
    return sourceRect.inflate(8f).contains(bounds.center)
}

private object ImagePreviewOverlayController {
    private val _request = MutableStateFlow<ImagePreviewOverlayRequest?>(null)
    val request = _request.asStateFlow()

    fun show(request: ImagePreviewOverlayRequest) {
        _request.value = request
    }

    fun dismiss(token: Long? = null) {
        val current = _request.value ?: return
        if (token == null || current.token == token) {
            _request.value = null
        }
    }
}

@Composable
fun ImagePreviewDialog(
    images: List<String>,
    initialIndex: Int,
    livePhotoVideos: Map<String, String> = emptyMap(),
    sourceRect: androidx.compose.ui.geometry.Rect? = null,
    sourceCornerRadiusDp: Float = resolveDrawGridCornerRadiusDp().toFloat(),
    textContent: ImagePreviewTextContent? = null,
    defaultTextVisible: Boolean = true,
    onImageLongPress: ((String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val latestOnDismiss by rememberUpdatedState(onDismiss)
    val requestToken = remember(images, initialIndex, sourceRect, sourceCornerRadiusDp, livePhotoVideos) { System.nanoTime() }

    LaunchedEffect(requestToken) {
        ImagePreviewOverlayController.show(
            ImagePreviewOverlayRequest(
                token = requestToken,
                images = images,
                livePhotoVideos = livePhotoVideos,
                initialIndex = initialIndex,
                sourceRect = sourceRect,
                sourceCornerRadiusDp = sourceCornerRadiusDp,
                textContent = textContent,
                defaultTextVisible = defaultTextVisible,
                onImageLongPress = onImageLongPress,
                onDismiss = { latestOnDismiss() }
            )
        )
    }

    DisposableEffect(requestToken) {
        onDispose {
            ImagePreviewOverlayController.dismiss(requestToken)
        }
    }
}

@Composable
fun ImagePreviewOverlayHost(
    modifier: Modifier = Modifier
) {
    val activeRequest by ImagePreviewOverlayController.request.collectAsStateWithLifecycle()
    activeRequest?.let { request ->
        Dialog(
            onDismissRequest = {
                ImagePreviewOverlayController.dismiss(request.token)
                request.onDismiss()
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            ImagePreviewOverlayContent(
                images = request.images,
                livePhotoVideos = request.livePhotoVideos,
                initialIndex = request.initialIndex,
                sourceRect = request.sourceRect,
                sourceCornerRadiusDp = request.sourceCornerRadiusDp,
                textContent = request.textContent,
                defaultTextVisible = request.defaultTextVisible,
                onImageLongPress = request.onImageLongPress,
                onDismiss = {
                    ImagePreviewOverlayController.dismiss(request.token)
                    request.onDismiss()
                },
                modifier = modifier
                    .fillMaxSize()
                    .zIndex(100f)
            )
        }
    }
}

@Composable
private fun ImagePreviewOverlayContent(
    images: List<String>,
    initialIndex: Int,
    livePhotoVideos: Map<String, String> = emptyMap(),
    sourceRect: androidx.compose.ui.geometry.Rect? = null,
    sourceCornerRadiusDp: Float = resolveDrawGridCornerRadiusDp().toFloat(),
    textContent: ImagePreviewTextContent? = null,
    defaultTextVisible: Boolean = true,
    onImageLongPress: ((String) -> Unit)? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val scope = rememberCoroutineScope()
    val haptic = rememberHapticFeedback()
    val shareIcon = rememberAppShareIcon()
    val likeIcon = rememberAppLikeIcon()
    val likeFilledIcon = rememberAppLikeFilledIcon()
    val commentContext = textContent?.commentContext
    // 普通图片与评论图片共用同一套 PiliPlus 风格画廊，不再分叉评论专用 chrome。
    val useCommentPreviewChrome = false
    var isSaving by remember { mutableStateOf(false) }
    var isSharing by remember { mutableStateOf(false) }
    var showOrdinaryImageActions by remember { mutableStateOf(false) }
    
    //  获取 Activity 和 Window 用于沉浸式控制
    val activity = remember {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return@remember ctx
            ctx = ctx.baseContext
        }
        null
    }
    val window = remember { activity?.window }
    val insetsController = remember {
        window?.let { WindowCompat.getInsetsController(it, it.decorView) }
    }
    
    //  保存原始导航栏颜色
    val originalNavBarColor = remember { window?.navigationBarColor ?: android.graphics.Color.BLACK }
    
    //  进入时设置沉浸式导航栏（透明黑色），退出时恢复
    DisposableEffect(Unit) {
        window?.let { setWindowNavigationBarColor(it, Color.Transparent.toArgb()) }
        insetsController?.isAppearanceLightNavigationBars = false
        
        onDispose {
            window?.let { setWindowNavigationBarColor(it, originalNavBarColor) }
        }
    }
    
    //  动画状态控制
    // 0f = 关闭/初始状态 (at sourceRect), 1f = 打开状态 (Fullscreen)
    val animateTrigger = remember { androidx.compose.animation.core.Animatable(0f) }
    val blurEffectCache = remember { ImagePreviewBlurEffectCache() }
    val backEventState = rememberNavigationEventState(NavigationEventInfo.None)
    val predictiveBackGestureEnabled = LocalPredictiveBackGestureEnabled.current
    val backProgress = if (predictiveBackGestureEnabled) {
        (backEventState.transitionState as? NavigationEventTransitionState.InProgress)
            ?.latestEvent
            ?.progress
            ?: 0f
    } else {
        0f
    }
    var isDismissing by remember { mutableStateOf(false) }
    var currentImageDisplayRect by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var dismissImageDisplayRect by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var activeZoomScale by remember { mutableFloatStateOf(1f) }
    var isVerticalDismissDragging by remember { mutableStateOf(false) }
    val longPressSaveEnabled by SettingsManager.getImagePreviewLongPressSaveEnabled(context)
        .collectAsStateWithLifecycle(initialValue = true)
    val gallery3dPageEnabled by SettingsManager.getImagePreview3dPageEnabled(context)
        .collectAsStateWithLifecycle(initialValue = false)
    var imagePreviewTextVisible by remember(textContent, defaultTextVisible) {
        mutableStateOf(
            resolveImagePreviewInitialTextVisibility(
                hasText = textContent != null,
                defaultVisible = defaultTextVisible
            )
        )
    }
    
    // 竖滑跟手用状态值，避免每帧 launch snapTo 竞态导致滑不动。
    var verticalDismissOffsetYPx by remember { mutableFloatStateOf(0f) }
    val verticalDismissSnapAnim = remember { androidx.compose.animation.core.Animatable(0f) }

    fun handleImageSaveResult(success: Boolean, successMessage: String = "图片已保存到相册") {
        haptic(resolveImagePreviewSaveFeedback(success))
        Toast.makeText(
            context,
            if (success) successMessage else "保存失败，请重试",
            Toast.LENGTH_SHORT
        ).show()
    }

    fun handleImageShareResult(success: Boolean) {
        haptic(resolveImagePreviewSaveFeedback(success))
        if (!success) {
            Toast.makeText(context, "分享失败，请重试", Toast.LENGTH_SHORT).show()
        }
    }

    //  GIF 图片加载器
    val gifImageLoader = context.imageLoader

    //  使用 HorizontalPager 实现滑动切换
    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { images.size }
    )

    // 已通过「查看原图」切换为全分辨率加载的页（按页索引记录）。
    var originalQualityPages by remember { mutableStateOf(setOf<Int>()) }

    LaunchedEffect(pagerState.currentPage) {
        activeZoomScale = 1f
        if (!isDismissing) {
            isVerticalDismissDragging = false
            verticalDismissOffsetYPx = 0f
            verticalDismissSnapAnim.snapTo(0f)
        }
    }
    
    val currentLiveVideoUrl = remember(pagerState.currentPage, livePhotoVideos, images) {
        val raw = images.getOrNull(pagerState.currentPage).orEmpty()
        resolveLivePhotoVideoUrl(raw, pagerState.currentPage, livePhotoVideos)
    }
    var isLivePhotoPlaying by remember(pagerState.currentPage) { mutableStateOf(true) }
    var isLivePhotoEnabled by remember(pagerState.currentPage) { mutableStateOf(true) }
    var isLivePhotoMuted by remember(pagerState.currentPage) { mutableStateOf(false) }
    var showLivePhotoMenu by remember(pagerState.currentPage) { mutableStateOf(false) }
    var livePhotoPlayer by remember { mutableStateOf<Player?>(null) }

    var pendingSaveAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val storagePermission = com.android.purebilibili.core.util.rememberStoragePermissionState { granted ->
        if (granted) {
            val action = pendingSaveAction
            pendingSaveAction = null
            action?.invoke()
        }
    }

    fun requestSaveCurrentImage(imageUrl: String) {
        if (imageUrl.isEmpty() || isSaving) return
        if (onImageLongPress != null) {
            onImageLongPress(imageUrl)
            return
        }
        if (storagePermission.isGranted) {
            isSaving = true
            scope.launch {
                val success = saveImageToGallery(context, imageUrl)
                isSaving = false
                withContext(Dispatchers.Main) {
                    handleImageSaveResult(success)
                }
            }
        } else {
            pendingSaveAction = { requestSaveCurrentImage(imageUrl) }
            storagePermission.request()
        }
    }

    fun requestSaveMotionPhoto(imageUrl: String, videoUrl: String) {
        if (imageUrl.isEmpty() || videoUrl.isEmpty() || isSaving) return
        if (storagePermission.isGranted) {
            isSaving = true
            scope.launch {
                val success = saveMotionPhotoToGallery(context, imageUrl, videoUrl)
                isSaving = false
                withContext(Dispatchers.Main) {
                    handleImageSaveResult(success, successMessage = "实况照片已保存到相册")
                }
            }
        } else {
            pendingSaveAction = { requestSaveMotionPhoto(imageUrl, videoUrl) }
            storagePermission.request()
        }
    }

    fun requestSaveLivePhotoVideo(videoUrl: String) {
        if (videoUrl.isEmpty() || isSaving) return
        if (storagePermission.isGranted) {
            isSaving = true
            scope.launch {
                val success = saveLivePhotoVideoToGallery(context, videoUrl)
                isSaving = false
                withContext(Dispatchers.Main) {
                    handleImageSaveResult(success, successMessage = "实况视频已保存到相册")
                }
            }
        } else {
            pendingSaveAction = { requestSaveLivePhotoVideo(videoUrl) }
            storagePermission.request()
        }
    }

    fun requestSaveAllImages() {
        if (images.isEmpty() || isSaving) return
        val urls = images.map(::normalizeImageUrl).filter(String::isNotEmpty)
        if (storagePermission.isGranted) {
            isSaving = true
            scope.launch {
                val success = urls.map { saveImageToGallery(context, it) }.all { it }
                isSaving = false
                withContext(Dispatchers.Main) { handleImageSaveResult(success) }
            }
        } else {
            pendingSaveAction = { requestSaveAllImages() }
            storagePermission.request()
        }
    }

    fun requestShareCurrentImage(imageUrl: String) {
        if (imageUrl.isEmpty() || isSharing) return
        isSharing = true
        scope.launch {
            val success = shareImageFromPreview(context, imageUrl)
            isSharing = false
            withContext(Dispatchers.Main) {
                handleImageShareResult(success)
            }
        }
    }
    
    // 当前页的图片 URL
    val currentImageUrl = remember(pagerState.currentPage, images) {
        normalizeImageUrl(images.getOrNull(pagerState.currentPage) ?: "")
    }
    
    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
            val constraints = this
            val fullWidth = constraints.maxWidth
            val fullHeight = constraints.maxHeight
            val fullWidthPx = with(density) { fullWidth.toPx() }
            val fullHeightPx = with(density) { fullHeight.toPx() }
            val maxBlurRadiusPx = with(density) {
                (AppSpacingTokens.Large + AppSpacingTokens.Micro).toPx()
            }
            
            // 手势 scrub 期间画面由 backProgress 驱动；transitionState 离开 InProgress 的
            // 瞬间 backProgress 归零而 animateTrigger 仍为 1f，若直接回落会让画面先跳回
            // 全屏再重新飞出（双重回弹）。记住最后一帧 scrub 值，在此过渡窗口内保持。
            var lastScrubRawProgress by remember { mutableFloatStateOf(1f) }
            var backRecovering by remember { mutableStateOf(false) }
            SideEffect {
                if (backProgress > 0f) {
                    lastScrubRawProgress = 1f - backProgress
                }
            }
            val rawProgress = when {
                isDismissing || backRecovering -> animateTrigger.value
                backProgress > 0f -> 1f - backProgress
                lastScrubRawProgress < 1f -> lastScrubRawProgress
                else -> animateTrigger.value
            }
            val verticalDragFrame = resolveImagePreviewVerticalDragFrame(
                dragOffsetYPx = verticalDismissOffsetYPx,
                containerHeightPx = fullHeightPx
            )
            
            //  计算容器位置和大小
            // 如果切走了或者没有源矩形，则全屏显示（仅淡入淡出）
            // 有缩略图源矩形时始终做尺寸落位，保证返回大小匹配预览格。
            val shouldUseRectAnim = sourceRect != null
            val transitionFrame = resolveImagePreviewTransitionFrame(
                rawProgress = rawProgress,
                hasSourceRect = shouldUseRectAnim,
                sourceCornerRadiusDp = sourceCornerRadiusDp
            )
            val presentedCornerRadiusDp = resolveImagePreviewPresentedCornerRadiusDp(
                visualProgress = transitionFrame.visualProgress,
                verticalDragProgress = if (isDismissing) 0f else verticalDragFrame.progress,
                hasSourceRect = shouldUseRectAnim,
                sourceCornerRadiusDp = sourceCornerRadiusDp
            )
            val visualFrame = resolveImagePreviewVisualFrame(
                visualProgress = transitionFrame.visualProgress,
                transitionEnabled = true,
                maxBlurRadiusPx = maxBlurRadiusPx,
                // Returning should stay optically sharp while the image morphs back
                // into its source rect. Blur made the source image look unfocused.
                blurEnabled = !isDismissing && backProgress <= 0f,
            )
            val backdropAlpha = if (isDismissing) {
                resolveImagePreviewDismissBackdropAlpha(transitionFrame.visualProgress)
            } else {
                visualFrame.backdropAlpha * verticalDragFrame.backdropAlphaMultiplier
            }
            val dismissRectFrame = resolveImagePreviewDismissRectFrame(
                transitionProgress = transitionFrame.layoutProgress,
                sourceRect = if (shouldUseRectAnim && isDismissing) sourceRect else null,
                displayedImageRect = if (shouldUseRectAnim && isDismissing) dismissImageDisplayRect else null
            )
            
            val targetLeft = AppSpacingTokens.None
            val targetTop = AppSpacingTokens.None
            val targetWidth = fullWidth
            val targetHeight = fullHeight
            val previewSurfaceRect = remember(constraints.maxWidth, constraints.maxHeight) {
                androidx.compose.ui.geometry.Rect(
                    left = 0f,
                    top = 0f,
                    right = with(density) { constraints.maxWidth.toPx() },
                    bottom = with(density) { constraints.maxHeight.toPx() }
                )
            }

            LaunchedEffect(Unit) {
                val openMotion = imagePreviewDismissMotion()
                animateTrigger.snapTo(0f)
                // 进场与退场同系 Continuity，一镜对称。
                animateTrigger.animateTo(
                    targetValue = 1f,
                    animationSpec = continuityTween(durationMillis = openMotion.openDurationMillis)
                )
            }

            fun triggerDismiss(
                startRect: androidx.compose.ui.geometry.Rect? = resolveImagePreviewDismissStartRect(
                    previewSurfaceRect = previewSurfaceRect,
                    displayedImageRect = currentImageDisplayRect,
                    // 从真实显示图区域飞回缩略图，黑边不参与 morph，观感更干净。
                    preferPreviewSurface = false
                )
            ) {
                if (isDismissing) return
                dismissImageDisplayRect = startRect
                isVerticalDismissDragging = false
                isDismissing = true
                scope.launch {
                    verticalDismissOffsetYPx = 0f
                    verticalDismissSnapAnim.snapTo(0f)
                    val dismissMotion = imagePreviewDismissMotion()
                    // 单段 morph：几何线性 + Continuity 速度曲线，无 overshoot / spring 二次落点。
                    animateTrigger.animateTo(
                        targetValue = dismissMotion.settleTarget,
                        animationSpec = continuityTween(
                            durationMillis = dismissMotion.collapseDurationMillis
                        )
                    )
                    onDismiss()
                }
            }

            NavigationBackHandler(
                state = backEventState,
                isBackEnabled = !isDismissing,
                onBackCancelled = {
                    scope.launch {
                        backRecovering = true
                        val dismissMotion = imagePreviewDismissMotion()
                        animateTrigger.snapTo(lastScrubRawProgress)
                        animateTrigger.animateTo(
                            targetValue = 1f,
                            animationSpec = emphasizedEnterTween(
                                durationMillis = dismissMotion.cancelRecoverDurationMillis
                            ),
                        )
                        lastScrubRawProgress = 1f
                        backRecovering = false
                    }
                },
                onBackCompleted = {
                    scope.launch {
                        animateTrigger.snapTo(lastScrubRawProgress)
                        triggerDismiss()
                    }
                },
            )
            
            val (currentLeft, currentTop, currentWidth, currentHeight) = if (shouldUseRectAnim) {
                val source = sourceRect
                val sourceLeft = with(density) { source.left.toDp() }
                val sourceTop = with(density) { source.top.toDp() }
                val sourceWidth = with(density) { source.width.toDp() }
                val sourceHeight = with(density) { source.height.toDp() }
                
                val l = androidx.compose.ui.unit.lerp(sourceLeft, targetLeft, transitionFrame.layoutProgress)
                val t = androidx.compose.ui.unit.lerp(sourceTop, targetTop, transitionFrame.layoutProgress)
                val w = androidx.compose.ui.unit.lerp(sourceWidth, targetWidth, transitionFrame.layoutProgress)
                val h = androidx.compose.ui.unit.lerp(sourceHeight, targetHeight, transitionFrame.layoutProgress)
                
                Quad(l, t, w, h)
            } else {
                Quad(AppSpacingTokens.None, AppSpacingTokens.None, fullWidth, fullHeight)
            }
            
            // 1. 背景层 (淡入淡出)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(IMAGE_PREVIEW_BACKDROP_TAG)
                    .background(MediaContrastPalette.Scrim.copy(alpha = backdropAlpha))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { triggerDismiss() }
                        )
                    }
            )
            
            // 2. 内容层 (缩放位移)
            val contentModifier = if (isDismissing && shouldUseRectAnim && dismissRectFrame != null) {
                Modifier
                    .offset(
                        x = with(density) { dismissRectFrame.rect.left.toDp() },
                        y = with(density) { dismissRectFrame.rect.top.toDp() }
                    )
                    .size(
                        width = with(density) { dismissRectFrame.rect.width.toDp() },
                        height = with(density) { dismissRectFrame.rect.height.toDp() }
                    )
                    .graphicsLayer {
                        shape = RoundedCornerShape(presentedCornerRadiusDp.dp)
                        clip = true
                        alpha = visualFrame.contentAlpha
                        renderEffect = blurEffectCache.resolve(visualFrame.blurRadiusPx)
                    }
            } else {
                Modifier
                    .offset(x = currentLeft, y = currentTop)
                    .size(width = currentWidth, height = currentHeight)
                    .graphicsLayer {
                        shape = RoundedCornerShape(presentedCornerRadiusDp.dp)
                        clip = true
                        alpha = visualFrame.contentAlpha
                        renderEffect = blurEffectCache.resolve(visualFrame.blurRadiusPx)
                        if (!shouldUseRectAnim) {
                            scaleX = transitionFrame.fallbackScale
                            scaleY = transitionFrame.fallbackScale
                        }
                        if (!isDismissing) {
                            translationY = verticalDismissOffsetYPx
                            val dragScale = verticalDragFrame.scale
                            scaleX *= dragScale
                            scaleY *= dragScale
                            transformOrigin = TransformOrigin.Center
                        }
                    }
            }

            Box(
                 modifier = contentModifier
            ) {
                //  使用 HorizontalPager 实现滑动切换 + 3D立体动画
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,  // 预加载相邻页面
                    userScrollEnabled = !isVerticalDismissDragging &&
                        !isDismissing &&
                        activeZoomScale <= 1.01f,
                    key = { images.getOrElse(it) { "" } }
                ) { page ->
                    // 所有图片默认平面横滑，可由同一个设置启用轻量 3D。
                    val apply3D = transitionFrame.visualProgress > 0.92f

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag(IMAGE_PREVIEW_PAGE_TAG)
                            .graphicsLayer {
                                // 页面偏移（0 = 居中，-1 = 左边，1 = 右边）在这里读取而不是
                                // 组合期。currentPageOffsetFraction 横滑时每帧都变，
                                // 在组合期读取等于把每一帧都升级成一次重组；
                                // 放进 graphicsLayer lambda 后只触发重绘，不触发重组。
                                val pageOffset =
                                    (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                                if (apply3D && gallery3dPageEnabled) {
                                    val transform = resolveImagePreviewGalleryPageTransform(
                                        pageOffsetFraction = pageOffset,
                                        containerWidthPx = fullWidthPx
                                    )
                                    rotationY = transform.rotationY
                                    translationX = transform.translationXPx
                                    cameraDistance = 16f * density.density
                                    transformOrigin = TransformOrigin(
                                        pivotFractionX = transform.pivotFractionX,
                                        pivotFractionY = 0.5f
                                    )
                                    scaleX = transform.scale
                                    scaleY = transform.scale
                                    alpha = transform.alpha
                                }
                            }
                            .pointerInput(Unit) {
                                // 阻止点击穿透到关闭手势
                                detectTapGestures { 
                                     if (!useCommentPreviewChrome) {
                                         // 点击图片也关闭
                                         triggerDismiss()
                                     }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        val imageUrl = remember(images.getOrNull(page)) {
                            normalizeImageUrl(images.getOrNull(page) ?: "")
                        }
                        val decodeSize = remember(page, imageUrl, page in originalQualityPages) {
                            resolveImageDecodeSize(
                                if (page in originalQualityPages) {
                                    ImageDecodeTarget.ORIGINAL_QUALITY
                                } else {
                                    ImageDecodeTarget.FULLSCREEN_PREVIEW
                                }
                            )
                        }
                        
                        ZoomableImage(
                            model = ImageRequest.Builder(context)
                                .data(imageUrl)
                                // 预览必须采样解码，避免超大原图超过 Canvas 单位图绘制上限。
                                .size(decodeSize.widthPx, decodeSize.heightPx)
                                .httpHeaders(NetworkHeaders.Builder().set("Referer", "https://www.bilibili.com/").build())
                                // 退出 morph 时关闭 crossfade，避免尺寸变化触发二次淡入发黏。
                                .crossfade(!isDismissing)
                                .build(),
                            contentDescription = null,
                            imageLoader = gifImageLoader,  //  使用 GIF 加载器
                            modifier = Modifier.fillMaxSize(),
                            onZoomChange = {
                                activeZoomScale = it
                            },
                            onDisplayRectChange = { rect ->
                                if (!isDismissing && page == pagerState.currentPage) {
                                    currentImageDisplayRect = rect
                                }
                            },
                            onVerticalDismissDragStart = {
                                if (page == pagerState.currentPage && !isDismissing) {
                                    isVerticalDismissDragging = true
                                    scope.launch { verticalDismissSnapAnim.stop() }
                                }
                            },
                            onVerticalDismissDrag = { dragDelta ->
                                if (page == pagerState.currentPage && !isDismissing && isVerticalDismissDragging) {
                                    verticalDismissOffsetYPx += dragDelta
                                }
                            },
                            onVerticalDismissDragEnd = {
                                if (page == pagerState.currentPage && !isDismissing && isVerticalDismissDragging) {
                                    isVerticalDismissDragging = false
                                    val draggedRect = resolveImagePreviewDraggedDisplayRect(
                                        displayedImageRect = currentImageDisplayRect,
                                        translationYPx = verticalDismissOffsetYPx,
                                        scale = verticalDragFrame.scale
                                    )
                                    when (
                                        resolveImagePreviewVerticalDismissDecision(
                                            dragOffsetYPx = verticalDismissOffsetYPx,
                                            containerHeightPx = fullHeightPx
                                        )
                                    ) {
                                        ImagePreviewVerticalDismissDecision.DISMISS -> triggerDismiss(draggedRect)
                                        ImagePreviewVerticalDismissDecision.SNAP_BACK -> {
                                            scope.launch {
                                                verticalDismissSnapAnim.snapTo(verticalDismissOffsetYPx)
                                                verticalDismissSnapAnim.animateTo(
                                                    targetValue = 0f,
                                                    animationSpec = interactiveSnapSpring()
                                                ) {
                                                    verticalDismissOffsetYPx = value
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            onExtremeAspectRatioDetected = {
                                // 长条图在 4096 方形采样档下短边像素不足，放大后仍会发糊。
                                // 自动提升到现有原图解码档；极端长宽比下实际内存远低于方形上限。
                                originalQualityPages = originalQualityPages + page
                            },
                            onVerticalDismissDragCancel = {
                                if (page == pagerState.currentPage && !isDismissing) {
                                    isVerticalDismissDragging = false
                                    scope.launch {
                                        verticalDismissSnapAnim.snapTo(verticalDismissOffsetYPx)
                                        verticalDismissSnapAnim.animateTo(
                                            targetValue = 0f,
                                            animationSpec = interactiveSnapSpring()
                                        ) {
                                            verticalDismissOffsetYPx = value
                                        }
                                    }
                                }
                            },
                            onLongPress = {
                                if (
                                    page == pagerState.currentPage &&
                                    shouldHandleImagePreviewLongPressSave(
                                        longPressSaveEnabled = longPressSaveEnabled,
                                        imageUrl = imageUrl,
                                        isSaving = isSaving
                                    )
                                ) {
                                    haptic(resolveImagePreviewLongPressSaveStartFeedback())
                                    if (onImageLongPress != null) {
                                        onImageLongPress(imageUrl)
                                    } else if (!useCommentPreviewChrome) {
                                        showOrdinaryImageActions = true
                                    } else {
                                        requestSaveCurrentImage(imageUrl)
                                    }
                                }
                            },
                            onClick = {
                                if (!useCommentPreviewChrome) {
                                    // 点击图片关闭预览
                                    triggerDismiss()
                                }
                            }
                        )
                        val currentRawUrl = images.getOrNull(page).orEmpty()
                        val liveVideoUrl = resolveLivePhotoVideoUrl(
                            rawUrl = currentRawUrl,
                            pageIndex = page,
                            livePhotoVideos = livePhotoVideos
                        )
                        if (
                            !liveVideoUrl.isNullOrBlank() &&
                            isLivePhotoEnabled &&
                            page == pagerState.currentPage &&
                            !isDismissing &&
                            transitionFrame.visualProgress >= 0.85f && activeZoomScale <= 1.05f
                        ) {
                            LivePhotoPlayback(
                                videoUrl = liveVideoUrl,
                                modifier = Modifier.fillMaxSize(),
                                isPlaying = isLivePhotoPlaying,
                                isMuted = isLivePhotoMuted,
                                playerRef = { livePhotoPlayer = it },
                                onClick = {
                                    if (showLivePhotoMenu) {
                                        showLivePhotoMenu = false
                                    } else if (!useCommentPreviewChrome) {
                                        triggerDismiss()
                                    }
                                },
                                onLongPress = {
                                    if (
                                        page == pagerState.currentPage &&
                                        shouldHandleImagePreviewLongPressSave(
                                            longPressSaveEnabled = longPressSaveEnabled,
                                            imageUrl = imageUrl,
                                            isSaving = isSaving
                                        )
                                    ) {
                                        haptic(resolveImagePreviewLongPressSaveStartFeedback())
                                        if (onImageLongPress != null) {
                                            onImageLongPress(imageUrl)
                                        } else if (!useCommentPreviewChrome) {
                                            showOrdinaryImageActions = true
                                        } else {
                                            requestSaveCurrentImage(imageUrl)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
            
            // 3. UI 覆盖层 - 退出时先于图片清掉 chrome，只剩干净一镜 morph
            val chromeAlpha = resolveImagePreviewChromeAlpha(
                visualProgress = transitionFrame.visualProgress,
                isDismissing = isDismissing
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = chromeAlpha }
            ) {
                val safeDrawingPadding = WindowInsets.safeDrawing.asPaddingValues()
                val overlayPadding = resolveImagePreviewOverlayPadding(
                    safeInsetStart = safeDrawingPadding.calculateStartPadding(layoutDirection),
                    safeInsetTop = safeDrawingPadding.calculateTopPadding(),
                    safeInsetEnd = safeDrawingPadding.calculateEndPadding(layoutDirection),
                    safeInsetBottom = safeDrawingPadding.calculateBottomPadding()
                )
                val resolvedText = resolveImagePreviewText(
                    textContent = textContent,
                    currentPage = pagerState.currentPage,
                    totalPages = images.size
                )
                val textPlacement = textContent?.placement ?: ImagePreviewTextPlacement.OVERLAY_BOTTOM
                val shouldShowResolvedText = shouldShowImagePreviewText(
                    hasText = resolvedText != null,
                    textVisible = imagePreviewTextVisible
                ) && useCommentPreviewChrome

                if (!useCommentPreviewChrome &&
                    resolvedText != null &&
                    shouldShowResolvedText &&
                    textPlacement == ImagePreviewTextPlacement.OVERLAY_BOTTOM
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(
                                start = overlayPadding.start + AppSpacingTokens.Small,
                                end = overlayPadding.end + AppSpacingTokens.Small,
                                bottom = overlayPadding.bottom + AppSpacingTokens.TripleExtraLarge + AppSpacingTokens.Large + AppSpacingTokens.Micro
                            )
                            .graphicsLayer {
                                // transform 在这里就地求值：它只依赖 currentPageOffsetFraction，
                                // 而那个值横滑时每帧都变，放在组合期会拖着整段文字浮层一起重组。
                                val textTransform = resolveImagePreviewTextTransform(
                                    pageOffsetFraction = pagerState.currentPageOffsetFraction
                                )
                                alpha = textTransform.alpha
                                rotationX = textTransform.rotationX
                                translationY = textTransform.translateYDp.dp.toPx()
                                cameraDistance = 10f * this.density
                                transformOrigin = TransformOrigin(0.5f, 1f)
                            }
                            .clickable {
                                imagePreviewTextVisible =
                                    resolveImagePreviewTextVisibilityAfterToggle(imagePreviewTextVisible)
                            }
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .widthIn(max = AppSpacingTokens.TripleExtraLarge * 11 + AppSpacingTokens.DoubleExtraLarge)
                                .clip(AppShapes.container(ContainerLevel.Sheet))
                                .background(
                                    androidx.compose.ui.graphics.Brush.verticalGradient(
                                        colors = listOf(
                                            MediaContrastPalette.Scrim.copy(alpha = 0.72f),
                                            MediaContrastPalette.Scrim.copy(alpha = 0.56f)
                                        )
                                    )
                                )
                                .padding(horizontal = AppSpacingTokens.Large, vertical = AppSpacingTokens.Medium + AppSpacingTokens.Micro / 2)
                        ) {
                            AnimatedContent(
                                targetState = pagerState.currentPage,
                                transitionSpec = {
                                    (fadeIn(animationSpec = emphasizedEnterTween(250)) + slideInVertically(
                                        animationSpec = emphasizedEnterTween(250)
                                    ) { it / 3 }) togetherWith
                                        (fadeOut(animationSpec = emphasizedExitTween(180)) + slideOutVertically(
                                            animationSpec = emphasizedExitTween(180)
                                        ) { -it / 4 })
                                },
                                label = "imagePreviewTextSwitch"
                            ) { page ->
                                val currentText = resolveImagePreviewText(
                                    textContent = textContent,
                                    currentPage = page,
                                    totalPages = images.size
                                ) ?: resolvedText
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.ExtraSmall + AppSpacingTokens.Micro)
                                ) {
                                    if (currentText.headline.isNotBlank() || currentText.pageIndicator.isNotBlank()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small + AppSpacingTokens.Micro),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (currentText.headline.isNotBlank()) {
                                                AppText(
                                                    text = currentText.headline,
                                                    color = MediaContrastPalette.Foreground.copy(alpha = 0.9f),
                                                    fontSize = MaterialTheme.typography.labelMedium.fontSize,
                                                    maxLines = 1,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                            }
                                            if (currentText.pageIndicator.isNotBlank()) {
                                                AppText(
                                                    text = currentText.pageIndicator,
                                                    color = MediaContrastPalette.Foreground.copy(alpha = 0.64f),
                                                    fontSize = MaterialTheme.typography.labelSmall.fontSize
                                                )
                                            }
                                        }
                                    }
                                    if (currentText.body.isNotBlank()) {
                                        AppText(
                                            text = currentText.body,
                                            color = MediaContrastPalette.Foreground.copy(alpha = 0.94f),
                                            fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                                            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight,
                                            maxLines = 4,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // PiliPlus 普通画廊：底部轻渐变 + 紧凑数字页码，单图也显示 1/1。
                if (!useCommentPreviewChrome) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .testTag(IMAGE_PREVIEW_PAGE_INDICATOR_TAG)
                            .background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        MediaContrastPalette.Scrim.copy(alpha = 0.3f)
                                    )
                                )
                            )
                            .padding(
                                start = overlayPadding.start + AppSpacingTokens.Medium,
                                top = AppSpacingTokens.Small,
                                end = overlayPadding.end + AppSpacingTokens.Medium,
                                bottom = overlayPadding.bottom + AppSpacingTokens.Small
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        AppText(
                            text = "${pagerState.currentPage + 1}/${images.size}",
                            color = Color.White,
                            fontSize = MaterialTheme.typography.bodyMedium.fontSize
                        )
                    }
                }
                
                val chromeModifier = Modifier.graphicsLayer {
                    // 同上：横滑期间 currentPageOffsetFraction 每帧变化，
                    // 原先在组合期读取会让整个 chrome（顶栏 + 底栏 + 页码）每帧重组。
                    // graphicsLayer 的 lambda 本身就是 Density，不需要外部的 with(density)。
                    val chromeOffset = pagerState.currentPageOffsetFraction.coerceIn(-1f, 1f)
                    rotationZ = -chromeOffset * 2.8f
                    translationX = (-chromeOffset * 10f).dp.toPx()
                    transformOrigin = TransformOrigin.Center
                }

                // 顶部按钮栏（关闭 + 页码 + 下载）
                if (useCommentPreviewChrome && commentContext != null) {
                    val currentPage = pagerState.currentPage
                    val isOriginalQuality = currentPage in originalQualityPages
                    ImagePreviewCommentTopBar(
                        label = if (isOriginalQuality) {
                            "原图已加载"
                        } else {
                            commentContext.originalSizeLabels.getOrNull(currentPage)
                                ?: resolveCommentImageOriginalSizeLabel(null)
                        },
                        shareIcon = shareIcon,
                        isSharing = isSharing,
                        enabled = !isSharing && !isSaving,
                        onDismiss = { triggerDismiss() },
                        onShare = { requestShareCurrentImage(currentImageUrl) },
                        onViewOriginal = {
                            // 按 API 文档：去掉 @ 尺寸参数即为原图 URL（预览已用该 URL），
                            // 此处切换为全分辨率解码重新加载，突破预览采样限制。
                            originalQualityPages = originalQualityPages + currentPage
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(
                                start = overlayPadding.start,
                                top = overlayPadding.top,
                                end = overlayPadding.end
                            )
                            .then(chromeModifier)
                    )
                } else if (useCommentPreviewChrome && textContent != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(
                            start = overlayPadding.start,
                            top = overlayPadding.top,
                            end = overlayPadding.end
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 关闭按钮
                    AppFilledIconButton(
                        onClick = { triggerDismiss() },
                        colors = AppIconButtonDefaults.colors(
                            containerColor = MediaContrastPalette.Scrim.copy(0.5f)
                        )
                    ) {
                        AppIcon(
                            imageVector = rememberAppClearIcon(),
                            contentDescription = "关闭",
                            tint = MediaContrastPalette.Foreground
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = AppSpacingTokens.Medium),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            resolvedText != null && shouldShowResolvedText && textPlacement == ImagePreviewTextPlacement.TOP_BAR -> {
                                Box(
                                    modifier = Modifier.graphicsLayer {
                                        val textTransform = resolveImagePreviewTextTransform(
                                            pageOffsetFraction = pagerState.currentPageOffsetFraction
                                        )
                                        alpha = textTransform.alpha
                                        translationY = (textTransform.translateYDp * 0.45f).dp.toPx()
                                    }
                                ) {
                                    AnimatedContent(
                                        targetState = pagerState.currentPage,
                                        transitionSpec = {
                                            val isForward = targetState > initialState
                                            (fadeIn(animationSpec = emphasizedEnterTween(220)) +
                                                slideInHorizontally { fullWidth ->
                                                    if (isForward) fullWidth / 3 else -fullWidth / 3
                                                }) togetherWith
                                                (fadeOut(animationSpec = emphasizedExitTween(160)) +
                                                    slideOutHorizontally { fullWidth ->
                                                        if (isForward) -fullWidth / 4 else fullWidth / 4
                                                    })
                                        },
                                        label = "imagePreviewTopBarTextSwitch"
                                    ) { page ->
                                        val pageText = resolveImagePreviewText(
                                            textContent = textContent,
                                            currentPage = page,
                                            totalPages = images.size
                                        ) ?: resolvedText
                                        val primaryText = pageText.body.ifBlank { pageText.headline }
                                        val secondaryText = if (
                                            pageText.body.isNotBlank() &&
                                            pageText.headline.isNotBlank()
                                        ) {
                                            pageText.headline
                                        } else {
                                            ""
                                        }
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.Micro)
                                        ) {
                                            if (secondaryText.isNotBlank()) {
                                                AppText(
                                                    text = secondaryText,
                                                    color = MediaContrastPalette.Foreground.copy(alpha = 0.82f),
                                                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                                    maxLines = 1,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                )
                                            }
                                            if (primaryText.isNotBlank()) {
                                                AppText(
                                                    text = primaryText,
                                                    color = MediaContrastPalette.Foreground,
                                                    fontSize = MaterialTheme.typography.labelMedium.fontSize,
                                                    maxLines = 2,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                    modifier = Modifier
                                                        .background(MediaContrastPalette.Scrim.copy(0.5f), AppShapes.container(ContainerLevel.Card))
                                                        .padding(horizontal = AppSpacingTokens.Medium, vertical = AppSpacingTokens.ExtraSmall + AppSpacingTokens.Micro)
                                                )
                                            }
                                            if (images.size > 1) {
                                                AppText(
                                                    text = "${page + 1} / ${images.size}",
                                                    color = MediaContrastPalette.Foreground.copy(alpha = 0.8f),
                                                    fontSize = MaterialTheme.typography.labelSmall.fontSize
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            images.size > 1 -> {
                                AppText(
                                    "${pagerState.currentPage + 1} / ${images.size}",
                                    color = MediaContrastPalette.Foreground,
                                    fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                                    modifier = Modifier
                                        .background(MediaContrastPalette.Scrim.copy(0.5f), AppShapes.container(ContainerLevel.Card))
                                        .padding(horizontal = AppSpacingTokens.Medium, vertical = AppSpacingTokens.ExtraSmall + AppSpacingTokens.Micro)
                                )
                            }
                        }
                    }

                    if (resolvedText != null) {
                        AppFilledIconButton(
                            onClick = {
                                imagePreviewTextVisible =
                                    resolveImagePreviewTextVisibilityAfterToggle(imagePreviewTextVisible)
                            },
                            colors = AppIconButtonDefaults.colors(
                                containerColor = MediaContrastPalette.Scrim.copy(0.5f)
                            )
                        ) {
                            AppIcon(
                                imageVector = if (imagePreviewTextVisible) {
                                    rememberAppVisibilityOffIcon()
                                } else {
                                    rememberAppVisibilityOnIcon()
                                },
                                contentDescription = if (imagePreviewTextVisible) "隐藏图片文字" else "显示图片文字",
                                tint = MediaContrastPalette.Foreground
                            )
                        }
                        Spacer(modifier = Modifier.width(AppSpacingTokens.Small))
                    }
                    
                    // 分享按钮
                    AppFilledIconButton(
                        onClick = {
                            requestShareCurrentImage(currentImageUrl)
                        },
                        enabled = !isSharing && !isSaving,
                        colors = AppIconButtonDefaults.colors(
                            containerColor = MediaContrastPalette.Scrim.copy(0.5f)
                        )
                    ) {
                        if (isSharing) {
                            AdaptiveLoadingIndicator(
                                size = AppSpacingTokens.ExtraLarge,
                                color = MediaContrastPalette.Foreground,
                                strokeWidth = AppSpacingTokens.Micro
                            )
                        } else {
                            AppIcon(
                                imageVector = shareIcon,
                                contentDescription = "分享图片",
                                tint = MediaContrastPalette.Foreground
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(AppSpacingTokens.Small))

                    //  下载按钮
                    AppFilledIconButton(
                        onClick = {
                            requestSaveCurrentImage(currentImageUrl)
                        },
                        enabled = !isSaving && !isSharing,
                        colors = AppIconButtonDefaults.colors(
                            containerColor = MediaContrastPalette.Scrim.copy(0.5f)
                        )
                    ) {
                        if (isSaving) {
                            AdaptiveLoadingIndicator(
                                size = AppSpacingTokens.ExtraLarge,
                                color = MediaContrastPalette.Foreground,
                                strokeWidth = AppSpacingTokens.Micro
                            )
                        } else {
                            AppIcon(
                                imageVector = rememberAppDownloadIcon(),
                                contentDescription = "保存图片",
                                tint = MediaContrastPalette.Foreground
                            )
                        }
                    }
                }
                }

                if (useCommentPreviewChrome && commentContext != null) {
                    ImagePreviewCommentPanel(
                        context = commentContext,
                        likeIcon = likeIcon,
                        likeFilledIcon = likeFilledIcon,
                        shareIcon = shareIcon,
                        isSharing = isSharing,
                        enabled = !isSharing && !isSaving,
                        onShare = { requestShareCurrentImage(currentImageUrl) },
                        onReply = {
                            commentContext.onReplyClick?.invoke()
                            triggerDismiss()
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(
                                start = overlayPadding.start,
                                end = overlayPadding.end,
                                bottom = overlayPadding.bottom + AppSpacingTokens.Medium
                            )
                            .then(chromeModifier)
                    )
                }

                // 若展开了实况菜单，点击背景空白区域收起菜单
                if (showLivePhotoMenu) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(onTap = { showLivePhotoMenu = false })
                            }
                    )
                }

                // 左上角实况照片控制胶囊与下拉菜单（对齐系统实况相册交互）
                if (!currentLiveVideoUrl.isNullOrBlank() && !useCommentPreviewChrome) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(
                                start = maxOf(12.dp, safeDrawingPadding.calculateStartPadding(layoutDirection) + 4.dp),
                                top = overlayPadding.top
                            )
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MediaContrastPalette.Scrim.copy(alpha = 0.65f))
                                    .clickable { showLivePhotoMenu = !showLivePhotoMenu }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isLivePhotoEnabled) {
                                    LivePhotoIcon(tint = Color.White)
                                } else {
                                    LivePhotoOffIcon(tint = Color.White.copy(alpha = 0.8f))
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                AppText(
                                    text = if (isLivePhotoEnabled) "实况" else "实况已关",
                                    color = Color.White,
                                    fontSize = MaterialTheme.typography.labelMedium.fontSize,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                AppIcon(
                                    imageVector = if (showLivePhotoMenu) rememberAppChevronUpIcon() else rememberAppChevronDownIcon(),
                                    contentDescription = "实况菜单",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            if (showLivePhotoMenu) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(MediaContrastPalette.Scrim.copy(alpha = 0.88f))
                                        .padding(vertical = 4.dp)
                                        .width(IntrinsicSize.Max)
                                ) {
                                    Column {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    isLivePhotoEnabled = !isLivePhotoEnabled
                                                    if (isLivePhotoEnabled) {
                                                        isLivePhotoPlaying = true
                                                    }
                                                    showLivePhotoMenu = false
                                                }
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (isLivePhotoEnabled) {
                                                LivePhotoOffIcon(tint = Color.White)
                                            } else {
                                                LivePhotoIcon(tint = Color.White)
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            AppText(
                                                text = if (isLivePhotoEnabled) "关闭实况" else "开启实况",
                                                color = Color.White,
                                                fontSize = MaterialTheme.typography.bodyMedium.fontSize
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(0.5.dp)
                                                .background(Color.White.copy(alpha = 0.15f))
                                        )

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    isLivePhotoEnabled = true
                                                    isLivePhotoPlaying = true
                                                    livePhotoPlayer?.seekTo(0)
                                                    livePhotoPlayer?.play()
                                                    showLivePhotoMenu = false
                                                }
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            AppIcon(
                                                imageVector = rememberAppRefreshIcon(),
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            AppText(
                                                text = "重新播放",
                                                color = Color.White,
                                                fontSize = MaterialTheme.typography.bodyMedium.fontSize
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 右下角声音切换按钮（支持有声实况播放与静音切换）
                if (!currentLiveVideoUrl.isNullOrBlank() && isLivePhotoEnabled) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(
                                end = maxOf(12.dp, safeDrawingPadding.calculateEndPadding(layoutDirection) + 4.dp),
                                bottom = overlayPadding.bottom
                            )
                            .clip(CircleShape)
                            .background(MediaContrastPalette.Scrim.copy(alpha = 0.65f))
                            .clickable { isLivePhotoMuted = !isLivePhotoMuted }
                            .padding(8.dp)
                    ) {
                        AppIcon(
                            imageVector = if (isLivePhotoMuted) {
                                Icons.AutoMirrored.Filled.VolumeOff
                            } else {
                                Icons.AutoMirrored.Filled.VolumeUp
                            },
                            contentDescription = if (isLivePhotoMuted) "开启声音" else "静音",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
    }

    if (showOrdinaryImageActions) {
        AppAlertDialog(
            onDismissRequest = { showOrdinaryImageActions = false },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ImagePreviewActionButton(
                        label = "分享",
                        onClick = {
                            showOrdinaryImageActions = false
                            requestShareCurrentImage(currentImageUrl)
                        }
                    )
                    ImagePreviewActionButton(
                        label = "复制链接",
                        onClick = {
                            showOrdinaryImageActions = false
                            val clipboard = context.getSystemService(ClipboardManager::class.java)
                            clipboard?.setPrimaryClip(ClipData.newPlainText("图片链接", currentImageUrl))
                        }
                    )
                    ImagePreviewActionButton(
                        label = "保存图片",
                        onClick = {
                            showOrdinaryImageActions = false
                            requestSaveCurrentImage(currentImageUrl)
                        }
                    )
                    if (!currentLiveVideoUrl.isNullOrBlank()) {
                        ImagePreviewActionButton(
                            label = "保存实况照片 (Motion Photo)",
                            onClick = {
                                showOrdinaryImageActions = false
                                requestSaveMotionPhoto(currentImageUrl, currentLiveVideoUrl)
                            }
                        )
                        ImagePreviewActionButton(
                            label = "保存实况视频 (MP4)",
                            onClick = {
                                showOrdinaryImageActions = false
                                requestSaveLivePhotoVideo(currentLiveVideoUrl)
                            }
                        )
                    }
                    if (images.size > 1) {
                        ImagePreviewActionButton(
                            label = "保存全部图片",
                            onClick = {
                                showOrdinaryImageActions = false
                                requestSaveAllImages()
                            }
                        )
                    }
                }
            },
            confirmButton = {}
        )
    }
}

@Composable
private fun ImagePreviewActionButton(
    label: String,
    onClick: () -> Unit
) {
    AppTextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AppChromeSizeTokens.MinimumTouchTarget),
        contentPadding = PaddingValues(horizontal = AppSpacingTokens.Medium),
        colors = ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        AppText(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ImagePreviewCommentTopBar(
    label: String,
    shareIcon: ImageVector,
    isSharing: Boolean,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onViewOriginal: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIconButton(
            onClick = onDismiss
        ) {
            AppIcon(
                imageVector = rememberAppClearIcon(),
                contentDescription = "关闭",
                tint = MediaContrastPalette.Foreground,
                modifier = Modifier.size(AppSpacingTokens.ExtraLarge)
            )
        }
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            AppText(
                text = label,
                color = MediaContrastPalette.Foreground.copy(alpha = if (onViewOriginal != null) 0.9f else 0.38f),
                fontSize = MaterialTheme.typography.labelMedium.fontSize,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier
                    .testTag(IMAGE_PREVIEW_ORIGINAL_CHIP_TAG)
                    .clip(AppShapes.container(ContainerLevel.Floating))
                    .background(MediaContrastPalette.Foreground.copy(alpha = 0.16f))
                    .then(
                        if (onViewOriginal != null) {
                            Modifier.clickable(
                                enabled = enabled,
                                onClick = onViewOriginal
                            )
                        } else {
                            Modifier
                        }
                    )
                    .padding(horizontal = AppSpacingTokens.Large + AppSpacingTokens.Micro, vertical = AppSpacingTokens.Small - AppSpacingTokens.Micro / 2)
            )
        }
        AppIconButton(
            onClick = onShare,
            enabled = enabled
        ) {
            if (isSharing) {
                AdaptiveLoadingIndicator(
                    size = AppSpacingTokens.ExtraLarge - AppSpacingTokens.Micro,
                    color = MediaContrastPalette.Foreground,
                    strokeWidth = AppSpacingTokens.Micro
                )
            } else {
                AppIcon(
                    imageVector = shareIcon,
                    contentDescription = "分享图片",
                    tint = MediaContrastPalette.Foreground,
                    modifier = Modifier.size(AppSpacingTokens.ExtraLarge - AppSpacingTokens.Micro / 2)
                )
            }
        }
    }
}

@Composable
private fun ImagePreviewCommentPanel(
    context: ImagePreviewCommentContext,
    likeIcon: ImageVector,
    likeFilledIcon: ImageVector,
    shareIcon: ImageVector,
    isSharing: Boolean,
    enabled: Boolean,
    onShare: () -> Unit,
    onReply: () -> Unit,
    modifier: Modifier = Modifier
) {
    var localLiked by remember(context.replyId, context.liked) { mutableStateOf(context.liked) }
    var localLikeCount by remember(context.replyId, context.likeCount) { mutableIntStateOf(context.likeCount) }
    val displayLikeCount = remember(localLikeCount) {
        FormatUtils.formatStat(localLikeCount.coerceAtLeast(0).toLong())
    }

    Column(
        modifier = modifier.testTag(IMAGE_PREVIEW_COMMENT_PANEL_TAG),
        verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small + AppSpacingTokens.Micro)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = context.avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(AppSpacingTokens.DoubleExtraLarge + AppSpacingTokens.ExtraSmall + AppSpacingTokens.Micro)
                    .clip(CircleShape)
                    .background(MediaContrastPalette.Foreground.copy(alpha = 0.16f))
            )
            Spacer(modifier = Modifier.width(AppSpacingTokens.Small + AppSpacingTokens.Micro))
            Column(modifier = Modifier.weight(1f)) {
                AppText(
                    text = context.authorName,
                    color = MediaContrastPalette.Foreground,
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                if (context.timeText.isNotBlank()) {
                    AppText(
                        text = context.timeText,
                        color = MediaContrastPalette.Foreground.copy(alpha = 0.58f),
                        fontSize = MaterialTheme.typography.labelSmall.fontSize,
                        maxLines = 1
                    )
                }
            }
        }

        if (context.body.isNotBlank()) {
            AppText(
                text = context.body,
                color = MediaContrastPalette.Foreground.copy(alpha = 0.94f),
                fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                lineHeight = MaterialTheme.typography.bodyLarge.lineHeight,
                maxLines = 3,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(AppSpacingTokens.DoubleExtraLarge + AppSpacingTokens.ExtraSmall + AppSpacingTokens.Micro)
                    .clip(AppShapes.container(ContainerLevel.Floating))
                    .background(MediaContrastPalette.Foreground.copy(alpha = 0.12f))
                    .clickable(enabled = context.onReplyClick != null, onClick = onReply)
                    .padding(horizontal = AppSpacingTokens.Medium + AppSpacingTokens.Micro),
                contentAlignment = Alignment.CenterStart
            ) {
                AppText(
                    text = "回复 ${context.authorName}",
                    color = MediaContrastPalette.Foreground.copy(alpha = 0.56f),
                    fontSize = MaterialTheme.typography.labelMedium.fontSize,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(AppSpacingTokens.Large))
            ImagePreviewCommentActionButton(
                icon = if (localLiked) likeFilledIcon else likeIcon,
                label = displayLikeCount,
                selected = localLiked,
                enabled = context.onLikeClick != null,
                onClick = {
                    context.onLikeClick?.invoke()
                    if (!localLiked) {
                        localLiked = true
                        localLikeCount += 1
                    } else {
                        localLiked = false
                        localLikeCount = (localLikeCount - 1).coerceAtLeast(0)
                    }
                }
            )
            Spacer(modifier = Modifier.width(AppSpacingTokens.Medium + AppSpacingTokens.Micro))
            ImagePreviewCommentActionButton(
                icon = shareIcon,
                label = "转发",
                selected = false,
                enabled = enabled,
                onClick = onShare,
                busy = isSharing
            )
        }
    }
}

@Composable
private fun ImagePreviewCommentActionButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    busy: Boolean = false
) {
    Column(
        modifier = Modifier
            .size(width = AppSpacingTokens.TripleExtraLarge - AppSpacingTokens.Micro, height = AppSpacingTokens.TripleExtraLarge)
            .clickable(enabled = enabled && !busy, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (busy) {
            AdaptiveLoadingIndicator(
                size = AppSpacingTokens.ExtraLarge - AppSpacingTokens.Micro,
                color = MediaContrastPalette.Foreground,
                strokeWidth = AppSpacingTokens.Micro
            )
        } else {
            AppIcon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) MaterialTheme.colorScheme.primary else MediaContrastPalette.Foreground,
                modifier = Modifier.size(AppSpacingTokens.ExtraLarge)
            )
        }
        AppText(
            text = label,
            color = MediaContrastPalette.Foreground.copy(alpha = if (enabled) 0.88f else 0.38f),
            fontSize = MaterialTheme.typography.labelSmall.fontSize,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

// 辅助数据类
data class Quad(val left: androidx.compose.ui.unit.Dp, val top: androidx.compose.ui.unit.Dp, val width: androidx.compose.ui.unit.Dp, val height: androidx.compose.ui.unit.Dp)

/**
 *  规范化图片 URL
 * 1. 修复协议头（http -> https, // -> https://）
 * 2. 移除分辨率限制参数（@...）以获取原图
 */
internal fun normalizeImageUrl(rawSrc: String): String {
    val trimmed = rawSrc.trim()
    var result = when {
        trimmed.startsWith("https://") -> trimmed
        trimmed.startsWith("http://") -> trimmed.replace("http://", "https://")
        trimmed.startsWith("//") -> "https:$trimmed"
        trimmed.isNotEmpty() -> "https://$trimmed"
        else -> ""
    }
    
    //  移除 Bilibili 图片尺寸参数（例如 @640w_400h.webp）以获取最高质量
    if (result.contains("@")) {
        result = result.substringBefore("@")
    }
    
    return result
}

internal fun resolveImageShareMimeType(imageUrl: String): String {
    val normalizedUrl = imageUrl.substringBefore('?').substringBefore('@').lowercase()
    return when {
        normalizedUrl.endsWith(".gif") -> "image/gif"
        normalizedUrl.endsWith(".webp") -> "image/webp"
        normalizedUrl.endsWith(".png") -> "image/png"
        else -> "image/jpeg"
    }
}

private fun resolveImageShareExtension(mimeType: String): String = when (mimeType) {
    "image/gif" -> "gif"
    "image/webp" -> "webp"
    "image/png" -> "png"
    else -> "jpg"
}

/**
 *  分享图片 - 下载原始图片到应用缓存，再通过 FileProvider 交给系统分享面板
 */
suspend fun shareImageFromPreview(context: Context, imageUrl: String): Boolean {
    val normalizedUrl = normalizeImageUrl(imageUrl)
    if (normalizedUrl.isEmpty()) return false
    val mimeType = resolveImageShareMimeType(normalizedUrl)
    val sharedFile = withContext(Dispatchers.IO) {
        createImagePreviewShareFile(context, normalizedUrl, mimeType)
    } ?: return false

    return withContext(Dispatchers.Main) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                sharedFile
            )
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newUri(context.contentResolver, "BiliPai image", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(sendIntent, "分享图片").apply {
                putExtra(Intent.EXTRA_TITLE, "分享图片")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            Log.e("ImagePreview", "Error sharing image", e)
            false
        }
    }
}

private fun createImagePreviewShareFile(
    context: Context,
    imageUrl: String,
    mimeType: String
): File? {
    return try {
        val cacheDir = File(context.cacheDir, "shared_images").apply { mkdirs() }
        cleanupImagePreviewShareCache(cacheDir)
        val extension = resolveImageShareExtension(mimeType)
        val outputFile = File(cacheDir, "BiliPai_${System.currentTimeMillis()}.$extension")
        val connection = java.net.URL(imageUrl).openConnection() as java.net.HttpURLConnection
        try {
            connection.setRequestProperty("Referer", "https://www.bilibili.com/")
            connection.connect()
            if (connection.responseCode !in 200..299) {
                Log.e("ImagePreview", "Failed to download for sharing: ${connection.responseCode}")
                return null
            }
            connection.inputStream.use { inputStream ->
                outputFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            outputFile
        } finally {
            connection.disconnect()
        }
    } catch (e: Exception) {
        Log.e("ImagePreview", "Error preparing image share", e)
        null
    }
}

private fun cleanupImagePreviewShareCache(cacheDir: File) {
    val expireBefore = System.currentTimeMillis() - IMAGE_PREVIEW_SHARE_CACHE_MAX_AGE_MS
    cacheDir.listFiles()?.forEach { file ->
        if (file.lastModified() < expireBefore) {
            file.delete()
        }
    }
}

/**
 *  保存图片到相册 - 支持 GIF/WebP 等格式保留
 */
suspend fun saveImageToGallery(context: android.content.Context, imageUrl: String): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            //  检测图片格式
            val isGif = imageUrl.contains(".gif", ignoreCase = true)
            val isWebp = imageUrl.contains(".webp", ignoreCase = true)
            val isPng = imageUrl.contains(".png", ignoreCase = true)
            
            //  对于 GIF/WebP，直接下载原始字节流保留动画
            if (isGif || isWebp) {
                val url = java.net.URL(imageUrl)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.setRequestProperty("Referer", "https://www.bilibili.com/")
                connection.connect()
                
                if (connection.responseCode != 200) {
                    Log.e("ImagePreview", "Failed to download: ${connection.responseCode}")
                    return@withContext false
                }
                
                // 先把下载流落到临时文件，再分发给保存目标，避免整块字节驻留 Java 堆。
                val tempFile = File.createTempFile("bilipai_save_", ".bin", context.cacheDir)
                try {
                    connection.inputStream.use { input ->
                        tempFile.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
                    }
                } catch (e: Exception) {
                    tempFile.delete()
                    throw e
                }
                connection.disconnect()

                // 生成文件名
                val extension = when {
                    isGif -> "gif"
                    isWebp -> "webp"
                    else -> "jpg"
                }
                val mimeType = when {
                    isGif -> "image/gif"
                    isWebp -> "image/webp"
                    else -> "image/jpeg"
                }
                val fileName = "BiliPai_${System.currentTimeMillis()}.$extension"

                val savedToCustomDirectory = tempFile.inputStream().use { input ->
                    saveStreamToCustomImageSaveDirectory(context, input, fileName, mimeType)
                }
                if (savedToCustomDirectory) {
                    tempFile.delete()
                    Log.d("ImagePreview", "Image saved to custom directory: $fileName")
                    return@withContext true
                }

                // 使用 MediaStore 保存
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, resolveDefaultImageMediaStoreRelativePath())
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }

                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                )
                if (uri == null) {
                    tempFile.delete()
                    return@withContext false
                }

                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    tempFile.inputStream().use { input -> input.copyTo(outputStream, 64 * 1024) }
                }
                tempFile.delete()
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    context.contentResolver.update(uri, contentValues, null, null)
                }
                
                Log.d("ImagePreview", "Image saved successfully: $fileName")
                return@withContext true
            }
            
            //  对于 JPEG/PNG 等静态图片，使用 Coil 下载并转换
            val imageLoader = context.imageLoader
            val request = ImageRequest.Builder(context)
                .data(imageUrl)
                .httpHeaders(NetworkHeaders.Builder().set("Referer", "https://www.bilibili.com/").build())
                .build()
            
            val result = imageLoader.execute(request)
            if (result !is SuccessResult) {
                Log.e("ImagePreview", "Failed to download image: $imageUrl")
                return@withContext false
            }
            
            val bitmap = (result.image as? coil3.BitmapImage)?.bitmap
            if (bitmap == null) {
                Log.e("ImagePreview", "Failed to convert drawable to bitmap")
                return@withContext false
            }
            
            // 生成文件名
            val extension = if (isPng) "png" else "jpg"
            val mimeType = if (isPng) "image/png" else "image/jpeg"
            val fileName = "BiliPai_${System.currentTimeMillis()}.$extension"
            val format = if (isPng) android.graphics.Bitmap.CompressFormat.PNG else android.graphics.Bitmap.CompressFormat.JPEG

            if (
                saveBitmapToCustomImageSaveDirectory(
                    context = context,
                    bitmap = bitmap,
                    fileName = fileName,
                    format = format,
                    quality = 95,
                    mimeType = mimeType
                )
            ) {
                Log.d("ImagePreview", "Image saved to custom directory: $fileName")
                return@withContext true
            }
            
            // 使用 MediaStore 保存图片
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, resolveDefaultImageMediaStoreRelativePath())
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            
            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ) ?: return@withContext false
            
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                bitmap.compress(format, 95, outputStream)
            }
            
            // 标记保存完成
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, contentValues, null, null)
            }
            
            Log.d("ImagePreview", "Image saved successfully: $fileName")
            true
        } catch (e: Exception) {
            Log.e("ImagePreview", "Error saving image", e)
            false
        }
    }
}

/**
 * 健壮解析当前页对应的实况视频 URL（对齐 PiliPlus 数据结构，兼容 http/https/相对路径及 scheme 差异）
 */
internal fun resolveLivePhotoVideoUrl(
    rawUrl: String,
    pageIndex: Int,
    livePhotoVideos: Map<String, String>
): String? {
    if (livePhotoVideos.isEmpty()) return null
    if (rawUrl.isNotBlank()) {
        // 1. 直接命中
        livePhotoVideos[rawUrl]?.let { return it }
        // 2. 归一化图片 URL 命中
        val normalized = normalizeImageUrl(rawUrl)
        livePhotoVideos[normalized]?.let { return it }
        // 3. 归一化实况视频 URL 命中
        normalizeLivePhotoVideoUrl(rawUrl)?.let { livePhotoVideos[it] }?.let { return it }
        // 4. 去除协议头匹配路径（兼容 http:// 与 https:// 混用场景）
        val stripped = rawUrl.removePrefix("https:").removePrefix("http:").substringBefore("@").substringBefore("?")
        for ((key, value) in livePhotoVideos) {
            val keyStripped = key.removePrefix("https:").removePrefix("http:").substringBefore("@").substringBefore("?")
            if (keyStripped.isNotEmpty() && (keyStripped == stripped || stripped.endsWith(keyStripped) || keyStripped.endsWith(stripped))) {
                return value
            }
        }
    }
    return null
}

private const val BROWSER_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

/**
 * 合成并保存符合 Google / Android 相册规范的 Motion Photo（实况动态照片 JPEG）
 * 包含 XMP 目录元数据与末尾追加的 MP4 视频流，在小米/华为/OPPO/vivo/三星/Google相册中均可直接作为实况照片互动（长按播放、可随时暂停打断）。
 */
suspend fun saveMotionPhotoToGallery(
    context: android.content.Context,
    imageUrl: String,
    videoUrl: String
): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            // 1. 下载实况视频 MP4 数据——直接落盘临时文件，避免把上百 MB 视频整体读进 Java 堆
            val videoConn = java.net.URL(videoUrl).openConnection() as java.net.HttpURLConnection
            videoConn.setRequestProperty("Referer", "https://www.bilibili.com/")
            videoConn.setRequestProperty("User-Agent", BROWSER_USER_AGENT)
            videoConn.connect()
            if (videoConn.responseCode !in 200..299) {
                Log.e("ImagePreview", "Failed to download live video: ${videoConn.responseCode}")
                return@withContext false
            }
            val tempVideoFile = File.createTempFile("motion_photo_video_", ".mp4", context.cacheDir)
            var videoSize = 0L
            try {
                videoConn.inputStream.use { input ->
                    tempVideoFile.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
                }
                videoSize = tempVideoFile.length()
                if (videoSize <= 0L) {
                    tempVideoFile.delete()
                    Log.e("ImagePreview", "Live video download produced an empty file")
                    return@withContext false
                }
            } catch (e: Exception) {
                tempVideoFile.delete()
                throw e
            } finally {
                videoConn.disconnect()
            }

            // 2. 下载并转码静态图片为标准 JPEG
            val imageConn = java.net.URL(normalizeImageUrl(imageUrl)).openConnection() as java.net.HttpURLConnection
            imageConn.setRequestProperty("Referer", "https://www.bilibili.com/")
            imageConn.setRequestProperty("User-Agent", BROWSER_USER_AGENT)
            imageConn.connect()
            if (imageConn.responseCode !in 200..299) {
                imageConn.disconnect()
                tempVideoFile.delete()
                Log.e("ImagePreview", "Failed to download image: ${imageConn.responseCode}")
                return@withContext false
            }
            val bitmap = imageConn.inputStream.use { android.graphics.BitmapFactory.decodeStream(it) }
            imageConn.disconnect()
            if (bitmap == null) {
                tempVideoFile.delete()
                return@withContext false
            }

            val rawJpegStream = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, rawJpegStream)
            val rawJpegBytes = rawJpegStream.toByteArray()

            // 3. 通过 tempFile 和 ExifInterface 注入标准 EXIF APP1（确保系统相册优先识别为标准相机实况照片）
            var jpegWithExif = rawJpegBytes
            try {
                val tempFile = File.createTempFile("motion_photo_temp_", ".jpg", context.cacheDir)
                try {
                    tempFile.outputStream().use { it.write(rawJpegBytes) }
                    val exif = android.media.ExifInterface(tempFile.absolutePath)
                    exif.setAttribute(android.media.ExifInterface.TAG_MAKE, Build.MANUFACTURER)
                    exif.setAttribute(android.media.ExifInterface.TAG_MODEL, Build.MODEL)
                    val now = java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())
                    exif.setAttribute(android.media.ExifInterface.TAG_DATETIME, now)
                    exif.setAttribute(android.media.ExifInterface.TAG_DATETIME_ORIGINAL, now)
                    exif.saveAttributes()
                    jpegWithExif = tempFile.readBytes()
                } finally {
                    tempFile.delete()
                }
            } catch (e: Exception) {
                Log.w("ImagePreview", "Failed to write EXIF attributes, fallback to raw JPEG", e)
            }

            // 4. 构建 Google / Android 官方 Motion Photo 1.0 标准 XMP 元数据（兼容 MicroVideo、小米 MiCamera 与新版 Container 规范）
            // videoSize 已在下载落盘时确定（XMP 的 MicroVideoOffset / Container Length 用）
            val xmpString = """
<x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="Adobe XMP Core 5.1.0-jc003">
  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
    <rdf:Description rdf:about=""
        xmlns:Camera="http://ns.google.com/photos/1.0/camera/"
        xmlns:GCamera="http://ns.google.com/photos/1.0/camera/"
        xmlns:MiCamera="http://ns.xiaomi.com/photos/1.0/camera/"
        xmlns:Container="http://ns.google.com/photos/1.0/container/"
        xmlns:Item="http://ns.google.com/photos/1.0/container/item/"
        Camera:MotionPhoto="1"
        Camera:MotionPhotoVersion="1"
        Camera:MotionPhotoPresentationTimestampUs="0"
        GCamera:MotionPhoto="1"
        GCamera:MotionPhotoVersion="1"
        GCamera:MotionPhotoPresentationTimestampUs="0"
        GCamera:MicroVideo="1"
        GCamera:MicroVideoVersion="1"
        GCamera:MicroVideoOffset="$videoSize"
        GCamera:MicroVideoPresentationTimestampUs="0"
        MiCamera:MotionPhoto="1"
        MiCamera:MotionPhotoVersion="1"
        MiCamera:MotionPhotoPresentationTimestampUs="0">
      <Camera:MotionPhoto>1</Camera:MotionPhoto>
      <Camera:MotionPhotoVersion>1</Camera:MotionPhotoVersion>
      <Camera:MotionPhotoPresentationTimestampUs>0</Camera:MotionPhotoPresentationTimestampUs>
      <GCamera:MotionPhoto>1</GCamera:MotionPhoto>
      <GCamera:MotionPhotoVersion>1</GCamera:MotionPhotoVersion>
      <GCamera:MotionPhotoPresentationTimestampUs>0</GCamera:MotionPhotoPresentationTimestampUs>
      <GCamera:MicroVideo>1</GCamera:MicroVideo>
      <GCamera:MicroVideoVersion>1</GCamera:MicroVideoVersion>
      <GCamera:MicroVideoOffset>$videoSize</GCamera:MicroVideoOffset>
      <GCamera:MicroVideoPresentationTimestampUs>0</GCamera:MicroVideoPresentationTimestampUs>
      <MiCamera:MotionPhoto>1</MiCamera:MotionPhoto>
      <MiCamera:MotionPhotoVersion>1</MiCamera:MotionPhotoVersion>
      <MiCamera:MotionPhotoPresentationTimestampUs>0</MiCamera:MotionPhotoPresentationTimestampUs>
      <Container:Directory>
        <rdf:Seq>
          <rdf:li rdf:parseType="Resource">
            <Container:Item
                Item:Mime="image/jpeg"
                Item:Semantic="Primary"
                Item:Length="0"
                Item:Padding="0"/>
          </rdf:li>
          <rdf:li rdf:parseType="Resource">
            <Container:Item
                Item:Mime="video/mp4"
                Item:Semantic="MotionPhoto"
                Item:Length="$videoSize"
                Item:Padding="0"/>
          </rdf:li>
        </rdf:Seq>
      </Container:Directory>
    </rdf:Description>
  </rdf:RDF>
</x:xmpmeta>
""".trimIndent()

            // 5. 打包 JPEG APP1 XMP 数据段
            val xmpNamespace = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(Charsets.UTF_8)
            val xmpPayload = xmpString.toByteArray(Charsets.UTF_8)
            val app1PayloadLen = xmpNamespace.size + xmpPayload.size
            val app1Len = app1PayloadLen + 2
            val app1Segment = java.io.ByteArrayOutputStream().apply {
                write(0xFF)
                write(0xE1)
                write((app1Len shr 8) and 0xFF)
                write(app1Len and 0xFF)
                write(xmpNamespace)
                write(xmpPayload)
            }.toByteArray()

            // 6. 确定 XMP 插入位置：紧跟在 EXIF APP1 之后，确保 EXIF 永远位于第一个 APP1
            var insertPos = 2
            var offset = 2
            while (offset + 4 < jpegWithExif.size) {
                if ((jpegWithExif[offset].toInt() and 0xFF) != 0xFF) break
                val marker = jpegWithExif[offset + 1].toInt() and 0xFF
                if (marker == 0xDA || marker == 0xD9) break // SOS or EOI
                val segLen = ((jpegWithExif[offset + 2].toInt() and 0xFF) shl 8) or (jpegWithExif[offset + 3].toInt() and 0xFF)
                if (marker == 0xE1 && offset + 8 <= jpegWithExif.size) {
                    val isExif = jpegWithExif[offset + 4] == 'E'.code.toByte() &&
                                 jpegWithExif[offset + 5] == 'x'.code.toByte() &&
                                 jpegWithExif[offset + 6] == 'i'.code.toByte() &&
                                 jpegWithExif[offset + 7] == 'f'.code.toByte()
                    if (isExif) {
                        insertPos = offset + 2 + segLen
                        break
                    }
                }
                offset += 2 + segLen
            }

            // 7. 组装 Motion Photo：JPEG头部 + APP1 XMP + JPEG剩余数据与EOI；
            //    MP4 视频数据不再进堆，写输出时从临时文件流式追加。
            val jpegSegmentBytes = java.io.ByteArrayOutputStream(jpegWithExif.size + app1Segment.size).apply {
                write(jpegWithExif, 0, insertPos)
                write(app1Segment)
                write(jpegWithExif, insertPos, jpegWithExif.size - insertPos)
            }.toByteArray()

            // 8. 保存到相册
            val fileName = "BiliPai_Live_${System.currentTimeMillis()}.jpg"

            // 8.1 优先检查是否配置了自定义 SAF 保存目录（JPEG 段 + 视频流顺序拼接，不进堆）
            val motionPhotoCombinedStream = java.io.SequenceInputStream(
                java.util.Collections.enumeration(
                    listOf(
                        jpegSegmentBytes.inputStream(),
                        tempVideoFile.inputStream()
                    )
                )
            )
            val savedToCustomDirectory = motionPhotoCombinedStream.use { input ->
                saveStreamToCustomImageSaveDirectory(context, input, fileName, "image/jpeg")
            }
            if (savedToCustomDirectory) {
                tempVideoFile.delete()
                Log.d("ImagePreview", "Motion photo saved to custom directory: $fileName")
                return@withContext true
            }

            // 8.2 插入 MediaStore（针对小米 MIUI/HyperOS 及各厂商系统相册优化）
            // 注意：绝不能在 ContentValues 中放入 "is_motion_photo"，因为该列在系统 MediaProvider 中为只读索引列，
            // 传入会导致小米/MIUI等设备直接抛出 IllegalArgumentException: Invalid column 导致保存失败！
            // 此外，小米设备禁止第三方应用向 "DCIM/Camera" 写入文件（报错权限拒绝），需优先使用 "DCIM/BiliPai" 或 "Pictures/BiliPai"。
            var insertedUri: Uri? = null
            var savedRelativePath: String? = null

            val targetPaths = listOf(
                "DCIM/BiliPai",
                resolveDefaultImageMediaStoreRelativePath(),
                "DCIM",
                "Pictures"
            )

            for (relPath in targetPaths) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, relPath)
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }
                try {
                    val uri = context.contentResolver.insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        values
                    )
                    if (uri != null) {
                        insertedUri = uri
                        savedRelativePath = relPath
                        break
                    }
                } catch (e: Exception) {
                    Log.w("ImagePreview", "Failed to insert MediaStore into $relPath: ${e.message}")
                }
            }

            // 如果指定相对路径均失败，尝试不指定 RELATIVE_PATH
            if (insertedUri == null) {
                val fallbackValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }
                insertedUri = try {
                    context.contentResolver.insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        fallbackValues
                    )
                } catch (e: Exception) {
                    Log.e("ImagePreview", "Fallback insert MediaStore failed", e)
                    null
                }
            }

            val uri = insertedUri ?: run {
                tempVideoFile.delete()
                return@withContext false
            }

            val writeSuccess = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(jpegSegmentBytes)
                    tempVideoFile.inputStream().use { videoInput ->
                        videoInput.copyTo(outputStream, 64 * 1024)
                    }
                    outputStream.flush()
                }
                true
            }.getOrDefault(false)

            if (!writeSuccess) {
                tempVideoFile.delete()
                try { context.contentResolver.delete(uri, null, null) } catch (_: Exception) {}
                return@withContext false
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val finishValues = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                try {
                    context.contentResolver.update(uri, finishValues, null, null)
                } catch (e: Exception) {
                    Log.w("ImagePreview", "Failed to clear IS_PENDING", e)
                }
            }

            // 9. 通知系统 MediaScanner 立即建立实况照片索引并触发实况解析
            try {
                val projection = arrayOf(MediaStore.Images.Media.DATA)
                var filePath = context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                }
                if (filePath.isNullOrBlank() && savedRelativePath != null) {
                    val root = android.os.Environment.getExternalStorageDirectory()
                    filePath = File(root, "$savedRelativePath/$fileName").absolutePath
                }
                if (!filePath.isNullOrBlank()) {
                    android.media.MediaScannerConnection.scanFile(
                        context,
                        arrayOf(filePath),
                        arrayOf("image/jpeg")
                    ) { path, scannedUri ->
                        Log.d("ImagePreview", "MediaScanner indexed: $path -> $scannedUri")
                    }
                }
            } catch (e: Exception) {
                Log.w("ImagePreview", "MediaScanner scanFile failed", e)
            }

            tempVideoFile.delete()
            Log.d("ImagePreview", "Motion photo saved successfully: $fileName, size: ${jpegSegmentBytes.size + videoSize}")
            true
        } catch (e: Exception) {
            Log.e("ImagePreview", "Error saving motion photo", e)
            false
        }
    }
}

/**
 * 保存实况视频文件到本地相册（对齐 PiliPlus downloadLivePhoto）
 */
suspend fun saveLivePhotoVideoToGallery(context: android.content.Context, videoUrl: String): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val url = java.net.URL(videoUrl)
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.setRequestProperty("Referer", "https://www.bilibili.com/")
            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            )
            connection.connect()

            if (connection.responseCode !in 200..299) {
                Log.e("ImagePreview", "Failed to download live video: ${connection.responseCode}")
                return@withContext false
            }

            val fileName = "BiliPai_Live_${System.currentTimeMillis()}.mp4"
            val mimeType = "video/mp4"

            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/BiliPai")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }

            val uri = context.contentResolver.insert(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ) ?: run {
                connection.inputStream.close()
                connection.disconnect()
                return@withContext false
            }

            // 视频直接从下载流写入 MediaStore，避免把整段 MP4 读进 Java 堆。
            connection.inputStream.use { input ->
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    input.copyTo(outputStream, 64 * 1024)
                }
            }
            connection.disconnect()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, contentValues, null, null)
            }

            Log.d("ImagePreview", "Live photo video saved successfully: $fileName")
            true
        } catch (e: Exception) {
            Log.e("ImagePreview", "Error saving live photo video", e)
            false
        }
    }
}

@Composable
private fun LivePhotoIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    androidx.compose.foundation.Canvas(modifier = modifier.size(16.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val outerRadius = size.minDimension / 2f - 1.5f
        val innerRadius = outerRadius * 0.46f
        drawCircle(
            color = tint,
            radius = outerRadius,
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.8f)
        )
        drawCircle(
            color = tint,
            radius = innerRadius,
            center = center
        )
    }
}

@Composable
private fun LivePhotoOffIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    androidx.compose.foundation.Canvas(modifier = modifier.size(16.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val outerRadius = size.minDimension / 2f - 1.5f
        val innerRadius = outerRadius * 0.46f
        drawCircle(
            color = tint,
            radius = outerRadius,
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.8f)
        )
        drawCircle(
            color = tint,
            radius = innerRadius,
            center = center
        )
        drawLine(
            color = tint,
            start = Offset(2f, size.height - 2f),
            end = Offset(size.width - 2f, 2f),
            strokeWidth = 1.8f
        )
    }
}

