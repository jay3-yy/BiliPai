package com.android.purebilibili.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import top.yukonga.miuix.kmp.basic.NumberPicker

/** The caller owns the pending value; selecting a number never commits a timer. */
@Composable
fun AppNumberPicker(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    range: IntRange = 0..59,
    label: String = "分钟",
) {
    val changeWithFeedback = appValueChangeWithFeedback<Int>(
        scene = com.android.purebilibili.core.ui.AppHapticScene.ADJUSTMENT,
        onValueChange = onValueChange,
    )
    if (LocalAppUiStyle.current == AppUiStyle.MIUIX) {
        Column(modifier = modifier) {
            AppText(label)
            com.android.purebilibili.core.ui.ProvideAppHapticScene(com.android.purebilibili.core.ui.AppHapticScene.ADJUSTMENT) {
                NumberPicker(
                    value = value.coerceIn(range),
                    onValueChange = onValueChange,
                    range = range,
                    visibleItemCount = 3,
                    wrapAround = false,
                    modifier = Modifier.heightIn(min = 135.dp),
                )
            }
        }
    } else {
        var input by remember { mutableStateOf(value.toString()) }
        var lastEditedValue by remember { mutableStateOf(value) }
        LaunchedEffect(value) {
            if (value != lastEditedValue) input = value.toString()
            lastEditedValue = value
        }
        Column(modifier = modifier) {
            com.android.purebilibili.core.ui.ProvideAppHapticScene(com.android.purebilibili.core.ui.AppHapticScene.TEXT) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { raw ->
                        if (raw.all(Char::isDigit)) {
                            val parsed = if (raw.isEmpty() && 0 in range) 0 else raw.toIntOrNull()
                            if (parsed != null && parsed in range) {
                                input = raw
                                lastEditedValue = parsed
                                onValueChange(parsed)
                            }
                        }
                    },
                    label = { Text(label) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.widthIn(min = 96.dp).onFocusChanged {
                        if (!it.isFocused) input = value.toString()
                    },
                )
            }
            Row {
                TextButton(
                    enabled = value > range.first,
                    onClick = { changeWithFeedback((value - 1).coerceIn(range)) },
                ) { Text("−", style = MaterialTheme.typography.titleLarge) }
                TextButton(
                    enabled = value < range.last,
                    onClick = { changeWithFeedback((value + 1).coerceIn(range)) },
                ) { Text("+", style = MaterialTheme.typography.titleLarge) }
            }
        }
    }
}
