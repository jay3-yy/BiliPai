package com.example.hdr_player

import android.content.Context
import android.util.Log
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler

class HdrPlayerPlugin : FlutterPlugin, MethodCallHandler {
    private lateinit var channel: MethodChannel
    private lateinit var appContext: Context
    companion object {
        private const val TAG = "HdrPlayerPlugin"
    }

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        Log.d(TAG, "onAttachedToEngine")
        appContext = binding.applicationContext
        channel = MethodChannel(binding.binaryMessenger, "hdr_player")
        channel.setMethodCallHandler(this)
        binding.platformViewRegistry.registerViewFactory(
            "hdr_player_view",
            HdrPlayerViewFactory(binding.binaryMessenger, appContext)
        )
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "checkHdrSupport" -> {
                Log.d(TAG, "checkHdrSupport")
                val support = HdrSupportChecker.check(appContext)
                result.success(support)
            }
            else -> result.notImplemented()
        }
    }
}
