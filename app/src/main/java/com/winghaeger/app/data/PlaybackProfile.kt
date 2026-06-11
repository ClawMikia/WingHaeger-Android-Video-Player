package com.winghaeger.app.data

data class PlaybackProfile(
    val id: Long = 0L,
    val name: String,
    val volumeLevel: Float = 1.0f,
    val enhancement: EnhancementMode = EnhancementMode.NONE,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val zoomLevel: Float = 1.0f,
    val audioBoost: Float = 1.0f
)
