package com.android.purebilibili.feature.home.components

import androidx.compose.animation.core.SpringSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.store.home.GlassMotionStyle
import com.android.purebilibili.core.store.home.LiquidGlassSettingsStore
import com.android.purebilibili.core.ui.motion.GlassMorphSprings

/** 仅真实玻璃入口订阅；关闭玻璃及皮肤外壳不读取玻璃专属参数。 */
@Composable
internal fun rememberGlassDockMotionStyle(enabled: Boolean): GlassMotionStyle {
    if (!enabled) return GlassMotionStyle.STANDARD
    val context = LocalContext.current
    val flow = remember(context) { LiquidGlassSettingsStore.observeDockMotionStyle(context) }
    val style by flow.collectAsStateWithLifecycle(initialValue = GlassMotionStyle.STANDARD)
    return style
}

/** 展开更轻，回收更沉稳；同一几何事务共用一个弹簧，反向时由 Compose 保留速度。 */
internal fun <T> glassDockMorphSpring(style: GlassMotionStyle, expanding: Boolean): SpringSpec<T> =
    when (style) {
        GlassMotionStyle.STANDARD -> bottomBarSearchMorphSpring()
        GlassMotionStyle.ELASTIC ->
            (if (expanding) GlassMorphSprings.Expand else GlassMorphSprings.Return).asComposeSpring()
    }

/** 两行收为一行时先按外壳方向选节奏；同一行内再由搜索框展开方向决定。 */
internal fun linkedDockMotionExpanding(initial: LinkedDockPhase, target: LinkedDockPhase): Boolean =
    target == LinkedDockPhase.Expanded ||
        (initial != LinkedDockPhase.Expanded && (target == LinkedDockPhase.Search || target == LinkedDockPhase.Compact))
