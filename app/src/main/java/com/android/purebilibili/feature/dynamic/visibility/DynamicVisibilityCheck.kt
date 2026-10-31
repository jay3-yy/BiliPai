package com.android.purebilibili.feature.dynamic.visibility

import android.content.Context
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.store.AccountSessionIdentity
import com.android.purebilibili.core.store.ContentDisplayPreferences
import com.android.purebilibili.core.store.TokenManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

internal enum class DynamicVisibilityStatus(val message: String) {
    VISIBLE("未登录时也能看到这条动态。"),
    UNAVAILABLE("未登录时暂时看不到这条动态，可能还在审核，也可能有可见范围限制。可以稍后再检查。"),
    UNKNOWN("这次没能完成检查，请稍后再试。检查失败不代表动态不可见。"),
}

internal fun resolveDynamicVisibilityStatus(code: Int, hasItem: Boolean): DynamicVisibilityStatus = when {
    code == 0 && hasItem -> DynamicVisibilityStatus.VISIBLE
    code == -404 -> DynamicVisibilityStatus.UNAVAILABLE
    else -> DynamicVisibilityStatus.UNKNOWN
}

internal data class DynamicVisibilityNotice(
    val identity: AccountSessionIdentity,
    val dynamicId: String,
    val status: DynamicVisibilityStatus,
)

/** 仅保存已完成的检查结果；异步任务由各发布入口的账号作用域拥有。 */
internal object DynamicVisibilityFeedback {
    private val mutableNotices = MutableStateFlow<List<DynamicVisibilityNotice>>(emptyList())
    val notices = mutableNotices.asStateFlow()

    fun offer(notice: DynamicVisibilityNotice) {
        if (TokenManager.accountIdentity.value != notice.identity) return
        mutableNotices.update { previous ->
            (previous.filter { it.identity == notice.identity && it.dynamicId != notice.dynamicId } + notice).takeLast(8)
        }
    }
    fun dismiss(notice: DynamicVisibilityNotice) { mutableNotices.update { it - notice } }
    fun retainAccount(identity: AccountSessionIdentity) { mutableNotices.update { list -> list.filter { it.identity == identity } } }
}

internal object DynamicVisibilityCheck {
    suspend fun check(dynamicId: String): DynamicVisibilityStatus {
        if (dynamicId.isBlank()) return DynamicVisibilityStatus.UNKNOWN
        return try {
            val response = NetworkModule.guestDynamicApi.getDynamicDetail(id = dynamicId)
            currentCoroutineContext().ensureActive()
            resolveDynamicVisibilityStatus(response.code, response.data?.item?.id_str == dynamicId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            DynamicVisibilityStatus.UNKNOWN
        }
    }

    suspend fun afterPublication(
        context: Context,
        dynamicId: String,
        identity: AccountSessionIdentity,
        privatePublication: Boolean = false,
        scheduledPublication: Boolean = false,
    ) {
        var requested = false
        try {
            if (!shouldCheckPublishedDynamic(dynamicId, privatePublication, scheduledPublication) ||
                TokenManager.accountIdentity.value != identity ||
                !ContentDisplayPreferences.dynamicVisibilityCheck(context).first()) return
            requested = true
            delay(5_000L)
            if (TokenManager.accountIdentity.value != identity ||
                !ContentDisplayPreferences.dynamicVisibilityCheck(context).first()) return
            val status = check(dynamicId)
            currentCoroutineContext().ensureActive()
            if (TokenManager.accountIdentity.value == identity && ContentDisplayPreferences.dynamicVisibilityCheck(context).first()) {
                DynamicVisibilityFeedback.offer(DynamicVisibilityNotice(identity, dynamicId, status))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // 检查设置或查询失败不改变已经成功的发布结果。
            if (requested && TokenManager.accountIdentity.value == identity) {
                DynamicVisibilityFeedback.offer(DynamicVisibilityNotice(identity, dynamicId, DynamicVisibilityStatus.UNKNOWN))
            }
        }
    }
}

internal fun shouldCheckPublishedDynamic(id: String, privatePublication: Boolean, scheduledPublication: Boolean): Boolean =
    id.isNotBlank() && !privatePublication && !scheduledPublication
