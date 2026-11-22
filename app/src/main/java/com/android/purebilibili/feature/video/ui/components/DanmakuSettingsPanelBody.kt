package com.android.purebilibili.feature.video.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import com.android.purebilibili.core.ui.components.appVerticalScroll as verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton

/** 固定标题与完成操作，只有设置内容滚动；玻璃与原生材质共用同一几何。 */
@Composable
internal fun DanmakuSettingsPanelBody(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "弹幕设置",
    scrollState: ScrollState = rememberScrollState(),
    navigation: (@Composable () -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.padding(horizontal = 14.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                com.android.purebilibili.core.ui.components.AppIconButton(onClick = onBack) {
                    com.android.purebilibili.core.ui.components.AppIcon(
                        androidx.compose.material.icons.Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "返回常用设置",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            AppText(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            AppTextButton(onClick = onDismiss) { AppText("完成") }
        }
        navigation?.invoke()
        Column(
            Modifier.weight(1f, fill = false)
                .verticalScroll(scrollState)
                .padding(bottom = 16.dp),
            content = content,
        )
    }
}

/** 管理和次级入口不叠加实色卡片，保留完整触控行。 */
@Composable
internal fun DanmakuSettingsAction(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppTextButton(onClick = onClick, modifier = modifier.fillMaxWidth().heightIn(min = 44.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AppText(title, modifier = Modifier.weight(1f))
            AppText("›")
        }
    }
}

/** 同窗玻璃内仅用文字和细分隔线分组，关闭玻璃的入口保留原生容器。 */
@Composable
internal fun DanmakuSettingsSectionTitle(title: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(top = 16.dp, bottom = 8.dp)) {
        com.android.purebilibili.core.ui.components.AppHorizontalDivider(
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
        )
        AppText(
            title,
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
internal fun DanmakuSettingsToggle(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppText(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        com.android.purebilibili.core.ui.components.AppSwitch(checked, onCheckedChange)
    }
}
