package com.android.purebilibili.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.appContentDialogWidth
import com.android.purebilibili.core.ui.resolveAppContentDialogLayoutPolicy
import com.android.purebilibili.core.ui.resolveAppContentDialogProperties
import kotlin.math.round
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.preference.WindowSpinnerPreference

enum class AppSingleChoicePresentation(val storageValue: String) {
    WINDOW_POPUP("window_popup"),
    CENTERED_DIALOG("centered_dialog");

    companion object {
        fun fromStorageValue(value: String?): AppSingleChoicePresentation =
            entries.firstOrNull { it.storageValue == value } ?: WINDOW_POPUP
    }
}

val LocalAppSingleChoicePresentation = compositionLocalOf {
    AppSingleChoicePresentation.WINDOW_POPUP
}

@Immutable
data class AppChoiceOption<T>(
    val value: T,
    val label: String,
    val description: String? = null,
)

@Composable
fun <T> AppSingleChoicePreference(
    title: String,
    selectedValue: T,
    options: List<AppChoiceOption<T>>,
    onValueChange: (T) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    enabled: Boolean = true,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    dialogTitle: String = title,
    presentation: AppSingleChoicePresentation = LocalAppSingleChoicePresentation.current,
) {
    if (presentation == AppSingleChoicePresentation.WINDOW_POPUP) {
        val selectedIndex = options.indexOfFirst { it.value == selectedValue }.coerceAtLeast(0)
        val dropdownItems = remember(options) {
            options.map { option ->
                DropdownItem(
                    text = option.label,
                    summary = option.description,
                )
            }
        }
        WindowSpinnerPreference(
            items = dropdownItems,
            selectedIndex = selectedIndex,
            title = title,
            summary = subtitle,
            enabled = enabled,
            modifier = modifier.alpha(if (enabled) 1f else 0.6f),
            startAction = icon?.let { imageVector ->
                {
                    Icon(
                        imageVector = imageVector,
                        contentDescription = null,
                        // 与其他设置条目一致：MD3 官方推荐预设下为 onSurfaceVariant 单色，
                        // 其余预设保留多彩语义色（MIUIX 等）。
                        tint = rememberAdaptivePreferenceIconTint(iconTint),
                        modifier = Modifier.size(24.dp),
                    )
                }
            },
            onSelectedIndexChange = { index ->
                options.getOrNull(index)?.value?.let { requestedValue ->
                    if (shouldDispatchAppChoiceSelection(selectedValue, requestedValue)) {
                        onValueChange(requestedValue)
                    }
                }
            },
        )
        return
    }

    var dialogVisible by rememberSaveable { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.value == selectedValue }?.label

    Box(modifier = modifier.alpha(if (enabled) 1f else 0.6f)) {
        AppPreference(
            icon = icon,
            title = title,
            subtitle = subtitle,
            value = selectedLabel,
            onClick = if (enabled) ({ dialogVisible = true }) else null,
            iconTint = iconTint,
            showChevron = enabled,
        )
    }

    if (dialogVisible) {
        AppSingleChoiceDialog(
            title = dialogTitle,
            selectedValue = selectedValue,
            options = options,
            onValueSelected = { value ->
                if (shouldDispatchAppChoiceSelection(selectedValue, value)) {
                    onValueChange(value)
                }
                dialogVisible = false
            },
            onDismissRequest = { dialogVisible = false },
        )
    }
}

@Composable
fun <T> AppSingleChoiceDialog(
    title: String,
    selectedValue: T,
    options: List<AppChoiceOption<T>>,
    onValueSelected: (T) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val maxDialogHeight = (configuration.screenHeightDp * 0.8f).dp
    val layoutPolicy = remember { resolveAppContentDialogLayoutPolicy(maxWidthDp = 420) }
    // Dialog 使用独立平台窗口；在进入窗口子组合前固定应用主题色，避免其默认色
    // 在“系统深色 + 应用手动浅色”时从窗口配置重新跟随系统。
    val dialogContainerColor = AppSurfaceTokens.cardContainer()
    val dialogContentColor = MaterialTheme.colorScheme.onSurface
    val dialogSecondaryContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = resolveAppContentDialogProperties(
            usePlatformDefaultWidth = layoutPolicy.usePlatformDefaultWidth,
        ),
    ) {
        Surface(
            modifier = modifier
                .appContentDialogWidth(policy = layoutPolicy, wrapHeight = false)
                .heightIn(max = maxDialogHeight),
            shape = AppShapes.container(ContainerLevel.Dialog),
            color = dialogContainerColor,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.padding(vertical = 12.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = dialogContentColor,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    options.forEach { option ->
                        val selected = option.value == selectedValue
                        AppSingleChoiceRow(
                            selected = selected,
                            onClick = { onValueSelected(option.value) },
                            modifier = Modifier
                                .fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = option.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = dialogContentColor,
                                )
                                option.description?.let { description ->
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = dialogSecondaryContentColor,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppSliderDialogPreference(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    steps: Int = 0,
    enabled: Boolean = true,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    valueFormatter: (Float) -> String = { it.toString() },
    dialogTitle: String = title,
) {
    var dialogVisible by rememberSaveable { mutableStateOf(false) }

    Box(modifier = modifier.alpha(if (enabled) 1f else 0.6f)) {
        AppPreference(
            icon = icon,
            title = title,
            subtitle = subtitle,
            value = valueFormatter(value),
            onClick = if (enabled) ({ dialogVisible = true }) else null,
            iconTint = iconTint,
            showChevron = enabled,
        )
    }

    if (dialogVisible) {
        AppSliderDialog(
            title = dialogTitle,
            value = value,
            valueRange = valueRange,
            steps = steps,
            valueFormatter = valueFormatter,
            onConfirm = { resolvedValue ->
                onValueChange(resolvedValue)
                dialogVisible = false
            },
            onDismissRequest = { dialogVisible = false },
        )
    }
}

/**
 * 滑块确认弹窗尺寸策略（委托统一内容 Dialog 策略）。
 *
 * 按钮区必须用内容尺寸按钮，不可复用 iOS Alert 的 [com.android.purebilibili.core.ui.AppDialogAction]
 * （会 fillMaxSize 撑满父级）。
 */
@Immutable
data class AppSliderDialogLayoutPolicy(
    val usePlatformDefaultWidth: Boolean,
    val horizontalPaddingDp: Int,
    val minWidthDp: Int,
    val maxWidthDp: Int,
)

fun resolveAppSliderDialogLayoutPolicy(): AppSliderDialogLayoutPolicy {
    val base = resolveAppContentDialogLayoutPolicy(maxWidthDp = 420)
    return AppSliderDialogLayoutPolicy(
        usePlatformDefaultWidth = base.usePlatformDefaultWidth,
        horizontalPaddingDp = base.horizontalPaddingDp,
        minWidthDp = base.minWidthDp,
        maxWidthDp = base.maxWidthDp,
    )
}

@Composable
fun AppSliderDialog(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onConfirm: (Float) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    valueFormatter: (Float) -> String = { it.toString() },
) {
    var draftValue by remember(value, valueRange, steps) {
        mutableFloatStateOf(resolveAppSliderDialogValue(value, valueRange, steps))
    }
    val layoutPolicy = remember { resolveAppSliderDialogLayoutPolicy() }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = resolveAppContentDialogProperties(
            usePlatformDefaultWidth = layoutPolicy.usePlatformDefaultWidth,
        ),
    ) {
        Surface(
            modifier = modifier.appContentDialogWidth(
                policy = resolveAppContentDialogLayoutPolicy(
                    maxWidthDp = layoutPolicy.maxWidthDp,
                    minWidthDp = layoutPolicy.minWidthDp,
                    horizontalPaddingDp = layoutPolicy.horizontalPaddingDp,
                ),
            ),
            shape = AppShapes.container(ContainerLevel.Dialog),
            color = AppSurfaceTokens.cardContainer(),
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = valueFormatter(draftValue),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Spacer(modifier = Modifier.height(8.dp))
                AppSlider(
                    value = draftValue,
                    onValueChange = { draftValue = it },
                    valueRange = valueRange,
                    steps = steps,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 内容尺寸按钮：避免 AppDialogAction 在 iOS 预设下 fillMaxSize 把弹窗撑满屏高
                    AppTextButton(onClick = onDismissRequest) {
                        Text("取消")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    AppTextButton(
                        onClick = {
                            onConfirm(resolveAppSliderDialogValue(draftValue, valueRange, steps))
                        },
                    ) {
                        Text("确定")
                    }
                }
            }
        }
    }
}

fun <T> shouldDispatchAppChoiceSelection(
    selectedValue: T,
    requestedValue: T,
): Boolean = selectedValue != requestedValue

fun resolveAppSliderDialogValue(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
): Float {
    val start = valueRange.start
    val end = valueRange.endInclusive
    if (end <= start) return start

    val clamped = value.coerceIn(start, end)
    val intervalCount = steps.coerceAtLeast(0) + 1
    if (intervalCount <= 1) return clamped

    val interval = (end - start) / intervalCount
    return (start + round((clamped - start) / interval) * interval).coerceIn(start, end)
}
