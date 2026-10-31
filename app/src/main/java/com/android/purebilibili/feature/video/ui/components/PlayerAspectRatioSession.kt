package com.android.purebilibili.feature.video.ui.components

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.CancellationException
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.android.purebilibili.core.store.SettingsManager
import java.util.WeakHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 播放器与内容共同确定临时比例；分 P 和小窗接力复用，切换内容不串用。 */
internal class PlayerAspectRatioSession {
    private val mutableSelection = MutableStateFlow<VideoAspectRatio?>(null)
    val selection = mutableSelection.asStateFlow()
    fun select(ratio: VideoAspectRatio) { mutableSelection.value = ratio }

    suspend fun saveIfRemembered(context: Context, ratio: VideoAspectRatio) {
        try {
            SettingsManager.savePlayerFullscreenAspectRatio(context, ratio.toFullscreenAspectRatio())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Toast.makeText(context, "比例已临时应用，默认值保存失败，请稍后重试", Toast.LENGTH_SHORT).show()
        }
    }
}

internal object PlayerAspectRatioSessions {
    private val owners = WeakHashMap<Any, LinkedHashMap<String, PlayerAspectRatioSession>>()

    @Synchronized
    fun forContent(owner: Any, contentKey: String): PlayerAspectRatioSession {
        val entries = owners.getOrPut(owner) { LinkedHashMap() }
        return entries.getOrPut(contentKey) { PlayerAspectRatioSession() }.also {
            while (entries.size > 16) entries.remove(entries.keys.first())
        }
    }
}

@Composable
internal fun rememberPlayerAspectRatioSession(owner: Any?, contentKey: String): PlayerAspectRatioSession =
    remember(owner, contentKey) {
        if (owner == null) PlayerAspectRatioSession() else PlayerAspectRatioSessions.forContent(owner, contentKey)
    }
