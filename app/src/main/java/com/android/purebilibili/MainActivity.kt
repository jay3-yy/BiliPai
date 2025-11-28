// 文件路径: app/src/main/java/com/android/purebilibili/MainActivity.kt
package com.android.purebilibili

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.theme.PureBiliBiliTheme
import com.android.purebilibili.navigation.AppNavigation

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // 启用全面屏

        setContent {
            val context = LocalContext.current
            // 读取设置中的深色模式状态
            val isDarkMode by SettingsManager.getDarkMode(context).collectAsState(initial = false)

            PureBiliBiliTheme(darkTheme = isDarkMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // 唯一的入口：导航控制器
                    AppNavigation()
                }
            }
        }
    }
}