package com.android.purebilibili.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.android.purebilibili.core.database.AppDatabase
import com.android.purebilibili.core.database.entity.BlockedUp
import com.android.purebilibili.core.network.BilibiliApi
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.store.TokenManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

private const val BILIBILI_RELATION_ACT_BLOCK = 5
private const val BILIBILI_RELATION_ACT_UNBLOCK = 6
private const val BILIBILI_RELATION_PROFILE_BLOCK_RE_SRC = 11
private const val BILIBILI_RELATION_COMMENT_BLOCK_RE_SRC = 15
private const val BLOCKED_UP_PROFILE_REFRESH_DELAY_MS = 120L
private const val BLOCKED_UP_SHARE_MARKER = "BILIPAI_BLOCKED_UPS_V1"

data class BlockedUpImportItem(
    val mid: Long,
    val name: String = "",
    val face: String = "",
    val sign: String = "",
    val level: Int? = null,
    val vipLabel: String = "",
    val officialTitle: String = "",
    val follower: Long? = null,
    val archiveCount: Int? = null,
    val isDeleted: Boolean = false
)

data class BlockedUpImportResult(
    val importedCount: Int,
    val existingCount: Int,
    val failedCount: Int,
    val message: String
)

enum class BilibiliBlockedListRemoteStatus {
    SUCCESS,
    SKIPPED_NOT_LOGGED_IN,
    FAILED
}

enum class BlockedUpRelationSource {
    PROFILE,
    COMMENT
}

data class BlockedUpWriteResult(
    val localChanged: Boolean,
    val remoteStatus: BilibiliBlockedListRemoteStatus,
    val message: String
)

data class BlockedUpMetadataRefreshResult(
    val updatedCount: Int,
    val deletedCount: Int,
    val failedCount: Int,
    val message: String
)

internal fun resolveBlockedUpRelationReSrc(source: BlockedUpRelationSource): Int {
    return when (source) {
        BlockedUpRelationSource.PROFILE -> BILIBILI_RELATION_PROFILE_BLOCK_RE_SRC
        BlockedUpRelationSource.COMMENT -> BILIBILI_RELATION_COMMENT_BLOCK_RE_SRC
    }
}

@Serializable
private data class BlockedUpSharePayload(
    val version: Int = 1,
    val items: List<BlockedUpShareItem>
)

@Serializable
private data class BlockedUpShareItem(
    val mid: Long,
    val name: String = "",
    val face: String = "",
    val sign: String = "",
    val level: Int? = null,
    val vipLabel: String = "",
    val officialTitle: String = "",
    val follower: Long? = null,
    val archiveCount: Int? = null,
    val isDeleted: Boolean = false
)

private val blockedUpShareJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
}

internal data class BlockedUpImportPlan(
    val itemsToInsert: List<BlockedUpImportItem>,
    val existingCount: Int,
    val failedCount: Int
)

internal fun buildBlockedUpImportPlan(
    existingMids: Set<Long>,
    items: List<BlockedUpImportItem>
): BlockedUpImportPlan {
    var existingCount = 0
    var failedCount = 0
    val seenMids = mutableSetOf<Long>()
    val itemsToInsert = mutableListOf<BlockedUpImportItem>()

    items.forEach { item ->
        val mid = item.mid
        if (mid <= 0L || !seenMids.add(mid)) {
            failedCount += 1
            return@forEach
        }
        if (mid in existingMids) {
            existingCount += 1
            return@forEach
        }
        itemsToInsert += item.copy(
            name = item.name.ifBlank { "UP主$mid" }
        )
    }

    return BlockedUpImportPlan(
        itemsToInsert = itemsToInsert,
        existingCount = existingCount,
        failedCount = failedCount
    )
}

internal fun buildBlockedUpImportMessage(
    importedCount: Int,
    existingCount: Int,
    failedCount: Int
): String {
    return when {
        importedCount > 0 -> "已导入 $importedCount 个 B站黑名单用户，$existingCount 个已存在"
        existingCount > 0 && failedCount == 0 -> "B站黑名单已同步，$existingCount 个用户已在本地黑名单中"
        failedCount > 0 -> "导入完成，$importedCount 个新增，$existingCount 个已存在，$failedCount 个无效条目已跳过"
        else -> "没有可导入的 B站黑名单用户"
    }
}

internal fun buildBlockedUpWriteMessage(
    blocked: Boolean,
    remoteStatus: BilibiliBlockedListRemoteStatus,
    remoteMessage: String? = null
): String {
    val localMessage = if (blocked) "已屏蔽该 UP 主" else "已解除屏蔽"
    return when (remoteStatus) {
        BilibiliBlockedListRemoteStatus.SUCCESS -> {
            if (blocked) "$localMessage，并已写入 B站黑名单" else "$localMessage，并已同步 B站黑名单"
        }
        BilibiliBlockedListRemoteStatus.SKIPPED_NOT_LOGGED_IN -> {
            "$localMessage；未登录，未同步 B站黑名单"
        }
        BilibiliBlockedListRemoteStatus.FAILED -> {
            "$localMessage；B站黑名单同步失败：${remoteMessage.orEmpty().ifBlank { "未知错误" }}"
        }
    }
}

internal fun buildBlockedUpMetadataRefreshMessage(
    updatedCount: Int,
    deletedCount: Int,
    failedCount: Int
): String {
    return when {
        updatedCount == 0 && deletedCount == 0 && failedCount == 0 -> "黑名单为空，无需刷新资料"
        failedCount == 0 && deletedCount == 0 -> "已刷新 $updatedCount 个黑名单用户资料"
        failedCount == 0 -> "已刷新 $updatedCount 个用户资料，$deletedCount 个账号疑似已注销"
        else -> "刷新完成：$updatedCount 个成功，$deletedCount 个疑似已注销，$failedCount 个失败"
    }
}

fun buildBlockedUpShareText(blockedUps: List<BlockedUp>): String {
    if (blockedUps.isEmpty()) {
        return "BiliPai 黑名单导出\n暂无黑名单用户\n\n$BLOCKED_UP_SHARE_MARKER\n${buildBlockedUpShareJson(blockedUps)}"
    }

    val body = blockedUps.mapIndexed { index, up ->
        val name = up.name.ifBlank { "UP主${up.mid}" }
        val statusText = if (up.isDeleted) "疑似已注销" else "正常"
        val profileUrl = "https://space.bilibili.com/${up.mid}"
        buildString {
            append("${index + 1}. $name\n")
            append("UID: ${up.mid}\n")
            append("状态: $statusText")
            up.level?.let { append(" · LV$it") }
            up.vipLabel.takeIf { it.isNotBlank() }?.let { append(" · $it") }
            up.officialTitle.takeIf { it.isNotBlank() }?.let { append(" · $it") }
            append('\n')
            up.follower?.let { append("粉丝: $it\n") }
            up.archiveCount?.let { append("投稿: $it\n") }
            up.sign.takeIf { it.isNotBlank() }?.let { append("签名: ${it.trim()}\n") }
            append("主页: $profileUrl")
        }
    }.joinToString(separator = "\n\n")

    return "BiliPai 黑名单导出（${blockedUps.size} 个用户）\n\n$body\n\n$BLOCKED_UP_SHARE_MARKER\n" +
        buildBlockedUpShareJson(blockedUps)
}

fun buildBlockedUpShareJson(blockedUps: List<BlockedUp>): String {
    return blockedUpShareJson.encodeToString(buildBlockedUpSharePayload(blockedUps))
}

fun parseBlockedUpShareText(text: String): List<BlockedUpImportItem> {
    parseBlockedUpSharePayload(text.trim())?.let { payload ->
        return payload.toImportItems()
    }

    val markerIndex = text.indexOf(BLOCKED_UP_SHARE_MARKER)
    if (markerIndex >= 0) {
        val payloadText = text
            .substring(markerIndex + BLOCKED_UP_SHARE_MARKER.length)
            .trim()
        parseBlockedUpSharePayload(payloadText)?.let { payload ->
            return payload.toImportItems()
        }
    }

    return Regex("""(?m)^\s*UID:\s*(\d+)\s*$""")
        .findAll(text)
        .mapNotNull { match ->
            val mid = match.groupValues.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
            BlockedUpImportItem(mid = mid)
        }
        .toList()
}

private fun buildBlockedUpSharePayload(blockedUps: List<BlockedUp>): BlockedUpSharePayload {
    return BlockedUpSharePayload(
        items = blockedUps.map { up ->
            BlockedUpShareItem(
                mid = up.mid,
                name = up.name,
                face = up.face,
                sign = up.sign,
                level = up.level,
                vipLabel = up.vipLabel,
                officialTitle = up.officialTitle,
                follower = up.follower,
                archiveCount = up.archiveCount,
                isDeleted = up.isDeleted
            )
        }
    )
}

private fun parseBlockedUpSharePayload(text: String): BlockedUpSharePayload? {
    if (!text.startsWith("{")) return null
    return runCatching {
        blockedUpShareJson.decodeFromString<BlockedUpSharePayload>(text)
    }.getOrNull()
}

private fun BlockedUpSharePayload.toImportItems(): List<BlockedUpImportItem> {
    return items.map { item ->
        BlockedUpImportItem(
            mid = item.mid,
            name = item.name,
            face = item.face,
            sign = item.sign,
            level = item.level,
            vipLabel = item.vipLabel,
            officialTitle = item.officialTitle,
            follower = item.follower,
            archiveCount = item.archiveCount,
            isDeleted = item.isDeleted
        )
    }
}

class BlockedUpRepository(
    context: Context,
    private val api: BilibiliApi = NetworkModule.api
) {
    companion object {
        @Volatile
        private var sharedInstance: BlockedUpRepository? = null

        /**
         * 列表项（评论/相关视频）滚动组合用的共享实例：每项新建仓库会重复打开
         * 数据库帮助类，fling 时是新 item 首次组合的主要开销。
         */
        fun getInstance(context: Context): BlockedUpRepository =
            sharedInstance ?: synchronized(this) {
                sharedInstance ?: BlockedUpRepository(context.applicationContext)
                    .also { sharedInstance = it }
            }
    }

    private val database = AppDatabase.getDatabase(context)
    private val blockedUpDao = database.blockedUpDao()

    /** 一次编辑只提交一次列表变更，并保留已有用户的名称、头像与屏蔽时间。 */
    suspend fun replaceLocalBlockedUserIds(mids: Set<Long>) {
        database.withTransaction {
            val existing = blockedUpDao.getAllBlockedUpsSnapshot().mapTo(hashSetOf()) { it.mid }
            (existing - mids).forEach { blockedUpDao.delete(it) }
            (mids - existing).forEach { blockedUpDao.insert(BlockedUp(mid = it, name = "UID:$it", face = "")) }
        }
    }

    fun getAllBlockedUps(): Flow<List<BlockedUp>> = blockedUpDao.getAllBlockedUps()

    fun isBlocked(mid: Long): Flow<Boolean> = blockedUpDao.isBlocked(mid)

    suspend fun blockUp(mid: Long, name: String, face: String) {
        val entity = BlockedUp(mid = mid, name = name, face = face)
        blockedUpDao.insert(entity)
    }

    suspend fun blockUpWithBilibiliSync(
        mid: Long,
        name: String,
        face: String,
        relationSource: BlockedUpRelationSource = BlockedUpRelationSource.PROFILE
    ): BlockedUpWriteResult {
        blockUp(mid = mid, name = name, face = face)
        val remoteResult = modifyBilibiliBlockedList(
            mid = mid,
            blocked = true,
            relationSource = relationSource
        )
        return BlockedUpWriteResult(
            localChanged = true,
            remoteStatus = remoteResult.first,
            message = buildBlockedUpWriteMessage(
                blocked = true,
                remoteStatus = remoteResult.first,
                remoteMessage = remoteResult.second
            )
        )
    }

    suspend fun importBlockedUps(items: List<BlockedUpImportItem>): BlockedUpImportResult {
        val candidateMids = items.map { it.mid }.filter { it > 0L }.distinct()
        val existingMids = candidateMids
            .filter { blockedUpDao.getBlockedUp(it) != null }
            .toSet()
        val plan = buildBlockedUpImportPlan(existingMids = existingMids, items = items)

        plan.itemsToInsert.forEach { item ->
            blockedUpDao.insert(
                BlockedUp(
                    mid = item.mid,
                    name = item.name,
                    face = item.face,
                    sign = item.sign,
                    level = item.level,
                    vipLabel = item.vipLabel,
                    officialTitle = item.officialTitle,
                    follower = item.follower,
                    archiveCount = item.archiveCount,
                    isDeleted = item.isDeleted
                )
            )
        }

        val importedCount = plan.itemsToInsert.size
        return BlockedUpImportResult(
            importedCount = importedCount,
            existingCount = plan.existingCount,
            failedCount = plan.failedCount,
            message = buildBlockedUpImportMessage(
                importedCount = importedCount,
                existingCount = plan.existingCount,
                failedCount = plan.failedCount
            )
        )
    }

    suspend fun unblockUp(mid: Long) {
        blockedUpDao.delete(mid)
    }

    suspend fun unblockUpWithBilibiliSync(
        mid: Long,
        relationSource: BlockedUpRelationSource = BlockedUpRelationSource.PROFILE
    ): BlockedUpWriteResult {
        unblockUp(mid)
        val remoteResult = modifyBilibiliBlockedList(
            mid = mid,
            blocked = false,
            relationSource = relationSource
        )
        return BlockedUpWriteResult(
            localChanged = true,
            remoteStatus = remoteResult.first,
            message = buildBlockedUpWriteMessage(
                blocked = false,
                remoteStatus = remoteResult.first,
                remoteMessage = remoteResult.second
            )
        )
    }

    suspend fun refreshBlockedUpProfiles(): BlockedUpMetadataRefreshResult = withContext(Dispatchers.IO) {
        val blockedUps = blockedUpDao.getAllBlockedUpsSnapshot()
        var updatedCount = 0
        var deletedCount = 0
        var failedCount = 0
        val now = System.currentTimeMillis()

        blockedUps.forEachIndexed { index, up ->
            val response = runCatching { api.getUserCard(mid = up.mid, photo = true) }.getOrNull()
            val card = response?.data?.card
            if (response?.code == 0 && card != null) {
                blockedUpDao.insert(
                    up.copy(
                        name = card.name.ifBlank { up.name },
                        face = card.face.ifBlank { up.face },
                        sign = card.sign,
                        level = card.level_info?.current_level,
                        vipLabel = card.vip?.label?.text.orEmpty(),
                        officialTitle = card.Official?.title.orEmpty(),
                        follower = requireNotNull(response?.data).follower.toLong(),
                        archiveCount = requireNotNull(response?.data).archive_count,
                        isDeleted = false,
                        lastSyncedAt = now
                    )
                )
                updatedCount += 1
            } else if (response != null) {
                blockedUpDao.insert(
                    up.copy(
                        isDeleted = true,
                        lastSyncedAt = now
                    )
                )
                deletedCount += 1
            } else {
                failedCount += 1
            }
            if (index != blockedUps.lastIndex) {
                delay(BLOCKED_UP_PROFILE_REFRESH_DELAY_MS)
            }
        }

        BlockedUpMetadataRefreshResult(
            updatedCount = updatedCount,
            deletedCount = deletedCount,
            failedCount = failedCount,
            message = buildBlockedUpMetadataRefreshMessage(
                updatedCount = updatedCount,
                deletedCount = deletedCount,
                failedCount = failedCount
            )
        )
    }

    private suspend fun modifyBilibiliBlockedList(
        mid: Long,
        blocked: Boolean,
        relationSource: BlockedUpRelationSource
    ): Pair<BilibiliBlockedListRemoteStatus, String?> {
        val csrf = TokenManager.csrfCache.orEmpty()
        if (csrf.isBlank()) return BilibiliBlockedListRemoteStatus.SKIPPED_NOT_LOGGED_IN to null

        return runCatching {
            api.modifyRelation(
                fid = mid,
                act = if (blocked) BILIBILI_RELATION_ACT_BLOCK else BILIBILI_RELATION_ACT_UNBLOCK,
                csrf = csrf,
                reSrc = resolveBlockedUpRelationReSrc(relationSource)
            )
        }.fold(
            onSuccess = { response ->
                if (response.code == 0) {
                    BilibiliBlockedListRemoteStatus.SUCCESS to null
                } else {
                    BilibiliBlockedListRemoteStatus.FAILED to response.message.ifBlank {
                        "接口返回 ${response.code}"
                    }
                }
            },
            onFailure = { error ->
                BilibiliBlockedListRemoteStatus.FAILED to error.message
            }
        )
    }
}
