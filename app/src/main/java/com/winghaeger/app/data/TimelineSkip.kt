package com.winghaeger.app.data

data class TimelineSkip(
    val id: Long = 0L,
    val videoId: Long,
    val startMs: Long,
    val endMs: Long,
    val label: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
