package com.android.purebilibili.feature.download

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import com.android.purebilibili.core.ui.components.appVerticalScroll as verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppDialogAction
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.AppSingleChoiceRow
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.resolveAppContentDialogLayoutPolicy
import kotlinx.coroutines.launch

/**
 *  下载画质选择对话框
 */
@Composable
fun DownloadQualityDialog(
    title: String,
    qualityOptions: List<Pair<Int, String>>,  // (qualityId, qualityLabel)
    currentQuality: Int,
    onQualitySelected: (Int, DownloadOptions) -> Unit,
    onDismiss: () -> Unit,
    vipQualityIds: Set<Int>,
) {
    val preferences = rememberDownloadPreferenceState(qualityOptions, currentQuality) ?: return
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    AppAlertDialog(
        onDismissRequest = onDismiss,
        contentLayout = remember { resolveAppContentDialogLayoutPolicy(maxWidthDp = 420) },
        title = { AppText("选择下载画质") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                AppText(
                    text = title,
                    color = AppContentColors.secondaryText(),
                    maxLines = 2,
                )
                Spacer(modifier = Modifier.height(AppSpacingTokens.Large))
                DownloadPreferenceControls(preferences, Modifier.fillMaxWidth())

                Spacer(modifier = Modifier.height(AppSpacingTokens.Small))

                // 画质列表
                qualityOptions.forEach { (qualityId, qualityLabel) ->
                    val isSelected = qualityId == preferences.selectedQuality

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
                            if (qualityId in vipQualityIds) {
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
            }
        },
        confirmButton = {
            AppTextButton(
                onClick = {
                    scope.launch { preferences.confirm(context, onQualitySelected) }
                },
                enabled = !preferences.saving && qualityOptions.any { it.first == preferences.selectedQuality },
            ) {
                AppText("加入下载")
            }
        },
        dismissButton = {
            AppDialogAction(onClick = onDismiss) { AppText("取消") }
        },
    )
}
