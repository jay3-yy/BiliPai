package com.android.purebilibili.feature.aicu

import androidx.compose.runtime.staticCompositionLocalOf
import com.android.purebilibili.data.model.response.AicuCategory
import com.android.purebilibili.data.model.response.AicuRecord
import com.android.purebilibili.navigation3.BiliPaiNavKey

/** The app shell supplies navigation; leaf comments never depend on a NavController. */
internal val LocalAicuNavigation = staticCompositionLocalOf<((Long?) -> Unit)?> { null }

internal fun aicuNativeTarget(category: AicuCategory, record: AicuRecord): BiliPaiNavKey? {
    fun String.positiveId() = toLongOrNull()?.takeIf { it > 0 }
    if (category == AicuCategory.LIVE_DANMAKU) {
        return record.roomId.positiveId()?.let {
            BiliPaiNavKey.Live(roomId = it.toString(), title = record.roomName, uname = record.upName)
        }
    }
    val oid = record.objectId.positiveId() ?: return null
    val target = if (category == AicuCategory.COMMENT) record.id.positiveId() ?: 0L else 0L
    val root = if (record.rank == 1) target else record.rootId.positiveId() ?: 0L
    return when (record.objectType) {
        1 -> BiliPaiNavKey.VideoDetail(bvid = "av$oid", commentRootRpid = root, commentTargetRpid = target)
        17 -> BiliPaiNavKey.DynamicDetail(oid.toString(), commentRootRpid = root, commentTargetRpid = target)
        12 -> BiliPaiNavKey.ArticleDetail(oid, commentRootRpid = root, commentTargetRpid = target)
        else -> null // Legacy picture/article comment IDs cannot safely be treated as dynamic IDs.
    }
}
