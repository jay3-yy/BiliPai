package com.android.purebilibili.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.TextUnit

/** Compact toolbar labels use the active MD3 or Miuix theme's adaptive label role. */
@Composable
fun appTabLabelFontSize(): TextUnit = MaterialTheme.typography.labelMedium.fontSize
