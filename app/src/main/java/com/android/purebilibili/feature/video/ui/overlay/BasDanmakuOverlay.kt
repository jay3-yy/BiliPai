package com.android.purebilibili.feature.video.ui.overlay

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.net.Uri
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import com.android.purebilibili.core.ui.adaptive.DeviceThermalBudgetState
import com.android.purebilibili.danmaku.parser.TimedDanmakuWindow
import com.android.purebilibili.danmaku.parser.bas.BasDanmaku
import com.android.purebilibili.danmaku.parser.bas.BasTarget
import com.android.purebilibili.feature.video.danmaku.DanmakuViewport
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.ceil

/** Mode 9 is a retained, native multi-object scene, not a Mode 7 text array. */
@Composable
fun BasDanmakuOverlay(
    items: List<BasDanmaku>,
    player: Player,
    viewport: DanmakuViewport,
    opacity: Float = 1f,
    fontScale: Float = 1f,
    fontWeight: Int = 5,
    modifier: Modifier = Modifier,
    renderingPaused: Boolean = false,
) {
    val context = LocalContext.current
    val view = remember(context, player) { BasOverlayView(context, player) }
    val wake = remember(player) { Channel<Unit>(Channel.CONFLATED) }
    val latestRenderingPaused by rememberUpdatedState(renderingPaused)
    DisposableEffect(player, view, wake) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (latestRenderingPaused) return
                view.frame(player.currentPosition)
                wake.trySend(Unit)
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            view.clear()
        }
    }
    LaunchedEffect(player, items, view, wake, renderingPaused) {
        if (renderingPaused) return@LaunchedEffect
        var lastRenderedFrameNs = 0L
        while (isActive) {
            val position = player.currentPosition
            view.frame(position)
            if (!player.isPlaying || items.isEmpty()) {
                lastRenderedFrameNs = 0L
                wake.receive()
            } else if (view.hasActiveScene) {
                val minIntervalNs = resolveSpecialDanmakuMinFrameIntervalNs(
                    thermalTier = DeviceThermalBudgetState.tier,
                    activeCount = view.activeSceneCount,
                )
                lastRenderedFrameNs = awaitNextSpecialDanmakuFrame(lastRenderedFrameNs, minIntervalNs)
            } else {
                lastRenderedFrameNs = 0L
                val next = view.nextSceneTimeMs
                if (next == null) wake.receive()
                else {
                    val speed = player.playbackParameters.speed.coerceAtLeast(0.01f)
                    val waitMs = ceil((next - position) / speed.toDouble()).toLong().coerceAtLeast(1L)
                    withTimeoutOrNull(waitMs) { wake.receive() }
                }
            }
        }
    }
    AndroidView(
        factory = { view },
        modifier = modifier.fillMaxSize(),
        update = {
            it.configure(items, viewport, opacity, fontScale, fontWeight)
            if (!renderingPaused) it.frame(player.currentPosition)
        }
    )
}

private class BasOverlayView(context: Context, private val player: Player) : View(context) {
    private var items: List<BasDanmaku> = emptyList()
    private var window = sceneWindow(items)
    val nextSceneTimeMs: Long? get() = window.nextStartTimeMs

    private fun sceneWindow(items: List<BasDanmaku>) = TimedDanmakuWindow(items,
        startTimeMs = { it.startTimeMs },
        endTimeMs = { it.startTimeMs + it.durationMs - 1L },
    )
    private val painters = java.util.IdentityHashMap<BasDanmaku, BasScenePainter>()
    private val active = ArrayList<BasScenePainter>()
    private var viewport: DanmakuViewport? = null
    private var opacity = 1f
    private var fontScale = 1f
    private var fontWeight = 5
    private var pressed: BasTarget? = null
    private var downX = 0f
    private var downY = 0f
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    val hasActiveScene: Boolean get() = active.isNotEmpty()
    val activeSceneCount: Int get() = active.size

    init {
        setWillNotDraw(false)
    }

    fun configure(items: List<BasDanmaku>, viewport: DanmakuViewport, opacity: Float, fontScale: Float, fontWeight: Int) {
        if (this.items !== items) {
            this.items = items
            window = sceneWindow(items)
            val retained = java.util.IdentityHashMap<BasDanmaku, Boolean>()
            items.forEach { retained[it] = true }
            val iterator = painters.keys.iterator()
            while (iterator.hasNext()) {
                val cached = iterator.next()
                if (!retained.containsKey(cached)) iterator.remove()
            }
        }
        this.viewport = viewport
        this.opacity = opacity
        this.fontScale = fontScale
        this.fontWeight = fontWeight
    }

    fun frame(positionMs: Long) {
        val stage = viewport ?: return
        active.clear()
        for (item in window.update(positionMs)) {
            val relative = positionMs - item.startTimeMs
            val painter = painters.getOrPut(item) { BasScenePainter(item) }
            painter.update(relative, stage.widthPx.toFloat(), stage.heightPx.toFloat(), opacity, fontScale, fontWeight)
            active.add(painter)
        }
        if (painters.size > 64) {
            val iterator = painters.values.iterator()
            while (iterator.hasNext()) {
                if (iterator.next() !in active) iterator.remove()
            }
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val stage = viewport ?: return
        val save = canvas.save()
        canvas.clipRect(0f, 0f, stage.widthPx.toFloat(), stage.heightPx.toFloat())
        for (index in active.indices) active[index].draw(canvas)
        canvas.restoreToCount(save)
    }

    private fun hit(x: Float, y: Float): BasTarget? {
        val stage = viewport ?: return null
        if (x < 0f || y < 0f || x > stage.widthPx || y > stage.heightPx) return null
        for (i in active.indices.reversed()) active[i].hit(x, y)?.let { return it }
        return null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = hit(event.x, event.y) ?: return false
                downX = event.x; downY = event.y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop) pressed = null
            }
            MotionEvent.ACTION_UP -> {
                val target = pressed
                pressed = null
                if (target != null && hit(event.x, event.y) == target) {
                    performClick()
                    activate(target)
                }
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> pressed = null
            else -> return pressed != null
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun activate(target: BasTarget) {
        if (target is BasTarget.Seek) {
            player.seekTo(target.timeMs)
            return
        }
        val url = when (target) {
            is BasTarget.Video -> {
                val video = target.bvid ?: target.aid?.let { "av$it" } ?: return
                Uri.Builder().scheme("https").authority("www.bilibili.com")
                    .appendPath("video").appendPath(video)
                    .appendQueryParameter("p", target.page.toString())
                    .appendQueryParameter("t", seconds(target.timeMs)).build()
            }
            is BasTarget.Bangumi -> {
                val episode = target.episodeId?.let { "ep$it" }
                    ?: target.seasonId?.let { "ss$it" } ?: return
                Uri.Builder().scheme("https").authority("www.bilibili.com")
                    .appendPath("bangumi").appendPath("play").appendPath(episode)
                    .appendQueryParameter("t", seconds(target.timeMs)).build()
            }
            is BasTarget.Seek -> return
        }
        player.pause()
        val generic = Intent(Intent.ACTION_VIEW, url)
            .addCategory(Intent.CATEGORY_BROWSABLE)
        val manager = context.packageManager
        val browserQuery = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER)
        val browsers = manager.queryIntentActivities(browserQuery, 0)
            .filter { it.activityInfo.packageName != context.packageName &&
                !it.activityInfo.packageName.startsWith("com.android.purebilibili") }
        val preferred = manager.resolveActivity(generic, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        val browser = browsers.firstOrNull { it.activityInfo.packageName == preferred?.activityInfo?.packageName }
            ?: browsers.firstOrNull()
        if (browser == null) {
            Toast.makeText(context, "未找到可打开弹幕链接的浏览器", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, url)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setPackage(browser.activityInfo.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "无法打开弹幕链接", Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(context, "浏览器拒绝打开弹幕链接", Toast.LENGTH_SHORT).show()
        }
    }

    private fun seconds(timeMs: Long): String = java.math.BigDecimal.valueOf(timeMs, 3).stripTrailingZeros().toPlainString()

    fun clear() {
        pressed = null
        active.clear()
        painters.clear()
    }
}
