package com.android.purebilibili.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.plugin.json.*
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton

/** 共用代码编辑表面，不以玻璃材质参数改变 MD3 或 Miuix 的输入行为。 */
@Composable
internal fun JsonSourceCodeField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    diagnostic: JsonSourceDiagnostic?,
    modifier: Modifier = Modifier,
    pluginType: String? = null,
    readOnly: Boolean = false,
    minLines: Int = 4,
    maxLines: Int = 18,
) {
    var editor by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(value)) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(value) {
        if (value != editor.text) editor = TextFieldValue(value, TextRange(value.length))
    }
    val suggestions = remember(editor.text, editor.selection, pluginType) {
        if (pluginType == null || !editor.selection.collapsed) emptyList()
        else completeJsonSource(editor.text, editor.selection.start, pluginType)
    }
    val textColor = AppContentColors.primaryText()
    val errorColor = AppContentColors.error()
    val accentColor = AppContentColors.accent()
    val currentDiagnostic = diagnostic.takeIf { editor.text == value }
    val transformation = remember(currentDiagnostic, errorColor) {
        VisualTransformation { text ->
            TransformedText(
                buildAnnotatedString {
                    append(text)
                    currentDiagnostic?.span?.let { span ->
                        // EOF 错误标在最后一个字符；行列提示仍指向文件末尾插入位置。
                        val start = span.start.coerceAtMost((text.length - 1).coerceAtLeast(0))
                        val end = span.end.coerceAtMost(text.length).coerceAtLeast((start + 1).coerceAtMost(text.length))
                        if (start < end) addStyle(
                            SpanStyle(color = errorColor, background = errorColor.copy(alpha = 0.12f), textDecoration = TextDecoration.Underline),
                            start, end,
                        )
                    }
                },
                OffsetMapping.Identity,
            )
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AppText(label, style = MaterialTheme.typography.labelLarge)
        AppSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            border = BorderStroke(1.dp, if (currentDiagnostic != null) errorColor else MaterialTheme.colorScheme.outlineVariant),
        ) {
            BasicTextField(
                value = editor,
                onValueChange = {
                    editor = it
                    if (it.text != value) onValueChange(it.text)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .focusRequester(focusRequester)
                    .semantics {
                        contentDescription = label
                        currentDiagnostic?.let { error(it.message) }
                    },
                readOnly = readOnly,
                minLines = minLines,
                maxLines = maxLines,
                textStyle = MaterialTheme.typography.bodySmall.copy(color = textColor, fontFamily = FontFamily.Monospace),
                cursorBrush = SolidColor(accentColor),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                visualTransformation = transformation,
            )
        }
        val beforeCursor = editor.text.substring(0, editor.selection.start)
        AppText(
            "光标：第 ${beforeCursor.count { it == '\n' } + 1} 行，第 ${beforeCursor.length - beforeCursor.lastIndexOf('\n')} 列",
            style = MaterialTheme.typography.labelSmall,
            color = AppContentColors.secondaryText(),
        )
        currentDiagnostic?.let { issue ->
            AppText(
                text = if (issue.line != null) "第 ${issue.line} 行，第 ${issue.column} 列：${issue.message}" else issue.message,
                style = MaterialTheme.typography.bodySmall,
                color = errorColor,
            )
            issue.span?.let { span ->
                AppTextButton(onClick = {
                    editor = editor.copy(selection = TextRange(span.start, span.end), composition = null)
                    focusRequester.requestFocus()
                }) { AppText("跳到错误") }
            }
        }
        if (suggestions.isNotEmpty() && !readOnly) {
            AppText("补全建议", style = MaterialTheme.typography.labelSmall, color = AppContentColors.secondaryText())
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                suggestions.forEach { suggestion ->
                    AppTextButton(onClick = {
                        val edit = applyJsonCompletion(editor.text, suggestion)
                        editor = TextFieldValue(edit.text, TextRange(edit.cursor))
                        onValueChange(edit.text)
                        focusRequester.requestFocus()
                    }) { AppText(suggestion.label) }
                }
            }
        }
    }
}
