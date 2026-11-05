package com.android.purebilibili.feature.settings

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.resolveAppContentDialogLayoutPolicy
import com.android.purebilibili.core.ui.AppDialogAction
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.R
import com.android.purebilibili.core.ui.LocalComponentMotionEnabled
import com.android.purebilibili.core.ui.LocalSystemReduceMotion
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppPreferenceSectionTitle
import com.android.purebilibili.core.ui.components.AppSingleChoicePreference
import com.android.purebilibili.core.ui.components.AppSingleChoicePresentation
import com.android.purebilibili.core.ui.components.AppChoiceOption
import com.android.purebilibili.core.ui.components.AppTextButton
import androidx.compose.ui.res.painterResource
import com.android.purebilibili.core.ui.components.formatPlaybackSpeed
import com.android.purebilibili.feature.dynamic.components.resolveImagePreviewGalleryPageTransform
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay

internal enum class SettingsFeaturePreview(
    val settingId: String,
    @StringRes val titleResId: Int,
    @StringRes val summaryResId: Int = 0,
) {
    TOP_BLUR("animation.progressive_top_blur_enabled", R.string.preview_title_top_blur, R.string.preview_summary_top_blur),
    IMAGE_PAGE("playback.image_preview3d_page_enabled", R.string.preview_title_image_page, R.string.preview_summary_image_page),
    LONG_PRESS("playback.long_press_speed", R.string.preview_title_long_press, R.string.preview_summary_long_press),
    CARD_ENTRANCE("animation.card_animation_enabled", R.string.preview_title_card_entrance, R.string.preview_summary_card_entrance),
    CARD_EXPAND("animation.card_transition_enabled", R.string.preview_title_card_expand, R.string.preview_summary_card_expand),
    LIVE_FRAME("animation.live_surface_card_transition_enabled", R.string.preview_title_live_frame, R.string.preview_summary_live_frame),
    TRANSITION_BLUR("animation.video_transition_realtime_blur_enabled", R.string.preview_title_transition_blur, R.string.preview_summary_transition_blur);
}

/** 演示状态只存在于面板内，不调用设置写入或真实播放器。材质由现有自适应控件处理。 */
@Composable
internal fun SettingsFeaturePreviewEntry(
    feature: SettingsFeaturePreview,
    currentEnabled: Boolean,
    modifier: Modifier = Modifier,
    speed: Float = 2f,
    transitionDurationMillis: Int? = null,
) {
    var open by rememberSaveable(feature) { mutableStateOf(false) }
    val previewLabel = stringResource(R.string.preview_accessibility, stringResource(feature.titleResId))
    AppIconButton(
        onClick = { open = true },
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .semantics { contentDescription = previewLabel },
    ) {
        AppIcon(
            painter = painterResource(R.drawable.ms_play_circle_outline_24),
            contentDescription = null,
            tint = AppContentColors.secondaryText(),
            modifier = Modifier.size(20.dp),
        )
    }
    if (open) {
        SettingsFeaturePreviewDialog(feature, currentEnabled, speed, transitionDurationMillis, onDismiss = { open = false })
    }
}

/** 卡片动画共用一个分组入口，列表中保留原有设置行密度。 */
@Composable
internal fun SettingsCardPreviewGroupHeader(
    enabledStates: Map<SettingsFeaturePreview, Boolean>,
    transitionDurationMillis: Int,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(SettingsFeaturePreview.CARD_EXPAND) }
    val previewAccessibilityLabel = stringResource(R.string.preview_card_accessibility)
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
        Box(Modifier.weight(1f)) { AppPreferenceSectionTitle(stringResource(R.string.animation_section_card)) }
        AppTextButton(
            onClick = { open = true },
            modifier = Modifier
                .padding(end = 16.dp)
                .heightIn(min = 48.dp)
                .semantics { contentDescription = previewAccessibilityLabel },
        ) { AppText(stringResource(R.string.preview_button), style = MaterialTheme.typography.labelLarge) }
    }
    if (open) {
        SettingsFeaturePreviewDialog(
            feature = selected,
            currentEnabled = enabledStates[selected] ?: false,
            speed = 2f,
            transitionDurationMillis = transitionDurationMillis.takeUnless { selected == SettingsFeaturePreview.CARD_ENTRANCE },
            onDismiss = { open = false },
            availableFeatures = enabledStates.keys.toList(),
            onFeatureSelected = { selected = it },
        )
    }
}

@Composable
private fun SettingsFeaturePreviewDialog(
    feature: SettingsFeaturePreview,
    currentEnabled: Boolean,
    speed: Float,
    transitionDurationMillis: Int?,
    onDismiss: () -> Unit,
    availableFeatures: List<SettingsFeaturePreview> = emptyList(),
    onFeatureSelected: ((SettingsFeaturePreview) -> Unit)? = null,
) {
    var enabled by rememberSaveable(feature) { mutableStateOf(currentEnabled) }
    var replay by rememberSaveable(feature) { mutableIntStateOf(0) }
    val progress = remember(feature) { Animatable(0f) }
    val reduceMotion = LocalSystemReduceMotion.current || !LocalComponentMotionEnabled.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val localizedFeatureOptions = availableFeatures.map {
        AppChoiceOption(it, context.getString(it.titleResId))
    }
    // 单次播放；关闭面板即取消。减少动态效果时展示中间状态供静态比较。
    LaunchedEffect(feature, enabled, replay, reduceMotion, transitionDurationMillis) {
        if (reduceMotion || currentCoroutineContext()[MotionDurationScale]?.scaleFactor == 0f) {
            progress.snapTo(0.5f)
        } else {
            progress.snapTo(0f)
            // 留出读清初始状态的时间，运动本身使用与真实转场一致的时长。
            if (transitionDurationMillis != null) delay(400)
            progress.animateTo(1f, tween(transitionDurationMillis ?: 3200, easing = LinearEasing))
        }
    }
    val contentMaxHeight = (LocalConfiguration.current.screenHeightDp.dp * 0.55f).coerceAtLeast(80.dp)
    val explanation = when (feature) {
        SettingsFeaturePreview.LONG_PRESS -> stringResource(feature.summaryResId, formatPlaybackSpeed(speed))
        else -> stringResource(feature.summaryResId)
    }
    AppAlertDialog(
        onDismissRequest = onDismiss,
        contentLayout = resolveAppContentDialogLayoutPolicy(maxWidthDp = 560, minWidthDp = 0, horizontalPaddingDp = 16),
        title = { AppText(if (availableFeatures.isEmpty()) stringResource(feature.titleResId) else stringResource(R.string.preview_card_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = contentMaxHeight)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (onFeatureSelected != null) {
                    AppSingleChoicePreference(
                        title = stringResource(R.string.preview_function_title),
                        selectedValue = feature,
                        options = localizedFeatureOptions,
                        presentation = AppSingleChoicePresentation.WINDOW_POPUP,
                        onValueChange = onFeatureSelected,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                AppText(explanation, color = AppContentColors.secondaryText())
                Row(Modifier.fillMaxWidth()) {
                    listOf(false, true).forEach { choice ->
                        AppTextButton(
                            onClick = { enabled = choice },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .semantics { selected = enabled == choice },
                        ) {
                            AppText(if (feature == SettingsFeaturePreview.LONG_PRESS) {
                                if (choice) stringResource(R.string.preview_long_press_demo) else stringResource(R.string.preview_normal_playback)
                            } else if (choice) stringResource(R.string.preview_enable_effect) else stringResource(R.string.preview_disable_effect))
                        }
                    }
                }
                AppText(
                    if (feature == SettingsFeaturePreview.LONG_PRESS) {
                        stringResource(if (enabled) R.string.preview_status_long_press_on else R.string.preview_status_long_press_off)
                    } else stringResource(if (enabled) R.string.preview_status_enabled else R.string.preview_status_disabled),
                    style = MaterialTheme.typography.labelMedium,
                )
                FeaturePreviewScene(
                    feature, enabled, { progress.value }, speed = speed,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                AppText(stringResource(R.string.preview_illustration_notice), style = MaterialTheme.typography.bodySmall,
                    color = AppContentColors.secondaryText())
                if (transitionDurationMillis != null) {
                    AppText(stringResource(R.string.preview_transition_speed_notice), style = MaterialTheme.typography.bodySmall)
                }
                if (reduceMotion) {
                    AppText(stringResource(R.string.preview_reduce_motion_notice), style = MaterialTheme.typography.bodySmall)
                }
                if (feature in setOf(SettingsFeaturePreview.TOP_BLUR, SettingsFeaturePreview.TRANSITION_BLUR) && Build.VERSION.SDK_INT < 31) {
                    AppText(stringResource(R.string.preview_blur_unavailable_notice), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        dismissButton = { AppDialogAction(onClick = { replay++ }) { AppText(stringResource(R.string.preview_replay)) } },
        confirmButton = { AppDialogAction(onClick = onDismiss) { AppText(stringResource(R.string.common_close)) } },
    )
}

@Composable
private fun FeaturePreviewScene(
    feature: SettingsFeaturePreview,
    enabled: Boolean,
    progress: () -> Float,
    modifier: Modifier = Modifier,
    speed: Float = 2f,
) {
    val accent = AppContentColors.accent()
    val ink = AppContentColors.primaryText()
    val sceneDescription = when (feature) {
        SettingsFeaturePreview.TOP_BLUR -> stringResource(if (enabled) R.string.preview_scene_top_blur_on else R.string.preview_scene_top_blur_off)
        SettingsFeaturePreview.IMAGE_PAGE -> stringResource(if (enabled) R.string.preview_scene_image_3d_on else R.string.preview_scene_image_3d_off)
        SettingsFeaturePreview.LONG_PRESS -> stringResource(if (enabled) R.string.preview_scene_long_press_on else R.string.preview_scene_long_press_off)
        else -> stringResource(if (enabled) R.string.preview_scene_enabled else R.string.preview_scene_disabled, stringResource(feature.titleResId))
    }
    Box(modifier.semantics { contentDescription = sceneDescription }) {
        when (feature) {
            SettingsFeaturePreview.TOP_BLUR -> {
                PreviewScrollingCards(progress, accent, Modifier.fillMaxSize())
                // 仅预览画布使用局部模糊，绝不修改宿主页或关闭玻璃后的原生组件参数。
                if (enabled) {
                    Box(Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(0.dp))) {
                        PreviewScrollingCards(progress, accent, Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                drawContent()
                                drawRect(Brush.verticalGradient(listOf(Color.Black, Color.Transparent), endY = 64.dp.toPx()), blendMode = BlendMode.DstIn)
                            }
                            .blur(10.dp))
                    }
                }
                // 标题直接悬浮在滚动内容上，不添加真实顶栏没有的独立底色遮罩。
                Box(
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AppText(stringResource(R.string.preview_home_top_title), color = ink)
                }
            }
            SettingsFeaturePreview.IMAGE_PAGE -> {
                repeat(2) { page ->
                    Canvas(Modifier.fillMaxSize().graphicsLayer {
                        val offset = page - progress()
                        translationX = offset * size.width
                        if (enabled) {
                            val transform = resolveImagePreviewGalleryPageTransform(offset, size.width)
                            rotationY = transform.rotationY
                            transformOrigin = TransformOrigin(transform.pivotFractionX, 0.5f)
                            translationX += transform.translationXPx
                            scaleX = transform.scale
                            scaleY = transform.scale
                            alpha = transform.alpha
                            cameraDistance = 16f * density
                        }
                    }) {
                        drawRect(if (page == 0) accent.copy(alpha = 0.25f) else accent.copy(alpha = 0.45f))
                        drawCircle(accent, radius = size.minDimension * 0.16f, center = Offset(size.width * 0.72f, size.height * 0.3f))
                        drawPath(Path().apply {
                            moveTo(0f, size.height)
                            lineTo(size.width * 0.35f, size.height * (if (page == 0) 0.35f else 0.6f))
                            lineTo(size.width * 0.65f, size.height * 0.7f)
                            lineTo(size.width, size.height * 0.45f)
                            lineTo(size.width, size.height)
                            close()
                        }, accent)
                    }
                }
                AppText(stringResource(R.string.preview_image_pair), modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp), color = ink)
            }
            SettingsFeaturePreview.LONG_PRESS -> {
                Canvas(Modifier.fillMaxSize()) {
                    val p = progress()
                    val holding = enabled && p in 0.25f..0.75f
                    // 同一时间尺度：按住期间按当前设置倍速推进，1x 时保持原速。
                    val rate = 0.85f / (0.5f + 0.5f * speed.coerceIn(1f, 8f))
                    val position = if (enabled) when {
                        p < 0.25f -> p * rate
                        p <= 0.75f -> (0.25f + (p - 0.25f) * speed) * rate
                        else -> (0.25f + 0.5f * speed + p - 0.75f) * rate
                    } else p * rate
                    drawLine(ink.copy(alpha = 0.15f), Offset(20.dp.toPx(), size.height * 0.75f), Offset(size.width - 20.dp.toPx(), size.height * 0.75f), 4.dp.toPx())
                    drawCircle(accent, 7.dp.toPx(), Offset(20.dp.toPx() + (size.width - 40.dp.toPx()) * position, size.height * 0.75f))
                    if (holding) {
                        drawCircle(accent.copy(alpha = 0.18f), 24.dp.toPx(), center)
                        drawCircle(accent, 9.dp.toPx(), center)
                    }
                }
                val stage by remember(enabled, progress) {
                    derivedStateOf {
                        if (!enabled) 0 else when {
                            progress() < 0.25f -> 0
                            progress() <= 0.75f -> 1
                            else -> 2
                        }
                    }
                }
                AppText(
                    when (stage) {
                        1 -> stringResource(R.string.preview_hold_speed, formatPlaybackSpeed(speed))
                        2 -> stringResource(R.string.preview_release_speed)
                        else -> stringResource(R.string.preview_normal_speed)
                    },
                    modifier = Modifier.align(Alignment.TopCenter).padding(12.dp), color = ink,
                )
            }
            else -> SettingsCardMotionPreview(feature, enabled, progress, Modifier.fillMaxSize())
        }
    }
}

@Composable
internal fun PreviewScrollingCards(progress: () -> Float, accent: Color, modifier: Modifier = Modifier) {
    val background = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier) {
        drawRect(background)
        repeat(6) { row ->
            val y = row * 56.dp.toPx() - progress() * 100.dp.toPx()
            drawRoundRect(accent.copy(alpha = if (row % 2 == 0) 0.7f else 0.3f),
                topLeft = Offset(12.dp.toPx(), y), size = Size(size.width * 0.32f, 44.dp.toPx()))
            drawRoundRect(accent.copy(alpha = 0.35f),
                topLeft = Offset(size.width * 0.4f, y + 8.dp.toPx()), size = Size(size.width * 0.5f, 8.dp.toPx()))
            drawRoundRect(accent.copy(alpha = 0.2f),
                topLeft = Offset(size.width * 0.4f, y + 24.dp.toPx()), size = Size(size.width * 0.35f, 8.dp.toPx()))
        }
    }
}
