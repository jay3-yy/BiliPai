package com.android.purebilibili.feature.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import kotlinx.coroutines.launch

@Composable
fun DonateDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val qrResource = donationQrResource()
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var saved by rememberSaveable { mutableStateOf(false) }
    var showSaveConfirmation by remember { mutableStateOf(false) }
    val requestLongPressSave: () -> Unit = { if (!saving) showSaveConfirmation = true }
    val previewMotion = rememberDonationPreviewMotion()
    // 从已挂载的宿主读取设备圆角，静止、展开和返回共用同一个外框形状。
    // 仅打赏容器显式采用设备形状，缺失时沿用当前材质的弹窗圆角。
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
        ModalWindowBackHandler(
            enabled = previewMotion.expanded && !showSaveConfirmation,
            predictive = previewMotion.predictive && LocalPredictiveBackGestureEnabled.current,
            onStarted = previewMotion.onBackStarted,
            onProgress = previewMotion.onBackProgress,
            onCancelled = previewMotion.onBackCancelled,
            onCompleted = previewMotion.close,
        )
        ModalWindowBlurBehindEffect()
        AppPopupWindowHost {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                val wide = maxWidth >= 600.dp && maxWidth > maxHeight
                AppPopupSurface(
                    type = AppPopupSurfaceType.DIALOG,
                    shape = containerShape,
                    containerColor = MaterialTheme.colorScheme.surface,
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
