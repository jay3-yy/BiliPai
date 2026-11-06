package com.android.purebilibili.navigation3

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.android.purebilibili.core.lifecycle.FairMemoryNavigationRegistry
import com.android.purebilibili.core.lifecycle.FairMemoryNavigationState
import com.android.purebilibili.core.lifecycle.FairMemoryRecoverySnapshot
import com.android.purebilibili.core.lifecycle.FairMemoryRecoveryStore
import com.android.purebilibili.core.util.Logger
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.lifecycle.canRestoreFairMemorySnapshot
import com.android.purebilibili.core.lifecycle.forActivityState
import com.android.purebilibili.feature.home.HomeMemoryRecoveryRuntime
import java.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString

internal data class FairMemoryRecoveryLoad(val snapshot: FairMemoryRecoverySnapshot?)

/** null 为加载中；旋转不重复读盘，新进程必须先读取最新的终止快照。 */
@Composable
internal fun rememberFairMemoryNavigationRecovery(
    context: Context,
    explicitEntry: Boolean,
    expectedVideoBvid: String?,
): FairMemoryRecoveryLoad? {
    var raw by rememberSaveable { mutableStateOf<String?>(null) }
    var loadedSessionId by rememberSaveable { mutableStateOf<String?>(null) }
    val sessionId = FairMemoryRecoveryStore.processSessionId
    LaunchedEffect(context, explicitEntry, expectedVideoBvid) {
        if (loadedSessionId == sessionId) return@LaunchedEffect
        raw = try {
            val latest = FairMemoryRecoveryStore.consume(context, explicitEntry, expectedVideoBvid)
            if (latest != null) HomeMemoryRecoveryRuntime.install(latest.accountMid, latest.navigation.homeFeed)
            val prior = raw?.takeIf { it.isNotEmpty() }?.let {
                try {
                    FairMemoryRecoveryStore.json.decodeFromString(FairMemoryRecoverySnapshot.serializer(), it)
                } catch (error: SerializationException) {
                    Logger.w("FairMemory", "Saved recovery parameters are invalid", error)
                    null
                }
            }
            // 普通系统重建没有新 KILL 快照时，保留原来的保存键，让系统导航状态正常恢复。
            val selected = latest ?: prior?.takeIf {
                canRestoreFairMemorySnapshot(
                    it, sessionId, TokenManager.midCache ?: 0L, explicitEntry, expectedVideoBvid,
                    NetworkModule.playbackAccount()?.mid ?: TokenManager.midCache ?: 0L,
                )
            }
            selected?.let {
                FairMemoryRecoveryStore.json.encodeToString(it.forActivityState())
            }.orEmpty()
        } catch (error: IOException) {
            Logger.w("FairMemory", "Recovery snapshot could not be read", error)
            ""
        }
        loadedSessionId = sessionId
    }
    if (loadedSessionId != sessionId) return null
    val loaded = raw ?: return null
    return remember(loaded) {
        val snapshot = if (loaded.isEmpty()) null else try {
            FairMemoryRecoveryStore.json.decodeFromString(FairMemoryRecoverySnapshot.serializer(), loaded)
        } catch (error: SerializationException) {
            Logger.w("FairMemory", "Saved recovery parameters are invalid", error)
            null
        }
        FairMemoryRecoveryLoad(snapshot)
    }
}

@Composable
internal fun RegisterFairMemoryNavigationState(
    activity: ComponentActivity,
    backStack: List<BiliPaiNavKey>,
    bottomRoute: String,
    capture: () -> FairMemoryNavigationState,
) {
    val latestCapture by rememberUpdatedState(capture)
    DisposableEffect(activity) {
        val unregister = FairMemoryNavigationRegistry.register(activity) { latestCapture() }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) FairMemoryNavigationRegistry.markActive(activity)
        }
        activity.lifecycle.addObserver(observer)
        if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            FairMemoryNavigationRegistry.markActive(activity)
        }
        onDispose {
            activity.lifecycle.removeObserver(observer)
            unregister()
        }
    }
    LaunchedEffect(backStack.toList(), bottomRoute) {
        FairMemoryRecoveryStore.invalidateCurrentProcessCheckpoint(activity)
    }
}
