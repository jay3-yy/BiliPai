package com.android.purebilibili.feature.plugin

import com.android.purebilibili.core.plugin.BuiltinPluginIds

import android.content.Context
import com.android.purebilibili.core.plugin.PluginStore
import com.android.purebilibili.data.model.response.VideoItem
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val AD_FILTER_HISTORY_STORE_NAME = "filter_history"
private const val AD_FILTER_UP_PROFILE_STORE_NAME = "up_profiles"
private const val AD_FILTER_HISTORY_LIMIT = 120
private const val AD_FILTER_UP_PROFILE_LIMIT = 160

@Serializable
internal enum class AdFilterReasonType {
    BLOCKED_UP,
    SPONSORED,
    CLICKBAIT,
    CUSTOM_KEYWORD,
    TAG_KEYWORD,
    LOW_VIEW
}

@Serializable
internal data class AdFilterRecord(
    val reasonType: AdFilterReasonType,
    val reasonLabel: String,
    val matchedText: String,
    val videoTitle: String,
    val bvid: String,
    val cid: Long,
    val videoCoverUrl: String,
    val upName: String,
    val upFaceUrl: String,
    val upMid: Long,
    val viewCount: Int,
    val timestampMs: Long
)

internal data class AdFilterInsightSummary(
    val totalFilteredCount: Int,
    val blockedUpCount: Int,
    val sponsoredRecords: List<AdFilterRecord>,
    val clickbaitRecords: List<AdFilterRecord>,
    val lowViewRecords: List<AdFilterRecord>,
    val customKeywordRecords: List<AdFilterRecord>,
    val upProfiles: List<AdFilterUpProfile>,
    val blockedUpProfiles: List<AdFilterBlockedUpProfile>
)

@Serializable
internal data class AdFilterUpProfile(
    val name: String,
    val faceUrl: String,
    val mid: Long,
    val updatedAtMs: Long
)

internal data class AdFilterBlockedUpProfile(
    val name: String,
    val faceUrl: String,
    val mid: Long,
    val filteredCount: Int
)

internal fun buildAdFilterRecord(
    item: VideoItem,
    reasonType: AdFilterReasonType,
    matchedText: String,
    timestampMs: Long = System.currentTimeMillis()
): AdFilterRecord {
    return AdFilterRecord(
        reasonType = reasonType,
        reasonLabel = resolveAdFilterReasonLabel(reasonType),
        matchedText = matchedText,
        videoTitle = item.title,
        bvid = item.bvid,
        cid = item.cid,
        videoCoverUrl = item.pic,
        upName = item.owner.name,
        upFaceUrl = item.owner.face,
        upMid = item.owner.mid,
        viewCount = item.stat.view,
        timestampMs = timestampMs
    )
}

internal fun resolveAdFilterInsightSummary(
    records: List<AdFilterRecord>,
    blockedUpNames: List<String>,
    cachedUpProfiles: List<AdFilterUpProfile> = emptyList(),
    recentLimit: Int = 6
): AdFilterInsightSummary {
    val sortedRecords = records.sortedByDescending { it.timestampMs }
    val safeLimit = recentLimit.coerceAtLeast(1)
    val upProfiles = resolveAdFilterKnownUpProfiles(
        records = records,
        cachedUpProfiles = cachedUpProfiles
    )
    return AdFilterInsightSummary(
        totalFilteredCount = records.size,
        blockedUpCount = blockedUpNames.size,
        sponsoredRecords = sortedRecords
            .filter { it.reasonType == AdFilterReasonType.SPONSORED }
            .take(safeLimit),
        clickbaitRecords = sortedRecords
            .filter { it.reasonType == AdFilterReasonType.CLICKBAIT }
            .take(safeLimit),
        lowViewRecords = sortedRecords
            .filter { it.reasonType == AdFilterReasonType.LOW_VIEW }
            .take(safeLimit),
        customKeywordRecords = sortedRecords
            .filter { it.reasonType == AdFilterReasonType.CUSTOM_KEYWORD || it.reasonType == AdFilterReasonType.TAG_KEYWORD }
            .take(safeLimit),
        upProfiles = upProfiles,
        blockedUpProfiles = resolveAdFilterBlockedUpProfiles(
            records = records,
            blockedUpNames = blockedUpNames,
            cachedUpProfiles = upProfiles
        )
    )
}

internal fun resolveAdFilterBlockedUpProfiles(
    records: List<AdFilterRecord>,
    blockedUpNames: List<String>,
    cachedUpProfiles: List<AdFilterUpProfile> = emptyList()
): List<AdFilterBlockedUpProfile> {
    return blockedUpNames.map { name ->
        val matchedRecords = records
            .filter { record ->
                record.reasonType == AdFilterReasonType.BLOCKED_UP &&
                    record.upName.equals(name, ignoreCase = true)
            }
            .sortedByDescending { it.timestampMs }
        val latest = matchedRecords.firstOrNull()
        val latestFace = matchedRecords.firstOrNull { it.upFaceUrl.isNotBlank() }?.upFaceUrl.orEmpty()
        val cachedProfile = findAdFilterUpProfile(
            profiles = cachedUpProfiles,
            name = name,
            mid = latest?.upMid ?: 0L
        )
        AdFilterBlockedUpProfile(
            name = name,
            faceUrl = latestFace.ifBlank { cachedProfile?.faceUrl.orEmpty() },
            mid = latest?.upMid?.takeIf { it > 0L } ?: cachedProfile?.mid ?: 0L,
            filteredCount = matchedRecords.size
        )
    }
}

internal fun resolveAdFilterKnownUpProfiles(
    records: List<AdFilterRecord>,
    cachedUpProfiles: List<AdFilterUpProfile> = emptyList()
): List<AdFilterUpProfile> {
    val recordProfiles = records
        .sortedByDescending { it.timestampMs }
        .mapNotNull { record ->
            if (record.upName.isBlank() || (record.upFaceUrl.isBlank() && record.upMid <= 0L)) {
                null
            } else {
                AdFilterUpProfile(
                    name = record.upName,
                    faceUrl = record.upFaceUrl,
                    mid = record.upMid,
                    updatedAtMs = record.timestampMs
                )
            }
        }
    return mergeAdFilterUpProfiles(recordProfiles + cachedUpProfiles)
}

internal fun resolveAdFilterRecordUpFaceUrl(
    record: AdFilterRecord,
    upProfiles: List<AdFilterUpProfile>
): String {
    return record.upFaceUrl.ifBlank {
        findAdFilterUpProfile(
            profiles = upProfiles,
            name = record.upName,
            mid = record.upMid
        )?.faceUrl.orEmpty()
    }
}

internal fun resolveAdFilterReasonLabel(reasonType: AdFilterReasonType): String {
    return when (reasonType) {
        AdFilterReasonType.BLOCKED_UP -> "UP 拉黑"
        AdFilterReasonType.SPONSORED -> "广告推广"
        AdFilterReasonType.CLICKBAIT -> "标题党"
        AdFilterReasonType.CUSTOM_KEYWORD -> "关键词"
        AdFilterReasonType.TAG_KEYWORD -> "视频标签"
        AdFilterReasonType.LOW_VIEW -> "低播放量"
    }
}

internal object AdFilterInsightStore {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val writeMutex = Mutex()
    private val profileWriteMutex = Mutex()

    suspend fun readRecords(context: Context): List<AdFilterRecord> {
        val value = PluginStore.getDataJson(
            context = context,
            pluginId = BuiltinPluginIds.AD_FILTER,
            name = AD_FILTER_HISTORY_STORE_NAME
        ) ?: return emptyList()
        return runCatching {
            json.decodeFromString(
                ListSerializer(AdFilterRecord.serializer()),
                value
            )
        }.getOrDefault(emptyList())
    }

    suspend fun appendRecord(
        context: Context,
        record: AdFilterRecord
    ) {
        writeMutex.withLock {
            val nextRecords = (listOf(record) + readRecords(context))
                .distinctBy { "${it.reasonType}:${it.bvid}:${it.cid}:${it.videoTitle}:${it.upMid}" }
                .sortedByDescending { it.timestampMs }
                .take(AD_FILTER_HISTORY_LIMIT)
            PluginStore.setDataJson(
                context = context,
                pluginId = BuiltinPluginIds.AD_FILTER,
                name = AD_FILTER_HISTORY_STORE_NAME,
                dataJson = json.encodeToString(
                    ListSerializer(AdFilterRecord.serializer()),
                    nextRecords
                )
            )
        }
        record.toUpProfileOrNull()?.let { profile ->
            upsertUpProfiles(context, listOf(profile))
        }
    }

    suspend fun readUpProfiles(context: Context): List<AdFilterUpProfile> {
        val value = PluginStore.getDataJson(
            context = context,
            pluginId = BuiltinPluginIds.AD_FILTER,
            name = AD_FILTER_UP_PROFILE_STORE_NAME
        ) ?: return emptyList()
        return runCatching {
            json.decodeFromString(
                ListSerializer(AdFilterUpProfile.serializer()),
                value
            )
        }.getOrDefault(emptyList())
    }

    suspend fun upsertUpProfiles(
        context: Context,
        profiles: List<AdFilterUpProfile>
    ) {
        val validProfiles = profiles.filter { profile ->
            profile.name.isNotBlank() && (profile.faceUrl.isNotBlank() || profile.mid > 0L)
        }
        if (validProfiles.isEmpty()) return

        profileWriteMutex.withLock {
            val nextProfiles = mergeAdFilterUpProfiles(validProfiles + readUpProfiles(context))
                .sortedByDescending { it.updatedAtMs }
                .take(AD_FILTER_UP_PROFILE_LIMIT)
            PluginStore.setDataJson(
                context = context,
                pluginId = BuiltinPluginIds.AD_FILTER,
                name = AD_FILTER_UP_PROFILE_STORE_NAME,
                dataJson = json.encodeToString(
                    ListSerializer(AdFilterUpProfile.serializer()),
                    nextProfiles
                )
            )
        }
    }
}

private fun AdFilterRecord.toUpProfileOrNull(): AdFilterUpProfile? {
    if (upName.isBlank() || (upFaceUrl.isBlank() && upMid <= 0L)) return null
    return AdFilterUpProfile(
        name = upName,
        faceUrl = upFaceUrl,
        mid = upMid,
        updatedAtMs = timestampMs
    )
}

private fun mergeAdFilterUpProfiles(profiles: List<AdFilterUpProfile>): List<AdFilterUpProfile> {
    val merged = mutableListOf<AdFilterUpProfile>()
    profiles
        .filter { it.name.isNotBlank() }
        .sortedByDescending { it.updatedAtMs }
        .forEach { profile ->
            val index = merged.indexOfFirst { existing ->
                isSameAdFilterUpProfile(existing, profile)
            }
            if (index < 0) {
                merged += profile
            } else {
                val existing = merged[index]
                merged[index] = AdFilterUpProfile(
                    name = existing.name.ifBlank { profile.name },
                    faceUrl = existing.faceUrl.ifBlank { profile.faceUrl },
                    mid = existing.mid.takeIf { it > 0L } ?: profile.mid,
                    updatedAtMs = maxOf(existing.updatedAtMs, profile.updatedAtMs)
                )
            }
        }
    return merged
}

private fun findAdFilterUpProfile(
    profiles: List<AdFilterUpProfile>,
    name: String,
    mid: Long
): AdFilterUpProfile? {
    return profiles.firstOrNull { profile ->
        profile.faceUrl.isNotBlank() && mid > 0L && profile.mid == mid
    } ?: profiles.firstOrNull { profile ->
        profile.faceUrl.isNotBlank() && profile.name.equals(name, ignoreCase = true)
    }
}

private fun isSameAdFilterUpProfile(
    first: AdFilterUpProfile,
    second: AdFilterUpProfile
): Boolean {
    return (first.mid > 0L && second.mid > 0L && first.mid == second.mid) ||
        first.name.equals(second.name, ignoreCase = true)
}
