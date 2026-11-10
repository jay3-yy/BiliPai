package com.android.purebilibili.navigation3

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.transition.LocalVideoCardSharedElementSourceRoute
import com.android.purebilibili.core.ui.transition.videoCardSourceBoundsInRoot
import com.android.purebilibili.core.util.CardPositionManager
import kotlin.math.roundToInt

/**
 * 系统一镜到底窗口来源：内容卡片点击时向视频转场管线登记几何，
 * 与视频卡片共用锚点、像素归属与返回落点。identity 不含冒号，与 route:identity 来源键规则兼容。
 */
internal class SystemWindowCardSource internal constructor(
    val identity: String,
    private val sourceRoute: String?,
    private val screenWidthPx: Float,
    private val screenHeightPx: Float,
    private val densityValue: Float,
) {
    var coordinates: androidx.compose.ui.layout.LayoutCoordinates? = null

    fun boundsInRoot(): Rect? = coordinates?.takeIf { it.isAttached }?.videoCardSourceBoundsInRoot()

    /** 点击时先于路由跳转调用；未登记成功时调用方继续原窗口内导航。 */
    fun record(cornerRadiusPx: Float, layer: GraphicsLayer? = null) {
        val bounds = boundsInRoot() ?: return
        CardPositionManager.recordVideoCardPosition(
            bvid = identity,
            sourceRoute = sourceRoute,
            bounds = bounds,
            screenWidth = screenWidthPx,
            screenHeight = screenHeightPx,
            density = densityValue,
            sourceCornerDp = cornerRadiusPx.takeIf { it > 0f }
                ?.let { (it / densityValue).roundToInt() },
        )
        // 登记当前点击自己的整卡像素，视口裁切时不从窗口截图补齐被遮挡区域。
        CardPositionManager.recordNativeCardLayer(
            layer?.takeIf { !it.isReleased && it.size == coordinates?.size },
        )
    }
}

/** 窗口可承接的容器目标键；其余键返回 null，调用方保持原窗口内导航。 */
internal fun resolveSystemWindowCardIdentity(target: BiliPaiNavKey?): String? = when (target) {
    is BiliPaiNavKey.AudioMode -> target.sourceBvid.takeIf(String::isNotBlank)
    is BiliPaiNavKey.Live -> "live_${target.roomId}"
    is BiliPaiNavKey.BangumiPlayer -> "bangumi_${target.seasonId}"
    is BiliPaiNavKey.BangumiDetail -> "bangumi_${target.seasonId}"
    is BiliPaiNavKey.ArticleDetail -> "article_${target.articleId}"
    is BiliPaiNavKey.DynamicDetail -> "dynamic_${target.dynamicId}"
    is BiliPaiNavKey.Space -> "space_${target.mid}"
    is BiliPaiNavKey.LiveAreaDetail -> "live_area_${target.parentAreaId}_${target.areaId}"
    is BiliPaiNavKey.Web -> if (com.android.purebilibili.feature.web.shouldOpenWebUrlExternally(target.url)) {
        null
    } else {
        "web_${java.util.UUID.nameUUIDFromBytes(target.url.toByteArray(Charsets.UTF_8))}"
    }
    else -> null
}

@Composable
internal fun rememberSystemWindowCardSource(target: BiliPaiNavKey?): SystemWindowCardSource? {
    val identity = resolveSystemWindowCardIdentity(target) ?: return null
    val sourceRoute = LocalVideoCardSharedElementSourceRoute.current
    val configuration: Configuration = LocalConfiguration.current
    val density = LocalDensity.current
    return remember(
        identity, sourceRoute, configuration.screenWidthDp, configuration.screenHeightDp, density,
    ) {
        SystemWindowCardSource(
            identity = identity,
            sourceRoute = sourceRoute,
            screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() },
            screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() },
            densityValue = density.density,
        )
    }
}
