package com.android.purebilibili.data.repository

import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.data.model.response.FavFolder
import com.android.purebilibili.data.model.response.RelationData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object UserActionRepository {
    private data class RelationKey(val accountScope: String, val mid: Long)
    private data class UserFoldersKey(val accountScope: String, val aid: Long?)

    private val relationRequests = OnDemandRequestCache<RelationKey, Result<RelationData?>>(
        maxEntries = 64,
        maxAgeMs = 3_000L,
        canCache = { it.isSuccess },
    )
    private val folderRequests = OnDemandRequestCache<UserFoldersKey, Result<List<FavFolder>>>(
        maxEntries = 8,
        maxAgeMs = 0L,
    )

    fun invalidateRelation(mid: Long) {
        relationRequests.invalidateWhere { it.mid == mid }
    }

    fun clearRelationCache() {
        relationRequests.clearCompleted()
    }

    fun invalidateFolders(aid: Long? = null) {
        folderRequests.invalidateWhere { aid == null || it.aid == aid || it.aid == null }
    }

    private fun csrf() = TokenManager.csrfCache?.takeIf { it.isNotBlank() } ?: throw ContentRequestException(-101, "")

    suspend fun like(aid: Long, selected: Boolean): Result<Boolean> = dataRequest {
        val response = NetworkModule.api.likeVideo(aid, if (selected) 1 else 2, csrf())
        if (response.code != 0) throw ContentRequestException(response.code, response.message)
        true
    }

    suspend fun liked(aid: Long): Result<Boolean> = dataRequest {
        val response = NetworkModule.api.hasLiked(aid)
        if (response.code != 0) throw ContentRequestException(response.code, response.message)
        response.data == 1
    }

    suspend fun follow(mid: Long, selected: Boolean): Result<Boolean> = dataRequest {
        val response = NetworkModule.api.modifyRelation(mid, if (selected) 1 else 2, csrf())
        if (response.code != 0) throw ContentRequestException(response.code, response.message)
        invalidateRelation(mid)
        true
    }

    suspend fun relation(mid: Long): Result<RelationData?> = withContext(Dispatchers.IO) {
        try {
            TokenManager.awaitRestore()
            val key = RelationKey(videoMetadataAccountScope(), mid)
            val result = relationRequests.getOrLoad(key) {
                dataRequest {
                    val response = NetworkModule.api.getRelation(mid)
                    if (response.code != 0) throw ContentRequestException(response.code, response.message)
                    check(key.accountScope == videoMetadataAccountScope()) { "Account changed" }
                    response.data
                }
            }
            if (key.accountScope == videoMetadataAccountScope()) result
            else Result.failure(IllegalStateException("Account changed"))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun following(mid: Long): Result<Boolean> =
        relation(mid).map { it?.isFollowing ?: false }

    suspend fun folders(aid: Long? = null): Result<List<FavFolder>> = withContext(Dispatchers.IO) {
        try {
            TokenManager.awaitRestore()
            val key = UserFoldersKey(videoMetadataAccountScope(), aid)
            val result = folderRequests.getOrLoad(key) {
                dataRequest {
                    val mid = TokenManager.midCache ?: throw ContentRequestException(-101, "")
                    val response = NetworkModule.api.getFavFolders(mid, type = aid?.let { 2 }, rid = aid)
                    if (response.code != 0) throw ContentRequestException(response.code, response.message)
                    check(key.accountScope == videoMetadataAccountScope()) { "Account changed" }
                    response.data?.list.orEmpty()
                }
            }
            if (key.accountScope == videoMetadataAccountScope()) result
            else Result.failure(IllegalStateException("Account changed"))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun favorite(aid: Long, add: Set<Long>, remove: Set<Long>): Result<Boolean> = dataRequest {
        check(add.intersect(remove).isEmpty()) { "收藏夹操作冲突" }
        val response = NetworkModule.api.dealFavorite(rid = aid, addIds = add.sorted().joinToString(","),
            delIds = remove.sorted().joinToString(","), csrf = csrf())
        if (response.code != 0) throw ContentRequestException(response.code, response.message)
        invalidateFolders(aid)
        true
    }

    /** 投币：count 1-2，alsoLike 同时点赞；错误码文案与移动端 ActionRepository 保持一致。 */
    suspend fun coin(aid: Long, count: Int, alsoLike: Boolean): Result<Boolean> = dataRequest {
        val response = NetworkModule.api.coinVideo(
            aid = aid, multiply = count.coerceIn(1, 2), selectLike = if (alsoLike) 1 else 0, csrf = csrf(),
        )
        if (response.code != 0) {
            val message = when (response.code) {
                34004 -> "操作太频繁，请稍后重试"
                34005 -> "已投满 2 个硬币"
                -104 -> "硬币余额不足"
                else -> response.message.ifBlank { "投币失败" }
            }
            throw ContentRequestException(response.code, message)
        }
        true
    }
}
