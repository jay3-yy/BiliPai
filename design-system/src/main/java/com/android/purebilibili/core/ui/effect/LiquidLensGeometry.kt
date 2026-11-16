package com.android.purebilibili.core.ui.effect

/** 折射区限于短边半径，使横向、纵向胶囊及收缩中的玻璃都保留正常采样的中心。 */
internal fun resolveLiquidLensRefractionHeight(
    requestedHeight: Float,
    width: Float,
    height: Float,
): Float = requestedHeight.coerceAtMost(minOf(width, height) * 0.5f)
