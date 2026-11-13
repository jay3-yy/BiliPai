package com.android.purebilibili.feature.plugin

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.android.purebilibili.core.plugin.BuiltinPlugin
import com.android.purebilibili.core.plugin.BuiltinPluginIds
import com.android.purebilibili.data.model.response.AiModelResult
import com.android.purebilibili.data.model.response.AiOutline
import com.android.purebilibili.data.model.response.AiPartOutline
import com.android.purebilibili.data.model.response.AiSummaryData
import com.android.purebilibili.data.model.response.AiSummaryResponse
import com.android.purebilibili.feature.download.buildBilibiliDownloadCookieHeader
import com.android.purebilibili.plugin.sdk.PluginCapability
import com.android.purebilibili.plugin.sdk.PluginCapabilityManifest
import com.bilipai.aisummary.AiSummaryPlugin
import com.bilipai.aisummary.AiSummaryRuntime
import com.bilipai.aisummary.data.AudioTrack
import com.bilipai.aisummary.model.SummaryResult
import com.bilipai.aisummary.pipeline.SummaryRequest
import com.bilipai.aisummary.ui.AiSummarySettingsScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 「AI 总结（自定义模型）」编译期内置插件。
 *
 * - 数据面：把 BiliPai 原生的 VideoRepository.getAiSummary() 换成本插件的
 *   map-reduce 结果（返回官方同构的 AiSummaryResponse，下游诊断/重试策略零改动）。
 * - UI 面：复用现有 VideoSupplementStatsActions 图标与 AiSummarySheet 底部抽屉。
 * - 配置面：SettingsContent() 出现在「插件中心 → 本插件详情页」，Key 用 Keystore 加密保存。
 *
 * 判定规则：**填了模型 Key 就走自定义模型**（没填 / 调用失败都回落官方总结），
 * 这样用户不必额外理解插件中心的启用开关。
 */
object AiSummaryBuiltinPlugin : BuiltinPlugin {

    override val id: String = BuiltinPluginIds.AI_SUMMARY_BYOK
    override val name: String = "AI 总结（自定义模型）"
    override val description: String =
        "用自己的模型 API 总结视频；在此页配置 Key 后，视频页的 AI 总结按钮即改走自定义模型"
    override val version: String = "0.1.10"
    override val author: String = "BiliPai AI Summary Plugin"

    override val capabilityManifest: PluginCapabilityManifest = PluginCapabilityManifest(
        pluginId = id,
        displayName = name,
        version = version,
        apiVersion = 1,
        entryClassName = this::class.java.name,
        capabilities = setOf(PluginCapability.NETWORK, PluginCapability.PLUGIN_STORAGE)
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var runtimeInternal: AiSummaryRuntime? = null

    @Volatile
    private var appContext: Context? = null

    /** Application 启动时注入 Context，保证 ViewModel 取数据时 runtime 已就绪。 */
    fun initialize(context: Context) {
        if (runtimeInternal != null) return
        appContext = context.applicationContext
        synchronized(this) {
            if (runtimeInternal == null) {
                android.util.Log.i("AiSummaryPlugin", "initialize: 开始装配插件")
                runtimeInternal = AiSummaryPlugin.install(
                    context = context.applicationContext,
                    scope = scope,
                    // BiliPai 现成的登录态 Cookie（SESSDATA / bili_jct / buvid3）
                    cookieProvider = { buildBilibiliDownloadCookieHeader() },
                    // 复用宿主已有的取流能力（WBI 签名 / 登录态 / 风控都由它处理），
                    // 供无字幕视频的 ASR 兜底下载音频。
                    audioTrackProvider = { bvid, cid -> biliAudioTrack(bvid, cid) }
                )
                android.util.Log.i("AiSummaryPlugin", "initialize: 装配完成")
            }
        }
    }

    private fun runtime(context: Context): AiSummaryRuntime {
        initialize(context)
        return requireNotNull(runtimeInternal)
    }

    /** 是否配置了模型 Key —— 决定视频页走自定义模型还是官方总结。 */
    fun isConfigured(): Boolean {
        val runtimeReady = runtimeInternal != null
        val configured = runCatching {
            runtimeInternal?.configStore?.load()?.isConfigured == true
        }.getOrElse {
            android.util.Log.w("AiSummaryPlugin", "isConfigured 读取配置失败", it)
            false
        }
        android.util.Log.i("AiSummaryPlugin", "isConfigured=$configured (runtimeReady=$runtimeReady)")
        return configured
    }

    @Composable
    override fun SettingsContent() {
        val rt = runtime(LocalContext.current)
        AiSummarySettingsScreen(
            configStore = rt.configStore,
            chatClient = rt.chatClient,
            // 插件详情页用 LazyColumn 承载 SettingsContent（高度约束为 Infinity）：
            // 直接 verticalScroll 会崩；改为限高后再滚动，内容才完整可达。
            scrollable = false,
            asrTester = { cfg -> runtimeInternal?.asrTester?.invoke(cfg) ?: "ASR 未就绪" }
        )
    }

    suspend fun fetchSummary(
        bvid: String,
        cid: Long,
        upMid: Long,
        title: String,
        /** 真实进度：字幕 / ASR 下载与转写 / 模型请求 / 官方兜底，用于替换固定的 loading 文案。 */
        onProgress: (String) -> Unit = {}
    ): Result<AiSummaryResponse> = runCatching {
        android.util.Log.i("AiSummaryPlugin", "fetchSummary 开始: bvid=$bvid cid=$cid")
        val rt = requireNotNull(runtimeInternal) { "AI 总结插件尚未初始化" }
        onProgress("正在准备总结（读取配置与缓存）…")
        val result = rt.pipeline.run(
            SummaryRequest(
                bvid = bvid,
                cid = cid,
                title = title.ifBlank { bvid },
                upMid = upMid
            ),
            // 回调可能来自 IO 线程；宿主用 StateFlow.update 更新 UI，天然线程安全。
            onProgress = { stage, _, _ -> runCatching { onProgress(stage) } }
        )
        result.toOfficialResponse()
    }.onFailure { e ->
        // 走系统 Log 而不是 BiliPai 的 Logger：便于用户报问题时直接 logcat 抓取
        android.util.Log.w("AiSummaryPlugin", "fetchSummary failed: bvid=$bvid cid=$cid", e)
        // 同时落盘：logcat 缓冲容易被冲掉，用户反馈时可以直接取这个文件
        runCatching {
            val ctx = appContext ?: return@runCatching
            val f = java.io.File(ctx.cacheDir, "ai_summary_last_error.txt")
            f.writeText(
                buildString {
                    append("time=").append(java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                        .format(java.util.Date())).append('\n')
                    append("bvid=").append(bvid).append(" cid=").append(cid).append('\n')
                    append("error=").append(e.javaClass.name).append(": ").append(e.message).append('\n')
                    e.cause?.let { append("cause=").append(it.javaClass.name).append(": ").append(it.message).append('\n') }
                }
            )
        }
    }
}

/** 我们的结构化结果 → BiliPai 官方 AiSummaryResponse（时间戳单位为秒）。 */
private fun SummaryResult.toOfficialResponse(): AiSummaryResponse = AiSummaryResponse(
    code = 0,
    message = "",
    data = AiSummaryData(
        code = 0,
        modelResult = AiModelResult(
            resultType = 2, // 2 = 摘要 + 提纲
            // 原生卡片只渲染 summary/outline，没有「来源」字段，因此把来源注入 summary：
            //  - 开头放极短标记，保证折叠态（只显示首行）也能看出是谁生成的；
            //  - 末尾放完整说明（含缓存原始来源与生成时间），展开后可见。
            summary = buildString {
                append(sourceShortTag)
                append(overview)
                append("\n\n〔来源：").append(sourceLabel).append("〕")
            },
            outline = chapters.map { chapter ->
                AiOutline(
                    title = chapter.title,
                    timestamp = chapter.startSec.toLong(),
                    partOutline = chapter.points.map { point ->
                        // 每条要点用自己的时间；缺时间时才退回章节起点，
                        // 否则同一章节里所有小节会显示同一个时间。
                        AiPartOutline(
                            timestamp = (if (point.hasTime) point.startSec else chapter.startSec).toLong(),
                            content = point.text
                        )
                    }
                )
            }
        )
    )
)

/**
 * 从 BiliPai 现有取流接口挑一条**最低码率**的音轨。
 *
 * 为什么挑最低码率：ASR 只要人声可懂即可，而 Groq 单文件上限 25MB，
 * 码率越低越不容易超限（20 分钟视频 64kbps ≈ 9.6MB，192kbps 就要 28MB+）。
 */
private suspend fun biliAudioTrack(bvid: String, cid: Long): AudioTrack? {
    val playData = runCatching {
        com.android.purebilibili.data.repository.VideoRepository
            .getPlayUrlData(bvid = bvid, cid = cid, qn = 32)
    }.getOrNull() ?: return null
    val audios = playData.dash?.audio.orEmpty()
    if (audios.isEmpty()) return null
    val best = audios.minByOrNull { it.bandwidth } ?: return null
    if (best.baseUrl.isBlank()) return null
    return AudioTrack(
        url = best.baseUrl,
        backupUrls = best.backupUrl.orEmpty(),
        bandwidth = best.bandwidth,
        mimeType = best.mimeType,
        codecs = best.codecs,
        durationSec = playData.timelength / 1000.0
    )
}
