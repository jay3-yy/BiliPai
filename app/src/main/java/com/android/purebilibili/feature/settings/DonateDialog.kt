package com.android.purebilibili.feature.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animate
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import com.android.purebilibili.core.ui.motion.AppMotionTokens
import com.android.purebilibili.core.ui.motion.rememberSystemReduceMotion
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.android.purebilibili.R
import com.android.purebilibili.core.ui.*
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.util.rememberDeviceCornerShape
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
internal fun DonateDialog(visible: Boolean, origin: DonationDialogOrigin, onDismiss: () -> Unit) {
    val progress = remember { mutableFloatStateOf(0f) }
    var mounted by remember { mutableStateOf(false) }
    val motionEnabled = LocalComponentMotionEnabled.current && !rememberSystemReduceMotion()
    val spec = AppMotionTokens.spatialSpec<Float>()
    val scope = rememberCoroutineScope()
    val animation = remember { arrayOfNulls<Job>(1) }
    val gesture = remember {
        object {
            var tracking = false
            var start = 1f
            var velocity = 0f
            var lastSampleNs = 0L
        }
    }
    fun settle(target: Float) {
        animation[0]?.cancel()
        animation[0] = scope.launch {
            if (motionEnabled) {
                animate(progress.floatValue, target, initialVelocity = gesture.velocity, animationSpec = spec) { value, velocity ->
                    progress.floatValue = value.coerceIn(0f, 1f)
                    gesture.velocity = velocity
                }
            }
            progress.floatValue = target
            gesture.velocity = 0f
            if (target == 0f) mounted = false
        }
    }
    LaunchedEffect(visible, motionEnabled) {
        if (!visible && !mounted) return@LaunchedEffect
        gesture.tracking = false
        if (visible) mounted = true
        settle(if (visible) 1f else 0f)
    }
    DisposableEffect(Unit) { onDispose { animation[0]?.cancel() } }
    if (mounted) DonateDialogContent(
        origin = origin,
        entranceProgress = { progress.floatValue },
        backEnabled = visible,
        predictive = motionEnabled,
        onBackStarted = {
            animation[0]?.cancel()
            gesture.tracking = true
            gesture.start = progress.floatValue
            gesture.velocity = 0f
            gesture.lastSampleNs = System.nanoTime()
        },
        onBackProgress = { backProgress ->
            if (gesture.tracking && motionEnabled) {
                val now = System.nanoTime()
                val next = gesture.start * (1f - backProgress.coerceIn(0f, 1f))
                val seconds = (now - gesture.lastSampleNs) / 1_000_000_000f
                if (seconds > 0f) gesture.velocity = ((next - progress.floatValue) / seconds).coerceIn(-10f, 10f)
                progress.floatValue = next
                gesture.lastSampleNs = now
            }
        },
        onBackCancelled = {
            if (gesture.tracking) {
                gesture.tracking = false
                settle(1f)
            }
        },
        onDismiss = {
            gesture.tracking = false
            onDismiss()
        },
    )
}

@Composable
private fun DonateDialogContent(
    origin: DonationDialogOrigin,
    entranceProgress: () -> Float,
    backEnabled: Boolean,
    predictive: Boolean,
    onBackStarted: () -> Unit,
    onBackProgress: (Float) -> Unit,
    onBackCancelled: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val qrResource = donationQrResource()
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var saved by rememberSaveable { mutableStateOf(false) }
    var showSaveConfirmation by remember { mutableStateOf(false) }
    val requestLongPressSave: () -> Unit = { if (!saving) showSaveConfirmation = true }
    val previewMotion = rememberDonationPreviewMotion()
    // 紧凑态采用当前材质弹窗圆角，二维码展开态才逐角过渡到设备形状。
    val containerShape = rememberDeviceCornerShape(
        defaultRadius = AppShapes.containerCornerDp(ContainerLevel.Dialog),
    )
    var error by remember { mutableStateOf<Int?>(null) }
    fun save() {
        if (saving) return
        saving = true
        error = null
        scope.launch {
            try {
                if (saveDonationQrToGallery(context, qrResource)) saved = true
                else error = R.string.settings_donate_save_failed
            } finally {
                saving = false
            }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) save() else error = R.string.settings_donate_permission_denied
    }
    val requestSave: () -> Unit = {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        else save()
    }
    val openWechat: () -> Unit = {
        val intent = context.packageManager.getLaunchIntentForPackage("com.tencent.mm")
        if (intent == null) error = R.string.settings_donate_wechat_unavailable
        else try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            error = R.string.settings_donate_wechat_unavailable
        } catch (_: SecurityException) {
            error = R.string.settings_donate_wechat_unavailable
        }
    }
    Dialog(
        onDismissRequest = {
            if (showSaveConfirmation) showSaveConfirmation = false
            else if (previewMotion.expanded) previewMotion.close() else onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // 同一手势只操作一层：二维码预览优先，其次收回外层弹窗。
        val backTargetsPreview = remember { booleanArrayOf(false) }
        ModalWindowBackHandler(
            enabled = backEnabled && !showSaveConfirmation,
            predictive = (if (previewMotion.expanded) previewMotion.predictive else predictive) &&
                LocalPredictiveBackGestureEnabled.current,
            onStarted = {
                backTargetsPreview[0] = previewMotion.expanded
                if (backTargetsPreview[0]) previewMotion.onBackStarted() else onBackStarted()
            },
            onProgress = { progress ->
                if (backTargetsPreview[0]) previewMotion.onBackProgress(progress) else onBackProgress(progress)
            },
            onCancelled = {
                if (backTargetsPreview[0]) previewMotion.onBackCancelled() else onBackCancelled()
            },
            // 按键返回没有 started/progress，也沿用当前可见层的优先级。
            onCompleted = { if (previewMotion.expanded) previewMotion.close() else onDismiss() },
        )
        // 窗口留到收回结束；背景与容器共用同一有限进度，避免窗口自带动画叠加。
        val dialogView = LocalView.current
        DisposableEffect(dialogView) {
            val window = (dialogView.parent as DialogWindowProvider).window
            val originalDim = window.attributes.dimAmount
            val originalAnimations = window.attributes.windowAnimations
            window.setDimAmount(0f)
            window.setWindowAnimations(0)
            onDispose {
                window.setDimAmount(originalDim)
                window.setWindowAnimations(originalAnimations)
            }
        }
        ModalWindowBlurBehindEffect(progress = entranceProgress)
        AppPopupWindowHost {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.matchParentSize()
                    .drawBehind { drawRect(Color.Black.copy(alpha = 0.32f * entranceProgress().coerceIn(0f, 1f))) }
                    .pointerInput(Unit) { detectTapGestures { if (previewMotion.expanded) previewMotion.close() else onDismiss() } })
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val wide = maxWidth >= 600.dp && maxWidth > maxHeight
                    val density = LocalDensity.current
                    val compactRadius = AppShapes.containerCornerDp(ContainerLevel.Dialog)
                    DonationDialogContainer(
                        origin = origin,
                        progress = entranceProgress,
                        previewProgress = { previewMotion.progress.value },
                        compactRadiusPx = with(density) { compactRadius.toPx() },
                        previewShape = containerShape,
                        modifier = Modifier.heightIn(max = maxHeight),
                    ) {
                        DonationPopupSurface(
                            previewProgress = { previewMotion.progress.value },
                            compactRadius = compactRadius,
                            previewShape = containerShape,
                            modifier = Modifier.heightIn(max = maxHeight),
                        ) {
                            DonationPreviewContainer(
                                progress = { previewMotion.progress.value },
                                previewActive = previewMotion.expanded,
                                compactMaxWidth = if (wide) 740.dp else 420.dp,
                                compact = { qrModifier ->
                                    DonationCardContent(
                                        wide = wide,
                                        saving = saving,
                                        saved = saved,
                                        error = error,
                                        onSave = requestSave,
                                        onOpenWechat = openWechat,
                                        onEnlarge = previewMotion.open,
                                        onDismiss = onDismiss,
                                        modifier = Modifier.fillMaxWidth(),
                                        qrModifier = qrModifier,
                                        onQrLongPress = requestLongPressSave,
                                    )
                                },
                                preview = { qrModifier -> DonationQrPreview(previewMotion.close, requestLongPressSave, qrModifier) },
                            )
                        }
                    }
                }
            }
        }
    }
    if (showSaveConfirmation) {
        AppAlertDialog(
            onDismissRequest = { showSaveConfirmation = false },
            title = { AppText(stringResource(R.string.settings_donate_save_confirm_title)) },
            text = { AppText(stringResource(R.string.settings_donate_save_confirm_message)) },
            confirmButton = {
                AppTextButton(onClick = { showSaveConfirmation = false; requestSave() }) {
                    AppText(stringResource(R.string.common_save))
                }
            },
            dismissButton = {
                AppTextButton(onClick = { showSaveConfirmation = false }) {
                    AppText(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

/** 外壳使用当前主题材质，二维码不染色、不裁剪，短窗口允许完整滚动。 */
@Composable
internal fun DonationCardContent(
    wide: Boolean,
    saving: Boolean,
    saved: Boolean,
    error: Int?,
    onSave: () -> Unit,
    onOpenWechat: () -> Unit,
    onEnlarge: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    qrModifier: Modifier = Modifier,
    onQrLongPress: () -> Unit = {},
) {
    Column(modifier = modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppText(
                text = stringResource(R.string.settings_donate_title),
                style = MaterialTheme.typography.titleLarge,
                color = AppContentColors.primaryText(),
                modifier = Modifier.weight(1f),
            )
            AppTextButton(onClick = onDismiss) { AppText(stringResource(R.string.common_close)) }
        }
        Spacer(Modifier.height(8.dp))
        AppText(
            text = stringResource(R.string.settings_donate_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = AppContentColors.secondaryText(),
        )
        Spacer(Modifier.height(20.dp))
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                DonationQr(onEnlarge, onQrLongPress, Modifier.weight(1f), qrModifier)
                DonationActions(saving, saved, error, onSave, onOpenWechat, Modifier.weight(1f))
            }
        } else {
            DonationQr(onEnlarge, onQrLongPress, Modifier.widthIn(max = 320.dp).fillMaxWidth().align(Alignment.CenterHorizontally), qrModifier)
            Spacer(Modifier.height(20.dp))
            DonationActions(saving, saved, error, onSave, onOpenWechat, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun DonationQr(onEnlarge: () -> Unit, onLongPress: () -> Unit, modifier: Modifier = Modifier, imageModifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painter = painterResource(donationQrResource()),
            contentDescription = stringResource(R.string.settings_donate_qr_description),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                // 过渡层包住完整图片装饰；原图已有底色，不另绘制固定白色底板。
                .then(imageModifier)
                .clip(AppShapes.container(ContainerLevel.Card))
                .combinedClickable(
                    onClickLabel = stringResource(R.string.settings_donate_enlarge),
                    onClick = onEnlarge,
                    onLongClickLabel = stringResource(R.string.settings_donate_save),
                    onLongClick = onLongPress,
                ),
        )
        Spacer(Modifier.height(8.dp))
        AppTextButton(onClick = onEnlarge) {
            AppText(
                stringResource(R.string.settings_donate_enlarge),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun DonationActions(
    saving: Boolean,
    saved: Boolean,
    error: Int?,
    onSave: () -> Unit,
    onOpenWechat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppText(
            stringResource(R.string.settings_donate_voluntary),
            style = MaterialTheme.typography.titleSmall,
            color = AppContentColors.primaryText(),
        )
        AppText(
            stringResource(R.string.settings_donate_steps),
            style = MaterialTheme.typography.bodyMedium,
            color = AppContentColors.secondaryText(),
        )
        if (saved) {
            AppText(
                stringResource(R.string.settings_donate_saved),
                style = MaterialTheme.typography.bodySmall,
                color = AppContentColors.secondaryText(),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            AppButton(onClick = onOpenWechat, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                AppText(stringResource(R.string.settings_donate_open_wechat))
            }
            AppTextButton(onClick = onSave, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                AppText(stringResource(if (saving) R.string.settings_donate_saving else R.string.settings_donate_save_again))
            }
        } else {
            AppButton(onClick = onSave, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                AppText(stringResource(if (saving) R.string.settings_donate_saving else R.string.settings_donate_save))
            }
        }
        if (error != null) {
            AppText(
                stringResource(error),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        AppText(
            stringResource(R.string.settings_donate_thanks),
            style = MaterialTheme.typography.bodySmall,
            color = AppContentColors.secondaryText(),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DonationQrPreview(onBack: () -> Unit, onLongPress: () -> Unit, imageModifier: Modifier = Modifier) {
    val longPressLabel = stringResource(R.string.settings_donate_save)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            AppTextButton(onClick = onBack) { AppText(stringResource(R.string.common_back)) }
        }
        Image(
            painter = painterResource(donationQrResource()),
            contentDescription = stringResource(R.string.settings_donate_qr_description),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(onLongPress) { detectTapGestures(onLongPress = { onLongPress() }) }
                .semantics { onLongClick(label = longPressLabel) { onLongPress(); true } }
                .then(imageModifier),
        )
        AppText(
            stringResource(R.string.settings_donate_preview_hint),
            color = AppContentColors.secondaryText(),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(12.dp),
        )
    }
}
