package com.android.purebilibili

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

internal const val EXTRA_LAUNCHER_COMPONENT = "com.android.purebilibili.extra.LAUNCHER_COMPONENT"

internal fun resolveSplashThemeResIdForActivityClassName(className: String?): Int {
    val themePrefix = "com.android.purebilibili.MainActivitySplashBiliPaiTheme"
    if (className?.startsWith(themePrefix) == true) {
        return biliPaiThemeSplashThemes[className.removePrefix(themePrefix).toInt()]
    }
    return when (className) {
        "com.android.purebilibili.MainActivitySplashBlueSnowMaid" -> R.style.Theme_PureBiliBili_Splash_BlueSnowMaid
        "com.android.purebilibili.MainActivitySplashBlueSnowMaidAnnouncement" -> R.style.Theme_PureBiliBili_Splash_BlueSnowMaidAnnouncement
        "com.android.purebilibili.MainActivitySplashBlueSnowMaidAnnouncementLight" -> R.style.Theme_PureBiliBili_Splash_BlueSnowMaidAnnouncementLight
        "com.android.purebilibili.MainActivitySplashBlueSnowMaidAnnouncementDark" -> R.style.Theme_PureBiliBili_Splash_BlueSnowMaidAnnouncementDark
        "com.android.purebilibili.MainActivitySplashBlueSnowMaidFront" -> R.style.Theme_PureBiliBili_Splash_BlueSnowMaidFront
        "com.android.purebilibili.MainActivitySplashBlueSnowMaidLight" -> R.style.Theme_PureBiliBili_Splash_BlueSnowMaidLight
        "com.android.purebilibili.MainActivitySplashBlueSnowMaidDark" -> R.style.Theme_PureBiliBili_Splash_BlueSnowMaidDark
        "com.android.purebilibili.MainActivitySplashBlueSnowMaidFrontLight" -> R.style.Theme_PureBiliBili_Splash_BlueSnowMaidFrontLight
        "com.android.purebilibili.MainActivitySplashBlueSnowMaidFrontDark" -> R.style.Theme_PureBiliBili_Splash_BlueSnowMaidFrontDark
        "com.android.purebilibili.MainActivitySplashIcon3D" -> R.style.Theme_PureBiliBili_Splash_Icon3D
        "com.android.purebilibili.MainActivitySplashIcon3DLight" -> R.style.Theme_PureBiliBili_Splash_Icon3DLight
        "com.android.purebilibili.MainActivitySplashIcon3DDark" -> R.style.Theme_PureBiliBili_Splash_Icon3DDark
        "com.android.purebilibili.MainActivitySplashBiliPai" -> R.style.Theme_PureBiliBili_Splash_BiliPai
        "com.android.purebilibili.MainActivitySplashBiliPaiPink" -> R.style.Theme_PureBiliBili_Splash_BiliPaiPink
        "com.android.purebilibili.MainActivitySplashBiliPaiWhite" -> R.style.Theme_PureBiliBili_Splash_BiliPaiWhite
        "com.android.purebilibili.MainActivitySplashBiliPaiViolet" -> R.style.Theme_PureBiliBili_Splash_BiliPaiViolet
        "com.android.purebilibili.MainActivitySplashBiliPaiGreen" -> R.style.Theme_PureBiliBili_Splash_BiliPaiGreen
        "com.android.purebilibili.MainActivitySplashBiliPaiInk" -> R.style.Theme_PureBiliBili_Splash_BiliPaiInk
        "com.android.purebilibili.MainActivitySplashBiliPaiMonet" -> R.style.Theme_PureBiliBili_Splash_BiliPaiMonet
        "com.android.purebilibili.MainActivitySplashNoIcon" -> R.style.Theme_PureBiliBili_Splash_NoIcon
        else -> 0
    }
}

@Suppress("DEPRECATION")
internal fun resolveForwardedLauncherActivityInfo(context: Context, launchIntent: Intent?): ActivityInfo? {
    val component = launchIntent?.getStringExtra(EXTRA_LAUNCHER_COMPONENT)
        ?.let(ComponentName::unflattenFromString)
        ?: return null
    if (component.packageName != context.packageName) return null
    // An icon can be changed while the host is alive, disabling its original alias.
    val info = runCatching {
        context.packageManager.getActivityInfo(component, PackageManager.MATCH_DISABLED_COMPONENTS)
    }.getOrNull() ?: return null
    return info.takeIf {
        resolveSplashThemeResIdForActivityClassName(it.targetActivity ?: it.name) != 0
    }
}

/**
 * Retains the launcher's themed system splash, but never draws a second activity UI.
 * MainActivity alone owns Compose, navigation, playback and home-data preloading.
 */
abstract class MainActivityLauncherEntry : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition { true }
        startActivity(Intent(intent).apply {
            putExtra(
                EXTRA_LAUNCHER_COMPONENT,
                (intent.component ?: componentName).flattenToString()
            )
            setClass(this@MainActivityLauncherEntry, MainActivity::class.java)
        })
        finish()
    }
}

class MainActivitySplashBlueSnowMaid : MainActivityLauncherEntry()
class MainActivitySplashBlueSnowMaidAnnouncement : MainActivityLauncherEntry()
class MainActivitySplashBlueSnowMaidAnnouncementLight : MainActivityLauncherEntry()
class MainActivitySplashBlueSnowMaidAnnouncementDark : MainActivityLauncherEntry()
class MainActivitySplashBlueSnowMaidFront : MainActivityLauncherEntry()
class MainActivitySplashBlueSnowMaidLight : MainActivityLauncherEntry()
class MainActivitySplashBlueSnowMaidDark : MainActivityLauncherEntry()
class MainActivitySplashBlueSnowMaidFrontLight : MainActivityLauncherEntry()
class MainActivitySplashBlueSnowMaidFrontDark : MainActivityLauncherEntry()
class MainActivitySplashIcon3D : MainActivityLauncherEntry()
class MainActivitySplashIcon3DLight : MainActivityLauncherEntry()
class MainActivitySplashIcon3DDark : MainActivityLauncherEntry()
class MainActivitySplashBiliPai : MainActivityLauncherEntry()
class MainActivitySplashBiliPaiPink : MainActivityLauncherEntry()
class MainActivitySplashBiliPaiWhite : MainActivityLauncherEntry()
class MainActivitySplashBiliPaiMonet : MainActivityLauncherEntry()
class MainActivitySplashNoIcon : MainActivityLauncherEntry()

class MainActivitySplashBiliPaiViolet : MainActivityLauncherEntry()

class MainActivitySplashBiliPaiGreen : MainActivityLauncherEntry()

class MainActivitySplashBiliPaiInk : MainActivityLauncherEntry()
