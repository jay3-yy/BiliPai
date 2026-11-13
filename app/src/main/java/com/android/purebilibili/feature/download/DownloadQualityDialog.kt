package com.android.purebilibili.feature.download

import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.components.AppSingleChoiceRow
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.AppSpacingTokens

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalContext
import com.android.purebilibili.core.ui.components.AppButton
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
//  Material Icons
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.android.purebilibili.core.ui.appContentDialogWidth
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppOutlinedButton
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.resolveAppContentDialogLayoutPolicy
import com.android.purebilibili.core.ui.resolveAppContentDialogProperties
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppPopupSurface
import com.android.purebilibili.core.ui.AppPopupSurfaceType
import com.android.purebilibili.core.ui.ContainerLevel

/**
 *  下载画质选择对话框
 */
@Composable
fun DownloadQualityDialog(
    title: String,
    qualityOptions: List<Pair<Int, String>>,  // (qualityId, qualityLabel)
    currentQuality: Int,
    onQualitySelected: (Int, DownloadOptions) -> Unit,
    onDismiss: () -> Unit
) {
    val preferences = rememberDownloadPreferenceState(qualityOptions, currentQuality) ?: return
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val dialogLayout = remember { resolveAppContentDialogLayoutPolicy(maxWidthDp = 420) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = resolveAppContentDialogProperties(
            usePlatformDefaultWidth = dialogLayout.usePlatformDefaultWidth,
        ),
    ) {
        com.android.purebilibili.core.ui.ModalWindowBlurBehindEffect()
        AppPopupSurface(
            type = AppPopupSurfaceType.DIALOG,
            modifier = Modifier.appContentDialogWidth(policy = dialogLayout),
            shape = AppShapes.container(ContainerLevel.Card),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(AppSpacingTokens.Large),
            ) {
                // 标题行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppText(
                        text = "选择下载画质",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppContentColors.primaryText()
                    )
                    AppIconButton(onClick = onDismiss) {
                        AppIcon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "取消",
                            tint = AppContentColors.secondaryText()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(AppSpacingTokens.Small))

                // 视频标题
                AppText(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppContentColors.secondaryText(),
                    maxLines = 2
                )

                Spacer(modifier = Modifier.height(AppSpacingTokens.Large))

                DownloadPreferenceControls(preferences, Modifier.fillMaxWidth())

                Spacer(modifier = Modifier.height(AppSpacingTokens.Small))

                // 画质列表
                qualityOptions.forEach { (qualityId, qualityLabel) ->
                    val isSelected = qualityId == preferences.selectedQuality
                    val isVip = qualityLabel.contains("4K") || qualityLabel.contains("HDR") || qualityLabel.contains("杜比")

                    AppSingleChoiceRow(
                        selected = isSelected,
                        onClick = {
                            preferences.selectedQuality = qualityId
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppText(
                                text = qualityLabel,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = AppSurfaceTokens.onSurfaceContainerHigh()
                            )
                            if (isVip) {
                                Spacer(modifier = Modifier.width(AppSpacingTokens.Small))
                                AppSurface(
                                    color = MaterialTheme.colorScheme.tertiary,
                                    shape = AppShapes.container(ContainerLevel.Tag)
                                ) {
                                    AppText(
                                        text = "VIP",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onTertiary,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(AppSpacingTokens.Small))
                }

                Spacer(modifier = Modifier.height(8.dp))

                AppButton(
                    onClick = {
                        scope.launch { preferences.confirm(context, onQualitySelected) }
                    },
                    enabled = !preferences.saving && qualityOptions.any { it.first == preferences.selectedQuality },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    AppText("加入下载")
                }

                // 取消按钮
                AppOutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShapes.container(ContainerLevel.Chip)
                ) {
                    AppText("取消")
                }
            }
        }
    }
}
