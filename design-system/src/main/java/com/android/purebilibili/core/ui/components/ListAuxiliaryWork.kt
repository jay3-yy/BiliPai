package com.android.purebilibili.core.ui.components

import androidx.compose.runtime.compositionLocalOf

/** Only optional row requests consume this permission; covers and gestures stay immediate. */
val LocalListAuxiliaryWorkAllowed = compositionLocalOf { true }
