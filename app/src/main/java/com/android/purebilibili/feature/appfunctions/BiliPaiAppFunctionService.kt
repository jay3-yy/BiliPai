@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.android.purebilibili.feature.appfunctions

import android.app.appfunctions.AppFunctionException
import android.app.appfunctions.AppFunctionService
import android.app.appfunctions.ExecuteAppFunctionRequest
import android.app.appfunctions.ExecuteAppFunctionResponse
import android.app.appsearch.GenericDocument
import android.content.ComponentName
import android.content.Intent
import android.content.pm.SigningInfo
import android.os.Build
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import androidx.annotation.RequiresApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.android.purebilibili.EXTRA_PENDING_NAVIGATION_ROUTE
import com.android.purebilibili.MainActivity
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.data.repository.BangumiRepository
import com.android.purebilibili.data.repository.WatchLaterRepository
import com.android.purebilibili.feature.audio.player.AudioMediaLibrary
import com.android.purebilibili.feature.audio.player.BackgroundAudioService
import com.android.purebilibili.feature.audio.player.SystemMediaPreferences
import com.android.purebilibili.feature.audio.player.awaitBackgroundAudio
import com.android.purebilibili.navigation.ScreenRoutes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Android 16/17 原生 AppFunctions 系统级 AI 能力服务。
 * 允许系统 AI 助手按结构化契约调度搜索、稍后再看、听视频续播与今日追番查询，
 * 并严格遵循 BiliPai 的隐私锁边界与取消信号。
 */
@RequiresApi(Build.VERSION_CODES.BAKLAVA)
class BiliPaiAppFunctionService : AppFunctionService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onExecuteFunction(
        request: ExecuteAppFunctionRequest,
        callingPackage: String,
        callingPackageSigningInfo: SigningInfo,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<ExecuteAppFunctionResponse, AppFunctionException>,
    ) {
        val completed = AtomicBoolean(false)
        fun completeResult(doc: GenericDocument) {
            if (completed.compareAndSet(false, true)) {
                callback.onResult(ExecuteAppFunctionResponse(doc))
            }
        }
        fun completeError(code: Int, message: String) {
            if (completed.compareAndSet(false, true)) {
                callback.onError(AppFunctionException(code, message))
            }
        }

        val queryParam = request.parameters.getPropertyString("query")
        val parsed = parseBiliPaiAppFunctionRequest(
            functionId = request.functionIdentifier,
            queryParam = queryParam,
        )
        val action = when (parsed) {
            is AppFunctionParseResult.Valid -> parsed.action
            is AppFunctionParseResult.UnknownFunction -> {
                completeError(AppFunctionException.ERROR_FUNCTION_NOT_FOUND, "不支持的功能：${parsed.functionId}")
                return
            }
            is AppFunctionParseResult.InvalidArgument -> {
                completeError(AppFunctionException.ERROR_INVALID_ARGUMENT, parsed.reason)
                return
            }
        }

        val job = serviceScope.launch {
            try {
                val doc = withTimeout(10_000L) {
                    executeAction(request.functionIdentifier, action)
                }
                completeResult(doc)
            } catch (cancelled: CancellationException) {
                completeError(AppFunctionException.ERROR_CANCELLED, "操作已取消")
                throw cancelled
            } catch (denied: SecurityException) {
                completeError(AppFunctionException.ERROR_DENIED, denied.message ?: "隐私保护已开启")
            } catch (error: Exception) {
                completeError(
                    AppFunctionException.ERROR_APP_UNKNOWN_ERROR,
                    error.message ?: "执行 BiliPai 能力失败",
                )
            }
        }

        cancellationSignal.setOnCancelListener {
            job.cancel()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun executeAction(
        functionId: String,
        action: BiliPaiAppFunctionAction,
    ): GenericDocument {
        val appContext = applicationContext
        return when (action) {
            is BiliPaiAppFunctionAction.SearchContent -> {
                launchRoute(ScreenRoutes.Search.createRoute(action.query))
                buildResultDocument(
                    functionId = functionId,
                    summary = "已在 BiliPai 打开搜索：${action.query}",
                )
            }
            BiliPaiAppFunctionAction.OpenWatchLater -> {
                if (SystemMediaPreferences.libraryProtected(appContext)) {
                    throw SecurityException("已开启隐私验证，请在应用内查看稍后再看")
                }
                launchRoute(ScreenRoutes.WatchLater.route)
                val previewItems = if (!TokenManager.sessDataCache.isNullOrEmpty()) {
                    WatchLaterRepository.getPage(1, 0, "", false)
                        .getOrNull()
                        ?.items
                        ?.take(5)
                        ?.map { it.title }
                        .orEmpty()
                } else {
                    emptyList()
                }
                buildResultDocument(
                    functionId = functionId,
                    summary = if (previewItems.isNotEmpty()) {
                        "已打开稍后再看，包含 ${previewItems.size} 条最新待看内容"
                    } else {
                        "已打开稍后再看"
                    },
                    items = previewItems,
                )
            }
            BiliPaiAppFunctionAction.ResumeBackgroundAudio -> {
                if (SystemMediaPreferences.libraryProtected(appContext)) {
                    throw SecurityException("已开启隐私验证，暂停外部唤起听视频续播")
                }
                val saved = AudioMediaLibrary(appContext).saved()?.current
                    ?: error("暂无可续播的听视频队列")
                val controllerFuture = MediaController.Builder(
                    appContext,
                    SessionToken(appContext, ComponentName(appContext, BackgroundAudioService::class.java)),
                ).buildAsync()
                var controller: MediaController? = null
                try {
                    controller = controllerFuture.awaitBackgroundAudio()
                    controller.play()
                } finally {
                    controller?.release()
                    MediaController.releaseFuture(controllerFuture)
                }
                buildResultDocument(
                    functionId = functionId,
                    summary = "已开始后台听视频续播：${saved.title}",
                    items = listOf(saved.title),
                )
            }
            BiliPaiAppFunctionAction.QueryTodayBangumi -> {
                val days = BangumiRepository.getTimeline(type = 1, before = 0, after = 1).getOrThrow()
                val items = formatTodayBangumiSummaryItems(days, maxItems = 8)
                buildResultDocument(
                    functionId = functionId,
                    summary = if (items.isEmpty()) "今日暂无番剧更新" else "今日共有 ${items.size} 部番剧更新",
                    items = items,
                )
            }
        }
    }

    private fun launchRoute(route: String) {
        val intent = Intent(applicationContext, MainActivity::class.java)
            .putExtra(EXTRA_PENDING_NAVIGATION_ROUTE, route)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        applicationContext.startActivity(intent)
    }

    private class ResultDocumentBuilder(
        namespace: String,
        id: String,
        schemaType: String,
    ) : GenericDocument.Builder<ResultDocumentBuilder>(namespace, id, schemaType)

    private fun buildResultDocument(
        functionId: String,
        summary: String,
        items: List<String> = emptyList(),
    ): GenericDocument {
        return ResultDocumentBuilder("bilipai", functionId, "BiliPaiFunctionResult")
            .setPropertyString("status", "ok")
            .setPropertyString("summary", summary)
            .apply {
                if (items.isNotEmpty()) {
                    setPropertyString("items", *items.toTypedArray())
                }
            }
            .build()
    }
}
