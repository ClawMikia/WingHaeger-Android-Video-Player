package com.winghaeger.app.data

data class ChapterMarker(
    val id: Long = 0L,
    val videoId: Long,
    val positionMs: Long,
    val label: String,
    val isAutoDetected: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
