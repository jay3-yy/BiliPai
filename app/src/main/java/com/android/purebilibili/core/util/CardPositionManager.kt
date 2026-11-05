package com.android.purebilibili.core.util

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import com.android.purebilibili.core.ui.transition.VideoCardSourceChromeSnapshot
import com.android.purebilibili.core.ui.transition.VideoCardSourceLayout
import com.android.purebilibili.core.ui.transition.ownsVideoCardSourceSnapshot
import com.android.purebilibili.core.ui.transition.resolveVideoCardSourceLayout
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

private const val QUICK_RETURN_THRESHOLD_MS = 500L
private const val HOME_CATEGORY_SOURCE_PREFIX = "home?category="

internal fun shouldUseQuickReturnSharedTransitionPolicy(
    detailEnterUptimeMs: Long,
    detailReturnUptimeMs: Long,
    thresholdMs: Long = QUICK_RETURN_THRESHOLD_MS
): Boolean {
    if (detailEnterUptimeMs <= 0L || detailReturnUptimeMs < detailEnterUptimeMs) return false
    return detailReturnUptimeMs - detailEnterUptimeMs <= thresholdMs
}

/** Click identity is deliberately referential: restoring a route/key cannot relabel pixels. */
internal class VideoCardPixelOwner(
    val sourceKey: String,
    val sourceInstanceId: Long?,
) {
    // 每次点击独立等待详情首轮挂载；恢复来源元数据不能替新会话放行。
    var entryWorkReleased by mutableStateOf(false)
        private set

    fun releaseEntryWork() {
        entryWorkReleased = true
    }
}

internal class WholeCardReturnSnapshot(
    val owner: VideoCardPixelOwner,
    val bitmap: ImageBitmap,
)

/**
 *  卡片位置管理器
 * 
 * 用于记录点击卡片的位置，以便在返回动画时
 * 将缩放动画指向正确的卡片位置
 */
object CardPositionManager {
    private val videoCardSourceInstanceSequence = AtomicLong(0L)

    /**
     * 导航层最近一次真正放行跳转的时间戳（[System.currentTimeMillis]）。
     *
     * 卡片点击会先同步覆写“最后点击卡片”记录，之后才进入导航防抖；被防抖拒绝的相邻点击
     * 同样会清空在飞转场的冻结像素并翻转全局 key，飞行详情被迫退化到重建信息板。
     * 卡片在记录前必须先用同一时钟、同一窗口判断本次点击是否注定被拒绝，被拒绝时不得
     * 触碰全局转场记录。窗口值与 [com.android.purebilibili.navigation.AppTopLevelNavigationPolicy]
     * 的防抖参数保持一致。
     */
    @Volatile
    private var lastNavigationGrantedMillis: Long = 0L

    internal fun recordNavigationDebounceGrant(grantedMillis: Long) {
        lastNavigationGrantedMillis = grantedMillis
    }

    fun isVideoNavigationDebounceActive(
        nowMillis: Long = System.currentTimeMillis(),
        debounceWindowMillis: Long = 300L,
    ): Boolean = nowMillis - lastNavigationGrantedMillis <= debounceWindowMillis

    
    /**
     * 最后点击的卡片边界（在 Root 坐标系中）
     */
    var lastClickedCardBounds: Rect? = null
        private set

    /** 最后点击卡片的真实封面边界（在 Root 坐标系中）。 */
    var lastClickedCoverBounds: Rect? = null
        private set

    /**
     * 最后点击卡片的**信息面**（标题/统计/UP/日期那一块）边界（在 Root 坐标系中）。
     *
     * 与 [lastClickedCoverBounds] 一样属于「点击时冻结」的快照。重建来源卡壳的信息板
     * 必须落在这一块上；用「卡底 − 封面底」推导会在卡片测量高度与列表实际信息面不等价
     * （例如点击瞬间卡片还没量到最终高度）时留下一段没被盖住的近白带。
     */
    var lastClickedInfoBounds: Rect? = null
        private set

    /**
     * 最后点击的卡片中心点（归一化坐标 0-1）
     */
    var lastClickedCardCenter: Offset? = null
        private set

    var lastClickedVideoSourceKey: String? = null
        private set

    internal var lastClickedVideoSourceInstanceId: Long? = null
        private set

    internal var lastClickedVideoPixelOwner: VideoCardPixelOwner? = null
        private set

    private var activeVideoSourceOwner: VideoCardPixelOwner? by mutableStateOf(null)

    /** 恢复父来源只改变活跃归属，不得让子页最后一次点击继续发布像素。 */
    internal fun isActiveVideoCardPixelOwner(owner: VideoCardPixelOwner?): Boolean =
        owner != null && owner === lastClickedVideoPixelOwner && owner === activeVideoSourceOwner &&
            owner.sourceKey == lastClickedVideoSourceKey &&
            owner.sourceInstanceId == lastClickedVideoSourceInstanceId

    internal fun videoCardPixelOwnerFor(sourceKey: String?): VideoCardPixelOwner? =
        lastClickedVideoPixelOwner?.takeIf {
            sourceKey != null && it.sourceKey == sourceKey
        }

    internal fun newVideoCardSourceInstanceId(): Long =
        videoCardSourceInstanceSequence.incrementAndGet()

    var lastClickedVideoSourceCornerDp: Int? = null
        private set

    // internal: VideoCardSourceLayout is module-internal and must not leak from a public API.
    internal var lastClickedVideoSourceLayout: VideoCardSourceLayout =
        VideoCardSourceLayout.COVER_ONLY
        private set

    internal var lastClickedVideoSourceChromeSnapshot: VideoCardSourceChromeSnapshot? = null
        private set

    private var nativeResourcesOwner: VideoCardPixelOwner? = null
    private var nativeCardLayer: GraphicsLayer? by mutableStateOf(null)
    private var nativeCoverOverlayLayer: GraphicsLayer? by mutableStateOf(null)
    private var nativeCardBitmap: ImageBitmap? by mutableStateOf(null)

    private fun ownsNativeResources(): Boolean {
        val owner = nativeResourcesOwner ?: return false
        return owner === activeVideoSourceOwner &&
            owner.sourceKey == lastClickedVideoSourceKey &&
            owner.sourceInstanceId == lastClickedVideoSourceInstanceId
    }

    /** Frozen native resources must not be relabelled by restoring a parent's source metadata. */
    internal val lastClickedNativeCardLayer: GraphicsLayer?
        get() = nativeCardLayer.takeIf { ownsNativeResources() }

    internal val lastClickedNativeCoverOverlayLayer: GraphicsLayer?
        get() = nativeCoverOverlayLayer.takeIf { ownsNativeResources() }

    internal val lastClickedNativeCardBitmap: ImageBitmap?
        get() = nativeCardBitmap.takeIf { ownsNativeResources() }

    // 导航防抖发生在卡片记录之后。被拒绝的相邻点击也会替换全局资源，
    // 飞行详情必须用自身冻结的快照核对归属，不能直接借用“最后点击”的文字像素。
    internal fun nativeVideoCardLayerFor(snapshot: VideoCardSourceChromeSnapshot?): GraphicsLayer? =
        lastClickedNativeCardLayer.takeIf {
            ownsVideoCardSourceSnapshot(snapshot, lastClickedVideoSourceChromeSnapshot)
        }

    internal fun nativeVideoCoverOverlayLayerFor(snapshot: VideoCardSourceChromeSnapshot?): GraphicsLayer? =
        lastClickedNativeCoverOverlayLayer.takeIf {
            ownsVideoCardSourceSnapshot(snapshot, lastClickedVideoSourceChromeSnapshot)
        }

    internal fun nativeVideoCardBitmapFor(snapshot: VideoCardSourceChromeSnapshot?): ImageBitmap? =
        lastClickedNativeCardBitmap.takeIf {
            ownsVideoCardSourceSnapshot(snapshot, lastClickedVideoSourceChromeSnapshot)
        }

    /** Immutable pixel provenance survives metadata-only restoration of a parent source key. */
    internal var lastClickedWholeCardReturnSnapshot: WholeCardReturnSnapshot? by mutableStateOf(null)
        private set
    
    /**
     *  是否是单列卡片（故事卡片）
     * 用于决定导航动画方向：单列用垂直滑动，双列用水平滑动
     */
    var isSingleColumnCard: Boolean = false
        private set
    
    /**
     *  [新增] 是否正在切换分类
     * 用于跳过首页卡片的入场动画，避免切换标签时出现收缩效果
     */
    @Volatile
    var isSwitchingCategory: Boolean = false
    
    /**
     *  [新增] 屏幕密度，用于计算 dp 到 px
     */
    var lastScreenDensity: Float = 3f
        private set

    private var lastRecordedScreenWidth: Float = 0f
    private var lastRecordedScreenHeight: Float = 0f

    /**
     * 记录卡片位置
     * @param bounds 卡片在 Root 坐标系中的边界
     * @param screenWidth 屏幕宽度
     * @param screenHeight 屏幕高度
     * @param isSingleColumn 是否是单列卡片（故事卡片）
     * @param density 屏幕密度（可选）
     * @param bottomBarHeightDp 底部导航栏高度（dp），用于裁剪可见区域
     */
    fun recordCardPosition(
        bounds: Rect, 
        screenWidth: Float, 
        screenHeight: Float,
        isSingleColumn: Boolean = false,
        density: Float = 3f,
        bottomBarHeightDp: Float = 80f  //  底部导航栏默认高度
    ) {
        lastClickedVideoSourceKey = null
        lastClickedVideoSourceInstanceId = null
        lastClickedVideoPixelOwner = null
        activeVideoSourceOwner = null
        lastClickedVideoSourceCornerDp = null
        lastClickedCoverBounds = null
        lastClickedInfoBounds = null
        lastClickedVideoSourceLayout = VideoCardSourceLayout.COVER_ONLY
        lastClickedVideoSourceChromeSnapshot = null
        clearNativeVideoCardLayers()
        lastClickedCardBounds = bounds
        lastScreenDensity = density
        lastRecordedScreenWidth = screenWidth
        lastRecordedScreenHeight = screenHeight
        isSingleColumnCard = isSingleColumn
        //  [修复] 计算可见区域的底边界（屏幕高度减去底部导航栏）
        val bottomBarHeightPx = bottomBarHeightDp * density
        val visibleBottomPx = screenHeight - bottomBarHeightPx
        
        //  [修复] 计算卡片可见部分的中心点
        // 如果卡片底部被导航栏遮挡，使用可见部分的中心
        val visibleTop = bounds.top
        val visibleBottom = bounds.bottom.coerceAtMost(visibleBottomPx)
        val visibleCenterY = if (visibleBottom > visibleTop) {
            (visibleTop + visibleBottom) / 2
        } else {
            bounds.center.y  // 完全不可见时使用原始中心
        }
        
        // 计算归一化的中心点坐标 (0-1 范围)
        lastClickedCardCenter = Offset(
            x = bounds.center.x / screenWidth,
            y = visibleCenterY / screenHeight  //  使用可见部分的中心 Y
        )
    }

    internal fun recordVideoCardPosition(
        bvid: String,
        sourceRoute: String?,
        bounds: Rect,
        screenWidth: Float,
        screenHeight: Float,
        isSingleColumn: Boolean = false,
        density: Float = 3f,
        bottomBarHeightDp: Float = 80f,
        sourceCornerDp: Int? = null,
        coverBounds: Rect? = null,
        sourceLayout: VideoCardSourceLayout? = null,
        sourceChromeSnapshot: VideoCardSourceChromeSnapshot? = null,
        sourceInstanceId: Long? = null,
        infoBounds: Rect? = null,
    ) {
        recordCardPosition(
            bounds = bounds,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            isSingleColumn = isSingleColumn,
            density = density,
            bottomBarHeightDp = bottomBarHeightDp
        )
        com.android.purebilibili.core.ui.transition.VideoCardSourceInstances.recordClick(bvid, bounds)
        val normalizedBvid = bvid.trim()
        val normalizedRoute = normalizeVideoCardSourceRoute(sourceRoute)
        lastClickedVideoSourceKey = if (normalizedBvid.isNotEmpty() && normalizedRoute != null) {
            "$normalizedRoute:$normalizedBvid"
        } else {
            null
        }
        lastClickedVideoSourceInstanceId = sourceInstanceId
        lastClickedVideoPixelOwner = lastClickedVideoSourceKey?.let {
            VideoCardPixelOwner(it, sourceInstanceId)
        }
        activeVideoSourceOwner = lastClickedVideoPixelOwner
        lastClickedVideoSourceCornerDp = sourceCornerDp?.coerceAtLeast(0)
        lastClickedCoverBounds = coverBounds
            ?.takeIf { it.width > 1f && it.height > 1f }
            ?.let { Rect(it.left, it.top, it.right, it.bottom) }
        lastClickedVideoSourceLayout = sourceLayout ?: resolveVideoCardSourceLayout(
            cardBounds = lastClickedCardBounds,
            coverBounds = lastClickedCoverBounds,
        )
        lastClickedVideoSourceChromeSnapshot = sourceChromeSnapshot
        lastClickedInfoBounds = infoBounds
            ?.takeIf { it.width > 1f && it.height > 1f }
            ?.let { Rect(it.left, it.top, it.right, it.bottom) }
    }

    internal fun recordNativeCardLayer(layer: GraphicsLayer?) {
        nativeResourcesOwner = lastClickedVideoPixelOwner
        nativeCardLayer = layer
    }

    internal fun recordNativeCoverOverlayLayer(layer: GraphicsLayer?) {
        nativeResourcesOwner = lastClickedVideoPixelOwner
        nativeCoverOverlayLayer = layer
    }

    internal fun recordNativeCardBitmap(
        bitmap: ImageBitmap?,
        expectedOwner: VideoCardPixelOwner?,
    ) {
        if (isActiveVideoCardPixelOwner(expectedOwner)) {
            nativeResourcesOwner = expectedOwner
            nativeCardBitmap = bitmap
        }
    }

    /** Accept only the exact click that started capture, including repeated clicks on one card. */
    internal fun recordWholeCardReturnBitmap(bitmap: ImageBitmap, expectedOwner: VideoCardPixelOwner?) {
        if (expectedOwner != null && isActiveVideoCardPixelOwner(expectedOwner)) {
            lastClickedWholeCardReturnSnapshot = WholeCardReturnSnapshot(expectedOwner, bitmap)
        }
    }

    /** Release native display lists once their navigation transition has settled. */
    internal fun clearNativeVideoCardLayers() {
        nativeResourcesOwner = null
        nativeCardLayer = null
        nativeCoverOverlayLayer = null
        nativeCardBitmap = null
        lastClickedWholeCardReturnSnapshot = null
    }

    /** Drop click-time geometry when the window changed after the source was captured. */
    internal fun invalidateVideoSourceIfWindowChanged(
        screenWidth: Float,
        screenHeight: Float,
        tolerancePx: Float = 1f,
    ) {
        if (lastClickedCardBounds == null) return
        if (abs(lastRecordedScreenWidth - screenWidth) > tolerancePx ||
            abs(lastRecordedScreenHeight - screenHeight) > tolerancePx
        ) {
            clear()
        }
    }

    internal fun isNativeVideoCardLayerCurrentOwner(layer: GraphicsLayer): Boolean =
        nativeCardLayer === layer && ownsNativeResources() &&
            isActiveVideoCardPixelOwner(nativeResourcesOwner)

    internal fun isNativeCoverOverlayLayerCurrentOwner(layer: GraphicsLayer): Boolean =
        nativeCoverOverlayLayer === layer && ownsNativeResources() &&
            isActiveVideoCardPixelOwner(nativeResourcesOwner)
    
    /**
     * 清除记录的位置
     */
    fun clear() {
        lastClickedCardBounds = null
        lastClickedCoverBounds = null
        lastClickedInfoBounds = null
        lastClickedCardCenter = null
        lastClickedVideoSourceKey = null
        lastClickedVideoSourceInstanceId = null
        lastClickedVideoPixelOwner = null
        activeVideoSourceOwner = null
        lastClickedVideoSourceCornerDp = null
        lastClickedVideoSourceLayout = VideoCardSourceLayout.COVER_ONLY
        lastClickedVideoSourceChromeSnapshot = null
        lastRecordedScreenWidth = 0f
        lastRecordedScreenHeight = 0f
        clearNativeVideoCardLayers()
    }

    /** Restore source matching metadata, never the provenance of the last captured resource. */
    internal fun restoreVideoSource(sourceKey: String?, sourceOwner: VideoCardPixelOwner?) {
        lastClickedVideoSourceKey = sourceKey?.trim()?.takeIf { it.isNotEmpty() }
        activeVideoSourceOwner = sourceOwner?.takeIf { it.sourceKey == lastClickedVideoSourceKey }
        lastClickedVideoSourceInstanceId = activeVideoSourceOwner?.sourceInstanceId
    }
    
    /**
     *  卡片水平位置枚举
     */
    enum class CardHorizontalPosition {
        LEFT,   // 左边两个 (0% - 40%)
        MIDDLE, // 中间一个 (40% - 60%)
        RIGHT   // 右边两个 (60% - 100%)
    }

    /**
     *  获取卡片的水平位置区域（针对 5 列布局优化）
     */
    val cardHorizontalPosition: CardHorizontalPosition
        get() {
            val centerX = lastClickedCardCenter?.x ?: 0.5f
            return when {
                centerX < 0.4f -> CardHorizontalPosition.LEFT
                centerX > 0.6f -> CardHorizontalPosition.RIGHT
                else -> CardHorizontalPosition.MIDDLE
            }
        }
    
    /**
     *  判断最后点击的卡片是否在屏幕左侧
     * 用于小窗入场动画方向
     */
    val isCardOnLeft: Boolean
        get() = (lastClickedCardCenter?.x ?: 0.5f) < 0.5f
    
    /**
     *  [新增] 判断卡片是否完全可见（没有被顶部 header 遮挡）
     * Header 高度约为 156dp，如果卡片顶部在这个区域内，则认为被遮挡
     * 被遮挡的卡片应该禁用共享元素过渡
     */
    val isCardFullyVisible: Boolean
        get() {
            val bounds = lastClickedCardBounds ?: return true
            if (isSingleColumnCard) {
                val minVisibleHeightPx = 80 * lastScreenDensity
                return bounds.bottom > minVisibleHeightPx && (bounds.bottom - bounds.top) > minVisibleHeightPx
            }
            val headerHeightPx = 156 * lastScreenDensity  // 156dp header height
            return bounds.top >= headerHeightPx
        }
}

private fun normalizeVideoCardSourceRoute(sourceRoute: String?): String? {
    val normalized = sourceRoute?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return if (normalized.startsWith(HOME_CATEGORY_SOURCE_PREFIX)) {
        normalized
    } else {
        normalized.substringBefore("?")
    }
}
