package com.android.purebilibili.feature.dynamic.components

import android.content.Context
import android.content.Intent
import com.android.purebilibili.feature.video.ui.components.shouldOfferCommentTranslation

internal fun shouldOfferDynamicTranslation(text: String): Boolean {
    if (text.isBlank()) return false
    return shouldOfferCommentTranslation(
        switch = null,
        message = text,
        oid = 1L,
        rpid = 1L,
    )
}

internal fun launchSystemTextTranslation(context: Context, text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return false
    val translateIntent = Intent(Intent.ACTION_TRANSLATE).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, trimmed)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (translateIntent.resolveActivity(context.packageManager) != null) {
        context.startActivity(translateIntent)
        return true
    }
    val processTextIntent = Intent(Intent.ACTION_PROCESS_TEXT).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_PROCESS_TEXT, trimmed)
        putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (processTextIntent.resolveActivity(context.packageManager) != null) {
        context.startActivity(Intent.createChooser(processTextIntent, "翻译正文").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }
    return false
}
