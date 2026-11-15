package com.android.purebilibili.feature.home.components.cards

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.android.purebilibili.core.util.AppImageLoadPriority
import kotlinx.coroutines.CancellationException

/**
 * 取色预热在拿到"前台静默"之后额外等待的时长。
 *
 * 90ms 的取值理由：预取成功的那一刻恰好是前台刚跑完一次可见封面的时刻，`backgroundLocalWork`
 * 的 `settleMillis = 0` 只要求"瞬时空闲"，而此时往往还有下一张可见封面在飞。稍等一小段既能让
 * 真正的前台请求先开跑，又不至于把预热推到用户已经看到卡片之后（那就等于没预热）。
 */
internal const val HOME_CARD_TINT_WARM_SETTLE_MS = 90L

/**
 * 预取成功后的取色预热：**纯本地工作，不占网络许可**。
 *
 * 为什么挂在预取成功之后：预取刚把封面写进磁盘缓存，此刻做 96×96 采样命中的一定是本地磁盘条目，
 * 不需要二次网络请求。等卡片自己 `onSuccess` 之后再算，则要排在卡片自己的队列后面——那正是
 * "滑过去之后颜色延迟一段时间才出现"的直接原因。
 *
 * 失败一律吞掉：预热是纯粹的机会性优化，它失败不该影响预取流程、不该记进 ledger、更不该
 * 让整趟 pass 抛异常中断。
 *
 * @param context 任意 context，内部只取 `applicationContext`。
 * @param cacheKey 与卡片可见请求一致的磁盘缓存 key，必须逐字节相同否则命不中。
 * @param coverUrl 封面 URL，仅作为解码来源。
 * @param settleMillis 拿到前台静默后额外等待的时长，见 [HOME_CARD_TINT_WARM_SETTLE_MS]。
 * @return 命中的代表色；失败或取消之外的任何异常都返回 null。
 */
internal suspend fun warmHomeCardCoverTint(
    context: Context,
    cacheKey: String,
    coverUrl: String,
    settleMillis: Long = HOME_CARD_TINT_WARM_SETTLE_MS,
): Color? {
    if (cacheKey.isBlank() || coverUrl.isBlank()) return null
    return try {
        AppImageLoadPriority.backgroundLocalWork(settleMillis) {
            VideoCardCoverColorStore.extractColor(
                context = context,
                cacheKey = cacheKey,
                coverUrl = coverUrl,
            )
        }
    } catch (cancelled: CancellationException) {
        // 取消必须原样抛出：Compose 离开组合 / 窗口重建时整趟 pass 都要能立刻停下。
        throw cancelled
    } catch (_: Exception) {
        null
    }
}
