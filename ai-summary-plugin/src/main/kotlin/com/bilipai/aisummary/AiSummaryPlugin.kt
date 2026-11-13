package com.bilipai.aisummary

import android.content.Context
import com.bilipai.aisummary.data.BilibiliSubtitleApi
import com.bilipai.aisummary.data.AudioTrackProvider
import com.bilipai.aisummary.data.ChatCompletionsClient
import com.bilipai.aisummary.data.GroqAsrClient
import com.bilipai.aisummary.data.OfficialConclusionApi
import com.bilipai.aisummary.data.SummaryCache
import com.bilipai.aisummary.pipeline.NoteDraftSink
import com.bilipai.aisummary.pipeline.SummaryPipeline
import com.bilipai.aisummary.settings.AndroidConfigStore
import com.bilipai.aisummary.settings.ConfigStore
import com.bilipai.aisummary.ui.AiSummaryController
import com.bilipai.aisummary.util.HttpDownload
import com.bilipai.aisummary.util.HttpGet
import com.bilipai.aisummary.util.HttpPost
import com.bilipai.aisummary.util.HttpUpload
import com.bilipai.aisummary.util.OkHttpClientProvider
import com.bilipai.aisummary.util.WbiSigner
import kotlinx.coroutines.CoroutineScope
import java.io.File

/**
 * 插件装配入口。
 *
 * BiliPai 的插件是「编译期集成」的 Kotlin 代码（外部 .bpplugin 执行仍是预览特性），
 * 所以本插件不假设任何特定 SDK 契约，而是暴露一个 [install] 工厂：
 * 宿主只需在合适的地方调用一次，拿到 [AiSummaryRuntime]，再把 controller 交给抽屉与设置页。
 *
 * 典型用法（视频详情页 / 设置页宿主代码）：
 * ```
 * val runtime = AiSummaryPlugin.install(context, scope, cookieProvider)
 * AiSummarySheet(visible, request, runtime.controller, ...)
 * AiSummarySettingsScreen(runtime.configStore, runtime.chatClient)
 * ```
 *
 * 依赖全部可通过参数替换，便于单元测试注入假实现。
 */
object AiSummaryPlugin {

    data class Dependencies(
        val httpGet: HttpGet,
        val httpPost: HttpPost,
        val cookieProvider: () -> String,
        val noteDraftSink: NoteDraftSink? = null,
        val cacheDir: File? = null,
        /** ASR 需要下载音频 + multipart 上传；宿主不提供则禁用 ASR。 */
        val httpDownload: HttpDownload? = null,
        val httpUpload: HttpUpload? = null,
        /** 宿主提供音轨（复用其取流能力）；为空则禁用 ASR。 */
        val audioTrackProvider: AudioTrackProvider? = null
    )

    fun defaultDependencies(
        context: Context,
        cookieProvider: () -> String,
        noteDraftSink: NoteDraftSink? = null,
        audioTrackProvider: AudioTrackProvider? = null
    ): Dependencies {
        val http = OkHttpClientProvider()
        return Dependencies(
            httpGet = http,
            httpPost = http,
            cookieProvider = cookieProvider,
            noteDraftSink = noteDraftSink,
            cacheDir = File(context.applicationContext.cacheDir, "ai_summary"),
            httpDownload = http,
            httpUpload = http,
            audioTrackProvider = audioTrackProvider
        )
    }

    fun install(
        context: Context,
        scope: CoroutineScope,
        cookieProvider: () -> String,
        noteDraftSink: NoteDraftSink? = null,
        audioTrackProvider: AudioTrackProvider? = null,
        overrides: Dependencies? = null
    ): AiSummaryRuntime = install(
        scope = scope,
        deps = overrides ?: defaultDependencies(context, cookieProvider, noteDraftSink, audioTrackProvider),
        configStore = AndroidConfigStore(context)
    )

    /** 供 JVM 测试使用：不碰 Android Context。 */
    fun install(
        scope: CoroutineScope,
        deps: Dependencies,
        configStore: ConfigStore
    ): AiSummaryRuntime {
        val signer = WbiSigner(deps.httpGet, deps.cookieProvider)
        val subtitleApi = BilibiliSubtitleApi(deps.httpGet, signer, deps.cookieProvider)
        val officialApi = OfficialConclusionApi(deps.httpGet, signer, deps.cookieProvider)
        val chat = ChatCompletionsClient(deps.httpPost, deps.httpGet)
        val cache = deps.cacheDir?.let { SummaryCache(it) }
        // ASR：下载 + 上传 + 音轨来源三者齐全才启用
        val asrClient = if (deps.httpDownload != null && deps.httpUpload != null && deps.cacheDir != null) {
            GroqAsrClient(
                httpDownload = deps.httpDownload,
                httpUpload = deps.httpUpload,
                tempDir = File(deps.cacheDir, "asr"),
                cookieProvider = deps.cookieProvider,
                httpGetFactory = { proxy ->
                    HttpGet { url, headers ->
                        when (val g = deps.httpGet) {
                            is OkHttpClientProvider -> g.get(url, headers, proxy)
                            else -> g.get(url, headers)
                        }
                    }
                }
            )
        } else null
        val pipeline = SummaryPipeline(
            configStore = configStore,
            subtitleApi = subtitleApi,
            chat = chat,
            officialApi = officialApi,
            cache = cache,
            noteDraftSink = deps.noteDraftSink,
            asrClient = asrClient,
            audioTrackProvider = deps.audioTrackProvider
        )
        return AiSummaryRuntime(
            configStore = configStore,
            chatClient = chat,
            cache = cache,
            controller = AiSummaryController(pipeline, configStore, scope),
            pipeline = pipeline,
            asrTester = asrClient?.let { client -> { cfg -> client.testConnection(cfg) } }
        )
    }
}

data class AiSummaryRuntime(
    val configStore: ConfigStore,
    val chatClient: ChatCompletionsClient,
    val cache: SummaryCache?,
    val controller: AiSummaryController,
    /** 路径 A 需要直接跑流水线（把结果转成官方 AiSummaryResponse 喂给原生抽屉）。 */
    val pipeline: SummaryPipeline,
    /** 设置页「测试 ASR 连接」；为 null 表示宿主未启用 ASR。 */
    val asrTester: ((com.bilipai.aisummary.model.AiSummaryConfig) -> String)? = null
)
