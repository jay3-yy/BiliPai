package com.android.purebilibili.core.ui.adaptive

import android.app.Activity
import android.content.ClipDescription
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.view.DragEvent
import com.android.purebilibili.core.util.BilibiliNavigationTargetParser
import com.android.purebilibili.core.util.BilibiliUrlParser
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

private val SUPPORTED_SUBTITLE_EXTENSIONS = setOf("srt", "vtt", "ass", "ssa", "json", "bcc")

internal fun windowDropToken(activity: Activity): Int = System.identityHashCode(activity)

internal tailrec fun Context.findHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findHostActivity()
    else -> null
}

internal data class DroppedExternalSubtitle(
    val windowToken: Int,
    val uri: Uri,
    val displayName: String,
    val releasePermissions: () -> Unit = {},
)

/**
 * 按宿主窗口身份隔离的外部拖拽字幕单消费者路由表：
 * - 仅投递给被拖入的同一 Activity 窗口内活跃的播放器字幕工具，避免多实例双开时重复导入；
 * - 无活跃订阅者时直接返回 false 且不申请/泄漏 DragAndDropPermissions；
 * - 权限释放句柄单归属且幂等，避免跨订阅者提前撤销正在读取的 URI 临时权限。
 */
internal object ExternalSubtitleDropBridge {
    private val windowHandlers = ConcurrentHashMap<Int, (DroppedExternalSubtitle) -> Boolean>()

    fun registerHandler(
        windowToken: Int,
        handler: (DroppedExternalSubtitle) -> Boolean,
    ): () -> Unit {
        windowHandlers[windowToken] = handler
        return { windowHandlers.remove(windowToken, handler) }
    }

    fun hasHandler(windowToken: Int): Boolean = windowHandlers.containsKey(windowToken)

    fun dispatchToWindow(windowToken: Int, dropped: DroppedExternalSubtitle): Boolean {
        val handler = windowHandlers[windowToken] ?: return false
        return handler(dropped)
    }
}

internal fun isSupportedSubtitleDropFileName(
    fileName: String?,
    mimeType: String? = null,
): Boolean {
    val ext = fileName
        ?.substringAfterLast('.', missingDelimiterValue = "")
        ?.trim()
        ?.lowercase()
        .orEmpty()
    if (ext in SUPPORTED_SUBTITLE_EXTENSIONS) return true
    val normalizedMime = mimeType?.trim()?.lowercase().orEmpty()
    return normalizedMime in setOf(
        "application/x-subrip",
        "text/vtt",
        "text/x-ssa",
        "text/x-ass",
        "application/json",
    )
}

internal fun extractDroppableBilibiliLink(rawText: String?): String? {
    val cleaned = rawText?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (BilibiliNavigationTargetParser.parse(cleaned) != null) return cleaned
    val parsed = BilibiliUrlParser.parse(cleaned)
    return if (parsed.isValid) cleaned else null
}

/**
 * 为宿主窗口挂载跨应用系统拖拽接收器：
 * - 拖入 B 站视频/番剧/直播/动态/UP 链接文本 → 回调 [onBilibiliTextDropped] 秒开对应页面；
 * - 拖入本地 .srt/.vtt/.ass/.json 字幕文件 → 申请临时读权限后交由 [ExternalSubtitleDropBridge] 挂载。
 */
internal fun installExternalDropTarget(
    activity: Activity,
    onBilibiliTextDropped: (String) -> Unit,
) {
    val decorView = activity.window?.decorView ?: return
    decorView.setOnDragListener { _, event ->
        if (event.localState === INTERNAL_EXTERNAL_DRAG_LOCAL_STATE) {
            return@setOnDragListener false
        }
        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> {
                val desc = event.clipDescription ?: return@setOnDragListener false
                desc.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) ||
                    desc.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML) ||
                    desc.hasMimeType("text/*") ||
                    desc.hasMimeType("application/*")
            }
            DragEvent.ACTION_DROP -> handleExternalDropEvent(activity, event, onBilibiliTextDropped)
            DragEvent.ACTION_DRAG_ENDED -> true
            else -> true
        }
    }
}

private fun handleExternalDropEvent(
    activity: Activity,
    event: DragEvent,
    onBilibiliTextDropped: (String) -> Unit,
): Boolean {
    val clipData = event.clipData ?: return false
    if (clipData.itemCount <= 0) return false
    val item = clipData.getItemAt(0)

    // 1. 优先识别内存文本或 http(s)/bilibili URI，避免在主线程调用 coerceToText 触发 content:// 文件读取 I/O。
    val directText = item.text?.toString()
        ?: item.uri?.toString()?.takeIf {
            it.startsWith("http://", ignoreCase = true) ||
                it.startsWith("https://", ignoreCase = true) ||
                it.startsWith("bilibili://", ignoreCase = true) ||
                it.startsWith("bili://", ignoreCase = true)
        }
    val validBiliLink = extractDroppableBilibiliLink(directText)
    if (validBiliLink != null) {
        onBilibiliTextDropped(validBiliLink)
        return true
    }

    // 2. 识别从文件管理器拖入的本地字幕文件：仅当当前窗口存在活跃播放器字幕接收器时才申请临时授权
    val uri = item.uri ?: return false
    val mimeType = activity.contentResolver.getType(uri)
    val displayName = resolveDroppedDisplayName(activity, uri)
    if (!isSupportedSubtitleDropFileName(displayName, mimeType)) return false

    val windowToken = windowDropToken(activity)
    if (!ExternalSubtitleDropBridge.hasHandler(windowToken)) return false

    val dropPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        activity.requestDragAndDropPermissions(event)
    } else {
        null
    }
    val released = AtomicBoolean(false)
    val releaseOnce = {
        if (released.compareAndSet(false, true)) {
            runCatching { dropPermission?.release() }
        }
        Unit
    }
    val accepted = ExternalSubtitleDropBridge.dispatchToWindow(
        windowToken = windowToken,
        dropped = DroppedExternalSubtitle(
            windowToken = windowToken,
            uri = uri,
            displayName = displayName ?: uri.lastPathSegment ?: "拖入字幕",
            releasePermissions = releaseOnce,
        ),
    )
    if (!accepted) {
        releaseOnce()
    }
    return accepted
}

private fun resolveDroppedDisplayName(activity: Activity, uri: Uri): String? = runCatching {
    activity.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}.getOrNull() ?: uri.lastPathSegment
