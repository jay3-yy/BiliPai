package com.example.hdr_player

data class ToneMapOptions(
    val targetPeakNits: Int = 1000,
    val strength: Float = 1.0f,
    val saturation: Float = 1.0f,
    val highlightBoost: Float = 1.0f,
    val preDarken: Float = 0.0f,
    val highlightProtect: Float = 0.65f
)

