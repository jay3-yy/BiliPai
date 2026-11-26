package com.android.purebilibili.feature.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import com.android.purebilibili.core.ui.components.appCombinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import com.android.purebilibili.core.ui.components.appVerticalScroll as verticalScroll
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
import com.android.purebilibili.core.ui.components.AppSegmentOption
import com.android.purebilibili.core.ui.components.AppTabRowRole
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.components.AppThemeAdaptiveTabRow
import com.android.purebilibili.core.ui.motion.AppMotionTokens
import com.android.purebilibili.core.ui.util.rememberDeviceCornerShape
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.sqrt

// 进度有物理边界，沿用触点速度时也不越过端点再折返。
private const val DonationDialogStiffness = 500f
private val DonationDialogSpring = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = DonationDialogStiffness,
    visibilityThreshold = 0.001f,
)

@Composable
internal fun DonateDialog(visible: Boolean, origin: DonationDialogOrigin, onDismiss: () -> Unit) {
    val progress = remember { mutableFloatStateOf(0f) }
    var mounted by remember { mutableStateOf(false) }
    var positioned by remember { mutableStateOf(false) }
    val motionEnabled = LocalComponentMotionEnabled.current && !rememberSystemReduceMotion()
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
    fun finish(target: Float) {
        progress.floatValue = target
        gesture.velocity = 0f
        if (target == 0f) {
            mounted = false
            positioned = false
        }
    }
    fun settle(target: Float) {
        animation[0]?.cancel()
        if (!motionEnabled) {
            finish(target)
            return
        }
        animation[0] = scope.launch {
            val start = progress.floatValue
            val rate = sqrt(DonationDialogStiffness)
            val velocity = gesture.velocity.coerceIn(-start * rate, (1f - start) * rate)
            animate(start, target, initialVelocity = velocity, animationSpec = DonationDialogSpring) { value, sampleVelocity ->
                progress.floatValue = value.coerceIn(0f, 1f)
                gesture.velocity = sampleVelocity
            }
            finish(target)
        }
    }
    LaunchedEffect(visible, motionEnabled, positioned) {
        if (!visible && !mounted) return@LaunchedEffect
        gesture.tracking = false
        if (visible) mounted = true
        if (motionEnabled && !positioned) {
            if (!visible) mounted = false
            return@LaunchedEffect
        }
        settle(if (visible) 1f else 0f)
    }
    DisposableEffect(Unit) { onDispose { animation[0]?.cancel() } }
    if (mounted) DonateDialogContent(
        origin = origin,
        onPositioned = { positioned = true },
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
    onPositioned: () -> Unit,
    entranceProgress: () -> Float,
    backEnabled: Boolean,
    predictive: Boolean,
    onBackStarted: () -> Unit,
    onBackProgress: (Float) -> Unit,
    onBackCancelled: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var channel by rememberSaveable { mutableStateOf(DonationPaymentChannel.WECHAT) }
    val qrResource = donationQrResource(channel)
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var savedChannels by remember { mutableStateOf(emptySet<DonationPaymentChannel>()) }
    val saved = channel in savedChannels
    var showSaveConfirmation by remember { mutableStateOf(false) }
    val requestLongPressSave: () -> Unit = { if (!saving) showSaveConfirmation = true }
    val previewMotion = rememberDonationPreviewMotion()
    val dismissTopLayer: () -> Unit = {
        when {
            showSaveConfirmation -> showSaveConfirmation = false
            previewMotion.expanded -> previewMotion.close()
            else -> onDismiss()
        }
    }
    val latestDismissTopLayer by rememberUpdatedState(dismissTopLayer)
    // 紧凑态采用当前材质弹窗圆角，二维码展开态才逐角过渡到设备形状。
    val containerShape = rememberDeviceCornerShape(
        defaultRadius = AppShapes.containerCornerDp(ContainerLevel.Dialog),
    )
    var error by remember { mutableStateOf<Int?>(null) }
    fun save() {
        if (saving) return
        val targetChannel = channel
        val targetQrResource = donationQrResource(targetChannel)
        saving = true
        error = null
        scope.launch {
            try {
                if (saveDonationQrToGallery(context, targetQrResource)) {
                    savedChannels = savedChannels + targetChannel
                } else {
                    error = R.string.settings_donate_save_failed
                }
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
    val openPaymentApp: () -> Unit = {
        val packageName = when (channel) {
            DonationPaymentChannel.WECHAT -> "com.tencent.mm"
            DonationPaymentChannel.ALIPAY -> "com.eg.android.AlipayGphone"
        }
        val unavailableMessage = when (channel) {
            DonationPaymentChannel.WECHAT -> R.string.settings_donate_wechat_unavailable
            DonationPaymentChannel.ALIPAY -> R.string.settings_donate_alipay_unavailable
        }
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) error = unavailableMessage
        else try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            error = unavailableMessage
        } catch (_: SecurityException) {
            error = unavailableMessage
        }
    }
    Dialog(
        onDismissRequest = dismissTopLayer,
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
            onCompleted = dismissTopLayer,
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
                    .pointerInput(Unit) { detectTapGestures { latestDismissTopLayer() } })
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
                        onPositioned = onPositioned,
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
                                qrResource = qrResource,
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
                                        onOpenWechat = openPaymentApp,
                                        onEnlarge = previewMotion.open,
                                        onDismiss = onDismiss,
                                        modifier = Modifier.fillMaxWidth(),
                                        qrModifier = qrModifier,
                                        onQrLongPress = requestLongPressSave,
                                        channel = channel,
                                        onChannelChange = { next ->
                                            if (channel != next) {
                                                channel = next
                                                error = null
                                            }
                                        },
                                    )
                                },
                                preview = { qrModifier ->
                                    DonationQrPreview(
                                        qrResource = qrResource,
                                        onBack = previewMotion.close,
                                        onLongPress = requestLongPressSave,
                                        imageModifier = qrModifier,
                                    )
                                },
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
    channel: DonationPaymentChannel = DonationPaymentChannel.WECHAT,
    onChannelChange: (DonationPaymentChannel) -> Unit = {},
) {
    val wechatLabel = stringResource(R.string.settings_donate_channel_wechat)
    val alipayLabel = stringResource(R.string.settings_donate_channel_alipay)
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
        Spacer(Modifier.height(14.dp))
        AppThemeAdaptiveTabRow(
            options = listOf(
                AppSegmentOption(DonationPaymentChannel.WECHAT, wechatLabel),
                AppSegmentOption(DonationPaymentChannel.ALIPAY, alipayLabel),
            ),
            selectedValue = channel,
            onSelectionChange = onChannelChange,
            role = AppTabRowRole.SECONDARY,
            dragSelectionEnabled = true,
            tapPressRefractionEnabled = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                DonationQr(channel, onEnlarge, onQrLongPress, Modifier.weight(1f), qrModifier)
                DonationActions(channel, saving, saved, error, onSave, onOpenWechat, Modifier.weight(1f))
            }
        } else {
            DonationQr(channel, onEnlarge, onQrLongPress, Modifier.widthIn(max = 320.dp).fillMaxWidth().align(Alignment.CenterHorizontally), qrModifier)
            Spacer(Modifier.height(16.dp))
            DonationActions(channel, saving, saved, error, onSave, onOpenWechat, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun DonationQr(
    channel: DonationPaymentChannel,
    onEnlarge: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    imageModifier: Modifier = Modifier,
) {
    val motionEnabled = LocalComponentMotionEnabled.current && !rememberSystemReduceMotion()
    val slideSpec = AppMotionTokens.emphasizedSpec<androidx.compose.ui.unit.IntOffset>()
    val fadeSpec = AppMotionTokens.standardSpec<Float>()
    val scaleSpec = AppMotionTokens.emphasizedSpec<Float>()
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                // 过渡层包住完整图片装饰；原图已有底色，不另绘制固定白色底板。
                .then(imageModifier)
                .clip(AppShapes.container(ContainerLevel.Card))
                .appCombinedClickable(
                    onClickLabel = stringResource(R.string.settings_donate_enlarge),
                    onClick = onEnlarge,
                    onLongClickLabel = stringResource(R.string.settings_donate_save),
                    onLongClick = onLongPress,
                ),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = channel,
                transitionSpec = {
                    if (!motionEnabled) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                        (slideInHorizontally(slideSpec) { (it / 4) * direction } +
                            fadeIn(fadeSpec) +
                            scaleIn(scaleSpec, initialScale = 0.96f)) togetherWith
                            (slideOutHorizontally(slideSpec) { -(it / 4) * direction } +
                                fadeOut(fadeSpec) +
                                scaleOut(scaleSpec, targetScale = 0.96f)) using
                            SizeTransform(clip = false)
                    }
                },
                label = "donation_qr_channel_switch",
            ) { currentChannel ->
                Image(
                    painter = painterResource(donationQrResource(currentChannel)),
                    contentDescription = stringResource(R.string.settings_donate_qr_description),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
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
    channel: DonationPaymentChannel,
    saving: Boolean,
    saved: Boolean,
    error: Int?,
    onSave: () -> Unit,
    onOpenPaymentApp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motionEnabled = LocalComponentMotionEnabled.current && !rememberSystemReduceMotion()
    val textFadeSpec = AppMotionTokens.standardSpec<Float>()
    val textSlideSpec = AppMotionTokens.standardSpec<androidx.compose.ui.unit.IntOffset>()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppText(
            stringResource(R.string.settings_donate_voluntary),
            style = MaterialTheme.typography.titleSmall,
            color = AppContentColors.primaryText(),
        )
        AnimatedContent(
            targetState = channel,
            transitionSpec = {
                if (!motionEnabled) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (fadeIn(textFadeSpec) + slideInHorizontally(textSlideSpec) { (it / 6) * direction }) togetherWith
                        (fadeOut(textFadeSpec) + slideOutHorizontally(textSlideSpec) { -(it / 6) * direction }) using
                        SizeTransform(clip = false)
                }
            },
            label = "donation_steps_channel_switch",
        ) { currentChannel ->
            AppText(
                text = stringResource(
                    when (currentChannel) {
                        DonationPaymentChannel.WECHAT -> R.string.settings_donate_steps
                        DonationPaymentChannel.ALIPAY -> R.string.settings_donate_steps_alipay
                    }
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = AppContentColors.secondaryText(),
            )
        }
        if (saved) {
            AppText(
                stringResource(
                    when (channel) {
                        DonationPaymentChannel.WECHAT -> R.string.settings_donate_saved
                        DonationPaymentChannel.ALIPAY -> R.string.settings_donate_saved_alipay
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = AppContentColors.secondaryText(),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            AppButton(onClick = onOpenPaymentApp, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                AppText(
                    stringResource(
                        when (channel) {
                            DonationPaymentChannel.WECHAT -> R.string.settings_donate_open_wechat
                            DonationPaymentChannel.ALIPAY -> R.string.settings_donate_open_alipay
                        }
                    )
                )
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
private fun DonationQrPreview(
    @androidx.annotation.DrawableRes qrResource: Int,
    onBack: () -> Unit,
    onLongPress: () -> Unit,
    imageModifier: Modifier = Modifier,
) {
    val longPressLabel = stringResource(R.string.settings_donate_save)
    val feedbackLongPress = com.android.purebilibili.core.ui.components.appLongClickWithFeedback(onLongClick = onLongPress)
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
            painter = painterResource(qrResource),
            contentDescription = stringResource(R.string.settings_donate_qr_description),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(feedbackLongPress) { detectTapGestures(onLongPress = { feedbackLongPress() }) }
                .semantics { onLongClick(label = longPressLabel) { feedbackLongPress(); true } }
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
