package com.android.purebilibili.feature.plugin

import com.android.purebilibili.core.plugin.BuiltinPluginIds

import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.AppHorizontalDivider

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import com.android.purebilibili.core.ui.components.AppButton
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.components.AppOutlinedTextField
import com.android.purebilibili.core.ui.components.AppSwitch
import com.android.purebilibili.core.ui.components.AppText
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.android.purebilibili.R
import com.android.purebilibili.core.coroutines.AppScope
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.plugin.BuiltinPlugin
import com.android.purebilibili.plugin.sdk.PlaybackCdnPlugin
import com.android.purebilibili.plugin.sdk.PlaybackCdnRewriteResult
import com.android.purebilibili.plugin.sdk.PlaybackCdnCandidate
import com.android.purebilibili.plugin.sdk.PlaybackCdnCandidateSource
import com.android.purebilibili.plugin.sdk.CdnHealthEvent
import com.android.purebilibili.plugin.sdk.CdnLineDiagnostic
import com.android.purebilibili.plugin.sdk.PluginCapability
import com.android.purebilibili.plugin.sdk.PluginCapabilityManifest
import com.android.purebilibili.core.plugin.PluginManager
import com.android.purebilibili.core.plugin.PluginStore
import com.android.purebilibili.core.util.Logger
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Socket

private const val TAG = "CdnRegionPlugin"
private const val CDN_PROBE_SAMPLE_BYTES = 32 * 1024
private const val CDN_ACTIVE_PLAYBACK_SESSION_TTL_MS = 10 * 60_000L


private data class CdnProbeMeasure(
    val success: Boolean,
    val latencyMs: Long?,
    val speedKbps: Long?
)

private data class CdnSettingsProbeTarget(
    val region: String,
    val hosts: List<String>
)

/**
 * Kept in memory only. Signed playurl addresses must never be written to plugin storage because
 * they expire and would otherwise look like a reusable CDN rule on the next playback session.
 */
private data class CdnActivePlaybackSession(
    val candidates: List<PlaybackCdnCandidate>,
    val updatedAtMs: Long
)


class CdnRegionPlugin : BuiltinPlugin, PlaybackCdnPlugin, CdnTransferControls {
    override val id: String = BuiltinPluginIds.PLAYBACK_CDN
    override val name: String = "CDN 智能选线"
    override val description: String = "在 B 站当前授权的签名 CDN 候选中选线，支持多节点并发分块加速与可选分片预缓存"
    override val version: String = "1.5.0"
    override val author: String = "BiliPai项目组"
    override val icon: ImageVector = Icons.Outlined.Dns
    override val capabilityManifest: PluginCapabilityManifest = PluginCapabilityManifest(
        pluginId = id,
        displayName = name,
        version = version,
        apiVersion = 1,
        entryClassName = "com.android.purebilibili.feature.plugin.CdnRegionPlugin",
        capabilities = setOf(
            PluginCapability.PLAYBACK_CDN,
            PluginCapability.NETWORK,
            PluginCapability.PLUGIN_STORAGE
        )
    )

    @Volatile
    private var running = false

    @Volatile
    private var cache: CdnRegionPluginCache = CdnRegionPluginCache()

    @Volatile
    private var catalog: Map<String, List<String>> = emptyMap()

    @Volatile
    private var compiledCustomRules: List<Pair<CdnCustomRule, Regex>> = emptyList()

    @Volatile
    private var activePlaybackSession: CdnActivePlaybackSession? = null

    override suspend fun onEnable() {
        running = true
        val context = PluginManager.getContext()
        catalog = loadCdnRegionCatalog(context)
        cache = CdnRegionPluginStore.read(context)
        CdnTransferRuntime.configure(buildParallelConfig(running, cache))
        compiledCustomRules = compileCdnCustomRules(cache.customRules)
        AppScope.ioScope.launch {
            delay(1_500L)
            refreshIpLocationIfNeeded()
        }
        Logger.d(TAG, "CDN 属地优选已启用，缓存地区=${cache.selectedRegion.ifBlank { "未命中" }}")
    }

    override suspend fun onDisable() {
        running = false
        CdnTransferRuntime.configure(false, false)
        activePlaybackSession = null
        Logger.d(TAG, "CDN 属地优选已禁用")
    }

    override fun rewritePlaybackCandidates(
        videoUrls: List<String>,
        audioUrls: List<String>
    ): PlaybackCdnRewriteResult {
        val snapshot = cache
        CdnTransferRuntime.configure(buildParallelConfig(running, snapshot))
        CdnTransferRuntime.register(videoUrls, audioUrls)
        val originalCandidates = buildPlaybackCdnCandidates(videoUrls, audioUrls)
        publishActivePlaybackCandidates(originalCandidates)
        if (!snapshot.experimentalRewriteEnabled) {
            val initialCandidates = sortSafeSignedPlaybackCandidates(
                candidates = originalCandidates,
                healthByHost = snapshot.healthByHost
            )
            val rankedUrls = CdnTransferRuntime.rank(initialCandidates.map { it.videoUrl })
            val safeCandidates = rankedUrls.mapNotNull { url -> initialCandidates.firstOrNull { it.videoUrl == url } }
            return PlaybackCdnRewriteResult(
                candidates = safeCandidates,
                regionLabel = null,
                cacheKeysByUrl = buildPlaybackCdnCacheKeys(safeCandidates)
            )
        }
        val customCandidates = rewritePlaybackCdnCandidatesForCompiledCustomRules(
            candidates = originalCandidates,
            compiledRules = compiledCustomRules
        )
        val hosts = if (snapshot.fallbackUsed) emptyList() else resolveCdnRegionHosts(
            region = snapshot.selectedRegion,
            cachedHosts = snapshot.selectedHosts,
            catalog = catalog,
            isp = snapshot.location.isp
        )
        val regionCandidates = rewritePlaybackCdnCandidatesForRegion(originalCandidates, hosts)
        val candidates = selectPlaybackCdnCandidatesForMode(
                customCandidates = customCandidates,
                regionCandidates = regionCandidates,
                originalCandidates = originalCandidates,
                strictCustomCdn = snapshot.strictCustomCdn,
                healthByHost = snapshot.healthByHost
            )
        return PlaybackCdnRewriteResult(
            candidates = candidates,
            regionLabel = snapshot.selectedRegion.takeIf { hosts.isNotEmpty() },
            cacheKeysByUrl = buildPlaybackCdnCacheKeys(candidates)
        )
    }

    override fun buildPlaybackCdnDiagnostics(
        videoUrls: List<String>,
        sources: List<PlaybackCdnCandidateSource>
    ): List<CdnLineDiagnostic> {
        return buildCdnLineDiagnostics(videoUrls, cache.healthByHost, sources)
    }

    override suspend fun probePlaybackCdnCandidates(
        videoUrls: List<String>,
        sources: List<PlaybackCdnCandidateSource>
    ): List<CdnLineDiagnostic> {
        return probePlaybackCdnCandidatesInternal(
            videoUrls = videoUrls,
            sources = sources,
            ignoreCooldown = false
        )
    }

    private suspend fun probePlaybackCdnCandidatesInternal(
        videoUrls: List<String>,
        sources: List<PlaybackCdnCandidateSource>,
        ignoreCooldown: Boolean
    ): List<CdnLineDiagnostic> {
        val context = PluginManager.getContext()
        val now = System.currentTimeMillis()
        val current = CdnRegionPluginStore.read(context).also { cache = it }
        val candidates = resolveCdnProbeCandidates(
            urls = videoUrls,
            healthByHost = current.healthByHost,
            nowMs = now,
            limit = CdnProbeLimit(
                maxCandidates = 3,
                cooldownMs = if (ignoreCooldown) 0L else CDN_MANUAL_PROBE_COOLDOWN_MS
            )
        )
        var nextHealth = current.healthByHost
        candidates.filter { it.allowed }.forEach { candidate ->
            val result = probePlaybackUrl(candidate.url)
            val previous = nextHealth[candidate.host] ?: CdnCandidateHealth(host = candidate.host)
            nextHealth = nextHealth + (
                candidate.host to recordCdnProbeResult(
                    current = previous,
                    latencyMs = result.latencyMs,
                    speedKbps = result.speedKbps,
                    success = result.success,
                    nowMs = System.currentTimeMillis()
                )
            )
        }
        val next = current.copy(healthByHost = nextHealth)
        cache = next
        CdnRegionPluginStore.write(context, next)
        return buildCdnLineDiagnostics(videoUrls, next.healthByHost, sources)
    }

    override fun recordPlaybackCdnEvent(url: String, event: CdnHealthEvent) {
        val host = hostFromCdnUrl(url)
        if (host.isBlank()) return
        val now = System.currentTimeMillis()
        val current = cache
        val previous = decayCdnHealth(current.healthByHost[host] ?: CdnCandidateHealth(host = host), now)
        val next = current.copy(
            healthByHost = current.healthByHost + (host to recordCdnHealthEvent(previous, event, now))
        )
        cache = next
        AppScope.ioScope.launch {
            CdnRegionPluginStore.write(PluginManager.getContext(), next)
        }
    }

    override fun canUseParallelDownload(): Boolean = running && !cache.experimentalRewriteEnabled

    override suspend fun setParallelDownloadEnabled(enabled: Boolean) {
        updateParallelCache { it.copy(parallelDownloadEnabled = enabled) }
    }

    override fun currentParallelConfig(): CdnParallelConfig = buildParallelConfig(running, cache)

    override suspend fun setParallelMirrorMode(mode: CdnParallelMirrorMode) {
        updateParallelCache { it.copy(parallelMirrorMode = mode.storageValue) }
    }

    override suspend fun setParallelConcurrencyPreset(preset: CdnConcurrencyPreset) {
        updateParallelCache { it.copy(parallelConcurrency = preset.storageValue) }
    }

    override suspend fun setParallelAllowCellular(allow: Boolean) {
        updateParallelCache { it.copy(parallelAllowCellular = allow) }
    }

    private suspend fun updateParallelCache(transform: (CdnRegionPluginCache) -> CdnRegionPluginCache) {
        val next = transform(cache)
        CdnRegionPluginStore.write(PluginManager.getContext(), next)
        cache = next
        CdnTransferRuntime.configure(buildParallelConfig(running, next))
    }

    private fun buildParallelConfig(active: Boolean, snapshot: CdnRegionPluginCache): CdnParallelConfig {
        return CdnParallelConfig(
            active = active,
            parallel = snapshot.parallelDownloadEnabled && !snapshot.experimentalRewriteEnabled,
            mirrorMode = CdnParallelMirrorMode.fromStorageValue(snapshot.parallelMirrorMode),
            concurrencyPreset = CdnConcurrencyPreset.fromStorageValue(snapshot.parallelConcurrency),
            allowCellular = snapshot.parallelAllowCellular,
            customHosts = snapshot.parallelCustomHosts,
        )
    }

    override fun isAdaptivePrefetchEnabled(): Boolean {
        return cache.prefetchEnabled && !cache.experimentalRewriteEnabled
    }

    @Composable
    override fun SettingsContent() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var snapshot by remember { mutableStateOf(cache) }
        var catalogSnapshot by remember { mutableStateOf(catalog) }
        var probing by remember { mutableStateOf(false) }
        var customRules by remember(snapshot.customRules) { mutableStateOf(snapshot.customRules) }
        var strictCustomCdn by remember(snapshot.strictCustomCdn) { mutableStateOf(snapshot.strictCustomCdn) }
        val transfers by CdnTransferRuntime.state.collectAsStateWithLifecycle()
        var checkingPlayback by remember { mutableStateOf(false) }
        var prefetchEnabled by remember(snapshot.prefetchEnabled) { mutableStateOf(snapshot.prefetchEnabled) }
        var experimentalRewriteEnabled by remember(snapshot.experimentalRewriteEnabled) {
            mutableStateOf(snapshot.experimentalRewriteEnabled)
        }
        var customRuleError by remember { mutableStateOf<String?>(null) }
        var customParallelHostsInput by remember(snapshot.parallelCustomHosts) {
            mutableStateOf(snapshot.parallelCustomHosts.joinToString("\n"))
        }
        var customParallelHostsError by remember { mutableStateOf<String?>(null) }
        var activeCandidates by remember { mutableStateOf<List<PlaybackCdnCandidate>>(emptyList()) }
        var liveDiagnostics by remember { mutableStateOf<List<CdnLineDiagnostic>>(emptyList()) }

        LaunchedEffect(Unit) {
            catalogSnapshot = catalog.ifEmpty {
                loadCdnRegionCatalog(context).also { catalog = it }
            }
            snapshot = CdnRegionPluginStore.read(context).also { cache = it }
        }
        LaunchedEffect(Unit) {
            while (currentCoroutineContext().isActive) {
                val mediaUrl = com.android.purebilibili.feature.video.player.MiniPlayerManager
                    .getInstanceOrNull()?.player?.currentMediaItem?.localConfiguration?.uri?.toString()
                mediaUrl?.let(CdnTransferRuntime::observeMediaUrl)
                val session = activePlaybackSessionOrNull()
                val observed = CdnTransferRuntime.recentMediaUrls().map { url ->
                    PlaybackCdnCandidate(url, null, PlaybackCdnCandidateSource.ORIGINAL)
                }
                activeCandidates = observed.takeIf { it.isNotEmpty() } ?: session?.candidates.orEmpty()
                liveDiagnostics = buildPlaybackCdnDiagnostics(
                    activeCandidates.map { it.videoUrl }, activeCandidates.map { it.source }
                )
                delay(1_000L)
            }
        }
        val probeTarget = resolveSettingsProbeTarget(snapshot, catalogSnapshot)
        val regionText = snapshot.selectedRegion.ifBlank {
            probeTarget.region.ifBlank { "未命中" }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            AppText(
                text = "安全签名选线",
                style = MaterialTheme.typography.titleSmall,
                color = AppContentColors.primaryText()
            )
            AppText(
                text = "打开视频后会自动比较 B 站当前返回的签名主/备线路；不会改写 Host，也不会保存过期链接。",
                style = MaterialTheme.typography.labelSmall,
                color = AppContentColors.secondaryText()
            )
            Spacer(modifier = Modifier.height(10.dp))
            val mirrorMode = CdnParallelMirrorMode.fromStorageValue(snapshot.parallelMirrorMode)
            val concurrencyPreset = CdnConcurrencyPreset.fromStorageValue(snapshot.parallelConcurrency)
            CdnTransferPanel(
                state = transfers,
                diagnostics = liveDiagnostics,
                checking = checkingPlayback,
                canCheck = activeCandidates.isNotEmpty(),
                parallelEnabled = snapshot.parallelDownloadEnabled,
                experimentalRewriteEnabled = snapshot.experimentalRewriteEnabled,
                onParallelChange = { enabled ->
                    val next = snapshot.copy(parallelDownloadEnabled = enabled)
                    snapshot = next
                    cache = next
                    CdnTransferRuntime.configure(buildParallelConfig(running, next))
                    scope.launch { CdnRegionPluginStore.write(context, next) }
                },
                mirrorMode = mirrorMode,
                concurrencyPreset = concurrencyPreset,
                allowCellular = snapshot.parallelAllowCellular,
                onMirrorModeChange = { nextMode ->
                    val next = snapshot.copy(parallelMirrorMode = nextMode.storageValue)
                    snapshot = next
                    cache = next
                    CdnTransferRuntime.configure(buildParallelConfig(running, next))
                    scope.launch { CdnRegionPluginStore.write(context, next) }
                },
                onConcurrencyPresetChange = { nextPreset ->
                    val next = snapshot.copy(parallelConcurrency = nextPreset.storageValue)
                    snapshot = next
                    cache = next
                    CdnTransferRuntime.configure(buildParallelConfig(running, next))
                    scope.launch { CdnRegionPluginStore.write(context, next) }
                },
                onAllowCellularChange = { allow ->
                    val next = snapshot.copy(parallelAllowCellular = allow)
                    snapshot = next
                    cache = next
                    CdnTransferRuntime.configure(buildParallelConfig(running, next))
                    scope.launch { CdnRegionPluginStore.write(context, next) }
                },
                onCheck = {
                    checkingPlayback = true
                    scope.launch {
                        try {
                            liveDiagnostics = probePlaybackCdnCandidatesInternal(
                                activeCandidates.map { it.videoUrl }, activeCandidates.map { it.source }, true
                            )
                            snapshot = cache
                        } finally {
                            checkingPlayback = false
                        }
                    }
                },
                onAvoid = CdnTransferRuntime::avoid,
                onRestore = CdnTransferRuntime::restoreRoutes,
                modifier = Modifier.fillMaxWidth()
            )
            if (snapshot.parallelDownloadEnabled &&
                !snapshot.experimentalRewriteEnabled &&
                mirrorMode == CdnParallelMirrorMode.CUSTOM_HOSTS
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                AppOutlinedTextField(
                    value = customParallelHostsInput,
                    onValueChange = {
                        customParallelHostsInput = it
                        customParallelHostsError = null
                    },
                    label = { AppText("自定义并发节点（每行或逗号分隔，仅限 B 站 CDN 域名）") },
                    modifier = Modifier.fillMaxWidth()
                )
                customParallelHostsError?.let { error ->
                    AppText(
                        text = error,
                        style = MaterialTheme.typography.labelSmall,
                        color = AppContentColors.error()
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                AppButton(
                    onClick = {
                        val (validHosts, invalidEntries) = parseCustomCdnHosts(customParallelHostsInput)
                        if (invalidEntries.isNotEmpty()) {
                            customParallelHostsError = "仅支持 bilivideo.com / bilivideo.cn / akamaized.net 域名：${invalidEntries.first()}"
                        } else {
                            val next = snapshot.copy(parallelCustomHosts = validHosts)
                            snapshot = next
                            cache = next
                            customParallelHostsInput = validHosts.joinToString("\n")
                            CdnTransferRuntime.configure(buildParallelConfig(running, next))
                            scope.launch { CdnRegionPluginStore.write(context, next) }
                        }
                    }
                ) {
                    AppText("保存并发节点")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                AppText(
                    text = "自适应预缓存",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
                AppSwitch(
                    checked = prefetchEnabled,
                    onCheckedChange = { enabled ->
                        prefetchEnabled = enabled
                        val next = snapshot.copy(prefetchEnabled = enabled)
                        snapshot = next
                        cache = next
                        scope.launch { CdnRegionPluginStore.write(context, next) }
                    }
                )
            }
            AppText(
                text = "仅在 DASH 可读取分片索引且播放器已有至少 15 秒缓冲时下载未来分片；默认关闭以避免额外流量、电量和磁盘消耗。",
                style = MaterialTheme.typography.labelSmall,
                color = AppContentColors.secondaryText()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                AppText(
                    text = "实验性属地 / URL 改写",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
                AppSwitch(
                    checked = experimentalRewriteEnabled,
                    onCheckedChange = { enabled ->
                        experimentalRewriteEnabled = enabled
                        val next = snapshot.copy(experimentalRewriteEnabled = enabled)
                        CdnTransferRuntime.configure(buildParallelConfig(running, next))
                        snapshot = next
                        cache = next
                        scope.launch { CdnRegionPluginStore.write(context, next) }
                    }
                )
            }
            AppText(
                text = "兼容旧版地域 Host 表和高级 URL 替换。它不参与安全自动选线或预缓存，可能因签名与线路策略变化而不可用。",
                style = MaterialTheme.typography.labelSmall,
                color = AppContentColors.secondaryText()
            )
            if (experimentalRewriteEnabled) {
                val hostDiagnostics = buildCdnHostDiagnostics(
                    hosts = probeTarget.hosts.take(5),
                    healthByHost = snapshot.healthByHost
                )
                AppHorizontalDivider(modifier = Modifier.padding(top = 10.dp))
                Spacer(modifier = Modifier.height(12.dp))
                AppText(
                    text = "实验性属地检测",
                    style = MaterialTheme.typography.titleSmall,
                    color = AppContentColors.primaryText()
                )
                AppText(
                    text = "属地：${snapshot.location.province.ifBlank { "未知" }} / ${snapshot.location.city.ifBlank { "未知" }}；运营商：${snapshot.location.isp.ifBlank { "未知" }}；命中区域：$regionText",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppContentColors.secondaryText()
                )
                Spacer(modifier = Modifier.height(8.dp))
                hostDiagnostics.forEachIndexed { index, diagnostic ->
                    AppText(
                        text = "${index + 1}. ${diagnostic.host} · ${formatCdnHostDiagnostic(diagnostic)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppContentColors.primaryText()
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                AppButton(
                    enabled = !probing && probeTarget.hosts.isNotEmpty(),
                    onClick = {
                        probing = true
                        scope.launch {
                            snapshot = probeSelectedHosts(context, snapshot, probeTarget)
                            probing = false
                        }
                    }
                ) {
                    AppText(if (probing) "检测中..." else "检测实验线路")
                }
                AppHorizontalDivider(modifier = Modifier.padding(top = 10.dp))
                Spacer(modifier = Modifier.height(12.dp))
                AppText(
                    text = "高级 URL 替换",
                    style = MaterialTheme.typography.titleSmall,
                    color = AppContentColors.primaryText()
                )
                AppText(
                    text = "规则按顺序匹配完整播放 URL，仅应用首条命中规则。替换结果必须是 HTTPS 且指向 B 站或当前候选 CDN。",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppContentColors.secondaryText()
                )
                Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                AppText(
                    text = "严格使用自定义 CDN",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
                AppSwitch(
                    checked = strictCustomCdn,
                    onCheckedChange = { strictCustomCdn = it }
                )
            }
            AppText(
                text = "开启后，只要有自定义规则成功生成播放地址，就不再加入属地和原始线路；没有规则命中时仍会自动回退，避免无法播放。",
                style = MaterialTheme.typography.labelSmall,
                color = AppContentColors.secondaryText()
            )
            customRules.forEachIndexed { index, rule ->
                Spacer(modifier = Modifier.height(8.dp))
                CdnCustomRuleEditor(
                    index = index,
                    rule = rule,
                    canMoveUp = index > 0,
                    canMoveDown = index < customRules.lastIndex,
                    onChange = { updated ->
                        customRules = customRules.toMutableList().also { it[index] = updated }
                        customRuleError = null
                    },
                    onMoveUp = {
                        customRules = customRules.toMutableList().also { rules ->
                            rules[index] = rules[index - 1].also { rules[index - 1] = rules[index] }
                        }
                    },
                    onMoveDown = {
                        customRules = customRules.toMutableList().also { rules ->
                            rules[index] = rules[index + 1].also { rules[index + 1] = rules[index] }
                        }
                    },
                    onRemove = {
                        customRules = customRules.filterIndexed { itemIndex, _ -> itemIndex != index }
                        customRuleError = null
                    }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                AppButton(onClick = {
                    customRules = customRules + CdnCustomRule(enabled = false)
                    customRuleError = null
                }) {
                    AppText("新增规则")
                }
                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                AppButton(onClick = {
                    val error = validateCdnCustomRules(customRules)
                        .firstOrNull { it.error != null }
                        ?.error
                    if (error != null) {
                        customRuleError = error
                    } else {
                        val next = snapshot.copy(
                            customRules = customRules,
                            strictCustomCdn = strictCustomCdn
                        )
                        snapshot = next
                        cache = next
                        compiledCustomRules = compileCdnCustomRules(next.customRules)
                        scope.launch { CdnRegionPluginStore.write(context, next) }
                    }
                }) {
                    AppText("保存规则")
                }
            }
            customRuleError?.let { error ->
                AppText(
                    text = error,
                    style = MaterialTheme.typography.labelSmall,
                    color = AppContentColors.error()
                )
            }
            AppHorizontalDivider(modifier = Modifier.padding(top = 10.dp))
            }
        }
    }

    private fun publishActivePlaybackCandidates(candidates: List<PlaybackCdnCandidate>) {
        activePlaybackSession = CdnActivePlaybackSession(
            candidates = candidates.distinctBy { it.videoUrl }.take(3),
            updatedAtMs = System.currentTimeMillis()
        )
    }

    private fun activePlaybackSessionOrNull(): CdnActivePlaybackSession? {
        val session = activePlaybackSession ?: return null
        return session.takeIf {
            System.currentTimeMillis() - it.updatedAtMs <= CDN_ACTIVE_PLAYBACK_SESSION_TTL_MS
        }
    }

    private fun formatCdnLineDiagnostic(diagnostic: CdnLineDiagnostic): String {
        return buildList {
            diagnostic.latencyMs?.let { add("${it}ms") }
            diagnostic.speedKbps?.let { add("${it}Kbps") }
            add(diagnostic.statusLabel)
            if (diagnostic.errorCount > 0) add("失败 ${diagnostic.errorCount}")
            if (diagnostic.bufferingCount > 0) add("缓冲 ${diagnostic.bufferingCount}")
        }.distinct().joinToString(" · ")
    }

    @Composable
    private fun CdnCustomRuleEditor(
        index: Int,
        rule: CdnCustomRule,
        canMoveUp: Boolean,
        canMoveDown: Boolean,
        onChange: (CdnCustomRule) -> Unit,
        onMoveUp: () -> Unit,
        onMoveDown: () -> Unit,
        onRemove: () -> Unit
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row {
                AppText(
                    text = "规则 ${index + 1}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
                AppText("启用", style = MaterialTheme.typography.bodySmall)
                AppSwitch(checked = rule.enabled, onCheckedChange = { onChange(rule.copy(enabled = it)) })
            }
            AppOutlinedTextField(
                value = rule.pattern,
                onValueChange = { onChange(rule.copy(pattern = it)) },
                label = { AppText("匹配正则") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            AppOutlinedTextField(
                value = rule.replacement,
                onValueChange = { onChange(rule.copy(replacement = it)) },
                label = { AppText("替换内容") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row {
                AppButton(enabled = canMoveUp, onClick = onMoveUp) { AppText("上移") }
                Spacer(modifier = Modifier.padding(horizontal = 2.dp))
                AppButton(enabled = canMoveDown, onClick = onMoveDown) { AppText("下移") }
                Spacer(modifier = Modifier.padding(horizontal = 2.dp))
                AppButton(onClick = onRemove) { AppText("删除") }
            }
        }
    }

    private suspend fun probeSelectedHosts(
        context: Context,
        current: CdnRegionPluginCache,
        target: CdnSettingsProbeTarget
    ): CdnRegionPluginCache {
        var nextHealth = current.healthByHost
        val now = System.currentTimeMillis()
        target.hosts.take(5).forEach { host ->
            val previous = nextHealth[host] ?: CdnCandidateHealth(host = host)
            val elapsed = now - previous.lastProbeAtMs
            if (previous.lastProbeAtMs > 0L && elapsed < CDN_MANUAL_PROBE_COOLDOWN_MS) {
                return@forEach
            }
            val result = probeHostTlsPort(host)
            nextHealth = nextHealth + (
                host to recordCdnProbeResult(
                    current = previous,
                    latencyMs = result.latencyMs,
                    speedKbps = null,
                    success = result.success,
                    nowMs = System.currentTimeMillis()
                )
            )
        }
        val nextRegion = current.selectedRegion.ifBlank { target.region }
        val nextHosts = current.selectedHosts.ifEmpty { target.hosts }
        return current.copy(
            selectedRegion = nextRegion,
            selectedHosts = nextHosts,
            fallbackUsed = current.fallbackUsed && nextRegion.isBlank(),
            healthByHost = nextHealth
        ).also {
            cache = it
            CdnRegionPluginStore.write(context, it)
        }
    }

    private fun resolveSettingsProbeTarget(
        snapshot: CdnRegionPluginCache,
        catalogSnapshot: Map<String, List<String>>
    ): CdnSettingsProbeTarget {
        val selectedHosts = resolveCdnRegionHosts(
            region = snapshot.selectedRegion,
            cachedHosts = snapshot.selectedHosts,
            catalog = catalogSnapshot,
            isp = snapshot.location.isp
        )
        if (selectedHosts.isNotEmpty()) {
            return CdnSettingsProbeTarget(
                region = snapshot.selectedRegion,
                hosts = selectedHosts
            )
        }

        val selection = selectCdnRegionForLocation(
            location = snapshot.location,
            catalog = catalogSnapshot
        )
        return CdnSettingsProbeTarget(
            region = selection.region,
            hosts = selection.hosts
        )
    }

    private fun formatCdnHostDiagnostic(diagnostic: CdnHostDiagnostic): String {
        val parts = buildList {
            if (diagnostic.latencyMs != null) {
                add("延迟 ${diagnostic.latencyMs}ms")
            }
            if (diagnostic.speedKbps != null) {
                add("速度 ${diagnostic.speedKbps}Kbps")
            }
            when {
                diagnostic.lastProbeAtMs > 0L && diagnostic.latencyMs == null -> add("检测失败")
                diagnostic.lastProbeAtMs <= 0L -> add("未检测")
            }
            add(diagnostic.statusLabel)
            if (diagnostic.errorCount > 0) {
                add("失败 ${diagnostic.errorCount} 次")
            }
        }
        return parts.distinct().joinToString(" · ")
    }

    private suspend fun probePlaybackUrl(url: String): CdnProbeMeasure {
        return try {
            val sample = readExactCdnRange(
                NetworkModule.playbackOkHttpClient, url, CdnByteRange(0, CDN_PROBE_SAMPLE_BYTES - 1L)
            )
            CdnProbeMeasure(
                success = true,
                latencyMs = sample.firstByteMs,
                speedKbps = (sample.bytes.size * 8L / sample.elapsedMs).coerceAtLeast(1)
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: java.io.IOException) {
            Logger.w(TAG, "CDN 播放候选检测失败: ${hostFromCdnUrl(url)} ${error.javaClass.simpleName}")
            CdnProbeMeasure(false, null, null)
        }
    }

    private suspend fun probeHostTlsPort(host: String): CdnProbeMeasure {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val startedAt = System.nanoTime()
            runCatching {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, 443), 3_000)
                }
                val elapsedMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(1L)
                CdnProbeMeasure(success = true, latencyMs = elapsedMs, speedKbps = null)
            }.getOrElse { error ->
                if (error is CancellationException) throw error
                Logger.w(TAG, "CDN host 检测失败: $host ${error.message}")
                CdnProbeMeasure(success = false, latencyMs = null, speedKbps = null)
            }
        }
    }

    private suspend fun refreshIpLocationIfNeeded() {
        val context = PluginManager.getContext()
        val enabled = PluginStore.isEnabled(context, id)
        val loadedCatalog = catalog.ifEmpty {
            loadCdnRegionCatalog(context).also { catalog = it }
        }
        if (loadedCatalog.isEmpty()) return
        val current = CdnRegionPluginStore.read(context).also { cache = it }
        val hasSelection = hasUsableCdnRegionSelection(
            region = current.selectedRegion,
            cachedHosts = current.selectedHosts,
            catalog = loadedCatalog
        )

        if (!shouldRefreshCdnIpLocation(
                enabled = enabled,
                nowMs = System.currentTimeMillis(),
                lastRefreshMs = current.refreshedAtMs,
                hasSelection = hasSelection && !current.fallbackUsed
            )
        ) {
            val resolvedHosts = resolveCdnRegionHosts(
                region = current.selectedRegion,
                cachedHosts = current.selectedHosts,
                catalog = loadedCatalog,
                isp = current.location.isp
            )
            if (current.selectedHosts != resolvedHosts) {
                val corrected = current.copy(
                    selectedHosts = resolvedHosts
                )
                cache = corrected
                CdnRegionPluginStore.write(context, corrected)
            }
            return
        }

        try {
            val response = NetworkModule.api.getIpZone()
            val data = response.data
            if (response.code != 0 || data == null) {
                error(response.message.ifBlank { "IP 属地接口返回 code=${response.code}" })
            }

            val location = IpLocationSnapshot(
                addr = data.addr,
                country = data.country,
                province = data.province,
                city = data.city,
                isp = data.isp
            )
            val selection = selectCdnRegionForLocation(
                location = location,
                catalog = loadedCatalog
            )
            val next = current.copy(
                location = location,
                selectedRegion = selection.region,
                selectedHosts = selection.hosts,
                fallbackRegion = "",
                fallbackUsed = selection.fallbackUsed,
                refreshedAtMs = System.currentTimeMillis(),
                lastError = null
            )
            cache = next
            CdnRegionPluginStore.write(context, next)
            Logger.d(
                TAG,
                "CDN 属地刷新成功: ip=${maskIpAddressForLog(location.addr)}, " +
                    "${location.country}/${location.province}/${location.city}, isp=${location.isp.ifBlank { "未知" }} -> " +
                    (selection.region.ifBlank { "未命中" })
            )
        } catch (e: Exception) {
            val preserved = current.copy(lastError = e.message ?: e.javaClass.simpleName)
            cache = preserved
            CdnRegionPluginStore.write(context, preserved)
            Logger.w(TAG, "CDN 属地刷新失败，保留旧缓存: ${e.message}")
        }
    }
}

@Serializable
internal data class CdnRegionPluginCache(
    val location: IpLocationSnapshot = IpLocationSnapshot(),
    val selectedRegion: String = "",
    val selectedHosts: List<String> = emptyList(),
    val fallbackRegion: String = "",
    val fallbackUsed: Boolean = false,
    val refreshedAtMs: Long = 0L,
    val lastError: String? = null,
    val healthByHost: Map<String, CdnCandidateHealth> = emptyMap(),
    val customRules: List<CdnCustomRule> = emptyList(),
    val strictCustomCdn: Boolean = false,
    val prefetchEnabled: Boolean = false,
    val parallelDownloadEnabled: Boolean = false,
    val experimentalRewriteEnabled: Boolean = false,
    val parallelMirrorMode: String = CdnParallelMirrorMode.SAFE_SIGNED.storageValue,
    val parallelConcurrency: Int = CdnConcurrencyPreset.CONSERVATIVE.storageValue,
    val parallelAllowCellular: Boolean = false,
    val parallelCustomHosts: List<String> = emptyList()
)

internal object CdnRegionPluginStore {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun read(context: Context): CdnRegionPluginCache {
        val raw = PluginStore.getConfigJson(context, BuiltinPluginIds.PLAYBACK_CDN) ?: return CdnRegionPluginCache()
        val stored = runCatching { json.decodeFromString<CdnRegionPluginCache>(raw) }
            .getOrDefault(CdnRegionPluginCache())
        val now = System.currentTimeMillis()
        return stored.copy(healthByHost = stored.healthByHost.mapValues { decayCdnHealth(it.value, now) })
    }

    suspend fun write(context: Context, cache: CdnRegionPluginCache) {
        PluginStore.setConfigJson(
            context = context,
            pluginId = BuiltinPluginIds.PLAYBACK_CDN,
            configJson = json.encodeToString(cache)
        )
    }
}

internal fun loadCdnRegionCatalog(context: Context): Map<String, List<String>> {
    return runCatching {
        context.resources.openRawResource(R.raw.cdn_region_catalog).bufferedReader().use { reader ->
            Json.decodeFromString<Map<String, List<String>>>(reader.readText())
        }.filterValues { hosts -> hosts.any { it.isNotBlank() } }
            .mapValues { (_, hosts) -> hosts.filter { it.isNotBlank() }.distinct() }
    }.getOrElse { error ->
        Logger.w(TAG, "读取 CDN catalog 失败: ${error.message}")
        emptyMap()
    }
}
