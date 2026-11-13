package com.android.purebilibili.feature.settings

import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.Canvas
import com.android.purebilibili.core.store.BiliPaiIconColorSettings
import com.android.purebilibili.core.store.biliPaiThemeIconBackground
import com.android.purebilibili.core.store.automaticBiliPaiIconBackground
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.R
import com.android.purebilibili.core.store.biliPaiThemeIconColor
import com.android.purebilibili.core.store.getBiliPaiIconColorSettings
import com.android.purebilibili.core.store.getBiliPaiIconColorSettingsSync
import com.android.purebilibili.core.store.resolveBiliPaiThemeIconIndex
import com.android.purebilibili.core.theme.LocalAppThemeIconPrimary
import com.android.purebilibili.core.theme.parseMd3CustomColorHex
import com.android.purebilibili.core.theme.formatMd3CustomColorHex
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.AppPrimaryButton
import com.android.purebilibili.core.ui.components.AppSegmentOption
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import kotlin.math.roundToInt

@Composable
internal fun BiliPaiIconLiveColorEditor(
    onApply: (colors: BiliPaiIconColorSettings, appPrimaryColor: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val primary = LocalAppThemeIconPrimary.current.toArgb()
    val flow = remember(context) { getBiliPaiIconColorSettings(context) }
    val saved by flow.collectAsStateWithLifecycle(initialValue = getBiliPaiIconColorSettingsSync(context))
    var editing by rememberSaveable { mutableStateOf(false) }
    val requested = saved.foreground ?: primary
    val matchedIndex = resolveBiliPaiThemeIconIndex(requested, saved.background)
    val matched = biliPaiThemeIconColor(matchedIndex)
    Column(
        modifier = modifier
            .clip(AppShapes.container(ContainerLevel.Card))
            .background(AppSurfaceTokens.cardContainer())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppText(stringResource(R.string.icon_live_color_title), style = MaterialTheme.typography.titleMedium)
        AppText(stringResource(R.string.icon_live_color_summary),
            style = MaterialTheme.typography.bodySmall, color = AppContentColors.secondaryText())
        BiliPaiAdaptiveColorPreview(matched, stringResource(R.string.icon_live_color_matched),
            Modifier.size(96.dp).align(Alignment.CenterHorizontally), biliPaiThemeIconBackground(matchedIndex))
        AppText(
            stringResource(if (saved.foreground == null) R.string.icon_live_color_follow_app else R.string.icon_live_color_independent) +
                " · " + formatMd3CustomColorHex(Color(matched)),
            style = MaterialTheme.typography.bodySmall,
            color = AppContentColors.secondaryText(),
        )
        AppPrimaryButton(
            text = stringResource(R.string.icon_live_color_edit),
            onClick = { editing = true },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (editing) {
        BiliPaiIconColorDialog(
            initialColors = saved,
            appPrimary = primary,
            onDismiss = { editing = false },
            onApply = { selected -> onApply(selected, primary); editing = false },
        )
    }
}

/** 两个预览均由 AdaptiveIconDrawable 施加系统遮罩，避免完整 108dp 图层预览与桌面比例不同。 */
@Composable
private fun BiliPaiAdaptiveColorPreview(argb: Int, description: String, modifier: Modifier = Modifier, background: Int? = null) {
    val context = LocalContext.current
    val drawable = remember(context, argb, background) {
        val foreground = requireNotNull(ContextCompat.getDrawable(context, R.drawable.ic_bilipai_mark)).mutate()
        foreground.setTint(argb)
        val backgroundColor = background ?: automaticBiliPaiIconBackground(argb)
        AdaptiveIconDrawable(ColorDrawable(backgroundColor), foreground)
    }
    Canvas(modifier = modifier.semantics { contentDescription = description }) {
        drawIntoCanvas { canvas ->
            drawable.setBounds(0, 0, size.width.roundToInt(), size.height.roundToInt())
            drawable.draw(canvas.nativeCanvas)
        }
    }
}

@Composable
private fun BiliPaiIconColorDialog(
    initialColors: BiliPaiIconColorSettings,
    appPrimary: Int,
    onDismiss: () -> Unit,
    onApply: (BiliPaiIconColorSettings) -> Unit,
) {
    var independent by rememberSaveable { mutableStateOf(initialColors.foreground != null) }
    var draft by rememberSaveable { mutableIntStateOf(initialColors.foreground ?: appPrimary) }
    var draftBackground by rememberSaveable { mutableStateOf(initialColors.background) }
    var editingBackground by rememberSaveable { mutableStateOf(false) }
    var resetEditor by rememberSaveable { mutableIntStateOf(0) }
    var foregroundValid by rememberSaveable { mutableStateOf(true) }
    var backgroundValid by rememberSaveable { mutableStateOf(true) }
    val requested = if (independent) draft else appPrimary
    val matchedIndex = resolveBiliPaiThemeIconIndex(requested, draftBackground)
    val matched = biliPaiThemeIconColor(matchedIndex)
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { AppText(stringResource(R.string.icon_live_color_edit)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(
                        requested to R.string.icon_live_color_requested,
                        matched to R.string.icon_live_color_matched,
                    ).forEach { (color, label) ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val description = stringResource(label)
                            BiliPaiAdaptiveColorPreview(color, description,
                                Modifier.widthIn(max = 96.dp).fillMaxWidth().aspectRatio(1f),
                                if (label == R.string.icon_live_color_matched) biliPaiThemeIconBackground(matchedIndex) else draftBackground)
                            AppText(description, style = MaterialTheme.typography.labelMedium)
                            AppText(formatMd3CustomColorHex(Color(color)), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                AppText(stringResource(R.string.icon_live_color_preview_notice),
                    style = MaterialTheme.typography.bodySmall, color = AppContentColors.secondaryText())
                SettingsSingleChoicePreference(
                    title = stringResource(R.string.icon_live_color_source),
                    options = listOf(
                        AppSegmentOption(false, stringResource(R.string.icon_live_color_follow_app)),
                        AppSegmentOption(true, stringResource(R.string.icon_live_color_independent)),
                    ),
                    selectedValue = independent,
                    onSelectionChange = { independent = it; foregroundValid = true; backgroundValid = true; resetEditor++ },
                )
                SettingsSingleChoicePreference(
                    title = stringResource(R.string.icon_live_color_target),
                    options = listOf(
                        AppSegmentOption(false, stringResource(R.string.icon_palette_foreground)),
                        AppSegmentOption(true, stringResource(R.string.icon_palette_background)),
                    ),
                    selectedValue = editingBackground,
                    onSelectionChange = { editingBackground = it; foregroundValid = true; backgroundValid = true },
                )
                if (editingBackground) {
                    AppTextButton(onClick = { draftBackground = null; backgroundValid = true; resetEditor++ }) {
                        AppText(stringResource(if (draftBackground == null) R.string.icon_palette_auto_background_selected
                            else R.string.icon_palette_auto_background))
                    }
                } else if (!independent) {
                    AppText(stringResource(R.string.icon_live_color_follow_summary),
                        style = MaterialTheme.typography.bodySmall, color = AppContentColors.secondaryText())
                }
                key(editingBackground, resetEditor) {
                    IconContinuousColorSelection(
                        color = if (editingBackground) draftBackground ?: automaticBiliPaiIconBackground(requested) else requested,
                        onColorChange = { color ->
                            if (editingBackground) draftBackground = color else { draft = color; independent = true }
                        },
                        onValidityChange = { valid ->
                            if (editingBackground) backgroundValid = valid else foregroundValid = valid
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            AppTextButton(
                enabled = foregroundValid && backgroundValid,
                onClick = { onApply(BiliPaiIconColorSettings(if (independent) draft else null, draftBackground)) },
            ) {
                AppText(stringResource(R.string.icon_live_color_apply))
            }
        },
        dismissButton = { AppTextButton(onClick = onDismiss) { AppText(stringResource(R.string.common_cancel)) } },
    )
}


/** 编辑过程只更新草稿，取色展示复用主题颜色编辑器。 */
@Composable
private fun IconContinuousColorSelection(
    color: Int,
    onColorChange: (Int) -> Unit,
    onValidityChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val initialHsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(color, it) } }
    var hue by rememberSaveable { mutableFloatStateOf(initialHsv[0]) }
    var saturation by rememberSaveable { mutableFloatStateOf(initialHsv[1]) }
    var brightness by rememberSaveable { mutableFloatStateOf(initialHsv[2]) }
    var pendingHex by rememberSaveable { mutableStateOf(formatMd3CustomColorHex(Color(color))) }
    var lastEmitted by rememberSaveable { mutableIntStateOf(color) }
    LaunchedEffect(color) {
        if (color != lastEmitted) {
            val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(color, it) }
            hue = hsv[0]; saturation = hsv[1]; brightness = hsv[2]
            pendingHex = formatMd3CustomColorHex(Color(color))
            lastEmitted = color
            onValidityChange(true)
        }
    }
    ThemeColorSelectionControls(
        hue = hue, saturation = saturation, brightness = brightness, pendingHex = pendingHex,
        modifier = modifier,
        onHsvChange = { h, s, v ->
            hue = h; saturation = s; brightness = v
            val next = Color.hsv(h, s, v)
            pendingHex = formatMd3CustomColorHex(next)
            lastEmitted = next.toArgb()
            onValidityChange(true)
            onColorChange(lastEmitted)
        },
        onHexChange = { input ->
            pendingHex = input.trim().uppercase().take(7)
            val valid = isValidMd3CustomColorHex(pendingHex)
            onValidityChange(valid)
            if (valid) {
                val next = parseMd3CustomColorHex(pendingHex).toArgb()
                val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(next, it) }
                hue = hsv[0]; saturation = hsv[1]; brightness = hsv[2]
                lastEmitted = next
                onColorChange(next)
            }
        },
    )
}
