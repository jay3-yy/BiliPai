package com.android.purebilibili.feature.bangumi

import com.android.purebilibili.data.model.response.MyFollowBangumiData
import com.android.purebilibili.data.model.response.UserStatus
import com.android.purebilibili.data.repository.BangumiRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import com.android.purebilibili.core.store.TokenManager

internal const val FOLLOW_PRELOAD_PAGE_SIZE = 30
internal const val FOLLOW_PRELOAD_MAX_PAGES = 30

internal data class FollowPreloadResult(
    val total: Int,
    val requestSucceeded: Boolean,
    val targetFollowStatus: Int? = null
)

// 两个页面的后台校验共享限流，避免同时发起分页扫描。
private val followVerificationPermit = Semaphore(1)

internal data class BangumiFollowSession(val mid: Long?, val session: String?)

internal fun currentBangumiFollowSession() = BangumiFollowSession(
    TokenManager.midCache, TokenManager.sessDataCache
)

internal const val BANGUMI_FOLLOW_STATUS_UNFOLLOW = -1
internal const val BANGUMI_FOLLOW_STATUS_WANT = 1
internal const val BANGUMI_FOLLOW_STATUS_WATCHING = 2
internal const val BANGUMI_FOLLOW_STATUS_WATCHED = 3

internal data class BangumiFollowStatusOption(
    val status: Int,
    val label: String
)

internal data class BangumiResolvedFollowStatus(
    val isFollowing: Boolean,
    val followStatus: Int
)

internal val BANGUMI_FOLLOW_STATUS_OPTIONS = listOf(
    BangumiFollowStatusOption(BANGUMI_FOLLOW_STATUS_WANT, "想看"),
    BangumiFollowStatusOption(BANGUMI_FOLLOW_STATUS_WATCHING, "在看"),
    BangumiFollowStatusOption(BANGUMI_FOLLOW_STATUS_WATCHED, "看过")
)

internal fun isBangumiFollowed(userStatus: UserStatus?): Boolean {
    if (userStatus == null) return false
    return userStatus.follow == 1 || userStatus.followStatus > 0
}

internal fun resolveBangumiFollowStatusLabel(
    userStatus: UserStatus?,
    defaultFollowLabel: String = "追番"
): String {
    if (!isBangumiFollowed(userStatus)) return defaultFollowLabel
    return when (userStatus?.followStatus) {
        BANGUMI_FOLLOW_STATUS_WANT -> "想看"
        BANGUMI_FOLLOW_STATUS_WATCHING -> "在看"
        BANGUMI_FOLLOW_STATUS_WATCHED -> "看过"
        else -> "已追"
    }
}

internal fun resolveBangumiMergedFollowStatus(
    seasonId: Long,
    apiUserStatus: UserStatus?,
    cachedFollow: Map<Long, Boolean>,
    cachedStatus: Map<Long, Int>,
    followedSeasonIds: Set<Long>
): BangumiResolvedFollowStatus {
    cachedStatus[seasonId]?.let { status ->
        return BangumiResolvedFollowStatus(
            isFollowing = status > 0,
            followStatus = status.coerceAtLeast(0)
        )
    }
    cachedFollow[seasonId]?.let { isFollowing ->
        return BangumiResolvedFollowStatus(
            isFollowing = isFollowing,
            followStatus = if (isFollowing) {
                maxOf(apiUserStatus?.followStatus ?: 0, BANGUMI_FOLLOW_STATUS_WANT)
            } else {
                0
            }
        )
    }
    if (followedSeasonIds.contains(seasonId)) {
        return BangumiResolvedFollowStatus(
            isFollowing = true,
            followStatus = maxOf(apiUserStatus?.followStatus ?: 0, BANGUMI_FOLLOW_STATUS_WANT)
        )
    }
    val apiFollowing = isBangumiFollowed(apiUserStatus)
    return BangumiResolvedFollowStatus(
        isFollowing = apiFollowing,
        followStatus = if (apiFollowing) {
            maxOf(apiUserStatus?.followStatus ?: 0, BANGUMI_FOLLOW_STATUS_WANT)
        } else {
            0
        }
    )
}

internal fun resolveFollowPreloadPageCount(
    total: Int,
    pageSize: Int,
    maxPages: Int
): Int {
    if (total <= 0 || pageSize <= 0 || maxPages <= 0) return 0
    val pages = (total + pageSize - 1) / pageSize
    return pages.coerceAtMost(maxPages)
}

internal suspend fun preloadFollowedSeasonsForType(
    type: Int,
    followedSeasonIds: MutableSet<Long>,
    pageSize: Int = FOLLOW_PRELOAD_PAGE_SIZE,
    maxPages: Int = FOLLOW_PRELOAD_MAX_PAGES,
    targetSeasonId: Long? = null,
    sessionProvider: () -> BangumiFollowSession = ::currentBangumiFollowSession,
    fetchPage: suspend (type: Int, page: Int, pageSize: Int) -> Result<MyFollowBangumiData> =
        { requestType, page, ps ->
            BangumiRepository.getMyFollowBangumi(type = requestType, page = page, pageSize = ps)
        }
): FollowPreloadResult {
    if (pageSize <= 0 || maxPages <= 0) {
        return FollowPreloadResult(total = 0, requestSucceeded = false)
    }

    var total = 0
    var requestSucceeded = false

    var effectivePageSize = pageSize
    var targetFollowStatus: Int? = null
    val session = sessionProvider()
    val collectedIds = mutableSetOf<Long>()

    for (page in 1..maxPages) {
        currentCoroutineContext().ensureActive()
        if (targetSeasonId != null && page > 1) delay(120L)
        if (session != sessionProvider()) {
            return FollowPreloadResult(total = 0, requestSucceeded = false)
        }
        val pageResult = if (targetSeasonId != null) {
            followVerificationPermit.withPermit {
                if (session != sessionProvider()) {
                    return FollowPreloadResult(total = 0, requestSucceeded = false)
                }
                fetchPage(type, page, pageSize)
            }
        } else fetchPage(type, page, pageSize)
        pageResult.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        currentCoroutineContext().ensureActive()
        if (session != sessionProvider()) {
            return FollowPreloadResult(total = 0, requestSucceeded = false)
        }
        val data = pageResult.getOrNull() ?: break

        requestSucceeded = true
        if (page == 1) {
            total = data.total?.coerceAtLeast(0) ?: 0
            if (data.ps > 0) {
                effectivePageSize = data.ps
            }
        }

        val list = data.list.orEmpty()
        if (list.isNotEmpty()) {
            collectedIds.addAll(list.map { it.seasonId })
        }

        // 详情后台校验只寻找当前番剧；未命中不代表未追番。
        val target = list.firstOrNull { it.seasonId == targetSeasonId }
        if (target != null) {
            targetFollowStatus = target.followStatus.coerceAtLeast(BANGUMI_FOLLOW_STATUS_WANT)
            break
        }

        val responsePageSize = if (data.ps > 0) data.ps else effectivePageSize
        val reachedEndByCount = total > 0 && page * responsePageSize >= total
        val reachedEndByPageSize = list.size < responsePageSize
        if (list.isEmpty() || reachedEndByCount || reachedEndByPageSize) {
            break
        }
    }

    currentCoroutineContext().ensureActive()
    if (session != sessionProvider()) {
        return FollowPreloadResult(total = 0, requestSucceeded = false)
    }
    followedSeasonIds.addAll(collectedIds)
    return FollowPreloadResult(
        total = total,
        requestSucceeded = requestSucceeded,
        targetFollowStatus = targetFollowStatus
    )
}
