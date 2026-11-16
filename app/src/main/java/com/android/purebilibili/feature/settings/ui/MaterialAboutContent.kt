package com.android.purebilibili.feature.settings

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import coil3.compose.AsyncImage
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.BuildConfig
import com.android.purebilibili.core.ui.common.rememberClipboardCopyHandler
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton

/** MD3 首屏使用主题色角色和字阶；原生与玻璃共用几何，不使用 Miuix 渐变参数。 */
@Composable
internal fun MaterialAboutOverview(
    versionName: String,
    appIconRes: Int,
    versionHint: String?,
    onVersionClick: () -> Unit,
    onThemeSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val copyVersion = rememberClipboardCopyHandler()
    Column(modifier = modifier) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            shape = RoundedCornerShape(topStart = 48.dp, topEnd = 20.dp, bottomEnd = 48.dp, bottomStart = 20.dp),
            color = colors.primaryContainer,
            contentColor = colors.onPrimaryContainer,
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .drawWithCache {
                        val radius = size.minDimension * 0.48f
                        onDrawBehind {
                            drawCircle(
                                color = colors.secondaryContainer,
                                radius = radius,
                                center = Offset(size.width, size.height),
                            )
                        }
                    }
                    .padding(28.dp),
            ) {
                val iconSize = if (maxWidth < 300.dp) 72.dp else 88.dp
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Surface(shape = CircleShape, color = colors.secondaryContainer) {
                        // 应用图标可能包含自适应图标的前景与背景层，使用 Drawable 资源加载。
                        AsyncImage(
                            model = appIconRes,
                            contentDescription = "BiliPai 图标",
                            modifier = Modifier
                                .padding(12.dp)
                                .size(iconSize)
                                .clip(CircleShape),
                        )
                    }
                    AppText(
                        text = "BiliPai",
                        style = MaterialTheme.typography.displayLarge,
                        color = colors.onPrimaryContainer,
                        autoSize = TextAutoSize.StepBased(minFontSize = 28.sp, maxFontSize = 57.sp),
                        maxLines = 1,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AppText(
                        text = "简单一点，专注内容",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onPrimaryContainer,
                    )
                    Surface(
                        modifier = Modifier
                            .clip(CircleShape)
                            .combinedClickable(
                                role = Role.Button,
                                onClickLabel = "查看版本",
                                onLongClickLabel = "复制版本号",
                                onClick = onVersionClick,
                                onLongClick = { copyVersion("v$versionName", "版本") },
                            ),
                        shape = CircleShape,
                        color = colors.primary,
                        contentColor = colors.onPrimary,
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            AppText(
                                text = "$versionName (${BuildConfig.VERSION_CODE})",
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.onPrimary,
                                tapToCopyEnabled = false,
                            )
                            versionHint?.let {
                                AppText(
                                    text = it,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.onPrimary,
                                    tapToCopyEnabled = false,
                                )
                            }
                        }
                    }
                    AppTextButton(onClick = onThemeSettingsClick) {
                        AppText("主题与配色", color = colors.onPrimaryContainer, tapToCopyEnabled = false)
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        AboutDevelopersSection(modifier = Modifier.fillMaxWidth())
    }
}
