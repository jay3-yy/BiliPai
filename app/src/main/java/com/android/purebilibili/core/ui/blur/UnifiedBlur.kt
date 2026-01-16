// 文件路径: core/ui/blur/UnifiedBlur.kt
package com.android.purebilibili.core.ui.blur

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalContext
import com.android.purebilibili.core.store.SettingsManager
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect

/**
 *  统一的模糊Modifier
 * 
 * 自动根据用户设置选择模糊强度
 * 
 * @param hazeState Haze状态
 * @param enabled 是否启用模糊
 * @return 应用了用户偏好模糊的Modifier
 */
@Composable
fun Modifier.unifiedBlur(
    hazeState: HazeState,
    enabled: Boolean = true
): Modifier = composed {
    if (!enabled) return@composed this
    
    val context = LocalContext.current
    
    //  读取用户设置的模糊强度
    val blurIntensity by SettingsManager.getBlurIntensity(context)
        .collectAsState(initial = BlurIntensity.THIN)
    
    // 根据用户选择获取对应的模糊样式
    val blurStyle = BlurStyles.getBlurStyle(blurIntensity)
    
    //  [修复] 使用正确的 Haze API: hazeEffect 替代 hazeChild
    this.hazeEffect(
        state = hazeState,
        style = blurStyle
    )
}
