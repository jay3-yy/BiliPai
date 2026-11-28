package com.android.purebilibili.danmaku.engine

import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Typeface

const val DANMAKU_LAYER_SCROLL = 1001
const val DANMAKU_LAYER_TOP = 1002
const val DANMAKU_LAYER_BOTTOM = 1003
const val DANMAKU_LAYER_REVERSE = 2001

/** Renderer-neutral standard danmaku model used by the app and parser layers. */
open class DanmakuItem {
    var danmakuId: Long = 0L
    /** Original merged content before adding a display count suffix. */
    var mergedOriginalText: String? = null
    var userHash: String = ""
    var text: String? = null
    var showAtTime: Long = 0L
    var layerType: Int = DANMAKU_LAYER_SCROLL
    /** Semantic size relative to the renderer's user-configured base size. */
    var textSizeScale: Float = 1f
    /** Explicit render size in pixels. Prefer [textSizeScale] for server-defined size grades. */
    var textSize: Float? = null
    var textColor: Int? = null
    var typeface: Typeface? = null
    var textStrokeWidth: Float? = null
    var textStrokeColor: Int? = null
    var includeFontPadding: Boolean? = null
    var hasUnderline: Boolean = false
    var weight: Int = 0
    var pool: Int = 0
    var attr: Int = 0
    var likeCount: Long = 0L
    var isVipGradualColor: Boolean = false
    var duplicateCount: Int = 0
    var isSelf: Boolean = false
    var isUpOwner: Boolean = false
    var bitmap: Bitmap? = null
    var bitmapWidth: Float = 0f
    var bitmapHeight: Float = 0f

    fun copy(): DanmakuItem = DanmakuItem().also { target ->
        target.danmakuId = danmakuId
        target.mergedOriginalText = mergedOriginalText
        target.userHash = userHash
        target.text = text
        target.showAtTime = showAtTime
        target.layerType = layerType
        target.textSizeScale = textSizeScale
        target.textSize = textSize
        target.textColor = textColor
        target.typeface = typeface
        target.textStrokeWidth = textStrokeWidth
        target.textStrokeColor = textStrokeColor
        target.includeFontPadding = includeFontPadding
        target.hasUnderline = hasUnderline
        target.weight = weight
        target.pool = pool
        target.attr = attr
        target.likeCount = likeCount
        target.isVipGradualColor = isVipGradualColor
        target.duplicateCount = duplicateCount
        target.isSelf = isSelf
        target.isUpOwner = isUpOwner
        target.bitmap = bitmap
        target.bitmapWidth = bitmapWidth
        target.bitmapHeight = bitmapHeight
    }
}

data class DanmakuWindow(
    val anchorSegment: Int,
    val segmentIndices: List<Int>,
    val items: List<DanmakuItem>
)

data class DanmakuMaskFrame(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val path: Path?,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val visibilityPicture: android.graphics.Picture? = null,
)

enum class DanmakuPlaybackState {
    STOPPED,
    PLAYING,
    PAUSED,
    CLOSED
}

enum class DanmakuMaskScaleMode {
    FIT_CENTER,
    CENTER_CROP,
    FILL,
}

data class DanmakuRenderConfig(
    val alpha: Int = 255,
    val textSizePx: Float = 48f,
    val typeface: Typeface? = Typeface.DEFAULT,
    val strokeWidthPx: Float = 2.75f,
    val strokeColor: Int = 0x61000000,
    val scrollDurationMs: Long = 7_000L,
    val lineHeightPx: Float = 64f,
    val lineMarginPx: Float = 0f,
    val lineCount: Int = 8,
    val topMarginPx: Float = 0f,
    val bottomMarginPx: Float = 0f,
    val pinnedDurationMs: Long = 4_000L,
    val playSpeedPercent: Int = 100,
    val maskEnabled: Boolean = false,
    val maskScaleMode: DanmakuMaskScaleMode = DanmakuMaskScaleMode.FIT_CENTER,
)

data class DanmakuEngineDiagnostics(
    val replaceWindowCount: Long,
    val appendBatchCount: Long,
    val appendedItemCount: Long,
    val trimCount: Long,
    val residentItemCount: Int
)
