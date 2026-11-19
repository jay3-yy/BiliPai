package com.android.purebilibili.feature.video.ui.overlay

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.android.purebilibili.data.repository.SponsorBlockRepository
import com.android.purebilibili.feature.plugin.SponsorBlockConfig
import com.android.purebilibili.feature.plugin.SponsorBlockSegmentBehavior
import com.android.purebilibili.feature.plugin.loadSponsorBlockConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Keep this session above chrome/dialog visibility branches so dispatched requests survive closing. */
data class PiliSponsorMenuSession(
    val configReady: Boolean,
    val behaviorForCategory: (String) -> SponsorBlockSegmentBehavior,
    val categoryColors: Map<String, String>,
    val onVote: (segmentId: String, voteType: Int) -> Unit,
    val onChangeCategory: (segmentId: String, category: String) -> Unit,
)

@Composable
fun rememberPiliSponsorMenuSession(
    bvid: String,
    visible: Boolean,
    onConfigFailure: () -> Unit,
): PiliSponsorMenuSession {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var config by remember(context) { mutableStateOf(SponsorBlockConfig()) }
    var configReady by remember(context, bvid) { mutableStateOf(false) }
    val latestOnConfigFailure by rememberUpdatedState(onConfigFailure)

    LaunchedEffect(context, bvid, visible) {
        if (!visible) {
            configReady = false
            return@LaunchedEffect
        }
        try {
            config = loadSponsorBlockConfig(context, config)
            configReady = true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Toast.makeText(context, "读取空降助手设置失败: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
            latestOnConfigFailure()
        }
    }

    return remember(context, scope, bvid, config, configReady) {
        fun submit(segmentId: String, voteType: Int? = null, category: String? = null) {
            scope.launch {
                val success = if (category == null) "投票成功" else "类别更改成功"
                val failure = if (category == null) "投票失败" else "类别更改失败"
                try {
                    // Read again at submission: changing server/user identity in settings must
                    // never send a vote to the previous server with a stale identity.
                    val current = loadSponsorBlockConfig(context, config)
                    config = current
                    SponsorBlockRepository.voteOnSegment(
                        baseUrl = current.serverBaseUrl,
                        userId = current.userId,
                        segmentId = segmentId,
                        voteType = voteType,
                        category = category,
                    ).getOrThrow()
                    Toast.makeText(context, success, Toast.LENGTH_SHORT).show()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Toast.makeText(context, "$failure: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
        PiliSponsorMenuSession(
            configReady = configReady,
            behaviorForCategory = config::behaviorFor,
            categoryColors = config.categoryColorHex,
            onVote = { segmentId, voteType -> submit(segmentId, voteType = voteType) },
            onChangeCategory = { segmentId, category -> submit(segmentId, category = category) },
        )
    }
}
