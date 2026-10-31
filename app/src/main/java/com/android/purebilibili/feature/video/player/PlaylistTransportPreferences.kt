package com.android.purebilibili.feature.video.player

internal data class PlaylistTransportSelection(val revision: Long, val transport: RestoredPlayTransport)

/** 保存值与运行值分离：队列进度落盘不能把临时循环/随机模式变成默认值。 */
internal class PlaylistTransportPreferences {
    private var nextRevision = 0L
    private var savedRevision = 0L
    var saved = RestoredPlayTransport(PlayMode.SEQUENTIAL, false)
        private set

    fun request(transport: RestoredPlayTransport): PlaylistTransportSelection =
        PlaylistTransportSelection(++nextRevision, transport)

    fun commit(request: PlaylistTransportSelection, remember: Boolean): Boolean {
        if (!remember || request.revision <= savedRevision) return false
        savedRevision = request.revision
        saved = request.transport
        return true
    }

    fun restore(transport: RestoredPlayTransport) {
        savedRevision = ++nextRevision
        saved = transport
    }
}
