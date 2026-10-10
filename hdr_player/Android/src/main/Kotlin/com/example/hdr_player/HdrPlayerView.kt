package com.example.hdr_player

import android.content.Context
import android.util.Log
import android.view.SurfaceView
import android.widget.FrameLayout
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.platform.PlatformView

class HdrPlayerView(
    private val context: Context,
    messenger: BinaryMessenger,
    viewId: Int
) : PlatformView, MethodCallHandler, EventChannel.StreamHandler {
    companion object {
        private const val TAG = "HdrPlayerView"
    }
    private val surfaceView: SurfaceView = SurfaceView(context)
    private val methodChannel = MethodChannel(messenger, "hdr_player/view_$viewId")
    private val eventChannel = EventChannel(messenger, "hdr_player/view_$viewId/events")
    private val controller: HdrPlayerController

    init {
        Log.d(TAG, "init viewId=$viewId")
        val support = HdrSupportChecker.check(context)
        val displaySupported = support["displaySupported"] as? Boolean ?: false
        controller = HdrPlayerController(context, surfaceView, displaySupported)
        surfaceView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        methodChannel.setMethodCallHandler(this)
        eventChannel.setStreamHandler(this)
        surfaceView.setZOrderMediaOverlay(false)
    }

    override fun getView(): android.view.View = surfaceView

    override fun dispose() {
        controller.release()
        methodChannel.setMethodCallHandler(null)
        eventChannel.setStreamHandler(null)
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "prepare" -> {
                val args = call.arguments as? Map<*, *> ?: emptyMap<String, Any?>()
                val videoUrl = args["videoUrl"] as? String ?: ""
                val audioUrl = args["audioUrl"] as? String
                val headersAny = args["headers"] as? Map<*, *>
                val headers = headersAny?.mapNotNull { (k, v) ->
                    val key = k as? String ?: return@mapNotNull null
                    val value = v as? String ?: return@mapNotNull null
                    key to value
                }?.toMap()
                val autoplay = args["autoplay"] as? Boolean ?: true
                val startPositionMs = (args["startPositionMs"] as? Number)?.toLong()
                controller.prepare(videoUrl, audioUrl, headers, autoplay, startPositionMs)
                result.success(null)
            }
            "play" -> {
                controller.play()
                result.success(null)
            }
            "pause" -> {
                controller.pause()
                result.success(null)
            }
            "seekTo" -> {
                val args = call.arguments as? Map<*, *> ?: emptyMap<String, Any?>()
                val positionMs = (args["positionMs"] as? Number)?.toLong() ?: 0L
                controller.seekTo(positionMs)
                result.success(null)
            }
            "setRate" -> {
                val args = call.arguments as? Map<*, *> ?: emptyMap<String, Any?>()
                val rate = (args["rate"] as? Number)?.toDouble() ?: 1.0
                controller.setRate(rate)
                result.success(null)
            }
            "setVolume" -> {
                val args = call.arguments as? Map<*, *> ?: emptyMap<String, Any?>()
                val volume = (args["volume"] as? Number)?.toDouble() ?: 1.0
                controller.setVolume(volume)
                result.success(null)
            }
            "setToneMapOptions" -> {
                val args = call.arguments as? Map<*, *> ?: emptyMap<String, Any?>()
                val options = ToneMapOptions(
                    targetPeakNits = (args["targetPeakNits"] as? Number)?.toInt() ?: 1000,
                    strength = (args["strength"] as? Number)?.toFloat() ?: 1.0f,
                    saturation = (args["saturation"] as? Number)?.toFloat() ?: 1.0f,
                    highlightBoost = (args["highlightBoost"] as? Number)?.toFloat() ?: 1.0f,
                    preDarken = (args["preDarken"] as? Number)?.toFloat() ?: 0.0f,
                    highlightProtect = (args["highlightProtect"] as? Number)?.toFloat() ?: 0.65f
                )
                controller.setToneMapOptions(options)
                result.success(null)
            }
            "release" -> {
                controller.release()
                result.success(null)
            }
            else -> result.notImplemented()
        }
    }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        controller.setEventSink(events)
    }

    override fun onCancel(arguments: Any?) {
        controller.setEventSink(null)
    }
}
