package com.android.purebilibili.feature.article

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import com.android.purebilibili.core.ui.components.appVerticalScroll as verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppDialogAction
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton

@Composable
internal fun ArticleReadingSettingsDialog(
    preferences: ArticleReadingPreferences,
    onChange: (ArticleReadingPreferences) -> Unit,
    onDismiss: () -> Unit,
) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { AppText("阅读设置") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                AppText("正文字号")
                articleFontSizePresets.forEach { size ->
                    val selected = preferences.fontSizeSp == size
                    AppTextButton(
                        onClick = { onChange(preferences.copy(fontSizeSp = size)) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { this.selected = selected },
                    ) { AppText("${if (selected) "✓ " else ""}${size}sp", tapToCopyEnabled = false) }
                }
                AppText("行距")
                articleLineSpacingPresets.forEach { spacing ->
                    val selected = preferences.lineSpacingPercent == spacing
                    val label = when (spacing) { 115 -> "舒展"; 130 -> "宽松"; else -> "标准" }
                    AppTextButton(
                        onClick = { onChange(preferences.copy(lineSpacingPercent = spacing)) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { this.selected = selected },
                    ) { AppText("${if (selected) "✓ " else ""}$label", tapToCopyEnabled = false) }
                }
                AppText("阅读示例\n调整后会立即应用到专栏正文。",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyLarge.withArticleReadingPreferences(preferences))
            }
        },
        dismissButton = {
            AppDialogAction(onClick = { onChange(ArticleReadingPreferences()) }) { AppText("恢复默认", tapToCopyEnabled = false) }
        },
        confirmButton = { AppDialogAction(onClick = onDismiss) { AppText("完成", tapToCopyEnabled = false) } },
    )
}
