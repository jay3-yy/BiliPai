package com.android.purebilibili.core.util

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/**
 *  卡片位置管理器
 * 
 * 用于记录点击卡片的位置，以便在返回动画时
 * 将缩放动画指向正确的卡片位置
 */
object CardPositionManager {
    
    /**
     * 最后点击的卡片边界（在 Root 坐标系中）
     */
    var lastClickedCardBounds: Rect? = null
        private set
    
    /**
     * 最后点击的卡片中心点（归一化坐标 0-1）
     */
    var lastClickedCardCenter: Offset? = null
        private set
    
    /**
     *  是否正在从视频详情页返回
     * 用于跳过首页卡片的入场动画
     */
    var isReturningFromDetail: Boolean = false
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
        lastClickedCardBounds = bounds
        lastScreenDensity = density
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
    
    /**
     *  标记正在返回
     */
    fun markReturning() {
        isReturningFromDetail = true
    }
    
    /**
     *  清除返回标记
     */
    fun clearReturning() {
        isReturningFromDetail = false
    }
    
    /**
     * 清除记录的位置
     */
    fun clear() {
        lastClickedCardBounds = null
        lastClickedCardCenter = null
        isReturningFromDetail = false
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
            val headerHeightPx = 156 * lastScreenDensity  // 156dp header height
            return bounds.top >= headerHeightPx
        }
}
