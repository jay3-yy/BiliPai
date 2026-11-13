package com.android.purebilibili.core.store

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val launcherIconMutex = Mutex()
private const val THEME_INDEX_PREF = "theme_palette_index"

fun getBiliPaiThemeIconIndexSync(context: Context): Int =
    context.getSharedPreferences("app_icon_cache", Context.MODE_PRIVATE)
        .getInt(THEME_INDEX_PREF, DEFAULT_BILIPAI_THEME_ICON_INDEX)

/** 所有入口切换读取同一份已保存选择，并串行提交，避免主题更新与用户选图标交错。 */
suspend fun syncAppLauncherIcon(context: Context) = withContext(Dispatchers.IO) {
    launcherIconMutex.withLock { applySavedLauncherIcon(context) }
}

suspend fun syncAppThemeLauncherIcon(context: Context, index: Int) = withContext(Dispatchers.IO) {
    launcherIconMutex.withLock {
        val prefs = context.getSharedPreferences("app_icon_cache", Context.MODE_PRIVATE)
        if (prefs.getInt(THEME_INDEX_PREF, DEFAULT_BILIPAI_THEME_ICON_INDEX) != index) {
            check(prefs.edit().putInt(THEME_INDEX_PREF, index).commit())
        }
        if (SettingsManager.getAppIconSync(context) == BILIPAI_THEME_ICON_KEY) {
            applySavedLauncherIcon(context)
        }
    }
}

private fun applySavedLauncherIcon(context: Context) {
    val pm = context.packageManager
    val target = resolveAppIconLauncherAlias(
        context.packageName,
        SettingsManager.getAppIconSync(context),
        splashIconVisible = SettingsManager.isSplashIconAnimationEnabledSync(context),
        appearance = SettingsManager.getAppIconAppearanceSync(context),
        themeIconIndex = getBiliPaiThemeIconIndexSync(context),
    )
    val changes = mutableListOf<Pair<ComponentName, Int>>()
    val targetComponent = ComponentName(context.packageName, target)
    if (pm.getComponentEnabledSetting(targetComponent) != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
        changes += targetComponent to PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    }
    val managed = allManagedAppIconLauncherAliases(context.packageName)
    // 只查询当前可见入口，避免逐个读写整套预置色板的禁用 alias。
    @Suppress("DEPRECATION")
    val active = pm.queryIntentActivities(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName), 0,
    )
    for (entry in active) {
        val alias = entry.activityInfo.name
        if (alias != target && alias in managed) {
            changes += ComponentName(context.packageName, alias) to PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
    }
    if (changes.isEmpty()) return
    if (Build.VERSION.SDK_INT >= 33) {
        pm.setComponentEnabledSettings(changes.map { (component, state) ->
            PackageManager.ComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
        })
    } else {
        // 旧版本先启用目标入口，再关闭原入口。
        changes.forEach { (component, state) ->
            pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
        }
    }
}
