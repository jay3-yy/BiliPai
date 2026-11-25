// 文件路径: domain/usecase/UserInteractionUseCase.kt
package com.android.purebilibili.domain.usecase

import com.android.purebilibili.data.repository.ActionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * 三连操作结果
 */
data class TripleActionResult(
    val likeSuccess: Boolean,
    val coinSuccess: Boolean,
    val coinMessage: String?,
    val favoriteSuccess: Boolean
)

/**
 * 用户交互 UseCase
 * 
 * 职责：
 * 1. 处理点赞/取消点赞
 * 2. 处理投币
 * 3. 处理收藏/取消收藏
 * 4. 处理关注/取关
 * 5. 处理一键三连
 * 
 * 将用户交互逻辑从 VideoPlaybackViewModel 中抽离
 */
class UserInteractionUseCase {
    
    /**
     * 点赞/取消点赞视频
     * 
     * @param aid 视频 AID
     * @param isLiked 当前是否已点赞
     * @return 操作后的点赞状态
     */
    suspend fun toggleLike(aid: Long, isLiked: Boolean): Result<Boolean> {
        return ActionRepository.likeVideo(aid, !isLiked)
    }
    
    /**
     * 投币
     * 
     * @param aid 视频 AID
     * @param count 投币数量 (1 或 2)
     * @param alsoLike 是否同时点赞
     * @return 投币是否成功
     */
    suspend fun doCoin(aid: Long, count: Int, alsoLike: Boolean): Result<Boolean> {
        return ActionRepository.coinVideo(aid, count, alsoLike)
    }
    
    /**
     * 收藏/取消收藏视频
     * 
     * @param aid 视频 AID
     * @param isFavorited 当前是否已收藏
     * @return 操作后的收藏状态
     */
    suspend fun toggleFavorite(aid: Long, isFavorited: Boolean): Result<Boolean> {
        return ActionRepository.favoriteVideo(aid, !isFavorited)
    }
    
    /**
     * 关注/取关 UP 主
     * 
     * @param mid UP 主 MID
     * @param isFollowing 当前是否已关注
     * @return 操作后的关注状态
     */
    suspend fun toggleFollow(mid: Long, isFollowing: Boolean): Result<Boolean> {
        return ActionRepository.followUser(mid, !isFollowing)
    }
    
    /**
     * 一键三连
     * 
     * @param aid 视频 AID
     * @return 三连结果
     */
    suspend fun doTripleAction(aid: Long): Result<TripleActionResult> {
        return ActionRepository.tripleAction(aid).map { result ->
            TripleActionResult(
                likeSuccess = result.likeSuccess,
                coinSuccess = result.coinSuccess,
                coinMessage = result.coinMessage,
                favoriteSuccess = result.favoriteSuccess
            )
        }
    }
    
    /**
     * 检查用户交互状态（点赞、投币、收藏、关注）
     * 
     * @param aid 视频 AID
     * @param upMid UP 主 MID
     * @return 用户交互状态
     */
    suspend fun checkInteractionStatus(
        aid: Long,
        upMid: Long
    ): InteractionStatus = coroutineScope {
        val likedDeferred = async {
            try {
                ActionRepository.checkLikeStatus(aid)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
        }
        val coinDeferred = async {
            try {
                ActionRepository.checkCoinStatus(aid)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                0
            }
        }
        val favoritedDeferred = async {
            try {
                ActionRepository.checkFavoriteStatus(aid)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
        }
        val followingDeferred = async {
            try {
                ActionRepository.checkFollowStatus(upMid)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
        }

        InteractionStatus(
            isLiked = likedDeferred.await(),
            coinCount = coinDeferred.await(),
            isFavorited = favoritedDeferred.await(),
            isFollowing = followingDeferred.await(),
        )
    }
}

/**
 * 用户交互状态
 */
data class InteractionStatus(
    val isLiked: Boolean,
    val coinCount: Int,
    val isFavorited: Boolean,
    val isFollowing: Boolean
)
