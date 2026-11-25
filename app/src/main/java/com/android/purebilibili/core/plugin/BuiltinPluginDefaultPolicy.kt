package com.android.purebilibili.core.plugin

/**
 * 内置插件首次安装时的默认启用策略。
 *
 * 第三方/可选插件仍默认关闭；投屏为播放器核心能力，需开箱可用。
 */
internal fun resolvePluginDefaultEnabled(pluginId: String): Boolean {
    return pluginId in BUILTIN_DEFAULT_ENABLED_PLUGIN_IDS
}

private val BUILTIN_DEFAULT_ENABLED_PLUGIN_IDS = setOf(
    BuiltinPluginIds.DLNA_CAST,
    BuiltinPluginIds.GOOGLE_CAST,
)
