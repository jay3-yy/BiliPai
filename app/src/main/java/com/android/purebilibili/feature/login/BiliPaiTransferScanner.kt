package com.android.purebilibili.feature.login

import com.android.purebilibili.core.ui.AppContentColors

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.components.AppOutlinedButton
import com.android.purebilibili.core.ui.components.AppText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** CameraX scanner shared by Bilibili login authorization and BiliPai session transfer. */
@Composable
fun BiliPaiTransferScanner(
    onCode: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleShot: Boolean = false,
    acceptAnyQr: Boolean = false,
    onError: (String) -> Unit = {},
    // Explicit opt-in: authorization places actions below the preview; existing
    // session-transfer callers retain their overlay layout and original sizing.
    previewModifier: Modifier? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnCode by rememberUpdatedState(onCode)
    val currentOnError by rememberUpdatedState(onError)
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    var galleryJob by remember { mutableStateOf<Job?>(null) }
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }
    val galleryLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        galleryJob?.cancel()
        galleryJob = coroutineScope.launch {
            val text = withContext(Dispatchers.IO) {
                try {
                    val bitmap = decodeUriBitmap(context, uri, maxDimension = 2048)
                    bitmap?.let {
                        try {
                            BiliPaiQrDecoder.decodeBitmap(it, acceptAny = true)
                        } finally {
                            it.recycle()
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
            }
            when {
                text == null -> currentOnError("未能识别图片中的二维码，请选择清晰、完整的二维码截图")
                acceptAnyQr || text.startsWith("bilipai://transfer/") || parseBilibiliLoginQr(text) != null ->
                    currentOnCode(text)
                else -> currentOnError("已识别二维码，但它不是受支持的 B 站登录或哔哩派转移二维码")
            }
        }
    }
    fun launchGalleryPicker() {
        galleryLauncher.launch(PickVisualMediaRequest(
            ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    LaunchedEffect(Unit) {
        if (!granted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!granted) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.material3.Text("需要摄像头权限才能扫描二维码", color = AppContentColors.secondaryText())
                AppTextButton(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    androidx.compose.material3.Text("允许使用摄像头")
                }
                AppTextButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { androidx.compose.material3.Text("打开权限设置") }
                AppTextButton(onClick = { launchGalleryPicker() }) {
                    androidx.compose.material3.Text("从相册识别")
                }
            }
        }
        return
    }

    val previewView = remember { PreviewView(context) }
    // SurfaceView renders on a separate window layer that ignores view bounds clipping,
    // so the camera preview bleeds over surrounding text. TextureView (COMPATIBLE) clips.
    previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
    // NagramX-style viewfinder: spring-in appearance, corner brackets, recognition pulse.
    val appear = remember { androidx.compose.animation.core.Animatable(0f) }
    val pulse = remember { androidx.compose.animation.core.Animatable(0f) }
    var cameraControl by remember { mutableStateOf<androidx.camera.core.CameraControl?>(null) }
    var torchEnabled by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        appear.animateTo(
            1f,
            androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 250f),
        )
    }
    DisposableEffect(lifecycleOwner, previewView, singleShot, acceptAnyQr) {
        val executor = Executors.newSingleThreadExecutor()
        val active = AtomicBoolean(true)
        val delivered = AtomicBoolean(false)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var boundProvider: ProcessCameraProvider? = null
        var boundPreview: Preview? = null
        var boundAnalysis: ImageAnalysis? = null
        fun reportError() {
            mainExecutor.execute {
                if (active.get()) currentOnError("无法启动扫码，请确认设备有可用摄像头后重试")
            }
        }
        val listener = Runnable {
            if (!active.get()) return@Runnable
            try {
                val provider = providerFuture.get()
                if (!provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                    reportError()
                    return@Runnable
                }
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                var lastDeliveredAt = 0L
                var lastAnalyzedAt = 0L
                var failedFrames = 0
                analysis.setAnalyzer(executor) { image ->
                    try {
                        val now = android.os.SystemClock.elapsedRealtime()
                        if (active.get() && (!singleShot || !delivered.get()) &&
                            now - lastDeliveredAt >= 700L && now - lastAnalyzedAt >= 200L
                        ) {
                            lastAnalyzedAt = now
                            val bytes = image.toLuminanceBytes()
                            if (bytes != null) {
                                val code = if (acceptAnyQr)
                                    BiliPaiQrDecoder.decodeRaw(bytes, image.width, image.height, image.imageInfo.rotationDegrees)
                                    else BiliPaiQrDecoder.decode(bytes, image.width, image.height, image.imageInfo.rotationDegrees)
                                failedFrames = 0
                                code?.let {
                                    if (!singleShot || delivered.compareAndSet(false, true)) {
                                        lastDeliveredAt = now
                                        mainExecutor.execute {
                                            if (!active.get()) return@execute
                                            if (singleShot) {
                                                // Recognition pulse before handing the code over,
                                                // mirroring NagramX's lock-on feedback.
                                                coroutineScope.launch {
                                                    pulse.animateTo(
                                                        1f,
                                                        androidx.compose.animation.core.spring(
                                                            dampingRatio = 0.55f, stiffness = 700f,
                                                        ),
                                                    )
                                                    kotlinx.coroutines.delay(140)
                                                    currentOnCode(it)
                                                }
                                            } else {
                                                currentOnCode(it)
                                            }
                                        }
                                    }
                                }
                            } else throw IllegalStateException("Invalid luminance frame")
                        }
                    } catch (_: Exception) {
                        // Bad frames must not leak ImageProxy or stall CameraX's analysis pipeline.
                        failedFrames++
                        if (failedFrames >= 3 && delivered.compareAndSet(false, true)) {
                            mainExecutor.execute {
                                if (active.get()) currentOnError("相机画面解析失败，请关闭后重新打开扫码")
                            }
                        }
                    } finally {
                        image.close()
                    }
                }
                val camera = provider.bindToLifecycle(
                    lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis,
                )
                boundProvider = provider
                boundPreview = preview
                boundAnalysis = analysis
                cameraControl = camera.cameraControl
            } catch (_: Exception) {
                reportError()
            }
        }
        providerFuture.addListener(listener, mainExecutor)
        onDispose {
            active.set(false)
            cameraControl = null
            torchEnabled = false
            boundAnalysis?.clearAnalyzer()
            boundPreview?.let { preview ->
                boundAnalysis?.let { analysis -> boundProvider?.unbind(preview, analysis) }
            }
            executor.shutdown()
        }
    }
    val previewContent: @Composable (Modifier) -> Unit = { cameraModifier ->
        Box(
            modifier = cameraModifier
                // Never let the camera layer paint outside the preview slot.
                .clipToBounds()
        ) {
            AndroidView(factory = { previewView }, modifier = Modifier.matchParentSize())
            ScannerViewfinderOverlay(appear = appear.value, pulse = pulse.value)
            if (previewModifier == null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppTextButton(onClick = { launchGalleryPicker() }) {
                        androidx.compose.material3.Text("从相册识别")
                    }
                    if (cameraControl != null) {
                        AppTextButton(onClick = {
                            val next = !torchEnabled
                            torchEnabled = next
                            cameraControl?.enableTorch(next)
                        }) {
                            androidx.compose.material3.Text(if (torchEnabled) "关闭手电筒" else "手电筒")
                        }
                    }
                }
            }
        }
    }
    if (previewModifier == null) {
        previewContent(modifier)
    } else {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            previewContent(previewModifier)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppOutlinedButton(
                    onClick = { launchGalleryPicker() },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                ) {
                    AppText("相册识别", style = MaterialTheme.typography.labelLarge)
                }
                AppOutlinedButton(
                    onClick = {
                        val next = !torchEnabled
                        torchEnabled = next
                        cameraControl?.enableTorch(next)
                    },
                    enabled = cameraControl != null,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                ) {
                    AppText(
                        if (torchEnabled) "关闭手电筒" else "手电筒",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

/**
 * NagramX-inspired viewfinder: a centered square (short edge / 1.5) with 50% black
 * mask outside, white rounded corner brackets that spring in on appear, and a
 * pulse that thickens the brackets and darkens the mask when a code is recognized.
 */
@Composable
private fun ScannerViewfinderOverlay(appear: Float, pulse: Float) {
    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val side = minOf(width, height) / 1.5f * (0.5f + 0.5f * appear)
        val left = (width - side) / 2f
        val top = (height - side) / 2f

        val maskAlpha = (0.5f + 0.25f * pulse) * appear.coerceIn(0f, 1f)
        val mask = androidx.compose.ui.graphics.Color.Black.copy(alpha = maskAlpha)
        drawRect(mask, topLeft = Offset.Zero, size = Size(width, top))
        drawRect(mask, topLeft = Offset(0f, top + side), size = Size(width, height - top - side))
        drawRect(mask, topLeft = Offset(0f, top), size = Size(left, side))
        drawRect(mask, topLeft = Offset(left + side, top), size = Size(width - left - side, side))
        if (appear < 1f) {
            drawRect(
                androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f * (1f - appear)),
                topLeft = Offset(left, top),
                size = Size(side, side),
            )
        }

        val stroke = 4.dp.toPx() * (1f + 0.8f * pulse)
        val cornerLen = 20.dp.toPx() * (1f + 0.5f * pulse)
        val half = stroke / 2f
        val bracket = androidx.compose.ui.graphics.Color.White.copy(
            alpha = appear.coerceIn(0f, 1f),
        )
        val strokeStyle = androidx.compose.ui.graphics.drawscope.Stroke(
            width = stroke,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
            join = androidx.compose.ui.graphics.StrokeJoin.Round,
        )

        // Bracket with an arc bend at the corner, like NagramX's rounded corner path.
        val bendRadius = minOf(stroke * 1.5f, cornerLen / 2f)
        fun cornerPath(corner: Offset, dx: Float, dy: Float) = androidx.compose.ui.graphics.Path().apply {
            moveTo(corner.x + dx * cornerLen, corner.y + dy * half)
            lineTo(corner.x + dx * bendRadius, corner.y + dy * half)
            quadraticBezierTo(
                corner.x + dx * half, corner.y + dy * half,
                corner.x + dx * half, corner.y + dy * bendRadius,
            )
            lineTo(corner.x + dx * half, corner.y + dy * cornerLen)
        }
        listOf(
            Offset(left, top) to Offset(1f, 1f),
            Offset(left + side, top) to Offset(-1f, 1f),
            Offset(left, top + side) to Offset(1f, -1f),
            Offset(left + side, top + side) to Offset(-1f, -1f),
        ).forEach { (corner, dir) ->
            drawPath(cornerPath(corner, dir.x, dir.y), bracket, style = strokeStyle)
        }
    }
}

/** Loads an image Uri into a bitmap capped at [maxDimension], honoring EXIF rotation. */
private fun decodeUriBitmap(
    context: android.content.Context,
    uri: Uri,
    maxDimension: Int,
): android.graphics.Bitmap? {
    val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val input = context.contentResolver.openInputStream(uri) ?: return null
    // Bounds-only decode intentionally returns null; validity comes from outWidth/outHeight.
    input.use { BitmapFactory.decodeStream(it, null, options) }
    val sample = resolveQrAlbumSampleSize(options.outWidth, options.outHeight, maxDimension)
        ?: return null
    val bounds = android.graphics.BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
    }
    val decoded = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        ?: return null
    val rotationDegrees = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            androidExifOrientationDegrees(
                android.media.ExifInterface(input).getAttributeInt(
                    android.media.ExifInterface.TAG_ORIENTATION,
                    android.media.ExifInterface.ORIENTATION_NORMAL,
                )
            )
        }
    }.getOrNull() ?: 0
    if (rotationDegrees == 0) return decoded
    val matrix = android.graphics.Matrix().apply { postRotate(rotationDegrees.toFloat()) }
    val oriented = android.graphics.Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    if (oriented !== decoded) decoded.recycle()
    return oriented
}

private fun androidExifOrientationDegrees(orientation: Int): Int = when (orientation) {
    android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
    android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
    android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
    else -> 0
}

private fun androidx.camera.core.ImageProxy.toLuminanceBytes(): ByteArray? {
    val yPlane = planes.getOrNull(0) ?: return null
    val output = ByteArray(width * height)
    val yBuffer = yPlane.buffer.duplicate()
    val start = yBuffer.position()
    for (row in 0 until height) {
        for (col in 0 until width) {
            val index = start + row * yPlane.rowStride + col * yPlane.pixelStride
            if (index >= yBuffer.limit()) return null
            output[row * width + col] = yBuffer.get(index)
        }
    }
    return output
}
