package com.android.purebilibili.feature.plugin

import android.content.Context
import com.android.purebilibili.core.plugin.BuiltinPluginIds
import com.android.purebilibili.core.plugin.PluginStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val SponsorBlockConfigJson = Json { ignoreUnknownKeys = true }
private val SponsorBlockConfigMutex = Mutex()

/** Serialize the full read/migrate/write cycle so every consumer shares one persisted identity. */
internal suspend fun loadSponsorBlockConfig(
    context: Context,
    current: SponsorBlockConfig? = null,
): SponsorBlockConfig = SponsorBlockConfigMutex.withLock {
    val raw = PluginStore.getConfigJson(context, BuiltinPluginIds.SPONSOR_BLOCK)
    val decoded = raw?.let { SponsorBlockConfigJson.decodeFromString<SponsorBlockConfig>(it) }
    val loaded = decoded ?: current ?: SponsorBlockConfig()
    val normalized = loaded.copy(
        userId = loaded.userId.ifBlank { current?.userId.orEmpty() },
    ).normalized()
    if (decoded == null || decoded.userId.isBlank()) {
        PluginStore.setConfigJson(
            context,
            BuiltinPluginIds.SPONSOR_BLOCK,
            SponsorBlockConfigJson.encodeToString(normalized),
        )
    }
    normalized
}

/** Reactive read of the feed badge toggle so card badges respond to settings changes immediately. */
internal fun sponsorBlockFeedBadgeEnabledFlow(context: Context): Flow<Boolean> =
    PluginStore.getConfigJsonFlow(context, BuiltinPluginIds.SPONSOR_BLOCK).map { raw ->
        raw?.let { SponsorBlockConfigJson.decodeFromString<SponsorBlockConfig>(it).feedBadgeEnabled } ?: true
    }.distinctUntilChanged()
