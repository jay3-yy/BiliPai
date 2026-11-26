package com.android.purebilibili.feature.personal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppHapticScene
import com.android.purebilibili.core.ui.AppModalBottomSheet
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppOutlinedTextField
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.rememberAppHapticFeedback

/**
 * 追番/合集/多 P 最后一集看完后的克制完结留念弹窗。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CompletionKeepsakeSheet(
    prompt: CompletionKeepsakePrompt,
    onSave: (reflection: String) -> Unit,
    onDismiss: () -> Unit,
    initialReflection: String = "",
    isEditing: Boolean = false,
) {
    val haptics = rememberAppHapticFeedback(AppHapticScene.CONTROLS)
    var reflection by remember(prompt.id, initialReflection) { mutableStateOf(initialReflection) }

    AppModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppSurface(
                    shape = AppShapes.container(ContainerLevel.Chip),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        AppIcon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(15.dp),
                        )
                        AppText(
                            text = "${prompt.kind.badgeLabel} · 全 ${prompt.episodeCount} 集",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (prompt.coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = prompt.coverUrl,
                        contentDescription = prompt.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 96.dp, height = 60.dp)
                            .aspectRatio(16f / 10f)
                            .clip(AppShapes.container(ContainerLevel.Chip)),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    AppText(
                        text = if (isEditing) "编辑完结短评" else "看完啦，恭喜完结撒花",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = AppContentColors.primaryText(),
                    )
                    AppText(
                        text = prompt.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppContentColors.secondaryText(),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            AppOutlinedTextField(
                value = reflection,
                onValueChange = { reflection = it.take(140) },
                label = { AppText("完结短评（可选）") },
                placeholder = { AppText("留一句给未来自己的感受吧，仅保存在本地…") },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AppTextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    AppText(if (isEditing) "取消" else "先跳过")
                }
                AppButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSave(reflection.trim())
                    },
                    modifier = Modifier.weight(1.4f),
                ) {
                    AppText(if (isEditing) "保存修改" else "收进完结纪念")
                }
            }
        }
    }
}
