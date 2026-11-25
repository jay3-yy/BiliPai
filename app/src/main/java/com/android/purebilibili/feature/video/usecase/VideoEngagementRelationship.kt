package com.android.purebilibili.feature.video.usecase

import com.android.purebilibili.data.model.response.VideoRelationData
import com.android.purebilibili.data.repository.ActionRepository

/** Unlike the legacy Boolean queries, failures never masquerade as an unspent/unfavorited video. */
internal suspend fun queryVideoEngagementRelationship(aid: Long, bvid: String): Result<VideoRelationData> =
    ActionRepository.queryVideoRelation(bvid = bvid, aid = aid)
