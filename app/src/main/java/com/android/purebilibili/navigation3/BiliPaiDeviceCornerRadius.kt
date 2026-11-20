// SPDX-License-Identifier: GPL-3.0-only
package com.android.purebilibili.navigation3

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.view.RoundedCorner
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

internal data class WindowCornerRadii(
    val topLeft: Float = 0f,
    val topRight: Float = 0f,
    val bottomRight: Float = 0f,
    val bottomLeft: Float = 0f,
)

/** 只把目标边界实际贴合的窗口圆角交给转场。 */
internal fun resolveWindowCornerRadii(
    view: View,
    destinationInWindow: Rect,
    isInMultiWindow: Boolean,
): WindowCornerRadii {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val insets = view.rootWindowInsets ?: return WindowCornerRadii()
        fun radius(position: Int, destination: Offset, xSign: Int, ySign: Int): Float {
            val corner = insets.getRoundedCorner(position) ?: return 0f
            val center = corner.center
            val x = center.x + xSign * corner.radius
            val y = center.y + ySign * corner.radius
            return if (abs(destination.x - x) <= 1f && abs(destination.y - y) <= 1f) {
                corner.radius.toFloat()
            } else 0f
        }
        return WindowCornerRadii(
            topLeft = radius(RoundedCorner.POSITION_TOP_LEFT, destinationInWindow.topLeft, -1, -1),
            topRight = radius(RoundedCorner.POSITION_TOP_RIGHT, destinationInWindow.topRight, 1, -1),
            bottomRight = radius(RoundedCorner.POSITION_BOTTOM_RIGHT, destinationInWindow.bottomRight, 1, 1),
            bottomLeft = radius(RoundedCorner.POSITION_BOTTOM_LEFT, destinationInWindow.bottomLeft, -1, 1),
        )
    }
    // 旧系统的资源只提供整屏半径，无法定位分屏窗口的实际角点。
    val root = view.rootView
    if (isInMultiWindow || destinationInWindow != Rect(0f, 0f, root.width.toFloat(), root.height.toFloat())) {
        return WindowCornerRadii()
    }
    val radius = getCornerRadiusBottom(view.context).toFloat()
    return WindowCornerRadii(radius, radius, radius, radius)
}

/**
 * Dynamically calculates the device's physical corner radius.
 *
 * API 31+ uses the public RoundedCorner API.
 * API 30 and below falls back to the system rounded_corner_radius_bottom resource.
 *
 * @param defaultRadius The fallback radius to use if the device does not report rounded corners.
 * @return The calculated corner radius in Dp.
 */
@Composable
internal fun rememberDeviceCornerRadius(defaultRadius: Dp = 0.dp): Dp {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current

    return remember(context, view, density, defaultRadius) {
        val radiusPx = getDeviceCornerRadiusPx(context, view)

        if (radiusPx > 0) {
            with(density) {
                radiusPx.toDp()
            }
        } else {
            defaultRadius
        }
    }
}

private fun getDeviceCornerRadiusPx(
    context: Context,
    view: View
): Int {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val publicRadius = getRoundedCornerRadiusPx(view)
        if (publicRadius > 0) return publicRadius
    }

    return getCornerRadiusBottom(context)
}

private fun getRoundedCornerRadiusPx(view: View): Int {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return 0

    val insets = view.rootWindowInsets ?: return 0

    val corner = insets.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)
        ?: insets.getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT)
        ?: insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)
        ?: insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT)

    return corner?.radius ?: 0
}

// from https://dev.mi.com/distribute/doc/details?pId=1631
@SuppressLint("DiscouragedApi")
private fun getCornerRadiusBottom(context: Context): Int {
    val resources = context.resources

    val bottomId = resources.getIdentifier(
        "rounded_corner_radius_bottom",
        "dimen",
        "android"
    )
    if (bottomId > 0) return resources.getDimensionPixelSize(bottomId)

    val generalId = resources.getIdentifier(
        "rounded_corner_radius",
        "dimen",
        "android"
    )
    return if (generalId > 0) {
        resources.getDimensionPixelSize(generalId)
    } else {
        0
    }
}
