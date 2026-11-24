package com.android.purebilibili.feature.video.ui.pager

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.ui.components.AppText

internal const val PORTRAIT_MAX_CACHED_PAGE_METADATA = 160

/**
 * 为 VerticalPager 生成稳定 key，避免头部插页、合集插页或仅竖屏重过滤时按索引错位复用状态。
 * 同一列表中极少数重复项通过首次出现之后的索引后缀消歧，避免 Compose 重复 key 异常。
 */
internal fun resolvePortraitPagerItemKey(
    pageItems: List<Any>,
    index: Int,
): String {
    val item = pageItems.getOrNull(index) ?: return "page:$index"
    val identity = resolvePortraitPagePlaybackIdentity(item)
        ?: return "page:$index"
    val baseKey = portraitCollectionIdentityKey(identity.bvid, identity.cid)
    if (baseKey.isBlank()) return "page:$index"
    val duplicateBefore = (0 until index).any { previousIndex ->
        val previousIdentity = pageItems.getOrNull(previousIndex)
            ?.let(::resolvePortraitPagePlaybackIdentity)
            ?: return@any false
        portraitCollectionIdentityKey(previousIdentity.bvid, previousIdentity.cid) == baseKey
    }
    return if (duplicateBefore) "$baseKey#$index" else baseKey
}

/**
 * 计算放大画面平移后的受限偏移量（双指缩放平移与单指拖动共用）。
 */
internal fun resolvePortraitZoomedPan(
    currentPanX: Float,
    currentPanY: Float,
    deltaX: Float,
    deltaY: Float,
    scale: Float,
    containerWidth: Float,
    containerHeight: Float,
): Pair<Float, Float> {
    if (scale <= 1f || containerWidth <= 0f || containerHeight <= 0f) {
        return 0f to 0f
    }
    val maxPanX = ((containerWidth * scale - containerWidth) / 2f).coerceAtLeast(0f)
    val maxPanY = ((containerHeight * scale - containerHeight) / 2f).coerceAtLeast(0f)
    val nextX = (currentPanX + deltaX).coerceIn(-maxPanX, maxPanX)
    val nextY = (currentPanY + deltaY).coerceIn(-maxPanY, maxPanY)
    return nextX to nextY
}

/**
 * 裁剪长时刷视频累计的页级缓存，保留当前活跃键集合。
 */
internal fun <K, V> trimPortraitMetadataMap(
    map: MutableMap<K, V>,
    maxSize: Int = PORTRAIT_MAX_CACHED_PAGE_METADATA,
    keepKeys: Set<K> = emptySet(),
) {
    if (map.size <= maxSize) return
    val removable = map.keys.filter { it !in keepKeys }
    val excess = map.size - maxSize
    removable.take(excess).forEach { map.remove(it) }
}

/**
 * 画面处于放大状态时展示的轻量复位入口，避免用户锁在放大态误以为无法上下滑切页。
 */
@Composable
internal fun PortraitZoomResetPill(
    visible: Boolean,
    scale: Float,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
            .statusBarsPadding()
            .padding(top = 56.dp),
    ) {
        val percent = (scale * 100f).toInt().coerceAtLeast(100)
        AppText(
            text = "${percent}% · 点击或双击恢复",
            color = Color.White,
            fontSize = 12.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(Color.Black.copy(alpha = 0.62f))
                .clickable(onClick = onReset)
                .padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}
