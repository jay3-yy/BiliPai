package com.android.purebilibili.feature.video.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppFilterChip
import com.android.purebilibili.core.ui.components.AppText

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DanmakuCompactColorGrid(
    selectedColor: Int,
    onColorSelected: (Int) -> Unit,
    onCustomColor: () -> Unit,
    vipColorAvailable: Boolean,
    modifier: Modifier = Modifier,
) {
    val presets = danmakuSendColorOptions()
    val options = presets +
        (if (vipColorAvailable) listOf(DanmakuSendOption(DANMAKU_SEND_VIP_GRADUAL_COLOR, "会员渐变色")) else emptyList()) +
        (if (selectedColor >= 0 && presets.none { it.value == selectedColor }) {
            listOf(DanmakuSendOption(selectedColor, "当前自定义颜色"))
        } else emptyList())
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val shape = RoundedCornerShape(8.dp)
            Box(
                modifier = Modifier.size(40.dp)
                    .border(2.dp, if (selectedColor == option.value) AppContentColors.accent() else Color.Transparent, shape)
                    .padding(3.dp)
                    .then(if (option.value == DANMAKU_SEND_VIP_GRADUAL_COLOR) {
                        Modifier.background(Brush.linearGradient(listOf(Color(0xFFDD94DA), Color(0xFF72B2EA))), shape)
                    } else Modifier.background(Color(option.value or 0xFF000000.toInt()), shape))
                    .selectable(selectedColor == option.value, role = Role.RadioButton,
                        onClick = { onColorSelected(option.value) })
                    .semantics { contentDescription = option.label },
            )
        }
        Box(
            modifier = Modifier.size(40.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp))
                .selectable(false, role = Role.Button, onClick = onCustomColor),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(Icons.Outlined.Edit, contentDescription = "自定义颜色",
                tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
internal fun DanmakuColorOptions(
    selectedColor: Int,
    onColorSelected: (Int) -> Unit,
    onCustomColor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = danmakuSendColorOptions()
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            Box(
                Modifier.size(36.dp)
                    .background(Color(option.value or 0xFF000000.toInt()), RoundedCornerShape(8.dp))
                    .border(2.dp, if (selectedColor == option.value) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    .selectable(selected = selectedColor == option.value, role = Role.RadioButton,
                        onClick = { onColorSelected(option.value) })
                    .semantics { contentDescription = option.label },
            )
        }
        AppFilterChip(
            selected = selectedColor >= 0 && options.none { it.value == selectedColor },
            onClick = onCustomColor,
            label = { AppText("自定义") },
        )
    }
}
