package com.android.purebilibili.feature.settings

import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.R
import com.android.purebilibili.feature.settings.ui.SettingsPageScaffold
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.AppSpacingTokens

/**
 *  开源许可证数据类
 */
data class OpenSourceLibrary(
    val name: String,
    val license: String,
    val url: String,
    val description: String = ""
)

/**
 *  当前整理的开源库与实现参考列表。
 */
val openSourceLibraries = listOf(
    OpenSourceLibrary(
        name = "FlClash Liquid Indicator",
        license = "GPL-3.0",
        url = "https://github.com/chen08209/FlClash/blob/8d4f9744948751f1273fe871e1d05276fac09b0e/lib/ui/navigation_dock.dart",
        description = "液态玻璃指示器的独立形变弹簧参考；保留现有拖动效果，仅在停止后追加甩尾回弹",
    ),
    OpenSourceLibrary(
        name = "Telegram / Nagram Chat Avatar Placement",
        license = "GPL-2.0-or-later（按 GPLv3 分发）",
        url = "https://github.com/NextAlone/Nagram/blob/fb56ea9801e5de68c60f0152b444bbe2a0fda520/TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java",
        description = "聊天头像底部吸附与消息边界约束代码移植，原作者 Nikolai Kudashov；适配评论、回复和搜索结果"
    ),
    OpenSourceLibrary(
        name = "Flex Seed Scheme",
        license = "BSD-3-Clause",
        url = "https://github.com/rydmike/flex_seed_scheme",
        description = "扩展调色板的色调表与配色规则（原生移植自 5.0.1）"
    ),
    OpenSourceLibrary(
        name = "Kotlin",
        license = "Apache 2.0",
        url = "https://github.com/JetBrains/kotlin",
        description = "Kotlin 语言、标准库与 Gradle 插件生态"
    ),
    OpenSourceLibrary(
        name = "Jetpack Compose",
        license = "Apache 2.0",
        url = "https://github.com/androidx/androidx",
        description = "现代化 Android UI 框架"
    ),
    OpenSourceLibrary(
        name = "AndroidX Activity / AppCompat / Core KTX",
        license = "Apache 2.0",
        url = "https://github.com/androidx/androidx",
        description = "Activity Compose、AppCompat 与 Android 核心扩展"
    ),
    OpenSourceLibrary(
        name = "Material Components / Material 3",
        license = "Apache 2.0",
        url = "https://github.com/material-components/material-components-android",
        description = "Material Design 组件与主题系统"
    ),
    OpenSourceLibrary(
        name = "Material Symbols",
        license = "Apache 2.0",
        url = "https://github.com/google/material-design-icons",
        description = "设置语义图标使用的 Outlined 矢量字形"
    ),
    OpenSourceLibrary(
        name = "Miuix",
        license = "Apache 2.0",
        url = "https://github.com/compose-miuix-ui/miuix",
        description = "Miuix Compose 组件、偏好设置组件、模糊能力及弹窗预测性返回交互参考"
    ),
    OpenSourceLibrary(
        name = "AndroidX Media3 / ExoPlayer",
        license = "Apache 2.0",
        url = "https://github.com/androidx/media",
        description = "音视频播放器、HLS/DASH、会话与数据源"
    ),
    OpenSourceLibrary(
        name = "AndroidX Paging",
        license = "Apache 2.0",
        url = "https://github.com/androidx/androidx",
        description = "列表分页、加载状态与失败重试"
    ),
    OpenSourceLibrary(
        name = "Protocol Buffers Lite",
        license = "BSD-3-Clause",
        url = "https://github.com/protocolbuffers/protobuf",
        description = "网络消息字段编解码"
    ),
    OpenSourceLibrary(
        name = "AndroidX Room",
        license = "Apache 2.0",
        url = "https://github.com/androidx/androidx",
        description = "SQLite 数据库 ORM"
    ),
    OpenSourceLibrary(
        name = "AndroidX DataStore",
        license = "Apache 2.0",
        url = "https://github.com/androidx/androidx",
        description = "偏好设置存储"
    ),
    OpenSourceLibrary(
        name = "AndroidX Navigation 3 / NavigationEvent",
        license = "Apache 2.0",
        url = "https://github.com/androidx/androidx",
        description = "Compose 导航运行时与返回事件处理"
    ),
    OpenSourceLibrary(
        name = "AndroidX Lifecycle / WorkManager / Window / Startup",
        license = "Apache 2.0",
        url = "https://github.com/androidx/androidx",
        description = "生命周期、后台任务、窗口适配与启动初始化"
    ),
    OpenSourceLibrary(
        name = "AndroidX Biometric / Palette / Metrics / ProfileInstaller",
        license = "Apache 2.0",
        url = "https://github.com/androidx/androidx",
        description = "生物识别、取色、性能指标与 Baseline Profile 安装"
    ),
    OpenSourceLibrary(
        name = "AndroidX Core SplashScreen",
        license = "Apache 2.0",
        url = "https://github.com/androidx/androidx",
        description = "启动页兼容实现"
    ),
    OpenSourceLibrary(
        name = "OkHttp",
        license = "Apache 2.0",
        url = "https://github.com/square/okhttp",
        description = "HTTP 客户端"
    ),
    OpenSourceLibrary(
        name = "Retrofit",
        license = "Apache 2.0",
        url = "https://github.com/square/retrofit",
        description = "类型安全 REST API 客户端"
    ),
    OpenSourceLibrary(
        name = "Retrofit Kotlinx Serialization Converter",
        license = "Apache 2.0",
        url = "https://github.com/JakeWharton/retrofit2-kotlinx-serialization-converter",
        description = "Retrofit 的 Kotlinx Serialization 转换器"
    ),
    OpenSourceLibrary(
        name = "Kotlinx Serialization",
        license = "Apache 2.0",
        url = "https://github.com/Kotlin/kotlinx.serialization",
        description = "Kotlin 多平台序列化"
    ),
    OpenSourceLibrary(
        name = "Kotlinx Coroutines",
        license = "Apache 2.0",
        url = "https://github.com/Kotlin/kotlinx.coroutines",
        description = "协程与测试调度支持"
    ),
    OpenSourceLibrary(
        name = "Brotli Java",
        license = "MIT",
        url = "https://github.com/google/brotli",
        description = "Brotli 解码支持"
    ),
    OpenSourceLibrary(
        name = "Coil",
        license = "Apache 2.0",
        url = "https://github.com/coil-kt/coil",
        description = "Kotlin 优先的图片加载库"
    ),
    OpenSourceLibrary(
        name = "Material Kolor",
        license = "MIT / Apache 2.0",
        url = "https://github.com/jordond/materialkolor",
        description = "Material You 动态配色生成"
    ),
    OpenSourceLibrary(
        name = "Skydoves ColorPicker Compose",
        license = "Apache 2.0",
        url = "https://github.com/skydoves/colorpicker-compose",
        description = "Compose 颜色选择器"
    ),
    OpenSourceLibrary(
        name = "RichEditor Compose",
        license = "Apache 2.0",
        url = "https://github.com/MohamedRejeb/compose-rich-editor",
        description = "富文本编辑能力"
    ),
    OpenSourceLibrary(
        name = "Lottie Compose",
        license = "Apache 2.0",
        url = "https://github.com/airbnb/lottie-android",
        description = "矢量动画库"
    ),
    OpenSourceLibrary(
        name = "Haze",
        license = "Apache 2.0",
        url = "https://github.com/chrisbanes/haze",
        description = "毛玻璃效果"
    ),
    // Compose Shimmer 已随依赖一并移除：骨架屏改用自研实现，APK 里不再包含该库。
    // 致谢列表应当反映实际分发的内容，列一个没有随包发出的库是不准确的。
    OpenSourceLibrary(
        name = "Cupertino",
        license = "Apache 2.0",
        url = "https://github.com/alexzhirkevich/compose-cupertino",
        description = "Compose Cupertino 组件与图标"
    ),
    OpenSourceLibrary(
        name = "BBPlayer",
        license = "MIT",
        url = "https://github.com/bbplayer-app/BBPlayer",
        description = "多来源歌词匹配与播放器交互实现参考"
    ),
    OpenSourceLibrary(
        name = "KernelSU",
        license = "GPL-3.0",
        url = "https://github.com/tiann/KernelSU",
        description = "液态玻璃交互与管理界面细节参考"
    ),
    OpenSourceLibrary(
        name = "DanmakuRenderEngine",
        license = "Apache 2.0",
        url = "https://github.com/bytedance/DanmakuRenderEngine",
        description = "高性能弹幕渲染引擎"
    ),
    OpenSourceLibrary(
        name = "ZXing",
        license = "Apache 2.0",
        url = "https://github.com/zxing/zxing",
        description = "二维码解析与生成"
    ),
    OpenSourceLibrary(
        name = "TinyPinyin",
        license = "Apache-2.0",
        url = "https://github.com/biezhi/TinyPinyin",
        description = "中文拼音转换（替代含损坏 jar 的 com.belerweb:pinyin4j）"
    ),
    OpenSourceLibrary(
        name = "Google Cast SDK",
        license = "Google APIs Terms",
        url = "https://github.com/googlecast/CastVideos-android",
        description = "Google Cast 投屏能力"
    ),
    OpenSourceLibrary(
        name = "AndroidX MediaRouter",
        license = "Apache 2.0",
        url = "https://github.com/androidx/androidx",
        description = "媒体路由与投屏设备发现"
    ),
    OpenSourceLibrary(
        name = "NanoHTTPD",
        license = "BSD-3-Clause",
        url = "https://github.com/NanoHttpd/nanohttpd",
        description = "轻量级嵌入式 HTTP 服务"
    ),
    OpenSourceLibrary(
        name = "Firebase Crashlytics / Analytics",
        license = "Firebase Terms",
        url = "https://github.com/firebase/firebase-android-sdk",
        description = "崩溃上报与统计服务 SDK"
    ),
    OpenSourceLibrary(
        name = "LeakCanary",
        license = "Apache 2.0",
        url = "https://github.com/square/leakcanary",
        description = "Debug 内存泄漏检测"
    ),
    OpenSourceLibrary(
        name = "JUnit 4 / JUnit 5",
        license = "EPL-1.0 / EPL-2.0",
        url = "https://github.com/junit-team/junit5",
        description = "单元测试框架与 Vintage 兼容运行"
    ),
    OpenSourceLibrary(
        name = "Kotlin Test",
        license = "Apache 2.0",
        url = "https://github.com/JetBrains/kotlin",
        description = "Kotlin 测试断言"
    ),
    OpenSourceLibrary(
        name = "MockK",
        license = "Apache 2.0",
        url = "https://github.com/mockk/mockk",
        description = "Kotlin Mock 测试库"
    ),
    OpenSourceLibrary(
        name = "Turbine",
        license = "Apache 2.0",
        url = "https://github.com/cashapp/turbine",
        description = "Flow 测试工具"
    ),
    OpenSourceLibrary(
        name = "AndroidX Test / Espresso",
        license = "Apache 2.0",
        url = "https://github.com/android/android-test",
        description = "Android UI 与集成测试"
    ),
    OpenSourceLibrary(
        name = "AndroidX Benchmark / Tracing / UIAutomator",
        license = "Apache 2.0",
        url = "https://github.com/androidx/androidx",
        description = "Baseline Profile、性能基准、Perfetto 跟踪与设备自动化测试"
    )
)

/**
 *  开源许可证页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenSourceLicensesScreen(
    onBack: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val screenTitle = stringResource(R.string.open_source_licenses_title)
    val backLabel = stringResource(R.string.common_back)
    val bottomContentPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    SettingsPageScaffold(
        title = screenTitle,
        onBack = onBack,
        backContentDescription = backLabel,
        bottomContentPadding = bottomContentPadding,
        lazyListContent = {
            item {
                Column(
                    modifier = Modifier.padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = 16.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AppText(
                        text = "本应用使用了以下开源组件，感谢所有开源贡献者！",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppContentColors.secondaryText(),
                    )
                    AppText(
                        text = "说明：该列表按当前工程依赖、投屏/测试模块以及明确参考实现整理，可能不包含全部传递依赖或完整法律清单。",
                        style = MaterialTheme.typography.labelMedium,
                        color = AppContentColors.link(),
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }

            items(openSourceLibraries, key = { it.name }) { library ->
                LicenseCard(
                    library = library,
                    onClick = { uriHandler.openUri(library.url) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        },
    )
}

/**
 *  单个许可证卡片
 */
@Composable
fun LicenseCard(
    library: OpenSourceLibrary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppSurface(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(AppShapes.container(ContainerLevel.Card))
            .clickable(onClick = onClick),
        color = AppSurfaceTokens.cardContainer(),
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                AppText(
                    text = library.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = AppContentColors.primaryText()
                )
                if (library.description.isNotEmpty()) {
                    AppText(
                        text = library.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppContentColors.secondaryText(),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppSurface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = AppShapes.container(ContainerLevel.Tag)
                    ) {
                        AppText(
                            text = library.license,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = AppSpacingTokens.Small, vertical = 2.dp)
                        )
                    }
                    AppText(
                        text = "GitHub/链接",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppContentColors.link(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                AppText(
                    text = library.url,
                    style = MaterialTheme.typography.labelSmall,
                    color = AppContentColors.link(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            AppIcon(
                com.android.purebilibili.feature.settings.rememberMaterialSymbol(com.android.purebilibili.R.drawable.ms_keyboard_arrow_right_24),
                contentDescription = null,
                tint = AppContentColors.secondaryText().copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
