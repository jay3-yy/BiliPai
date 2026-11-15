package com.android.purebilibili.core.performance

import org.json.JSONArray
import org.json.JSONObject

internal fun historyReportFixture(start: Long = 1000, schema: Int = 5): JSONObject {
    val ui = JSONObject().put("frames", 100).put("jankyFrames", 2)
        .put("p95UiMsUpperBound", 20).put("maxUiMs", 134.8)
    if (schema >= 5) ui.put("longFrames100Ms", 1)
    val hwui = JSONObject().put("deadlineAvailableFrames", 100).put("missedDeadlineFrames", 3)
    val window = JSONObject().put("id", 1).put("kind", "main").put("frames", ui).put("hwui", hwui).put("navigation", JSONArray())
    if (schema >= 5) window.put("uiFrameSource", "raw_frame_metrics")
    return JSONObject().put("schema", schema).put("startedWallMs", start).put("startedMonotonicNs", start * 1_000_000)
        .put("elapsedMs", 5000).put("foregroundMs", 5000).put("stopReason", "user")
        .put("measurementNotes", "采集时的原始说明")
        .put("environment", JSONObject().put("version", "0.8.1").put("buildType", "release").put("debug", false)
            .put("manufacturer", "OnePlus").put("model", "CPH2691").put("sdk", 36)
            .put("uiStyle", "MATERIAL3").put("liquidGlass", false))
        .put("frames", ui).put("hwui", hwui).put("windows", JSONArray().put(window))
        .put("scenes", JSONObject().put("1/home/transition", ui))
        .put("hwuiScenes", JSONObject().put("1/home/transition", hwui))
        .put("samples", JSONArray().put(JSONObject().put("display", JSONObject().put("panelRefreshRateHz", 90.0))))
}
